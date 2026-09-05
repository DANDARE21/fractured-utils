package net.dandare21.fracturedutils.orchestrator.action;

import net.dandare21.fracturedutils.orchestrator.SequenceInstance;
import net.dandare21.fracturedutils.puppet.IPuppetEntity;
import net.dandare21.fracturedutils.util.SelectorUtils;
import net.minecraft.server.MinecraftServer;

import java.util.List;

public class PuppetMoveToAction implements OrchestratorAction {
    private String type = "puppet_move_to";
    private String entityUuid = "";
    private String targetSelector = "";
    private double x = 0.0;
    private double y = 64.0;
    private double z = 0.0;
    private double speed = 1.0;

    public PuppetMoveToAction() {
        this.type = "puppet_move_to";
    }

    public PuppetMoveToAction(double x, double y, double z, double speed, String entityUuid, String targetSelector) {
        this.type = "puppet_move_to";
        this.x = x;
        this.y = y;
        this.z = z;
        this.speed = speed > 0 ? speed : 1.0;
        this.entityUuid = entityUuid != null ? entityUuid : "";
        this.targetSelector = targetSelector != null ? targetSelector : "";
    }

    public String getEntityUuid() { return entityUuid; }
    public void setEntityUuid(String entityUuid) { this.entityUuid = entityUuid != null ? entityUuid : ""; }

    public String getTargetSelector() { return targetSelector; }
    public void setTargetSelector(String targetSelector) { this.targetSelector = targetSelector != null ? targetSelector : ""; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed > 0 ? speed : 1.0; }

    @Override
    public ActionResult execute(SequenceInstance instance, MinecraftServer server) {
        if (server == null) return ActionResult.SUCCESS;

        List<IPuppetEntity> targets = SelectorUtils.getPuppetEntities(server, entityUuid, targetSelector);
        for (IPuppetEntity puppetEntity : targets) {
            puppetEntity.getPuppetController().forceMoveTo(x, y, z, speed);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public String getType() {
        return "puppet_move_to";
    }

    @Override
    public OrchestratorAction copy() {
        return new PuppetMoveToAction(x, y, z, speed, entityUuid, targetSelector);
    }
}
