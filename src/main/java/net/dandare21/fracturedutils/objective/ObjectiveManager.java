package net.dandare21.fracturedutils.objective;

import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.S2CSyncObjectivePacket;
import net.dandare21.fracturedutils.orchestrator.SequenceInstance;
import net.dandare21.fracturedutils.orchestrator.SequenceState;
import net.dandare21.fracturedutils.orchestrator.action.AwaitTriggerAction;
import net.dandare21.fracturedutils.orchestrator.action.DelayAction;
import net.dandare21.fracturedutils.orchestrator.action.OrchestratorAction;
import net.dandare21.fracturedutils.orchestrator.action.WaitUntilAction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ObjectiveManager {
    private static final ObjectiveManager INSTANCE = new ObjectiveManager();

    public static ObjectiveManager getInstance() {
        return INSTANCE;
    }

    public static class ObjectiveEntry {
        private final SequenceInstance sequenceInstance;
        private final String name;
        private final String description;
        private final boolean showActiveWait;
        private final String targetPlayerName;
        private String lastSyncedWaitText = "";

        public ObjectiveEntry(SequenceInstance sequenceInstance, String name, String description, boolean showActiveWait, String targetPlayerName) {
            this.sequenceInstance = sequenceInstance;
            this.name = name != null ? name : "";
            this.description = description != null ? description : "";
            this.showActiveWait = showActiveWait;
            this.targetPlayerName = targetPlayerName != null ? targetPlayerName : "";
        }

        public SequenceInstance getSequenceInstance() {
            return sequenceInstance;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public boolean isShowActiveWait() {
            return showActiveWait;
        }

        public String getTargetPlayerName() {
            return targetPlayerName;
        }

        public String getLastSyncedWaitText() {
            return lastSyncedWaitText;
        }

        public void setLastSyncedWaitText(String text) {
            this.lastSyncedWaitText = text != null ? text : "";
        }
    }

    // Keyed by target player name (or "GLOBAL" for all players)
    private final Map<String, ObjectiveEntry> activeObjectives = new ConcurrentHashMap<>();

    private ObjectiveManager() {
    }

    public void setObjective(SequenceInstance sequenceInstance, MinecraftServer server, String name, String description, boolean showActiveWait) {
        String targetKey = getTargetKey(sequenceInstance);
        ObjectiveEntry entry = new ObjectiveEntry(sequenceInstance, name, description, showActiveWait, targetKey);
        activeObjectives.put(targetKey, entry);
        syncObjective(server, targetKey, entry, true);
    }

    public void clearObjective(SequenceInstance sequenceInstance, MinecraftServer server) {
        String targetKey = getTargetKey(sequenceInstance);
        activeObjectives.remove(targetKey);
        sendClearPacket(server, targetKey);
    }

    public void clearAllObjectives(MinecraftServer server) {
        activeObjectives.clear();
        sendClearPacket(server, "GLOBAL");
    }

    private String getTargetKey(SequenceInstance instance) {
        if (instance == null || instance.getTargetPlayerName() == null || instance.getTargetPlayerName().isBlank() || instance.getTargetPlayerName().equalsIgnoreCase("@a")) {
            return "GLOBAL";
        }
        return instance.getTargetPlayerName().toLowerCase(Locale.ROOT);
    }

    public void tick(MinecraftServer server) {
        if (server == null || activeObjectives.isEmpty()) return;

        Iterator<Map.Entry<String, ObjectiveEntry>> iterator = activeObjectives.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ObjectiveEntry> entrySet = iterator.next();
            String targetKey = entrySet.getKey();
            ObjectiveEntry entry = entrySet.getValue();

            // Auto-close objective if parent sequence has finished or completed without an end_objective action
            if (entry.getSequenceInstance() != null && entry.getSequenceInstance().getState() == SequenceState.FINISHED) {
                sendClearPacket(server, targetKey);
                iterator.remove();
                continue;
            }

            if (entry.isShowActiveWait()) {
                String currentWaitText = computeActiveWaitText(entry.getSequenceInstance());
                if (!currentWaitText.equals(entry.getLastSyncedWaitText())) {
                    entry.setLastSyncedWaitText(currentWaitText);
                    syncObjective(server, targetKey, entry, true);
                }
            }
        }
    }

    private String computeActiveWaitText(SequenceInstance rootInstance) {
        if (rootInstance == null || rootInstance.getState() == SequenceState.FINISHED) {
            return "";
        }

        OrchestratorAction activeAction = findActiveWaitAction(rootInstance);
        if (activeAction == null) {
            return "";
        }

        return formatActionWaitText(activeAction);
    }

    private OrchestratorAction findActiveWaitAction(SequenceInstance instance) {
        if (instance == null || instance.getState() == SequenceState.FINISHED) {
            return null;
        }

        for (SequenceInstance child : instance.getActiveChildren()) {
            OrchestratorAction childAction = findActiveWaitAction(child);
            if (childAction != null) {
                return childAction;
            }
        }

        List<OrchestratorAction> actions = instance.getActions();
        int idx = instance.getCurrentIndex();
        if (idx >= 0 && idx < actions.size()) {
            OrchestratorAction action = actions.get(idx);
            if (action instanceof WaitUntilAction || action instanceof DelayAction || action instanceof AwaitTriggerAction || action instanceof net.dandare21.fracturedutils.orchestrator.action.WaitForMusicSequenceAction) {
                return action;
            }
        }

        return null;
    }

    private String formatActionWaitText(OrchestratorAction action) {
        if (action instanceof WaitUntilAction wua) {
            String mode = wua.getWaitType().toLowerCase(Locale.ROOT);
            if (mode.equals("delay")) {
                int remaining = wua.getRemainingTicks();
                int ticks = remaining >= 0 ? remaining : wua.getTicks();
                return String.format(Locale.ROOT, "Countdown: %.1fs", ticks / 20.0f);
            } else if (mode.equals("operator_action")) {
                String label = wua.getLabel();
                return "Operator Action: " + (!label.isBlank() ? label : wua.getTriggerId());
            } else if (mode.equals("trigger")) {
                return "Wait for Trigger: " + wua.getTriggerId();
            } else if (mode.equals("proximity") || mode.equals("marker") || mode.equals("player_proximity") || mode.equals("area")) {
                return String.format(Locale.ROOT, "Reach Location (X: %.0f, Y: %.0f, Z: %.0f)", wua.getX(), wua.getY(), wua.getZ());
            } else if (mode.equals("video") || mode.equals("video_end") || mode.equals("cutscene") || mode.equals("cinematic")) {
                return "Wait for Cutscene";
            } else if (mode.equals("dialog") || mode.equals("dialog_end") || mode.equals("dialogs_end") || mode.equals("dialog_sequence") || mode.equals("dialog_finish")) {
                return "Wait for Dialog";
            } else if (mode.equals("waiting_room") || mode.equals("waiting_room_end") || mode.equals("waitingroom")) {
                return "Wait in Waiting Room";
            } else if (mode.equals("waiting_room_ready") || mode.equals("waitingroom_ready")) {
                return "Wait for Players Ready";
            } else if (mode.equals("downloads")) {
                return "Downloading Data...";
            } else if (mode.equals("music_sequence") || mode.equals("wait_music_sequence") || mode.equals("wait_for_music_sequence") || mode.equals("music_sequence_end") || mode.equals("music_end")) {
                return !wua.getTriggerId().isBlank() ? ("Wait for Music: " + wua.getTriggerId()) : "Wait for Music Sequence";
            } else {
                return "Wait: " + wua.getWaitType();
            }
        } else if (action instanceof DelayAction da) {
            int remaining = da.getRemainingTicks();
            int ticks = remaining >= 0 ? remaining : da.getTicks();
            return String.format(Locale.ROOT, "Countdown: %.1fs", ticks / 20.0f);
        } else if (action instanceof AwaitTriggerAction ata) {
            return "Wait for Trigger: " + ata.getTriggerId();
        } else if (action instanceof net.dandare21.fracturedutils.orchestrator.action.WaitForMusicSequenceAction wfms) {
            return !wfms.getSequenceFile().isBlank() ? ("Wait for Music: " + wfms.getSequenceFile()) : "Wait for Music Sequence";
        }
        return "";
    }

    private void syncObjective(MinecraftServer server, String targetKey, ObjectiveEntry entry, boolean active) {
        if (server == null) return;
        String waitText = "";
        String waitType = "";
        double x = 0.0, y = 0.0, z = 0.0;
        int remainingTicks = -1;

        if (active && entry.isShowActiveWait()) {
            OrchestratorAction activeAction = findActiveWaitAction(entry.getSequenceInstance());
            if (activeAction != null) {
                waitText = formatActionWaitText(activeAction);
                if (activeAction instanceof WaitUntilAction wua) {
                    String mode = wua.getWaitType().toLowerCase(Locale.ROOT);
                    if (mode.equals("delay")) {
                        waitType = "delay";
                        remainingTicks = wua.getRemainingTicks() >= 0 ? wua.getRemainingTicks() : wua.getTicks();
                    } else if (mode.equals("proximity") || mode.equals("marker") || mode.equals("player_proximity") || mode.equals("area")) {
                        waitType = "marker";
                        x = wua.getX();
                        y = wua.getY();
                        z = wua.getZ();
                    } else {
                        waitType = mode;
                    }
                } else if (activeAction instanceof DelayAction da) {
                    waitType = "delay";
                    remainingTicks = da.getRemainingTicks() >= 0 ? da.getRemainingTicks() : da.getTicks();
                } else if (activeAction instanceof AwaitTriggerAction) {
                    waitType = "trigger";
                }
            }
        }

        S2CSyncObjectivePacket packet = new S2CSyncObjectivePacket(
                active,
                entry.getName(),
                entry.getDescription(),
                waitText,
                waitType,
                x, y, z,
                remainingTicks
        );

        if ("GLOBAL".equalsIgnoreCase(targetKey)) {
            ModMessages.sendToAllPlayers(packet);
        } else {
            ServerPlayer player = server.getPlayerList().getPlayerByName(targetKey);
            if (player != null) {
                ModMessages.sendToPlayer(packet, player);
            } else {
                ModMessages.sendToAllPlayers(packet);
            }
        }
    }

    private void sendClearPacket(MinecraftServer server, String targetKey) {
        if (server == null) return;
        S2CSyncObjectivePacket packet = new S2CSyncObjectivePacket(false, "", "", "");
        if ("GLOBAL".equalsIgnoreCase(targetKey)) {
            ModMessages.sendToAllPlayers(packet);
        } else {
            ServerPlayer player = server.getPlayerList().getPlayerByName(targetKey);
            if (player != null) {
                ModMessages.sendToPlayer(packet, player);
            } else {
                ModMessages.sendToAllPlayers(packet);
            }
        }
    }
}
