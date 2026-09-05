package net.dandare21.fracturedutils.orchestrator.action;

import net.dandare21.fracturedutils.orchestrator.SequenceInstance;
import net.dandare21.fracturedutils.puppet.IPuppetEntity;
import net.dandare21.fracturedutils.util.SelectorUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.List;

public class PuppetLookAtAction implements OrchestratorAction {
    private String type = "puppet_look_at";
    private String entityUuid = "";
    private String targetSelector = "";
    private double x = 0.0;
    private double y = 64.0;
    private double z = 0.0;
    private String lookTargetSelector = "";

    public PuppetLookAtAction() {
        this.type = "puppet_look_at";
    }

    public PuppetLookAtAction(double x, double y, double z, String lookTargetSelector, String entityUuid, String targetSelector) {
        this.type = "puppet_look_at";
        this.x = x;
        this.y = y;
        this.z = z;
        this.lookTargetSelector = lookTargetSelector != null ? lookTargetSelector : "";
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

    public String getLookTargetSelector() { return lookTargetSelector; }
    public void setLookTargetSelector(String lookTargetSelector) { this.lookTargetSelector = lookTargetSelector != null ? lookTargetSelector : ""; }

    @Override
    public ActionResult execute(SequenceInstance instance, MinecraftServer server) {
        if (server == null) return ActionResult.SUCCESS;

        List<IPuppetEntity> targets = SelectorUtils.getPuppetEntities(server, entityUuid, targetSelector);
        if (targets.isEmpty()) return ActionResult.SUCCESS;

        Entity lookTargetEntity = null;
        if (lookTargetSelector != null && !lookTargetSelector.isBlank()) {
            List<Entity> matches = SelectorUtils.getTargetEntities(server, lookTargetSelector);
            if (!matches.isEmpty()) {
                lookTargetEntity = matches.get(0);
            }
        }

        for (IPuppetEntity puppetEntity : targets) {
            if (lookTargetEntity != null) {
                puppetEntity.getPuppetController().forceLookAt(lookTargetEntity);
            } else {
                puppetEntity.getPuppetController().forceLookAt(x, y, z);
            }
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public String getType() {
        return "puppet_look_at";
    }

    @Override
    public OrchestratorAction copy() {
        return new PuppetLookAtAction(x, y, z, lookTargetSelector, entityUuid, targetSelector);
    }
}
