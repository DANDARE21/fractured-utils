package net.dandare21.fracturedutils.puppet.capability;

import net.dandare21.fracturedutils.puppet.fsm.Phase;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionInstance;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Implementation of IPuppetHandler attached to mobs.
 * Manages action execution FSM, direct GoalSelector control flag locking, and state cleanup.
 */
public class PuppetHandlerImpl implements IPuppetHandler {
    private final Mob mob;
    private PuppetActionInstance<?> activeAction = null;
    private boolean puppetingActive = false;
    private boolean aiSuppressed = false;

    private boolean suppressedMove = false;
    private boolean suppressedLook = false;
    private boolean suppressedJump = false;
    private boolean suppressedTarget = false;
    private boolean suppressActions = false;

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

    public PuppetHandlerImpl(Mob mob) {
        this.mob = mob;
    }

    @Override
    public Mob getMob() {
        return this.mob;
    }

    @Override
    public <T> void dispatch(PuppetActionType<T> type, T params) {
        if (this.mob == null || this.mob.level().isClientSide) return;

        // Cancel any previous action cleanly
        stopActiveAction();

        this.puppetingActive = true;
        // Lock goals temporarily for the action duration (does NOT set persistent suppression flags)
        applyControlFlags();

        this.activeAction = type.createInstance(this.mob, params);
        this.activeAction.start();
    }

    @Override
    public void tick() {
        if (this.mob == null || this.mob.level().isClientSide) return;

        boolean freezeMove = (this.suppressedMove || this.aiSuppressed) && !this.hasMoveTarget;
        boolean freezeLook = (this.suppressedLook || this.aiSuppressed) && !this.hasLookTarget();
        boolean freezeTarget = this.suppressedTarget || this.aiSuppressed;
        boolean freezeJump = this.suppressedJump || this.aiSuppressed;

        // Reinforce suppression flags while active so AI cannot inadvertently regain control
        if (this.puppetingActive || this.aiSuppressed || this.suppressedMove || this.suppressedLook || this.suppressedJump || this.suppressedTarget) {
            applyControlFlags();
        }

        // Active Action Lifecycle Update
        if (this.activeAction != null) {
            if (!this.activeAction.isFinished()) {
                this.activeAction.tick();
            }

            if (this.activeAction.isFinished()) {
                stopActiveAction();
            }
        }

        // Direct Locomotion Update
        if (this.hasMoveTarget) {
            double distSq = this.mob.distanceToSqr(this.moveTargetX, this.moveTargetY, this.moveTargetZ);
            if (distSq <= 2.25) {
                clearMoveTarget();
            } else if (this.mob.tickCount % 5 == 0 || (this.mob.getNavigation() != null && this.mob.getNavigation().isDone())) {
                if (this.mob.getNavigation() != null) {
                    this.mob.getNavigation().moveTo(this.moveTargetX, this.moveTargetY, this.moveTargetZ, this.moveSpeed);
                }
            }
        } else if (freezeMove && this.activeAction == null) {
            // Strictly enforce complete movement halt when navigation is suppressed and no action/forced move is running
            if (this.mob.getNavigation() != null && !this.mob.getNavigation().isDone()) {
                this.mob.getNavigation().stop();
            }
            this.mob.setSpeed(0.0F);
            this.mob.zza = 0.0F;
            this.mob.xxa = 0.0F;
            Vec3 delta = this.mob.getDeltaMovement();
            if (delta.x != 0.0 || delta.z != 0.0) {
                this.mob.setDeltaMovement(0.0, delta.y, 0.0);
            }
        }

        // Direct Look Target Update (re-applied every tick because LookControl resets per tick)
        if (this.hasLookTargetEntity) {
            if (this.lookTargetEntity != null && this.lookTargetEntity.isAlive()) {
                this.mob.getLookControl().setLookAt(this.lookTargetEntity, 360.0F, 360.0F);
            } else {
                clearLookTarget();
            }
        } else if (this.hasLookTargetPos) {
            this.mob.getLookControl().setLookAt(this.lookTargetX, this.lookTargetY, this.lookTargetZ, 360.0F, 360.0F);
        }

        if (freezeTarget && this.mob.getTarget() != null) {
            this.mob.setTarget(null);
        }

        if (freezeJump) {
            this.mob.setJumping(false);
        }
    }

    @Override
    public void stopActiveAction() {
        if (this.activeAction != null) {
            PuppetActionInstance<?> action = this.activeAction;
            this.activeAction = null;
            if (!action.isFinished()) {
                action.stop();
            }
        }
        this.puppetingActive = false;
        // Do NOT call restoreAi()! Suppression state persists until explicitly turned off.
        applyControlFlags();
    }

    @Override
    public boolean isPuppetingActive() {
        return this.puppetingActive;
    }

    @Override
    public PuppetActionInstance<?> getActiveAction() {
        return this.activeAction;
    }

    @Override
    public Phase getCurrentPhase() {
        return this.activeAction != null ? this.activeAction.getCurrentPhase() : Phase.IDLE;
    }

    @Override
    public void suppressAi(boolean move, boolean look, boolean jump, boolean target) {
        this.suppressedMove = move;
        this.suppressedLook = look;
        this.suppressedJump = jump;
        this.suppressedTarget = target;
        this.aiSuppressed = move || look || jump || target;

        applyControlFlags();
    }

    @Override
    public void setSuppressAi(boolean suppress) {
        this.aiSuppressed = suppress;
        if (suppress) {
            this.suppressedMove = true;
            this.suppressedLook = true;
            this.suppressedJump = true;
            this.suppressedTarget = true;
            this.suppressActions = true;
        } else {
            this.suppressedMove = false;
            this.suppressedLook = false;
            this.suppressedJump = false;
            this.suppressedTarget = false;
            this.suppressActions = false;
        }
        applyControlFlags();
    }

    @Override
    public void setSuppressNavigation(boolean suppress) {
        this.suppressedMove = suppress;
        applyControlFlags();
    }

    @Override
    public boolean isNavigationSuppressed() {
        return this.suppressedMove || this.aiSuppressed;
    }

    @Override
    public void setSuppressTargeting(boolean suppress) {
        this.suppressedTarget = suppress;
        applyControlFlags();
    }

    @Override
    public boolean isTargetingSuppressed() {
        return this.suppressedTarget || this.aiSuppressed;
    }

    @Override
    public void setSuppressLook(boolean suppress) {
        this.suppressedLook = suppress;
        applyControlFlags();
    }

    @Override
    public boolean isLookSuppressed() {
        return this.suppressedLook || this.aiSuppressed;
    }

    @Override
    public void setSuppressJump(boolean suppress) {
        this.suppressedJump = suppress;
        applyControlFlags();
    }

    @Override
    public boolean isJumpSuppressed() {
        return this.suppressedJump || this.aiSuppressed;
    }

    private void applyControlFlags() {
        if (this.mob == null) return;

        boolean freezeMove = this.suppressedMove || this.aiSuppressed || (this.puppetingActive && !this.hasMoveTarget);
        boolean freezeLook = this.suppressedLook || this.aiSuppressed || (this.puppetingActive && this.activeAction != null && !this.hasLookTarget());
        boolean freezeJump = this.suppressedJump || this.aiSuppressed || this.puppetingActive;
        boolean freezeTarget = this.suppressedTarget || this.aiSuppressed;

        if (this.mob.goalSelector != null) {
            this.mob.goalSelector.setControlFlag(Goal.Flag.MOVE, !freezeMove);
            this.mob.goalSelector.setControlFlag(Goal.Flag.LOOK, !freezeLook);
            this.mob.goalSelector.setControlFlag(Goal.Flag.JUMP, !freezeJump);

            if (freezeMove) {
                if (this.mob.getNavigation() != null) {
                    this.mob.getNavigation().stop();
                }
                this.mob.goalSelector.getRunningGoals().forEach(wrappedGoal -> {
                    if (wrappedGoal.getFlags().contains(Goal.Flag.MOVE)) {
                        wrappedGoal.stop();
                    }
                });
            }
            if (freezeLook) {
                this.mob.goalSelector.getRunningGoals().forEach(wrappedGoal -> {
                    if (wrappedGoal.getFlags().contains(Goal.Flag.LOOK)) {
                        wrappedGoal.stop();
                    }
                });
            }
            if (freezeJump) {
                this.mob.goalSelector.getRunningGoals().forEach(wrappedGoal -> {
                    if (wrappedGoal.getFlags().contains(Goal.Flag.JUMP)) {
                        wrappedGoal.stop();
                    }
                });
                this.mob.setJumping(false);
            }
        }

        if (this.mob.targetSelector != null) {
            this.mob.targetSelector.setControlFlag(Goal.Flag.TARGET, !freezeTarget);
            if (freezeTarget) {
                this.mob.setTarget(null);
                this.mob.targetSelector.getRunningGoals().forEach(Goal::stop);
            }
        }
    }

    @Override
    public void restoreAi() {
        this.puppetingActive = false;
        this.aiSuppressed = false;
        this.suppressedMove = false;
        this.suppressedLook = false;
        this.suppressedJump = false;
        this.suppressedTarget = false;
        this.suppressActions = false;
        clearMoveTarget();
        clearLookTarget();

        if (this.mob != null) {
            this.mob.setNoAi(false);
            if (this.mob.goalSelector != null) {
                this.mob.goalSelector.setControlFlag(Goal.Flag.MOVE, true);
                this.mob.goalSelector.setControlFlag(Goal.Flag.LOOK, true);
                this.mob.goalSelector.setControlFlag(Goal.Flag.JUMP, true);
            }
            if (this.mob.targetSelector != null) {
                this.mob.targetSelector.setControlFlag(Goal.Flag.TARGET, true);
            }
        }
    }

    @Override
    public boolean isAiSuppressed() {
        return this.aiSuppressed;
    }


    @Override
    public void forceMoveTo(double x, double y, double z, double speed) {
        if (this.mob == null) return;
        this.puppetingActive = true;
        this.hasMoveTarget = true;
        this.moveTargetX = x;
        this.moveTargetY = y;
        this.moveTargetZ = z;
        this.moveSpeed = speed > 0 ? speed : 1.0;
        if (this.mob.getNavigation() != null) {
            this.mob.getNavigation().moveTo(x, y, z, this.moveSpeed);
        }
    }

    @Override
    public void clearMoveTarget() {
        this.hasMoveTarget = false;
        if (this.mob != null && this.mob.getNavigation() != null) {
            this.mob.getNavigation().stop();
        }
    }

    @Override
    public boolean hasMoveTarget() {
        return this.hasMoveTarget;
    }

    @Override
    public void forceLookAt(Entity target) {
        if (this.mob == null) return;
        this.puppetingActive = true;
        this.hasLookTargetEntity = true;
        this.lookTargetEntity = target;
        this.hasLookTargetPos = false;
        if (target != null && this.mob.getLookControl() != null) {
            this.mob.getLookControl().setLookAt(target, 360.0F, 360.0F);
        }
    }

    @Override
    public void forceLookAt(double x, double y, double z) {
        if (this.mob == null) return;
        this.puppetingActive = true;
        this.hasLookTargetPos = true;
        this.lookTargetX = x;
        this.lookTargetY = y;
        this.lookTargetZ = z;
        this.hasLookTargetEntity = false;
        this.lookTargetEntity = null;
        if (this.mob.getLookControl() != null) {
            this.mob.getLookControl().setLookAt(x, y, z, 360.0F, 360.0F);
        }
    }

    @Override
    public void clearLookTarget() {
        this.hasLookTargetEntity = false;
        this.lookTargetEntity = null;
        this.hasLookTargetPos = false;
    }

    @Override
    public boolean hasLookTarget() {
        return this.hasLookTargetEntity || this.hasLookTargetPos;
    }

    @Override
    public void setSuppressActions(boolean suppress) {
        this.suppressActions = suppress;
    }

    @Override
    public boolean isActionsSuppressed() {
        return this.suppressActions || this.aiSuppressed;
    }
}
