package net.dandare21.fracturedutils.puppet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Controller component stored inside a puppet entity. Manages AI aspect suppression flags,
 * registered actions, and active concurrent action lifetimes.
 */
public class PuppetController {
    private final Mob mob;
    private final Map<ResourceLocation, PuppetAction> actions = new HashMap<>();

    // AI Aspect Suppression Flags
    private boolean suppressAi = false;
    private boolean suppressNavigation = false;
    private boolean suppressTargeting = false;
    private boolean suppressLook = false;
    private boolean puppetingActive = false;

    public enum ActionPhase {
        IDLE, WINDUP, ACTIVE
    }

    /**
     * Represents a single actively executing action instance.
     */
    public static class ActiveActionExecution {
        private final ResourceLocation actionId;
        private final PuppetAction action;
        private final CompoundTag params;
        private ActionPhase currentPhase = ActionPhase.IDLE;
        private int windupTicksRemaining;
        private final int totalWindupTicks;
        private int durationTicksRemaining;
        private final int totalDurationTicks;
        private Runnable onActionCompleteCallback;
        private boolean finished = false;

        public ActiveActionExecution(ResourceLocation actionId, PuppetAction action, CompoundTag params, int windupTicks, int durationTicks, Runnable onComplete) {
            this.actionId = actionId;
            this.action = action;
            this.params = params != null ? params.copy() : new CompoundTag();
            this.onActionCompleteCallback = onComplete;
            int wTicks = Math.max(0, windupTicks);
            int dTicks = Math.max(0, durationTicks);
            this.totalWindupTicks = wTicks;
            this.windupTicksRemaining = wTicks;
            this.totalDurationTicks = dTicks;
            this.durationTicksRemaining = dTicks;
        }

        public void start(Mob mob) {
            if (this.totalWindupTicks > 0) {
                this.currentPhase = ActionPhase.WINDUP;
            } else {
                this.action.execute(mob, this.params);
                if (this.totalDurationTicks > 0) {
                    this.currentPhase = ActionPhase.ACTIVE;
                } else {
                    complete(mob);
                }
            }
        }

        public void tick(Mob mob) {
            if (this.finished) return;
            if (this.currentPhase == ActionPhase.WINDUP) {
                int currentWindupTick = this.totalWindupTicks - this.windupTicksRemaining + 1;
                this.action.onWindupTick(mob, this.params, currentWindupTick, this.totalWindupTicks);
                this.windupTicksRemaining--;
                if (this.windupTicksRemaining <= 0) {
                    this.action.execute(mob, this.params);
                    if (this.totalDurationTicks > 0) {
                        this.currentPhase = ActionPhase.ACTIVE;
                    } else {
                        complete(mob);
                    }
                }
            } else if (this.currentPhase == ActionPhase.ACTIVE) {
                int currentActiveTick = this.totalDurationTicks - this.durationTicksRemaining + 1;
                this.action.onActiveTick(mob, this.params, currentActiveTick, this.totalDurationTicks);
                this.durationTicksRemaining--;
                if (this.durationTicksRemaining <= 0) {
                    complete(mob);
                }
            }
        }

        public void complete(Mob mob) {
            if (this.finished) return;
            this.finished = true;
            this.currentPhase = ActionPhase.IDLE;
            this.action.onComplete(mob, this.params);
            if (this.onActionCompleteCallback != null) {
                Runnable cb = this.onActionCompleteCallback;
                this.onActionCompleteCallback = null;
                cb.run();
            }
        }

        public boolean isFinished() { return this.finished; }
        public ActionPhase getCurrentPhase() { return this.currentPhase; }
        public ResourceLocation getActionId() { return this.actionId; }
        public PuppetAction getAction() { return this.action; }
        public CompoundTag getParams() { return this.params; }
        public int getWindupTicksRemaining() { return this.windupTicksRemaining; }
        public int getTotalWindupTicks() { return this.totalWindupTicks; }
        public int getDurationTicksRemaining() { return this.durationTicksRemaining; }
        public int getTotalDurationTicks() { return this.totalDurationTicks; }
    }

    // Active Concurrent Action Executions
    private final List<ActiveActionExecution> activeExecutions = new CopyOnWriteArrayList<>();
    private long lastTickTime = -1;

    // Sustained Primitive Direct Controls State
    private boolean hasMoveTarget = false;
    private double moveTargetX = 0.0;
    private double moveTargetY = 0.0;
    private double moveTargetZ = 0.0;
    private double moveSpeed = 1.0;

    private boolean hasLookTargetEntity = false;
    private Entity lookTargetEntity = null;

    private boolean hasLookTargetPos = false;
    private double lookTargetX = 0.0;
    private double lookTargetY = 0.0;
    private double lookTargetZ = 0.0;

    public PuppetController(Mob mob) {
        this.mob = mob;
        // Inject priority 0 hijack goals into both goalSelector (MOVE, LOOK, JUMP) and targetSelector (TARGET)
        this.mob.goalSelector.addGoal(0, new PuppetOverrideGoal(this.mob, this));
        this.mob.targetSelector.addGoal(0, new PuppetTargetOverrideGoal(this.mob, this));

        if (this.mob instanceof IPuppetEntity puppet) {
            puppet.registerPuppetActions(new PuppetActionRegistry(this));
        }
    }

    // --- Registration & Execution ---

    public void registerAction(ResourceLocation id, PuppetAction action) {
        this.actions.put(id, action);
    }

    public void executeAction(ResourceLocation actionId, CompoundTag params, int windupTicks, int durationTicks, Runnable onComplete) {
        PuppetAction action = this.actions.get(actionId);
        if (action != null) {
            this.mob.setNoAi(false);
            this.puppetingActive = true;

            ActiveActionExecution exec = new ActiveActionExecution(actionId, action, params, windupTicks, durationTicks, onComplete);
            this.activeExecutions.add(exec);
            exec.start(this.mob);
            if (exec.isFinished()) {
                this.activeExecutions.remove(exec);
                if (this.activeExecutions.isEmpty() && !this.hasActionTarget()) {
                    this.puppetingActive = false;
                }
            }
        }
    }

    public void executeAction(ResourceLocation actionId, CompoundTag params, int durationTicks, Runnable onComplete) {
        executeAction(actionId, params, 0, durationTicks, onComplete);
    }

    public void executeAction(ResourceLocation actionId, CompoundTag params) {
        executeAction(actionId, params, 0, 0, null);
    }

    public void executeAction(ResourceLocation actionId, CompoundTag params, int durationTicks) {
        executeAction(actionId, params, 0, durationTicks, null);
    }

    // --- Tick Update ---

    public void tick() {
        long currentTime = this.mob.level() != null ? this.mob.level().getGameTime() : -1;
        boolean isNewTick = currentTime != this.lastTickTime;
        if (isNewTick) {
            this.lastTickTime = currentTime;
        }

        if (!this.puppetingActive && this.activeExecutions.isEmpty() && !this.hasActionTarget()) return;

        // 1. Concurrent Action Lifecycle Update
        if (isNewTick && !this.activeExecutions.isEmpty()) {
            List<ActiveActionExecution> completed = new ArrayList<>();
            for (ActiveActionExecution exec : this.activeExecutions) {
                if (!exec.isFinished()) {
                    exec.tick(this.mob);
                }
                if (exec.isFinished()) {
                    completed.add(exec);
                }
            }
            if (!completed.isEmpty()) {
                this.activeExecutions.removeAll(completed);
                if (this.activeExecutions.isEmpty() && !this.hasActionTarget()) {
                    this.puppetingActive = false;
                }
            }
        }

        // 2. Navigation & Movement Override
        if (this.suppressNavigation || (this.suppressAi && !this.hasMoveTarget)) {
            this.mob.getNavigation().stop();
        } else if (this.hasMoveTarget) {
            double distSq = this.mob.distanceToSqr(this.moveTargetX, this.moveTargetY, this.moveTargetZ);
            if (distSq <= 1.5) {
                this.hasMoveTarget = false;
            } else {
                if (this.mob.getNavigation().isDone() || (isNewTick && this.mob.tickCount % 5 == 0)) {
                    this.mob.getNavigation().moveTo(this.moveTargetX, this.moveTargetY, this.moveTargetZ, this.moveSpeed);
                }
            }
        }

        // 3. Targeting Suppression
        if (this.suppressTargeting || this.suppressAi) {
            this.mob.setTarget(null);
        }

        // 4. Look Angle Control (re-applied every tick because LookControl resets per tick)
        if (this.suppressLook) {
            // Suppress look changes
        } else if (this.hasLookTargetEntity) {
            if (this.lookTargetEntity != null && this.lookTargetEntity.isAlive()) {
                this.mob.getLookControl().setLookAt(this.lookTargetEntity, 360.0F, 360.0F);
            } else {
                this.hasLookTargetEntity = false;
                this.lookTargetEntity = null;
            }
        } else if (this.hasLookTargetPos) {
            this.mob.getLookControl().setLookAt(this.lookTargetX, this.lookTargetY, this.lookTargetZ, 360.0F, 360.0F);
        }
    }

    public void stopAction() {
        for (ActiveActionExecution exec : this.activeExecutions) {
            exec.complete(this.mob);
        }
        this.activeExecutions.clear();
        this.puppetingActive = false;

        this.clearMoveTarget();
        this.clearLookTarget();
    }

    public void stopAction(ResourceLocation actionId) {
        if (actionId == null) return;
        List<ActiveActionExecution> toStop = new ArrayList<>();
        for (ActiveActionExecution exec : this.activeExecutions) {
            if (exec.getActionId().equals(actionId)) {
                toStop.add(exec);
            }
        }
        for (ActiveActionExecution exec : toStop) {
            exec.complete(this.mob);
            this.activeExecutions.remove(exec);
        }
        if (this.activeExecutions.isEmpty() && !this.hasActionTarget()) {
            this.puppetingActive = false;
        }
    }

    public void clearMoveTarget() {
        this.hasMoveTarget = false;
        this.mob.getNavigation().stop();
    }

    public void clearLookTarget() {
        this.hasLookTargetEntity = false;
        this.hasLookTargetPos = false;
        this.lookTargetEntity = null;
    }

    // --- Suppression Controls ---

    public void setSuppressAi(boolean suppress) {
        this.suppressAi = suppress;
        // Never set native setNoAi(true) as it suppresses aiStep() which kills navigation and look controls for manual actions
        this.mob.setNoAi(false);
        if (suppress) {
            this.clearMoveTarget();
            this.clearLookTarget();
            this.mob.setTarget(null);
            this.mob.getNavigation().stop();
        }
    }

    public void setSuppressNavigation(boolean suppress) {
        this.suppressNavigation = suppress;
        if (suppress) {
            this.hasMoveTarget = false;
            this.mob.getNavigation().stop();
        }
    }

    public void setSuppressTargeting(boolean suppress) {
        this.suppressTargeting = suppress;
        if (suppress) this.mob.setTarget(null);
    }

    public void setSuppressLook(boolean suppress) {
        this.suppressLook = suppress;
        if (suppress) this.clearLookTarget();
    }

    public void setPuppetingActive(boolean active) {
        this.puppetingActive = active;
        if (!active) {
            this.clearActiveAction();
        }
    }

    public void clearActiveAction() {
        for (ActiveActionExecution exec : this.activeExecutions) {
            exec.complete(this.mob);
        }
        this.activeExecutions.clear();
    }

    public void resetSuppressionFlags() {
        this.setSuppressAi(false);
        this.suppressNavigation = false;
        this.suppressTargeting = false;
        this.suppressLook = false;
    }

    // --- Primitive Direct Controls (For Sequencer Scripts) ---

    public void forceMoveTo(double x, double y, double z, double speed) {
        this.mob.setNoAi(false);
        this.puppetingActive = true;
        this.hasMoveTarget = true;
        this.moveTargetX = x;
        this.moveTargetY = y;
        this.moveTargetZ = z;
        this.moveSpeed = speed;
        this.mob.getNavigation().moveTo(x, y, z, speed);
    }

    public void forceLookAt(Entity target) {
        this.mob.setNoAi(false);
        this.puppetingActive = true;
        this.hasLookTargetEntity = true;
        this.hasLookTargetPos = false;
        this.lookTargetEntity = target;
        if (target != null) {
            this.mob.getLookControl().setLookAt(target, 360.0F, 360.0F);
        }
    }

    public void forceLookAt(double x, double y, double z) {
        this.mob.setNoAi(false);
        this.puppetingActive = true;
        this.hasLookTargetPos = true;
        this.hasLookTargetEntity = false;
        this.lookTargetX = x;
        this.lookTargetY = y;
        this.lookTargetZ = z;
        this.mob.getLookControl().setLookAt(x, y, z, 360.0F, 360.0F);
    }

    // --- Getters ---

    public Mob getMob() { return this.mob; }
    public Map<ResourceLocation, PuppetAction> getActions() { return Collections.unmodifiableMap(this.actions); }
    public boolean isPuppetingActive() { return this.puppetingActive || !this.activeExecutions.isEmpty(); }
    public boolean hasActiveActions() { return !this.activeExecutions.isEmpty(); }
    public List<ActiveActionExecution> getActiveExecutions() { return Collections.unmodifiableList(this.activeExecutions); }
    public ActionPhase getCurrentPhase() {
        ActionPhase highest = ActionPhase.IDLE;
        for (ActiveActionExecution exec : this.activeExecutions) {
            ActionPhase p = exec.getCurrentPhase();
            if (p == ActionPhase.ACTIVE) return ActionPhase.ACTIVE;
            if (p == ActionPhase.WINDUP) highest = ActionPhase.WINDUP;
        }
        return highest;
    }
    public int getWindupTicksRemaining() {
        return this.activeExecutions.isEmpty() ? 0 : this.activeExecutions.get(this.activeExecutions.size() - 1).getWindupTicksRemaining();
    }
    public int getTotalWindupTicks() {
        return this.activeExecutions.isEmpty() ? 0 : this.activeExecutions.get(this.activeExecutions.size() - 1).getTotalWindupTicks();
    }
    public int getDurationTicksRemaining() {
        return this.activeExecutions.isEmpty() ? 0 : this.activeExecutions.get(this.activeExecutions.size() - 1).getDurationTicksRemaining();
    }
    public int getTotalDurationTicks() {
        return this.activeExecutions.isEmpty() ? 0 : this.activeExecutions.get(this.activeExecutions.size() - 1).getTotalDurationTicks();
    }
    public CompoundTag getActiveParams() {
        return this.activeExecutions.isEmpty() ? new CompoundTag() : this.activeExecutions.get(this.activeExecutions.size() - 1).getParams();
    }
    public boolean isAiSuppressed() { return this.suppressAi || this.mob.isNoAi(); }
    public boolean isNavigationSuppressed() { return this.suppressNavigation; }
    public boolean isTargetingSuppressed() { return this.suppressTargeting; }
    public boolean isLookSuppressed() { return this.suppressLook; }
    public boolean hasActionTarget() { return this.hasMoveTarget || this.hasLookTargetEntity || this.hasLookTargetPos; }
}
