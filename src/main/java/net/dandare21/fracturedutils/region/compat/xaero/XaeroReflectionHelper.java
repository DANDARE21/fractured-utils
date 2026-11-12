package net.dandare21.fracturedutils.region.compat.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Safe reflection helper for Xaero's World Map (xaero.map.gui.GuiMap).
 * Caches field and method accessors for cameraX, cameraZ, scale, and screenScale.
 * Accurately calculates effective scale in Minecraft GUI coordinates to eliminate parallax drift.
 */
public class XaeroReflectionHelper {

    private static boolean initialized = false;
    private static Field fieldCameraX = null;
    private static Field fieldCameraZ = null;
    private static Field fieldScale = null;
    private static Field fieldScreenScale = null;

    private static Method methodGetCameraX = null;
    private static Method methodGetCameraZ = null;
    private static Method methodGetScale = null;

    public record MapViewport(double cameraX, double cameraZ, double scale, double guiScale) {
        /**
         * Effective scale in Minecraft GUI-coordinate units.
         * Xaero's World Map renders map tiles in physical framebuffer coordinates using raw scale.
         * GuiGraphics renders in GUI-scaled units (where 1 unit = guiScale physical pixels).
         * Dividing scale by guiScale aligns the polygon overlays with the map tiles pixel-for-pixel.
         */
        public double effectiveScale() {
            double gs = guiScale > 0.0001 ? guiScale : 1.0;
            return scale / gs;
        }
    }

    public static boolean isXaeroGuiMap(Screen screen) {
        if (screen == null) return false;
        return screen.getClass().getName().equals("xaero.map.gui.GuiMap");
    }

    private static void initReflection(Class<?> clazz) {
        if (initialized) return;
        initialized = true;

        // 1. Search for public/inherited methods on GuiMap
        for (Method m : clazz.getMethods()) {
            String name = m.getName().toLowerCase();
            if (m.getParameterCount() == 0) {
                if (name.equals("getcamerax") || name.equals("camerax")) {
                    methodGetCameraX = m;
                } else if (name.equals("getcameraz") || name.equals("cameraz")) {
                    methodGetCameraZ = m;
                } else if (name.equals("getscale") || name.equals("scale") || name.equals("getmapscale")) {
                    methodGetScale = m;
                }
            }
        }

        // 2. Search hierarchy for exact fields
        fieldCameraX = findFieldInHierarchy(clazz, new String[]{"cameraX", "cx"});
        fieldCameraZ = findFieldInHierarchy(clazz, new String[]{"cameraZ", "cz"});
        fieldScale = findFieldInHierarchy(clazz, new String[]{"scale", "destScale"});
        fieldScreenScale = findFieldInHierarchy(clazz, new String[]{"screenScale"});
    }

    private static Field findFieldInHierarchy(Class<?> startClazz, String[] names) {
        for (String name : names) {
            Class<?> current = startClazz;
            while (current != null && current != Object.class && current != Screen.class) {
                try {
                    Field f = current.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {
                }
                current = current.getSuperclass();
            }
        }
        return null;
    }

    public static MapViewport getViewport(Screen screen) {
        if (!isXaeroGuiMap(screen)) {
            return null;
        }

        initReflection(screen.getClass());

        try {
            double cx = 0.0;
            if (methodGetCameraX != null) {
                cx = ((Number) methodGetCameraX.invoke(screen)).doubleValue();
            } else if (fieldCameraX != null) {
                cx = fieldCameraX.getDouble(screen);
            }

            double cz = 0.0;
            if (methodGetCameraZ != null) {
                cz = ((Number) methodGetCameraZ.invoke(screen)).doubleValue();
            } else if (fieldCameraZ != null) {
                cz = fieldCameraZ.getDouble(screen);
            }

            double scale = 1.0;
            if (methodGetScale != null) {
                scale = ((Number) methodGetScale.invoke(screen)).doubleValue();
            } else if (fieldScale != null) {
                scale = fieldScale.getDouble(screen);
            }
            if (scale <= 0.00001) scale = 1.0;

            double guiScale = 1.0;
            if (fieldScreenScale != null) {
                try {
                    guiScale = fieldScreenScale.getDouble(screen);
                } catch (Exception ignored) {
                }
            }
            if (guiScale <= 0.00001) {
                guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            }
            if (guiScale <= 0.00001) guiScale = 1.0;

            return new MapViewport(cx, cz, scale, guiScale);
        } catch (Exception e) {
            return null;
        }
    }
}
