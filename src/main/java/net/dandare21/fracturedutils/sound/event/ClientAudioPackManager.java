package net.dandare21.fracturedutils.sound.event;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.C2SResourcePackStatusPacket;
import net.minecraft.network.protocol.game.ServerboundResourcePackPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@OnlyIn(Dist.CLIENT)
public class ClientAudioPackManager {

    private static final ClientAudioPackManager INSTANCE = new ClientAudioPackManager();

    private final EventMusicPackBuilder packBuilder = new EventMusicPackBuilder();
    private String localSha1Hex = "";
    private List<String> availableTracks = new ArrayList<>();
    private File activePackFile = null;
    private boolean initialized = false;

    public static ClientAudioPackManager getInstance() {
        return INSTANCE;
    }

    public synchronized void init() {
        if (initialized) return;
        initialized = true;

        File zipFile = locateClientPackZip();
        if (zipFile != null && zipFile.exists()) {
            this.activePackFile = zipFile;
            this.localSha1Hex = EventMusicPackBuilder.computeFileSha1Hex(zipFile);
            FracturedUtils.LOGGER.info("[ClientAudioPackManager] Successfully initialized client event_music_pack.zip from {} (SHA1: {})", zipFile.getAbsolutePath(), localSha1Hex);
        } else {
            FracturedUtils.LOGGER.info("[ClientAudioPackManager] No local event_music_pack.zip found in client directory.");
        }
    }

    public File locateClientPackZip() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path p1 = gameDir.resolve("event_music").resolve("event_music_pack.zip");
        if (p1.toFile().exists()) return p1.toFile();

        Path p2 = gameDir.resolve("fractured_utils_cache").resolve("event_music_pack.zip");
        if (p2.toFile().exists()) return p2.toFile();

        Path p3 = gameDir.resolve("resourcepacks").resolve("event_music_pack.zip");
        if (p3.toFile().exists()) return p3.toFile();

        Path tracksDir = gameDir.resolve("event_music").resolve("tracks");
        if (Files.exists(tracksDir)) {
            boolean built = packBuilder.buildPack("fracturedutils");
            if (built && packBuilder.getZipFile().toFile().exists()) {
                return packBuilder.getZipFile().toFile();
            }
        }

        return null;
    }

    public byte[] getTrackBytes(String soundEventId) {
        if (soundEventId == null || soundEventId.trim().isEmpty()) return null;
        String trackStr = soundEventId.trim();
        String cleanId = trackStr.contains(":") ? trackStr.substring(trackStr.indexOf(':') + 1) : trackStr;
        if (cleanId.startsWith("event.")) cleanId = cleanId.substring(6);

        String pathFromId = cleanId.replace('.', '/');
        Path gameDir = FMLPaths.GAMEDIR.get();

        // 1. Prioritize raw disk files in event_music/tracks (always up-to-date)
        Path tracksDir = gameDir.resolve("event_music").resolve("tracks");
        if (Files.exists(tracksDir)) {
            Path p1 = tracksDir.resolve(cleanId + ".ogg");
            if (Files.exists(p1)) {
                try { return Files.readAllBytes(p1); } catch (Exception ignored) {}
            }
            Path p1w = tracksDir.resolve(cleanId + ".wav");
            if (Files.exists(p1w)) {
                try { return Files.readAllBytes(p1w); } catch (Exception ignored) {}
            }
            Path p2 = tracksDir.resolve(pathFromId + ".ogg");
            if (Files.exists(p2)) {
                try { return Files.readAllBytes(p2); } catch (Exception ignored) {}
            }
            Path p2w = tracksDir.resolve(pathFromId + ".wav");
            if (Files.exists(p2w)) {
                try { return Files.readAllBytes(p2w); } catch (Exception ignored) {}
            }

            // Recursive search in event_music/tracks
            final String searchCleanId = cleanId;
            final String searchPathId = pathFromId;
            try (var stream = Files.walk(tracksDir)) {
                var found = stream.filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.toString().toLowerCase();
                            return name.endsWith(".ogg") || name.endsWith(".wav");
                        })
                        .filter(p -> {
                            String rel = tracksDir.relativize(p).toString().replace('\\', '/');
                            String nameNoExt = rel;
                            if (rel.endsWith(".ogg")) nameNoExt = rel.substring(0, rel.length() - 4);
                            else if (rel.endsWith(".wav")) nameNoExt = rel.substring(0, rel.length() - 4);
                            String dotName = nameNoExt.replace('/', '.');
                            return nameNoExt.equalsIgnoreCase(searchCleanId)
                                    || nameNoExt.equalsIgnoreCase(searchPathId)
                                    || dotName.equalsIgnoreCase(searchCleanId)
                                    || ("event." + dotName).equalsIgnoreCase(trackStr)
                                    || p.getFileName().toString().equalsIgnoreCase(searchCleanId + ".ogg")
                                    || p.getFileName().toString().equalsIgnoreCase(searchCleanId + ".wav");
                        })
                        .findFirst();
                if (found.isPresent()) {
                    try { return Files.readAllBytes(found.get()); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }

        // 2. Search client event_music_pack.zip
        File packFile = getActivePackFile();
        if (packFile == null || !packFile.exists()) packFile = locateClientPackZip();

        if (packFile != null && packFile.exists()) {
            try (ZipFile zf = new ZipFile(packFile)) {
                String[] candidates = new String[]{
                        "assets/fracturedutils/sounds/music/" + cleanId + ".ogg",
                        "assets/fracturedutils/sounds/music/" + cleanId + ".wav",
                        "assets/fracturedutils/sounds/music/" + pathFromId + ".ogg",
                        "assets/fracturedutils/sounds/music/" + pathFromId + ".wav",
                        "assets/fracturedutils/sounds/" + cleanId + ".ogg",
                        "assets/fracturedutils/sounds/" + cleanId + ".wav",
                        "assets/fracturedutils/sounds/" + pathFromId + ".ogg",
                        "assets/fracturedutils/sounds/" + pathFromId + ".wav",
                        "assets/fractured_utils/sounds/music/" + cleanId + ".ogg",
                        "assets/fractured_utils/sounds/music/" + cleanId + ".wav",
                        "assets/minecraft/sounds/music/" + cleanId + ".ogg",
                        "assets/minecraft/sounds/music/" + cleanId + ".wav",
                        cleanId + ".ogg",
                        cleanId + ".wav"
                };
                for (String cand : candidates) {
                    ZipEntry entry = zf.getEntry(cand);
                    if (entry != null) {
                        try (InputStream is = zf.getInputStream(entry)) {
                            return is.readAllBytes();
                        }
                    }
                }
            } catch (Exception e) {
                FracturedUtils.LOGGER.error("[ClientAudioPackManager] Error reading track bytes for " + soundEventId, e);
            }
        }

        // 3. Search Minecraft Client ResourceManager
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.getResourceManager() != null) {
                var rm = mc.getResourceManager();
                net.minecraft.resources.ResourceLocation[] resCandidates = new net.minecraft.resources.ResourceLocation[]{
                        net.minecraft.resources.ResourceLocation.tryParse("fracturedutils:sounds/music/" + cleanId + ".ogg"),
                        net.minecraft.resources.ResourceLocation.tryParse("fracturedutils:sounds/music/" + pathFromId + ".ogg"),
                        net.minecraft.resources.ResourceLocation.tryParse("fracturedutils:sounds/" + cleanId + ".ogg"),
                        net.minecraft.resources.ResourceLocation.tryParse("fracturedutils:sounds/" + pathFromId + ".ogg"),
                        net.minecraft.resources.ResourceLocation.tryParse("fracturedutils:music/" + cleanId + ".ogg"),
                        net.minecraft.resources.ResourceLocation.tryParse("minecraft:sounds/music/" + cleanId + ".ogg")
                };
                for (var loc : resCandidates) {
                    if (loc != null) {
                        var res = rm.getResource(loc);
                        if (res.isPresent()) {
                            try (InputStream is = res.get().open()) {
                                return is.readAllBytes();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // 4. Search parent workspace directory (dev environment)
        if (gameDir.getParent() != null) {
            Path parentTracks = gameDir.getParent().resolve("event_music").resolve("tracks");
            if (Files.exists(parentTracks)) {
                Path p1 = parentTracks.resolve(cleanId + ".ogg");
                if (Files.exists(p1)) {
                    try { return Files.readAllBytes(p1); } catch (Exception ignored) {}
                }
                Path p2 = parentTracks.resolve(pathFromId + ".ogg");
                if (Files.exists(p2)) {
                    try { return Files.readAllBytes(p2); } catch (Exception ignored) {}
                }
            }
        }

        return null;
    }

    public synchronized void handlePackSync(String serverSha1Hex, boolean required, List<String> tracks) {
        if (tracks != null && !tracks.isEmpty()) {
            this.availableTracks = new ArrayList<>(tracks);
        }

        File found = activePackFile != null && activePackFile.exists() ? activePackFile : locateClientPackZip();
        if (found != null && found.exists()) {
            this.activePackFile = found;
            this.localSha1Hex = EventMusicPackBuilder.computeFileSha1Hex(found);
        }

        ModMessages.sendToServer(new C2SResourcePackStatusPacket(ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
        FracturedUtils.LOGGER.info("[ClientAudioPackManager] Client event audio pack ready. Replied SUCCESSFULLY_LOADED to server.");
    }

    public File getActivePackFile() {
        return activePackFile;
    }

    public String getLocalSha1Hex() {
        return localSha1Hex;
    }

    public List<String> getAvailableTracks() {
        if (availableTracks == null || availableTracks.isEmpty()) {
            boolean built = packBuilder.buildPack("fracturedutils");
            if (built) {
                availableTracks = packBuilder.getAvailableTrackSuggestions("fracturedutils");
            }
        }
        return availableTracks != null ? availableTracks : new ArrayList<>();
    }
}
