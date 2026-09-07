package net.dandare21.fracturedutils.puppet.capability;

import net.dandare21.fracturedutils.puppet.fsm.Phase;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionInstance;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Capability interface attached to any Mob to allow puppet orchestration,
 * AI goal flag suppression, and action dispatching.
 */
public interface IPuppetHandler {

    Mob getMob();

    /**
     * Dispatches and begins execution of a registered action type with the specified parameters.
     */
    <T> void dispatch(PuppetActionType<T> type, T params);

    /**
     * Ticks the active puppet action instance and monitors lifecycle completion.
     */
    void tick();

    /**
     * Stops any currently running action and restores AI control flags.
     */
    void stopActiveAction();

    /**
     * Whether puppeteering is currently active.
     */
    boolean isPuppetingActive();

    /**
     * @return The currently active action instance, or null if idle.
     */
    PuppetActionInstance<?> getActiveAction();

    /**
     * @return The active phase of the running action, or IDLE.
     */
    Phase getCurrentPhase();

    /**
     * Directly locks goal selector and target selector flags on the mob.
     *
     * @param move   Lock Flag.MOVE and stop navigation.
     * @param look   Lock Flag.LOOK.
     * @param jump   Lock Flag.JUMP.
     * @param target Lock Flag.TARGET and clear target entity.
     */
    void suppressAi(boolean move, boolean look, boolean jump, boolean target);

    /**
     * Sets overall master AI suppression.
     */
    void setSuppressAi(boolean suppress);

    /**
     * Restores all locked GoalSelector and TargetSelector control flags to true and resets suppression.
     */
    void restoreAi();

    /**
     * @return Whether overall AI flags are currently suppressed.
     */
    boolean isAiSuppressed();

    /**
     * Sets whether navigation and locomotion AI are suppressed.
     */
    void setSuppressNavigation(boolean suppress);

    /**
     * @return Whether navigation/locomotion is suppressed.
     */
    boolean isNavigationSuppressed();

    /**
     * Sets whether target selection AI is suppressed.
     */
    void setSuppressTargeting(boolean suppress);

    /**
     * @return Whether target selection is suppressed.
     */
    boolean isTargetingSuppressed();

    /**
     * Sets whether looking AI is suppressed.
     */
    void setSuppressLook(boolean suppress);

    /**
     * @return Whether look control is suppressed.
     */
    boolean isLookSuppressed();

    /**
     * Sets whether jumping AI is suppressed.
     */
    void setSuppressJump(boolean suppress);

    /**
     * @return Whether jumping is suppressed.
     */
    boolean isJumpSuppressed();

    /**
     * Drives the mob towards specific world coordinates at a given speed.
     */
    void forceMoveTo(double x, double y, double z, double speed);

    /**
     * Clears any active forced movement target.
     */
    void clearMoveTarget();

    /**
     * Whether the mob has an active move target.
     */
    boolean hasMoveTarget();

    /**
     * Forces the mob to look at the specified target entity.
     */
    void forceLookAt(Entity target);

    /**
     * Forces the mob to look at specific coordinates.
     */
    void forceLookAt(double x, double y, double z);

    /**
     * Clears any forced look target.
     */
    void clearLookTarget();

    /**
     * Whether the mob has an active look target.
     */
    boolean hasLookTarget();

    /**
     * Disables or enables autonomous puppet action execution on the mob.
     * When true, autonomous mob AI will not dispatch puppet attacks, allowing manual triggering.
     */
    void setSuppressActions(boolean suppress);

    /**
     * Whether autonomous puppet action execution is suppressed.
     */
    boolean isActionsSuppressed();
}

