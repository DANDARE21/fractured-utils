package net.dandare21.fracturedutils.orchestrator.action;

import net.dandare21.fracturedutils.orchestrator.SequenceInstance;
import net.dandare21.fracturedutils.puppet.IPuppetEntity;
import net.dandare21.fracturedutils.util.SelectorUtils;
import net.minecraft.server.MinecraftServer;

import java.util.List;

public class PuppetStopAction implements OrchestratorAction {
    private String type = "puppet_stop_action";
    private String entityUuid = "";
    private String targetSelector = "";

    public PuppetStopAction() {
        this.type = "puppet_stop_action";
    }

    public PuppetStopAction(String entityUuid, String targetSelector) {
        this.type = "puppet_stop_action";
        this.entityUuid = entityUuid != null ? entityUuid : "";
        this.targetSelector = targetSelector != null ? targetSelector : "";
    }

    public String getEntityUuid() { return entityUuid; }
    public void setEntityUuid(String entityUuid) { this.entityUuid = entityUuid != null ? entityUuid : ""; }

    public String getTargetSelector() { return targetSelector; }
    public void setTargetSelector(String targetSelector) { this.targetSelector = targetSelector != null ? targetSelector : ""; }

    @Override
    public ActionResult execute(SequenceInstance instance, MinecraftServer server) {
        if (server == null) return ActionResult.SUCCESS;

        List<net.dandare21.fracturedutils.puppet.capability.IPuppetHandler> handlers =
                SelectorUtils.getPuppetHandlers(server, entityUuid, targetSelector);
        for (net.dandare21.fracturedutils.puppet.capability.IPuppetHandler handler : handlers) {
            handler.stopActiveAction();
        }

        List<IPuppetEntity> targets = SelectorUtils.getPuppetEntities(server, entityUuid, targetSelector);
        for (IPuppetEntity puppetEntity : targets) {
            puppetEntity.getPuppetController().stopAction();
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public String getType() {
        return "puppet_stop_action";
    }

    @Override
    public OrchestratorAction copy() {
        return new PuppetStopAction(entityUuid, targetSelector);
    }
}
