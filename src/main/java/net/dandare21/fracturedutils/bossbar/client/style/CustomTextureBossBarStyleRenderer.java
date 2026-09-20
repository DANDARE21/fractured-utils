package net.dandare21.fracturedutils.bossbar.client.style;

import net.dandare21.fracturedutils.bossbar.BossHealthBar;
import net.dandare21.fracturedutils.bossbar.client.ClientBossBarData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Renderer supporting custom texture sheets for boss healthbars.
 * Allows resource pack or mod authors to supply unique bossbar textures and UV layouts.
 */
public class CustomTextureBossBarStyleRenderer implements IBossBarStyleRenderer {
    public static final String ID = "custom_texture";

    @Override
    public String getStyleId() {
        return ID;
    }

    @Override
    public int getTotalHeight(ClientBossBarData bar) {
        return bar.getBarHeight() + 16;
    }

    @Override
    public void render(GuiGraphics graphics, ClientBossBarData bar, int centerX, int y, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int barWidth = bar.getBarWidth();
        int barHeight = bar.getBarHeight();
        int x = centerX - (barWidth / 2);
        int barY = y + 10;

        // 1. Draw Centered Boss Name
        Component name = bar.getName();
        int nameWidth = font.width(name);
        graphics.drawString(font, name, centerX - (nameWidth / 2), y, 0xFFFFFFFF, true);

        ResourceLocation texture = bar.getCustomTexture();
        if (texture != null) {
            // Draw background empty bar from custom texture at (u=0, v=0)
            graphics.blit(texture, x, barY, 0, 0, barWidth, barHeight);

            // Draw ghost bar if active
            if (bar.isShowGhostBar() && bar.getGhostPercent() > bar.getSmoothPercent()) {
                int ghostW = (int) (bar.getGhostPercent() * barWidth);
                int mainW = (int) (bar.getSmoothPercent() * barWidth);
                if (ghostW > mainW) {
                    graphics.fill(x + mainW, barY, x + ghostW, barY + barHeight, 0x88FFAA00);
                }
            }

            // Draw filled foreground bar from custom texture at (u=0, v=barHeight)
            int fillW = (int) (bar.getSmoothPercent() * barWidth);
            if (fillW > 0) {
                graphics.blit(texture, x, barY, 0, barHeight, fillW, barHeight);
            }
        } else {
            // Fallback: draw dark track with color fill
            graphics.fill(x - 1, barY - 1, x + barWidth + 1, barY + barHeight + 1, 0xFF000000);
            graphics.fill(x, barY, x + barWidth, barY + barHeight, 0xFF222222);

            int fillW = (int) (bar.getSmoothPercent() * barWidth);
            if (fillW > 0) {
                graphics.fill(x, barY, x + fillW, barY + barHeight, 0xFFFF2233);
            }
        }

        // Draw Phase Markers
        for (float threshold : bar.getPhaseThresholds()) {
            int markerX = x + (int) (threshold * barWidth);
            graphics.fill(markerX, barY - 1, markerX + 1, barY + barHeight + 1, 0xFFFFFFFF);
        }

        // Draw Health Numbers
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
