package net.dandare21.fracturedutils.threshold;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * Events fired during health threshold interactions.
 */
public class HealthThresholdEvent extends LivingEvent {
    private final HealthThreshold threshold;

    public HealthThresholdEvent(LivingEntity entity, HealthThreshold threshold) {
        super(entity);
        this.threshold = threshold;
    }

    public HealthThreshold getThreshold() {
        return threshold;
    }

    /**
     * Fired when an incoming attack causes an entity's health to reach its minimum threshold,
     * or when an attack is resisted while already at the threshold.
     */
    public static class Reached extends HealthThresholdEvent {
        private final float remainingHealth;

        public Reached(LivingEntity entity, HealthThreshold threshold, float remainingHealth) {
            super(entity, threshold);
            this.remainingHealth = remainingHealth;
        }

        public float getRemainingHealth() {
            return remainingHealth;
        }
    }
}
