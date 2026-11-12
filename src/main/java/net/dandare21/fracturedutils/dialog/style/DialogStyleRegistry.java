package net.dandare21.fracturedutils.dialog.style;

import net.dandare21.fracturedutils.FracturedUtils;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DialogStyleRegistry {
    public static final ResourceLocation CYBERPUNK_ID = new ResourceLocation(FracturedUtils.MOD_ID, "cyberpunk");
    public static final ResourceLocation RETRO_UNDERTALE_ID = new ResourceLocation(FracturedUtils.MOD_ID, "retro_undertale");
    public static final ResourceLocation FANTASY_PARCHMENT_ID = new ResourceLocation(FracturedUtils.MOD_ID, "fantasy_parchment");
    public static final ResourceLocation MINIMAL_DARK_ID = new ResourceLocation(FracturedUtils.MOD_ID, "minimal_dark");

    public static final DialogStyle CYBERPUNK = new DialogStyle(
            CYBERPUNK_ID,
            Optional.empty(),
            Optional.empty(),
            0xF505090C,
            0xFF00E5FF,
            0x4400E5FF,
            0xFF00E5FF,
            0xFFFFFFFF,
            0xF50A1622,
            0xFF00E5FF,
            0xFFFFFFFF,
            16, 16, 8, 8,
            true
    );

    public static final DialogStyle RETRO_UNDERTALE = new DialogStyle(
            RETRO_UNDERTALE_ID,
            Optional.empty(),
            Optional.empty(),
            0xFF000000,
            0xFFFFFFFF,
            0x88FFFFFF,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFF000000,
            0xFFFFFFFF,
            0xFFFFFFFF,
            16, 16, 8, 8,
            false
    );

    public static final DialogStyle FANTASY_PARCHMENT = new DialogStyle(
            FANTASY_PARCHMENT_ID,
            Optional.empty(),
            Optional.empty(),
            0xF51A140E,
            0xFFFFD700,
            0x55FFD700,
            0xFFFFD700,
            0xFFFFF8DC,
            0xF5261C12,
            0xFFFFD700,
            0xFFFFEAA7,
            16, 16, 8, 8,
            false
    );

    public static final DialogStyle MINIMAL_DARK = new DialogStyle(
            MINIMAL_DARK_ID,
            Optional.empty(),
            Optional.empty(),
            0xCC111111,
            0x88444444,
            0x22444444,
            0x88444444,
            0xFFEEEEEE,
            0xDD1A1A1A,
            0x88555555,
            0xFFFFFFFF,
            14, 14, 8, 8,
            false
    );

    private static final Map<ResourceLocation, DialogStyle> STYLES = new ConcurrentHashMap<>();

    static {
        registerBuiltins();
    }

    public static void registerBuiltins() {
        STYLES.put(CYBERPUNK_ID, CYBERPUNK);
        STYLES.put(RETRO_UNDERTALE_ID, RETRO_UNDERTALE);
        STYLES.put(FANTASY_PARCHMENT_ID, FANTASY_PARCHMENT);
        STYLES.put(MINIMAL_DARK_ID, MINIMAL_DARK);
    }

    public static void register(DialogStyle style) {
        if (style != null && style.id() != null) {
            STYLES.put(style.id(), style);
        }
    }

    public static DialogStyle get(ResourceLocation id) {
        if (id == null) return CYBERPUNK;
        DialogStyle found = STYLES.get(id);
        return found != null ? found : CYBERPUNK;
    }

    public static DialogStyle get(String idStr) {
        if (idStr == null || idStr.isBlank()) return CYBERPUNK;
        try {
            ResourceLocation rl = idStr.contains(":") ? new ResourceLocation(idStr) : new ResourceLocation(FracturedUtils.MOD_ID, idStr);
            return get(rl);
        } catch (Exception e) {
            return CYBERPUNK;
        }
    }

    public static void reset() {
        STYLES.clear();
        registerBuiltins();
    }

    public static Map<ResourceLocation, DialogStyle> getAll() {
        return Map.copyOf(STYLES);
    }
}

