package net.dandare21.fracturedutils.sound.event;

import net.dandare21.fracturedutils.sound.sequence.MusicSequenceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class AudioTrackDiscovery {

    /**
     * Discovers all available music/audio tracks from raw disk directories, audio pack zips,
     * client/server managers, sequence files, and Minecraft SoundManager.
     */
    public static List<String> getAllAvailableTracks() {
        Set<String> tracks = new LinkedHashSet<>();

        // 1. Scan filesystem directories for audio files (.ogg, .wav)
        scanDiskTracks(tracks);

        // 2. Scan audio pack zip files
        scanZipPacks(tracks);

        // 3. ClientAudioPackManager registered tracks
        try {
            List<String> clientTracks = ClientAudioPackManager.getInstance().getAvailableTracks();
            if (clientTracks != null) {
                tracks.addAll(clientTracks);
            }
        } catch (Throwable ignored) {}

        // 4. EventAudioManager registered tracks
        try {
            List<String> serverTracks = EventAudioManager.getInstance().getAvailableTrackSuggestions();
            if (serverTracks != null) {
                tracks.addAll(serverTracks);
            }
        } catch (Throwable ignored) {}

        // 5. Existing sequence files in server & client storage
        try {
            for (String json : MusicSequenceManager.getInstance().getAllSequenceFiles().values()) {
                extractTrackFromJson(json, tracks);
            }
        } catch (Throwable ignored) {}

        // 6. Minecraft Client SoundManager (music discs, custom sounds, event sounds)
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                for (ResourceLocation loc : mc.getSoundManager().getAvailableSounds()) {
                    if (loc == null) continue;
                    String path = loc.getPath();
                    String ns = loc.getNamespace();

                    if (ns.equals("fracturedutils") || ns.equals("fractured_utils")) {
                        if (path.startsWith("event.")) {
                            tracks.add(path);
                        } else {
                            tracks.add(loc.toString());
                        }
                    } else if (path.startsWith("event.")) {
                        tracks.add(path);
                    } else if (path.startsWith("music_disc.") || path.startsWith("music.")) {
                        tracks.add(loc.toString());
                    }
                }
            }
        } catch (Throwable ignored) {}

        return cleanAndDeduplicate(tracks);
    }

    private static void scanDiskTracks(Set<String> tracks) {
        Path gameDir = FMLPaths.GAMEDIR.get();
        List<Path> candidateDirs = new ArrayList<>();

        // In game directory
        candidateDirs.add(gameDir.resolve("event_music").resolve("tracks"));
        candidateDirs.add(gameDir.resolve("event_music"));
        candidateDirs.add(gameDir.resolve("tracks"));

        // In parent directory (e.g. multi-client run configurations like run/client1)
        if (gameDir.getParent() != null) {
            candidateDirs.add(gameDir.getParent().resolve("event_music").resolve("tracks"));
            candidateDirs.add(gameDir.getParent().resolve("event_music"));
            candidateDirs.add(gameDir.getParent().resolve("tracks"));
        }

        for (Path dir : candidateDirs) {
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> {
                                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                                return name.endsWith(".ogg") || name.endsWith(".wav");
                            })
                            .forEach(p -> {
                                String rel = dir.relativize(p).toString().replace('\\', '/');
                                String clean = rel;
                                if (clean.endsWith(".ogg")) clean = clean.substring(0, clean.length() - 4);
                                else if (clean.endsWith(".wav")) clean = clean.substring(0, clean.length() - 4);

                                // Strip redundant 'tracks/' if dir was base event_music
                                if (clean.startsWith("tracks/")) {
                                    clean = clean.substring(7);
                                }

                                String trackId = "event." + clean.replace('/', '.');
                                tracks.add(trackId);
                            });
                } catch (Exception ignored) {}
            }
        }
    }

    private static void scanZipPacks(Set<String> tracks) {
        Path gameDir = FMLPaths.GAMEDIR.get();
        List<Path> zipCandidates = new ArrayList<>();
        zipCandidates.add(gameDir.resolve("event_music").resolve("event_music_pack.zip"));
        zipCandidates.add(gameDir.resolve("fractured_utils_cache").resolve("event_music_pack.zip"));
        zipCandidates.add(gameDir.resolve("resourcepacks").resolve("event_music_pack.zip"));

        if (gameDir.getParent() != null) {
            zipCandidates.add(gameDir.getParent().resolve("event_music").resolve("event_music_pack.zip"));
            zipCandidates.add(gameDir.getParent().resolve("fractured_utils_cache").resolve("event_music_pack.zip"));
            zipCandidates.add(gameDir.getParent().resolve("resourcepacks").resolve("event_music_pack.zip"));
        }

        for (Path zipPath : zipCandidates) {
            if (Files.exists(zipPath) && Files.isRegularFile(zipPath)) {
                try (ZipFile zf = new ZipFile(zipPath.toFile())) {
                    var entries = zf.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.endsWith(".ogg") || name.endsWith(".wav")) {
                            if (name.contains("/sounds/music/")) {
                                int idx = name.indexOf("/sounds/music/") + "/sounds/music/".length();
                                String trackSub = name.substring(idx);
                                if (trackSub.endsWith(".ogg")) trackSub = trackSub.substring(0, trackSub.length() - 4);
                                else if (trackSub.endsWith(".wav")) trackSub = trackSub.substring(0, trackSub.length() - 4);
                                tracks.add("event." + trackSub.replace('/', '.'));
                            } else if (name.contains("/sounds/")) {
                                int idx = name.indexOf("/sounds/") + "/sounds/".length();
                                String trackSub = name.substring(idx);
                                if (trackSub.endsWith(".ogg")) trackSub = trackSub.substring(0, trackSub.length() - 4);
                                else if (trackSub.endsWith(".wav")) trackSub = trackSub.substring(0, trackSub.length() - 4);
                                tracks.add("event." + trackSub.replace('/', '.'));
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private static void extractTrackFromJson(String json, Set<String> tracks) {
        if (json == null || json.isEmpty()) return;
        int idx = json.indexOf("\"songTrack\":");
        if (idx != -1) {
            int startQuote = json.indexOf('\"', idx + 12);
            if (startQuote != -1) {
                int endQuote = json.indexOf('\"', startQuote + 1);
                if (endQuote != -1) {
                    String track = json.substring(startQuote + 1, endQuote).trim();
                    if (!track.isEmpty()) {
                        tracks.add(track);
                    }
                }
            }
        }
    }

    private static List<String> cleanAndDeduplicate(Set<String> rawTracks) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : rawTracks) {
            if (raw == null || raw.trim().isEmpty()) continue;
            String t = raw.trim();

            if (t.startsWith("fracturedutils:event.")) {
                t = t.substring(15);
            } else if (t.startsWith("fractured_utils:event.")) {
                t = t.substring(16);
            } else if (t.startsWith("fracturedutils:") && !t.contains("/")) {
                t = "event." + t.substring(15);
            }

            normalized.add(t);
        }

        // Separate event tracks and other tracks
        List<String> eventTracks = new ArrayList<>();
        List<String> vanillaTracks = new ArrayList<>();
        List<String> otherTracks = new ArrayList<>();

        for (String t : normalized) {
            if (t.startsWith("event.")) {
                eventTracks.add(t);
            } else if (t.startsWith("minecraft:music_disc.") || t.startsWith("minecraft:music.")) {
                vanillaTracks.add(t);
            } else {
                otherTracks.add(t);
            }
        }

        Collections.sort(eventTracks, String.CASE_INSENSITIVE_ORDER);
        Collections.sort(vanillaTracks, String.CASE_INSENSITIVE_ORDER);
        Collections.sort(otherTracks, String.CASE_INSENSITIVE_ORDER);

        List<String> result = new ArrayList<>();
        result.addAll(eventTracks);
        result.addAll(vanillaTracks);
        result.addAll(otherTracks);
        return result;
    }

    /**
     * Formats a raw track ID into a clean, human-readable display title.
     * e.g. "event.gamersillo" -> "Gamersillo"
     *      "minecraft:music_disc.pigstep" -> "Music Disc: Pigstep"
     */
    public static String formatTrackLabel(String trackId) {
        if (trackId == null || trackId.isEmpty()) return "[None / No Song]";
        String s = trackId;

        if (s.startsWith("event.")) {
            s = s.substring(6);
            return capitalizeWords(s.replace('_', ' ').replace(".", " / "));
        }

        if (s.startsWith("minecraft:music_disc.")) {
            String disc = s.substring("minecraft:music_disc.".length());
            return "Music Disc: " + capitalizeWords(disc.replace('_', ' '));
        }

        if (s.startsWith("minecraft:music.")) {
            String mus = s.substring("minecraft:music.".length());
            return "Music: " + capitalizeWords(mus.replace('_', ' ').replace(".", " / "));
        }

        if (s.contains(":")) {
            String path = s.substring(s.indexOf(':') + 1);
            return capitalizeWords(path.replace('_', ' ').replace("/", " / "));
        }

        return capitalizeWords(s.replace('_', ' '));
    }

    private static String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c) || c == '/' || c == '-' || c == ':') {
                capitalizeNext = true;
                sb.append(c);
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
