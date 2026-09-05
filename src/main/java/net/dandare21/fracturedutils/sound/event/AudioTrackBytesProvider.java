package net.dandare21.fracturedutils.sound.event;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

public class AudioTrackBytesProvider {

    public static byte[] getTrackBytes(String soundTrack) {
        if (soundTrack == null || soundTrack.trim().isEmpty()) return null;

        String cleanTrack = soundTrack.trim();

        // 1. Try reading directly from disk event_music/tracks/ (works on dedicated server & client)
        byte[] serverDiskBytes = readFromEventMusicTracks(cleanTrack);
        if (serverDiskBytes != null && serverDiskBytes.length > 0) {
            return serverDiskBytes;
        }

        // 2. Try reading from event_music/event_music_pack.zip
        byte[] serverZipBytes = readFromEventMusicZip(cleanTrack);
        if (serverZipBytes != null && serverZipBytes.length > 0) {
            return serverZipBytes;
        }

        // 3. If on CLIENT dist, safely delegate to ClientAudioPackManager without triggering DistCleaner on DEDICATED_SERVER
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return ClientAudioHelper.getClientTrackBytes(cleanTrack);
        }

        return null;
    }

    private static byte[] readFromEventMusicZip(String soundTrack) {
        try {
            Path zipPath = FMLPaths.GAMEDIR.get().resolve("event_music").resolve("event_music_pack.zip");
            if (!Files.exists(zipPath)) return null;

            String pathName = soundTrack;
            if (pathName.contains(":")) {
                pathName = pathName.substring(pathName.indexOf(':') + 1);
            }
            if (pathName.startsWith("sounds/music/")) {
                pathName = pathName.substring(13);
            } else if (pathName.startsWith("music/")) {
                pathName = pathName.substring(6);
            } else if (pathName.startsWith("event.")) {
                pathName = pathName.substring(6);
            }
            pathName = pathName.replace('.', '/');
            String targetOgg = pathName.endsWith(".ogg") || pathName.endsWith(".wav") ? pathName : pathName + ".ogg";
            String targetWav = pathName.endsWith(".ogg") || pathName.endsWith(".wav") ? pathName : pathName + ".wav";

            try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(zipPath.toFile())) {
                var entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    String entryName = entry.getName().replace('\\', '/');
                    if (entryName.endsWith(targetOgg) || entryName.endsWith(targetWav)) {
                        try (var is = zipFile.getInputStream(entry)) {
                            return is.readAllBytes();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static byte[] readFromEventMusicTracks(String soundTrack) {
        try {
            Path tracksDir = FMLPaths.GAMEDIR.get().resolve("event_music").resolve("tracks");
            if (!Files.exists(tracksDir)) return null;

            String pathName = soundTrack;
            if (pathName.contains(":")) {
                pathName = pathName.substring(pathName.indexOf(':') + 1);
            }
            if (pathName.startsWith("sounds/music/")) {
                pathName = pathName.substring(13);
            } else if (pathName.startsWith("music/")) {
                pathName = pathName.substring(6);
            } else if (pathName.startsWith("event.")) {
                pathName = pathName.substring(6);
            }

            pathName = pathName.replace('.', '/');
            if (pathName.endsWith(".ogg") || pathName.endsWith(".wav")) {
                Path trackPath = tracksDir.resolve(pathName);
                if (Files.exists(trackPath) && Files.isRegularFile(trackPath)) {
                    return Files.readAllBytes(trackPath);
                }
            } else {
                Path oggPath = tracksDir.resolve(pathName + ".ogg");
                if (Files.exists(oggPath) && Files.isRegularFile(oggPath)) {
                    return Files.readAllBytes(oggPath);
                }
                Path wavPath = tracksDir.resolve(pathName + ".wav");
                if (Files.exists(wavPath) && Files.isRegularFile(wavPath)) {
                    return Files.readAllBytes(wavPath);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static class ClientAudioHelper {
        private static byte[] getClientTrackBytes(String soundTrack) {
            return ClientAudioPackManager.getInstance().getTrackBytes(soundTrack);
        }
    }
}
