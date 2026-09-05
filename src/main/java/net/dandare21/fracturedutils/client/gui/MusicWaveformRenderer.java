package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.sound.event.AudioTrackBytesProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Controller and cache bridge for DAW audio waveform generation and rendering.
 * Delegates decoding to AudioWaveformExtractor and native GL rendering to WaveformRenderer.
 */
public class MusicWaveformRenderer {

    public static class TrackWaveformData {
        public static final TrackWaveformData EMPTY = new TrackWaveformData(new AudioWaveformExtractor.AudioPeak[0], new float[0], 0L);

        public final AudioWaveformExtractor.AudioPeak[] peaks;
        public final float[] amplitudes;
        public final long totalDurationMs;

        public TrackWaveformData(AudioWaveformExtractor.AudioPeak[] peaks, float[] amplitudes, long totalDurationMs) {
            this.peaks = peaks;
            this.amplitudes = amplitudes;
            this.totalDurationMs = totalDurationMs;
        }

        public TrackWaveformData(AudioWaveformExtractor.AudioPeak[] peaks, long totalDurationMs) {
            this(peaks, toAmplitudes(peaks), totalDurationMs);
        }

        public TrackWaveformData(float[] amplitudes, long totalDurationMs) {
            this(toPeaks(amplitudes), amplitudes, totalDurationMs);
        }

        private static float[] toAmplitudes(AudioWaveformExtractor.AudioPeak[] peaks) {
            if (peaks == null) return new float[0];
            float[] amps = new float[peaks.length];
            for (int i = 0; i < peaks.length; i++) {
                amps[i] = Math.max(Math.abs(peaks[i].min()), Math.abs(peaks[i].max()));
            }
            return amps;
        }

        private static AudioWaveformExtractor.AudioPeak[] toPeaks(float[] amplitudes) {
            if (amplitudes == null) return new AudioWaveformExtractor.AudioPeak[0];
            AudioWaveformExtractor.AudioPeak[] peaks = new AudioWaveformExtractor.AudioPeak[amplitudes.length];
            for (int i = 0; i < amplitudes.length; i++) {
                float a = amplitudes[i];
                peaks[i] = new AudioWaveformExtractor.AudioPeak(-a, a);
            }
            return peaks;
        }
    }

    private static final Map<String, TrackWaveformData> WAVEFORM_CACHE = Collections.synchronizedMap(new HashMap<>());
    private static final Set<String> LOADING_TRACKS = Collections.synchronizedSet(new HashSet<>());

    public static TrackWaveformData getOrComputeTrueWaveform(String soundTrack) {
        if (soundTrack == null || soundTrack.trim().isEmpty()) {
            return null;
        }

        String cleanTrack = soundTrack.trim();

        if (WAVEFORM_CACHE.containsKey(cleanTrack)) {
            return WAVEFORM_CACHE.get(cleanTrack);
        }

        if (!LOADING_TRACKS.contains(cleanTrack)) {
            LOADING_TRACKS.add(cleanTrack);

            // Asynchronous decoding in background worker so render thread is never blocked
            AudioWaveformExtractor.extractWaveformAsync(AudioTrackBytesProvider.getTrackBytes(cleanTrack))
                    .thenAccept(waveform -> {
                        if (waveform != null && waveform.peaks().length > 0) {
                            TrackWaveformData data = new TrackWaveformData(waveform.peaks(), waveform.durationMs());
                            WAVEFORM_CACHE.put(cleanTrack, data);
                            FracturedUtils.LOGGER.info("[MusicWaveformRenderer] Successfully decoded high-fidelity waveform for track '{}' ({}ms, {} peaks)",
                                    cleanTrack, waveform.durationMs(), waveform.peaks().length);
                        } else {
                            WAVEFORM_CACHE.put(cleanTrack, TrackWaveformData.EMPTY);
                            FracturedUtils.LOGGER.warn("[MusicWaveformRenderer] Could not decode track bytes for '{}', cached empty fallback.", cleanTrack);
                        }
                    })
                    .exceptionally(ex -> {
                        WAVEFORM_CACHE.put(cleanTrack, TrackWaveformData.EMPTY);
                        FracturedUtils.LOGGER.error("[MusicWaveformRenderer] Error decoding audio waveform for track '{}'", cleanTrack, ex);
                        return null;
                    })
                    .whenComplete((res, ex) -> LOADING_TRACKS.remove(cleanTrack));
        }

        return null;
    }

    public static void renderWaveform(
            GuiGraphics guiGraphics,
            String songTrack,
            int startX, int startY,
            int trackWidth, int trackHeight,
            double timeScrollMs, double pixelsPerSecond
    ) {
        Font font = Minecraft.getInstance().font;

        if (songTrack == null || songTrack.trim().isEmpty()) {
            guiGraphics.drawString(font, "CH 0: AUDIO WAVEFORM [No Song Selected]", startX + 8, startY + 6, 0xFF00E5FF, false);
            int centerY = startY + (trackHeight / 2);
            guiGraphics.fill(startX, centerY, startX + trackWidth, centerY + 1, 0x5500E5FF);
            return;
        }

        TrackWaveformData waveformData = getOrComputeTrueWaveform(songTrack);

        if (waveformData == null) {
            // Render Cyberpunk Animated Loading Indicator
            long time = System.currentTimeMillis();
            String[] spinner = new String[]{"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
            String symbol = spinner[(int) ((time / 80) % spinner.length)];

            guiGraphics.drawString(font, "CH 0: AUDIO WAVEFORM (" + songTrack + ") " + symbol + " DECODING HIGH-RES PCM PEAKS...", startX + 8, startY + 6, 0xFFFFD700, false);

            // Animated loading bar accent
            int barLen = 80;
            int animX = (int) ((time / 10) % Math.max(1, trackWidth - barLen));
            guiGraphics.fill(startX + animX, startY + trackHeight - 4, startX + animX + barLen, startY + trackHeight - 2, 0xFFFFD700);

            // Subtle center line while loading
            int centerY = startY + (trackHeight / 2);
            guiGraphics.fill(startX, centerY, startX + trackWidth, centerY + 1, 0x44FFD700);
            return;
        }

        AudioWaveformExtractor.AudioPeak[] peaks = waveformData.peaks;
        long duration = waveformData.totalDurationMs > 0 ? waveformData.totalDurationMs : 180000L;

        if (peaks == null || peaks.length == 0) {
            guiGraphics.drawString(font, "CH 0: AUDIO WAVEFORM (" + songTrack + " - File Not Found / Unreadable)", startX + 8, startY + 6, 0xFFFF3355, false);
            int centerY = startY + (trackHeight / 2);
            guiGraphics.fill(startX, centerY, startX + trackWidth, centerY + 1, 0xAAFF3355);
            return;
        }

        // Render High-Resolution Screen-Space Waveform Mesh via Native GL Triangle Strips
        WaveformRenderer.renderWaveform(
                guiGraphics,
                startX, startY,
                trackWidth, trackHeight,
                pixelsPerSecond, timeScrollMs,
                peaks, duration
        );

        // Render Track Header Tag with True Duration
        long durSec = duration / 1000;
        String durationStr = String.format("%02d:%02d.%03d", durSec / 60, durSec % 60, duration % 1000);
        guiGraphics.drawString(font, "CH 0: AUDIO WAVEFORM (" + songTrack + " - " + durationStr + ")", startX + 8, startY + 6, 0xFF00E5FF, false);
    }
}
