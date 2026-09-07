package net.dandare21.fracturedutils.orchestrator.action;

import net.dandare21.fracturedutils.orchestrator.OrchestratorManager;
import net.dandare21.fracturedutils.orchestrator.SequenceInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;

import java.util.UUID;

public class WaitUntilAction implements OrchestratorAction {
    private String type = "wait_until";
    private String waitType = "delay"; // "delay", "trigger", "operator_action", "downloads", "proximity"
    private int ticks = 20;
    private String triggerId = "";
    private String label = "";
    private double x = 0.0;
    private double y = 0.0;
    private double z = 0.0;
    private double radius = 3.0;
    private boolean requireAllPlayers = false;
    private boolean opsOnlyVisibility = true;
    private boolean areaOpsOnlyVisibility = true;
    private boolean showRadiusArea = true;
    private String targetSelector = "@a";

    private transient int remainingTicks = -1;
    private transient boolean triggered = false;
    private transient boolean hasSeenActive = false;
    private transient int graceTicks = 0;
    private transient UUID markerUUID = null;

    public WaitUntilAction() {
        this.type = "wait_until";
        this.waitType = "delay";
        this.ticks = 20;
        this.triggerId = "";
        this.label = "";
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
        this.radius = 3.0;
        this.requireAllPlayers = false;
        this.opsOnlyVisibility = true;
        this.areaOpsOnlyVisibility = true;
        this.showRadiusArea = true;
    }

    public WaitUntilAction(String waitType, int ticks, String triggerId, String label) {
        this.type = "wait_until";
        this.waitType = waitType != null ? waitType : "delay";
        this.ticks = Math.max(0, ticks);
        this.triggerId = triggerId != null ? triggerId : "";
        this.label = label != null ? label : "";
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
        this.radius = 3.0;
        this.requireAllPlayers = false;
        this.opsOnlyVisibility = true;
        this.areaOpsOnlyVisibility = true;
        this.showRadiusArea = true;
    }

    public WaitUntilAction(String waitType, double x, double y, double z, double radius, boolean requireAllPlayers, boolean opsOnlyVisibility, boolean showRadiusArea) {
        this(waitType, x, y, z, radius, requireAllPlayers, opsOnlyVisibility, true, showRadiusArea);
    }

    public WaitUntilAction(String waitType, double x, double y, double z, double radius, boolean requireAllPlayers, boolean opsOnlyVisibility, boolean areaOpsOnlyVisibility, boolean showRadiusArea) {
        this.type = "wait_until";
        this.waitType = waitType != null ? waitType : "proximity";
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = Math.max(0.1, radius);
        this.requireAllPlayers = requireAllPlayers;
        this.opsOnlyVisibility = opsOnlyVisibility;
        this.areaOpsOnlyVisibility = areaOpsOnlyVisibility;
        this.showRadiusArea = showRadiusArea;
    }

    public String getWaitType() {
        return waitType != null ? waitType : "delay";
    }

    public void setWaitType(String waitType) {
        this.waitType = waitType != null ? waitType : "delay";
    }

    public int getTicks() {
        return ticks;
    }

    public void setTicks(int ticks) {
        this.ticks = Math.max(0, ticks);
    }

    public String getTriggerId() {
        return triggerId != null ? triggerId : "";
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId != null ? triggerId : "";
    }

    public String getLabel() {
        return label != null ? label : "";
    }

    public void setLabel(String label) {
        this.label = label != null ? label : "";
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = Math.max(0.1, radius);
    }

    public boolean isRequireAllPlayers() {
        return requireAllPlayers;
    }

    public void setRequireAllPlayers(boolean requireAllPlayers) {
        this.requireAllPlayers = requireAllPlayers;
    }

    public boolean isOpsOnlyVisibility() {
        return opsOnlyVisibility;
    }

    public void setOpsOnlyVisibility(boolean opsOnlyVisibility) {
        this.opsOnlyVisibility = opsOnlyVisibility;
    }

    public boolean isAreaOpsOnlyVisibility() {
        return areaOpsOnlyVisibility;
    }

    public void setAreaOpsOnlyVisibility(boolean areaOpsOnlyVisibility) {
        this.areaOpsOnlyVisibility = areaOpsOnlyVisibility;
    }

    public boolean isShowRadiusArea() {
        return showRadiusArea;
    }

    public void setShowRadiusArea(boolean showRadiusArea) {
        this.showRadiusArea = showRadiusArea;
    }

    public String getTargetSelector() {
        return targetSelector != null && !targetSelector.isBlank() ? targetSelector : "@a";
    }

    public void setTargetSelector(String targetSelector) {
        this.targetSelector = (targetSelector != null && !targetSelector.isBlank()) ? targetSelector : "@a";
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public void trigger() {
        this.triggered = true;
    }

    public boolean isTriggered() {
        return triggered;
    }

    private void cleanupMarker(ServerLevel level) {
        if (markerUUID != null && level != null) {
            Entity entity = level.getEntity(markerUUID);
            if (entity != null) {
                entity.discard();
            }
            markerUUID = null;
        }
    }

    @Override
    public ActionResult execute(SequenceInstance instance, MinecraftServer server) {
        String mode = getWaitType().toLowerCase();

        if (mode.equals("delay")) {
            if (remainingTicks < 0) {
                remainingTicks = ticks;
            }
            if (remainingTicks <= 0) {
                remainingTicks = -1;
                return ActionResult.SUCCESS;
            }
            remainingTicks--;
            return ActionResult.BLOCK;
        } else if (mode.equals("operator_action")) {
            String effectiveTrigger = (triggerId != null && !triggerId.isBlank()) ? triggerId : ("op_action_" + instance.getSequenceName());
            if (triggered) {
                OrchestratorManager.getInstance().unregisterOperatorAction(server, effectiveTrigger);
                triggered = false;
                return ActionResult.SUCCESS;
            }
            OrchestratorManager.getInstance().registerOperatorAction(server, effectiveTrigger, (label != null && !label.isBlank()) ? label : ("Resume " + instance.getSequenceName()));
            return ActionResult.BLOCK;
        } else if (mode.equals("puppet_action") || mode.equals("puppet_action_end") || mode.equals("puppet_idle") || mode.equals("puppet_finish") || mode.equals("puppet_action_finish")) {
            String targetUuid = (triggerId != null && !triggerId.isBlank()) ? triggerId : "";
            java.util.List<net.dandare21.fracturedutils.puppet.capability.IPuppetHandler> handlers =
                    net.dandare21.fracturedutils.util.SelectorUtils.getPuppetHandlers(server, targetUuid, targetSelector);

            boolean anyActive = false;
            for (net.dandare21.fracturedutils.puppet.capability.IPuppetHandler handler : handlers) {
                if (handler.isPuppetingActive() || handler.getActiveAction() != null) {
                    anyActive = true;
                    break;
                }
            }

            if (!anyActive) {
                java.util.List<net.dandare21.fracturedutils.puppet.IPuppetEntity> legacyPuppets =
                        net.dandare21.fracturedutils.util.SelectorUtils.getPuppetEntities(server, targetUuid, targetSelector);
                for (net.dandare21.fracturedutils.puppet.IPuppetEntity puppet : legacyPuppets) {
                    if (puppet.getPuppetController() != null && puppet.getPuppetController().isPuppetingActive()) {
                        anyActive = true;
                        break;
                    }
                }
            }

            if (anyActive) {
                hasSeenActive = true;
                return ActionResult.BLOCK;
            }
            if (hasSeenActive) {
                hasSeenActive = false;
                graceTicks = 0;
                return ActionResult.SUCCESS;
            }
            graceTicks++;
            if (graceTicks < 5) {
                return ActionResult.BLOCK;
            }
            graceTicks = 0;
            return ActionResult.SUCCESS;
        } else if (mode.equals("video") || mode.equals("video_end") || mode.equals("cutscene") || mode.equals("cinematic")) {
            boolean activeNow = net.dandare21.fracturedutils.cutscene.ServerCutsceneManager.getInstance().isCutsceneActive();
            if (activeNow) {
                hasSeenActive = true;
                return ActionResult.BLOCK;
            }
            if (hasSeenActive) {
                hasSeenActive = false;
                graceTicks = 0;
                return ActionResult.SUCCESS;
            }
            graceTicks++;
            if (graceTicks < 20) {
                return ActionResult.BLOCK;
            }
            graceTicks = 0;
            return ActionResult.SUCCESS;
        } else if (mode.equals("waiting_room") || mode.equals("waiting_room_end") || mode.equals("waitingroom")) {
            boolean activeNow = net.dandare21.fracturedutils.waitingroom.WaitingRoomManager.getInstance().isActive();
            if (activeNow) {
                hasSeenActive = true;
                return ActionResult.BLOCK;
            }
            if (hasSeenActive) {
                hasSeenActive = false;
                graceTicks = 0;
                return ActionResult.SUCCESS;
            }
            graceTicks++;
            if (graceTicks < 20) {
                return ActionResult.BLOCK;
            }
            graceTicks = 0;
            return ActionResult.SUCCESS;
        } else if (mode.equals("dialog") || mode.equals("dialog_end") || mode.equals("dialogs_end") || mode.equals("dialog_sequence") || mode.equals("dialog_finish")) {
            boolean activeNow = (triggerId != null && !triggerId.isBlank())
                    ? net.dandare21.fracturedutils.dialog.DialogManager.getInstance().isSequenceRunning(triggerId)
                    : net.dandare21.fracturedutils.dialog.DialogManager.getInstance().isSequenceRunning();
            if (activeNow) {
                hasSeenActive = true;
                return ActionResult.BLOCK;
            }
            if (hasSeenActive) {
                hasSeenActive = false;
                graceTicks = 0;
                return ActionResult.SUCCESS;
            }
            graceTicks++;
            if (graceTicks < 10) {
                return ActionResult.BLOCK;
            }
            graceTicks = 0;
            return ActionResult.SUCCESS;
        } else if (mode.equals("downloads") || mode.equals("downloads_end") || mode.equals("cutscene_downloads") || mode.equals("video_downloads")) {
            boolean allFinished = net.dandare21.fracturedutils.cutscene.ServerCutsceneManager.getInstance().areAllDownloadsComplete(server);
            if (!allFinished) {
                hasSeenActive = true;
                return ActionResult.BLOCK;
            }
            if (hasSeenActive) {
                hasSeenActive = false;
                graceTicks = 0;
                return ActionResult.SUCCESS;
            }
            graceTicks++;
            if (graceTicks < 10) {
                return ActionResult.BLOCK;
            }
            graceTicks = 0;
            return ActionResult.SUCCESS;
        } else if (mode.equals("waiting_room_ready") || mode.equals("waiting_room_all_ready") || mode.equals("waitingroom_ready")) {
            boolean allReady = net.dandare21.fracturedutils.waitingroom.WaitingRoomManager.getInstance().areAllPlayersReady(server);
            if (!allReady) {
                hasSeenActive = true;
                return ActionResult.BLOCK;
            }
            if (hasSeenActive) {
                hasSeenActive = false;
                graceTicks = 0;
                return ActionResult.SUCCESS;
            }
            graceTicks++;
            if (graceTicks < 10) {
                return ActionResult.BLOCK;
            }
            graceTicks = 0;
            return ActionResult.SUCCESS;
        } else if (mode.equals("proximity") || mode.equals("marker") || mode.equals("player_proximity") || mode.equals("area")) {
            ServerLevel level = null;
            String targetName = instance.getTargetPlayerName();
            ServerPlayer targetPlayer = (targetName != null && !targetName.isBlank())
                    ? server.getPlayerList().getPlayerByName(targetName)
                    : null;

            if (targetPlayer != null) {
                level = targetPlayer.serverLevel();
            } else if (server != null) {
                level = server.overworld();
            }

            if (level == null) {
                return ActionResult.BLOCK;
            }

            // Spawn marker entity if not spawned yet
            if (markerUUID == null) {
                Marker marker = new Marker(EntityType.MARKER, level);
                marker.setPos(x, y, z);
                level.addFreshEntity(marker);
                this.markerUUID = marker.getUUID();
            }

            // Check if player is within radius
            double effectiveRadius = radius > 0 ? radius : 3.0;
            double radSq = effectiveRadius * effectiveRadius;
            boolean conditionMet = false;

            java.util.List<ServerPlayer> matchingPlayers = net.dandare21.fracturedutils.util.SelectorUtils.getTargetPlayers(server, getTargetSelector());
            java.util.List<ServerPlayer> players = new java.util.ArrayList<>();
            for (ServerPlayer p : level.players()) {
                if (matchingPlayers.contains(p)) {
                    players.add(p);
                }
            }

            if (requireAllPlayers) {
                if (!players.isEmpty()) {
                    boolean allInside = true;
                    for (ServerPlayer player : players) {
                        if (player.distanceToSqr(x, y, z) > radSq) {
                            allInside = false;
                            break;
                        }
                    }
                    conditionMet = allInside;
                }
            } else {
                if (targetPlayer != null && targetPlayer.level() == level) {
                    conditionMet = (targetPlayer.distanceToSqr(x, y, z) <= radSq);
                } else {
                    for (ServerPlayer player : players) {
                        if (player.distanceToSqr(x, y, z) <= radSq) {
                            conditionMet = true;
                            break;
                        }
                    }
                }
            }

            if (conditionMet) {
                cleanupMarker(level);
                return ActionResult.SUCCESS;
            } else {
                // Emit static marker particle at target location (like barrier block)
                if (level.getGameTime() % 10 == 0) {
                    if (opsOnlyVisibility) {
                        for (ServerPlayer player : level.players()) {
                            if (player != null && player.hasPermissions(2)) {
                                level.sendParticles(player, net.dandare21.fracturedutils.particle.ModParticles.MARKER_PARTICLE.get(), false, x, y + 0.5, z, 1, 0.0, 0.0, 0.0, 0.0);
                            }
                        }
                    } else {
                        level.sendParticles(net.dandare21.fracturedutils.particle.ModParticles.MARKER_PARTICLE.get(), x, y + 0.5, z, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
                return ActionResult.BLOCK;
            }
        } else {
            // "trigger"
            if (triggered) {
                triggered = false;
                return ActionResult.SUCCESS;
            }
            return ActionResult.BLOCK;
        }
    }

    @Override
    public String getType() {
        return "wait_until";
    }

    @Override
    public OrchestratorAction copy() {
        WaitUntilAction copy = new WaitUntilAction(this.waitType, this.ticks, this.triggerId, this.label);
        copy.setX(this.x);
        copy.setY(this.y);
        copy.setZ(this.z);
        copy.setRadius(this.radius);
        copy.setRequireAllPlayers(this.requireAllPlayers);
        copy.setOpsOnlyVisibility(this.opsOnlyVisibility);
        copy.setAreaOpsOnlyVisibility(this.areaOpsOnlyVisibility);
        copy.setShowRadiusArea(this.showRadiusArea);
        return copy;
    }
}
