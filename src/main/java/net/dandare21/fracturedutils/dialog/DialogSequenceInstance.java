package net.dandare21.fracturedutils.dialog;

import com.mojang.brigadier.StringReader;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.S2CDialogClearPacket;
import net.dandare21.fracturedutils.network.packet.S2CDialogDisplayPacket;
import net.dandare21.fracturedutils.network.packet.S2CDialogReadinessPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class DialogSequenceInstance {
    private final String fileName;
    private final List<DialogLine> lines;
    private final Set<UUID> targetedPlayerUUIDs = new HashSet<>();
    private final Set<UUID> readyPlayers = new HashSet<>();
    private int currentIndex = 0;
    private int delayRemainingTicks = 0;
    private boolean lineStarted = false;
    private boolean finished = false;

    public DialogSequenceInstance(String fileName, List<DialogLine> lines, Collection<ServerPlayer> targetPlayers) {
        this.fileName = fileName != null ? fileName : "dialog.json";
        this.lines = lines != null ? lines : List.of();
        if (targetPlayers != null) {
            for (ServerPlayer p : targetPlayers) {
                if (p != null) {
                    this.targetedPlayerUUIDs.add(p.getUUID());
                }
            }
        }
    }

    public String getFileName() {
        return fileName;
    }

    public boolean isFinished() {
        return finished;
    }

    public synchronized boolean isPlayerTargeted(UUID uuid) {
        if (finished || uuid == null) return false;
        return targetedPlayerUUIDs.isEmpty() || targetedPlayerUUIDs.contains(uuid);
    }

    public synchronized boolean isCameraActiveForPlayer(UUID uuid) {
        if (finished || uuid == null || currentIndex >= lines.size()) return false;
        DialogLine line = lines.get(currentIndex);
        if (!line.isUseCamera()) return false;
        return targetedPlayerUUIDs.isEmpty() || targetedPlayerUUIDs.contains(uuid);
    }

    public static List<ServerPlayer> resolvePlayers(MinecraftServer server, Collection<UUID> targetedUUIDs) {
        if (server == null) return List.of();
        List<ServerPlayer> list = new ArrayList<>();
        if (targetedUUIDs == null || targetedUUIDs.isEmpty()) {
            return new ArrayList<>(server.getPlayerList().getPlayers());
        }
        for (UUID uuid : targetedUUIDs) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

    public synchronized void recordPlayerReady(ServerPlayer player, MinecraftServer server) {
        if (finished || player == null || server == null || currentIndex >= lines.size()) return;

        // Verify player is targeted by this sequence
        if (!targetedPlayerUUIDs.isEmpty() && !targetedPlayerUUIDs.contains(player.getUUID())) {
            return;
        }

        readyPlayers.add(player.getUUID());
        List<ServerPlayer> targets = resolvePlayers(server, targetedPlayerUUIDs);

        // Broadcast updated ready player list to targeted players so skin face icons update
        S2CDialogReadinessPacket packet = new S2CDialogReadinessPacket(new ArrayList<>(readyPlayers));
        ModMessages.sendToPlayers(packet, targets);

        // Consensus check: if all online targeted players are ready, advance line!
        if (readyPlayers.size() >= Math.max(1, targets.size())) {
            advanceLine(server);
        }
    }

    public synchronized void advanceLine(MinecraftServer server) {
        if (finished) return;

        if (server != null && currentIndex < lines.size()) {
            List<ServerPlayer> targets = resolvePlayers(server, targetedPlayerUUIDs);
            ModMessages.sendToPlayers(new S2CDialogClearPacket(), targets);
        }

        readyPlayers.clear();
        currentIndex++;
        lineStarted = false;
        if (currentIndex >= lines.size()) {
            finished = true;
            if (server != null) {
                List<ServerPlayer> targets = resolvePlayers(server, targetedPlayerUUIDs);
                ModMessages.sendToPlayers(new S2CDialogClearPacket(), targets);
            }
        }
    }

    public synchronized void handlePlayerLoggedOut(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        readyPlayers.remove(uuid);
        targetedPlayerUUIDs.remove(uuid);
    }

    public synchronized void stop(MinecraftServer server) {
        finished = true;
        readyPlayers.clear();
        if (server != null) {
            List<ServerPlayer> targets = resolvePlayers(server, targetedPlayerUUIDs);
            ModMessages.sendToPlayers(new S2CDialogClearPacket(), targets);
        }
    }

    public synchronized void cancelWithoutClear() {
        this.finished = true;
        this.readyPlayers.clear();
    }

    public synchronized void tick(MinecraftServer server) {
        if (finished || server == null || lines.isEmpty()) {
            return;
        }

        if (currentIndex >= lines.size()) {
            finished = true;
            List<ServerPlayer> targets = resolvePlayers(server, targetedPlayerUUIDs);
            ModMessages.sendToPlayers(new S2CDialogClearPacket(), targets);
            return;
        }

        List<ServerPlayer> targets = resolvePlayers(server, targetedPlayerUUIDs);

        // Auto-prune disconnected players from ready set
        readyPlayers.removeIf(uuid -> targets.stream().noneMatch(p -> p.getUUID().equals(uuid)));

        DialogLine currentLine = lines.get(currentIndex);

        // 1. Play line ONCE when starting a new line index
        if (!lineStarted) {
            readyPlayers.clear();
            playLine(server, currentLine, targets);
            lineStarted = true;

            if (!currentLine.isWaitForInput()) {
                int visibleChars = DialogFormatUtil.getVisibleCharCount(currentLine.getText());
                int animTicks = currentLine.getCharSpeedTicks() * visibleChars;
                delayRemainingTicks = animTicks + Math.max(1, currentLine.getDelayTicks());
            } else {
                delayRemainingTicks = -1; // Waiting for client C2SDialogAdvancePacket
            }
            return;
        }

        // 2. Check if consensus reached due to player disconnects
        if (currentLine.isWaitForInput() && !targets.isEmpty() && readyPlayers.size() >= targets.size()) {
            advanceLine(server);
            return;
        }

        // 3. If line is not waiting for input, tick down post-text delay
        if (!currentLine.isWaitForInput() && delayRemainingTicks > 0) {
            delayRemainingTicks--;
            if (delayRemainingTicks <= 0) {
                advanceLine(server);
            }
        }
    }

    private void playLine(MinecraftServer server, DialogLine line, List<ServerPlayer> targets) {
        if (targets == null || targets.isEmpty()) return;

        S2CDialogDisplayPacket packet = new S2CDialogDisplayPacket(
                line.getSpeaker(),
                line.getText(),
                line.getDelayTicks(),
                line.getCharSpeedTicks(),
                line.getLetterSound(),
                line.getLetterSoundPitchMin(),
                line.getLetterSoundPitchMax(),
                line.isWaitForInput(),
                line.isUseCamera(),
                line.getCameraX(),
                line.getCameraY(),
                line.getCameraZ(),
                line.getCameraYaw(),
                line.getCameraPitch(),
                line.getCameraFov()
        );
        ModMessages.sendToPlayers(packet, targets);

        // Reset readiness indicator on clients for new line
        ModMessages.sendToPlayers(new S2CDialogReadinessPacket(new ArrayList<>()), targets);

        if (line.getSound() != null && !line.getSound().trim().isEmpty()) {
            try {
                ResourceLocation soundLocation = new ResourceLocation(line.getSound().trim());
                SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(soundLocation);
                if (soundEvent != null) {
                    for (ServerPlayer player : targets) {
                        player.playNotifySound(soundEvent, SoundSource.RECORDS, line.getVolume(), line.getPitch());
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
}
