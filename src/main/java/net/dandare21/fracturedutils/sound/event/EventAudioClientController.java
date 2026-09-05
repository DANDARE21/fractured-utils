package net.dandare21.fracturedutils.sound.event;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.network.packet.S2CPlayEventAudioPacket.PlaybackMode;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = FracturedUtils.MOD_ID, value = Dist.CLIENT)
public class EventAudioClientController {

    private static final EventAudioClientController INSTANCE = new EventAudioClientController();

    private PlaybackMode currentMode = PlaybackMode.FIRE_AND_FORGET;
    private long localPlaybackStartTimeMs = 0;
    private int currentSyncThresholdMs = 2000;
    private String currentSoundEventId = "";

    public static EventAudioClientController getInstance() {
        return INSTANCE;
    }

    public synchronized void playAudio(String soundEventId, SoundSource category, float volume, float pitch, int fadeDurationMs, long startOffsetMs, boolean stopPrevious, PlaybackMode mode, boolean looping, int syncThresholdMs) {
        if (soundEventId == null || soundEventId.trim().isEmpty()) return;

        if (stopPrevious && ModAudioPlayer.getInstance().isPlaying()) {
            ModAudioPlayer.getInstance().stopTrack(0);
        }

        byte[] oggBytes = ClientAudioPackManager.getInstance().getTrackBytes(soundEventId);
        if (oggBytes == null || oggBytes.length == 0) {
            FracturedUtils.LOGGER.error("[EventAudioClientController] Could not find track bytes for '{}'", soundEventId);
            return;
        }

        String customChannel = (category != null && category.getName().toLowerCase().contains("ambien")) ? "eventambience" : "eventmusic";

        this.currentSoundEventId = soundEventId;
        this.currentMode = mode;
        this.currentSyncThresholdMs = syncThresholdMs;
        this.localPlaybackStartTimeMs = System.currentTimeMillis() - Math.max(0L, startOffsetMs);

        ModAudioPlayer.getInstance().playTrack(oggBytes, soundEventId, customChannel, volume, pitch, looping, fadeDurationMs, startOffsetMs);
        FracturedUtils.LOGGER.info("[EventAudioClientController] Playing track '{}' via ModAudioPlayer (channel: {}, mode: {}, loop: {}, offset: {}ms, fade: {}ms)",
                soundEventId, customChannel, mode, looping, startOffsetMs, fadeDurationMs);
    }

    public synchronized void stopAudio(int fadeDurationMs) {
        ModAudioPlayer.getInstance().stopTrack(fadeDurationMs);
        currentSoundEventId = "";
    }

    public synchronized void stopAudioImmediately() {
        ModAudioPlayer.getInstance().stopTrackImmediately();
        currentSoundEventId = "";
    }

    public synchronized void seekAudio(long offsetMs) {
        this.localPlaybackStartTimeMs = System.currentTimeMillis() - Math.max(0L, offsetMs);
        if (ModAudioPlayer.getInstance().isPlaying()) {
            ModAudioPlayer.getInstance().seekTrack(offsetMs);
        }
    }

    public boolean isPlaying() {
        return ModAudioPlayer.getInstance().isPlaying();
    }

    public synchronized void handleServerSyncHeartbeat(String soundEventId, long serverStartTimeMs, int syncThresholdMs, boolean looping) {
        if (currentMode != PlaybackMode.SERVER_CONTROLLED) return;
        if (!ModAudioPlayer.getInstance().isPlaying()) return;

        long expectedPos = System.currentTimeMillis() - serverStartTimeMs;
        long clientPos = System.currentTimeMillis() - localPlaybackStartTimeMs;
        long drift = Math.abs(clientPos - expectedPos);

        if (drift > syncThresholdMs) {
            FracturedUtils.LOGGER.info("[EventAudioClientController] Audio drift detected ({}ms > threshold {}ms). Re-syncing ModAudioPlayer track position...", drift, syncThresholdMs);
            this.localPlaybackStartTimeMs = serverStartTimeMs;
            ModAudioPlayer.getInstance().seekTrack(expectedPos);
        }
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        FracturedUtils.LOGGER.info("[EventAudioClientController] Client logging out, stopping event audio immediately.");
        getInstance().stopAudioImmediately();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                if (ModAudioPlayer.getInstance().isPlaying()) {
                    ModAudioPlayer.getInstance().stopTrackImmediately();
                }
            } else {
                ModAudioPlayer.getInstance().tick();
            }
        }
    }
}
