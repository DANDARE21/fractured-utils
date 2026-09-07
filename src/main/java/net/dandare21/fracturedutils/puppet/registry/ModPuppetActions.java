package net.dandare21.fracturedutils.puppet.registry;

import net.dandare21.fracturedutils.puppet.action.AbyssalBarrageAction;
import net.dandare21.fracturedutils.puppet.action.LeapSlamAction;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionType;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all global PuppetActionType definitions.
 */
public class ModPuppetActions {
    private static final Map<ResourceLocation, PuppetActionType<?>> REGISTRY = new ConcurrentHashMap<>();

    // Registered default actions
    public static final LeapSlamAction LEAP_SLAM = register(new LeapSlamAction());
    public static final AbyssalBarrageAction ABYSSAL_BARRAGE = register(new AbyssalBarrageAction());

    public static <T, A extends PuppetActionType<T>> A register(A action) {
        REGISTRY.put(action.getId(), action);
        return action;
    }

    public static PuppetActionType<?> get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Optional<PuppetActionType<?>> getOptional(ResourceLocation id) {
        return Optional.ofNullable(REGISTRY.get(id));
    }

    public static Collection<PuppetActionType<?>> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static boolean contains(ResourceLocation id) {
        return REGISTRY.containsKey(id);
    }
}
