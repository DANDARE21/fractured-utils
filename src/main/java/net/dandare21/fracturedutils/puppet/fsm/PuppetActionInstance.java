package net.dandare21.fracturedutils.puppet.fsm;

import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.ClientboundPuppetAnimPacket;
import net.minecraft.world.entity.Mob;

/**
 * Base class for an active puppet action finite state machine instance.
 * Manages the WINDUP -> ACTIVE -> RECOVERY -> IDLE lifecycle and phase transitions.
 *
 * @param <T> The action parameter type.
 */
public abstract class PuppetActionInstance<T> {
    protected final Mob mob;
    protected final T params;
    private Phase currentPhase = Phase.IDLE;
    private int ticksInPhase = 0;
    private boolean finished = false;

    public PuppetActionInstance(Mob mob, T params) {
        this.mob = mob;
        this.params = params;
    }

    public Mob getMob() {
        return mob;
    }

    public T getParams() {
        return params;
    }

    public Phase getCurrentPhase() {
        return currentPhase;
    }

    public int getTicksInPhase() {
        return ticksInPhase;
    }

    public boolean isFinished() {
        return finished || currentPhase == Phase.IDLE;
    }

    /**
     * Initializes and starts this action.
     */
    public void start() {
        this.finished = false;
        this.ticksInPhase = 0;
        onStart();
    }

    /**
     * Called when the action starts. Typically transitions to WINDUP or ACTIVE.
     */
    public void onStart() {
        transitionTo(Phase.WINDUP);
    }

    /**
     * Advances the finite state machine by one server tick.
     */
    public void tick() {
        if (isFinished()) return;

        this.ticksInPhase++;
        switch (this.currentPhase) {
            case WINDUP -> onWindupTick(this.ticksInPhase);
            case ACTIVE -> onActiveTick(this.ticksInPhase);
            case RECOVERY -> onRecoveryTick(this.ticksInPhase);
            case IDLE -> {
                this.finished = true;
            }
        }
    }

    /**
     * Switches to a new FSM phase and triggers the onPhaseTransition lifecycle hook.
     */
    public void transitionTo(Phase newPhase) {
        this.currentPhase = newPhase;
        this.ticksInPhase = 0;
        onPhaseTransition(newPhase);
        if (newPhase == Phase.IDLE) {
            this.finished = true;
        }
    }

    /**
     * Hook called upon entering a new phase. Ideal for broadcasting GeckoLib animation triggers.
     */
    public abstract void onPhaseTransition(Phase newPhase);

    /**
     * Hook called on each tick of the WINDUP phase.
     */
    public abstract void onWindupTick(int tick);

    /**
     * Hook called on each tick of the ACTIVE phase.
     */
    public abstract void onActiveTick(int tick);

    /**
     * Hook called on each tick of the RECOVERY phase.
     */
    public abstract void onRecoveryTick(int tick);

    /**
     * Stops this action prematurely and transitions to IDLE.
     */
    public void stop() {
        transitionTo(Phase.IDLE);
        this.finished = true;
    }

    /**
     * Broadcasts a GeckoLib animation trigger packet to all clients tracking this mob and self.
     */
    public void broadcastAnim(String controllerName, String animName) {
        if (this.mob != null && !this.mob.level().isClientSide) {
            ModMessages.sendToTrackingEntityAndSelf(
                    new ClientboundPuppetAnimPacket(this.mob.getId(), controllerName, animName),
                    this.mob
            );
        }
    }

    /**
     * Displays a circular attack indicator decal on the ground using textures/misc/attack_indicator_circle.png.
     */
    public int showCircleIndicator(net.minecraft.world.phys.Vec3 pos, double radius, int durationTicks) {
        return showCircleIndicator(pos, radius, durationTicks, net.dandare21.fracturedutils.puppet.util.AttackIndicatorUtils.COLOR_VOID);
    }

    /**
     * Displays a colored circular attack indicator decal on the ground.
     */
    public int showCircleIndicator(net.minecraft.world.phys.Vec3 pos, double radius, int durationTicks, int argbColor) {
        if (this.mob != null && this.mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            return net.dandare21.fracturedutils.puppet.util.AttackIndicatorUtils.spawnCircle(
                    serverLevel, pos, radius, durationTicks, argbColor
            );
        }
        return -1;
    }

    /**
     * Updates an existing circular attack indicator's position and radius.
     */
    public void updateCircleIndicator(int id, net.minecraft.world.phys.Vec3 pos, double radius) {
        if (id > 0 && this.mob != null && this.mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            net.dandare21.fracturedutils.puppet.util.AttackIndicatorUtils.updateCircle(serverLevel, id, pos, radius);
        }
    }

    /**
     * Removes an active circular attack indicator early.
     */
    public void removeCircleIndicator(int id, net.minecraft.world.phys.Vec3 pos) {
        if (id > 0 && this.mob != null && this.mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            net.dandare21.fracturedutils.puppet.util.AttackIndicatorUtils.removeCircle(serverLevel, id, pos);
        }
    }
}
