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
        if (resLoc == null && !actionId.contains(":")) {
            resLoc = new ResourceLocation(net.dandare21.fracturedutils.FracturedUtils.MOD_ID, actionId);
        }
        if (resLoc == null) {
            return ActionResult.SUCCESS;
        }

        // 1. Check v2.0 Capability Handlers & Global Registry
        List<net.dandare21.fracturedutils.puppet.capability.IPuppetHandler> handlers =
                SelectorUtils.getPuppetHandlers(server, entityUuid, targetSelector);
        net.dandare21.fracturedutils.puppet.fsm.PuppetActionType<?> actionType =
                net.dandare21.fracturedutils.puppet.registry.ModPuppetActions.get(resLoc);
        if (actionType == null && !actionId.contains(":")) {
            actionType = net.dandare21.fracturedutils.puppet.registry.ModPuppetActions.get(
                    new ResourceLocation(net.dandare21.fracturedutils.FracturedUtils.MOD_ID, actionId));
        }

        if (!handlers.isEmpty() && actionType != null) {
            CompoundTag resolvedParams = this.params != null ? this.params.copy() : new CompoundTag();

            // Auto-inject target if not provided
            if (!resolvedParams.contains("target")) {
                if (targetSelector != null && !targetSelector.isBlank() && !targetSelector.equals("@e")) {
                    resolvedParams.putString("target", targetSelector);
                } else if (instance != null && instance.getTargetPlayerName() != null && !instance.getTargetPlayerName().isBlank()) {
                    resolvedParams.putString("target", instance.getTargetPlayerName());
                } else {
                    resolvedParams.putString("target", "@p");
                }
            }

            if (windupTicks > 0 && !resolvedParams.contains("windupTicks")) {
                resolvedParams.putInt("windupTicks", windupTicks);
            }

            dispatchToHandlers(handlers, actionType, resolvedParams);
            return ActionResult.SUCCESS;
        }

        // 2. Fallback to legacy IPuppetEntity controller
        List<IPuppetEntity> targets = SelectorUtils.getPuppetEntities(server, entityUuid, targetSelector);
        for (IPuppetEntity puppetEntity : targets) {
            puppetEntity.getPuppetController().executeAction(resLoc, params != null ? params : new CompoundTag(), windupTicks, durationTicks, null);
        }

        return ActionResult.SUCCESS;
    }

    private static <T> void dispatchToHandlers(List<net.dandare21.fracturedutils.puppet.capability.IPuppetHandler> handlers,
                                               net.dandare21.fracturedutils.puppet.fsm.PuppetActionType<T> actionType,
                                               CompoundTag paramsTag) {
        com.mojang.serialization.DataResult<T> parseResult = actionType.getCodec().parse(net.minecraft.nbt.NbtOps.INSTANCE, paramsTag);
        if (parseResult.result().isPresent()) {
            T typedParams = parseResult.result().get();
            for (net.dandare21.fracturedutils.puppet.capability.IPuppetHandler handler : handlers) {
                handler.dispatch(actionType, typedParams);
            }
        } else {
            net.dandare21.fracturedutils.FracturedUtils.LOGGER.warn("[ExecutePuppetAction] Failed to parse params for action '{}': {}",
                    actionType.getId(), parseResult.error().map(com.mojang.serialization.DataResult.PartialResult::message).orElse("Unknown error"));
        }
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
