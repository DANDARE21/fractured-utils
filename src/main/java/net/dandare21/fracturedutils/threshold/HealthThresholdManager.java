package net.dandare21.fracturedutils.threshold;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Manages health thresholds for LivingEntity instances.
 * Stores thresholds directly in the entity's persistent data tag, ensuring
 * persistence across chunk unloads, dimensions, and server restarts.
 */
public class HealthThresholdManager {
    public static final String NBT_KEY_THRESHOLDS = "fractured_utils:health_thresholds";

    /**
     * Sets a single threshold on the entity, replacing all existing thresholds.
     */
    public static void setThreshold(LivingEntity entity, HealthThreshold threshold) {
        if (entity == null || threshold == null) return;
        List<HealthThreshold> list = new ArrayList<>();
        list.add(threshold);
        saveThresholds(entity, list);
    }

    /**
     * Adds or updates a threshold on the entity (by threshold ID).
     */
    public static void addThreshold(LivingEntity entity, HealthThreshold threshold) {
        if (entity == null || threshold == null) return;
        List<HealthThreshold> list = getThresholds(entity);
        list.removeIf(t -> t.getId().equalsIgnoreCase(threshold.getId()));
        list.add(threshold);
        saveThresholds(entity, list);
    }

    /**
     * Removes a threshold by ID. If id is null or empty, removes the first threshold.
     * @return true if a threshold was removed, false otherwise.
     */
    public static boolean removeThreshold(LivingEntity entity, String id) {
        if (entity == null) return false;
        List<HealthThreshold> list = getThresholds(entity);
        if (list.isEmpty()) return false;

        boolean removed;
        if (id == null || id.isBlank()) {
            list.remove(0);
            removed = true;
        } else {
            removed = list.removeIf(t -> t.getId().equalsIgnoreCase(id.trim()));
        }

        if (removed) {
            saveThresholds(entity, list);
        }
        return removed;
    }

    /**
     * Clears all thresholds from the entity.
     */
    public static void clearThresholds(LivingEntity entity) {
        if (entity == null) return;
        entity.getPersistentData().remove(NBT_KEY_THRESHOLDS);
    }

    /**
     * Checks if the entity has any registered thresholds.
     */
    public static boolean hasThresholds(LivingEntity entity) {
        if (entity == null) return false;
        return entity.getPersistentData().contains(NBT_KEY_THRESHOLDS, Tag.TAG_LIST);
    }

    /**
     * Retrieves all thresholds registered on this entity.
     */
    public static List<HealthThreshold> getThresholds(LivingEntity entity) {
        if (entity == null) return Collections.emptyList();
        CompoundTag persistentData = entity.getPersistentData();
        if (!persistentData.contains(NBT_KEY_THRESHOLDS, Tag.TAG_LIST)) {
            return new ArrayList<>();
        }

        ListTag listTag = persistentData.getList(NBT_KEY_THRESHOLDS, Tag.TAG_COMPOUND);
        List<HealthThreshold> list = new ArrayList<>();
        for (int i = 0; i < listTag.size(); i++) {
            list.add(HealthThreshold.deserializeNBT(listTag.getCompound(i)));
        }
        return list;
    }

    private static void saveThresholds(LivingEntity entity, List<HealthThreshold> thresholds) {
        if (entity == null) return;
        if (thresholds == null || thresholds.isEmpty()) {
            clearThresholds(entity);
            return;
        }

        ListTag listTag = new ListTag();
        for (HealthThreshold t : thresholds) {
            listTag.add(t.serializeNBT());
        }
        entity.getPersistentData().put(NBT_KEY_THRESHOLDS, listTag);
    }

    /**
     * Determines which threshold is currently active for the entity based on its current health.
     * When multiple thresholds exist (e.g. for multi-phase boss fights), the active threshold is
     * the highest minHealth threshold that is still below current health (i.e. the next barrier).
     * If health is already at or below all thresholds, the highest minHealth threshold holding it is returned.
     */
    public static Optional<HealthThreshold> getActiveThreshold(LivingEntity entity) {
        List<HealthThreshold> list = getThresholds(entity);
        if (list.isEmpty()) {
            return Optional.empty();
        }

        if (list.size() == 1) {
            return Optional.of(list.get(0));
        }

        float currentHealth = entity.getHealth();

        // Sort descending by resolved minHealth
        List<HealthThreshold> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparingDouble((HealthThreshold t) -> (double) t.getMinHealthResolved(entity)).reversed());

        // Find the first threshold whose minHealth is strictly below current health
        for (HealthThreshold t : sorted) {
            if (currentHealth > t.getMinHealthResolved(entity)) {
                return Optional.of(t);
            }
        }

        // If current health is <= all minHealth thresholds, the highest one is currently holding the entity
        return Optional.of(sorted.get(0));
    }

    /**
     * Applies damage reduction based on the entity's active threshold.
     * @param entity The living entity taking damage
     * @param rawDamage The incoming damage amount
     * @return The reduced damage amount to be applied to the entity
     */
    public static float applyDamageReduction(LivingEntity entity, float rawDamage) {
        if (entity == null || rawDamage <= 0.0f) {
            return rawDamage;
        }

        Optional<HealthThreshold> activeOpt = getActiveThreshold(entity);
        if (activeOpt.isEmpty()) {
            return rawDamage;
        }

        HealthThreshold threshold = activeOpt.get();
        float currentHealth = entity.getHealth();
        float hMin = threshold.getMinHealthResolved(entity);

        float effectiveDamage = threshold.calculateEffectiveDamage(entity, rawDamage);

        // Check if the threshold was reached or resisted
        boolean reachedThreshold = (currentHealth - effectiveDamage) <= (hMin + 0.001f);
        boolean damageResisted = effectiveDamage < rawDamage;

        if (damageResisted) {
            // Visual / audio feedback on server level
            if (entity.level() instanceof ServerLevel serverLevel) {
                double eyeY = entity.getY() + entity.getEyeHeight() * 0.6;
                serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        entity.getX(), eyeY, entity.getZ(),
                        8, 0.25, 0.25, 0.25, 0.1);

                if (reachedThreshold) {
                    serverLevel.playSound(null, entity.getX(), eyeY, entity.getZ(),
                            SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.2f);
                }
            }
        }

        if (reachedThreshold) {
            MinecraftForge.EVENT_BUS.post(new HealthThresholdEvent.Reached(
                    entity, threshold, Math.max(currentHealth - effectiveDamage, hMin)));
        }

        return effectiveDamage;
    }
}
