package net.dandare21.fracturedutils.puppet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;

/**
 * Functional interface representing a discrete boss attack, spell, or scripted movement routine.
 */
@FunctionalInterface
public interface PuppetAction {
    /**
     * Executes the main action trigger on the host entity (called when entering the active phase).
     *
     * @param mob    The entity being puppeteered.
     * @param params Key-value parameters passed from the sequencer/data file.
     */
    void execute(Mob mob, CompoundTag params);

    /**
     * Called every tick during the windup/telegraph phase (0 < currentTick <= totalWindupTicks).
     * Override to spawn attack indicators, charge particles, telegraph sounds, or track target position.
     */
    default void onWindupTick(Mob mob, CompoundTag params, int currentTick, int totalWindupTicks) {}

    /**
     * Called every tick during the active execution phase (0 < currentTick <= totalDurationTicks).
     * Override for multi-tick sustained attacks (e.g. continuous beam, whirlwind).
     */
    default void onActiveTick(Mob mob, CompoundTag params, int currentTick, int totalDurationTicks) {}

    /**
     * Called when the action finishes (after windup + duration expire or stopAction is called).
     */
    default void onComplete(Mob mob, CompoundTag params) {}
}
