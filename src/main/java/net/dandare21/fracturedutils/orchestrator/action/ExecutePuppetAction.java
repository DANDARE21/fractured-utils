package net.dandare21.fracturedutils.orchestrator.action;

import net.dandare21.fracturedutils.orchestrator.SequenceInstance;
import net.dandare21.fracturedutils.puppet.IPuppetEntity;
import net.dandare21.fracturedutils.util.SelectorUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.List;

public class ExecutePuppetAction implements OrchestratorAction {
    private String type = "puppet_action";
    private String actionId = "";
    private String entityUuid = "";
    private String targetSelector = "";
    private int windupTicks = 0;
    private int durationTicks = 0;
    private CompoundTag params = new CompoundTag();

    public ExecutePuppetAction() {
        this.actionId = "";
        this.entityUuid = "";
        this.targetSelector = "";
        this.windupTicks = 0;
        this.durationTicks = 0;
        this.params = new CompoundTag();
    }

    public ExecutePuppetAction(String actionId, String entityUuid, String targetSelector, int windupTicks, int durationTicks, CompoundTag params) {
        this.actionId = actionId != null ? actionId : "";
        this.entityUuid = entityUuid != null ? entityUuid : "";
        this.targetSelector = targetSelector != null ? targetSelector : "";
        this.windupTicks = windupTicks;
        this.durationTicks = durationTicks;
        this.params = params != null ? params.copy() : new CompoundTag();
    }

    public ExecutePuppetAction(String actionId, String entityUuid, String targetSelector, int durationTicks) {
        this(actionId, entityUuid, targetSelector, 0, durationTicks, new CompoundTag());
    }

    public ExecutePuppetAction(String actionId, String entityUuid, int durationTicks) {
        this(actionId, entityUuid, "", 0, durationTicks, new CompoundTag());
    }

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId != null ? actionId : ""; }

    public String getEntityUuid() { return entityUuid; }
    public void setEntityUuid(String entityUuid) { this.entityUuid = entityUuid != null ? entityUuid : ""; }

    public String getTargetSelector() { return targetSelector; }
    public void setTargetSelector(String targetSelector) { this.targetSelector = targetSelector != null ? targetSelector : ""; }

    public int getWindupTicks() { return windupTicks; }
    public void setWindupTicks(int windupTicks) { this.windupTicks = windupTicks; }

    public int getDurationTicks() { return durationTicks; }
    public void setDurationTicks(int durationTicks) { this.durationTicks = durationTicks; }

    public CompoundTag getParams() { return params; }
    public void setParams(CompoundTag params) { this.params = params != null ? params.copy() : new CompoundTag(); }

    @Override
    public ActionResult execute(SequenceInstance instance, MinecraftServer server) {
        if (server == null || actionId == null || actionId.isBlank()) {
            return ActionResult.SUCCESS;
        }

        ResourceLocation resLoc = ResourceLocation.tryParse(actionId);
        if (resLoc == null) {
            return ActionResult.SUCCESS;
        }

        List<IPuppetEntity> targets = SelectorUtils.getPuppetEntities(server, entityUuid, targetSelector);
        for (IPuppetEntity puppetEntity : targets) {
            puppetEntity.getPuppetController().executeAction(resLoc, params != null ? params : new CompoundTag(), windupTicks, durationTicks, null);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public String getType() {
        return "puppet_action";
    }

    @Override
    public OrchestratorAction copy() {
        return new ExecutePuppetAction(this.actionId, this.entityUuid, this.targetSelector, this.windupTicks, this.durationTicks, this.params);
    }
}
