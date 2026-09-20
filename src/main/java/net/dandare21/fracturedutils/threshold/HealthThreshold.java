package net.dandare21.fracturedutils.threshold;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Defines a threshold constraint for a LivingEntity:
 * - minHealth: minimum HP the entity can reach. Under no circumstances can health drop below this.
 * - umbral: the health point at which incoming damage begins to be reduced.
 */
public class HealthThreshold {
    private final String id;
    private final HealthValue minHealth;
    private final HealthValue umbral;

    public HealthThreshold(String id, HealthValue minHealth, HealthValue umbral) {
        this.id = id != null && !id.isBlank() ? id : "default";
        this.minHealth = minHealth != null ? minHealth : HealthValue.ofAbsolute(0.0f);
        this.umbral = umbral != null ? umbral : this.minHealth;
    }

    public String getId() {
        return id;
    }

    public HealthValue getMinHealth() {
        return minHealth;
    }

    public HealthValue getUmbral() {
        return umbral;
    }

    public float getMinHealthResolved(LivingEntity entity) {
        return minHealth.resolve(entity);
    }

    public float getUmbralResolved(LivingEntity entity) {
        float hMin = getMinHealthResolved(entity);
        float hUmbral = umbral.resolve(entity);
        return Math.max(hMin, hUmbral);
    }

    /**
     * Calculates the effective damage that should be inflicted on the entity,
     * reducing damage according to the umbral falloff curve and clamping to minHealth.
     */
    public float calculateEffectiveDamage(LivingEntity entity, float rawDamage) {
        if (entity == null || rawDamage <= 0.0f) {
            return 0.0f;
        }

        float currentHealth = entity.getHealth();
        float hMin = getMinHealthResolved(entity);
        float hUmbral = getUmbralResolved(entity);

        // Already at or below the minimum threshold: completely ineffective
        if (currentHealth <= hMin) {
            return 0.0f;
        }

        float L = hUmbral - hMin;

        // Case 1: Entity is currently above the umbral
        if (currentHealth > hUmbral) {
            float damageToUmbral = currentHealth - hUmbral;
            if (rawDamage <= damageToUmbral) {
                // Completely outside reduction zone: 100% effective
                return rawDamage;
            }

            float rawRemainder = rawDamage - damageToUmbral;
            float damageInZone;

            if (L <= 0.001f) {
                // No umbral buffer; hard stop at hMin
                damageInZone = 0.0f;
            } else {
                // Progressive exponential falloff across [hMin, hUmbral]
                float hFinal = hMin + L * (float) Math.exp(-rawRemainder / L);
                damageInZone = hUmbral - hFinal;
            }

            float totalDamage = damageToUmbral + damageInZone;
            // Safety guarantee: never reduce health below hMin
            return Math.min(totalDamage, currentHealth - hMin);
        }

        // Case 2: Entity is already inside the umbral reduction zone [hMin, hUmbral]
        if (L <= 0.001f) {
            return 0.0f;
        }

        float bufferCurrent = currentHealth - hMin;
        float hFinal = hMin + bufferCurrent * (float) Math.exp(-rawDamage / L);
        float effectiveDamage = currentHealth - hFinal;

        // Safety guarantee: never reduce health below hMin
        return Math.min(effectiveDamage, currentHealth - hMin);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.put("min", minHealth.serializeNBT());
        tag.put("umbral", umbral.serializeNBT());
        return tag;
    }

    public static HealthThreshold deserializeNBT(CompoundTag tag) {
        if (tag == null) {
            return new HealthThreshold("default", HealthValue.ofAbsolute(0.0f), HealthValue.ofAbsolute(0.0f));
        }
        String id = tag.getString("id");
        HealthValue min = HealthValue.deserializeNBT(tag.getCompound("min"));
        HealthValue umbral = HealthValue.deserializeNBT(tag.getCompound("umbral"));
        return new HealthThreshold(id, min, umbral);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "Threshold[%s: min=%s, umbral=%s]", id, minHealth, umbral);
    }

    public String format(LivingEntity entity) {
        return String.format(Locale.US, "[%s] Min: %s | Umbral: %s",
                id, minHealth.formatResolved(entity), umbral.formatResolved(entity));
    }
}
