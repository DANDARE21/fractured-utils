package net.dandare21.fracturedutils.screeneffect;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.screeneffect.effects.HueShiftEffect;
import net.dandare21.fracturedutils.screeneffect.effects.ImpactFrameEffect;
import net.dandare21.fracturedutils.screeneffect.effects.InvertColorsEffect;
import net.dandare21.fracturedutils.screeneffect.effects.ScreenShakeEffect;
import net.dandare21.fracturedutils.screeneffect.effects.StrobeEffect;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for screen effect types.
 * Third-party mods can call {@link #register(ScreenEffectType)} to add their own screen effects.
 */
public class ScreenEffectRegistry {
    private static final Map<ResourceLocation, ScreenEffectType<?>> REGISTRY = new ConcurrentHashMap<>();

    static {
        register(ScreenShakeEffect.TYPE);
        register(InvertColorsEffect.TYPE);
        register(StrobeEffect.TYPE);
        register(HueShiftEffect.TYPE);
        register(ImpactFrameEffect.TYPE);
    }

    public static synchronized void register(ScreenEffectType<?> type) {
        if (type == null || type.getId() == null) {
            throw new IllegalArgumentException("ScreenEffectType and its ID cannot be null");
        }
        REGISTRY.put(type.getId(), type);
        FracturedUtils.LOGGER.info("[ScreenEffectRegistry] Registered screen effect type: {}", type.getId());
    }

    public static ScreenEffectType<?> get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static ScreenEffectType<?> get(String idStr) {
        if (idStr == null || idStr.isBlank()) return null;
        if (!idStr.contains(":")) {
            idStr = FracturedUtils.MOD_ID + ":" + idStr.toLowerCase(java.util.Locale.ROOT);
        }
        ResourceLocation rl = ResourceLocation.tryParse(idStr);
        return rl != null ? REGISTRY.get(rl) : null;
    }

    public static Collection<ScreenEffectType<?>> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static boolean contains(ResourceLocation id) {
        return REGISTRY.containsKey(id);
    }
}
