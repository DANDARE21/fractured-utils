package net.dandare21.fracturedutils.puppet.registry;

import net.dandare21.fracturedutils.puppet.action.AbyssalBarrageAction;
import net.dandare21.fracturedutils.puppet.action.LeapSlamAction;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionType;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
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

    /**
     * Resolves a puppet action type from an action ID, command string, or partial name.
     * Falls back to LEAP_SLAM if not found.
     */
    public static PuppetActionType<?> resolve(String query) {
        if (query == null || query.isBlank()) {
            return LEAP_SLAM;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);

        // Check if query is a command string containing "action:"
        if (q.contains("action:")) {
            for (String part : q.split("\\s+")) {
                if (part.startsWith("action:")) {
                    return resolve(part.substring(7));
                }
            }
        }

        // Direct match from registry by resource location
        if (q.contains(":")) {
            ResourceLocation rl = ResourceLocation.tryParse(q);
            if (rl != null && REGISTRY.containsKey(rl)) {
                return REGISTRY.get(rl);
            }
        }

        // Match by path or registry key
        for (Map.Entry<ResourceLocation, PuppetActionType<?>> entry : REGISTRY.entrySet()) {
            ResourceLocation rl = entry.getKey();
            if (rl.toString().equalsIgnoreCase(q) || rl.getPath().equalsIgnoreCase(q)) {
                return entry.getValue();
            }
            if (q.contains(rl.getPath())) {
                return entry.getValue();
            }
        }

        return LEAP_SLAM;
    }
}
