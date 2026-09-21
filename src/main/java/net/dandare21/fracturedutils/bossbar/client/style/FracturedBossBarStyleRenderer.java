package net.dandare21.fracturedutils.bossbar.client.style;

import net.dandare21.fracturedutils.bossbar.BossHealthBar;
import net.dandare21.fracturedutils.bossbar.client.ClientBossBarData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Custom sci-fi / void themed bossbar renderer for Fractured Utils.
 * Features glowing holographic borders, corner bracket accents, vibrant fill,
 * and high-visibility phase and ghost damage indicators.
 */
public class FracturedBossBarStyleRenderer implements IBossBarStyleRenderer {
    public static final String ID = "fractured";

    @Override
    public String getStyleId() {
        return ID;
    }

    @Override
    public int getTotalHeight(ClientBossBarData bar) {
        return 28;
    }

    @Override
    public void render(GuiGraphics graphics, ClientBossBarData bar, int centerX, int y, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int barWidth = Math.max(200, bar.getBarWidth());
        int barHeight = Math.max(7, bar.getBarHeight());
        int x = centerX - (barWidth / 2);
        int barY = y + 12;

        // Primary theme color calculation
        int primaryColor = parseThemeColor(bar);
        int darkBgColor = 0xEE090C14;

        // 1. Draw Boss Name with Stylized Spacing
        Component name = bar.getName();
        int nameWidth = font.width(name);
        graphics.drawString(font, name, centerX - (nameWidth / 2), y, 0xFFFFFFFF, true);

        // 2. Base Dark Container Panel
        graphics.fill(x - 2, barY - 2, x + barWidth + 2, barY + barHeight + 2, darkBgColor);

        // 3. Glowing Outer Border Lines
        graphics.fill(x - 2, barY - 2, x + barWidth + 2, barY - 1, primaryColor);
        graphics.fill(x - 2, barY + barHeight + 1, x + barWidth + 2, barY + barHeight + 2, primaryColor);
        graphics.fill(x - 2, barY - 2, x - 1, barY + barHeight + 2, primaryColor);
        graphics.fill(x + barWidth + 1, barY - 2, x + barWidth + 2, barY + barHeight + 2, primaryColor);

        // 4. Tech Corner Bracket Accents
        graphics.fill(x - 4, barY - 4, x - 1, barY - 2, primaryColor);
        graphics.fill(x - 4, barY - 4, x - 2, barY - 1, primaryColor);
        graphics.fill(x + barWidth + 1, barY - 4, x + barWidth + 4, barY - 2, primaryColor);
        graphics.fill(x + barWidth + 2, barY - 4, x + barWidth + 4, barY - 1, primaryColor);

        graphics.fill(x - 4, barY + barHeight + 2, x - 1, barY + barHeight + 4, primaryColor);
        graphics.fill(x - 4, barY + barHeight + 1, x - 2, barY + barHeight + 4, primaryColor);
        graphics.fill(x + barWidth + 1, barY + barHeight + 2, x + barWidth + 4, barY + barHeight + 4, primaryColor);
        graphics.fill(x + barWidth + 2, barY + barHeight + 1, x + barWidth + 4, barY + barHeight + 4, primaryColor);

        // 5. Empty Bar Inner Track
        graphics.fill(x, barY, x + barWidth, barY + barHeight, 0xFF0E131F);

        // 6. Ghost Damage Trail Fill
        if (bar.isShowGhostBar() && bar.getGhostPercent() > bar.getSmoothPercent()) {
            int ghostW = (int) (bar.getGhostPercent() * barWidth);
            int mainW = (int) (bar.getSmoothPercent() * barWidth);
            if (ghostW > mainW) {
                graphics.fill(x + mainW, barY, x + ghostW, barY + barHeight, 0xCCFF5533);
            }
        }

        // 7. Active Health Fill
        int fillW = (int) (bar.getSmoothPercent() * barWidth);
        if (fillW > 0) {
            graphics.fill(x, barY, x + fillW, barY + barHeight, primaryColor);
            // Highlight shine strip on top of bar
            graphics.fill(x, barY, x + fillW, barY + 1, 0x88FFFFFF);
        }

        // 8. Phase Markers
        for (float threshold : bar.getPhaseThresholds()) {
            int markerX = x + (int) (threshold * barWidth);
            graphics.fill(markerX - 1, barY - 3, markerX + 1, barY + barHeight + 3, 0xFFFFFFFF);
            graphics.fill(markerX, barY - 4, markerX + 1, barY + barHeight + 4, primaryColor);
        }

        // 8b. Active Entity Health Threshold Barrier Lines
        for (float threshold : bar.getEntityThresholds()) {
            int markerX = x + (int) (threshold * barWidth);
            // Holographic barrier drop shadow
            graphics.fill(markerX - 2, barY - 4, markerX + 3, barY + barHeight + 4, 0xAA000000);
            graphics.fill(markerX - 1, barY - 5, markerX + 2, barY + barHeight + 5, 0xFFFFCC00);
            // Bright white energy center beam
            graphics.fill(markerX, barY - 4, markerX + 1, barY + barHeight + 4, 0xFFFFFFFF);
            // Diamond node accents at top and bottom
            graphics.fill(markerX - 2, barY - 6, markerX + 3, barY - 3, 0xFFFFCC00);
            graphics.fill(markerX - 2, barY + barHeight + 3, markerX + 3, barY + barHeight + 6, 0xFFFFCC00);
        }

        // 9. Numeric Health Info
        if (bar.getTextDisplayMode() != BossHealthBar.TextDisplayMode.NONE) {
            String text = formatHealthText(bar);
            int tw = font.width(text);
            graphics.drawString(font, text, centerX - (tw / 2), barY + barHeight + 4, 0xFF00FFDD, true);
        }
    }

    private int parseThemeColor(ClientBossBarData bar) {
        if (bar.getCustomColorHex() != null && !bar.getCustomColorHex().isEmpty()) {
            try {
                String hex = bar.getCustomColorHex().replace("#", "");
                if (hex.length() == 6) {
                    return (int) (0xFF000000L | Long.parseLong(hex, 16));
                } else if (hex.length() == 8) {
                    return (int) Long.parseLong(hex, 16);
                }
            } catch (Exception ignored) {}
        }

        return switch (bar.getColor()) {
            case PINK -> 0xFFFF44AA;
            case BLUE -> 0xFF00CCFF;
            case RED -> 0xFFFF2233;
            case GREEN -> 0xFF00FF66;
            case YELLOW -> 0xFFFFCC00;
            case PURPLE -> 0xFFAA33FF;
            case WHITE -> 0xFFEEEEEE;
        };
    }

    private String formatHealthText(ClientBossBarData bar) {
        int cur = Math.round(bar.getCurrentHealth());
        int max = Math.round(bar.getMaxHealth());
        int pct = Math.round(bar.getTargetPercent() * 100.0f);

        return switch (bar.getTextDisplayMode()) {
            case VALUE -> String.format(Locale.US, "%d / %d", cur, max);
            case PERCENT -> String.format(Locale.US, "%d%%", pct);
            case BOTH -> String.format(Locale.US, "%d / %d  //  %d%%", cur, max, pct);
            case NONE -> "";
        };
    }
}
