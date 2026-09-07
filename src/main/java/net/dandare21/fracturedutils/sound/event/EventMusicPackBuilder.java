package net.dandare21.fracturedutils.sound.event;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.dandare21.fracturedutils.FracturedUtils;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class EventMusicPackBuilder {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path baseDir;
    private final Path tracksDir;
    private final Path zipFile;

    private String sha1Hex = "";
    private List<String> registeredTracks = new ArrayList<>();

    public EventMusicPackBuilder() {
        this.baseDir = FMLPaths.GAMEDIR.get().resolve("event_music");
        this.tracksDir = baseDir.resolve("tracks");
        this.zipFile = baseDir.resolve("event_music_pack.zip");
    }

    public synchronized boolean buildPack(String namespace) {
        try {
            if (!Files.exists(tracksDir)) {
                Files.createDirectories(tracksDir);
                createDummySampleTrackIfEmpty();
            }

            List<File> oggFiles = new ArrayList<>();
            List<Path> searchDirs = new ArrayList<>();
            if (Files.exists(tracksDir)) searchDirs.add(tracksDir);
            if (Files.exists(baseDir)) searchDirs.add(baseDir);
            Path parentTracks = baseDir.getParent() != null ? baseDir.getParent().resolve("event_music").resolve("tracks") : null;
            if (parentTracks != null && Files.exists(parentTracks) && !parentTracks.equals(tracksDir)) {
                searchDirs.add(parentTracks);
            }

            Set<String> seenNames = new HashSet<>();
            for (Path dir : searchDirs) {
                try (var stream = Files.walk(dir)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> p.toString().toLowerCase().endsWith(".ogg"))
                            .forEach(p -> {
                                String name = p.getFileName().toString();
                                if (seenNames.add(name)) {
                                    oggFiles.add(p.toFile());
                                }
                            });
                } catch (Exception ignored) {}
            }

            // Sort files alphabetically for consistent content ordering & zip reproducibility across OSes & server resets
            Collections.sort(oggFiles, (a, b) -> a.getPath().compareTo(b.getPath()));

            registeredTracks.clear();

            // 1. Build pack.mcmeta JSON
            JsonObject mcmetaRoot = new JsonObject();
            JsonObject packObj = new JsonObject();
            packObj.addProperty("pack_format", 15);
            packObj.addProperty("description", "Server Custom Event Audio Assets");
            mcmetaRoot.add("pack", packObj);
            String mcmetaContent = GSON.toJson(mcmetaRoot);

            // 2. Build assets/<namespace>/sounds.json
            JsonObject soundsJsonRoot = new JsonObject();
            for (File oggFile : oggFiles) {
                String relativeName = tracksDir.relativize(oggFile.toPath()).toString().replace('\\', '/');
                String cleanName = relativeName.substring(0, relativeName.length() - 4); // strip .ogg
                String soundEventId = "event." + cleanName.replace('/', '.');
                String soundPath = namespace + ":music/" + cleanName;

                JsonObject entryObj = new JsonObject();
                entryObj.addProperty("category", "record");

                JsonArray soundsArr = new JsonArray();
                JsonObject soundDetail = new JsonObject();
                soundDetail.addProperty("name", soundPath);
                soundDetail.addProperty("stream", true); // MANDATORY Memory Safety Requirement
                soundsArr.add(soundDetail);

                entryObj.add("sounds", soundsArr);
                soundsJsonRoot.add(soundEventId, entryObj);

                // Add short name alias (e.g. track1)
                if (soundEventId.startsWith("event.")) {
                    String shortName = soundEventId.substring(6);
                    soundsJsonRoot.add(shortName, entryObj);
                }

                registeredTracks.add(soundEventId);
            }
            String soundsJsonContent = GSON.toJson(soundsJsonRoot);

            // 3. Create reproducible zip archive (fixed timestamps)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                // Add pack.mcmeta
                ZipEntry mcmetaEntry = new ZipEntry("pack.mcmeta");
                mcmetaEntry.setTime(0L);
                zos.putNextEntry(mcmetaEntry);
                zos.write(mcmetaContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // Add assets/<namespace>/sounds.json
                ZipEntry soundsEntry = new ZipEntry("assets/" + namespace + "/sounds.json");
                soundsEntry.setTime(0L);
                zos.putNextEntry(soundsEntry);
                zos.write(soundsJsonContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // Add audio files with fixed timestamps
                for (File oggFile : oggFiles) {
                    String relativeName = tracksDir.relativize(oggFile.toPath()).toString().replace('\\', '/');
                    String zipPath = "assets/" + namespace + "/sounds/music/" + relativeName;
                    ZipEntry audioEntry = new ZipEntry(zipPath);
                    audioEntry.setTime(0L);
                    zos.putNextEntry(audioEntry);
                    Files.copy(oggFile.toPath(), zos);
                    zos.closeEntry();
                }
            }

            byte[] zipBytes = baos.toByteArray();
            Files.write(zipFile, zipBytes);

            // 4. Compute SHA-1 Checksum of the generated zip file (matching client verification)
            this.sha1Hex = computeFileSha1Hex(zipFile.toFile());

            FracturedUtils.LOGGER.info("[EventMusicPackBuilder] Successfully built deterministic event music resource pack (SHA1: {}, tracks: {})", sha1Hex, registeredTracks.size());
            return true;

        } catch (Exception e) {
            FracturedUtils.LOGGER.error("[EventMusicPackBuilder] Failed to build event music resource pack", e);
            return false;
        }
    }

    public static String computeFileSha1Hex(File file) {
        if (file == null || !file.exists()) return "";
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void createDummySampleTrackIfEmpty() {
        try {
            File readme = tracksDir.resolve("README.txt").toFile();
            if (!readme.exists()) {
                try (FileWriter writer = new FileWriter(readme, StandardCharsets.UTF_8)) {
                    writer.write("Place your event music files (.ogg format) in this directory!\n" +
                            "Subfolders are supported and will be prefixed in the sound identifier.\n" +
                            "Example: track1.ogg -> fracturedutils:event.track1\n" +
                            "All sound entries are automatically formatted with 'stream: true' for memory safety.\n");
                }
            }
        } catch (Exception ignored) {
        }
    }

    public Path getZipFile() {
        return zipFile;
    }

    public String getSha1Hex() {
        return sha1Hex;
    }

    public List<String> getRegisteredTracks() {
        return registeredTracks;
    }

    public List<String> getAvailableTrackSuggestions(String namespace) {
        List<String> suggestions = new ArrayList<>();
        for (String track : registeredTracks) {
            suggestions.add(track);
        }
        return suggestions;
    }
}
