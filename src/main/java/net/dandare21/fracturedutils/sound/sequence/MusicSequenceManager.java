package net.dandare21.fracturedutils.sound.sequence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.dialog.DialogLine;
import net.dandare21.fracturedutils.dialog.DialogManager;
import net.dandare21.fracturedutils.objective.ObjectiveManager;
import net.dandare21.fracturedutils.puppet.IPuppetEntity;
import net.dandare21.fracturedutils.puppet.action.AbyssalBarrageAction;
import net.dandare21.fracturedutils.puppet.action.LeapSlamAction;
import net.dandare21.fracturedutils.puppet.boss.VoidHeraldBoss;
import net.dandare21.fracturedutils.puppet.capability.IPuppetHandler;
import net.dandare21.fracturedutils.puppet.capability.PuppetCapabilityProvider;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionType;
import net.dandare21.fracturedutils.puppet.registry.ModPuppetActions;
import net.dandare21.fracturedutils.puppet.target.ActionTarget;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.S2CCameraOverridePacket;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectInstance;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectManager;
import net.dandare21.fracturedutils.screeneffect.effects.HueShiftEffect;
import net.dandare21.fracturedutils.screeneffect.effects.ImpactFrameEffect;
import net.dandare21.fracturedutils.screeneffect.effects.InvertColorsEffect;
import net.dandare21.fracturedutils.screeneffect.effects.ScreenShakeEffect;
import net.dandare21.fracturedutils.screeneffect.effects.StrobeEffect;
import net.dandare21.fracturedutils.sound.ModSoundSources;
import net.dandare21.fracturedutils.sound.event.EventAudioManager;
import net.dandare21.fracturedutils.network.packet.S2CPlayEventAudioPacket.PlaybackMode;
import net.dandare21.fracturedutils.util.SelectorUtils;
import net.dandare21.fracturedutils.puppet.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MusicSequenceManager {
    private static final MusicSequenceManager INSTANCE = new MusicSequenceManager();

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public static class ActiveMusicSequence {
        private final String fileName;
        private final MusicSequence sequence;
        private long startTimeMs;
        private final long expectedDurationMs;
        private final Set<UUID> targetPlayerUuids;
        private final Set<Integer> executedEntryIndices = new HashSet<>();
        private boolean finished = false;

        public ActiveMusicSequence(String fileName, MusicSequence sequence, Collection<ServerPlayer> targets) {
            this.fileName = fileName;
            this.sequence = sequence;
            this.startTimeMs = System.currentTimeMillis();
            this.targetPlayerUuids = new HashSet<>();
            if (targets != null) {
                for (ServerPlayer player : targets) {
                    if (player != null) {
                        targetPlayerUuids.add(player.getUUID());
                    }
                }
            }

            long songDuration = 0L;
            if (sequence.getSongTrack() != null && !sequence.getSongTrack().trim().isEmpty()) {
                byte[] oggBytes = net.dandare21.fracturedutils.sound.event.AudioTrackBytesProvider.getTrackBytes(sequence.getSongTrack());
                songDuration = decodeOggDurationMs(oggBytes);
            }

            long maxEntryTimestamp = 0L;
            for (MusicSequenceEntry entry : sequence.getEntries()) {
                if (entry.getTimestampMs() > maxEntryTimestamp) {
                    maxEntryTimestamp = entry.getTimestampMs();
                }
            }

            if (sequence.getEndMs() > 0) {
                this.expectedDurationMs = sequence.getEndMs();
            } else if (songDuration > 0) {
                this.expectedDurationMs = songDuration;
            } else {
                this.expectedDurationMs = Math.max(30000L, maxEntryTimestamp + 1000L);
            }
        }

        public String getFileName() {
            return fileName;
        }

        public MusicSequence getSequence() {
            return sequence;
        }

        public long getStartTimeMs() {
            return startTimeMs;
        }

        public void setStartTimeMs(long startTimeMs) {
            this.startTimeMs = startTimeMs;
        }

        public long getExpectedDurationMs() {
            return expectedDurationMs;
        }

        public boolean isFinished() {
            return finished;
        }

        public void setFinished(boolean finished) {
            this.finished = finished;
        }

        public Set<UUID> getTargetPlayerUuids() {
            return targetPlayerUuids;
        }

        public Set<Integer> getExecutedEntryIndices() {
            return executedEntryIndices;
        }

        public Collection<ServerPlayer> getTargets(MinecraftServer server) {
            if (server == null) return Collections.emptyList();
            if (targetPlayerUuids.isEmpty()) return server.getPlayerList().getPlayers();
            List<ServerPlayer> targets = new ArrayList<>();
            for (UUID u : targetPlayerUuids) {
                ServerPlayer p = server.getPlayerList().getPlayer(u);
                if (p != null) targets.add(p);
            }
            return targets;
        }
    }

    public static long decodeOggDurationMs(byte[] bytes) {
        if (bytes == null || bytes.length < 28) return 0L;

        int sampleRate = 0;
        for (int i = 0; i <= bytes.length - 15; i++) {
            if (bytes[i] == 1 && bytes[i + 1] == 'v' && bytes[i + 2] == 'o' && bytes[i + 3] == 'r'
                    && bytes[i + 4] == 'b' && bytes[i + 5] == 'i' && bytes[i + 6] == 's') {
                sampleRate = (bytes[i + 11] & 0xFF) |
                        ((bytes[i + 12] & 0xFF) << 8) |
                        ((bytes[i + 13] & 0xFF) << 16) |
                        ((bytes[i + 14] & 0xFF) << 24);
                break;
            }
        }

        if (sampleRate <= 0) return 0L;

        long totalSamples = -1;
        for (int i = bytes.length - 4; i >= 0; i--) {
            if (bytes[i] == 0x4F && bytes[i + 1] == 0x67 && bytes[i + 2] == 0x67 && bytes[i + 3] == 0x53) {
                if (i + 13 < bytes.length) {
                    long granule = (bytes[i + 6] & 0xFFL) |
                            ((bytes[i + 7] & 0xFFL) << 8) |
                            ((bytes[i + 8] & 0xFFL) << 16) |
                            ((bytes[i + 9] & 0xFFL) << 24) |
                            ((bytes[i + 10] & 0xFFL) << 32) |
                            ((bytes[i + 11] & 0xFFL) << 40) |
                            ((bytes[i + 12] & 0xFFL) << 48) |
                            ((bytes[i + 13] & 0xFFL) << 56);
                    if (granule > 0) {
                        totalSamples = granule;
                        break;
                    }
                }
            }
        }

        if (totalSamples > 0) {
            return (totalSamples * 1000L) / sampleRate;
        }

        return 0L;
    }

    private final List<ActiveMusicSequence> activeSequences = new CopyOnWriteArrayList<>();

    public static MusicSequenceManager getInstance() {
        return INSTANCE;
    }

    public boolean isSequenceActive(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return false;
        String cleanName = sanitizeFileName(fileName);
        for (ActiveMusicSequence activeSeq : activeSequences) {
            String activeClean = sanitizeFileName(activeSeq.getFileName());
            if (activeClean.equalsIgnoreCase(cleanName) && !activeSeq.isFinished()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasActiveSequences() {
        return !activeSequences.isEmpty();
    }

    private MusicSequenceManager() {
        ensureDirectoryExists();
    }

    public File getDirectory() {
        File dir = FMLPaths.CONFIGDIR.get().resolve("music_sequences").toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public void ensureDirectoryExists() {
        getDirectory();
    }

    public String sanitizeFileName(String fileName) {
        if (fileName == null) return "";
        fileName = fileName.trim();
        fileName = fileName.replaceAll("[\\\\/]", "");
        if (!fileName.endsWith(".json")) {
            fileName += ".json";
        }
        return fileName;
    }

    public List<String> getSequenceFileNames() {
        File dir = getDirectory();
        if (!dir.exists()) return Collections.emptyList();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        for (File f : files) {
            list.add(f.getName());
        }
        Collections.sort(list);
        return list;
    }

    public Map<String, String> getAllSequenceFiles() {
        Map<String, String> map = new HashMap<>();
        File dir = getDirectory();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                try {
                    String content = Files.readString(f.toPath());
                    map.put(f.getName(), content);
                } catch (IOException e) {
                    FracturedUtils.LOGGER.error("[MusicSequenceManager] Failed to read music sequence file {}: {}", f.getName(), e.getMessage());
                }
            }
        }
        return map;
    }

    public boolean saveSequenceFile(String fileName, String jsonContent) {
        String cleanName = sanitizeFileName(fileName);
        if (cleanName.isEmpty()) return false;

        try {
            MusicSequence sequence = GSON.fromJson(jsonContent, MusicSequence.class);
            if (sequence == null) {
                sequence = new MusicSequence();
            }
            sequence.sortEntriesByTimestamp();
            String formattedJson = GSON.toJson(sequence);

            File file = new File(getDirectory(), cleanName);
            Files.writeString(file.toPath(), formattedJson);
            FracturedUtils.LOGGER.info("[MusicSequenceManager] Saved music sequence file: {}", cleanName);
            return true;
        } catch (Exception e) {
            FracturedUtils.LOGGER.error("[MusicSequenceManager] Error saving music sequence file {}: {}", cleanName, e.getMessage());
            return false;
        }
    }

    public boolean deleteSequenceFile(String fileName) {
        String cleanName = sanitizeFileName(fileName);
        if (cleanName.isEmpty()) return false;

        File file = new File(getDirectory(), cleanName);
        if (file.exists() && file.delete()) {
            FracturedUtils.LOGGER.info("[MusicSequenceManager] Deleted music sequence file: {}", cleanName);
            return true;
        }
        return false;
    }

    public MusicSequence loadSequence(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return null;
        File file = new File(getDirectory(), sanitizeFileName(fileName));
        if (!file.exists()) return null;

        try {
            String json = Files.readString(file.toPath());
            return GSON.fromJson(json, MusicSequence.class);
        } catch (IOException e) {
            FracturedUtils.LOGGER.error("[MusicSequenceManager] Failed to load music sequence '{}'", fileName, e);
            return null;
        }
    }

    public boolean saveSequence(String fileName, MusicSequence sequence) {
        if (fileName == null || fileName.trim().isEmpty() || sequence == null) return false;
        File file = new File(getDirectory(), sanitizeFileName(fileName));

        try {
            String json = GSON.toJson(sequence);
            Files.writeString(file.toPath(), json);
            FracturedUtils.LOGGER.info("[MusicSequenceManager] Saved music sequence '{}'", fileName);
            return true;
        } catch (IOException e) {
            FracturedUtils.LOGGER.error("[MusicSequenceManager] Failed to save music sequence '{}'", fileName, e);
            return false;
        }
    }

    public boolean startSequence(String fileName, Collection<ServerPlayer> targets) {
        MusicSequence sequence = loadSequence(fileName);
        if (sequence == null) return false;

        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();

        // 1. Start Audio Playback via EventAudioManager if song track is specified
        if (sequence.getSongTrack() != null && !sequence.getSongTrack().trim().isEmpty()) {
            EventAudioManager.getInstance().playAudio(
                    server,
                    sequence.getSongTrack(),
                    ModSoundSources.EVENT_MUSIC,
                    targets,
                    sequence.getVolume(),
                    sequence.getPitch(),
                    1000,
                    PlaybackMode.SERVER_CONTROLLED,
                    sequence.isLooping(),
                    2000
            );
        }

        // 2. Track Active Sequence for Timed Action Execution (cleanly stop prior instance of this sequence if running)
        activeSequences.removeIf(seq -> seq.getFileName().equalsIgnoreCase(fileName));
        ActiveMusicSequence activeSeq = new ActiveMusicSequence(fileName, sequence, targets);
        activeSequences.add(activeSeq);
        FracturedUtils.LOGGER.info("[MusicSequenceManager] Started music sequence '{}' with {} entries (expected duration: {}ms).", fileName, sequence.getEntries().size(), activeSeq.getExpectedDurationMs());
        return true;
    }

    public void stopAllSequences(MinecraftServer server) {
        if (server != null) {
            S2CCameraOverridePacket clearPacket = new S2CCameraOverridePacket(
                    false, "CLEAR", 0, 0, 0, 0, 0, 0, 70.0, 0, false, -1, 0, 0, 0
            );
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ModMessages.sendToPlayer(clearPacket, player);
            }
            ScreenEffectManager.stopAllEffects(server);
        }
        activeSequences.clear();
        EventAudioManager.getInstance().stopAudio(server, null, 1000);
        DialogManager.getInstance().stopAllSequences(server);
        FracturedUtils.LOGGER.info("[MusicSequenceManager] Stopped all active music sequences.");
    }

    public void tick(MinecraftServer server) {
        if (server == null || activeSequences.isEmpty()) return;

        long now = System.currentTimeMillis();

        for (ActiveMusicSequence activeSeq : activeSequences) {
            long elapsedMs = now - activeSeq.getStartTimeMs();
            List<MusicSequenceEntry> entries = activeSeq.getSequence().getEntries();

            for (int i = 0; i < entries.size(); i++) {
                if (activeSeq.getExecutedEntryIndices().contains(i)) {
                    continue;
                }

                MusicSequenceEntry entry = entries.get(i);
                if (elapsedMs >= entry.getTimestampMs()) {
                    executeEntry(server, activeSeq, entry);
                    activeSeq.getExecutedEntryIndices().add(i);
                }
            }

            long endMs = activeSeq.getSequence().getEndMs() > 0 ? activeSeq.getSequence().getEndMs() : activeSeq.getExpectedDurationMs();

            if (elapsedMs >= endMs) {
                if (activeSeq.getSequence().isLooping()) {
                    activeSeq.setStartTimeMs(now - activeSeq.getSequence().getStartMs());
                    activeSeq.getExecutedEntryIndices().clear();
                    FracturedUtils.LOGGER.info("[MusicSequenceManager] Looping sequence '{}' (reset to {}ms)",
                            activeSeq.getFileName(), activeSeq.getSequence().getStartMs());
                } else {
                    activeSeq.setFinished(true);
                    EventAudioManager.getInstance().stopAudio(server, activeSeq.getTargets(server), 500);
                    S2CCameraOverridePacket clearPacket = new S2CCameraOverridePacket(
                            false, "CLEAR", 0, 0, 0, 0, 0, 0, 70.0, 0, false, -1, 0, 0, 0
                    );
                    for (ServerPlayer player : activeSeq.getTargets(server)) {
                        ModMessages.sendToPlayer(clearPacket, player);
                        ScreenEffectManager.stopAllEffects(player);
                    }
                    FracturedUtils.LOGGER.info("[MusicSequenceManager] Sequence '{}' reached OUT marker at {}ms. Stopped audio and finished.",
                            activeSeq.getFileName(), endMs);
                }
            }
        }

        activeSequences.removeIf(ActiveMusicSequence::isFinished);
    }

    private void executeEntry(MinecraftServer server, ActiveMusicSequence activeSeq, MusicSequenceEntry entry) {
        ServerPlayer contextPlayer = null;
        for (UUID uuid : activeSeq.getTargetPlayerUuids()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                contextPlayer = p;
                break;
            }
        }
        if (contextPlayer == null && !server.getPlayerList().getPlayers().isEmpty()) {
            contextPlayer = server.getPlayerList().getPlayers().get(0);
        }

        MusicSequenceChannel matchedChannel = null;
        for (MusicSequenceChannel ch : activeSeq.getSequence().getChannels()) {
            if (ch.getId().equalsIgnoreCase(entry.getChannelId())) {
                matchedChannel = ch;
                break;
            }
        }

        boolean isCameraChannel = (matchedChannel != null && MusicSequenceChannel.TYPE_CAMERA.equalsIgnoreCase(matchedChannel.getType()));
        boolean isCameraEntry = isCameraChannel || entry.isUseCamera() || "CAMERA".equalsIgnoreCase(entry.getActionType());

        if (isCameraEntry) {
            executeCameraEntry(server, activeSeq, entry, contextPlayer);
            return;
        }

        boolean isScreenEffectChannel = (matchedChannel != null && (MusicSequenceChannel.TYPE_SCREEN_EFFECT.equalsIgnoreCase(matchedChannel.getType()) || MusicSequenceChannel.TYPE_OBJECTIVE.equalsIgnoreCase(matchedChannel.getType())));
        boolean isScreenEffectEntry = isScreenEffectChannel || "SCREEN_EFFECT".equalsIgnoreCase(entry.getActionType()) || "SCREEN_EFFECTS".equalsIgnoreCase(entry.getActionType());

        if (isScreenEffectEntry) {
            executeScreenEffectEntry(server, activeSeq, entry, contextPlayer);
            return;
        }

        String rawCmd = entry.getCommand() != null ? entry.getCommand().trim() : "";
        boolean isPuppetChannel = (matchedChannel != null && "PUPPET".equalsIgnoreCase(matchedChannel.getType()));
        boolean isPuppetCmd = rawCmd.startsWith("puppet_action") || rawCmd.startsWith("puppet_suppress") || rawCmd.startsWith("puppet_restore") || "PUPPET".equalsIgnoreCase(entry.getActionType());

        if (isPuppetChannel || isPuppetCmd) {
            executePuppetEntry(server, activeSeq, entry, contextPlayer);
            return;
        }

        if ("DIALOG".equalsIgnoreCase(entry.getActionType())) {
            DialogLine dialogLine = entry.getDialog();
            if (dialogLine != null) {
                DialogManager.getInstance().startSingleDialog(dialogLine, activeSeq.getTargets(server));
                FracturedUtils.LOGGER.info("[MusicSequenceManager] Started single dialog '{}' at {}ms", entry.getDescription(), entry.getTimestampMs());
                return;
            }

            String cmd = entry.getCommand() != null ? entry.getCommand().trim() : "";
            if (!cmd.startsWith("/")) {
                if (cmd.endsWith(".json")) {
                    DialogManager.getInstance().startSequence(cmd, activeSeq.getTargets(server));
                    FracturedUtils.LOGGER.info("[MusicSequenceManager] Started legacy dialog sequence '{}' at {}ms", cmd, entry.getTimestampMs());
                } else if (!cmd.isEmpty()) {
                    DialogLine fallback = new DialogLine();
                    fallback.setText(cmd);
                    fallback.setSpeaker(entry.getDescription() != null ? entry.getDescription() : "");
                    fallback.setWaitForInput(false);
                    fallback.setUseCamera(false);
                    DialogManager.getInstance().startSingleDialog(fallback, activeSeq.getTargets(server));
                    FracturedUtils.LOGGER.info("[MusicSequenceManager] Started fallback single dialog '{}' at {}ms", cmd, entry.getTimestampMs());
                }
                return;
            }
        }

        if ("SCREEN_EFFECT".equalsIgnoreCase(entry.getActionType()) || "SCREEN_EFFECTS".equalsIgnoreCase(entry.getActionType())) {
            executeScreenEffectEntry(server, activeSeq, entry, contextPlayer);
            return;
        }

        if (entry.getCommand() == null || entry.getCommand().trim().isEmpty()) return;

        String cmd = entry.getCommand().trim();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }

        // Do not pass internal puppet directives to the server command manager
        if (cmd.startsWith("puppet_action") || cmd.startsWith("puppet_suppress") || cmd.startsWith("puppet_restore")) {
            return;
        }

        if (contextPlayer != null) {
            cmd = cmd.replace("%player%", contextPlayer.getGameProfile().getName());
            cmd = cmd.replace("%uuid%", contextPlayer.getStringUUID());
        } else {
            cmd = cmd.replace("%player%", "@p");
            cmd = cmd.replace("%uuid%", "");
        }

        CommandSourceStack sourceStack = contextPlayer != null
                ? contextPlayer.createCommandSourceStack().withPermission(4).withSuppressedOutput()
                : server.createCommandSourceStack();

        try {
            server.getCommands().performPrefixedCommand(sourceStack, cmd);
            FracturedUtils.LOGGER.info("[MusicSequenceManager] Executed entry command at {}ms: '{}'", entry.getTimestampMs(), cmd);
        } catch (Exception e) {
            FracturedUtils.LOGGER.error("[MusicSequenceManager] Error executing entry command '{}' in sequence {}", cmd, activeSeq.getFileName(), e);
        }
    }

    private void executeCameraEntry(MinecraftServer server, ActiveMusicSequence activeSeq, MusicSequenceEntry entry, ServerPlayer contextPlayer) {
        String mode = entry.getCameraMode();
        if (mode == null || mode.isBlank()) {
            mode = "STATIC";
        }

        int targetEntityId = -1;
        if ("FOLLOW".equalsIgnoreCase(mode) || "OVER_THE_SHOULDER".equalsIgnoreCase(mode)) {
            String targetSelector = entry.getCameraTarget();
            if (targetSelector == null || targetSelector.isBlank() || "@p".equalsIgnoreCase(targetSelector)) {
                if (contextPlayer != null) {
                    targetEntityId = contextPlayer.getId();
                }
            } else {
                List<Entity> found = SelectorUtils.getTargetEntities(server, targetSelector);
                if (!found.isEmpty()) {
                    targetEntityId = found.get(0).getId();
                } else if (contextPlayer != null) {
                    targetEntityId = contextPlayer.getId();
                }
            }
        }

        int durationMs = (int) entry.getTotalDurationMs();
        if (durationMs <= 0) {
            durationMs = 3000;
        }

        S2CCameraOverridePacket packet = new S2CCameraOverridePacket(
                !"CLEAR".equalsIgnoreCase(mode),
                mode,
                entry.getCameraX(),
                entry.getCameraY(),
                entry.getCameraZ(),
                entry.getCameraYaw(),
                entry.getCameraPitch(),
                entry.getCameraRoll(),
                entry.getCameraFov(),
                durationMs,
                entry.isCameraInterpolate(),
                targetEntityId,
                entry.getCameraHeightOffset(),
                entry.getCameraBackDistance(),
                entry.getCameraShoulderOffset()
        );

        for (ServerPlayer player : activeSeq.getTargets(server)) {
            ModMessages.sendToPlayer(packet, player);
        }

        FracturedUtils.LOGGER.info("[MusicSequenceManager] Dispatched camera override (mode={}, dur={}ms) to {} players",
                mode, durationMs, activeSeq.getTargets(server).size());
    }

    private void executeScreenEffectEntry(MinecraftServer server, ActiveMusicSequence activeSeq, MusicSequenceEntry entry, ServerPlayer contextPlayer) {
        int durationMs = (int) entry.getTotalDurationMs();
        if (durationMs <= 0) {
            durationMs = entry.getDurationMs() > 0 ? entry.getDurationMs() : 1000;
        }

        String effectId = entry.getScreenEffectId();
        if (effectId == null || effectId.isBlank()) {
            effectId = entry.getSubAction();
        }
        if (effectId == null || effectId.isBlank()) {
            effectId = "fractured_utils:screen_shake";
        }

        ScreenEffectInstance instance;
        if (effectId.equalsIgnoreCase("fractured_utils:invert_colors") || effectId.equalsIgnoreCase("invert") || effectId.equalsIgnoreCase("invert_colors")) {
            instance = new InvertColorsEffect.InvertColorsInstance(durationMs, entry.isScreenEffectPulse(), entry.getScreenEffectFrequency());
        } else if (effectId.equalsIgnoreCase("fractured_utils:strobe") || effectId.equalsIgnoreCase("strobe")) {
            instance = new StrobeEffect.StrobeInstance(durationMs, entry.getScreenEffectFrequency(), entry.getScreenEffectColor(), entry.isScreenEffectSmooth(), entry.getScreenEffectMaxAlpha());
        } else if (effectId.equalsIgnoreCase("fractured_utils:hue_shift") || effectId.equalsIgnoreCase("hue_shift") || effectId.equalsIgnoreCase("hue")) {
            instance = new HueShiftEffect.HueShiftInstance(durationMs, entry.getScreenEffectFrequency(), entry.isScreenEffectContinuous(), entry.getScreenEffectAngle(), entry.getScreenEffectIntensity());
        } else if (effectId.equalsIgnoreCase("fractured_utils:impact_frame") || effectId.equalsIgnoreCase("impact_frame") || effectId.equalsIgnoreCase("impact")) {
            int frameInterval = entry.getScreenEffectFrequency() > 0 ? (int) (1000.0f / entry.getScreenEffectFrequency()) : 35;
            if (frameInterval < 10) frameInterval = 35;
            int pColor = entry.getScreenEffectColor() != 0 ? entry.getScreenEffectColor() : 0xFFFFFFFF;
            int sColor = entry.getScreenEffectSecondaryColor() != 0 ? entry.getScreenEffectSecondaryColor() : 0xFF000000;
            String style = entry.getScreenEffectStyle();
            if (style == null || style.isBlank()) style = "DRAW";
            instance = new ImpactFrameEffect.ImpactFrameInstance(durationMs, pColor, sColor, frameInterval, entry.isScreenEffectPulse(), style);
        } else {
            // Default: screen shake
            instance = new ScreenShakeEffect.ScreenShakeInstance(durationMs, entry.getScreenEffectIntensity(), entry.getScreenEffectFrequency(), entry.isScreenEffectDecay());
        }

        ScreenEffectManager.playEffect(activeSeq.getTargets(server), instance);
        FracturedUtils.LOGGER.info("[MusicSequenceManager] Dispatched screen effect (type={}, dur={}ms) to {} players",
                instance.getType().getId(), durationMs, activeSeq.getTargets(server).size());
    }

    private String resolveActorTag(MusicSequenceChannel channel, MusicSequenceEntry entry) {
        String cmd = entry.getCommand() != null ? entry.getCommand().trim() : "";
        if (cmd.contains("tag:")) {
            int idx = cmd.indexOf("tag:") + 4;
            int space = cmd.indexOf(" ", idx);
            if (space == -1) space = cmd.indexOf("}", idx);
            if (space == -1) space = cmd.length();
            String tag = SelectorUtils.cleanTag(cmd.substring(idx, space));
            if (!tag.isEmpty()) return tag;
        }
        if (channel != null && !channel.getActorTag().isBlank()) {
            return SelectorUtils.cleanTag(channel.getActorTag());
        }
        if (channel != null && !channel.getPuppetActor().isBlank()) {
            String tag = SelectorUtils.cleanTag(channel.getPuppetActor());
            if (!tag.isEmpty()) return tag;
        }
        if (cmd.contains("Tags:[\"")) {
            int start = cmd.indexOf("Tags:[\"") + 7;
            int end = cmd.indexOf("\"", start);
            if (end != -1) return SelectorUtils.cleanTag(cmd.substring(start, end));
        }
        if (entry.getDescription() != null && entry.getDescription().contains("#")) {
            int start = entry.getDescription().indexOf("#") + 1;
            int end = entry.getDescription().indexOf(")", start);
            if (end == -1) end = entry.getDescription().indexOf(" ", start);
            if (end == -1) end = entry.getDescription().length();
            return SelectorUtils.cleanTag(entry.getDescription().substring(start, end));
        }
        return (channel != null && !channel.getName().isBlank()) ? channel.getName().toLowerCase(Locale.ROOT).replace(" ", "_") : "";
    }

    private void executePuppetEntry(MinecraftServer server, ActiveMusicSequence activeSeq, MusicSequenceEntry entry, ServerPlayer contextPlayer) {
        MusicSequenceChannel channel = null;
        for (MusicSequenceChannel ch : activeSeq.getSequence().getChannels()) {
            if (ch.getId().equalsIgnoreCase(entry.getChannelId())) {
                channel = ch;
                break;
            }
        }
        if (channel == null) {
            for (MusicSequenceChannel ch : activeSeq.getSequence().getChannels()) {
                if ("PUPPET".equalsIgnoreCase(ch.getType())) {
                    channel = ch;
                    break;
                }
            }
        }

        // If sub-channel has blank actor tag or entity info, fall back to its parent channel
        if (channel != null && channel.isSubChannel()) {
            for (MusicSequenceChannel ch : activeSeq.getSequence().getChannels()) {
                if (ch.getId().equalsIgnoreCase(channel.getParentChannelId())) {
                    if (channel.getActorTag().isBlank() && !ch.getActorTag().isBlank()) {
                        channel.setActorTag(ch.getActorTag());
                    }
                    if (channel.getPuppetActor().isBlank() && !ch.getPuppetActor().isBlank()) {
                        channel.setPuppetActor(ch.getPuppetActor());
                    }
                    if (channel.getActorName().isBlank() && !ch.getActorName().isBlank()) {
                        channel.setActorName(ch.getActorName());
                    }
                    if (channel.getActorEntityType().isBlank() && !ch.getActorEntityType().isBlank()) {
                        channel.setActorEntityType(ch.getActorEntityType());
                    }
                    break;
                }
            }
        }

        String sub = entry.getSubAction();
        String cmd = entry.getCommand() != null ? entry.getCommand().trim() : "";
        if (sub == null || sub.isBlank()) {
            String lowerCmd = cmd.toLowerCase(Locale.ROOT);
            if (lowerCmd.startsWith("summon")) {
                sub = "SPAWN";
            } else if (lowerCmd.startsWith("kill")) {
                sub = "DESPAWN";
            } else if (lowerCmd.startsWith("puppet_suppress") || lowerCmd.startsWith("puppet_restore")) {
                sub = "TOGGLE_AI";
            } else {
                sub = "EXECUTE_ACTION";
            }
        }
        if ("ACTION".equalsIgnoreCase(sub)) {
            sub = "EXECUTE_ACTION";
        }

        String actorTag = resolveActorTag(channel, entry);

        FracturedUtils.LOGGER.info("[MusicSequenceManager] Executing puppet sub-action '{}' for actor tag '{}' at {}ms (cmd: '{}')",
                sub, actorTag, entry.getTimestampMs(), cmd);

        CommandSourceStack sourceStack = contextPlayer != null
                ? contextPlayer.createCommandSourceStack().withPermission(4).withSuppressedOutput()
                : server.createCommandSourceStack();

        if ("SPAWN".equalsIgnoreCase(sub)) {
            spawnPuppetActor(server, activeSeq, entry, channel, actorTag, contextPlayer);
        } else if ("DESPAWN".equalsIgnoreCase(sub)) {
            try {
                List<Entity> taggedEntities = SelectorUtils.getEntitiesByTag(server, actorTag);
                if (taggedEntities.isEmpty() && channel != null && !channel.getActorTag().isBlank()) {
                    taggedEntities = SelectorUtils.getEntitiesByTag(server, channel.getActorTag());
                }
                if (!taggedEntities.isEmpty()) {
                    for (Entity e : taggedEntities) {
                        e.discard();
                    }
                    FracturedUtils.LOGGER.info("[MusicSequenceManager] Despawned {} entity/entities with custom tag '{}'",
                            taggedEntities.size(), actorTag);
                } else {
                    String killCmd = "kill @e[tag=" + actorTag + "]";
                    server.getCommands().performPrefixedCommand(sourceStack, killCmd);
                }
            } catch (Exception e) {
                FracturedUtils.LOGGER.error("[MusicSequenceManager] Error despawning puppet with tag " + actorTag, e);
            }
        } else if ("TOGGLE_AI".equalsIgnoreCase(sub)) {
            try {
                List<IPuppetHandler> handlers = SelectorUtils.getPuppetHandlersByTag(server, actorTag);
                if (handlers.isEmpty() && channel != null && !channel.getActorTag().isBlank()) {
                    handlers = SelectorUtils.getPuppetHandlersByTag(server, channel.getActorTag());
                }
                if (handlers.isEmpty()) {
                    handlers = SelectorUtils.getPuppetHandlersByTag(server, "puppet_actor");
                }
                if (handlers.isEmpty()) {
                    FracturedUtils.LOGGER.warn("[MusicSequenceManager] No active puppet handlers found with tag '{}' to toggle AI", actorTag);
                    return;
                }
                boolean isRestore = cmd.contains("puppet_restore") || entry.getDescription().toLowerCase(Locale.ROOT).contains("restore");
                for (IPuppetHandler h : handlers) {
                    if (isRestore) {
                        h.restoreAi();
                    } else {
                        boolean fullAi = cmd.contains("ai:true");
                        boolean nav = cmd.contains("nav:true");
                        boolean tgt = cmd.contains("tgt:true");
                        boolean look = cmd.contains("look:true");
                        boolean act = cmd.contains("actions:true");
                        h.setSuppressAi(fullAi);
                        h.setSuppressNavigation(nav);
                        h.setSuppressTargeting(tgt);
                        h.setSuppressLook(look);
                        h.setSuppressActions(act);
                    }
                }
                FracturedUtils.LOGGER.info("[MusicSequenceManager] Toggled AI on {} puppet handler(s) with tag '{}' (restore={})",
                        handlers.size(), actorTag, isRestore);
            } catch (Exception e) {
                FracturedUtils.LOGGER.error("[MusicSequenceManager] Error toggling puppet AI", e);
            }
        } else if ("EXECUTE_ACTION".equalsIgnoreCase(sub)) {
            try {
                String actionId = "";
                String combatTarget = "@p";
                int windupTicks = entry.getWindupMs() > 0 ? entry.getWindupMs() / 50 : 0;
                int jumpTicks = entry.getJumpMs() > 0 ? entry.getJumpMs() / 50 : 0;
                int durationTicks = entry.getDurationMs() > 0 ? entry.getDurationMs() / 50 : 0;
                int recoveryTicks = entry.getRecoveryMs() > 0 ? entry.getRecoveryMs() / 50 : 0;

                Map<String, String> commandParams = new LinkedHashMap<>();
                if (cmd.startsWith("puppet_action")) {
                    if (cmd.contains("action:")) {
                        for (String part : cmd.split("\\s+")) {
                            if (part.startsWith("action:")) {
                                actionId = part.substring(7).trim();
                            } else if (part.startsWith("target:")) {
                                combatTarget = part.substring(7).trim();
                            } else if (part.startsWith("tag:")) {
                                String parsedTag = SelectorUtils.cleanTag(part.substring(4).trim());
                                if (!parsedTag.isBlank()) {
                                    actorTag = parsedTag;
                                }
                            } else if (part.startsWith("windup:")) {
                                try {
                                    int ms = Integer.parseInt(part.substring(7).trim());
                                    windupTicks = Math.max(0, ms / 50);
                                } catch (Exception ignored) {}
                            } else if (part.startsWith("jump:")) {
                                try {
                                    int ms = Integer.parseInt(part.substring(5).trim());
                                    jumpTicks = Math.max(0, ms / 50);
                                } catch (Exception ignored) {}
                            } else if (part.startsWith("duration:")) {
                                try {
                                    int ms = Integer.parseInt(part.substring(9).trim());
                                    durationTicks = Math.max(0, ms / 50);
                                } catch (Exception ignored) {}
                            } else if (part.startsWith("recovery:")) {
                                try {
                                    int ms = Integer.parseInt(part.substring(9).trim());
                                    recoveryTicks = Math.max(0, ms / 50);
                                } catch (Exception ignored) {}
                            } else if (part.contains(":")) {
                                int colIdx = part.indexOf(':');
                                String pKey = part.substring(0, colIdx).trim();
                                String pVal = part.substring(colIdx + 1).trim();
                                if (!pKey.isEmpty()) {
                                    commandParams.put(pKey, pVal);
                                }
                            }
                        }
                    } else {
                        String[] parts = cmd.split("\\s+");
                        if (parts.length > 1) actionId = parts[1].trim();
                        if (parts.length > 2) combatTarget = parts[2].trim();
                    }
                } else {
                    actionId = cmd;
                }

                if (actionId.isEmpty()) {
                    actionId = "fractured_utils:leap_slam";
                }

                List<IPuppetHandler> handlers = new ArrayList<>();
                if (!actorTag.isBlank()) {
                    handlers.addAll(SelectorUtils.getPuppetHandlersByTag(server, actorTag));
                }
                if (handlers.isEmpty() && channel != null && !channel.getActorTag().isBlank()) {
                    handlers.addAll(SelectorUtils.getPuppetHandlersByTag(server, channel.getActorTag()));
                }
                if (handlers.isEmpty() && channel != null && !channel.getName().isBlank()) {
                    handlers.addAll(SelectorUtils.getPuppetHandlersByTag(server, channel.getName().toLowerCase(Locale.ROOT).replace(" ", "_")));
                }
                if (handlers.isEmpty()) {
                    handlers.addAll(SelectorUtils.getPuppetHandlersByTag(server, "puppet_actor"));
                }
                if (handlers.isEmpty()) {
                    for (ServerLevel level : server.getAllLevels()) {
                        for (Entity e : level.getAllEntities()) {
                            if (e.isAlive() && e instanceof Mob mob) {
                                if (mob instanceof VoidHeraldBoss || mob.getTags().contains("puppet_actor") || (!actorTag.isBlank() && mob.getTags().contains(actorTag))) {
                                    mob.getCapability(PuppetCapabilityProvider.PUPPET_HANDLER).ifPresent(h -> {
                                        if (!handlers.contains(h)) handlers.add(h);
                                    });
                                }
                            }
                        }
                    }
                }

                if (handlers.isEmpty()) {
                    FracturedUtils.LOGGER.warn("[MusicSequenceManager] Cannot execute action '{}': No alive mob found with custom tag '{}' or 'puppet_actor'!",
                            actionId, actorTag);
                    return;
                }

                // Ensure mob physics is enabled
                for (IPuppetHandler h : handlers) {
                    Mob mob = h.getMob();
                    if (mob != null && mob.isNoAi()) {
                        mob.setNoAi(false);
                        FracturedUtils.LOGGER.info("[MusicSequenceManager] Cleared NoAI on puppet mob '{}' for action execution", mob.getName().getString());
                    }
                }

                ResourceLocation resLoc = ResourceLocation.tryParse(actionId);
                if (resLoc == null && !actionId.contains(":")) {
                    resLoc = new ResourceLocation(FracturedUtils.MOD_ID, actionId);
                }

                PuppetActionType<?> actionType = resLoc != null ? ModPuppetActions.get(resLoc) : null;
                if (actionType == null) {
                    String norm = actionId.toLowerCase(Locale.ROOT);
                    if (norm.contains("leap")) {
                        actionType = ModPuppetActions.LEAP_SLAM;
                        resLoc = ModPuppetActions.LEAP_SLAM.getId();
                    } else if (norm.contains("barrage")) {
                        actionType = ModPuppetActions.ABYSSAL_BARRAGE;
                        resLoc = ModPuppetActions.ABYSSAL_BARRAGE.getId();
                    }
                }

                // Fallback to entry defaults if not set by command
                if (windupTicks <= 0 && entry.getWindupMs() > 0) windupTicks = entry.getWindupMs() / 50;
                if (jumpTicks <= 0 && entry.getJumpMs() > 0) jumpTicks = entry.getJumpMs() / 50;
                if (durationTicks <= 0 && entry.getDurationMs() > 0) durationTicks = entry.getDurationMs() / 50;
                if (recoveryTicks <= 0 && entry.getRecoveryMs() > 0) recoveryTicks = entry.getRecoveryMs() / 50;

                // For Leap Slam backward compatibility: If jumpMs was 0, but duration was the jump duration (>= 1500ms)
                if (actionType == ModPuppetActions.LEAP_SLAM && jumpTicks == 0 && durationTicks >= 30 && !cmd.contains("jump:")) {
                    jumpTicks = durationTicks;
                    durationTicks = 16;
                }

                // Check if any timings were explicitly set in the command or the entry
                boolean hasExplicitTiming = (entry.getTotalDurationMs() > 0)
                        || cmd.contains("windup:") || cmd.contains("jump:") || cmd.contains("duration:") || cmd.contains("recovery:");

                // Effective fallbacks: Only apply defaults if completely unconfigured
                int effWindupTicks = hasExplicitTiming ? windupTicks : 10;
                int effJumpTicks = hasExplicitTiming ? jumpTicks : 30;
                int effDurationTicks = hasExplicitTiming ? durationTicks : 16;
                int effRecoveryTicks = hasExplicitTiming ? recoveryTicks : 12;

                String targetSelectorStr = "@p";
                if (contextPlayer != null && ("@p".equalsIgnoreCase(combatTarget) || combatTarget.isBlank())) {
                    targetSelectorStr = contextPlayer.getStringUUID();
                } else if (!combatTarget.isBlank()) {
                    targetSelectorStr = combatTarget;
                }

                CompoundTag paramsTag = new CompoundTag();
                paramsTag.putString("target", targetSelectorStr);
                paramsTag.putInt("windupTicks", effWindupTicks);
                paramsTag.putInt("indicationTicks", effWindupTicks);
                paramsTag.putInt("windupMs", effWindupTicks * 50);
                paramsTag.putInt("jumpTicks", effJumpTicks);
                paramsTag.putInt("jumpMs", effJumpTicks * 50);
                paramsTag.putInt("durationTicks", effDurationTicks);
                paramsTag.putInt("durationMs", effDurationTicks * 50);
                paramsTag.putInt("channelTicks", effDurationTicks);
                paramsTag.putInt("recoveryTicks", effRecoveryTicks);
                paramsTag.putInt("recoveryMs", effRecoveryTicks * 50);

                // Inject custom / action parameters
                Map<String, String> mergedParams = new LinkedHashMap<>();
                if (entry.getPuppetParams() != null) {
                    mergedParams.putAll(entry.getPuppetParams());
                }
                mergedParams.putAll(commandParams);
                for (Map.Entry<String, String> p : mergedParams.entrySet()) {
                    injectPuppetParam(paramsTag, actionType, p.getKey(), p.getValue());
                }

                ActionTarget directTarget = (contextPlayer != null && ("@p".equalsIgnoreCase(combatTarget) || combatTarget.isBlank()))
                        ? ActionTarget.fromEntity(contextPlayer.getUUID())
                        : ActionTarget.fromSelector(targetSelectorStr);

                if (actionType != null) {
                    com.mojang.serialization.DataResult<?> parseResult = actionType.getCodec().parse(net.minecraft.nbt.NbtOps.INSTANCE, paramsTag);
                    if (parseResult.result().isPresent()) {
                        dispatchTyped(handlers, actionType, parseResult.result().get());
                        FracturedUtils.LOGGER.info("[MusicSequenceManager] Dispatched action '{}' to {} handler(s) with tag '{}' (target: '{}')",
                                resLoc, handlers.size(), actorTag, targetSelectorStr);
                    } else {
                        FracturedUtils.LOGGER.warn("[MusicSequenceManager] Codec parse failed for '{}': {}. Falling back to manual dispatch.",
                                resLoc, parseResult.error().map(com.mojang.serialization.DataResult.PartialResult::message).orElse("Unknown"));
                        if (actionType == ModPuppetActions.LEAP_SLAM) {
                            double slamRad = paramsTag.contains("slamRadius") ? paramsTag.getDouble("slamRadius") : 6.0;
                            float dmg = paramsTag.contains("damage") ? paramsTag.getFloat("damage") : 20.0F;
                            LeapSlamAction.LeapSlamParams fallbackParams = new LeapSlamAction.LeapSlamParams(
                                    directTarget, slamRad, dmg,
                                    effWindupTicks,
                                    effJumpTicks,
                                    effDurationTicks,
                                    effRecoveryTicks
                            );
                            for (IPuppetHandler h : handlers) {
                                h.dispatch(ModPuppetActions.LEAP_SLAM, fallbackParams);
                            }
                            FracturedUtils.LOGGER.info("[MusicSequenceManager] Dispatched fallback LeapSlam to {} handler(s)", handlers.size());
                        } else if (actionType == ModPuppetActions.ABYSSAL_BARRAGE) {
                            int wInt = paramsTag.contains("waveInterval") ? paramsTag.getInt("waveInterval") : 20;
                            double pSpeed = paramsTag.contains("projectileSpeed") ? paramsTag.getDouble("projectileSpeed") : 0.75;
                            AbyssalBarrageAction.AbyssalBarrageParams fallbackParams = new AbyssalBarrageAction.AbyssalBarrageParams(
                                    durationTicks > 0 ? durationTicks : 100, wInt, pSpeed
                            );
                            for (IPuppetHandler h : handlers) {
                                h.dispatch(ModPuppetActions.ABYSSAL_BARRAGE, fallbackParams);
                            }
                            FracturedUtils.LOGGER.info("[MusicSequenceManager] Dispatched fallback AbyssalBarrage to {} handler(s)", handlers.size());
                        }
                    }
                } else {
                    List<Entity> taggedEntities = SelectorUtils.getEntitiesByTag(server, actorTag);
                    for (Entity entity : taggedEntities) {
                        if (entity instanceof VoidHeraldBoss boss) {
                            boss.triggerOrchestratedAction(actionId, directTarget);
                            FracturedUtils.LOGGER.info("[MusicSequenceManager] Dispatched orchestrated action '{}' to VoidHeraldBoss with tag '{}'",
                                    actionId, actorTag);
                        } else if (entity instanceof IPuppetEntity puppet) {
                            puppet.getPuppetController().executeAction(resLoc, paramsTag, windupTicks, durationTicks, null);
                            FracturedUtils.LOGGER.info("[MusicSequenceManager] Dispatched legacy action '{}' to puppet entity with tag '{}'",
                                    resLoc, actorTag);
                        }
                    }
                }
            } catch (Exception e) {
                FracturedUtils.LOGGER.error("[MusicSequenceManager] Error executing puppet action", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void dispatchTyped(List<IPuppetHandler> handlers, PuppetActionType<T> actionType, Object params) {
        for (IPuppetHandler handler : handlers) {
            handler.dispatch(actionType, (T) params);
        }
    }

    private static void injectPuppetParam(CompoundTag tag, PuppetActionType<?> actionType, String key, String value) {
        if (tag == null || key == null || key.isBlank() || value == null) return;
        if (actionType != null) {
            java.util.Optional<net.dandare21.fracturedutils.puppet.fsm.ActionParameter<?>> opt = actionType.getParameter(key);
            if (opt.isPresent()) {
                opt.get().writeToTag(tag, value);
                return;
            }
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            tag.putBoolean(key, Boolean.parseBoolean(value));
        } else {
            try {
                tag.putInt(key, Integer.parseInt(value));
            } catch (NumberFormatException e1) {
                try {
                    tag.putDouble(key, Double.parseDouble(value.replace(',', '.')));
                } catch (NumberFormatException e2) {
                    tag.putString(key, value);
                }
            }
        }
    }

    private void spawnPuppetActor(MinecraftServer server, ActiveMusicSequence activeSeq, MusicSequenceEntry entry,
                                  MusicSequenceChannel channel, String actorTag, ServerPlayer contextPlayer) {
        String cmd = entry.getCommand() != null ? entry.getCommand().trim() : "";
        if (cmd.startsWith("/")) cmd = cmd.substring(1);

        String entityTypeStr = (channel != null && !channel.getEntityTypeId().isBlank())
                ? channel.getEntityTypeId()
                : "fractured_utils:void_herald";

        String actorName = (channel != null && !channel.getActorName().isBlank())
                ? channel.getActorName()
                : (channel != null && !channel.getName().isBlank() ? channel.getName() : "Puppet Actor");

        boolean disableAi = false;
        String xStr = "~";
        String yStr = "~";
        String zStr = "~";

        if (cmd.startsWith("summon")) {
            String[] parts = cmd.split("\\s+");
            if (parts.length > 1 && !parts[1].isBlank()) {
                entityTypeStr = parts[1].trim();
            }
            if (parts.length > 4) {
                xStr = parts[2].trim();
                yStr = parts[3].trim();
                zStr = parts[4].trim();
            }
        }

        if (cmd.contains("NoAI:1") || cmd.contains("NoAI:1b")) {
            disableAi = true;
        }

        if (entityTypeStr.equalsIgnoreCase("void_herald") || entityTypeStr.equalsIgnoreCase("fracturedutils:void_herald")) {
            entityTypeStr = "fractured_utils:void_herald";
        }

        // Resolve EntityType
        EntityType<?> type = null;
        ResourceLocation rl = ResourceLocation.tryParse(entityTypeStr);
        if (rl != null) {
            type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        }
        if (type == null) {
            type = ModEntities.VOID_HERALD.get();
        }

        // Resolve ServerLevel
        ServerLevel level = contextPlayer != null ? contextPlayer.serverLevel() : server.overworld();

        // Resolve Coordinates
        Vec3 basePos = contextPlayer != null ? contextPlayer.position() : new Vec3(0, 64, 0);
        float yaw = contextPlayer != null ? contextPlayer.getYRot() : 0.0F;

        double spawnX = parseCoordinate(xStr, basePos.x);
        double spawnY = parseCoordinate(yStr, basePos.y);
        double spawnZ = parseCoordinate(zStr, basePos.z);

        try {
            Entity entity = type.create(level);
            if (entity != null) {
                // Ensure chunk is loaded at spawn location
                int chunkX = SectionPos.blockToSectionCoord(spawnX);
                int chunkZ = SectionPos.blockToSectionCoord(spawnZ);
                level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);

                if (cmd.contains("{") && cmd.contains("}")) {
                    try {
                        int nbtStart = cmd.indexOf('{');
                        int nbtEnd = cmd.lastIndexOf('}');
                        if (nbtEnd > nbtStart) {
                            String nbtStr = cmd.substring(nbtStart, nbtEnd + 1);
                            CompoundTag tag = TagParser.parseTag(nbtStr);

                            // Supply the actual position and rotation to the NBT tag so Entity.load does not zero them!
                            ListTag posList = new ListTag();
                            posList.add(DoubleTag.valueOf(spawnX));
                            posList.add(DoubleTag.valueOf(spawnY));
                            posList.add(DoubleTag.valueOf(spawnZ));
                            tag.put("Pos", posList);

                            ListTag rotList = new ListTag();
                            rotList.add(FloatTag.valueOf(yaw));
                            rotList.add(FloatTag.valueOf(0.0F));
                            tag.put("Rotation", rotList);

                            entity.load(tag);
                            if (actorTag != null && !actorTag.isBlank()) entity.addTag(actorTag);
                            entity.addTag("puppet_actor");
                            if (entity instanceof Mob mob) {
                                mob.setPersistenceRequired();
                                if (disableAi) {
                                    mob.setNoAi(true);
                                    mob.getCapability(PuppetCapabilityProvider.PUPPET_HANDLER).ifPresent(h -> {
                                        h.setSuppressAi(true);
                                        h.setSuppressNavigation(true);
                                        h.setSuppressTargeting(true);
                                    });
                                }
                            }
                        }
                    } catch (Exception e) {
                        FracturedUtils.LOGGER.warn("[MusicSequenceManager] Could not parse extra NBT: {}", e.getMessage());
                    }
                }

                // Explicitly set position, rotation and head yaw after any NBT loading
                entity.moveTo(spawnX, spawnY, spawnZ, yaw, 0.0F);
                entity.setPos(spawnX, spawnY, spawnZ);
                entity.setYRot(yaw);
                entity.setXRot(0.0F);
                entity.setYHeadRot(yaw);
                entity.setYBodyRot(yaw);

                if (actorTag != null && !actorTag.isBlank()) {
                    entity.addTag(actorTag);
                }
                entity.addTag("puppet_actor");

                if (!actorName.isBlank()) {
                    entity.setCustomName(Component.literal(actorName));
                    entity.setCustomNameVisible(true);
                }

                if (entity instanceof Mob mob) {
                    mob.moveTo(spawnX, spawnY, spawnZ, yaw, 0.0F);
                    mob.setPersistenceRequired();
                    if (disableAi) {
                        mob.setNoAi(true);
                        mob.getCapability(PuppetCapabilityProvider.PUPPET_HANDLER).ifPresent(h -> {
                            h.setSuppressAi(true);
                            h.setSuppressNavigation(true);
                            h.setSuppressTargeting(true);
                        });
                    }
                }

                boolean added = level.addFreshEntity(entity);
                FracturedUtils.LOGGER.info("[MusicSequenceManager] Successfully spawned puppet actor '{}' ({}) with tag '{}' at ({}, {}, {}) [UUID: {}] in dimension {} (added: {})",
                        actorName, ForgeRegistries.ENTITY_TYPES.getKey(type), actorTag,
                        String.format(Locale.ROOT, "%.2f", spawnX), String.format(Locale.ROOT, "%.2f", spawnY), String.format(Locale.ROOT, "%.2f", spawnZ),
                        entity.getStringUUID(), level.dimension().location(), added);

                if (contextPlayer != null) {
                    contextPlayer.sendSystemMessage(Component.literal("🎭 Spawned Puppet '" + actorName + "' [#" + actorTag + "] at ("
                            + String.format(Locale.ROOT, "%.1f, %.1f, %.1f", spawnX, spawnY, spawnZ) + ")")
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            } else {
                FracturedUtils.LOGGER.error("[MusicSequenceManager] Failed to create entity of type '{}'", entityTypeStr);
            }
        } catch (Exception e) {
            FracturedUtils.LOGGER.error("[MusicSequenceManager] Direct entity spawning failed, falling back to command", e);
            CommandSourceStack sourceStack = contextPlayer != null
                    ? contextPlayer.createCommandSourceStack().withPermission(4)
                    : server.createCommandSourceStack().withPermission(4);
            try {
                server.getCommands().performPrefixedCommand(sourceStack, cmd);
                FracturedUtils.LOGGER.info("[MusicSequenceManager] Fallback command executed: '{}'", cmd);
            } catch (Exception cmdEx) {
                FracturedUtils.LOGGER.error("[MusicSequenceManager] Fallback command also failed", cmdEx);
            }
        }
    }

    private static double parseCoordinate(String coordStr, double baseVal) {
        if (coordStr == null || coordStr.isBlank() || coordStr.equals("~")) {
            return baseVal;
        }
        coordStr = coordStr.trim().replace(',', '.');
        if (coordStr.startsWith("~")) {
            String offsetStr = coordStr.substring(1).trim();
            if (offsetStr.isEmpty()) return baseVal;
            try {
                return baseVal + Double.parseDouble(offsetStr);
            } catch (NumberFormatException e) {
                return baseVal;
            }
        }
        try {
            return Double.parseDouble(coordStr);
        } catch (NumberFormatException e) {
            return baseVal;
        }
    }

    public List<ActiveMusicSequence> getActiveSequences() {
        return activeSequences;
    }
}
