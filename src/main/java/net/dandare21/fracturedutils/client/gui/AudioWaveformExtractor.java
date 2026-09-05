package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.FracturedUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-performance audio decoder and peak extractor for DAW-like waveform rendering.
 * Supports Vorbis (.ogg) via LWJGL STBVorbis and PCM/WAV via Java AudioSystem.
 */
public class AudioWaveformExtractor {

    public record AudioPeak(float min, float max) {
        public static final AudioPeak ZERO = new AudioPeak(0.0f, 0.0f);

        public AudioPeak {
            if (min > max) {
                float tmp = min;
                min = max;
                max = tmp;
            }
        }
    }

    public record ExtractedWaveform(
            AudioPeak[] peaks,
            long durationMs,
            int sampleRate,
            int channels
    ) {
        public static final ExtractedWaveform EMPTY = new ExtractedWaveform(new AudioPeak[0], 0L, 44100, 2);
    }

    private static final ExecutorService DECODE_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            r -> {
                Thread t = new Thread(r, "AudioWaveformExtractor-Worker");
                t.setDaemon(true);
                return t;
            }
    );

    /**
     * Default peak density target: 400 peaks per second of audio duration,
     * with a minimum slice count of 4000 slices.
     */
    public static final int DEFAULT_PEAKS_PER_SECOND = 400;
    public static final int MIN_TOTAL_SLICES = 4000;

    public static CompletableFuture<AudioPeak[]> extractPeaksAsync(byte[] audioBytes) {
        return CompletableFuture.supplyAsync(() -> extractPeaks(audioBytes), DECODE_EXECUTOR);
    }

    public static CompletableFuture<ExtractedWaveform> extractWaveformAsync(byte[] audioBytes) {
        return CompletableFuture.supplyAsync(() -> extractWaveform(audioBytes), DECODE_EXECUTOR);
    }

    public static AudioPeak[] extractPeaks(byte[] audioBytes) {
        ExtractedWaveform waveform = extractWaveform(audioBytes);
        return waveform != null ? waveform.peaks() : new AudioPeak[0];
    }

    public static AudioPeak[] extractPeaks(byte[] audioBytes, int targetSliceCount) {
        ExtractedWaveform waveform = extractWaveform(audioBytes, targetSliceCount);
        return waveform != null ? waveform.peaks() : new AudioPeak[0];
    }

    public static ExtractedWaveform extractWaveform(byte[] audioBytes) {
        return extractWaveform(audioBytes, -1);
    }

    public static ExtractedWaveform extractWaveform(byte[] audioBytes, int targetSliceCount) {
        if (audioBytes == null || audioBytes.length == 0) {
            return ExtractedWaveform.EMPTY;
        }

        // Detect format: Ogg Vorbis or WAV
        if (isOggFormat(audioBytes)) {
            ExtractedWaveform oggResult = decodeVorbisOgg(audioBytes, targetSliceCount);
            if (oggResult != null) return oggResult;
        }

        // Fallback or explicit WAV
        ExtractedWaveform wavResult = decodeWav(audioBytes, targetSliceCount);
        if (wavResult != null) return wavResult;

        // If WAV failed and we didn't try Ogg yet, try Ogg as fallback
        if (!isOggFormat(audioBytes)) {
            ExtractedWaveform oggResult = decodeVorbisOgg(audioBytes, targetSliceCount);
            if (oggResult != null) return oggResult;
        }

        FracturedUtils.LOGGER.warn("[AudioWaveformExtractor] Failed to decode audio bytes as .ogg or .wav");
        return ExtractedWaveform.EMPTY;
    }

    private static boolean isOggFormat(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 0x4F // 'O'
                && bytes[1] == 0x67 // 'g'
                && bytes[2] == 0x67 // 'g'
                && bytes[3] == 0x53; // 'S'
    }

    /**
     * Decodes Ogg Vorbis stream using LWJGL STBVorbis.
     */
    private static ExtractedWaveform decodeVorbisOgg(byte[] oggBytes, int requestedSlices) {
        ByteBuffer rawOggBuffer = null;
        long decoder = 0;
        try {
            rawOggBuffer = MemoryUtil.memAlloc(oggBytes.length);
            rawOggBuffer.put(oggBytes);
            rawOggBuffer.flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer error = stack.mallocInt(1);
                decoder = STBVorbis.stb_vorbis_open_memory(rawOggBuffer, error, null);
                if (decoder == 0) {
                    return null;
                }

                STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                STBVorbis.stb_vorbis_get_info(decoder, info);
                int channels = Math.max(1, info.channels());
                int sampleRate = Math.max(1, info.sample_rate());
                int totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);

                if (totalSamples <= 0) {
                    return null;
                }

                long totalDurationMs = (long) (((double) totalSamples / sampleRate) * 1000.0);
                int numSlices = calculateSliceCount(totalDurationMs, requestedSlices);
                AudioPeak[] peaks = new AudioPeak[numSlices];
                int samplesPerSlice = Math.max(1, totalSamples / numSlices);

                ShortBuffer pcmBuffer = BufferUtils.createShortBuffer(4096 * channels);

                int currentSlice = 0;
                int samplesAccumulatedInSlice = 0;
                float sliceMin = 1.0f;
                float sliceMax = -1.0f;

                while (currentSlice < numSlices) {
                    pcmBuffer.clear();
                    int samplesRead = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, pcmBuffer);
                    if (samplesRead <= 0) break;

                    for (int s = 0; s < samplesRead; s++) {
                        // Downmixing channels to mono: (L + R) / 2 or sum(C) / C
                        float mono;
                        if (channels == 1) {
                            mono = pcmBuffer.get() / 32768.0f;
                        } else if (channels == 2) {
                            float left = pcmBuffer.get() / 32768.0f;
                            float right = pcmBuffer.get() / 32768.0f;
                            mono = (left + right) * 0.5f;
                        } else {
                            float sum = 0.0f;
                            for (int c = 0; c < channels; c++) {
                                sum += pcmBuffer.get() / 32768.0f;
                            }
                            mono = sum / channels;
                        }

                        // Capture true mathematical min and max in the slice
                        if (mono < sliceMin) sliceMin = mono;
                        if (mono > sliceMax) sliceMax = mono;
                        samplesAccumulatedInSlice++;

                        if (samplesAccumulatedInSlice >= samplesPerSlice) {
                            peaks[currentSlice] = createPeak(sliceMin, sliceMax);
                            currentSlice++;
                            samplesAccumulatedInSlice = 0;
                            sliceMin = 1.0f;
                            sliceMax = -1.0f;
                            if (currentSlice >= numSlices) break;
                        }
                    }
                }

                // Fill remaining slices if stream ended early
                while (currentSlice < numSlices) {
                    peaks[currentSlice] = (sliceMin <= sliceMax) ? createPeak(sliceMin, sliceMax) : AudioPeak.ZERO;
                    currentSlice++;
                    sliceMin = 1.0f;
                    sliceMax = -1.0f;
                }

                return new ExtractedWaveform(peaks, totalDurationMs, sampleRate, channels);
            }
        } catch (Exception e) {
            FracturedUtils.LOGGER.error("[AudioWaveformExtractor] STBVorbis decode exception", e);
            return null;
        } finally {
            if (decoder != 0) {
                STBVorbis.stb_vorbis_close(decoder);
            }
            if (rawOggBuffer != null) {
                MemoryUtil.memFree(rawOggBuffer);
            }
        }
    }

    /**
     * Decodes WAV audio using standard Java AudioSystem.
     */
    private static ExtractedWaveform decodeWav(byte[] wavBytes, int requestedSlices) {
        try (InputStream bais = new ByteArrayInputStream(wavBytes);
             AudioInputStream baseAis = AudioSystem.getAudioInputStream(bais)) {

            AudioFormat baseFormat = baseAis.getFormat();
            // Convert to 16-bit signed PCM little-endian
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false // little-endian
            );

            AudioInputStream pcmAis = AudioSystem.isConversionSupported(pcmFormat, baseFormat)
                    ? AudioSystem.getAudioInputStream(pcmFormat, baseAis)
                    : baseAis;

            int channels = Math.max(1, pcmAis.getFormat().getChannels());
            int sampleRate = Math.max(1, (int) pcmAis.getFormat().getSampleRate());
            long frameLength = pcmAis.getFrameLength();

            byte[] allPcmBytes = pcmAis.readAllBytes();
            int bytesPerFrame = channels * 2;
            int totalFrames = allPcmBytes.length / bytesPerFrame;
            if (totalFrames <= 0) {
                return null;
            }

            long totalDurationMs = (long) (((double) totalFrames / sampleRate) * 1000.0);
            int numSlices = calculateSliceCount(totalDurationMs, requestedSlices);
            AudioPeak[] peaks = new AudioPeak[numSlices];
            int framesPerSlice = Math.max(1, totalFrames / numSlices);

            int currentSlice = 0;
            int framesAccumulatedInSlice = 0;
            float sliceMin = 1.0f;
            float sliceMax = -1.0f;

            for (int f = 0; f < totalFrames; f++) {
                int frameByteOffset = f * bytesPerFrame;
                float mono;

                if (channels == 1) {
                    short s = (short) ((allPcmBytes[frameByteOffset] & 0xFF) | (allPcmBytes[frameByteOffset + 1] << 8));
                    mono = s / 32768.0f;
                } else if (channels == 2) {
                    short sLeft = (short) ((allPcmBytes[frameByteOffset] & 0xFF) | (allPcmBytes[frameByteOffset + 1] << 8));
                    short sRight = (short) ((allPcmBytes[frameByteOffset + 2] & 0xFF) | (allPcmBytes[frameByteOffset + 3] << 8));
                    mono = ((sLeft / 32768.0f) + (sRight / 32768.0f)) * 0.5f;
                } else {
                    float sum = 0.0f;
                    for (int c = 0; c < channels; c++) {
                        int off = frameByteOffset + (c * 2);
                        short s = (short) ((allPcmBytes[off] & 0xFF) | (allPcmBytes[off + 1] << 8));
                        sum += s / 32768.0f;
                    }
                    mono = sum / channels;
                }

                if (mono < sliceMin) sliceMin = mono;
                if (mono > sliceMax) sliceMax = mono;
                framesAccumulatedInSlice++;

                if (framesAccumulatedInSlice >= framesPerSlice) {
                    peaks[currentSlice] = createPeak(sliceMin, sliceMax);
                    currentSlice++;
                    framesAccumulatedInSlice = 0;
                    sliceMin = 1.0f;
                    sliceMax = -1.0f;
                    if (currentSlice >= numSlices) break;
                }
            }

            while (currentSlice < numSlices) {
                peaks[currentSlice] = (sliceMin <= sliceMax) ? createPeak(sliceMin, sliceMax) : AudioPeak.ZERO;
                currentSlice++;
                sliceMin = 1.0f;
                sliceMax = -1.0f;
            }

            return new ExtractedWaveform(peaks, totalDurationMs, sampleRate, channels);
        } catch (Exception e) {
            // Not a supported WAV format
            return null;
        }
    }

    private static int calculateSliceCount(long durationMs, int requestedSlices) {
        if (requestedSlices > 0) {
            return requestedSlices;
        }
        double seconds = Math.max(1.0, durationMs / 1000.0);
        int computed = (int) (seconds * DEFAULT_PEAKS_PER_SECOND);
        return Math.max(MIN_TOTAL_SLICES, computed);
    }

    private static AudioPeak createPeak(float min, float max) {
        if (min > max) {
            return AudioPeak.ZERO;
        }
        // Clamp to normalized [-1.0, 1.0]
        float clampedMin = Math.max(-1.0f, Math.min(1.0f, min));
        float clampedMax = Math.max(-1.0f, Math.min(1.0f, max));
        return new AudioPeak(clampedMin, clampedMax);
    }
}
