package net.dandare21.fracturedutils.sound.event;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.config.ServerConfig;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.S2CAudioPackSyncPacket;
import net.dandare21.fracturedutils.network.packet.S2CAudioSyncTimePacket;
import net.dandare21.fracturedutils.network.packet.S2CPlayEventAudioPacket;
import net.dandare21.fracturedutils.network.packet.S2CPlayEventAudioPacket.PlaybackMode;
import net.dandare21.fracturedutils.network.packet.S2CStopEventAudioPacket;
import net.dandare21.fracturedutils.sound.ModSoundSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundResourcePackPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EventAudioManager {

    public enum PackStatus {
        UNVERIFIED,
        ACCEPTED,
        READY_FOR_EVENT,
        DECLINED,
        FAILED_DOWNLOAD
    }

    private static final EventAudioManager INSTANCE = new EventAudioManager();

    private final EventMusicPackBuilder packBuilder = new EventMusicPackBuilder();
    private final Map<UUID, PackStatus> playerStatusMap = new ConcurrentHashMap<>();

    private boolean isPlaying = false;
    private String currentSoundId = "";
    private SoundSource currentCategory = ModSoundSources.EVENT_MUSIC;
    private float currentVolume = 1.0f;
    private float currentPitch = 1.0f;
    private int currentFadeDurationMs = 1000;
    private long playbackStartTimeMs = 0;
    private PlaybackMode currentMode = PlaybackMode.SERVER_CONTROLLED;
    private boolean currentLooping = false;
    private int currentSyncThresholdMs = 2000;
    private final Set<UUID> targetPlayerUuids = new HashSet<>();

    private long tickCounter = 0;

    public static EventAudioManager getInstance() {
        return INSTANCE;
    }

    public void initServer(MinecraftServer server) {
        String namespace = ServerConfig.getEventAudioNamespace();
        packBuilder.buildPack(namespace);
        FracturedUtils.LOGGER.info("[EventAudioManager] Initialized event music pack builder on server. SHA1: {}", packBuilder.getSha1Hex());
    }

    public void tick(MinecraftServer server) {
        if (server == null || !isPlaying) return;
        tickCounter++;

        if (currentMode == PlaybackMode.SERVER_CONTROLLED && tickCounter % 20 == 0) {
            S2CAudioSyncTimePacket syncPacket = new S2CAudioSyncTimePacket(
                    currentSoundId, playbackStartTimeMs, currentSyncThresholdMs, currentLooping
            );
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player != null) {
                    if (targetPlayerUuids.isEmpty() || targetPlayerUuids.contains(player.getUUID())) {
                        ModMessages.sendToPlayer(syncPacket, player);
                    }
                }
            }
        }
    }

    public void onPlayerJoin(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        playerStatusMap.put(uuid, PackStatus.READY_FOR_EVENT);
        promptPlayerWithPack(player);

        // Always sync active mid-event music to joining player immediately!
        if (isPlaying) {
            syncMidEventAudioToPlayer(uuid);
        }
    }

    public void promptPlayerWithPack(ServerPlayer player) {
        if (player == null) return;
        try {
            String sha1 = packBuilder.getSha1Hex();
            ModMessages.sendToPlayer(new S2CAudioPackSyncPacket(sha1, ServerConfig.isEventAudioRequirePack(), packBuilder.getRegisteredTracks()), player);
            FracturedUtils.LOGGER.info("[EventAudioManager] Sent audio pack SHA-1 sync packet to player {} (SHA1: {})", player.getScoreboardName(), sha1);
        } catch (Exception e) {
            FracturedUtils.LOGGER.error("[EventAudioManager] Failed to send audio pack sync packet to player {}", player.getScoreboardName(), e);
        }
    }

    public void updatePlayerPackStatus(ServerPlayer player, ServerboundResourcePackPacket.Action action) {
        if (player != null) {
            updatePlayerPackStatus(player.getUUID(), action);
        }
    }

    public void updatePlayerPackStatus(UUID playerUuid, ServerboundResourcePackPacket.Action action) {
        if (playerUuid == null || action == null) return;

        PackStatus newStatus = switch (action) {
            case ACCEPTED -> PackStatus.ACCEPTED;
            case SUCCESSFULLY_LOADED -> PackStatus.READY_FOR_EVENT;
            case DECLINED -> PackStatus.DECLINED;
            case FAILED_DOWNLOAD -> PackStatus.FAILED_DOWNLOAD;
            default -> PackStatus.UNVERIFIED;
        };

        playerStatusMap.put(playerUuid, newStatus);
        FracturedUtils.LOGGER.info("[EventAudioManager] Player {} audio pack status updated to {}", playerUuid, newStatus);

        if (isPlaying) {
            syncMidEventAudioToPlayer(playerUuid);
        }
    }

    private void syncMidEventAudioToPlayer(UUID playerUuid) {
        if (!isPlaying) return;
        if (!targetPlayerUuids.isEmpty() && !targetPlayerUuids.contains(playerUuid)) return;

        net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().execute(() -> {
            ServerPlayer player = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerUuid);
            if (player != null) {
                long offsetMs = 0L;
                if (currentMode == PlaybackMode.SERVER_CONTROLLED) {
                    offsetMs = Math.max(0L, System.currentTimeMillis() - playbackStartTimeMs);
                } else {
                    offsetMs = 0L;
                }

                ModMessages.sendToPlayer(new S2CPlayEventAudioPacket(
                        currentSoundId, currentCategory, currentVolume, currentPitch, currentFadeDurationMs, offsetMs, false, currentMode, currentLooping, currentSyncThresholdMs
                ), player);
                FracturedUtils.LOGGER.info("[EventAudioManager] Synchronized mid-event audio to player {} (mode: {}, offset: {}ms)", player.getScoreboardName(), currentMode, offsetMs);
            }
        });
    }

    public synchronized void playAudio(MinecraftServer server, String soundEventId, SoundSource category, Collection<ServerPlayer> targets, float volume, float pitch, int fadeDurationMs, PlaybackMode mode, boolean looping, int syncThresholdMs) {
        playAudio(server, soundEventId, category, targets, volume, pitch, fadeDurationMs, mode, looping, syncThresholdMs, 0L);
    }

    public synchronized void playAudio(MinecraftServer server, String soundEventId, SoundSource category, Collection<ServerPlayer> targets, float volume, float pitch, int fadeDurationMs, PlaybackMode mode, boolean looping, int syncThresholdMs, long startOffsetMs) {
        if (server == null || soundEventId == null || soundEventId.trim().isEmpty()) return;

        String namespace = ServerConfig.getEventAudioNamespace();
        String fullSoundId = soundEventId.contains(":") ? soundEventId.trim() : namespace + ":" + soundEventId.trim();

        long safeOffset = Math.max(0L, startOffsetMs);
        this.isPlaying = true;
        this.currentSoundId = fullSoundId;
        this.currentCategory = category != null ? category : ModSoundSources.EVENT_MUSIC;
        this.currentVolume = volume;
        this.currentPitch = pitch;
        this.currentFadeDurationMs = fadeDurationMs;
        this.playbackStartTimeMs = System.currentTimeMillis() - safeOffset;
        this.currentMode = mode != null ? mode : PlaybackMode.SERVER_CONTROLLED;
        this.currentLooping = looping;
        this.currentSyncThresholdMs = syncThresholdMs;

        this.targetPlayerUuids.clear();
        if (targets != null && !targets.isEmpty()) {
            for (ServerPlayer player : targets) {
                if (player != null) {
                    this.targetPlayerUuids.add(player.getUUID());
                }
            }
        }

        S2CPlayEventAudioPacket packet = new S2CPlayEventAudioPacket(
                fullSoundId, currentCategory, volume, pitch, fadeDurationMs, safeOffset, true, currentMode, looping, syncThresholdMs
        );

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null) {
                if (targetPlayerUuids.isEmpty() || targetPlayerUuids.contains(player.getUUID())) {
                    ModMessages.sendToPlayer(packet, player);
                }
            }
        }

        FracturedUtils.LOGGER.info("[EventAudioManager] Started event audio '{}' (channel: {}, mode: {}, loop: {}, vol: {}, pitch: {}, offset: {}ms)",
                fullSoundId, currentCategory.getName(), currentMode, looping, volume, pitch, safeOffset);
    }

    public synchronized void stopAudio(MinecraftServer server, Collection<ServerPlayer> targets, int fadeDurationMs) {
        this.isPlaying = false;
        this.currentSoundId = "";
        this.targetPlayerUuids.clear();

        Collection<ServerPlayer> recipients = (targets != null && !targets.isEmpty()) ? targets : (server != null ? server.getPlayerList().getPlayers() : Collections.emptyList());
        for (ServerPlayer target : recipients) {
            if (target != null) {
                ModMessages.sendToPlayer(new S2CStopEventAudioPacket(fadeDurationMs), target);
            }
        }
        FracturedUtils.LOGGER.info("[EventAudioManager] Broadcast event audio stop (fade: {}ms)", fadeDurationMs);
    }

    public synchronized void reloadPacks(MinecraftServer server) {
        if (server == null) return;
        String namespace = ServerConfig.getEventAudioNamespace();
        packBuilder.buildPack(namespace);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null) {
                promptPlayerWithPack(player);
            }
        }
    }

    public void onServerStarting(MinecraftServer server) {
        initServer(server);
    }

    public void onServerStopping(MinecraftServer server) {
        stopAudio(server, null, 0);
    }

    public List<String> getAvailableTrackSuggestions() {
        if (packBuilder.getRegisteredTracks().isEmpty()) {
            packBuilder.buildPack(ServerConfig.getEventAudioNamespace());
        }
        List<String> suggestions = packBuilder.getAvailableTrackSuggestions(ServerConfig.getEventAudioNamespace());
        if (suggestions.isEmpty()) {
            packBuilder.buildPack(ServerConfig.getEventAudioNamespace());
            suggestions = packBuilder.getAvailableTrackSuggestions(ServerConfig.getEventAudioNamespace());
        }
        return suggestions;
    }

    public PackStatus getPlayerStatus(UUID playerUuid) {
        return playerUuid != null ? playerStatusMap.getOrDefault(playerUuid, PackStatus.UNVERIFIED) : PackStatus.UNVERIFIED;
    }

    public boolean isPlayerReady(UUID playerUuid) {
        return true;
    }

    public EventMusicPackBuilder getPackBuilder() {
        return packBuilder;
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}
