package net.dandare21.fracturedutils.checkpoint;

import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.S2CSyncDownedPacket;
import net.dandare21.fracturedutils.orchestrator.SequenceInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CheckpointManager {
    private static final CheckpointManager INSTANCE = new CheckpointManager();

    private SequenceInstance activeSequence = null;
    private int checkpointIndex = -1;
    private double spawnX = 0.0;
    private double spawnY = 64.0;
    private double spawnZ = 0.0;
    private float yaw = 0.0f;
    private float pitch = 0.0f;
    private String label = "";
    private String targetSelector = "@a";

    private final Set<UUID> downedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Integer> reviveProgress = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> reviverMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastReviveAttemptTick = new ConcurrentHashMap<>();

    private int teamWipeTimerTicks = -1;
    private List<ServerPlayer> pendingWipePlayers = new ArrayList<>();

    private CheckpointManager() {}

    public static CheckpointManager getInstance() {
        return INSTANCE;
    }

    public synchronized void setActiveCheckpoint(SequenceInstance instance, int actionIndex, double x, double y, double z, float yaw, float pitch, String label, String targetSelector) {
        this.activeSequence = instance;
        this.checkpointIndex = actionIndex;
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.label = label != null ? label : "";
        this.targetSelector = (targetSelector != null && !targetSelector.isBlank()) ? targetSelector : "@a";
    }

    public synchronized void setActiveCheckpoint(SequenceInstance instance, int actionIndex, double x, double y, double z, float yaw, float pitch, String label) {
        setActiveCheckpoint(instance, actionIndex, x, y, z, yaw, pitch, label, "@a");
    }

    public boolean hasActiveCheckpoint() {
        return activeSequence != null && activeSequence.getState() != net.dandare21.fracturedutils.orchestrator.SequenceState.FINISHED;
    }

    public boolean isPlayerMatchingCheckpoint(MinecraftServer server, ServerPlayer player) {
        if (!hasActiveCheckpoint() || server == null || player == null) return false;
        return net.dandare21.fracturedutils.util.SelectorUtils.isPlayerMatching(server, player, targetSelector);
    }

    public String getTargetSelector() {
        return targetSelector != null && !targetSelector.isBlank() ? targetSelector : "@a";
    }

    public boolean isPlayerDowned(UUID uuid) {
        return downedPlayers.contains(uuid);
    }

    public Set<UUID> getDownedPlayers() {
        return downedPlayers;
    }

    public void setPlayerDowned(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        if (!downedPlayers.contains(uuid)) {
            downedPlayers.add(uuid);
            player.setHealth(1.0f);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 999999, 255, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 999999, 128, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 999999, 255, false, false, false));

            // Notify client HUD and sync to all clients
            ModMessages.sendToAllPlayers(new S2CSyncDownedPacket(player.getUUID(), true, false, 0.0f));
            player.sendSystemMessage(Component.literal("CRITICAL: YOU ARE DOWNED! Wait for a teammate to revive you.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
    }

    public void revivePlayer(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        downedPlayers.remove(uuid);
        reviveProgress.remove(uuid);
        UUID reviverUUID = reviverMap.remove(uuid);
        lastReviveAttemptTick.remove(uuid);

        if (reviverUUID != null && player.getServer() != null) {
            ServerPlayer reviver = player.getServer().getPlayerList().getPlayer(reviverUUID);
            if (reviver != null) {
                ModMessages.sendToPlayer(new S2CSyncDownedPacket(reviverUUID, false, false, 0.0f), reviver);
            }
        }

        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.JUMP);
        player.removeEffect(MobEffects.WEAKNESS);
        player.setHealth(Math.max(10.0f, player.getMaxHealth() / 2.0f));

        if (player.level() != null) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
        }

        ModMessages.sendToAllPlayers(new S2CSyncDownedPacket(player.getUUID(), false, false, 0.0f));
        player.sendSystemMessage(Component.literal("REVIVED: SYSTEMS RESTORED!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    public void recordReviveAttempt(ServerPlayer reviver, ServerPlayer target) {
        if (reviver == null || target == null) return;
        if (!isPlayerDowned(target.getUUID()) || isPlayerDowned(reviver.getUUID())) return;

        UUID targetUUID = target.getUUID();
        UUID reviverUUID = reviver.getUUID();

        reviverMap.put(targetUUID, reviverUUID);
        lastReviveAttemptTick.put(targetUUID, (long) reviver.server.getTickCount());

        int current = reviveProgress.getOrDefault(targetUUID, 0) + 1;
        reviveProgress.put(targetUUID, current);

        float progressFraction = Math.min(1.0f, current / 60.0f);
        ModMessages.sendToAllPlayers(new S2CSyncDownedPacket(targetUUID, true, false, progressFraction));
        ModMessages.sendToPlayer(new S2CSyncDownedPacket(reviverUUID, false, true, progressFraction), reviver);

        if (current >= 60) {
            revivePlayer(target);
            ModMessages.sendToAllPlayers(new S2CSyncDownedPacket(reviverUUID, false, false, 0.0f));
            lastReviveAttemptTick.remove(targetUUID);
        }
    }

    public synchronized void restoreCheckpoint(MinecraftServer server) {
        if (server == null || !hasActiveCheckpoint()) return;

        teamWipeTimerTicks = -1;

        List<ServerPlayer> playersToRestore = new ArrayList<>(pendingWipePlayers);
        if (playersToRestore.isEmpty()) {
            playersToRestore = net.dandare21.fracturedutils.util.SelectorUtils.getTargetPlayers(server, targetSelector);
        }

        for (ServerPlayer player : playersToRestore) {
            if (player != null && player.connection != null) {
                revivePlayer(player);
                player.teleportTo(player.serverLevel(), spawnX, spawnY, spawnZ, yaw, pitch);
                player.setHealth(player.getMaxHealth());
            }
        }

        if (activeSequence != null && checkpointIndex >= 0) {
            activeSequence.setCurrentIndex(checkpointIndex);
            activeSequence.unpause();
        }

        // 1. Despawn any puppets spawned by music sequences
        net.dandare21.fracturedutils.sound.sequence.MusicSequenceManager.getInstance().despawnAllSequencePuppets(server);

        // 2. Stop active music sequences and event audio when restoring checkpoint
        net.dandare21.fracturedutils.sound.sequence.MusicSequenceManager.getInstance().stopAllSequences(server);
        net.dandare21.fracturedutils.sound.event.EventAudioManager.getInstance().stopAudio(server, null, 0);

        downedPlayers.clear();
        reviveProgress.clear();
        reviverMap.clear();
        lastReviveAttemptTick.clear();
        pendingWipePlayers.clear();
    }

    public void tick(MinecraftServer server) {
        if (server == null || !hasActiveCheckpoint()) return;

        if (teamWipeTimerTicks > 0) {
            teamWipeTimerTicks--;
            if (teamWipeTimerTicks == 0) {
                restoreCheckpoint(server);
            }
            return;
        }

        List<ServerPlayer> targetPlayers = net.dandare21.fracturedutils.util.SelectorUtils.getTargetPlayers(server, targetSelector);
        if (targetPlayers.isEmpty()) return;

        List<ServerPlayer> activePlayers = new ArrayList<>();
        for (ServerPlayer p : targetPlayers) {
            if (!p.isSpectator()) {
                activePlayers.add(p);
            }
        }

        if (activePlayers.isEmpty()) return;

        // Maintain downed effects, health lock, and static marker particle
        int downedCount = 0;
        for (ServerPlayer player : activePlayers) {
            if (isPlayerDowned(player.getUUID())) {
                downedCount++;
                if (player.getHealth() < 1.0f && player.isAlive()) {
                    player.setHealth(1.0f);
                }
                // Maintain downed effects and health lock
                if (!player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 999999, 255, false, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.JUMP, 999999, 128, false, false, false));
                }
            }
        }

        // Check team wipe condition: all active non-spectators downed
        if (downedCount >= activePlayers.size()) {
            triggerTeamWipe(server, activePlayers);
            return;
        }

        // Decay revive progress if reviver stepped away or stopped holding right click
        long currentTick = server.getTickCount();
        for (UUID targetUUID : new ArrayList<>(reviveProgress.keySet())) {
            UUID reviverUUID = reviverMap.get(targetUUID);
            ServerPlayer target = server.getPlayerList().getPlayer(targetUUID);
            ServerPlayer reviver = reviverUUID != null ? server.getPlayerList().getPlayer(reviverUUID) : null;
            long lastAttempt = lastReviveAttemptTick.getOrDefault(targetUUID, 0L);

            boolean activelyReviving = false;
            if (target != null && reviver != null && isPlayerDowned(targetUUID) && !isPlayerDowned(reviverUUID)) {
                if (reviver.distanceToSqr(target) <= 9.0 && (currentTick - lastAttempt <= 5)) { // within 3 blocks and right clicking recently
                    activelyReviving = true;
                }
            }

            if (!activelyReviving) {
                // Inform reviver that reviving is inactive
                if (reviver != null) {
                    ModMessages.sendToPlayer(new S2CSyncDownedPacket(reviverUUID, false, false, 0.0f), reviver);
                }

                int p = reviveProgress.getOrDefault(targetUUID, 0);
                if (p > 0) {
                    p = Math.max(0, p - 2);
                    reviveProgress.put(targetUUID, p);
                    float prog = p / 60.0f;
                    if (target != null) {
                        ModMessages.sendToPlayer(new S2CSyncDownedPacket(targetUUID, true, false, prog), target);
                    }
                } else {
                    reviveProgress.remove(targetUUID);
                    reviverMap.remove(targetUUID);
                    lastReviveAttemptTick.remove(targetUUID);
                }
            }
        }
    }

    private void triggerTeamWipe(MinecraftServer server, List<ServerPlayer> players) {
        if (teamWipeTimerTicks > 0) return;

        int durationSec = net.dandare21.fracturedutils.config.ServerConfig.getTeamWipeScreenDurationSeconds();
        this.teamWipeTimerTicks = durationSec * 20;
        this.pendingWipePlayers = new ArrayList<>(players);

        // Send Team Wipe packet to open TeamWipeScreen on clients
        for (ServerPlayer player : players) {
            ModMessages.sendToPlayer(new net.dandare21.fracturedutils.network.packet.S2CTeamWipePacket(durationSec), player);
        }
    }

    public void clearCheckpoint() {
        this.activeSequence = null;
        this.checkpointIndex = -1;
        this.downedPlayers.clear();
        this.reviveProgress.clear();
        this.reviverMap.clear();
    }
}
