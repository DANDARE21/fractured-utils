package net.dandare21.fracturedutils.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * High-performance, high-fidelity DAW-like audio waveform renderer for Minecraft 1.20.1.
 * Renders smooth triangle-strip meshes with sub-pixel precision and screen-space scissor clipping.
 */
public class WaveformRenderer {

    // Default waveform theme: Cyan #00E5FF at ~85% opacity (217 / 255)
    public static final int DEFAULT_COLOR_R = 0;
    public static final int DEFAULT_COLOR_G = 229;
    public static final int DEFAULT_COLOR_B = 255;
    public static final int DEFAULT_COLOR_A = 217;

    // Subtle 0 dB silence center line color (35% opacity cyan)
    public static final int ZERO_DB_LINE_COLOR = 0x5900E5FF;

    /**
     * Renders waveform with explicit duration in milliseconds and horizontal zoom in pixels per second.
     */
    public static void renderWaveform(
            GuiGraphics guiGraphics,
            int channelX, int channelY, int channelWidth, int channelHeight,
            double pixelsPerSecond, double scrollOffsetMs,
            AudioWaveformExtractor.AudioPeak[] peaks, long totalDurationMs
    ) {
        renderWaveform(
                guiGraphics,
                channelX, channelY, channelWidth, channelHeight,
                pixelsPerSecond, scrollOffsetMs,
                peaks, totalDurationMs,
                DEFAULT_COLOR_R, DEFAULT_COLOR_G, DEFAULT_COLOR_B, DEFAULT_COLOR_A
        );
    }

    /**
     * Overload taking generic zoomFactor and scrollOffset.
     * If totalDurationMs is not provided, infers it from peak count assuming standard sampling rate.
     */
    public static void renderWaveform(
            GuiGraphics guiGraphics,
            int channelX, int channelY, int channelWidth, int channelHeight,
            double zoomFactor, double scrollOffset,
            AudioWaveformExtractor.AudioPeak[] peaks
    ) {
        long inferredDurationMs = (peaks != null && peaks.length > 0)
                ? (long) ((peaks.length / (double) AudioWaveformExtractor.DEFAULT_PEAKS_PER_SECOND) * 1000.0)
                : 180000L;
        renderWaveform(guiGraphics, channelX, channelY, channelWidth, channelHeight, zoomFactor, scrollOffset, peaks, inferredDurationMs);
    }

    /**
     * Full render implementation with custom RGBA color channels.
     */
    public static void renderWaveform(
            GuiGraphics guiGraphics,
            int channelX, int channelY, int channelWidth, int channelHeight,
            double pixelsPerSecond, double scrollOffsetMs,
            AudioWaveformExtractor.AudioPeak[] peaks, long totalDurationMs,
            int r, int g, int b, int a
    ) {
        if (channelWidth <= 0 || channelHeight <= 0) return;

        // 1. Subtle 1px center horizontal line representing 0 dB silence across middle of the channel
        float centerY = channelY + (channelHeight / 2.0f);
        float halfHeight = (channelHeight / 2.0f) - 2.0f; // 2px margin from top/bottom borders
        int centerLineY = Math.round(centerY);
        guiGraphics.fill(channelX, centerLineY, channelX + channelWidth, centerLineY + 1, ZERO_DB_LINE_COLOR);

        if (peaks == null || peaks.length == 0 || totalDurationMs <= 0 || pixelsPerSecond <= 0.0) {
            return;
        }

        // 2. Setup Screen-Space Scissor Clipping
        Matrix4f matrix = guiGraphics.pose().last().pose();
        float scaleX = matrix.m00();
        float scaleY = matrix.m11();
        float transX = matrix.m30();
        float transY = matrix.m31();

        int scissorMinX = (int) Math.floor(channelX * scaleX + transX);
        int scissorMinY = (int) Math.floor(channelY * scaleY + transY);
        int scissorMaxX = (int) Math.ceil((channelX + channelWidth) * scaleX + transX);
        int scissorMaxY = (int) Math.ceil((channelY + channelHeight) * scaleY + transY);

        guiGraphics.enableScissor(scissorMinX, scissorMinY, scissorMaxX, scissorMaxY);

        try {
            // 3. Viewport Culling & Visible Index Range Calculation
            double msPerPeak = (double) totalDurationMs / peaks.length;
            double visibleStartMs = scrollOffsetMs;
            double visibleEndMs = scrollOffsetMs + ((channelWidth / pixelsPerSecond) * 1000.0);

            // Audio is completely to the right or left of viewport
            if (visibleEndMs < 0 || visibleStartMs > totalDurationMs) {
                return;
            }

            int startIdx = (int) Math.floor(visibleStartMs / msPerPeak);
            int endIdx = (int) Math.ceil(visibleEndMs / msPerPeak);

            startIdx = Math.max(0, Math.min(peaks.length - 1, startIdx));
            endIdx = Math.max(0, Math.min(peaks.length, endIdx + 1));

            int count = endIdx - startIdx;
            if (count < 2) {
                // Ensure at least 2 vertices for triangle strip if at the track boundary
                if (startIdx < peaks.length - 1) {
                    endIdx = startIdx + 2;
                } else if (startIdx > 0) {
                    startIdx = endIdx - 2;
                } else {
                    return;
                }
            }

            // 4. Multi-level LOD / Dynamic Transient Preserving Step
            // If the visible range contains significantly more peaks than physical screen pixels,
            // downsample by chunk while preserving true min and max to eliminate GPU overhead on extreme zoom-outs
            double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            int maxVertices = (int) Math.max(1000, channelWidth * guiScale * 2);
            int step = 1;
            if ((endIdx - startIdx) > maxVertices) {
                step = (int) Math.ceil((double) (endIdx - startIdx) / maxVertices);
            }

            // 5. Render Triangle Strip via BufferBuilder
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder buffer = tesselator.getBuilder();
            buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            for (int i = startIdx; i < endIdx; i += step) {
                // Determine peak min and max for this slice (preserving transients even when stepped)
                float pMin;
                float pMax;
                if (step == 1) {
                    pMin = peaks[i].min();
                    pMax = peaks[i].max();
                } else {
                    pMin = 1.0f;
                    pMax = -1.0f;
                    int chunkEnd = Math.min(endIdx, i + step);
                    for (int k = i; k < chunkEnd; k++) {
                        if (peaks[k].min() < pMin) pMin = peaks[k].min();
                        if (peaks[k].max() > pMax) pMax = peaks[k].max();
                    }
                    if (pMin > pMax) {
                        pMin = 0.0f;
                        pMax = 0.0f;
                    }
                }

                // Sub-pixel floating-point position mapped directly to timeline channel bounds
                double peakTimeMs = i * msPerPeak;
                float px = (float) (channelX + ((peakTimeMs - scrollOffsetMs) / 1000.0) * pixelsPerSecond);

                float topY = centerY - (pMax * halfHeight);
                float bottomY = centerY - (pMin * halfHeight);

                // Ensure minimal 0.5px presence so quiet audio remains visible
                if (bottomY - topY < 0.5f) {
                    topY = centerY - 0.25f;
                    bottomY = centerY + 0.25f;
                }

                buffer.vertex(matrix, px, topY, 0.0f).color(r, g, b, a).endVertex();
                buffer.vertex(matrix, px, bottomY, 0.0f).color(r, g, b, a).endVertex();
            }

            // If stepped and last index wasn't rendered, render the final peak to close the strip cleanly
            if (step > 1 && (endIdx - 1) % step != 0) {
                int lastIdx = endIdx - 1;
                double peakTimeMs = lastIdx * msPerPeak;
                float px = (float) (channelX + ((peakTimeMs - scrollOffsetMs) / 1000.0) * pixelsPerSecond);
                float topY = centerY - (peaks[lastIdx].max() * halfHeight);
                float bottomY = centerY - (peaks[lastIdx].min() * halfHeight);

                buffer.vertex(matrix, px, topY, 0.0f).color(r, g, b, a).endVertex();
                buffer.vertex(matrix, px, bottomY, 0.0f).color(r, g, b, a).endVertex();
            }

            tesselator.end();
            RenderSystem.disableBlend();

        } finally {
            guiGraphics.disableScissor();
        }
    }
}
