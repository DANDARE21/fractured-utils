package net.dandare21.fracturedutils.threshold;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Represents a health value that can either be an absolute number of hitpoints (HP)
 * or a percentage (0.0 to 1.0) of the target entity's maximum health.
 */
public class HealthValue {
    private final float rawValue;
    private final boolean isPercentage;

    private HealthValue(float rawValue, boolean isPercentage) {
        this.rawValue = rawValue;
        this.isPercentage = isPercentage;
    }

    public static HealthValue ofAbsolute(float hp) {
        return new HealthValue(Math.max(0.0f, hp), false);
    }

    public static HealthValue ofPercent(float fraction) {
        return new HealthValue(Math.max(0.0f, fraction), true);
    }

    /**
     * Parses a string input into a HealthValue.
     * Examples:
     *   "25%" -> 25% of max health (fraction 0.25)
     *   "0.5" -> 0.5 HP
     *   "50"  -> 50 HP
     */
    public static HealthValue parse(String input) throws IllegalArgumentException {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Health value cannot be empty.");
        }
        String trimmed = input.trim();
        if (trimmed.endsWith("%")) {
            String numPart = trimmed.substring(0, trimmed.length() - 1).trim();
            float percentVal = Float.parseFloat(numPart);
            if (percentVal < 0.0f) {
                throw new IllegalArgumentException("Percentage cannot be negative: " + input);
            }
            return new HealthValue(percentVal / 100.0f, true);
        } else {
            float absVal = Float.parseFloat(trimmed);
            if (absVal < 0.0f) {
                throw new IllegalArgumentException("Health value cannot be negative: " + input);
            }
            return new HealthValue(absVal, false);
        }
    }

    public float getRawValue() {
        return rawValue;
    }

    public boolean isPercentage() {
        return isPercentage;
    }

    /**
     * Resolves this health value into an actual number of HP for the given entity.
     */
    public float resolve(LivingEntity entity) {
        if (entity == null) {
            return rawValue;
        }
        if (isPercentage) {
            return rawValue * entity.getMaxHealth();
        } else {
            return rawValue;
        }
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("val", rawValue);
        tag.putBoolean("pct", isPercentage);
        return tag;
    }

    public static HealthValue deserializeNBT(CompoundTag tag) {
        if (tag == null) {
            return ofAbsolute(0.0f);
        }
        float val = tag.getFloat("val");
        boolean pct = tag.getBoolean("pct");
        return new HealthValue(val, pct);
    }

    @Override
    public String toString() {
        if (isPercentage) {
            return String.format(Locale.US, "%.1f%%", rawValue * 100.0f);
        } else {
            return String.format(Locale.US, "%.1f HP", rawValue);
        }
    }

    public String formatResolved(LivingEntity entity) {
        float resolved = resolve(entity);
        if (isPercentage) {
            return String.format(Locale.US, "%.1f HP (%.1f%%)", resolved, rawValue * 100.0f);
        } else {
            float pct = entity.getMaxHealth() > 0 ? (resolved / entity.getMaxHealth()) * 100.0f : 0.0f;
            return String.format(Locale.US, "%.1f HP (%.1f%%)", resolved, pct);
        }
    }
}
