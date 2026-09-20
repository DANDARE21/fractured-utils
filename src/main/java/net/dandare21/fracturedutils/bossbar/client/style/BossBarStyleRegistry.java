package net.dandare21.fracturedutils.bossbar.client.style;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for custom bossbar style renderers.
 */
public class BossBarStyleRegistry {
    private static final Map<String, IBossBarStyleRenderer> RENDERERS = new ConcurrentHashMap<>();
    private static final IBossBarStyleRenderer DEFAULT_RENDERER = new VanillaBossBarStyleRenderer();

    static {
        register(DEFAULT_RENDERER);
        register(new FracturedBossBarStyleRenderer());
        register(new CustomTextureBossBarStyleRenderer());
    }

    public static void register(IBossBarStyleRenderer renderer) {
        RENDERERS.put(renderer.getStyleId(), renderer);
    }

    public static IBossBarStyleRenderer get(String styleId) {
        if (styleId == null) return DEFAULT_RENDERER;
        return RENDERERS.getOrDefault(styleId, DEFAULT_RENDERER);
    }

    public static boolean hasStyle(String styleId) {
        return RENDERERS.containsKey(styleId);
    }
}
