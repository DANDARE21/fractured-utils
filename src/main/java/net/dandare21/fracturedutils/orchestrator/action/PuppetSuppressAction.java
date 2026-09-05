package net.dandare21.fracturedutils.orchestrator.action;

import net.dandare21.fracturedutils.orchestrator.SequenceInstance;
import net.dandare21.fracturedutils.puppet.IPuppetEntity;
import net.dandare21.fracturedutils.puppet.PuppetController;
import net.dandare21.fracturedutils.util.SelectorUtils;
import net.minecraft.server.MinecraftServer;

import java.util.List;

public class PuppetSuppressAction implements OrchestratorAction {
    private String type = "puppet_suppress_ai";
    private String entityUuid = "";
    private String targetSelector = "";
    private boolean suppressAi = false;
    private boolean suppressNavigation = false;
    private boolean suppressTargeting = false;
    private boolean suppressLook = false;
    private boolean puppetingActive = true;

    public PuppetSuppressAction() {
        this.type = "puppet_suppress_ai";
    }

    public PuppetSuppressAction(boolean suppressAi, boolean suppressNavigation, boolean suppressTargeting, boolean suppressLook, boolean puppetingActive, String entityUuid, String targetSelector) {
        this.type = "puppet_suppress_ai";
        this.suppressAi = suppressAi;
        this.suppressNavigation = suppressNavigation;
        this.suppressTargeting = suppressTargeting;
        this.suppressLook = suppressLook;
        this.puppetingActive = puppetingActive;
        this.entityUuid = entityUuid != null ? entityUuid : "";
        this.targetSelector = targetSelector != null ? targetSelector : "";
    }

    public PuppetSuppressAction(boolean suppressNavigation, boolean suppressTargeting, boolean suppressLook, boolean puppetingActive, String entityUuid, String targetSelector) {
        this(false, suppressNavigation, suppressTargeting, suppressLook, puppetingActive, entityUuid, targetSelector);
    }

    public String getEntityUuid() { return entityUuid; }
    public void setEntityUuid(String entityUuid) { this.entityUuid = entityUuid != null ? entityUuid : ""; }

    public String getTargetSelector() { return targetSelector; }
    public void setTargetSelector(String targetSelector) { this.targetSelector = targetSelector != null ? targetSelector : ""; }

    public boolean isSuppressAi() { return suppressAi; }
    public void setSuppressAi(boolean suppressAi) { this.suppressAi = suppressAi; }

    public boolean isSuppressNavigation() { return suppressNavigation; }
    public void setSuppressNavigation(boolean suppressNavigation) { this.suppressNavigation = suppressNavigation; }

    public boolean isSuppressTargeting() { return suppressTargeting; }
    public void setSuppressTargeting(boolean suppressTargeting) { this.suppressTargeting = suppressTargeting; }

    public boolean isSuppressLook() { return suppressLook; }
    public void setSuppressLook(boolean suppressLook) { this.suppressLook = suppressLook; }

    public boolean isPuppetingActive() { return puppetingActive; }
    public void setPuppetingActive(boolean puppetingActive) { this.puppetingActive = puppetingActive; }

    @Override
    public ActionResult execute(SequenceInstance instance, MinecraftServer server) {
        if (server == null) return ActionResult.SUCCESS;

        List<IPuppetEntity> targets = SelectorUtils.getPuppetEntities(server, entityUuid, targetSelector);
        for (IPuppetEntity puppetEntity : targets) {
            PuppetController controller = puppetEntity.getPuppetController();
            controller.setSuppressAi(suppressAi);
            controller.setSuppressNavigation(suppressNavigation);
            controller.setSuppressTargeting(suppressTargeting);
            controller.setSuppressLook(suppressLook);
            controller.setPuppetingActive(puppetingActive);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public String getType() {
        return "puppet_suppress_ai";
    }

    @Override
    public OrchestratorAction copy() {
        return new PuppetSuppressAction(suppressAi, suppressNavigation, suppressTargeting, suppressLook, puppetingActive, entityUuid, targetSelector);
    }
}
