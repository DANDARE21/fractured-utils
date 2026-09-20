package net.dandare21.fracturedutils.bossbar.feature;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Registry for modular BossBar features.
 */
public class BossBarFeatureRegistry {

    private static final Map<String, Supplier<IBossBarFeature>> FACTORIES = new ConcurrentHashMap<>();

    static {
        register(PhaseMarkersFeature.ID, PhaseMarkersFeature::new);
    }

    public static void register(String featureId, Supplier<IBossBarFeature> factory) {
        FACTORIES.put(featureId, factory);
    }

    public static IBossBarFeature create(String featureId) {
        Supplier<IBossBarFeature> supplier = FACTORIES.get(featureId);
        return supplier != null ? supplier.get() : null;
    }

    public static boolean hasFeature(String featureId) {
        return FACTORIES.containsKey(featureId);
    }
}
