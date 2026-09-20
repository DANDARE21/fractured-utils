package net.dandare21.fracturedutils.bossbar.client;

import net.dandare21.fracturedutils.bossbar.client.style.BossBarStyleRegistry;
import net.dandare21.fracturedutils.bossbar.client.style.IBossBarStyleRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.*;

/**
 * Client-side manager holding active boss healthbars and coordinating rendering.
 */
public class ClientBossBarManager {
    private static final ClientBossBarManager INSTANCE = new ClientBossBarManager();

    private final Map<String, ClientBossBarData> activeBossBars = new LinkedHashMap<>();

    public static ClientBossBarManager getInstance() {
        return INSTANCE;
    }

    private ClientBossBarManager() {
    }

    public synchronized void updateBossBars(List<ClientBossBarData> incoming) {
        Set<String> incomingIds = new HashSet<>();

        for (ClientBossBarData barData : incoming) {
            incomingIds.add(barData.getId());
            ClientBossBarData existing = activeBossBars.get(barData.getId());
            if (existing != null) {
                existing.updateFrom(barData);
            } else {
                activeBossBars.put(barData.getId(), barData);
            }
        }

        // Remove bossbars no longer present in incoming list
        activeBossBars.keySet().removeIf(id -> !incomingIds.contains(id));
    }

    public synchronized void clientTick() {
        for (ClientBossBarData bar : activeBossBars.values()) {
            bar.tick();
        }
    }

    public synchronized void clear() {
        activeBossBars.clear();
    }

    public synchronized void render(GuiGraphics graphics, float partialTick) {
        if (activeBossBars.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null && !mc.screen.isPauseScreen()) {
            // Keep rendering unless in special full-screen menus if preferred
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int centerX = screenWidth / 2;

        int vanillaBossCount = getVanillaBossCount(mc);
        int currentY = 12 + (vanillaBossCount * 19);

        for (ClientBossBarData bar : activeBossBars.values()) {
            if (!bar.isVisible()) {
                continue;
            }

            IBossBarStyleRenderer renderer = BossBarStyleRegistry.get(bar.getStyleId());
            renderer.render(graphics, bar, centerX, currentY, partialTick);
            currentY += renderer.getTotalHeight(bar);
        }
    }

    public synchronized boolean hasActiveBars() {
        return !activeBossBars.isEmpty();
    }

    private static java.lang.reflect.Field eventsField = null;
    private static boolean reflectionAttempted = false;

    private static int getVanillaBossCount(Minecraft mc) {
        if (!reflectionAttempted) {
            reflectionAttempted = true;
            try {
                for (java.lang.reflect.Field f : net.minecraft.client.gui.components.BossHealthOverlay.class.getDeclaredFields()) {
                    if (java.util.Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        eventsField = f;
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (eventsField != null && mc.gui != null && mc.gui.getBossOverlay() != null) {
            try {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) eventsField.get(mc.gui.getBossOverlay());
                return map != null ? map.size() : 0;
            } catch (Exception ignored) {}
        }
        return 0;
    }
}
