package net.dandare21.fracturedutils.sound.event;

import net.dandare21.fracturedutils.FracturedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

@OnlyIn(Dist.CLIENT)
public class ModAudioPlayer {

    private static final ModAudioPlayer INSTANCE = new ModAudioPlayer();

    private static final int BUFFER_SIZE = 4096 * 8; // 32KB PCM buffer chunk
    private static final int NUM_BUFFERS = 3;

    private ByteBuffer rawOggBuffer = null;
    private long vorbisDecoder = 0;
    private int openAlSource = 0;
    private int[] openAlBuffers = null;

    private int channels = 2;
    private int sampleRate = 44100;

    private boolean isPlaying = false;
    private boolean looping = false;
    private String currentTrackId = "";
    private String customChannel = "eventmusic";
    private float targetVolume = 1.0f;
    private float currentVolume = 1.0f;
    private float pitch = 1.0f;
    private volatile float currentRmsEnergy = 0.0f;
    private volatile float currentBassEnergy = 0.0f;

    // 2nd-order IIR Low-Pass Filter state (fc ~ 160Hz for sub-bass & kick drums)
    private double lpB0 = 0.0, lpB1 = 0.0, lpB2 = 0.0;
    private double lpA1 = 0.0, lpA2 = 0.0;
    private double lpX1 = 0.0, lpX2 = 0.0;
    private double lpY1 = 0.0, lpY2 = 0.0;
    private int filterSampleRate = 0;

    // Hardware-synchronized playback timeline & energy tracking
    private static final int SLICE_SIZE = 1024; // ~23ms at 44.1kHz
    private static class BufferMeta {
        final int bufferId;
        final long startFrame;
        final int frameCount;
        BufferMeta(int bufferId, long startFrame, int frameCount) {
            this.bufferId = bufferId;
            this.startFrame = startFrame;
            this.frameCount = frameCount;
        }
    }
    private static class EnergySlice {
        final long frameStart;
        final long frameEnd;
        final float rms;
        final float bass;
        EnergySlice(long frameStart, long frameEnd, float rms, float bass) {
            this.frameStart = frameStart;
            this.frameEnd = frameEnd;
            this.rms = rms;
            this.bass = bass;
        }
    }
    private final Deque<BufferMeta> activeBufferQueue = new ArrayDeque<>();
    private final Deque<EnergySlice> energySlices = new ArrayDeque<>();
    private long currentFrameCursor = 0L;
    private volatile long currentPlaybackFrame = 0L;

    // Fading state
    private boolean isFading = false;
    private float fadeStartVolume = 1.0f;
    private float fadeTargetVolume = 1.0f;
    private int fadeDurationMs = 0;
    private long fadeStartTimeMs = 0;
    private boolean stopOnFadeOutComplete = false;

    public static ModAudioPlayer getInstance() {
        return INSTANCE;
    }

    public synchronized void playTrack(byte[] oggBytes, String trackId, String channel, float volume, float pitch, boolean looping, int fadeDurationMs, long startOffsetMs) {
        if (oggBytes == null || oggBytes.length == 0) {
            FracturedUtils.LOGGER.error("[ModAudioPlayer] Cannot play track '{}': Ogg bytes are null or empty", trackId);
            return;
        }

        stopTrackImmediately();

        this.currentTrackId = trackId;
        this.customChannel = channel != null ? channel : "eventmusic";
        this.targetVolume = volume;
        this.pitch = pitch;
        this.looping = looping;

        try {
            // 1. Allocate Direct ByteBuffer for STBVorbis
            this.rawOggBuffer = MemoryUtil.memAlloc(oggBytes.length);
            this.rawOggBuffer.put(oggBytes);
            this.rawOggBuffer.flip();

            // 2. Open Vorbis Decoder
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer error = stack.mallocInt(1);
                this.vorbisDecoder = STBVorbis.stb_vorbis_open_memory(rawOggBuffer, error, null);
                if (this.vorbisDecoder == 0) {
                    FracturedUtils.LOGGER.error("[ModAudioPlayer] Failed to open Vorbis decoder for track '{}' (error: {})", trackId, error.get(0));
                    freeOggBuffer();
                    return;
                }

                STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                STBVorbis.stb_vorbis_get_info(vorbisDecoder, info);
                this.channels = info.channels();
                this.sampleRate = info.sample_rate();
            }

            // 3. Seek to requested start offset in samples
            if (startOffsetMs > 50L) {
                int targetSample = (int) ((startOffsetMs / 1000.0) * sampleRate);
                STBVorbis.stb_vorbis_seek(vorbisDecoder, targetSample);
                FracturedUtils.LOGGER.info("[ModAudioPlayer] Seeked STBVorbis decoder to sample {} ({}ms) for track '{}'", targetSample, startOffsetMs, trackId);
            }

            // 4. Generate OpenAL Source & Buffers
            this.openAlSource = AL10.alGenSources();
            this.openAlBuffers = new int[NUM_BUFFERS];
            for (int i = 0; i < NUM_BUFFERS; i++) {
                openAlBuffers[i] = AL10.alGenBuffers();
            }

            // 5. Pre-fill OpenAL queued buffers
            this.activeBufferQueue.clear();
            this.energySlices.clear();
            this.currentFrameCursor = (startOffsetMs > 50L) ? (long) ((startOffsetMs / 1000.0) * sampleRate) : 0L;
            this.currentPlaybackFrame = this.currentFrameCursor;

            int format = (channels == 1) ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            ShortBuffer pcmBuffer = BufferUtils.createShortBuffer(BUFFER_SIZE);

            int queuedCount = 0;
            for (int i = 0; i < NUM_BUFFERS; i++) {
                pcmBuffer.clear();
                int samplesRead = STBVorbis.stb_vorbis_get_samples_short_interleaved(vorbisDecoder, channels, pcmBuffer);
                if (samplesRead > 0) {
                    pcmBuffer.limit(samplesRead * channels);
                    recordEnergySlices(pcmBuffer, samplesRead, currentFrameCursor);
                    AL10.alBufferData(openAlBuffers[i], format, pcmBuffer, sampleRate);
                    AL10.alSourceQueueBuffers(openAlSource, openAlBuffers[i]);
                    activeBufferQueue.add(new BufferMeta(openAlBuffers[i], currentFrameCursor, samplesRead));
                    currentFrameCursor += samplesRead;
                    queuedCount++;
                }
            }

            if (queuedCount == 0) {
                FracturedUtils.LOGGER.warn("[ModAudioPlayer] Could not queue initial audio buffers for track '{}'", trackId);
                stopTrackImmediately();
                return;
            }

            // 6. Set OpenAL Source Properties
            AL10.alSourcef(openAlSource, AL10.AL_PITCH, pitch);
            updateSourceGain();

            // 7. Initialize Fading & Play
            if (fadeDurationMs > 0) {
                this.isFading = true;
                this.fadeStartVolume = 0.001f;
                this.fadeTargetVolume = volume;
                this.currentVolume = 0.001f;
                this.fadeDurationMs = fadeDurationMs;
                this.fadeStartTimeMs = System.currentTimeMillis();
                this.stopOnFadeOutComplete = false;
            } else {
                this.isFading = false;
                this.currentVolume = volume;
            }

            updateSourceGain();
            AL10.alSourcePlay(openAlSource);
            this.isPlaying = true;

            FracturedUtils.LOGGER.info("[ModAudioPlayer] Successfully playing track '{}' (sampleRate: {}Hz, channels: {}, offset: {}ms)", trackId, sampleRate, channels, startOffsetMs);

        } catch (Exception e) {
            FracturedUtils.LOGGER.error("[ModAudioPlayer] Exception initializing playback for track '" + trackId + "'", e);
            stopTrackImmediately();
        }
    }

    public synchronized void tick() {
        if (!isPlaying || openAlSource == 0 || vorbisDecoder == 0) return;

        // 1. Update Volume Fading
        if (isFading && fadeDurationMs > 0) {
            long elapsed = System.currentTimeMillis() - fadeStartTimeMs;
            float progress = Math.min(1.0f, (float) elapsed / fadeDurationMs);
            this.currentVolume = fadeStartVolume + (fadeTargetVolume - fadeStartVolume) * progress;

            if (progress >= 1.0f) {
                this.isFading = false;
                if (stopOnFadeOutComplete) {
                    stopTrackImmediately();
                    return;
                }
            }
        }

        // 2. Update OpenAL Source Gain with Master & Custom Slider Scaling
        updateSourceGain();

        // 3. Process & Refill OpenAL Buffers
        try {
            int processed = AL10.alGetSourcei(openAlSource, AL10.AL_BUFFERS_PROCESSED);
            int format = (channels == 1) ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            ShortBuffer pcmBuffer = BufferUtils.createShortBuffer(BUFFER_SIZE);

            while (processed > 0) {
                int bufferId = AL10.alSourceUnqueueBuffers(openAlSource);
                processed--;
                activeBufferQueue.poll();

                pcmBuffer.clear();
                int samplesRead = STBVorbis.stb_vorbis_get_samples_short_interleaved(vorbisDecoder, channels, pcmBuffer);

                if (samplesRead <= 0 && looping) {
                    // Loop back to start
                    STBVorbis.stb_vorbis_seek(vorbisDecoder, 0);
                    pcmBuffer.clear();
                    samplesRead = STBVorbis.stb_vorbis_get_samples_short_interleaved(vorbisDecoder, channels, pcmBuffer);
                    currentFrameCursor = 0L;
                }

                if (samplesRead > 0) {
                    pcmBuffer.limit(samplesRead * channels);
                    recordEnergySlices(pcmBuffer, samplesRead, currentFrameCursor);
                    AL10.alBufferData(bufferId, format, pcmBuffer, sampleRate);
                    AL10.alSourceQueueBuffers(openAlSource, bufferId);
                    activeBufferQueue.add(new BufferMeta(bufferId, currentFrameCursor, samplesRead));
                    currentFrameCursor += samplesRead;
                }
            }

            // Ensure source remains playing if queued buffers are active
            int state = AL10.alGetSourcei(openAlSource, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING && AL10.alGetSourcei(openAlSource, AL10.AL_BUFFERS_QUEUED) > 0) {
                AL10.alSourcePlay(openAlSource);
            } else if (state != AL10.AL_PLAYING && !looping) {
                // End of track reached
                stopTrackImmediately();
                return;
            }

            // Update energy synchronized to actual hardware playback frame
            updateCurrentPlaybackEnergy();

        } catch (Exception e) {
            FracturedUtils.LOGGER.error("[ModAudioPlayer] Error in audio player tick", e);
        }
    }

    private void updateSourceGain() {
        if (openAlSource == 0) return;
        Minecraft mc = Minecraft.getInstance();
        float masterVol = mc != null && mc.options != null ? mc.options.getSoundSourceVolume(SoundSource.MASTER) : 1.0f;
        float channelVol = ClientAudioConfig.getChannelVolume(customChannel);
        float effectiveGain = Math.max(0.0f, currentVolume * channelVol * masterVol);
        AL10.alSourcef(openAlSource, AL10.AL_GAIN, effectiveGain);
    }

    public synchronized void seekTrack(long offsetMs) {
        if (!isPlaying || vorbisDecoder == 0 || openAlSource == 0) return;

        try {
            int targetSample = (int) ((offsetMs / 1000.0) * sampleRate);
            STBVorbis.stb_vorbis_seek(vorbisDecoder, targetSample);

            // Flush active OpenAL queued buffers
            AL10.alSourceStop(openAlSource);
            int queued = AL10.alGetSourcei(openAlSource, AL10.AL_BUFFERS_QUEUED);
            while (queued > 0) {
                AL10.alSourceUnqueueBuffers(openAlSource);
                queued--;
            }

            // Re-fill OpenAL queued buffers
            this.activeBufferQueue.clear();
            this.energySlices.clear();
            this.currentFrameCursor = (long) ((offsetMs / 1000.0) * sampleRate);
            this.currentPlaybackFrame = this.currentFrameCursor;

            int format = (channels == 1) ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            ShortBuffer pcmBuffer = BufferUtils.createShortBuffer(BUFFER_SIZE);

            int count = 0;
            for (int i = 0; i < NUM_BUFFERS; i++) {
                pcmBuffer.clear();
                int samplesRead = STBVorbis.stb_vorbis_get_samples_short_interleaved(vorbisDecoder, channels, pcmBuffer);
                if (samplesRead > 0) {
                    pcmBuffer.limit(samplesRead * channels);
                    recordEnergySlices(pcmBuffer, samplesRead, currentFrameCursor);
                    AL10.alBufferData(openAlBuffers[i], format, pcmBuffer, sampleRate);
                    AL10.alSourceQueueBuffers(openAlSource, openAlBuffers[i]);
                    activeBufferQueue.add(new BufferMeta(openAlBuffers[i], currentFrameCursor, samplesRead));
                    currentFrameCursor += samplesRead;
                    count++;
                }
            }

            if (count > 0) {
                updateSourceGain();
                AL10.alSourcePlay(openAlSource);
                FracturedUtils.LOGGER.info("[ModAudioPlayer] Re-synced track '{}' to offset {}ms (sample {})", currentTrackId, offsetMs, targetSample);
            }
        } catch (Exception e) {
            FracturedUtils.LOGGER.error("[ModAudioPlayer] Error seeking track", e);
        }
    }

    public synchronized void stopTrack(int fadeDurationMs) {
        if (!isPlaying) return;

        if (fadeDurationMs <= 0) {
            stopTrackImmediately();
        } else {
            this.isFading = true;
            this.fadeStartVolume = this.currentVolume;
            this.fadeTargetVolume = 0.0f;
            this.fadeDurationMs = fadeDurationMs;
            this.fadeStartTimeMs = System.currentTimeMillis();
            this.stopOnFadeOutComplete = true;
        }
    }

    public synchronized void stopTrackImmediately() {
        this.isPlaying = false;
        this.isFading = false;
        this.activeBufferQueue.clear();
        this.energySlices.clear();
        this.currentFrameCursor = 0L;
        this.currentPlaybackFrame = 0L;
        this.currentRmsEnergy = 0.0f;
        this.currentBassEnergy = 0.0f;
        this.lpX1 = 0.0;
        this.lpX2 = 0.0;
        this.lpY1 = 0.0;
        this.lpY2 = 0.0;

        if (openAlSource != 0) {
            try {
                AL10.alSourceStop(openAlSource);
                int queued = AL10.alGetSourcei(openAlSource, AL10.AL_BUFFERS_QUEUED);
                while (queued > 0) {
                    AL10.alSourceUnqueueBuffers(openAlSource);
                    queued--;
                }
                AL10.alDeleteSources(openAlSource);
            } catch (Exception ignored) {
            }
            openAlSource = 0;
        }

        if (openAlBuffers != null) {
            for (int buf : openAlBuffers) {
                if (buf != 0) {
                    try {
                        AL10.alDeleteBuffers(buf);
                    } catch (Exception ignored) {
                    }
                }
            }
            openAlBuffers = null;
        }

        if (vorbisDecoder != 0) {
            try {
                STBVorbis.stb_vorbis_close(vorbisDecoder);
            } catch (Exception ignored) {
            }
            vorbisDecoder = 0;
        }

        freeOggBuffer();
        this.currentTrackId = "";
    }

    private void freeOggBuffer() {
        if (rawOggBuffer != null) {
            try {
                MemoryUtil.memFree(rawOggBuffer);
            } catch (Exception ignored) {
            }
            rawOggBuffer = null;
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public String getCurrentTrackId() {
        return currentTrackId;
    }

    private void updateFilterCoefficients(int rate) {
        if (rate <= 0) rate = 44100;
        if (this.filterSampleRate == rate) return;
        this.filterSampleRate = rate;

        double cutoff = 160.0; // 160 Hz cutoff isolating sub-bass and kick drums
        double q = 0.7071; // Butterworth Q factor
        double omega = 2.0 * Math.PI * cutoff / rate;
        double sinOmega = Math.sin(omega);
        double cosOmega = Math.cos(omega);
        double alpha = sinOmega / (2.0 * q);

        double b0 = (1.0 - cosOmega) / 2.0;
        double b1 = 1.0 - cosOmega;
        double b2 = (1.0 - cosOmega) / 2.0;
        double a0 = 1.0 + alpha;
        double a1 = -2.0 * cosOmega;
        double a2 = 1.0 - alpha;

        this.lpB0 = b0 / a0;
        this.lpB1 = b1 / a0;
        this.lpB2 = b2 / a0;
        this.lpA1 = a1 / a0;
        this.lpA2 = a2 / a0;

        this.lpX1 = 0.0;
        this.lpX2 = 0.0;
        this.lpY1 = 0.0;
        this.lpY2 = 0.0;
    }

    private void recordEnergySlices(ShortBuffer buffer, int samplesRead, long startFrame) {
        if (samplesRead <= 0 || buffer == null) return;
        updateFilterCoefficients(this.sampleRate);

        int ch = Math.max(1, this.channels);
        int totalFrames = samplesRead;
        int frameIndex = 0;

        while (frameIndex < totalFrames) {
            int sliceFrames = Math.min(SLICE_SIZE, totalFrames - frameIndex);
            long sliceStartFrame = startFrame + frameIndex;
            long sliceEndFrame = sliceStartFrame + sliceFrames;

            long sumSq = 0;
            double bassSumSq = 0;

            for (int f = 0; f < sliceFrames; f++) {
                int baseIndex = (frameIndex + f) * ch;
                double monoSample = 0.0;
                for (int c = 0; c < ch; c++) {
                    short s = buffer.get(baseIndex + c);
                    sumSq += (long) s * s;
                    monoSample += s;
                }
                monoSample /= ch;

                // Direct Form I IIR Biquad Low-Pass Filter
                double y = lpB0 * monoSample + lpB1 * lpX1 + lpB2 * lpX2 - lpA1 * lpY1 - lpA2 * lpY2;
                lpX2 = lpX1;
                lpX1 = monoSample;
                lpY2 = lpY1;
                lpY1 = y;

                bassSumSq += y * y;
            }

            float sliceRms = (float) Math.sqrt((double) sumSq / (sliceFrames * ch)) / 32768.0F;
            float sliceBass = (float) Math.sqrt(bassSumSq / sliceFrames) / 32768.0F;

            energySlices.addLast(new EnergySlice(sliceStartFrame, sliceEndFrame, sliceRms, sliceBass));
            frameIndex += sliceFrames;
        }
    }

    public synchronized void updateCurrentPlaybackEnergy() {
        if (!isPlaying || openAlSource == 0 || activeBufferQueue.isEmpty()) {
            this.currentRmsEnergy = 0.0F;
            this.currentBassEnergy = 0.0F;
            this.currentPlaybackFrame = 0L;
            return;
        }

        BufferMeta currentBuf = activeBufferQueue.peek();
        if (currentBuf == null) return;

        int sampleOffset = 0;
        try {
            sampleOffset = AL10.alGetSourcei(openAlSource, AL11.AL_SAMPLE_OFFSET);
        } catch (Exception ignored) {
        }
        if (sampleOffset < 0 || sampleOffset > currentBuf.frameCount) {
            sampleOffset = 0;
        }

        long playFrame = currentBuf.startFrame + sampleOffset;
        this.currentPlaybackFrame = playFrame;

        // Prune old slices older than playFrame - 44100 (1 second behind)
        while (!energySlices.isEmpty() && energySlices.peekFirst().frameEnd < (playFrame - 44100L)) {
            energySlices.pollFirst();
        }

        // Find slice for current playing frame
        EnergySlice matched = null;
        for (EnergySlice slice : energySlices) {
            if (playFrame >= slice.frameStart && playFrame < slice.frameEnd) {
                matched = slice;
                break;
            }
        }

        if (matched != null) {
            this.currentRmsEnergy = matched.rms;
            this.currentBassEnergy = matched.bass;
        }
    }

    public long getCurrentPlaybackFrame() {
        return currentPlaybackFrame;
    }

    public double getCurrentPlaybackSeconds() {
        if (!isPlaying || sampleRate <= 0) return 0.0;
        return (double) currentPlaybackFrame / (double) sampleRate;
    }

    public float getCurrentRmsEnergy() {
        return currentRmsEnergy;
    }

    public float getCurrentBassEnergy() {
        return currentBassEnergy;
    }
}
