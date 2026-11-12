package net.dandare21.fracturedutils.dialog.style;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class NineSliceRenderer {

    /**
     * Renders a dialog frame according to the given DialogStyle.
     * Uses custom 9-slice texture if configured; otherwise draws styled procedural borders.
     */
    public static void renderFrame(GuiGraphics guiGraphics, DialogStyle style, int x, int y, int width, int height) {
        if (style == null) return;

        if (style.texture().isPresent() && style.nineSlice().isPresent()) {
            renderNineSliceTexture(guiGraphics, style.texture().get(), style.nineSlice().get(), x, y, width, height);
            return;
        }

        renderProceduralBox(guiGraphics, style, x, y, width, height);
    }

    public static void renderNineSliceTexture(GuiGraphics guiGraphics, ResourceLocation texture, NineSliceConfig cfg, int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int texW = cfg.textureWidth();
        int texH = cfg.textureHeight();
        int sl = cfg.sliceLeft();
        int st = cfg.sliceTop();
        int sr = cfg.sliceRight();
        int sb = cfg.sliceBottom();

        int centerW = texW - sl - sr;
        int centerH = texH - st - sb;
        int targetCenterW = width - sl - sr;
        int targetCenterH = height - st - sb;

        // 1. Top-Left Corner
        guiGraphics.blit(texture, x, y, 0, 0, sl, st, texW, texH);
        // 2. Top-Right Corner
        guiGraphics.blit(texture, x + width - sr, y, texW - sr, 0, sr, st, texW, texH);
        // 3. Bottom-Left Corner
        guiGraphics.blit(texture, x, y + height - sb, 0, texH - sb, sl, sb, texW, texH);
        // 4. Bottom-Right Corner
        guiGraphics.blit(texture, x + width - sr, y + height - sb, texW - sr, texH - sb, sr, sb, texW, texH);

        // 5. Top Edge
        if (targetCenterW > 0) {
            guiGraphics.blit(texture, x + sl, y, targetCenterW, st, sl, 0, centerW, st, texW, texH);
        }
        // 6. Bottom Edge
        if (targetCenterW > 0) {
            guiGraphics.blit(texture, x + sl, y + height - sb, targetCenterW, sb, sl, texH - sb, centerW, sb, texW, texH);
        }
        // 7. Left Edge
        if (targetCenterH > 0) {
            guiGraphics.blit(texture, x, y + st, sl, targetCenterH, 0, st, sl, centerH, texW, texH);
        }
        // 8. Right Edge
        if (targetCenterH > 0) {
            guiGraphics.blit(texture, x + width - sr, y + st, sr, targetCenterH, texW - sr, st, sr, centerH, texW, texH);
        }
        // 9. Center
        if (targetCenterW > 0 && targetCenterH > 0) {
            guiGraphics.blit(texture, x + sl, y + st, targetCenterW, targetCenterH, sl, st, centerW, centerH, texW, texH);
        }
    }

    public static void renderProceduralBox(GuiGraphics guiGraphics, DialogStyle style, int boxX, int boxY, int boxW, int boxH) {
        // 1. Background Fill
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, style.backgroundColor());

        // 2. Outer Border
        int borderColor = style.borderColor();
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + 1, borderColor);
        guiGraphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, borderColor);
        guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxH, borderColor);
        guiGraphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, borderColor);

        // 3. Inner Border (if non-zero alpha)
        int innerColor = style.innerBorderColor();
        if ((innerColor & 0xFF000000) != 0 && boxW > 4 && boxH > 4) {
            guiGraphics.fill(boxX + 2, boxY + 2, boxX + boxW - 2, boxY + 3, innerColor);
            guiGraphics.fill(boxX + 2, boxY + boxH - 3, boxX + boxW - 2, boxY + boxH - 2, innerColor);
            guiGraphics.fill(boxX + 2, boxY + 2, boxX + 3, boxY + boxH - 2, innerColor);
            guiGraphics.fill(boxX + boxW - 3, boxY + 2, boxX + boxW - 2, boxY + boxH - 2, innerColor);
        }

        // 4. Corner Notches
        if (style.drawCornerNotches() && boxW > 16 && boxH > 16) {
            int cornerColor = style.cornerColor();
            // Top-Left
            guiGraphics.fill(boxX + 3, boxY + 3, boxX + 8, boxY + 5, cornerColor);
            guiGraphics.fill(boxX + 3, boxY + 3, boxX + 5, boxY + 8, cornerColor);
            // Top-Right
            guiGraphics.fill(boxX + boxW - 8, boxY + 3, boxX + boxW - 3, boxY + 5, cornerColor);
            guiGraphics.fill(boxX + boxW - 5, boxY + 3, boxX + boxW - 3, boxY + 8, cornerColor);
            // Bottom-Left
            guiGraphics.fill(boxX + 3, boxY + boxH - 5, boxX + 8, boxY + boxH - 3, cornerColor);
            guiGraphics.fill(boxX + 3, boxY + boxH - 8, boxX + 5, boxY + boxH - 3, cornerColor);
            // Bottom-Right
            guiGraphics.fill(boxX + boxW - 8, boxY + boxH - 5, boxX + boxW - 3, boxY + boxH - 3, cornerColor);
            guiGraphics.fill(boxX + boxW - 5, boxY + boxH - 8, boxX + boxW - 3, boxY + boxH - 3, cornerColor);
        }
    }
}

