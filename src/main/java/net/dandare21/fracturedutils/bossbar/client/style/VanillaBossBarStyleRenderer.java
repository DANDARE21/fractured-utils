package net.dandare21.fracturedutils.bossbar.client.style;

import net.dandare21.fracturedutils.bossbar.BossHealthBar;
import net.dandare21.fracturedutils.bossbar.client.ClientBossBarData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;

import java.util.Locale;

/**
 * Default Minecraft style bossbar renderer.
 * Exactly matches vanilla Minecraft's boss healthbar rendering, textures, colors, and notches.
 */
public class VanillaBossBarStyleRenderer implements IBossBarStyleRenderer {
    public static final String ID = "default";
    public static final ResourceLocation BARS_LOCATION = new ResourceLocation("textures/gui/bars.png");

    @Override
    public String getStyleId() {
        return ID;
    }

    @Override
    public int getTotalHeight(ClientBossBarData bar) {
        return bar.getTextDisplayMode() != BossHealthBar.TextDisplayMode.NONE ? 24 : 19;
    }

    @Override
    public void render(GuiGraphics graphics, ClientBossBarData bar, int centerX, int y, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int barWidth = 182;
        int barHeight = 5;
        int x = centerX - (barWidth / 2);
        int barY = y + 10;

        // 1. Draw Boss Name Centered
        Component name = bar.getName();
        int nameWidth = font.width(name);
        graphics.drawString(font, name, centerX - (nameWidth / 2), y, 0xFFFFFFFF, true);

        // 2. Determine Vanilla Color and Overlay UVs
        int colorOrdinal = bar.getColor().ordinal();
        int overlayOrdinal = bar.getOverlay().ordinal();

        int bgV = colorOrdinal * 10;
        int fgV = bgV + 5;

        // 3. Draw Background Bar (Empty)
        graphics.blit(BARS_LOCATION, x, barY, 0, bgV, barWidth, barHeight);

        // 4. Draw Ghost Damage Trail (if enabled and higher than smooth percent)
        if (bar.isShowGhostBar() && bar.getGhostPercent() > bar.getSmoothPercent()) {
            int ghostW = (int) (bar.getGhostPercent() * barWidth);
            int mainW = (int) (bar.getSmoothPercent() * barWidth);
            if (ghostW > mainW) {
                // Ghost trail tinted white/yellow overlay over empty bar
                graphics.fill(x + mainW, barY, x + ghostW, barY + barHeight, 0x88FFAA00);
            }
        }

        // 5. Draw Foreground Bar (Filled)
        int fillW = (int) (bar.getSmoothPercent() * barWidth);
        if (fillW > 0) {
            graphics.blit(BARS_LOCATION, x, barY, 0, fgV, fillW, barHeight);
        }

        // 6. Draw Overlay Notches if specified
        if (bar.getOverlay() != BossEvent.BossBarOverlay.PROGRESS) {
            int notchBgV = 70 + (overlayOrdinal - 1) * 10;
            int notchFgV = notchBgV + 5;
            graphics.blit(BARS_LOCATION, x, barY, 0, notchBgV, barWidth, barHeight);
            if (fillW > 0) {
                graphics.blit(BARS_LOCATION, x, barY, 0, notchFgV, fillW, barHeight);
            }
        }

        // 7. Draw Phase Markers (if any)
        for (float threshold : bar.getPhaseThresholds()) {
            int markerX = x + (int) (threshold * barWidth);
            graphics.fill(markerX, barY - 1, markerX + 1, barY + barHeight + 1, 0xFFFFFFFF);
        }

        // 7b. Draw Active Entity Health Threshold Barrier Lines
        for (float threshold : bar.getEntityThresholds()) {
            int markerX = x + (int) (threshold * barWidth);
            // Dark drop shadow border for contrast
            graphics.fill(markerX - 1, barY - 1, markerX + 2, barY + barHeight + 1, 0xFF000000);
            // Glowing golden threshold core line
            graphics.fill(markerX, barY - 2, markerX + 1, barY + barHeight + 2, 0xFFFFDD00);
            // Indicator tick caps at top and bottom
            graphics.fill(markerX - 1, barY - 3, markerX + 2, barY - 1, 0xFFFFDD00);
            graphics.fill(markerX - 1, barY + barHeight + 1, markerX + 2, barY + barHeight + 3, 0xFFFFDD00);
        }

        // 8. Draw Optional Health Values
        if (bar.getTextDisplayMode() != BossHealthBar.TextDisplayMode.NONE) {
            String text = formatHealthText(bar);
            int tw = font.width(text);
            graphics.drawString(font, text, centerX - (tw / 2), barY + barHeight + 2, 0xFFDDDDDD, true);
        }
    }

    private String formatHealthText(ClientBossBarData bar) {
        int cur = Math.round(bar.getCurrentHealth());
        int max = Math.round(bar.getMaxHealth());
        int pct = Math.round(bar.getTargetPercent() * 100.0f);

        return switch (bar.getTextDisplayMode()) {
            case VALUE -> String.format(Locale.US, "%d / %d", cur, max);
            case PERCENT -> String.format(Locale.US, "%d%%", pct);
            case BOTH -> String.format(Locale.US, "%d / %d (%d%%)", cur, max, pct);
            case NONE -> "";
        };
    }
}
