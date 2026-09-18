package net.dandare21.fracturedutils.screeneffect.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.dandare21.fracturedutils.screeneffect.effects.ImpactFrameEffect;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/**
 * High-fidelity renderer for the anime/manga "Draw" impact frame effect.
 * Renders the central fracture core, pixel-stepped radial rays, dotted bead trails,
 * 4-pointed manga sparkle crosses, and floating debris in two designated colors.
 */
public class MangaDrawImpactRenderer {

    public static void render(GuiGraphics graphics, int width, int height, ImpactFrameEffect.ImpactFrameInstance instance) {
        long elapsed = System.currentTimeMillis() - instance.getStartTimeMs();
        int interval = Math.max(20, instance.getFrameIntervalMs());
        int frameIndex = (int) (elapsed / interval);

        boolean isNegativeFrame = (frameIndex % 2 == 0);

        int pColor = instance.getPrimaryColor();
        int sColor = instance.getSecondaryColor();

        // If inverted cut frames are active, swap colors on negative frames
        if (instance.isInvertWorld() && isNegativeFrame) {
            int temp = pColor;
            pColor = sColor;
            sColor = temp;
        }

        // Ensure full alpha if not provided
        if ((pColor & 0xFF000000) == 0) pColor |= 0xFF000000;
        if ((sColor & 0xFF000000) == 0) sColor |= 0xFF000000;

        float cx = width / 2.0f;
        float cy = height * 0.50f; // Center focal point (aiming reticle / combat center)

        // Seeded random per frame for animated twitch / sakuga jitter
        long seed = instance.getStartTimeMs() ^ ((long) frameIndex * 100003L);
        Random rand = new Random(seed);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 1. Render Dotted Bead Trails radiating outward towards edges
        renderDottedTrails(graphics, cx, cy, width, height, rand, pColor, sColor);

        // 2. Render Pixel-Stepped Radial Beam Rays
        renderPixelSteppedRays(graphics, cx, cy, width, height, rand, pColor, sColor);

        // 3. Render Floating Debris Rectangles
        renderDebris(graphics, cx, cy, rand, pColor, sColor);

        // 4. Render 4-Pointed Manga Sparkle Crosses (Retro anime impact stars)
        renderSparkleField(graphics, cx, cy, width, height, rand, pColor, sColor);

        // 5. Render Central Fracture Burst Core
        renderCentralCore(graphics, cx, cy, rand, pColor, sColor);

        RenderSystem.disableBlend();
    }

    /**
     * Renders dotted bead trails (chains of square dots radiating out to edges).
     */
    private static void renderDottedTrails(GuiGraphics graphics, float cx, float cy, int width, int height, Random rand, int pColor, int sColor) {
        float maxR = (float) Math.hypot(width, height) * 0.75f;
        int numTrails = 14 + rand.nextInt(6);
        float angleStep = (float) (Math.PI * 2.0 / numTrails);

        for (int i = 0; i < numTrails; i++) {
            float angle = i * angleStep + (rand.nextFloat() - 0.5f) * 0.25f;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            float startDist = 35.0f + rand.nextFloat() * 30.0f;
            float endDist = maxR * (0.85f + rand.nextFloat() * 0.25f);
            float step = 7.0f + rand.nextInt(4); // bead spacing

            int beadSize = (rand.nextFloat() < 0.25f) ? 3 : 2;

            for (float d = startDist; d < endDist; d += step) {
                int px = Math.round(cx + cos * d);
                int py = Math.round(cy + sin * d);

                // Border 1px
                graphics.fill(px - 1, py - 1, px + beadSize + 1, py + beadSize + 1, sColor);
                // Inner bead
                graphics.fill(px, py, px + beadSize, py + beadSize, pColor);
            }
        }
    }

    /**
     * Renders chunky, pixel-stepped solid rays shooting from center to screen boundaries.
     */
    private static void renderPixelSteppedRays(GuiGraphics graphics, float cx, float cy, int width, int height, Random rand, int pColor, int sColor) {
        float maxR = (float) Math.hypot(width, height) * 0.8f;
        int numRays = 18 + rand.nextInt(8);
        float stepAngle = (float) (Math.PI * 2.0 / numRays);

        for (int i = 0; i < numRays; i++) {
            float angle = i * stepAngle + (rand.nextFloat() - 0.5f) * 0.3f;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            float startR = 25.0f + rand.nextFloat() * 20.0f;
            float length = maxR * (0.6f + rand.nextFloat() * 0.45f);

            int rayWidth = 2 + rand.nextInt(3); // 2 to 4px ray width
            int pixelStep = 3; // quantize along vector in 3px increments

            float curR = startR;
            while (curR < length) {
                int px = Math.round(cx + cos * curR);
                int py = Math.round(cy + sin * curR);

                // Draw stepped segment
                int w = rayWidth;
                int h = rayWidth;

                // Draw black outline
                graphics.fill(px - 1, py - 1, px + w + 1, py + h + 1, sColor);
                // Draw white ray fill
                graphics.fill(px, py, px + w, py + h, pColor);

                curR += pixelStep;
            }
        }
    }

    /**
     * Renders dense cluster of pixel-art 4-pointed manga sparkle crosses.
     */
    private static void renderSparkleField(GuiGraphics graphics, float cx, float cy, int width, int height, Random rand, int pColor, int sColor) {
        // We place sparkles in 3 concentric zones: inner (dense, medium/small), mid (large hero crosses), outer (scattered)
        int numSparkles = 32 + rand.nextInt(12);

        for (int i = 0; i < numSparkles; i++) {
            // Distance distribution: more clustered around mid and center
            float distRatio = rand.nextFloat();
            float dist = 20.0f + distRatio * distRatio * (Math.min(width, height) * 0.65f);
            float angle = rand.nextFloat() * (float) (Math.PI * 2.0);

            int sx = Math.round(cx + (float) Math.cos(angle) * dist);
            int sy = Math.round(cy + (float) Math.sin(angle) * dist);

            // Skip off-screen
            if (sx < -40 || sx > width + 40 || sy < -40 || sy > height + 40) continue;

            boolean isDiagonal = rand.nextBoolean();
            int sizeRoll = rand.nextInt(10);
            int radius;
            boolean hollow = false;

            if (sizeRoll >= 8) {
                // Large hero cross (16-24px radius)
                radius = 16 + rand.nextInt(8);
                hollow = true; // classic anime hollow center square
            } else if (sizeRoll >= 4) {
                // Medium cross (10-14px radius)
                radius = 10 + rand.nextInt(5);
                hollow = (rand.nextFloat() < 0.4f);
            } else if (sizeRoll >= 1) {
                // Small cross (6-8px radius)
                radius = 6 + rand.nextInt(3);
            } else {
                // Tiny micro-dot cross (3-4px radius)
                radius = 3 + rand.nextInt(2);
            }

            if (isDiagonal) {
                drawDiagonalCross(graphics, sx, sy, radius, pColor, sColor, hollow);
            } else {
                drawCardinalCross(graphics, sx, sy, radius, pColor, sColor, hollow);
            }
        }
    }

    /**
     * Draws a cardinal (+) 4-pointed manga sparkle cross with crisp pixel-art stepped arms.
     */
    public static void drawCardinalCross(GuiGraphics graphics, int ox, int oy, int r, int fillColor, int borderColor, boolean hollow) {
        if (r <= 3) {
            // Micro cross: 3x3 center plus 1px tips
            graphics.fill(ox - 2, oy - 2, ox + 3, oy + 3, borderColor);
            graphics.fill(ox - 1, oy - 1, ox + 2, oy + 2, fillColor);
            graphics.fill(ox, oy - r, ox + 1, oy + r + 1, fillColor);
            graphics.fill(ox - r, oy, ox + r + 1, oy + 1, fillColor);
            return;
        }

        int armBaseHalf = Math.max(1, r / 4);

        // 1. Draw outer border in secondary color
        int bo = 1; // border offset
        // Vertical border bar
        graphics.fill(ox - armBaseHalf - bo, oy - r - bo, ox + armBaseHalf + 1 + bo, oy + r + 1 + bo, borderColor);
        // Horizontal border bar
        graphics.fill(ox - r - bo, oy - armBaseHalf - bo, ox + r + 1 + bo, oy + armBaseHalf + 1 + bo, borderColor);
        // Center box border
        int cBox = armBaseHalf + 2;
        graphics.fill(ox - cBox - bo, oy - cBox - bo, ox + cBox + 1 + bo, oy + cBox + 1 + bo, borderColor);

        // 2. Draw stepped arm fills in primary color
        // Vertical core
        graphics.fill(ox - armBaseHalf, oy - r, ox + armBaseHalf + 1, oy + r + 1, fillColor);
        // Horizontal core
        graphics.fill(ox - r, oy - armBaseHalf, ox + r + 1, oy + armBaseHalf + 1, fillColor);
        // Center thickened box
        graphics.fill(ox - cBox, oy - cBox, ox + cBox + 1, oy + cBox + 1, fillColor);

        // Stepped tapered arm tips
        int tipR = r - Math.max(2, r / 3);
        if (tipR > 0) {
            int tipHalf = Math.max(0, armBaseHalf - 1);
            graphics.fill(ox - tipHalf, oy - r, ox + tipHalf + 1, oy - tipR, fillColor);
            graphics.fill(ox - tipHalf, oy + tipR, ox + tipHalf + 1, oy + r + 1, fillColor);
            graphics.fill(ox - r, oy - tipHalf, ox - tipR, oy + tipHalf + 1, fillColor);
            graphics.fill(ox + tipR, oy - tipHalf, ox + r + 1, oy + tipHalf + 1, fillColor);
        }

        // 3. Optional hollow center cutout
        if (hollow && r >= 12) {
            int holeHalf = Math.max(1, r / 8);
            graphics.fill(ox - holeHalf, oy - holeHalf, ox + holeHalf + 1, oy + holeHalf + 1, borderColor);
        }
    }

    /**
     * Draws a diagonal (x) 4-pointed manga sparkle cross using stepped diagonal pixel squares.
     */
    public static void drawDiagonalCross(GuiGraphics graphics, int ox, int oy, int r, int fillColor, int borderColor, boolean hollow) {
        if (r <= 4) {
            // Small diagonal star
            for (int d = -r; d <= r; d++) {
                graphics.fill(ox + d - 1, oy + d - 1, ox + d + 2, oy + d + 2, borderColor);
                graphics.fill(ox + d - 1, oy - d - 1, ox + d + 2, oy - d + 2, borderColor);
            }
            for (int d = -r; d <= r; d++) {
                graphics.fill(ox + d, oy + d, ox + d + 1, oy + d + 1, fillColor);
                graphics.fill(ox + d, oy - d, ox + d + 1, oy - d + 1, fillColor);
            }
            return;
        }

        // Center hub
        int cSize = Math.max(2, r / 3);
        graphics.fill(ox - cSize - 1, oy - cSize - 1, ox + cSize + 2, oy + cSize + 2, borderColor);
        graphics.fill(ox - cSize, oy - cSize, ox + cSize + 1, oy + cSize + 1, fillColor);

        // Step diagonally outwards along 4 arms
        int step = 2;
        for (int d = cSize; d <= r; d += step) {
            int boxW = (d > r - 3) ? 1 : ((d > r - 7) ? 2 : 3);
            int half = boxW / 2;

            // 4 diagonal positions: (d, d), (-d, -d), (d, -d), (-d, d)
            int[][] pts = { {ox + d, oy + d}, {ox - d, oy - d}, {ox + d, oy - d}, {ox - d, oy + d} };
            for (int[] pt : pts) {
                // Border
                graphics.fill(pt[0] - half - 1, pt[1] - half - 1, pt[0] + half + 2, pt[1] + half + 2, borderColor);
            }
            for (int[] pt : pts) {
                // Fill
                graphics.fill(pt[0] - half, pt[1] - half, pt[0] + half + 1, pt[1] + half + 1, fillColor);
            }
        }

        if (hollow && r >= 12) {
            int holeHalf = Math.max(1, r / 7);
            graphics.fill(ox - holeHalf, oy - holeHalf, ox + holeHalf + 1, oy + holeHalf + 1, borderColor);
        }
    }

    /**
     * Renders the jagged, shattered central explosion core.
     */
    private static void renderCentralCore(GuiGraphics graphics, float cx, float cy, Random rand, int pColor, int sColor) {
        int icx = Math.round(cx);
        int icy = Math.round(cy);

        // 1. Base central fracture shards (overlapping blocky pixel chunks)
        int numChunks = 14 + rand.nextInt(6);
        for (int i = 0; i < numChunks; i++) {
            int w = 12 + rand.nextInt(28);
            int h = 8 + rand.nextInt(24);
            int ox = icx + (rand.nextInt(41) - 20) - (w / 2);
            int oy = icy + (rand.nextInt(41) - 20) - (h / 2);

            // Border
            graphics.fill(ox - 1, oy - 1, ox + w + 1, oy + h + 1, sColor);
            // Core fill
            graphics.fill(ox, oy, ox + w, oy + h, pColor);
        }

        // 2. Center bright heart block
        int coreW = 20 + rand.nextInt(12);
        int coreH = 18 + rand.nextInt(10);
        graphics.fill(icx - coreW / 2 - 2, icy - coreH / 2 - 2, icx + coreW / 2 + 2, icy + coreH / 2 + 2, sColor);
        graphics.fill(icx - coreW / 2, icy - coreH / 2, icx + coreW / 2, icy + coreH / 2, pColor);

        // 3. Inner contrast notches/cutouts giving the iconic shattered negative-space look
        int numCutouts = 4 + rand.nextInt(4);
        for (int i = 0; i < numCutouts; i++) {
            int cw = 3 + rand.nextInt(6);
            int ch = 3 + rand.nextInt(6);
            int cox = icx + (rand.nextInt(coreW) - coreW / 2);
            int coy = icy + (rand.nextInt(coreH) - coreH / 2);
            graphics.fill(cox, coy, cox + cw, coy + ch, sColor);
        }

        // 4. Center large sparkle star anchor
        drawCardinalCross(graphics, icx, icy, 26 + rand.nextInt(6), pColor, sColor, true);
    }

    /**
     * Renders floating debris rectangles in the upper mid-ground.
     */
    private static void renderDebris(GuiGraphics graphics, float cx, float cy, Random rand, int pColor, int sColor) {
        int numDebris = 10 + rand.nextInt(6);
        for (int i = 0; i < numDebris; i++) {
            float angle = (float) (-Math.PI * 0.15 - rand.nextFloat() * Math.PI * 0.7); // mostly upper hemisphere
            float dist = 50.0f + rand.nextFloat() * 120.0f;

            int dx = Math.round(cx + (float) Math.cos(angle) * dist);
            int dy = Math.round(cy + (float) Math.sin(angle) * dist);

            int dw = 6 + rand.nextInt(12);
            int dh = 4 + rand.nextInt(10);

            // Shard with border
            graphics.fill(dx - 1, dy - 1, dx + dw + 1, dy + dh + 1, sColor);
            graphics.fill(dx, dy, dx + dw, dy + dh, pColor);
        }
    }
}
