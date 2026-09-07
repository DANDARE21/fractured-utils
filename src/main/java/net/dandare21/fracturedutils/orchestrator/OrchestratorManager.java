package net.dandare21.fracturedutils.orchestrator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.S2CSyncSequenceTelemetryPacket;

import net.dandare21.fracturedutils.orchestrator.action.*;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class OrchestratorManager {
    private static final OrchestratorManager INSTANCE = new OrchestratorManager();
    private final List<SequenceInstance> activeRootSequences = new CopyOnWriteArrayList<>();
    private final Map<String, String> activeOperatorActions = new ConcurrentHashMap<>();

    public void registerOperatorAction(MinecraftServer server, String triggerId, String label) {
        if (triggerId == null || triggerId.isEmpty()) return;
        if (!activeOperatorActions.containsKey(triggerId)) {
            activeOperatorActions.put(triggerId, label != null ? label : "Resume Sequence");
            syncOperatorActionsToOps(server);
        }
    }

    public void unregisterOperatorAction(MinecraftServer server, String triggerId) {
        if (triggerId == null || triggerId.isEmpty()) return;
        if (activeOperatorActions.remove(triggerId) != null) {
            syncOperatorActionsToOps(server);
        }
    }

    public Map<String, String> getActiveOperatorActions() {
        return activeOperatorActions;
    }

    public void syncOperatorActionsToOps(MinecraftServer server) {
        if (server == null) return;
        net.dandare21.fracturedutils.network.packet.S2CSyncOperatorActionsPacket packet =
                new net.dandare21.fracturedutils.network.packet.S2CSyncOperatorActionsPacket(activeOperatorActions);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && player.hasPermissions(2)) {
                ModMessages.sendToPlayer(packet, player);
            }
        }
    }

    private static final Gson GSON = ActionAdapter.registerAll(new GsonBuilder())
            .setPrettyPrinting()
            .create();

    public static OrchestratorManager getInstance() {
        return INSTANCE;
    }

    private OrchestratorManager() {
        ensureDirectoryExists();
    }

    public File getDirectory() {
        File dir = FMLPaths.CONFIGDIR.get().resolve("command_orchestrator").toFile();
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

    public SequenceInstance createSequenceInstance(String fileName, String targetPlayerName, SequenceInstance parent) {
        return createSequenceInstance(fileName, targetPlayerName, parent, 0);
    }

    public SequenceInstance createSequenceInstance(String fileName, String targetPlayerName, SequenceInstance parent, int startIndex) {
        String cleanName = sanitizeFileName(fileName);
        File file = new File(getDirectory(), cleanName);

        if (!file.exists()) {
            FracturedUtils.LOGGER.error("Orchestrator file not found: {}", file.getAbsolutePath());
            return null;
        }

        try (FileReader reader = new FileReader(file)) {
            List<OrchestratorAction> actions = GSON.fromJson(reader, new TypeToken<List<OrchestratorAction>>() {}.getType());
            if (actions == null) {
                actions = new ArrayList<>();
            }
            return new SequenceInstance(cleanName, targetPlayerName, actions, parent, startIndex);
        } catch (Exception e) {
            FracturedUtils.LOGGER.error("Failed to parse sequence file {}: {}", cleanName, e.getMessage());
            return null;
        }
    }

    public boolean startSequence(String fileName, String targetPlayerName) {
        return startSequence(fileName, targetPlayerName, 0);
    }

    public boolean startSequence(String fileName, String targetPlayerName, int startIndex) {
        SequenceInstance instance = createSequenceInstance(fileName, targetPlayerName, null, startIndex);
        if (instance != null) {
            activeRootSequences.add(instance);
            return true;
        }
        return false;
    }

    public void tick(MinecraftServer server) {
        Iterator<SequenceInstance> iterator = activeRootSequences.iterator();
        while (iterator.hasNext()) {
            SequenceInstance sequence = iterator.next();
            sequence.tick(server);
            if (sequence.getState() == SequenceState.FINISHED) {
                activeRootSequences.remove(sequence);
            }
        }
        net.dandare21.fracturedutils.objective.ObjectiveManager.getInstance().tick(server);
        syncActiveSequenceTelemetryToOps(server);
    }

    public void syncActiveSequenceTelemetryToOps(MinecraftServer server) {
        if (server == null) return;
        List<S2CSyncSequenceTelemetryPacket.SequenceTelemetryData> telemetryList = new ArrayList<>();
        for (SequenceInstance seq : activeRootSequences) {
            collectSequenceTelemetry(seq, telemetryList);
        }

        S2CSyncSequenceTelemetryPacket packet = new S2CSyncSequenceTelemetryPacket(telemetryList);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && player.hasPermissions(2)) {
                ModMessages.sendToPlayer(packet, player);
            }
        }
    }

    private void collectSequenceTelemetry(SequenceInstance seq, List<S2CSyncSequenceTelemetryPacket.SequenceTelemetryData> list) {
        List<S2CSyncSequenceTelemetryPacket.ActionInfo> actionInfos = new ArrayList<>();
        for (OrchestratorAction action : seq.getActions()) {
            String type = action.getType();
            String details = getActionDetails(action);
            actionInfos.add(new S2CSyncSequenceTelemetryPacket.ActionInfo(type, details));
        }
        list.add(new S2CSyncSequenceTelemetryPacket.SequenceTelemetryData(
                seq.getSequenceName(),
                seq.getTargetPlayerName(),
                seq.getCurrentIndex(),
                seq.getState().name(),
                actionInfos
        ));
        for (SequenceInstance child : seq.getActiveChildren()) {
            if (child != null && child.getState() != SequenceState.FINISHED) {
                collectSequenceTelemetry(child, list);
            }
        }
    }

    private String getActionDetails(OrchestratorAction action) {
        if (action instanceof CommandAction ca) {
            return ca.getRun();
        } else if (action instanceof net.dandare21.fracturedutils.orchestrator.action.CheckpointAction cp) {
            return String.format(Locale.ROOT, "checkpoint %s:%.1f,%.1f,%.1f", cp.getTargetSelector(), cp.getX(), cp.getY(), cp.getZ());
        } else if (action instanceof DelayAction da) {
            return String.valueOf(da.getTicks());
        } else if (action instanceof WaitUntilAction wua) {
            String mode = wua.getWaitType().toLowerCase(Locale.ROOT);
            if (mode.equals("delay")) {
                return String.valueOf(wua.getTicks());
            } else if (mode.equals("operator_action")) {
                String lbl = wua.getLabel();
                return "operator_action:" + (!lbl.isBlank() ? lbl : wua.getTriggerId());
            } else if (mode.equals("trigger")) {
                return "trigger:" + wua.getTriggerId();
            } else if (mode.equals("proximity") || mode.equals("marker") || mode.equals("player_proximity") || mode.equals("area")) {
                String modeTag = wua.isRequireAllPlayers() ? "ALL" : "ANY";
                String markerVisTag = wua.isOpsOnlyVisibility() ? "Marker:Ops" : "Marker:All";
                String areaVisTag = wua.isAreaOpsOnlyVisibility() ? "Area:Ops" : "Area:All";
                return String.format(Locale.ROOT, "proximity:%.1f,%.1f,%.1f r=%.1f (%s, %s, %s, area=%b)", wua.getX(), wua.getY(), wua.getZ(), wua.getRadius(), modeTag, markerVisTag, areaVisTag, wua.isShowRadiusArea());
            } else {
                return wua.getWaitType();
            }
        } else if (action instanceof AwaitTriggerAction ata) {
            return ata.getTriggerId();
        } else if (action instanceof ForkSequenceAction fsa) {
            return fsa.getFile();
        } else if (action instanceof RunSequenceAction rsa) {
            return rsa.getFile();
        } else if (action instanceof StallParentAction) {
            return "parent";
        } else if (action instanceof ResumeParentAction) {
            return "parent";
        } else if (action instanceof ExecutePuppetAction epa) {
            return String.format(Locale.ROOT, "puppet action %s (%dt)", epa.getActionId(), epa.getDurationTicks());
        } else if (action instanceof PuppetMoveToAction pmt) {
            return String.format(Locale.ROOT, "puppet move to %.1f,%.1f,%.1f (spd=%.1f)", pmt.getX(), pmt.getY(), pmt.getZ(), pmt.getSpeed());
        } else if (action instanceof PuppetLookAtAction pla) {
            if (!pla.getLookTargetSelector().isBlank()) {
                return "puppet look at " + pla.getLookTargetSelector();
            }
            return String.format(Locale.ROOT, "puppet look at %.1f,%.1f,%.1f", pla.getX(), pla.getY(), pla.getZ());
        } else if (action instanceof PuppetSuppressAction psa) {
            return String.format(Locale.ROOT, "puppet suppress (nav=%b, tgt=%b, look=%b, actions=%b)", psa.isSuppressNavigation(), psa.isSuppressTargeting(), psa.isSuppressLook(), psa.isSuppressActions());
        } else if (action instanceof PuppetStopAction) {
            return "puppet stop action";
        }
        return "";
    }

    public void onPlayerLoggedOut(ServerPlayer player) {
        // Sequences execute on the server thread independently of player disconnects.
    }

    public boolean resumeTrigger(String playerName, String triggerId) {
        boolean resumed = false;
        for (SequenceInstance seq : activeRootSequences) {
            if (playerName == null || playerName.isEmpty() || seq.getTargetPlayerName() == null || seq.getTargetPlayerName().equalsIgnoreCase(playerName)) {
                if (seq.resumeTrigger(triggerId)) {
                    resumed = true;
                }
            }
        }
        return resumed;
    }

    public boolean resumeTrigger(String triggerId) {
        return resumeTrigger(null, triggerId);
    }

    private boolean matchesSequence(SequenceInstance seq, String playerName, String cleanFileName) {
        boolean playerMatches = (playerName == null || playerName.isEmpty() || seq.getTargetPlayerName() == null || seq.getTargetPlayerName().equalsIgnoreCase(playerName));
        boolean fileMatches = (cleanFileName == null || cleanFileName.isEmpty() || seq.getSequenceName().equalsIgnoreCase(cleanFileName));
        return playerMatches && fileMatches;
    }

    public boolean pauseSequence(String playerName, String fileName) {
        String cleanName = fileName != null && !fileName.isBlank() ? sanitizeFileName(fileName) : null;
        boolean pausedAny = false;
        for (SequenceInstance seq : activeRootSequences) {
            if (matchesSequence(seq, playerName, cleanName)) {
                seq.pause();
                pausedAny = true;
            }
        }
        return pausedAny;
    }

    public boolean cancelSequence(String playerName, String fileName) {
        String cleanName = fileName != null && !fileName.isBlank() ? sanitizeFileName(fileName) : null;
        boolean cancelledAny = false;
        Iterator<SequenceInstance> iterator = activeRootSequences.iterator();
        while (iterator.hasNext()) {
            SequenceInstance seq = iterator.next();
            if (matchesSequence(seq, playerName, cleanName)) {
                seq.cancel();
                iterator.remove();
                cancelledAny = true;
            }
        }
        return cancelledAny;
    }

    public List<String> getSequenceFileNames() {
        List<String> names = new ArrayList<>();
        File dir = getDirectory();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                names.add(f.getName());
                if (f.getName().endsWith(".json")) {
                    String withoutExt = f.getName().substring(0, f.getName().length() - 5);
                    if (!names.contains(withoutExt)) {
                        names.add(withoutExt);
                    }
                }
            }
        }
        return names;
    }

    public Map<String, String> getAllSequenceFiles() {
        Map<String, String> filesMap = new HashMap<>();
        File dir = getDirectory();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                try {
                    String content = Files.readString(f.toPath());
                    filesMap.put(f.getName(), content);
                } catch (IOException e) {
                    FracturedUtils.LOGGER.error("Failed to read file {}", f.getName(), e);
                }
            }
        }
        return filesMap;
    }

    public boolean saveSequenceFile(String fileName, String jsonContent) {
        String cleanName = sanitizeFileName(fileName);
        if (cleanName.isEmpty()) {
            return false;
        }

        try {
            List<OrchestratorAction> actions = GSON.fromJson(jsonContent, new TypeToken<List<OrchestratorAction>>() {}.getType());
            if (actions == null) {
                return false;
            }
        } catch (Exception e) {
            FracturedUtils.LOGGER.error("Invalid sequence JSON for file {}: {}", cleanName, e.getMessage(), e);
            return false;
        }

        File file = new File(getDirectory(), cleanName);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(jsonContent);
            return true;
        } catch (IOException e) {
            FracturedUtils.LOGGER.error("Failed to save sequence file {}", cleanName, e);
            return false;
        }
    }
}
