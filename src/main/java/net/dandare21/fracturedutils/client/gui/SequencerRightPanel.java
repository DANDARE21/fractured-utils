package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.client.camera.CameraEasing;
import net.dandare21.fracturedutils.client.camera.CameraTransform;
import net.dandare21.fracturedutils.client.camera.SequencerViewportManager;
import net.dandare21.fracturedutils.client.camera.SplineInterpolationType;
import net.dandare21.fracturedutils.dialog.DialogFormatUtil;
import net.dandare21.fracturedutils.dialog.DialogLine;
import net.dandare21.fracturedutils.puppet.fsm.ActionParameter;
import net.dandare21.fracturedutils.puppet.fsm.ActionTimingPhase;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionType;
import net.dandare21.fracturedutils.puppet.registry.ModPuppetActions;
import net.dandare21.fracturedutils.sound.DialogSoundRegistry;
import net.dandare21.fracturedutils.sound.sequence.MusicSequence;
import net.dandare21.fracturedutils.sound.sequence.MusicSequenceChannel;
import net.dandare21.fracturedutils.sound.sequence.MusicSequenceEntry;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectInstance;
import net.dandare21.fracturedutils.screeneffect.client.ClientScreenEffectHandler;
import net.dandare21.fracturedutils.screeneffect.effects.CinematicBarsEffect;
import net.dandare21.fracturedutils.screeneffect.effects.HueShiftEffect;
import net.dandare21.fracturedutils.screeneffect.effects.ImpactFrameEffect;
import net.dandare21.fracturedutils.screeneffect.effects.InvertColorsEffect;
import net.dandare21.fracturedutils.screeneffect.effects.ScreenShakeEffect;
import net.dandare21.fracturedutils.screeneffect.effects.StrobeEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Mine-imator / Axiom style Right Inspector Panel for MusicSequenceScreen.
 * Seamlessly integrates ALL action parameters, channels, and sequence settings
 * directly into the sidebar with live real-time editing and smooth scrolling.
 */
public class SequencerRightPanel {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xF5060C12;
    private static final int CARD_BG = 0xDD091520;
    private static final int BORDER_CYAN = 0x8800E5FF;
    private static final int TEXT_MUTED = 0xFF7A9BB0;
    private static final int TEXT_LABEL = 0xFFAABBCC;
    private static final int RED_DELETE = 0xFFFF3355;
    private static final int GREEN_APPLY = 0xFF00FF88;
    private static final int GOLD_ACCENT = 0xFFFFD700;
    private static final int MAGENTA_ACC = 0xFFFF44AA;

    public enum InspectorTab {
        ACTION,
        CHANNEL,
        SEQUENCE
    }

    private static class PositionedWidget {
        final AbstractWidget widget;
        final int relY;

        PositionedWidget(AbstractWidget widget, int relY) {
            this.widget = widget;
            this.relY = relY;
        }
    }

    private static class CyberpunkLabel extends AbstractWidget {
        private final int color;
        private final Supplier<Component> textSupplier;

        public CyberpunkLabel(int x, int y, int width, int height, Component message, int color) {
            super(x, y, width, height, message);
            this.color = color;
            this.textSupplier = null;
        }

        public CyberpunkLabel(int x, int y, int width, int height, Supplier<Component> textSupplier, int color) {
            super(x, y, width, height, Component.empty());
            this.color = color;
            this.textSupplier = textSupplier;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Component msg = (textSupplier != null) ? textSupplier.get() : getMessage();
            graphics.drawString(Minecraft.getInstance().font, msg, getX(), getY() + (getHeight() - 8) / 2, color, false);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {}
    }

    private static class CyberpunkTimingBar extends AbstractWidget {
        private final MusicSequenceEntry entry;
        private final PuppetActionType<?> actType;
        private final Font font;
        private final List<EditBox> phaseBoxes;
        private final SequencerRightPanel panel;

        private int draggingDividerIndex = -1;
        private int dragStartPhaseMs = 0;
        private int dragStartNextPhaseMs = 0;
        private double dragStartX = 0;
        private int dragStartTotalMs = 0;

        public CyberpunkTimingBar(int x, int y, int width, int height, MusicSequenceEntry entry, PuppetActionType<?> actType, Font font, List<EditBox> phaseBoxes, SequencerRightPanel panel) {
            super(x, y, width, height, Component.empty());
            this.entry = entry;
            this.actType = actType;
            this.font = font;
            this.phaseBoxes = phaseBoxes;
            this.panel = panel;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int bx = getX();
            int by = getY();
            int bw = getWidth();
            int bh = getHeight();

            List<ActionTimingPhase> phases = actType.getTimingPhases();
            int totalMs = 0;
            for (int i = 0; i < phases.size(); i++) {
                ActionTimingPhase phase = phases.get(i);
                totalMs += panel.getPhaseMs(entry, phase, i);
            }
            if (totalMs <= 0 && phases.isEmpty()) {
                totalMs = entry.getTotalDurationMs();
            }

            // Outline & Background
            guiGraphics.fill(bx, by, bx + bw, by + bh, 0xEE060C12);
            guiGraphics.fill(bx, by, bx + bw, by + 1, 0xFF172D3D);
            guiGraphics.fill(bx, by + bh - 1, bx + bw, by + bh, 0xFF172D3D);
            guiGraphics.fill(bx, by, bx + 1, by + bh, 0xFF172D3D);
            guiGraphics.fill(bx + bw - 1, by, bx + bw, by + bh, 0xFF172D3D);

            if (totalMs <= 0) {
                guiGraphics.drawCenteredString(font, "Instant Action (0ms)", bx + (bw / 2), by + 5, 0xFF8899AA);
                return;
            }

            int usableW = bw - 4;
            int curX = bx + 2;
            int execX = curX;

            for (int i = 0; i < phases.size(); i++) {
                ActionTimingPhase phase = phases.get(i);
                int pMs = panel.getPhaseMs(entry, phase, i);
                int pW = (int) Math.round(((double) pMs / totalMs) * usableW);
                if (i == phases.size() - 1) {
                    pW = Math.max(0, (bx + bw - 2) - curX);
                }
                int clr = phase.color();
                String pLabel = phase.label().toUpperCase(Locale.ROOT);
                if (phase.isExecutionPoint()) {
                    execX = curX;
                }

                if (pW > 0) {
                    boolean isHover = mouseX >= curX && mouseX < curX + pW && mouseY >= by + 2 && mouseY <= by + bh - 2;
                    boolean isBoxFocused = (i < phaseBoxes.size() && phaseBoxes.get(i).isFocused());

                    guiGraphics.fill(curX, by + 2, curX + pW, by + bh - 2, clr);

                    if (isHover || isBoxFocused) {
                        guiGraphics.fill(curX, by + 2, curX + pW, by + 4, 0xFFFFFFFF);
                        guiGraphics.fill(curX, by + bh - 4, curX + pW, by + bh - 2, 0xFFFFFFFF);
                    }

                    int textClr = isLight(clr) ? 0xFF000000 : 0xFFFFFFFF;
                    String fullLabel = pLabel + " (" + pMs + "ms)";
                    if (pW >= font.width(fullLabel) + 6) {
                        guiGraphics.drawCenteredString(font, fullLabel, curX + (pW / 2), by + 5, textClr);
                    } else if (pW >= font.width(pMs + "ms") + 4) {
                        guiGraphics.drawCenteredString(font, pMs + "ms", curX + (pW / 2), by + 5, textClr);
                    }

                    curX += pW;
                }
            }

            // Divider Handles & Tooltip
            int divScanX = bx + 2;
            int hoveredDivider = -1;
            String hoveredTooltip = null;
            int hoveredHandleClr = CYAN_MAIN;

            for (int i = 0; i < phases.size(); i++) {
                ActionTimingPhase phase = phases.get(i);
                int pMs = panel.getPhaseMs(entry, phase, i);
                int pW = (int) Math.round(((double) pMs / totalMs) * usableW);
                if (i == phases.size() - 1) {
                    pW = Math.max(0, (bx + bw - 2) - divScanX);
                }
                int divX = divScanX + pW;

                boolean isDraggingThis = (draggingDividerIndex == i);
                boolean isHoveringThis = (Math.abs(mouseX - divX) <= 4 && mouseY >= by - 2 && mouseY <= by + bh + 2);

                if (i < phases.size() - 1) {
                    guiGraphics.fill(divX - 1, by + 2, divX, by + 5, 0x88FFFFFF);
                    guiGraphics.fill(divX - 1, by + bh - 5, divX, by + bh - 2, 0x88FFFFFF);
                }

                if (isHoveringThis || isDraggingThis) {
                    hoveredDivider = i;
                    int handleColor = isDraggingThis ? GREEN_APPLY : CYAN_MAIN;
                    hoveredHandleClr = handleColor;

                    guiGraphics.fill(divX - 1, by - 1, divX + 1, by + bh + 1, 0xFFFFFFFF);
                    guiGraphics.fill(divX - 3, by - 2, divX + 3, by, handleColor);
                    guiGraphics.fill(divX - 3, by + bh, divX + 3, by + bh + 2, handleColor);

                    int cy = by + (bh / 2);
                    guiGraphics.fill(divX - 3, cy - 3, divX + 3, cy + 3, 0xEE060C14);
                    guiGraphics.fill(divX - 2, cy - 2, divX + 2, cy + 2, handleColor);
                    guiGraphics.fill(divX - 1, cy - 1, divX + 1, cy + 1, 0xFFFFFFFF);

                    String modeTag = Screen.hasShiftDown() ? " [Split Dual-Edit]" : " [Ripple Edit]";
                    if (i < phases.size() - 1) {
                        ActionTimingPhase nextP = phases.get(i + 1);
                        hoveredTooltip = phase.label().toUpperCase(Locale.ROOT) + " (" + pMs + "ms) ↔ " + nextP.label().toUpperCase(Locale.ROOT) + modeTag;
                    } else {
                        hoveredTooltip = phase.label().toUpperCase(Locale.ROOT) + ": " + pMs + "ms (Resize End)" + modeTag;
                    }
                }

                divScanX = divX;
            }

            // Attack Execution Marker
            int centerY = by + (bh / 2);
            guiGraphics.fill(execX - 1, by - 1, execX + 1, by + bh + 1, 0xFFFFFFFF);
            guiGraphics.fill(execX - 3, centerY - 3, execX + 3, centerY + 3, 0xFFFF0055);
            guiGraphics.fill(execX - 1, centerY - 1, execX + 1, centerY + 1, 0xFFFFFFFF);

            // Floating Tooltip Badge
            if (hoveredTooltip != null && hoveredDivider >= 0) {
                int tipW = font.width(hoveredTooltip);
                int tipX = bx + (bw - tipW) / 2;
                int tipY = by - 13;
                guiGraphics.fill(tipX - 3, tipY - 1, tipX + tipW + 3, tipY + 9, 0xEE060C14);
                guiGraphics.fill(tipX - 3, tipY - 1, tipX + tipW + 3, tipY, hoveredHandleClr);
                guiGraphics.drawString(font, hoveredTooltip, tipX, tipY, 0xFFFFFFFF, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (mouseX < getX() || mouseX >= getX() + getWidth() || mouseY < getY() - 2 || mouseY >= getY() + getHeight() + 2) {
                return false;
            }
            List<ActionTimingPhase> phases = actType.getTimingPhases();
            int totalMs = 0;
            for (int i = 0; i < phases.size(); i++) {
                totalMs += panel.getPhaseMs(entry, phases.get(i), i);
            }
            int usableW = getWidth() - 4;

            // 1. Check divider click for dragging
            if (totalMs > 0 && usableW > 0) {
                int divScanX = getX() + 2;
                for (int i = 0; i < phases.size(); i++) {
                    ActionTimingPhase phase = phases.get(i);
                    int pMs = panel.getPhaseMs(entry, phase, i);
                    int pW = (int) Math.round(((double) pMs / totalMs) * usableW);
                    if (i == phases.size() - 1) {
                        pW = Math.max(0, (getX() + getWidth() - 2) - divScanX);
                    }
                    int divX = divScanX + pW;
                    if (Math.abs(mouseX - divX) <= 5) {
                        draggingDividerIndex = i;
                        dragStartPhaseMs = pMs;
                        dragStartNextPhaseMs = (i < phases.size() - 1) ? panel.getPhaseMs(entry, phases.get(i + 1), i + 1) : 0;
                        dragStartX = mouseX;
                        dragStartTotalMs = totalMs;
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
                        return true;
                    }
                    divScanX = divX;
                }
            }

            // 2. Click on a phase to focus its text input box
            if (!phaseBoxes.isEmpty()) {
                if (totalMs <= 0) {
                    EditBox eb = phaseBoxes.get(0);
                    panel.focusEditBox(eb);
                    eb.setHighlightPos(0);
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
                    return true;
                }
                int curX = getX() + 2;
                for (int i = 0; i < phases.size(); i++) {
                    ActionTimingPhase phase = phases.get(i);
                    int pMs = panel.getPhaseMs(entry, phase, i);
                    int pW = (int) Math.round(((double) pMs / totalMs) * usableW);
                    if (i == phases.size() - 1) {
                        pW = Math.max(0, (getX() + getWidth() - 2) - curX);
                    }
                    if (mouseX >= curX && mouseX < curX + pW) {
                        if (i < phaseBoxes.size()) {
                            EditBox eb = phaseBoxes.get(i);
                            panel.focusEditBox(eb);
                            eb.setHighlightPos(0);
                            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
                            return true;
                        }
                    }
                    curX += pW;
                }
            }
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (draggingDividerIndex < 0) return false;
            List<ActionTimingPhase> phases = actType.getTimingPhases();
            if (draggingDividerIndex >= phases.size()) return false;

            int usableW = getWidth() - 4;
            if (usableW <= 0 || dragStartTotalMs <= 0) return false;

            double msPerPixel = (double) dragStartTotalMs / usableW;
            int deltaMs = (int) Math.round((mouseX - dragStartX) * msPerPixel);
            if (!Screen.hasAltDown()) {
                deltaMs = Math.round(deltaMs / 25.0f) * 25;
            }

            ActionTimingPhase currentPhase = phases.get(draggingDividerIndex);
            if (Screen.hasShiftDown() && draggingDividerIndex < phases.size() - 1) {
                // Dual split edit: keeps total fixed between current and next phase
                int combinedMs = dragStartPhaseMs + dragStartNextPhaseMs;
                int newPMs = Math.max(0, Math.min(combinedMs, dragStartPhaseMs + deltaMs));
                int newNextMs = combinedMs - newPMs;
                panel.setPhaseMs(entry, currentPhase, draggingDividerIndex, newPMs);
                panel.setPhaseMs(entry, phases.get(draggingDividerIndex + 1), draggingDividerIndex + 1, newNextMs);
                if (draggingDividerIndex < phaseBoxes.size()) {
                    phaseBoxes.get(draggingDividerIndex).setValue(String.valueOf(newPMs));
                }
                if (draggingDividerIndex + 1 < phaseBoxes.size()) {
                    phaseBoxes.get(draggingDividerIndex + 1).setValue(String.valueOf(newNextMs));
                }
            } else {
                // Ripple edit: adjusts current phase duration
                int newPMs = Math.max(0, dragStartPhaseMs + deltaMs);
                panel.setPhaseMs(entry, currentPhase, draggingDividerIndex, newPMs);
                if (draggingDividerIndex < phaseBoxes.size()) {
                    phaseBoxes.get(draggingDividerIndex).setValue(String.valueOf(newPMs));
                }
            }

            panel.updatePuppetCommand(entry);
            panel.parent.saveCurrentSequenceToWorkingMap();
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (draggingDividerIndex >= 0) {
                draggingDividerIndex = -1;
                return true;
            }
            return false;
        }

        private static boolean isLight(int color) {
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            double luma = 0.299 * r + 0.587 * g + 0.114 * b;
            return luma > 150;
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {}
    }

    public static void drawLine(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            guiGraphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private static boolean isModeActive(MusicSequenceEntry entry, String mode) {
        if (entry == null) return false;
        String cur = entry.getCameraMode();
        if (cur == null || cur.isBlank()) cur = "KEYFRAME";
        if ("KEYFRAME".equalsIgnoreCase(mode)) {
            return "KEYFRAME".equalsIgnoreCase(cur) || "STATIC".equalsIgnoreCase(cur);
        }
        return mode.equalsIgnoreCase(cur);
    }

    private static String getEasingDescription(CameraEasing easing) {
        if (easing == null) return "Smooth interpolation curve";
        return switch (easing) {
            case EASE_IN_OUT_CUBIC, EASE_IN_OUT_QUAD -> "Cinematic S-curve: gentle acceleration, brisk mid-flight, gentle landing";
            case EASE_OUT_CUBIC, EASE_OUT_QUAD -> "Soft deceleration: enters quickly and gently settles into keyframe position";
            case EASE_IN_CUBIC, EASE_IN_QUAD -> "Accelerating ramp: starts slowly and speeds up steadily towards keyframe";
            case LINEAR -> "Uniform velocity: steady mechanical straight-speed tracking";
            case EASE_OUT_BOUNCE, EASE_IN_OUT_BOUNCE -> "Elastic bounce: rebounds on arrival, perfect for impactful stops";
            case EASE_OUT_ELASTIC, EASE_IN_OUT_ELASTIC -> "Spring inertia: oscillates past target before snapping into place";
            case EASE_IN_OUT_EXPO, EASE_OUT_EXPO -> "Exponential curve: dramatic high-speed whip transition";
            case INSTANT -> "Instant jump cut: immediate hard snap without camera motion";
            default -> "Smooth organic velocity curve";
        };
    }

    private static class CameraInterpolationWidget extends AbstractWidget {
        private final MusicSequenceEntry entry;
        private final MusicSequenceScreen parentScreen;
        private final SequencerRightPanel panel;
        private final Font font;

        private record SplineOption(SplineInterpolationType type, String title, String subtitle, int accentColor) {}
        private static final List<SplineOption> SPLINE_OPTIONS = List.of(
                new SplineOption(SplineInterpolationType.CATMULL_ROM, "Catmull-Rom", "Smooth Spline", 0xFF00E5FF),
                new SplineOption(SplineInterpolationType.BEZIER, "Cubic Bézier", "Tangent Arms", 0xFFFFD700),
                new SplineOption(SplineInterpolationType.LINEAR, "Linear Lerp", "Direct Path", 0xFFFF9900),
                new SplineOption(SplineInterpolationType.STEP, "Step Cut", "Instant Snap", 0xFFFF3366)
        );

        private record EasingOption(CameraEasing easing, String label, int accentColor) {}
        private static final List<EasingOption> EASING_OPTIONS = List.of(
                new EasingOption(CameraEasing.EASE_IN_OUT_CUBIC, "Smooth S", 0xFF00E5FF),
                new EasingOption(CameraEasing.EASE_OUT_CUBIC, "Soft Stop", 0xFF00FF88),
                new EasingOption(CameraEasing.EASE_IN_QUAD, "Fast Accel", 0xFFFFCC00),
                new EasingOption(CameraEasing.LINEAR, "Linear", 0xFFAABBCC),
                new EasingOption(CameraEasing.EASE_OUT_BOUNCE, "Bounce", 0xFFFF44AA),
                new EasingOption(CameraEasing.INSTANT, "Step / Cut", 0xFFFF3355)
        );

        public CameraInterpolationWidget(int x, int y, int width, int height, MusicSequenceEntry entry, MusicSequenceScreen parentScreen, SequencerRightPanel panel, Font font) {
            super(x, y, width, height, Component.empty());
            this.entry = entry;
            this.parentScreen = parentScreen;
            this.panel = panel;
            this.font = font;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int bx = getX();
            int by = getY();
            int bw = getWidth();
            int bh = getHeight();

            // Background Card
            guiGraphics.fill(bx, by, bx + bw, by + bh, 0xDD091520);
            guiGraphics.fill(bx, by, bx + bw, by + 1, 0x6600E5FF);
            guiGraphics.fill(bx, by + bh - 1, bx + bw, by + bh, 0x6600E5FF);
            guiGraphics.fill(bx, by, bx + 1, by + bh, 0x6600E5FF);
            guiGraphics.fill(bx + bw - 1, by, bx + bw, by + bh, 0x6600E5FF);

            // Title Banner
            guiGraphics.fill(bx, by, bx + bw, by + 18, 0xEE0D1E2C);
            guiGraphics.fill(bx, by + 17, bx + bw, by + 18, 0x4400E5FF);
            guiGraphics.drawString(font, "⚡ INTERPOLATION & TRAJECTORY", bx + 6, by + 5, 0xFF00E5FF, false);

            // --- 1. Spline Trajectory Grid (2x2) ---
            int sGridY = by + 22;
            guiGraphics.drawString(font, "✦ PATH SPLINE (3D GEOMETRY)", bx + 6, sGridY, 0xFFAABBCC, false);
            sGridY += 12;

            int sColW = (bw - 16) / 2;
            int sCardH = 30;
            SplineInterpolationType curSpline = SplineInterpolationType.fromString(entry.getSplineMode());

            for (int i = 0; i < SPLINE_OPTIONS.size(); i++) {
                SplineOption opt = SPLINE_OPTIONS.get(i);
                int r = i / 2;
                int c = i % 2;
                int cx = bx + 6 + c * (sColW + 4);
                int cy = sGridY + r * (sCardH + 4);

                boolean active = (curSpline == opt.type());
                boolean hov = mouseX >= cx && mouseX < cx + sColW && mouseY >= cy && mouseY < cy + sCardH;

                int bgCol = active ? 0xEE0B2538 : (hov ? 0xEE0D1E2C : 0xEE081018);
                int borderCol = active ? 0xFFFFFFFF : (hov ? opt.accentColor() : 0x4400E5FF);

                guiGraphics.fill(cx, cy, cx + sColW, cy + sCardH, bgCol);
                guiGraphics.fill(cx, cy, cx + sColW, cy + 1, borderCol);
                guiGraphics.fill(cx, cy + sCardH - 1, cx + sColW, cy + sCardH, borderCol);
                guiGraphics.fill(cx, cy, cx + 1, cy + sCardH, borderCol);
                guiGraphics.fill(cx + sColW - 1, cy, cx + sColW, cy + sCardH, borderCol);

                if (active) {
                    guiGraphics.fill(cx + 1, cy + 1, cx + sColW - 1, cy + 2, opt.accentColor());
                }

                // Procedural Curve Thumbnail (Left Box)
                int ix = cx + 3;
                int iy = cy + 3;
                int iw = 30;
                int ih = sCardH - 6;
                guiGraphics.fill(ix, iy, ix + iw, iy + ih, 0xBB04080D);

                int strokeClr = active ? 0xFFFFFFFF : opt.accentColor();
                drawSplineIcon(guiGraphics, opt.type(), ix, iy, iw, ih, strokeClr);

                // Text Labels (Right)
                guiGraphics.drawString(font, opt.title(), cx + 36, cy + 4, active ? 0xFFFFFFFF : 0xFFCCDDFF, false);
                guiGraphics.drawString(font, opt.subtitle(), cx + 36, cy + 16, active ? opt.accentColor() : 0xFF778899, false);

                if (active) {
                    guiGraphics.drawString(font, "✔", cx + sColW - 10, cy + 4, opt.accentColor(), false);
                }
            }

            // Spline Description Info Banner
            int sDescY = sGridY + 2 * (sCardH + 4);
            guiGraphics.fill(bx + 6, sDescY, bx + bw - 6, sDescY + 14, 0xEE050D14);
            guiGraphics.fill(bx + 6, sDescY, bx + bw - 6, sDescY + 1, 0x3300E5FF);
            String splineDesc = curSpline != null ? curSpline.getDescription() : "";
            if (font.width(splineDesc) > bw - 20) {
                splineDesc = font.plainSubstrByWidth(splineDesc, bw - 26) + "...";
            }
            guiGraphics.drawString(font, "ℹ " + splineDesc, bx + 10, sDescY + 3, 0xFF88AABB, false);

            // --- 2. Live Animated Velocity Graph Box ---
            int graphStartY = sDescY + 18;
            guiGraphics.drawString(font, "✦ VELOCITY & ACCELERATION", bx + 6, graphStartY, 0xFFAABBCC, false);

            CameraEasing curEasing = CameraEasing.fromString(entry.getCameraEasing());
            String curEaseName = curEasing != null ? curEasing.getDisplayName() : "Linear";
            int nameW = font.width(curEaseName);
            guiGraphics.drawString(font, curEaseName, bx + bw - 6 - nameW, graphStartY, 0xFF00FF88, false);

            int gBoxY = graphStartY + 12;
            int gBoxH = 46;
            int gx0 = bx + 8;
            int gx1 = bx + bw - 8;
            int gy0 = gBoxY + 3;
            int gy1 = gBoxY + gBoxH - 9;
            int pw = gx1 - gx0;
            int ph = gy1 - gy0;

            // Dark Graph Backdrop
            guiGraphics.fill(bx + 6, gBoxY, bx + bw - 6, gBoxY + gBoxH, 0xEE03070B);
            guiGraphics.fill(bx + 6, gBoxY, bx + bw - 6, gBoxY + 1, 0x5500E5FF);
            guiGraphics.fill(bx + 6, gBoxY + gBoxH - 1, bx + bw - 6, gBoxY + gBoxH, 0x5500E5FF);
            guiGraphics.fill(bx + 6, gBoxY, bx + 7, gBoxY + gBoxH, 0x5500E5FF);
            guiGraphics.fill(bx + bw - 7, gBoxY, bx + bw - 6, gBoxY + gBoxH, 0x5500E5FF);

            // Subtle Grid Lines
            guiGraphics.fill(gx0, gy0 + ph / 4, gx1, gy0 + ph / 4 + 1, 0x1800E5FF);
            guiGraphics.fill(gx0, gy0 + ph / 2, gx1, gy0 + ph / 2 + 1, 0x2200E5FF);
            guiGraphics.fill(gx0, gy0 + (ph * 3) / 4, gx1, gy0 + (ph * 3) / 4 + 1, 0x1800E5FF);

            // Plot Mathematical Easing Curve
            if (curEasing != null && pw > 10 && ph > 5) {
                int prevPX = gx0;
                int prevPY = gy1 - (int) Math.round(curEasing.ease(0.0) * ph);
                int samples = Math.min(pw, 64);
                for (int s = 1; s <= samples; s++) {
                    double t = (double) s / samples;
                    int curPX = gx0 + (int) Math.round(t * pw);
                    double ev = curEasing.ease(t);
                    int curPY = gy1 - (int) Math.round(ev * ph);
                    drawLine(guiGraphics, prevPX, prevPY, curPX, curPY, 0xFF00E5FF);
                    prevPX = curPX;
                    prevPY = curPY;
                }

                // Live Pulsing Momentum Dot & Fading Playhead
                long now = System.currentTimeMillis();
                double tAnim = (now % 1600L) / 1600.0;
                double vAnim = curEasing.ease(tAnim);
                int dotX = gx0 + (int) Math.round(tAnim * pw);
                int dotY = gy1 - (int) Math.round(vAnim * ph);

                // Playhead line
                guiGraphics.fill(dotX, gy0, dotX + 1, gy1, 0x33FFFFFF);

                // Glowing momentum orb
                guiGraphics.fill(dotX - 3, dotY - 3, dotX + 4, dotY + 4, 0x4400FF88);
                guiGraphics.fill(dotX - 2, dotY - 2, dotX + 3, dotY + 3, 0xAA00FF88);
                guiGraphics.fill(dotX - 1, dotY - 1, dotX + 2, dotY + 2, 0xFFFFFFFF);
            }

            // Bottom axis labels
            guiGraphics.drawString(font, "0.0s (Keyframe)", gx0, gBoxY + gBoxH - 8, 0xFF445566, false);
            guiGraphics.drawString(font, "1.0s (Target)", gx1 - font.width("1.0s (Target)"), gBoxY + gBoxH - 8, 0xFF445566, false);

            // --- 3. Easing Curve Presets (3x2 Grid) ---
            int easeStartY = gBoxY + gBoxH + 6;
            guiGraphics.drawString(font, "✦ EASING PRESETS (ACCELERATION)", bx + 6, easeStartY, 0xFFAABBCC, false);
            int eGridY = easeStartY + 12;

            int eColW = (bw - 20) / 3;
            int eCardH = 24;

            for (int j = 0; j < EASING_OPTIONS.size(); j++) {
                EasingOption opt = EASING_OPTIONS.get(j);
                int er = j / 3;
                int ec = j % 3;
                int ex = bx + 6 + ec * (eColW + 4);
                int ey = eGridY + er * (eCardH + 4);

                boolean active = (curEasing == opt.easing());
                boolean hov = mouseX >= ex && mouseX < ex + eColW && mouseY >= ey && mouseY < ey + eCardH;

                int bgCol = active ? 0xEE0B2538 : (hov ? 0xEE0D1E2C : 0xEE081018);
                int borderCol = active ? 0xFFFFFFFF : (hov ? opt.accentColor() : 0x4400E5FF);

                guiGraphics.fill(ex, ey, ex + eColW, ey + eCardH, bgCol);
                guiGraphics.fill(ex, ey, ex + eColW, ey + 1, borderCol);
                guiGraphics.fill(ex, ey + eCardH - 1, ex + eColW, ey + eCardH, borderCol);
                guiGraphics.fill(ex, ey, ex + 1, ey + eCardH, borderCol);
                guiGraphics.fill(ex + eColW - 1, ey, ex + eColW, ey + eCardH, borderCol);

                if (active) {
                    guiGraphics.fill(ex + 1, ey + 1, ex + eColW - 1, ey + 2, opt.accentColor());
                }

                // Mini Curve Thumbnail
                int tx = ex + 2;
                int ty = ey + 2;
                int tw = 18;
                int th = eCardH - 4;
                guiGraphics.fill(tx, ty, tx + tw, ty + th, 0xBB04080D);

                int ePrevX = tx + 1;
                int ePrevY = ty + th - 2 - (int) Math.round(opt.easing().ease(0.0) * (th - 4));
                for (int step = 1; step <= 8; step++) {
                    double t = step / 8.0;
                    int curX = tx + 1 + (int) Math.round(t * (tw - 2));
                    double ev = Mth.clamp(opt.easing().ease(t), -0.2, 1.2);
                    int curY = ty + th - 2 - (int) Math.round(ev * (th - 4));
                    drawLine(guiGraphics, ePrevX, ePrevY, curX, curY, active ? 0xFFFFFFFF : opt.accentColor());
                    ePrevX = curX;
                    ePrevY = curY;
                }

                // Text
                guiGraphics.drawString(font, opt.label(), ex + 22, ey + 7, active ? 0xFFFFFFFF : 0xFFCCDDFF, false);
            }

            // Easing Description Banner
            int eDescY = eGridY + 2 * (eCardH + 4);
            guiGraphics.fill(bx + 6, eDescY, bx + bw - 6, eDescY + 14, 0xEE050D14);
            guiGraphics.fill(bx + 6, eDescY, bx + bw - 6, eDescY + 1, 0x3300E5FF);
            String easeDesc = getEasingDescription(curEasing);
            if (font.width(easeDesc) > bw - 20) {
                easeDesc = font.plainSubstrByWidth(easeDesc, bw - 26) + "...";
            }
            guiGraphics.drawString(font, "ℹ " + easeDesc, bx + 10, eDescY + 3, 0xFF88AABB, false);
        }

        private static void drawSplineIcon(GuiGraphics guiGraphics, SplineInterpolationType type, int ix, int iy, int iw, int ih, int color) {
            switch (type) {
                case CATMULL_ROM -> {
                    int prevX = ix + 2;
                    int prevY = iy + ih - 4;
                    for (int step = 1; step <= 16; step++) {
                        float t = step / 16.0f;
                        int curX = ix + 2 + (int) (t * (iw - 4));
                        float normalizedY = (float) (0.5 - 0.5 * Math.cos(t * Math.PI));
                        int curY = iy + ih - 4 - (int) (normalizedY * (ih - 8));
                        drawLine(guiGraphics, prevX, prevY, curX, curY, color);
                        prevX = curX;
                        prevY = curY;
                    }
                    guiGraphics.fill(ix + 1, iy + ih - 5, ix + 4, iy + ih - 2, 0xFFFFFFFF);
                    guiGraphics.fill(ix + iw / 2 - 1, iy + ih / 2 - 1, ix + iw / 2 + 2, iy + ih / 2 + 2, 0xFFFFFFFF);
                    guiGraphics.fill(ix + iw - 4, iy + 2, ix + iw - 1, iy + 5, 0xFFFFFFFF);
                }
                case BEZIER -> {
                    int p0x = ix + 2, p0y = iy + ih - 4;
                    int p1x = ix + 6, p1y = iy + 4;
                    int p2x = ix + iw - 6, p2y = iy + ih - 4;
                    int p3x = ix + iw - 2, p3y = iy + 4;

                    drawLine(guiGraphics, p0x, p0y, p1x, p1y, 0x55FFFFFF);
                    drawLine(guiGraphics, p3x, p3y, p2x, p2y, 0x55FFFFFF);
                    guiGraphics.fill(p1x - 1, p1y - 1, p1x + 2, p1y + 2, 0xFFFFD700);
                    guiGraphics.fill(p2x - 1, p2y - 1, p2x + 2, p2y + 2, 0xFFFFD700);

                    int prevX = p0x, prevY = p0y;
                    for (int step = 1; step <= 16; step++) {
                        float t = step / 16.0f;
                        float omt = 1.0f - t;
                        float omt2 = omt * omt;
                        float t2 = t * t;
                        float bx = omt2 * omt * p0x + 3 * omt2 * t * p1x + 3 * omt * t2 * p2x + t2 * t * p3x;
                        float by = omt2 * omt * p0y + 3 * omt2 * t * p1y + 3 * omt * t2 * p2y + t2 * t * p3y;
                        int curX = Math.round(bx);
                        int curY = Math.round(by);
                        drawLine(guiGraphics, prevX, prevY, curX, curY, color);
                        prevX = curX;
                        prevY = curY;
                    }
                    guiGraphics.fill(p0x - 1, p0y - 1, p0x + 2, p0y + 2, 0xFFFFFFFF);
                    guiGraphics.fill(p3x - 1, p3y - 1, p3x + 2, p3y + 2, 0xFFFFFFFF);
                }
                case LINEAR -> {
                    int p0x = ix + 2, p0y = iy + ih - 4;
                    int p1x = ix + iw / 2, p1y = iy + 4;
                    int p2x = ix + iw - 2, p2y = iy + ih - 6;

                    drawLine(guiGraphics, p0x, p0y, p1x, p1y, color);
                    drawLine(guiGraphics, p1x, p1y, p2x, p2y, color);

                    guiGraphics.fill(p0x - 1, p0y - 1, p0x + 2, p0y + 2, 0xFFFFFFFF);
                    guiGraphics.fill(p1x - 1, p1y - 1, p1x + 2, p1y + 2, 0xFFFFFFFF);
                    guiGraphics.fill(p2x - 1, p2y - 1, p2x + 2, p2y + 2, 0xFFFFFFFF);
                }
                case STEP -> {
                    int midX = ix + iw / 2;
                    drawLine(guiGraphics, ix + 2, iy + ih - 4, midX, iy + ih - 4, color);
                    drawLine(guiGraphics, midX, iy + ih - 4, midX, iy + 4, color);
                    drawLine(guiGraphics, midX, iy + 4, ix + iw - 2, iy + 4, color);

                    guiGraphics.fill(ix + 1, iy + ih - 5, ix + 4, iy + ih - 2, 0xFFFFFFFF);
                    guiGraphics.fill(midX - 1, iy + 3, midX + 2, iy + 6, 0xFFFFFFFF);
                    guiGraphics.fill(ix + iw - 3, iy + 3, ix + iw, iy + 6, 0xFFFFFFFF);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int bx = getX();
            int by = getY();
            int bw = getWidth();
            int bh = getHeight();

            if (mouseX < bx || mouseX >= bx + bw || mouseY < by || mouseY >= by + bh) {
                return false;
            }

            // Spline Clicks
            int sGridY = by + 22 + 12;
            int sColW = (bw - 16) / 2;
            int sCardH = 30;

            for (int i = 0; i < SPLINE_OPTIONS.size(); i++) {
                SplineOption opt = SPLINE_OPTIONS.get(i);
                int r = i / 2;
                int c = i % 2;
                int cx = bx + 6 + c * (sColW + 4);
                int cy = sGridY + r * (sCardH + 4);

                if (mouseX >= cx && mouseX < cx + sColW && mouseY >= cy && mouseY < cy + sCardH) {
                    entry.setSplineMode(opt.type().name());
                    parentScreen.saveCurrentSequenceToWorkingMap();
                    parentScreen.updatePreviewCamera();
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
                    panel.rebuildWidgets();
                    return true;
                }
            }

            // Easing Clicks
            int sDescY = sGridY + 2 * (sCardH + 4);
            int graphStartY = sDescY + 18;
            int gBoxY = graphStartY + 12;
            int gBoxH = 46;
            int easeStartY = gBoxY + gBoxH + 6;
            int eGridY = easeStartY + 12;
            int eColW = (bw - 20) / 3;
            int eCardH = 24;

            for (int j = 0; j < EASING_OPTIONS.size(); j++) {
                EasingOption opt = EASING_OPTIONS.get(j);
                int er = j / 3;
                int ec = j % 3;
                int ex = bx + 6 + ec * (eColW + 4);
                int ey = eGridY + er * (eCardH + 4);

                if (mouseX >= ex && mouseX < ex + eColW && mouseY >= ey && mouseY < ey + eCardH) {
                    entry.setCameraEasing(opt.easing().name());
                    parentScreen.saveCurrentSequenceToWorkingMap();
                    parentScreen.updatePreviewCamera();
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f));
                    panel.rebuildWidgets();
                    return true;
                }
            }

            return true;
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {}
    }

    private static class CommandBlockChassisWidget extends AbstractWidget {
        private final String blockType;
        private final int accentColor;

        public CommandBlockChassisWidget(int x, int y, int width, int height, String blockType, int accentColor) {
            super(x, y, width, height, Component.empty());
            this.blockType = blockType;
            this.accentColor = accentColor;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int bx = getX();
            int by = getY();
            int bw = getWidth();
            int bh = getHeight();

            // Outer Chassis Slate
            guiGraphics.fill(bx, by, bx + bw, by + bh, 0xEE16100D);
            guiGraphics.fill(bx, by, bx + bw, by + 1, accentColor);
            guiGraphics.fill(bx, by + bh - 1, bx + bw, by + bh, accentColor);
            guiGraphics.fill(bx, by, bx + 1, by + bh, accentColor);
            guiGraphics.fill(bx + bw - 1, by, bx + bw, by + bh, accentColor);

            // Command Block Corner Rivets
            int rivetColor = 0xFF8A5A3B;
            guiGraphics.fill(bx + 2, by + 2, bx + 4, by + 4, rivetColor);
            guiGraphics.fill(bx + bw - 4, by + 2, bx + bw - 2, by + 4, rivetColor);
            guiGraphics.fill(bx + 2, by + bh - 4, bx + 4, by + bh - 2, rivetColor);
            guiGraphics.fill(bx + bw - 4, by + bh - 4, bx + bw - 2, by + bh - 2, rivetColor);

            // Procedural Command Block Icon on left
            int cx = bx + 8;
            int cy = by + 6;
            int size = 16;
            guiGraphics.fill(cx, cy, cx + size, cy + size, 0xEE2A1E17);
            guiGraphics.fill(cx, cy, cx + size, cy + 1, accentColor);
            guiGraphics.fill(cx, cy + size - 1, cx + size, cy + size, accentColor);
            guiGraphics.fill(cx, cy, cx + 1, cy + size, accentColor);
            guiGraphics.fill(cx + size - 1, cy, cx + size, cy + size, accentColor);

            // Inner Octagonal Core & Pulsing LED
            int coreX = cx + 3;
            int coreY = cy + 3;
            int coreSize = 10;
            guiGraphics.fill(coreX, coreY, coreX + coreSize, coreY + coreSize, 0xEE120B07);

            // LED Center
            long now = System.currentTimeMillis();
            boolean ledPulse = (now % 1000L) < 500L;
            int ledColor = ledPulse ? accentColor : 0xFFFFFFFF;
            guiGraphics.fill(cx + 6, cy + 6, cx + 10, cy + 10, ledColor);

            // Command Block Title & Subtitle
            Font font = Minecraft.getInstance().font;
            guiGraphics.drawString(font, "⚙ " + blockType + " COMMAND BLOCK", cx + size + 8, by + 5, accentColor, false);
            String sub = switch (blockType) {
                case "CHAIN" -> "Chain Mode: Sequential Timeline Trigger";
                case "REPEAT" -> "Repeat Mode: Continuous Keyframe Loop";
                default -> "Impulse Mode: Instant Timestamp Trigger";
            };
            guiGraphics.drawString(font, sub, cx + size + 8, by + 16, 0xFF887766, false);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {}
    }

    private static class CommandOutputConsoleWidget extends AbstractWidget {
        private final Supplier<String> statusSupplier;
        private final int accentColor;

        public CommandOutputConsoleWidget(int x, int y, int width, int height, Supplier<String> statusSupplier, int accentColor) {
            super(x, y, width, height, Component.empty());
            this.statusSupplier = statusSupplier;
            this.accentColor = accentColor;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int bx = getX();
            int by = getY();
            int bw = getWidth();
            int bh = getHeight();

            // Terminal Screen
            guiGraphics.fill(bx, by, bx + bw, by + bh, 0xEE090705);
            guiGraphics.fill(bx, by, bx + bw, by + 1, 0x44443322);
            guiGraphics.fill(bx, by + bh - 1, bx + bw, by + bh, 0x44443322);
            guiGraphics.fill(bx, by, bx + 1, by + bh, 0x44443322);
            guiGraphics.fill(bx + bw - 1, by, bx + bw, by + bh, 0x44443322);

            Font font = Minecraft.getInstance().font;
            String status = statusSupplier.get();
            if (status == null || status.isBlank()) {
                status = "✦ Previous Output: Ready for sequence trigger";
            }
            if (font.width(status) > bw - 12) {
                status = font.plainSubstrByWidth(status, bw - 16) + "...";
            }
            guiGraphics.drawString(font, status, bx + 6, by + (bh - 8) / 2, 0xFF00FF88, false);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {}
    }

    private final MusicSequenceScreen parent;
    private int x;
    private int y;
    private int width;
    private int height;

    private InspectorTab currentTab = InspectorTab.ACTION;
    private double scrollY = 0.0;
    private int contentHeight = 400;

    // Track active selection
    private int boundEntryIndex = -2;
    private MusicSequenceEntry boundEntry = null;
    private boolean isUpdatingFields = false;

    // Widget list for the active tab (scroll-managed)
    private final List<PositionedWidget> activeWidgets = new ArrayList<>();

    // Keep references to edit boxes for focus checks and specialized triggers
    private final List<EditBox> activeEditBoxes = new ArrayList<>();

    // Command Block Autocomplete & Console State
    private CommandSuggestions activeCommandSuggestions = null;
    private EditBox activeCommandBox = null;
    private String lastCommandExecutionStatus = null;

    public SequencerRightPanel(MusicSequenceScreen parent, int x, int y, int width, int height) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        rebuildWidgets();
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void setTab(InspectorTab tab) {
        this.currentTab = tab;
        this.scrollY = 0.0;
        rebuildWidgets();
    }

    public InspectorTab getTab() {
        return currentTab;
    }

    public void updateSelection(int entryIndex, MusicSequenceEntry entry) {
        if (this.boundEntryIndex != entryIndex || this.boundEntry != entry) {
            this.boundEntryIndex = entryIndex;
            this.boundEntry = entry;
            this.scrollY = 0.0;
            rebuildWidgets();
        }
    }

    private <T extends AbstractWidget> T addWidget(T widget, int relY) {
        int contentY = y + 28;
        widget.setY(contentY + relY - (int) scrollY);
        activeWidgets.add(new PositionedWidget(widget, relY));
        if (widget instanceof EditBox eb) {
            activeEditBoxes.add(eb);
        }
        return widget;
    }

    private void applyScrollOffset() {
        int contentY = y + 28;
        for (PositionedWidget pw : activeWidgets) {
            pw.widget.setY(contentY + pw.relY - (int) scrollY);
        }
        if (activeCommandSuggestions != null) {
            activeCommandSuggestions.updateCommandInfo();
        }
    }

    public boolean hasOpenDropdown() {
        for (PositionedWidget pw : activeWidgets) {
            if (pw.widget instanceof CyberpunkDropdown<?> dd && dd.isOpen()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasCommandSuggestions() {
        return activeCommandSuggestions != null;
    }

    public void closeAllDropdowns() {
        for (PositionedWidget pw : activeWidgets) {
            if (pw.widget instanceof CyberpunkDropdown<?> dd && dd.isOpen()) {
                dd.setOpen(false);
            }
        }
    }

    public void closeAllDropdownsExcept(CyberpunkDropdown<?> except) {
        for (PositionedWidget pw : activeWidgets) {
            if (pw.widget instanceof CyberpunkDropdown<?> dd && dd != except && dd.isOpen()) {
                dd.setOpen(false);
            }
        }
    }

    private <T> CyberpunkDropdown<T> createDropdown(int relY, int innerW, int pad, Component message) {
        CyberpunkDropdown<T> dd = new CyberpunkDropdown<>(x + pad, y + 28 + relY, innerW, 20, message);
        dd.setOnOpenListener(() -> closeAllDropdownsExcept(dd));
        return addWidget(dd, relY);
    }

    public void rebuildWidgets() {
        isUpdatingFields = true;
        activeWidgets.clear();
        activeEditBoxes.clear();
        activeCommandSuggestions = null;
        activeCommandBox = null;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int pad = 8;
        int innerW = width - (pad * 2);

        if (currentTab == InspectorTab.ACTION) {
            rebuildActionWidgets(font, pad, innerW);
        } else if (currentTab == InspectorTab.CHANNEL) {
            rebuildChannelWidgets(font, pad, innerW);
        } else if (currentTab == InspectorTab.SEQUENCE) {
            rebuildSequenceWidgets(font, pad, innerW);
        }

        int maxScroll = Math.max(0, contentHeight - (height - 36));
        scrollY = Math.max(0.0, Math.min(maxScroll, scrollY));
        applyScrollOffset();

        isUpdatingFields = false;
    }

    // =========================================================================
    // 1. ACTION TAB WIDGETS
    // =========================================================================

    private void rebuildActionWidgets(Font font, int pad, int innerW) {
        MusicSequenceEntry entry = parent.getSelectedEntry();
        if (entry == null) {
            contentHeight = 200;
            return;
        }

        int curY = 8;

        // --- Channel Assignment Dropdown ---
        List<MusicSequenceChannel> allChannels = parent.getCurrentSequence().getChannels();
        List<CyberpunkDropdown.DropdownEntry<MusicSequenceChannel>> chEntries = new ArrayList<>();
        MusicSequenceChannel selectedChannel = null;
        for (MusicSequenceChannel ch : allChannels) {
            String chLabel = (ch.isSubChannel() ? "↳ " : "") + ch.getName() + " (" + ch.getType() + ")";
            chEntries.add(new CyberpunkDropdown.DropdownEntry<>(ch, Component.literal(chLabel), null, ch.getColor()));
            if (ch.getId().equalsIgnoreCase(entry.getChannelId())) {
                selectedChannel = ch;
            }
        }
        if (selectedChannel == null && !allChannels.isEmpty()) {
            selectedChannel = allChannels.get(0);
        }

        CyberpunkDropdown<MusicSequenceChannel> chDropdown = createDropdown(
                curY, innerW, pad, Component.literal("Track / Channel")
        );
        chDropdown.setOptions(chEntries);
        if (selectedChannel != null) {
            chDropdown.selectByValue(selectedChannel);
        }
        chDropdown.setOnSelect(sel -> {
            if (sel.getValue() != null) {
                entry.setChannelId(sel.getValue().getId());
                entry.setActionType(sel.getValue().getType());
                parent.saveCurrentSequenceToWorkingMap();
                rebuildWidgets();
            }
        });
        curY += 24;

        // --- Custom Description / Note Box ---
        EditBox descBox = addWidget(
                new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Label")),
                curY
        );
        descBox.setValue(entry.getDescription() != null ? entry.getDescription() : "");
        descBox.setHint(Component.literal("Keyframe label / note..."));
        descBox.setResponder(val -> {
            if (isUpdatingFields) return;
            entry.setDescription(val.trim());
            parent.saveCurrentSequenceToWorkingMap();
        });
        curY += 24;

        // --- Timestamp & Snap Button ---
        int snapBtnW = 48;
        int timeBoxW = innerW - snapBtnW - 4;
        EditBox timeBox = addWidget(
                new EditBox(font, x + pad, y + 28 + curY, timeBoxW, 18, Component.literal("Time (ms)")),
                curY
        );
        timeBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        timeBox.setValue(String.valueOf(entry.getTimestampMs()));
        timeBox.setTooltip(Tooltip.create(Component.literal("Timestamp in milliseconds")));
        timeBox.setResponder(val -> {
            if (isUpdatingFields) return;
            try {
                long ts = val.trim().isEmpty() ? 0 : Long.parseLong(val.trim());
                if (ts >= 0) {
                    entry.setTimestampMs(ts);
                    parent.getCurrentSequence().sortEntriesByTimestamp();
                    parent.saveCurrentSequenceToWorkingMap();
                }
            } catch (Exception ignored) {}
        });

        CyberpunkButton snapBtn = addWidget(
                new CyberpunkButton(x + pad + timeBoxW + 4, y + 28 + curY, snapBtnW, 18, Component.literal("⚡ SNAP"), b -> {
                    long snapped = parent.snapTimestamp(entry.getTimestampMs(), false);
                    entry.setTimestampMs(snapped);
                    timeBox.setValue(String.valueOf(snapped));
                    parent.getCurrentSequence().sortEntriesByTimestamp();
                    parent.saveCurrentSequenceToWorkingMap();
                }, GOLD_ACCENT, false, Component.literal("Snap timestamp to nearest beat / 50ms tick")),
                curY
        );
        curY += 24;

        // --- Duration (ms) Box ---
        boolean isPuppetEntry = "PUPPET".equalsIgnoreCase(entry.getActionType()) ||
                isChannelType(entry, MusicSequenceChannel.TYPE_PUPPET);
        boolean hasDuration = !isPuppetEntry && ("DIALOG".equalsIgnoreCase(entry.getActionType()) ||
                "SCREEN_EFFECT".equalsIgnoreCase(entry.getActionType()) ||
                entry.getTotalDurationMs() > 0 ||
                isChannelType(entry, MusicSequenceChannel.TYPE_DIALOG) ||
                isChannelType(entry, MusicSequenceChannel.TYPE_SCREEN_EFFECT));

        if (hasDuration) {
            if (("SCREEN_EFFECT".equalsIgnoreCase(entry.getActionType()) || isChannelType(entry, MusicSequenceChannel.TYPE_SCREEN_EFFECT)) && entry.getDurationMs() <= 0) {
                entry.setDurationMs(1000);
                parent.updateScreenEffectEntryCommand(entry);
            }
            int curDur = entry.getTotalDurationMs() > 0 ? entry.getTotalDurationMs() : (entry.getDurationMs() > 0 ? entry.getDurationMs() : 1000);
            EditBox durBox = addWidget(
                    new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Duration (ms)")),
                    curY
            );
            durBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
            durBox.setValue(String.valueOf(curDur));
            durBox.setTooltip(Tooltip.create(Component.literal("Total action active duration in milliseconds")));
            durBox.setResponder(val -> {
                if (isUpdatingFields) return;
                try {
                    int dur = val.trim().isEmpty() ? 0 : Integer.parseInt(val.trim());
                    if (dur >= 0) {
                        entry.setDurationMs(dur);
                        if ("SCREEN_EFFECT".equalsIgnoreCase(entry.getActionType()) || isChannelType(entry, MusicSequenceChannel.TYPE_SCREEN_EFFECT)) {
                            parent.updateScreenEffectEntryCommand(entry);
                        }
                        parent.saveCurrentSequenceToWorkingMap();
                    }
                } catch (Exception ignored) {}
            });
            curY += 22;

            // Quick Duration Step Buttons (-250, +250, +500, +1s, +2s)
            int stepBtnW = (innerW - 16) / 5;
            addWidget(new CyberpunkButton(x + pad, y + 28 + curY, stepBtnW, 16, Component.literal("-250"), b -> {
                int cur = Math.max(50, entry.getDurationMs() - 250);
                entry.setDurationMs(cur);
                durBox.setValue(String.valueOf(cur));
                if ("SCREEN_EFFECT".equalsIgnoreCase(entry.getActionType()) || isChannelType(entry, MusicSequenceChannel.TYPE_SCREEN_EFFECT)) {
                    parent.updateScreenEffectEntryCommand(entry);
                }
                parent.saveCurrentSequenceToWorkingMap();
            }, 0xFF8899AA, false, Component.literal("Subtract 250ms from duration")), curY);

            addWidget(new CyberpunkButton(x + pad + stepBtnW + 4, y + 28 + curY, stepBtnW, 16, Component.literal("+250"), b -> {
                int cur = entry.getDurationMs() + 250;
                entry.setDurationMs(cur);
                durBox.setValue(String.valueOf(cur));
                if ("SCREEN_EFFECT".equalsIgnoreCase(entry.getActionType()) || isChannelType(entry, MusicSequenceChannel.TYPE_SCREEN_EFFECT)) {
                    parent.updateScreenEffectEntryCommand(entry);
                }
                parent.saveCurrentSequenceToWorkingMap();
            }, 0xFF00E5FF, false, Component.literal("Add 250ms to duration")), curY);

            addWidget(new CyberpunkButton(x + pad + (stepBtnW + 4) * 2, y + 28 + curY, stepBtnW, 16, Component.literal("+500"), b -> {
                int cur = entry.getDurationMs() + 500;
                entry.setDurationMs(cur);
                durBox.setValue(String.valueOf(cur));
                if ("SCREEN_EFFECT".equalsIgnoreCase(entry.getActionType()) || isChannelType(entry, MusicSequenceChannel.TYPE_SCREEN_EFFECT)) {
                    parent.updateScreenEffectEntryCommand(entry);
                }
                parent.saveCurrentSequenceToWorkingMap();
            }, 0xFF00E5FF, false, Component.literal("Add 500ms to duration")), curY);

            addWidget(new CyberpunkButton(x + pad + (stepBtnW + 4) * 3, y + 28 + curY, stepBtnW, 16, Component.literal("+1s"), b -> {
                int cur = entry.getDurationMs() + 1000;
                entry.setDurationMs(cur);
                durBox.setValue(String.valueOf(cur));
                if ("SCREEN_EFFECT".equalsIgnoreCase(entry.getActionType()) || isChannelType(entry, MusicSequenceChannel.TYPE_SCREEN_EFFECT)) {
                    parent.updateScreenEffectEntryCommand(entry);
                }
                parent.saveCurrentSequenceToWorkingMap();
            }, 0xFF00FF88, false, Component.literal("Add 1000ms to duration")), curY);

            addWidget(new CyberpunkButton(x + pad + (stepBtnW + 4) * 4, y + 28 + curY, stepBtnW, 16, Component.literal("+2s"), b -> {
                int cur = entry.getDurationMs() + 2000;
                entry.setDurationMs(cur);
                durBox.setValue(String.valueOf(cur));
                if ("SCREEN_EFFECT".equalsIgnoreCase(entry.getActionType()) || isChannelType(entry, MusicSequenceChannel.TYPE_SCREEN_EFFECT)) {
                    parent.updateScreenEffectEntryCommand(entry);
                }
                parent.saveCurrentSequenceToWorkingMap();
            }, 0xFF00FF88, false, Component.literal("Add 2000ms to duration")), curY);

            curY += 24;
        }

        // --- Quick Actions Bar: Duplicate, Delete, Jump ---
        int thirdW = (innerW - 8) / 3;
        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, thirdW, 18, Component.literal("+ DUP"), b -> {
            parent.duplicateSelectedEntry();
        }, CYAN_MAIN, false, Component.literal("Duplicate keyframe at +100ms")), curY);

        addWidget(new CyberpunkButton(x + pad + thirdW + 4, y + 28 + curY, thirdW, 18, Component.literal("✕ DEL"), b -> {
            parent.deleteSelectedEntry();
        }, RED_DELETE, false, Component.literal("Delete this keyframe")), curY);

        addWidget(new CyberpunkButton(x + pad + (thirdW * 2) + 8, y + 28 + curY, thirdW, 18, Component.literal("▶ JUMP"), b -> {
            parent.setPlayheadMs(entry.getTimestampMs());
        }, GOLD_ACCENT, false, Component.literal("Seek playhead to this keyframe")), curY);

        curY += 28;

        // --- Type-Specific Parameters Card ---
        String actionType = entry.getActionType() != null ? entry.getActionType().toUpperCase(Locale.ROOT) : "COMMAND";
        boolean isCamera = entry.isUseCamera() || "CAMERA".equalsIgnoreCase(actionType) || isChannelType(entry, MusicSequenceChannel.TYPE_CAMERA);
        boolean isPuppet = "PUPPET".equalsIgnoreCase(actionType) || isChannelType(entry, MusicSequenceChannel.TYPE_PUPPET);
        boolean isDialog = "DIALOG".equalsIgnoreCase(actionType) || isChannelType(entry, MusicSequenceChannel.TYPE_DIALOG);
        boolean isScreenEffect = "SCREEN_EFFECT".equalsIgnoreCase(actionType) || isChannelType(entry, MusicSequenceChannel.TYPE_SCREEN_EFFECT);
        boolean isSound = "SOUND".equalsIgnoreCase(actionType) || (entry.getCommand() != null && entry.getCommand().startsWith("playsound"));

        if (isCamera) {
            curY = buildCameraControls(font, entry, pad, innerW, curY);
        } else if (isPuppet) {
            curY = buildPuppetControls(font, entry, pad, innerW, curY);
        } else if (isDialog) {
            curY = buildDialogControls(font, entry, pad, innerW, curY);
        } else if (isScreenEffect) {
            curY = buildScreenEffectControls(font, entry, pad, innerW, curY);
        } else if (isSound) {
            curY = buildSoundControls(font, entry, pad, innerW, curY);
        } else {
            curY = buildCommandControls(font, entry, pad, innerW, curY);
        }

        this.contentHeight = curY + 40;
    }

    private boolean isChannelType(MusicSequenceEntry entry, String type) {
        if (entry == null || entry.getChannelId() == null) return false;
        MusicSequenceChannel ch = parent.getChannelById(entry.getChannelId());
        return ch != null && type.equalsIgnoreCase(ch.getType());
    }

    // =========================================================================
    // 2. CAMERA CONTROLS
    // =========================================================================

    private int buildCameraControls(Font font, MusicSequenceEntry entry, int pad, int innerW, int curY) {
        int halfW = (innerW - 4) / 2;
        int remHalfW = innerW - halfW - 4;
        int thirdW = (innerW - 8) / 3;
        int thirdW3 = innerW - (thirdW * 2) - 8;

        // =====================================================================
        // SECTION 1: VIEWPORT TOOLBAR
        // =====================================================================
        addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 14, Component.literal("🎬 3D VIEWPORT TOOLS"), CYAN_MAIN), curY);
        curY += 16;

        // Row 1: Primary Viewport Capture & View
        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, halfW, 20, Component.literal("🎥 CAPTURE CAM"), b -> {
            Vec3 pos = SequencerViewportManager.getWorkCameraPos();
            float yaw = SequencerViewportManager.getWorkCameraYaw();
            float pitch = SequencerViewportManager.getWorkCameraPitch();
            float roll = SequencerViewportManager.getWorkCameraRoll();
            double fov = SequencerViewportManager.getWorkCameraFov();

            entry.setUseCamera(true);
            entry.setCameraX(pos.x);
            entry.setCameraY(pos.y);
            entry.setCameraZ(pos.z);
            entry.setCameraYaw(yaw);
            entry.setCameraPitch(pitch);
            entry.setCameraRoll(roll);
            entry.setCameraFov(fov);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
            rebuildWidgets();
        }, GREEN_APPLY, false, Component.literal("Copy 3D Viewport camera position & angles to this keyframe")), curY);

        addWidget(new CyberpunkButton(x + pad + halfW + 4, y + 28 + curY, remHalfW, 20, Component.literal("👁 VIEW CAM"), b -> {
            SequencerViewportManager.setWorkCamera(
                    new Vec3(entry.getCameraX(), entry.getCameraY(), entry.getCameraZ()),
                    entry.getCameraYaw(),
                    entry.getCameraPitch(),
                    entry.getCameraRoll(),
                    entry.getCameraFov() > 0 ? entry.getCameraFov() : 70.0
            );
            SequencerViewportManager.setCurrentMode(SequencerViewportManager.CameraMode.WORK_CAMERA);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            parent.init();
        }, CYAN_MAIN, false, Component.literal("Move 3D work camera to see through this keyframe")), curY);
        curY += 22;

        // Row 2: Secondary Viewport Snapping & Flight (3 columns)
        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, thirdW, 18, Component.literal("📍 SNAP EYE"), b -> snapPlayerEye(entry),
                0xFF00FF88, false, Component.literal("Snap keyframe to player eye position and view angle")), curY);

        addWidget(new CyberpunkButton(x + pad + thirdW + 4, y + 28 + curY, thirdW, 18, Component.literal("🎯 AIM TARGET"), b -> snapAimAtTarget(entry),
                GOLD_ACCENT, false, Component.literal("Raycast and point keyframe or focus target directly at aimed block/entity")), curY);

        addWidget(new CyberpunkButton(x + pad + (thirdW * 2) + 8, y + 28 + curY, thirdW3, 18, Component.literal("✈ FREE FLY"), b -> flyWorkCamToKeyframe(entry),
                CYAN_MAIN, false, Component.literal("Teleport Work Camera to this keyframe and start free flight in 3D viewport")), curY);
        curY += 26;

        // =====================================================================
        // SECTION 2: CAMERA MODE (SEGMENTED SWITCHER)
        // =====================================================================
        addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 14, Component.literal("✦ CAMERA MODE"), 0xFFAABBCC), curY);
        curY += 16;

        // Row 1: Keyframe, Follow, OTS
        boolean isKeyframe = isModeActive(entry, "KEYFRAME");
        boolean isFollow = isModeActive(entry, "FOLLOW");
        boolean isOts = isModeActive(entry, "OVER_THE_SHOULDER");
        boolean isEnable = isModeActive(entry, "ENABLE");
        boolean isDisable = isModeActive(entry, "DISABLE");

        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, thirdW, 20, Component.literal("📷 KEYFRAME"), b -> {
            entry.setCameraMode("KEYFRAME");
            entry.setSubAction("KEYFRAME");
            entry.setUseCamera(true);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
            rebuildWidgets();
        }, isKeyframe ? 0xFF00E5FF : 0xFF88AABB, isKeyframe, Component.literal("Keyframe: Fixed 3D camera coordinates & angles")), curY);

        addWidget(new CyberpunkButton(x + pad + thirdW + 4, y + 28 + curY, thirdW, 20, Component.literal("👤 FOLLOW"), b -> {
            entry.setCameraMode("FOLLOW");
            entry.setSubAction("FOLLOW");
            entry.setUseCamera(true);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
            rebuildWidgets();
        }, isFollow ? 0xFF00E5FF : 0xFF88AABB, isFollow, Component.literal("Follow: Camera follows target entity dynamically")), curY);

        addWidget(new CyberpunkButton(x + pad + (thirdW * 2) + 8, y + 28 + curY, thirdW3, 20, Component.literal("👤 OTS"), b -> {
            entry.setCameraMode("OVER_THE_SHOULDER");
            entry.setSubAction("OVER_THE_SHOULDER");
            entry.setUseCamera(true);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
            rebuildWidgets();
        }, isOts ? 0xFF00E5FF : 0xFF88AABB, isOts, Component.literal("Over-The-Shoulder: Third-person shoulder camera")), curY);
        curY += 22;

        // Row 2: Enable & Disable Camera Toggles
        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, halfW, 18, Component.literal("▶ ENABLE CAM"), b -> {
            entry.setCameraMode("ENABLE");
            entry.setSubAction("ENABLE");
            entry.setUseCamera(true);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
            rebuildWidgets();
        }, isEnable ? 0xFF00FF88 : 0xFF7799AA, isEnable, Component.literal("Enable camera view on sequence playback")), curY);

        addWidget(new CyberpunkButton(x + pad + halfW + 4, y + 28 + curY, remHalfW, 18, Component.literal("■ DISABLE CAM"), b -> {
            entry.setCameraMode("DISABLE");
            entry.setSubAction("DISABLE");
            entry.setUseCamera(false);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
            rebuildWidgets();
        }, isDisable ? 0xFFFF3355 : 0xFF7799AA, isDisable, Component.literal("Disable sequence camera and restore player perspective")), curY);
        curY += 26;

        boolean isTrackingMode = isFollow || isOts;

        // =====================================================================
        // SECTION 3: WORLD TRANSFORM (POSITION & ANGLES)
        // =====================================================================
        addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 14, Component.literal("✦ POSITION (WORLD COORDS)"), 0xFFAABBCC), curY);
        curY += 16;

        // Position Inputs (X, Y, Z)
        EditBox posX = addWidget(new EditBox(font, x + pad, y + 28 + curY, thirdW, 18, Component.literal("X")), curY);
        posX.setValue(String.format(Locale.US, "%.2f", entry.getCameraX()));
        posX.setTooltip(Tooltip.create(Component.literal("Camera X position (East/West) [Red]")));
        posX.setResponder(v -> parseDouble(v, entry::setCameraX));

        EditBox posY = addWidget(new EditBox(font, x + pad + thirdW + 4, y + 28 + curY, thirdW, 18, Component.literal("Y")), curY);
        posY.setValue(String.format(Locale.US, "%.2f", entry.getCameraY()));
        posY.setTooltip(Tooltip.create(Component.literal("Camera Y position (Elevation) [Green]")));
        posY.setResponder(v -> parseDouble(v, entry::setCameraY));

        EditBox posZ = addWidget(new EditBox(font, x + pad + (thirdW * 2) + 8, y + 28 + curY, thirdW3, 18, Component.literal("Z")), curY);
        posZ.setValue(String.format(Locale.US, "%.2f", entry.getCameraZ()));
        posZ.setTooltip(Tooltip.create(Component.literal("Camera Z position (North/South) [Blue]")));
        posZ.setResponder(v -> parseDouble(v, entry::setCameraZ));
        curY += 24;

        // Rotation Inputs (Yaw, Pitch, Roll) + ↺ 0° Roll Button
        addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 14, Component.literal("✦ ROTATION (YAW / PITCH / ROLL)"), 0xFFAABBCC), curY);
        curY += 16;

        int rotW = (innerW - 46 - 8) / 3;
        int rollBtnW = 46;

        EditBox rotYaw = addWidget(new EditBox(font, x + pad, y + 28 + curY, rotW, 18, Component.literal("Yaw")), curY);
        rotYaw.setValue(String.format(Locale.US, "%.1f", entry.getCameraYaw()));
        rotYaw.setTooltip(Tooltip.create(Component.literal("Camera Yaw angle (degrees)")));
        rotYaw.setResponder(v -> parseFloat(v, entry::setCameraYaw));

        EditBox rotPitch = addWidget(new EditBox(font, x + pad + rotW + 4, y + 28 + curY, rotW, 18, Component.literal("Pitch")), curY);
        rotPitch.setValue(String.format(Locale.US, "%.1f", entry.getCameraPitch()));
        rotPitch.setTooltip(Tooltip.create(Component.literal("Camera Pitch angle (degrees)")));
        rotPitch.setResponder(v -> parseFloat(v, entry::setCameraPitch));

        EditBox rotRoll = addWidget(new EditBox(font, x + pad + (rotW * 2) + 8, y + 28 + curY, rotW, 18, Component.literal("Roll")), curY);
        rotRoll.setValue(String.format(Locale.US, "%.1f", entry.getCameraRoll()));
        rotRoll.setTooltip(Tooltip.create(Component.literal("Camera Dutch Roll tilt (degrees)")));
        rotRoll.setResponder(v -> parseFloat(v, entry::setCameraRoll));

        addWidget(new CyberpunkButton(x + pad + (rotW * 3) + 12, y + 28 + curY, rollBtnW, 18, Component.literal("↺ 0°"), b -> resetCameraRoll(entry),
                GOLD_ACCENT, false, Component.literal("Reset camera roll angle to level 0°")), curY);
        curY += 26;

        // =====================================================================
        // SECTION 4: LENS & FOV (SLIDER + QUICK PRESETS)
        // =====================================================================
        addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 14, Component.literal("✦ LENS & FIELD OF VIEW"), 0xFFAABBCC), curY);
        curY += 16;

        double curFov = entry.getCameraFov() > 0 ? entry.getCameraFov() : 70.0;
        addWidget(new CyberpunkSlider(x + pad, y + 28 + curY, innerW, 18, "FOV", (curFov - 10.0) / 120.0, val -> {
            if (isUpdatingFields) return;
            double fov = 10.0 + (val * 120.0);
            entry.setCameraFov(fov);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
        }, val -> String.format(Locale.US, "FOV: %.0f°", 10.0 + (val * 120.0))), curY);
        curY += 22;

        // 4 Quick FOV Focal Length Preset Pills
        int fovPillW = (innerW - 12) / 4;
        int remFovPillW = innerW - (fovPillW * 3) - 12;

        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, fovPillW, 16, Component.literal("50° Tele"), b -> {
            entry.setCameraFov(50.0);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
            rebuildWidgets();
        }, (Math.abs(curFov - 50.0) < 1.0) ? 0xFF00E5FF : 0xFF88AABB, Math.abs(curFov - 50.0) < 1.0, Component.literal("Telephoto 50° focal length")), curY);

        addWidget(new CyberpunkButton(x + pad + fovPillW + 4, y + 28 + curY, fovPillW, 16, Component.literal("70° Norm"), b -> {
            entry.setCameraFov(70.0);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
            rebuildWidgets();
        }, (Math.abs(curFov - 70.0) < 1.0) ? 0xFF00E5FF : 0xFF88AABB, Math.abs(curFov - 70.0) < 1.0, Component.literal("Standard 70° default FOV")), curY);

        addWidget(new CyberpunkButton(x + pad + (fovPillW * 2) + 8, y + 28 + curY, fovPillW, 16, Component.literal("90° Wide"), b -> {
            entry.setCameraFov(90.0);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
            rebuildWidgets();
        }, (Math.abs(curFov - 90.0) < 1.0) ? 0xFF00E5FF : 0xFF88AABB, Math.abs(curFov - 90.0) < 1.0, Component.literal("Wide-Angle 90° FOV")), curY);

        addWidget(new CyberpunkButton(x + pad + (fovPillW * 3) + 12, y + 28 + curY, remFovPillW, 16, Component.literal("110° Ultra"), b -> {
            entry.setCameraFov(110.0);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
            rebuildWidgets();
        }, (Math.abs(curFov - 110.0) < 1.0) ? 0xFF00E5FF : 0xFF88AABB, Math.abs(curFov - 110.0) < 1.0, Component.literal("Action/Cinematic 110° Ultra-Wide FOV")), curY);
        curY += 26;

        // =====================================================================
        // SECTION 5: VISUAL MOTION & INTERPOLATION WIDGET
        // =====================================================================
        addWidget(new CameraInterpolationWidget(x + pad, y + 28 + curY, innerW, 274, entry, parent, this, font), curY);
        curY += 280;

        // Fine-Tuning Easing Dropdown (All 20 Easing curves for precision tuning)
        List<CyberpunkDropdown.DropdownEntry<CameraEasing>> easingEntries = new ArrayList<>();
        for (CameraEasing e : CameraEasing.values()) {
            easingEntries.add(new CyberpunkDropdown.DropdownEntry<>(e, Component.literal("Easing: " + e.getDisplayName())));
        }
        CyberpunkDropdown<CameraEasing> easingDropdown = createDropdown(
                curY, innerW, pad, Component.literal("Easing Preset (All Variants)")
        );
        easingDropdown.setOptions(easingEntries);
        CameraEasing curEasing = CameraEasing.fromString(entry.getCameraEasing());
        easingDropdown.selectByValue(curEasing);
        easingDropdown.setOnSelect(sel -> {
            entry.setCameraEasing(sel.getValue() != null ? sel.getValue().name() : "EASE_IN_OUT_CUBIC");
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
            rebuildWidgets();
        });
        curY += 24;

        // Smooth Interpolation Toggle Checkbox
        addWidget(new CyberpunkCheckbox(x + pad, y + 28 + curY, innerW, 16, Component.literal("Smooth Transition Motion"), entry.isCameraInterpolate(), chk -> {
            entry.setCameraInterpolate(chk);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
        }), curY);
        curY += 22;

        // Bézier Handles (when Bézier spline selected)
        SplineInterpolationType curSpline = SplineInterpolationType.fromString(entry.getSplineMode());
        if (curSpline == SplineInterpolationType.BEZIER) {
            addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 14, Component.literal("✦ BÉZIER TANGENT HANDLES"), GOLD_ACCENT), curY);
            curY += 16;

            EditBox inX = addWidget(new EditBox(font, x + pad, y + 28 + curY, thirdW, 18, Component.literal("InX")), curY);
            inX.setValue(String.format(Locale.US, "%.2f", entry.getInHandleX()));
            inX.setTooltip(Tooltip.create(Component.literal("Bézier In-Handle X offset")));
            inX.setResponder(v -> parseDouble(v, entry::setInHandleX));

            EditBox inY = addWidget(new EditBox(font, x + pad + thirdW + 4, y + 28 + curY, thirdW, 18, Component.literal("InY")), curY);
            inY.setValue(String.format(Locale.US, "%.2f", entry.getInHandleY()));
            inY.setTooltip(Tooltip.create(Component.literal("Bézier In-Handle Y offset")));
            inY.setResponder(v -> parseDouble(v, entry::setInHandleY));

            EditBox inZ = addWidget(new EditBox(font, x + pad + (thirdW * 2) + 8, y + 28 + curY, thirdW3, 18, Component.literal("InZ")), curY);
            inZ.setValue(String.format(Locale.US, "%.2f", entry.getInHandleZ()));
            inZ.setTooltip(Tooltip.create(Component.literal("Bézier In-Handle Z offset")));
            inZ.setResponder(v -> parseDouble(v, entry::setInHandleZ));
            curY += 22;

            EditBox outX = addWidget(new EditBox(font, x + pad, y + 28 + curY, thirdW, 18, Component.literal("OutX")), curY);
            outX.setValue(String.format(Locale.US, "%.2f", entry.getOutHandleX()));
            outX.setTooltip(Tooltip.create(Component.literal("Bézier Out-Handle X offset")));
            outX.setResponder(v -> parseDouble(v, entry::setOutHandleX));

            EditBox outY = addWidget(new EditBox(font, x + pad + thirdW + 4, y + 28 + curY, thirdW, 18, Component.literal("OutY")), curY);
            outY.setValue(String.format(Locale.US, "%.2f", entry.getOutHandleY()));
            outY.setTooltip(Tooltip.create(Component.literal("Bézier Out-Handle Y offset")));
            outY.setResponder(v -> parseDouble(v, entry::setOutHandleY));

            EditBox outZ = addWidget(new EditBox(font, x + pad + (thirdW * 2) + 8, y + 28 + curY, thirdW3, 18, Component.literal("OutZ")), curY);
            outZ.setValue(String.format(Locale.US, "%.2f", entry.getOutHandleZ()));
            outZ.setTooltip(Tooltip.create(Component.literal("Bézier Out-Handle Z offset")));
            outZ.setResponder(v -> parseDouble(v, entry::setOutHandleZ));
            curY += 26;
        }

        // =====================================================================
        // SECTION 6: TARGET TRACKING INPUTS (FOLLOW / OTS)
        // =====================================================================
        if (isTrackingMode) {
            addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 14, Component.literal("✦ TARGET TRACKING"), 0xFFAABBCC), curY);
            curY += 16;

            EditBox targetBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Target")), curY);
            targetBox.setValue(entry.getCameraTarget() != null ? entry.getCameraTarget() : "@p");
            targetBox.setHint(Component.literal("Target Entity / Tag (@p, hero)"));
            targetBox.setResponder(val -> {
                if (isUpdatingFields) return;
                entry.setCameraTarget(val.trim());
                parent.saveCurrentSequenceToWorkingMap();
                parent.updatePreviewCamera();
            });
            curY += 22;

            // Height, Back Distance, Shoulder Offsets
            EditBox hBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, thirdW, 18, Component.literal("Height")), curY);
            hBox.setValue(String.format(Locale.US, "%.1f", entry.getCameraHeightOffset()));
            hBox.setTooltip(Tooltip.create(Component.literal("Height offset from target eyes")));
            hBox.setResponder(v -> parseDouble(v, entry::setCameraHeightOffset));

            EditBox bBox = addWidget(new EditBox(font, x + pad + thirdW + 4, y + 28 + curY, thirdW, 18, Component.literal("Back")), curY);
            bBox.setValue(String.format(Locale.US, "%.1f", entry.getCameraBackDistance()));
            bBox.setTooltip(Tooltip.create(Component.literal("Follow distance behind target")));
            bBox.setResponder(v -> parseDouble(v, entry::setCameraBackDistance));

            EditBox sBox = addWidget(new EditBox(font, x + pad + (thirdW * 2) + 8, y + 28 + curY, thirdW3, 18, Component.literal("Shoulder")), curY);
            sBox.setValue(String.format(Locale.US, "%.1f", entry.getCameraShoulderOffset()));
            sBox.setTooltip(Tooltip.create(Component.literal("Shoulder offset (left / right)")));
            sBox.setResponder(v -> parseDouble(v, entry::setCameraShoulderOffset));
            curY += 26;
        }

        // =====================================================================
        // SECTION 7: LOOK-AT TARGET OPTIONS (IK)
        // =====================================================================
        addWidget(new CyberpunkCheckbox(x + pad, y + 28 + curY, innerW, 16, Component.literal("Look-At Focus Target (IK)"), entry.isLookAtEnabled(), chk -> {
            entry.setLookAtEnabled(chk);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
            rebuildWidgets();
        }), curY);
        curY += 20;

        if (entry.isLookAtEnabled()) {
            List<CyberpunkDropdown.DropdownEntry<String>> lookModes = new ArrayList<>();
            lookModes.add(new CyberpunkDropdown.DropdownEntry<>("COORDINATE", Component.literal("Fixed Coordinates (X, Y, Z)")));
            lookModes.add(new CyberpunkDropdown.DropdownEntry<>("ENTITY", Component.literal("Target Entity (@p, tag)")));

            CyberpunkDropdown<String> lookModeDropdown = createDropdown(
                    curY, innerW, pad, Component.literal("Focus Mode")
            );
            lookModeDropdown.setOptions(lookModes);
            String curLMode = entry.getLookAtMode() != null ? entry.getLookAtMode() : "COORDINATE";
            lookModeDropdown.selectByValue(curLMode);
            lookModeDropdown.setOnSelect(sel -> {
                entry.setLookAtMode(sel.getValue());
                parent.saveCurrentSequenceToWorkingMap();
                parent.updatePreviewCamera();
                rebuildWidgets();
            });
            curY += 24;

            if ("COORDINATE".equalsIgnoreCase(curLMode)) {
                EditBox lx = addWidget(new EditBox(font, x + pad, y + 28 + curY, thirdW, 18, Component.literal("LookX")), curY);
                lx.setValue(String.format(Locale.US, "%.2f", entry.getLookAtX()));
                lx.setResponder(v -> parseDouble(v, entry::setLookAtX));

                EditBox ly = addWidget(new EditBox(font, x + pad + thirdW + 4, y + 28 + curY, thirdW, 18, Component.literal("LookY")), curY);
                ly.setValue(String.format(Locale.US, "%.2f", entry.getLookAtY()));
                ly.setResponder(v -> parseDouble(v, entry::setLookAtY));

                EditBox lz = addWidget(new EditBox(font, x + pad + (thirdW * 2) + 8, y + 28 + curY, thirdW3, 18, Component.literal("LookZ")), curY);
                lz.setValue(String.format(Locale.US, "%.2f", entry.getLookAtZ()));
                lz.setResponder(v -> parseDouble(v, entry::setLookAtZ));
                curY += 22;
            } else {
                EditBox lTgt = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("LookTarget")), curY);
                lTgt.setValue(entry.getLookAtTarget() != null ? entry.getLookAtTarget() : "@p");
                lTgt.setHint(Component.literal("Target selector (@p, boss)"));
                lTgt.setResponder(v -> {
                    if (isUpdatingFields) return;
                    entry.setLookAtTarget(v.trim());
                    parent.saveCurrentSequenceToWorkingMap();
                    parent.updatePreviewCamera();
                });
                curY += 22;
            }

            // Look-At Weight Slider
            double curWeight = entry.getLookAtWeight() > 0 ? entry.getLookAtWeight() : 1.0;
            addWidget(new CyberpunkSlider(x + pad, y + 28 + curY, innerW, 16, "Weight", curWeight, val -> {
                if (isUpdatingFields) return;
                entry.setLookAtWeight((float) (double) val);
                parent.saveCurrentSequenceToWorkingMap();
                parent.updatePreviewCamera();
            }, v -> String.format(Locale.US, "Focus Weight: %.0f%%", v * 100.0)), curY);
            curY += 20;
        }

        return curY;
    }

    private void snapPlayerEye(MusicSequenceEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Vec3 eye = mc.player.getEyePosition();
            entry.setUseCamera(true);
            entry.setCameraX(eye.x);
            entry.setCameraY(eye.y);
            entry.setCameraZ(eye.z);
            entry.setCameraYaw(mc.player.getYRot());
            entry.setCameraPitch(mc.player.getXRot());
            entry.setCameraRoll(0.0f);
            entry.setCameraFov(70.0);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.3f));
            rebuildWidgets();
        }
    }

    private void snapAimAtTarget(MusicSequenceEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            boolean isWorkCam = (SequencerViewportManager.getCurrentMode() == SequencerViewportManager.CameraMode.WORK_CAMERA);
            Vec3 origin = isWorkCam ? SequencerViewportManager.getWorkCameraPos() : mc.player.getEyePosition();
            float fYaw = isWorkCam ? SequencerViewportManager.getWorkCameraYaw() : mc.player.getYRot();
            float fPitch = isWorkCam ? SequencerViewportManager.getWorkCameraPitch() : mc.player.getXRot();
            float yawRad = (float) Math.toRadians(fYaw);
            float pitchRad = (float) Math.toRadians(fPitch);
            Vec3 look = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad)).normalize();
            Vec3 end = origin.add(look.scale(96.0));
            BlockHitResult hit = mc.level.clip(new ClipContext(origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            Vec3 hitPos = hit.getLocation();

            if (entry.isLookAtEnabled() && "COORDINATE".equalsIgnoreCase(entry.getLookAtMode())) {
                entry.setLookAtX(hitPos.x);
                entry.setLookAtY(hitPos.y);
                entry.setLookAtZ(hitPos.z);
            } else {
                Vec3 camPos = new Vec3(entry.getCameraX(), entry.getCameraY(), entry.getCameraZ());
                Vec3 diff = hitPos.subtract(camPos);
                if (diff.lengthSqr() > 1e-4) {
                    float targetYaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
                    float targetPitch = (float) Math.toDegrees(-Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
                    entry.setCameraYaw(targetYaw);
                    entry.setCameraPitch(targetPitch);
                }
            }
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.3f));
            rebuildWidgets();
        }
    }

    private void resetCameraRoll(MusicSequenceEntry entry) {
        entry.setCameraRoll(0.0f);
        if (SequencerViewportManager.getCurrentMode() == SequencerViewportManager.CameraMode.WORK_CAMERA) {
            SequencerViewportManager.resetWorkCameraRoll();
        }
        parent.saveCurrentSequenceToWorkingMap();
        parent.updatePreviewCamera();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.4f));
        rebuildWidgets();
    }

    private void flyWorkCamToKeyframe(MusicSequenceEntry entry) {
        SequencerViewportManager.setWorkCamera(
                new Vec3(entry.getCameraX(), entry.getCameraY(), entry.getCameraZ()),
                entry.getCameraYaw(),
                entry.getCameraPitch(),
                entry.getCameraRoll(),
                entry.getCameraFov() > 0 ? entry.getCameraFov() : 70.0
        );
        SequencerViewportManager.setCurrentMode(SequencerViewportManager.CameraMode.WORK_CAMERA);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
        parent.init();
    }

    // =========================================================================
    // 3. PUPPET CONTROLS
    // =========================================================================

    private String getPuppetActionId(MusicSequenceEntry entry) {
        if (entry.getPuppetParams() != null && entry.getPuppetParams().containsKey("action")) {
            return entry.getPuppetParams().get("action");
        }
        String cmd = entry.getCommand();
        if (cmd != null) {
            String q = cmd.trim();
            if (q.startsWith("puppet_action")) {
                for (String part : q.split("\\s+")) {
                    if (part.startsWith("action:")) {
                        return part.substring(7).trim();
                    }
                }
            } else if (!q.isBlank() && !q.startsWith("summon") && !q.startsWith("kill") && !q.startsWith("puppet_")) {
                return q;
            }
        }
        return ModPuppetActions.LEAP_SLAM.getId().toString();
    }

    private String getPuppetActorTag(MusicSequenceEntry entry) {
        if (entry.getPuppetParams() != null && entry.getPuppetParams().containsKey("actorTag") && !entry.getPuppetParams().get("actorTag").isBlank()) {
            return entry.getPuppetParams().get("actorTag");
        }
        if (entry.getChannelId() != null) {
            MusicSequenceChannel ch = parent.getChannelById(entry.getChannelId());
            if (ch != null) {
                if (ch.getActorTag() != null && !ch.getActorTag().isBlank()) {
                    return ch.getActorTag();
                }
                if (ch.getName() != null && !ch.getName().isBlank()) {
                    return ch.getName().toLowerCase(Locale.ROOT).replace(" ", "_");
                }
            }
        }
        return "puppet";
    }

    private String getPuppetActorName(MusicSequenceEntry entry) {
        if (entry.getChannelId() != null) {
            MusicSequenceChannel ch = parent.getChannelById(entry.getChannelId());
            if (ch != null) {
                if (ch.getActorName() != null && !ch.getActorName().isBlank()) {
                    return ch.getActorName();
                }
                if (ch.getName() != null && !ch.getName().isBlank()) {
                    return ch.getName();
                }
            }
        }
        return "Puppet";
    }

    private void ensurePuppetTimingsInitialized(MusicSequenceEntry entry, PuppetActionType<?> act) {
        String cmd = entry.getCommand();
        if (cmd != null && cmd.contains(":")) {
            for (String part : cmd.split("\\s+")) {
                if (part.startsWith("windup:") && entry.getWindupMs() == 0) {
                    try { entry.setWindupMs(Math.max(0, Integer.parseInt(part.substring(7).trim()))); } catch (Exception ignored) {}
                } else if (part.startsWith("jump:") && entry.getJumpMs() == 0) {
                    try { entry.setJumpMs(Math.max(0, Integer.parseInt(part.substring(5).trim()))); } catch (Exception ignored) {}
                } else if (part.startsWith("duration:") && entry.getDurationMs() == 0) {
                    try { entry.setDurationMs(Math.max(0, Integer.parseInt(part.substring(9).trim()))); } catch (Exception ignored) {}
                } else if (part.startsWith("recovery:") && entry.getRecoveryMs() == 0) {
                    try { entry.setRecoveryMs(Math.max(0, Integer.parseInt(part.substring(9).trim()))); } catch (Exception ignored) {}
                } else if (part.startsWith("target:") && !entry.getPuppetParams().containsKey("target")) {
                    entry.putPuppetParam("target", part.substring(7).trim());
                } else if (part.startsWith("tag:") && !entry.getPuppetParams().containsKey("actorTag")) {
                    entry.putPuppetParam("actorTag", part.substring(4).trim());
                } else if (part.contains(":") && !part.startsWith("puppet_") && !part.startsWith("action:")) {
                    int c = part.indexOf(':');
                    String k = part.substring(0, c).trim();
                    String v = part.substring(c + 1).trim();
                    if (!k.isEmpty() && !entry.getPuppetParams().containsKey(k)) {
                        entry.putPuppetParam(k, v);
                    }
                }
            }
        }

        if (entry.getWindupMs() == 0 && entry.getJumpMs() == 0 && entry.getDurationMs() == 0 && entry.getRecoveryMs() == 0) {
            List<ActionTimingPhase> phases = act.getTimingPhases();
            for (int i = 0; i < phases.size(); i++) {
                ActionTimingPhase p = phases.get(i);
                setPhaseMs(entry, p, i, p.defaultMs());
            }
        }
    }

    public int getPhaseMs(MusicSequenceEntry entry, ActionTimingPhase phase, int index) {
        if (phase == null) return 0;
        return switch (phase.id().toLowerCase(Locale.ROOT)) {
            case "windup" -> entry.getWindupMs();
            case "jump" -> entry.getJumpMs();
            case "duration" -> entry.getDurationMs();
            case "recovery" -> entry.getRecoveryMs();
            default -> switch (index) {
                case 0 -> entry.getWindupMs();
                case 1 -> entry.getJumpMs();
                case 2 -> entry.getDurationMs();
                default -> entry.getRecoveryMs();
            };
        };
    }

    public void setPhaseMs(MusicSequenceEntry entry, ActionTimingPhase phase, int index, int ms) {
        if (phase == null) return;
        switch (phase.id().toLowerCase(Locale.ROOT)) {
            case "windup" -> entry.setWindupMs(ms);
            case "jump" -> entry.setJumpMs(ms);
            case "duration" -> entry.setDurationMs(ms);
            case "recovery" -> entry.setRecoveryMs(ms);
            default -> {
                switch (index) {
                    case 0 -> entry.setWindupMs(ms);
                    case 1 -> entry.setJumpMs(ms);
                    case 2 -> entry.setDurationMs(ms);
                    default -> entry.setRecoveryMs(ms);
                }
            }
        }
    }

    private void updatePuppetCommand(MusicSequenceEntry entry) {
        if ("EXECUTE_ACTION".equalsIgnoreCase(entry.getSubAction()) || entry.getSubAction() == null || entry.getSubAction().isEmpty()) {
            String actId = getPuppetActionId(entry);
            String tag = getPuppetActorTag(entry);
            String target = entry.getPuppetParam("target", "@p");
            PuppetActionType<?> act = ModPuppetActions.resolve(actId);
            List<ActionTimingPhase> phases = act.getTimingPhases();

            StringBuilder sb = new StringBuilder();
            sb.append("puppet_action tag:").append(tag).append(" action:").append(actId).append(" target:").append(target);
            for (int i = 0; i < phases.size(); i++) {
                ActionTimingPhase phase = phases.get(i);
                int ms = getPhaseMs(entry, phase, i);
                sb.append(" ").append(phase.id()).append(":").append(ms);
            }
            if (entry.getPuppetParams() != null) {
                for (Map.Entry<String, String> pe : entry.getPuppetParams().entrySet()) {
                    String k = pe.getKey();
                    if ("action".equals(k) || "target".equals(k) || "actorTag".equals(k)) continue;
                    if ("windup".equals(k) || "jump".equals(k) || "duration".equals(k) || "recovery".equals(k)) continue;
                    sb.append(" ").append(k).append(":").append(pe.getValue());
                }
            }
            entry.setCommand(sb.toString());
        }
    }

    private void updatePuppetDespawnCommand(MusicSequenceEntry entry) {
        String actorTag = getPuppetActorTag(entry);
        String actorName = getPuppetActorName(entry);
        entry.setCommand(String.format(Locale.ROOT, "kill @e[tag=%s]", actorTag));
        entry.setDescription("Despawn " + actorName + " (#" + actorTag + ")");
    }

    private void updatePuppetSpawnCommand(MusicSequenceEntry entry) {
        String actorTag = getPuppetActorTag(entry);
        String actorName = getPuppetActorName(entry);
        String entityType = entry.getPuppetParam("entityType", "");
        if (entityType.isBlank() && entry.getChannelId() != null) {
            MusicSequenceChannel ch = parent.getChannelById(entry.getChannelId());
            if (ch != null && ch.getEntityTypeId() != null && !ch.getEntityTypeId().isBlank()) {
                entityType = ch.getEntityTypeId();
            }
        }
        if (entityType.isBlank()) {
            entityType = "fractured_utils:void_herald";
        }
        String sx = entry.getPuppetParam("spawnX", "0.0");
        String sy = entry.getPuppetParam("spawnY", "64.0");
        String sz = entry.getPuppetParam("spawnZ", "0.0");
        boolean showNametag = "true".equalsIgnoreCase(entry.getPuppetParam("showNametag", "false"));
        boolean disableAi = "true".equalsIgnoreCase(entry.getPuppetParam("spawnDisableAi", "false"));
        float yaw = entry.getSpawnYaw();
        float pitch = entry.getSpawnPitch();

        String cmd = String.format(Locale.ROOT, "summon %s %s %s %s {Tags:[\"%s\",\"puppet_actor\"],CustomName:'{\"text\":\"%s\"}',CustomNameVisible:%sb,NoAI:%sb,PersistenceRequired:1b,Rotation:[%.2ff,%.2ff]}",
                entityType, sx, sy, sz, actorTag, actorName, showNametag ? "1" : "0", disableAi ? "1" : "0", yaw, pitch);
        entry.setCommand(cmd);
        entry.setDescription("Spawn " + actorName + " @ " + sx + " " + sy + " " + sz);
    }

    private void updatePuppetAiCommand(MusicSequenceEntry entry) {
        String actorTag = getPuppetActorTag(entry);
        String aiState = entry.getPuppetParam("aiState", "FREEZE");
        boolean isFreeze = "FREEZE".equalsIgnoreCase(aiState);
        if (isFreeze) {
            entry.setCommand(String.format(Locale.ROOT, "puppet_suppress tag:%s ai:true nav:true tgt:true look:false actions:true", actorTag));
            entry.setDescription("Freeze AI: #" + actorTag);
        } else {
            entry.setCommand(String.format(Locale.ROOT, "puppet_restore tag:%s", actorTag));
            entry.setDescription("Restore AI: #" + actorTag);
        }
    }

    private int buildPuppetControls(Font font, MusicSequenceEntry entry, int pad, int innerW, int curY) {
        // Sub-Action Selector (Action Type)
        List<CyberpunkDropdown.DropdownEntry<String>> subActions = new ArrayList<>();
        subActions.add(new CyberpunkDropdown.DropdownEntry<>("EXECUTE_ACTION", Component.literal("Execute Action")));
        subActions.add(new CyberpunkDropdown.DropdownEntry<>("SPAWN", Component.literal("Spawn Puppet")));
        subActions.add(new CyberpunkDropdown.DropdownEntry<>("DESPAWN", Component.literal("Despawn Puppet")));
        subActions.add(new CyberpunkDropdown.DropdownEntry<>("TOGGLE_AI", Component.literal("Toggle AI (Freeze / Unfreeze)")));

        CyberpunkDropdown<String> subActionDropdown = createDropdown(
                curY, innerW, pad, Component.literal("Action Type")
        );
        subActionDropdown.setOptions(subActions);
        String sub = entry.getSubAction() != null && !entry.getSubAction().isEmpty() ? entry.getSubAction() : "EXECUTE_ACTION";
        subActionDropdown.selectByValue(sub);
        subActionDropdown.setOnSelect(sel -> {
            String newSub = sel.getValue();
            entry.setSubAction(newSub);
            if ("DESPAWN".equalsIgnoreCase(newSub)) {
                entry.setWindupMs(0);
                entry.setJumpMs(0);
                entry.setDurationMs(0);
                entry.setRecoveryMs(0);
                updatePuppetDespawnCommand(entry);
            } else if ("SPAWN".equalsIgnoreCase(newSub)) {
                entry.setWindupMs(0);
                entry.setJumpMs(0);
                entry.setDurationMs(0);
                entry.setRecoveryMs(0);
                updatePuppetSpawnCommand(entry);
            } else if ("TOGGLE_AI".equalsIgnoreCase(newSub)) {
                entry.setWindupMs(0);
                entry.setJumpMs(0);
                entry.setDurationMs(0);
                entry.setRecoveryMs(0);
                updatePuppetAiCommand(entry);
            } else {
                String curAct = getPuppetActionId(entry);
                PuppetActionType<?> activeAct = ModPuppetActions.resolve(curAct);
                ensurePuppetTimingsInitialized(entry, activeAct);
                updatePuppetCommand(entry);
            }
            parent.saveCurrentSequenceToWorkingMap();
            rebuildWidgets();
        });
        curY += 24;

        if ("DESPAWN".equalsIgnoreCase(sub) || "SPAWN".equalsIgnoreCase(sub) || "TOGGLE_AI".equalsIgnoreCase(sub)) {
            if (entry.getTotalDurationMs() > 0) {
                entry.setWindupMs(0);
                entry.setJumpMs(0);
                entry.setDurationMs(0);
                entry.setRecoveryMs(0);
                parent.saveCurrentSequenceToWorkingMap();
            }
        }

        if ("EXECUTE_ACTION".equalsIgnoreCase(sub)) {
            String curAct = getPuppetActionId(entry);
            PuppetActionType<?> activeAct = ModPuppetActions.resolve(curAct);
            ensurePuppetTimingsInitialized(entry, activeAct);

            // Action Type dropdown
            List<CyberpunkDropdown.DropdownEntry<String>> actionTypes = new ArrayList<>();
            for (PuppetActionType<?> pat : ModPuppetActions.getAll()) {
                String simpleName = pat.getId().getPath().replace("_", " ").toUpperCase(Locale.ROOT);
                actionTypes.add(new CyberpunkDropdown.DropdownEntry<>(pat.getId().toString(), Component.literal("⚡ " + simpleName)));
            }
            CyberpunkDropdown<String> actDropdown = createDropdown(
                    curY, innerW, pad, Component.literal("Attack / Animation")
            );
            actDropdown.setOptions(actionTypes);
            actDropdown.selectByValue(curAct);
            actDropdown.setOnSelect(sel -> {
                entry.putPuppetParam("action", sel.getValue());
                PuppetActionType<?> pat = ModPuppetActions.resolve(sel.getValue());
                if (pat != null) {
                    List<ActionTimingPhase> newPhases = pat.getTimingPhases();
                    entry.setWindupMs(0);
                    entry.setJumpMs(0);
                    entry.setDurationMs(0);
                    entry.setRecoveryMs(0);
                    for (int i = 0; i < newPhases.size(); i++) {
                        ActionTimingPhase p = newPhases.get(i);
                        setPhaseMs(entry, p, i, p.defaultMs());
                    }
                    for (ActionParameter<?> param : pat.getParameters()) {
                        if (!entry.getPuppetParams().containsKey(param.getKey())) {
                            entry.putPuppetParam(param.getKey(), param.getDefaultValueString());
                        }
                    }
                }
                updatePuppetCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
                rebuildWidgets();
            });
            curY += 24;

            // Target Selector Box
            addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 10, Component.literal("Target Selector"), TEXT_MUTED), curY);
            curY += 12;
            EditBox targetBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Target")), curY);
            String curTarget = entry.getPuppetParam("target", "@p");
            targetBox.setValue(curTarget);
            targetBox.setHint(Component.literal("@p, @e[tag=...], or coordinates"));
            targetBox.setTooltip(Tooltip.create(Component.literal("Action combat target selector")));
            targetBox.setResponder(val -> {
                if (isUpdatingFields) return;
                entry.putPuppetParam("target", val.trim());
                updatePuppetCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
            });
            curY += 22;

            // Actor Tag Box
            addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 10, Component.literal("Actor Tag"), TEXT_MUTED), curY);
            curY += 12;
            EditBox tagBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Actor Tag")), curY);
            tagBox.setValue(getPuppetActorTag(entry));
            tagBox.setHint(Component.literal("Target Actor Tag (#hero, #boss)"));
            tagBox.setResponder(val -> {
                if (isUpdatingFields) return;
                entry.putPuppetParam("actorTag", val.trim());
                updatePuppetCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
            });
            curY += 22;

            // Dynamic Action Timing Phases
            List<ActionTimingPhase> phases = activeAct.getTimingPhases();
            int phaseCount = phases.size();

            addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 10, () -> {
                int curTotalMs = 0;
                for (int i = 0; i < phases.size(); i++) {
                    curTotalMs += getPhaseMs(entry, phases.get(i), i);
                }
                long curExecOffset = 0;
                for (int i = 0; i < phases.size(); i++) {
                    ActionTimingPhase p = phases.get(i);
                    if (p.isExecutionPoint()) break;
                    curExecOffset += getPhaseMs(entry, p, i);
                }
                long curExecTotalTime = entry.getTimestampMs() + curExecOffset;
                return Component.literal(String.format(Locale.ROOT, "⏱ TIMINGS — 💥 %s: %dms (Total: %dms)",
                        activeAct.getExecutionLabel(), curExecTotalTime, curTotalMs));
            }, CYAN_MAIN), curY);
            curY += 12;

            List<EditBox> phaseBoxes = new ArrayList<>();

            // Segmented Timing Preview Bar
            addWidget(new CyberpunkTimingBar(x + pad, y + 28 + curY, innerW, 18, entry, activeAct, font, phaseBoxes, this), curY);
            curY += 22;

            // Exact Number of Phase Input Boxes with Appropriate Names
            if (phaseCount == 1) {
                ActionTimingPhase phase = phases.get(0);
                int labelCol = (phase.color() != 0) ? (0xFF000000 | phase.color()) : CYAN_MAIN;
                addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 10, Component.literal(phase.label() + " (ms)"), labelCol), curY);
                curY += 12;

                EditBox box = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal(phase.label())), curY);
                box.setMaxLength(6);
                box.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
                box.setValue(String.valueOf(getPhaseMs(entry, phase, 0)));
                box.setHint(Component.literal(String.valueOf(phase.defaultMs())));
                box.setTooltip(Tooltip.create(Component.literal(phase.label() + " (" + phase.id() + ") duration in milliseconds [Default: " + phase.defaultMs() + "ms]")));
                box.setResponder(val -> {
                    if (isUpdatingFields) return;
                    try {
                        int ms = val.trim().isEmpty() ? 0 : Math.max(0, Integer.parseInt(val.trim()));
                        setPhaseMs(entry, phase, 0, ms);
                        updatePuppetCommand(entry);
                        parent.saveCurrentSequenceToWorkingMap();
                    } catch (Exception ignored) {}
                });
                phaseBoxes.add(box);
                curY += 22;
            } else {
                int halfW = (innerW - 6) / 2;
                for (int i = 0; i < phaseCount; i += 2) {
                    final int idxA = i;
                    final ActionTimingPhase phaseA = phases.get(idxA);
                    final int idxB = i + 1;
                    final ActionTimingPhase phaseB = (idxB < phaseCount) ? phases.get(idxB) : null;

                    // Label Row
                    int colA = (phaseA.color() != 0) ? (0xFF000000 | phaseA.color()) : CYAN_MAIN;
                    addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, halfW, 10, Component.literal(phaseA.label() + " (ms)"), colA), curY);
                    if (phaseB != null) {
                        int colB = (phaseB.color() != 0) ? (0xFF000000 | phaseB.color()) : CYAN_MAIN;
                        addWidget(new CyberpunkLabel(x + pad + halfW + 6, y + 28 + curY, halfW, 10, Component.literal(phaseB.label() + " (ms)"), colB), curY);
                    }
                    curY += 12;

                    // Input Row
                    int currentBoxW = (phaseB != null) ? halfW : innerW;
                    EditBox boxA = addWidget(new EditBox(font, x + pad, y + 28 + curY, currentBoxW, 18, Component.literal(phaseA.label())), curY);
                    boxA.setMaxLength(6);
                    boxA.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
                    boxA.setValue(String.valueOf(getPhaseMs(entry, phaseA, idxA)));
                    boxA.setHint(Component.literal(String.valueOf(phaseA.defaultMs())));
                    boxA.setTooltip(Tooltip.create(Component.literal(phaseA.label() + " (" + phaseA.id() + ") duration in ms [Default: " + phaseA.defaultMs() + "ms]")));
                    boxA.setResponder(val -> {
                        if (isUpdatingFields) return;
                        try {
                            int ms = val.trim().isEmpty() ? 0 : Math.max(0, Integer.parseInt(val.trim()));
                            setPhaseMs(entry, phaseA, idxA, ms);
                            updatePuppetCommand(entry);
                            parent.saveCurrentSequenceToWorkingMap();
                        } catch (Exception ignored) {}
                    });
                    phaseBoxes.add(boxA);

                    if (phaseB != null) {
                        EditBox boxB = addWidget(new EditBox(font, x + pad + halfW + 6, y + 28 + curY, halfW, 18, Component.literal(phaseB.label())), curY);
                        boxB.setMaxLength(6);
                        boxB.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
                        boxB.setValue(String.valueOf(getPhaseMs(entry, phaseB, idxB)));
                        boxB.setHint(Component.literal(String.valueOf(phaseB.defaultMs())));
                        boxB.setTooltip(Tooltip.create(Component.literal(phaseB.label() + " (" + phaseB.id() + ") duration in ms [Default: " + phaseB.defaultMs() + "ms]")));
                        boxB.setResponder(val -> {
                            if (isUpdatingFields) return;
                            try {
                                int ms = val.trim().isEmpty() ? 0 : Math.max(0, Integer.parseInt(val.trim()));
                                setPhaseMs(entry, phaseB, idxB, ms);
                                updatePuppetCommand(entry);
                                parent.saveCurrentSequenceToWorkingMap();
                            } catch (Exception ignored) {}
                        });
                        phaseBoxes.add(boxB);
                    }
                    curY += 22;
                }
            }

            // Declared Action Parameters
            List<ActionParameter<?>> params = activeAct.getParameters();
            if (!params.isEmpty()) {
                addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 10, Component.literal("⚙ ACTION PARAMETERS"), CYAN_MAIN), curY);
                curY += 12;

                int halfW = (innerW - 6) / 2;
                for (int i = 0; i < params.size(); i += 2) {
                    final ActionParameter<?> paramA = params.get(i);
                    final ActionParameter<?> paramB = (i + 1 < params.size()) ? params.get(i + 1) : null;

                    // Parameter Labels
                    addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, halfW, 10, Component.literal(paramA.getLabel().getString()), TEXT_MUTED), curY);
                    if (paramB != null) {
                        addWidget(new CyberpunkLabel(x + pad + halfW + 6, y + 28 + curY, halfW, 10, Component.literal(paramB.getLabel().getString()), TEXT_MUTED), curY);
                    }
                    curY += 12;

                    // Parameter Inputs
                    int curW = (paramB != null) ? halfW : innerW;
                    EditBox pBoxA = addWidget(new EditBox(font, x + pad, y + 28 + curY, curW, 18, paramA.getLabel()), curY);
                    pBoxA.setValue(entry.getPuppetParam(paramA.getKey(), paramA.getDefaultValueString()));
                    pBoxA.setHint(Component.literal(paramA.getDefaultValueString()));
                    pBoxA.setTooltip(Tooltip.create(paramA.getDescription()));
                    pBoxA.setResponder(val -> {
                        if (isUpdatingFields) return;
                        entry.putPuppetParam(paramA.getKey(), val.trim());
                        updatePuppetCommand(entry);
                        parent.saveCurrentSequenceToWorkingMap();
                    });

                    if (paramB != null) {
                        EditBox pBoxB = addWidget(new EditBox(font, x + pad + halfW + 6, y + 28 + curY, halfW, 18, paramB.getLabel()), curY);
                        pBoxB.setValue(entry.getPuppetParam(paramB.getKey(), paramB.getDefaultValueString()));
                        pBoxB.setHint(Component.literal(paramB.getDefaultValueString()));
                        pBoxB.setTooltip(Tooltip.create(paramB.getDescription()));
                        pBoxB.setResponder(val -> {
                            if (isUpdatingFields) return;
                            entry.putPuppetParam(paramB.getKey(), val.trim());
                            updatePuppetCommand(entry);
                            parent.saveCurrentSequenceToWorkingMap();
                        });
                    }
                    curY += 22;
                }
            }
        } else if ("SPAWN".equalsIgnoreCase(sub)) {
            // Spawn Header Banner
            addWidget(new CyberpunkButton(x + pad, y + 28 + curY, innerW, 20,
                    Component.literal("★ SPAWN PUPPET ACTOR"), b -> {}, GREEN_APPLY, false,
                    Component.literal("Summons a new puppet actor mob at this timestamp")), curY);
            curY += 24;

            // Entity Type Box
            addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 10, Component.literal("Entity Type ID:"), TEXT_LABEL), curY);
            curY += 12;
            EditBox entBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Entity Type")), curY);
            String curEnt = entry.getPuppetParam("entityType", "");
            if (curEnt.isBlank() && entry.getChannelId() != null) {
                MusicSequenceChannel ch = parent.getChannelById(entry.getChannelId());
                if (ch != null && ch.getEntityTypeId() != null && !ch.getEntityTypeId().isBlank()) {
                    curEnt = ch.getEntityTypeId();
                }
            }
            if (curEnt.isBlank()) curEnt = "fractured_utils:void_herald";
            entBox.setValue(curEnt);
            entBox.setHint(Component.literal("e.g. fractured_utils:void_herald or minecraft:zombie"));
            entBox.setResponder(v -> {
                if (isUpdatingFields) return;
                entry.putPuppetParam("entityType", v.trim());
                updatePuppetSpawnCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
            });
            curY += 22;

            // Spawn Pos Inputs
            addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 10, Component.literal("Spawn Position (X, Y, Z):"), TEXT_LABEL), curY);
            curY += 12;
            int thirdW = (innerW - 8) / 3;
            EditBox sx = addWidget(new EditBox(font, x + pad, y + 28 + curY, thirdW, 18, Component.literal("SpawnX")), curY);
            sx.setValue(entry.getPuppetParam("spawnX", "0.0"));
            sx.setResponder(v -> {
                if (isUpdatingFields) return;
                entry.putPuppetParam("spawnX", v.trim());
                updatePuppetSpawnCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
            });

            EditBox sy = addWidget(new EditBox(font, x + pad + thirdW + 4, y + 28 + curY, thirdW, 18, Component.literal("SpawnY")), curY);
            sy.setValue(entry.getPuppetParam("spawnY", "64.0"));
            sy.setResponder(v -> {
                if (isUpdatingFields) return;
                entry.putPuppetParam("spawnY", v.trim());
                updatePuppetSpawnCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
            });

            EditBox sz = addWidget(new EditBox(font, x + pad + (thirdW * 2) + 8, y + 28 + curY, thirdW, 18, Component.literal("SpawnZ")), curY);
            sz.setValue(entry.getPuppetParam("spawnZ", "0.0"));
            sz.setResponder(v -> {
                if (isUpdatingFields) return;
                entry.putPuppetParam("spawnZ", v.trim());
                updatePuppetSpawnCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
            });
            curY += 22;

            // Capture Player Pos Button
            addWidget(new CyberpunkButton(x + pad, y + 28 + curY, innerW, 18, Component.literal("📍 CAPTURE PLAYER POSITION"), b -> {
                if (Minecraft.getInstance().player != null) {
                    Vec3 pos = Minecraft.getInstance().player.position();
                    sx.setValue(String.format(Locale.US, "%.2f", pos.x));
                    sy.setValue(String.format(Locale.US, "%.2f", pos.y));
                    sz.setValue(String.format(Locale.US, "%.2f", pos.z));
                    entry.putPuppetParam("spawnX", String.format(Locale.US, "%.2f", pos.x));
                    entry.putPuppetParam("spawnY", String.format(Locale.US, "%.2f", pos.y));
                    entry.putPuppetParam("spawnZ", String.format(Locale.US, "%.2f", pos.z));
                    entry.setSpawnYaw(Minecraft.getInstance().player.getYRot());
                    entry.setSpawnPitch(Minecraft.getInstance().player.getXRot());
                    updatePuppetSpawnCommand(entry);
                    parent.saveCurrentSequenceToWorkingMap();
                }
            }, CYAN_MAIN, false, Component.literal("Stamp player coordinates as spawn location")), curY);
            curY += 22;

            // Actor Tag Box
            addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 10, Component.literal("Spawned Mob Tag:"), TEXT_LABEL), curY);
            curY += 12;
            EditBox tagBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Actor Tag")), curY);
            tagBox.setValue(getPuppetActorTag(entry));
            tagBox.setHint(Component.literal("Spawned Mob Tag"));
            tagBox.setResponder(val -> {
                if (isUpdatingFields) return;
                entry.putPuppetParam("actorTag", val.trim());
                updatePuppetSpawnCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
            });
            curY += 22;

            // Show Nametag Checkbox
            boolean showTag = "true".equalsIgnoreCase(entry.getPuppetParam("showNametag", "false"));
            addWidget(new CyberpunkCheckbox(x + pad, y + 28 + curY, innerW, 16, Component.literal("Show Nametag Above Mob"), showTag, chk -> {
                entry.putPuppetParam("showNametag", String.valueOf(chk));
                entry.setShowNametag(chk);
                updatePuppetSpawnCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
            }), curY);
            curY += 20;
        } else if ("TOGGLE_AI".equalsIgnoreCase(sub)) {
            // AI Header Banner
            addWidget(new CyberpunkButton(x + pad, y + 28 + curY, innerW, 20,
                    Component.literal("⚡ TOGGLE MOB AI STATE"), b -> {}, GOLD_ACCENT, false,
                    Component.literal("Freezes or unfreezes puppet mob AI behavior")), curY);
            curY += 24;

            List<CyberpunkDropdown.DropdownEntry<String>> aiStates = new ArrayList<>();
            aiStates.add(new CyberpunkDropdown.DropdownEntry<>("FREEZE", Component.literal("Freeze Mob (Disable AI)")));
            aiStates.add(new CyberpunkDropdown.DropdownEntry<>("RESTORE", Component.literal("Restore Mob (Enable AI)")));

            CyberpunkDropdown<String> aiDropdown = createDropdown(
                    curY, innerW, pad, Component.literal("AI State")
            );
            aiDropdown.setOptions(aiStates);
            String curAi = entry.getPuppetParam("aiState", "FREEZE");
            aiDropdown.selectByValue(curAi);
            aiDropdown.setOnSelect(sel -> {
                entry.putPuppetParam("aiState", sel.getValue());
                updatePuppetAiCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
            });
            curY += 24;

            addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 10, Component.literal("Target Mob Tag:"), TEXT_LABEL), curY);
            curY += 12;
            EditBox tagBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Actor Tag")), curY);
            tagBox.setValue(getPuppetActorTag(entry));
            tagBox.setHint(Component.literal("Target Mob Tag"));
            tagBox.setResponder(val -> {
                if (isUpdatingFields) return;
                entry.putPuppetParam("actorTag", val.trim());
                updatePuppetAiCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
            });
            curY += 22;
        } else if ("DESPAWN".equalsIgnoreCase(sub)) {
            // Despawn Header Banner
            addWidget(new CyberpunkButton(x + pad, y + 28 + curY, innerW, 20,
                    Component.literal("✕ DESPAWN PUPPET ACTOR"), b -> {}, RED_DELETE, false,
                    Component.literal("Removes and discards the spawned actor entity from the world")), curY);
            curY += 24;

            String actorTag = getPuppetActorTag(entry);
            addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 10,
                    Component.literal("Target Selector: @e[tag=" + actorTag + "]"), TEXT_MUTED), curY);
            curY += 14;

            addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 10,
                    Component.literal("Target Mob Tag to Despawn:"), TEXT_LABEL), curY);
            curY += 12;

            EditBox tagBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Actor Tag")), curY);
            tagBox.setValue(actorTag);
            tagBox.setHint(Component.literal("Target Mob Tag to Despawn"));
            tagBox.setResponder(val -> {
                if (isUpdatingFields) return;
                entry.putPuppetParam("actorTag", val.trim());
                updatePuppetDespawnCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
            });
            curY += 22;

            addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 24,
                    Component.literal("Instantly despawns this actor mob at the keyframe timestamp."), TEXT_MUTED), curY);
            curY += 26;
        }

        return curY;
    }

    // =========================================================================
    // 4. DIALOG CONTROLS
    // =========================================================================

    private int buildDialogControls(Font font, MusicSequenceEntry entry, int pad, int innerW, int curY) {
        DialogLine line = entry.getDialog();
        if (line == null) {
            line = new DialogLine();
            line.setSpeaker("Narrator");
            line.setText("Dialog text here...");
            entry.setDialog(line);
        }
        final DialogLine finalLine = line;

        // Speaker Box
        EditBox speakerBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Speaker")), curY);
        speakerBox.setValue(finalLine.getSpeaker() != null ? finalLine.getSpeaker() : "");
        speakerBox.setHint(Component.literal("Speaker Name (Narrator, Void Herald)"));
        speakerBox.setResponder(val -> {
            if (isUpdatingFields) return;
            finalLine.setSpeaker(val.trim());
            parent.saveCurrentSequenceToWorkingMap();
        });
        curY += 22;

        // Spoken Dialog Text Box
        EditBox textBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Dialog Text")), curY);
        textBox.setValue(finalLine.getText() != null ? finalLine.getText() : "");
        textBox.setHint(Component.literal("Spoken dialog text line..."));
        textBox.setResponder(val -> {
            if (isUpdatingFields) return;
            finalLine.setText(val);
            entry.setCommand(val);
            parent.saveCurrentSequenceToWorkingMap();
        });
        curY += 22;

        // Formatting Code Buttons Row
        int btnW = (innerW - 16) / 5;
        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, btnW, 16, Component.literal("§6GOLD"), b -> {
            textBox.setValue(textBox.getValue() + "§6");
        }, GOLD_ACCENT, false, Component.literal("Insert Gold color code")), curY);

        addWidget(new CyberpunkButton(x + pad + btnW + 4, y + 28 + curY, btnW, 16, Component.literal("§cRED"), b -> {
            textBox.setValue(textBox.getValue() + "§c");
        }, RED_DELETE, false, Component.literal("Insert Red color code")), curY);

        addWidget(new CyberpunkButton(x + pad + (btnW * 2) + 8, y + 28 + curY, btnW, 16, Component.literal("§bCYAN"), b -> {
            textBox.setValue(textBox.getValue() + "§b");
        }, CYAN_MAIN, false, Component.literal("Insert Cyan color code")), curY);

        addWidget(new CyberpunkButton(x + pad + (btnW * 3) + 12, y + 28 + curY, btnW, 16, Component.literal("§lBOLD"), b -> {
            textBox.setValue(textBox.getValue() + "§l");
        }, 0xFFFFFFFF, false, Component.literal("Insert Bold format code")), curY);

        addWidget(new CyberpunkButton(x + pad + (btnW * 4) + 16, y + 28 + curY, btnW, 16, Component.literal("§oITAL"), b -> {
            textBox.setValue(textBox.getValue() + "§o");
        }, TEXT_MUTED, false, Component.literal("Insert Italic format code")), curY);
        curY += 22;

        // Auto Duration Calculator Button
        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, innerW, 18, Component.literal("⚡ AUTO-CALC DURATION"), b -> {
            int charCount = DialogFormatUtil.getVisibleCharCount(finalLine.getText());
            int autoDur = Math.max(1200, (charCount * 55) + 800);
            entry.setDurationMs(autoDur);
            rebuildWidgets();
            parent.saveCurrentSequenceToWorkingMap();
        }, GREEN_APPLY, false, Component.literal("Calculate optimal display duration from word count")), curY);
        curY += 22;

        // Voice / Audio Sound ID
        int previewBtnW = 48;
        EditBox soundBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW - previewBtnW - 4, 18, Component.literal("Voice Sound")), curY);
        soundBox.setValue(finalLine.getSound() != null ? finalLine.getSound() : "");
        soundBox.setHint(Component.literal("Voice sound (minecraft:entity.villager.ambient)"));
        soundBox.setResponder(val -> {
            if (isUpdatingFields) return;
            finalLine.setSound(val.trim());
            parent.saveCurrentSequenceToWorkingMap();
        });

        addWidget(new CyberpunkButton(x + pad + innerW - previewBtnW, y + 28 + curY, previewBtnW, 18, Component.literal("▶ PLAY"), b -> {
            previewSound(soundBox.getValue(), 1.0f, 1.0f);
        }, CYAN_MAIN, false, Component.literal("Play sound preview")), curY);
        curY += 24;

        // Letter Sound Dropdown (via DialogSoundRegistry)
        List<CyberpunkDropdown.DropdownEntry<String>> soundEntries = DialogSoundRegistry.getAvailableSoundEntries(finalLine.getLetterSound());
        CyberpunkDropdown<String> letterDropdown = createDropdown(
                curY, innerW, pad, Component.literal("Typewriter Blip Sound")
        );
        letterDropdown.setOptions(soundEntries);
        String curLetterSound = finalLine.getLetterSound() != null ? finalLine.getLetterSound() : "";
        letterDropdown.selectByValue(curLetterSound);
        letterDropdown.setOnSelect(sel -> {
            finalLine.setLetterSound(sel.getValue());
            parent.saveCurrentSequenceToWorkingMap();
            previewSound(sel.getValue(), 1.0f, 1.0f);
        });
        curY += 24;

        return curY;
    }

    // =========================================================================
    // 5. SCREEN EFFECT CONTROLS
    // =========================================================================

    private boolean impactEditingSecondary = false;

    private int buildScreenEffectControls(Font font, MusicSequenceEntry entry, int pad, int innerW, int curY) {
        List<CyberpunkDropdown.DropdownEntry<String>> effectTypes = new ArrayList<>();
        effectTypes.add(new CyberpunkDropdown.DropdownEntry<>("SCREEN_SHAKE", Component.literal("Screen Shake / Rumble"), null, 0xFFFF00CC));
        effectTypes.add(new CyberpunkDropdown.DropdownEntry<>("INVERT_COLORS", Component.literal("Invert Colors (Negative)"), null, 0xFF00FFCC));
        effectTypes.add(new CyberpunkDropdown.DropdownEntry<>("STROBE", Component.literal("Strobe Flash"), null, 0xFFFFD700));
        effectTypes.add(new CyberpunkDropdown.DropdownEntry<>("HUE_SHIFT", Component.literal("Hue Shift"), null, 0xFF33CCFF));
        effectTypes.add(new CyberpunkDropdown.DropdownEntry<>("IMPACT_FRAME", Component.literal("Impact Frame (Action Shock)"), null, 0xFFFF2255));
        effectTypes.add(new CyberpunkDropdown.DropdownEntry<>("CINEMATIC_BARS", Component.literal("Cinematic Bars (Letterbox)"), null, 0xFFAA55FF));

        String curMode = "SCREEN_SHAKE";
        String effId = entry.getScreenEffectId();
        String sub = entry.getSubAction();
        if (sub != null && !sub.isBlank()) {
            curMode = sub.toUpperCase(Locale.ROOT);
        } else if (effId != null) {
            if (effId.contains("cinematic") || effId.contains("bars")) curMode = "CINEMATIC_BARS";
            else if (effId.contains("strobe")) curMode = "STROBE";
            else if (effId.contains("hue")) curMode = "HUE_SHIFT";
            else if (effId.contains("impact")) curMode = "IMPACT_FRAME";
            else if (effId.contains("invert")) curMode = "INVERT_COLORS";
            else curMode = "SCREEN_SHAKE";
        }

        int modeThemeClr = switch (curMode) {
            case "INVERT_COLORS" -> 0xFF00FFCC;
            case "STROBE" -> 0xFFFFD700;
            case "HUE_SHIFT" -> 0xFF33CCFF;
            case "IMPACT_FRAME" -> 0xFFFF2255;
            case "CINEMATIC_BARS" -> 0xFFAA55FF;
            default -> 0xFFFF00CC;
        };

        // 1. Mode Dropdown
        CyberpunkDropdown<String> effDropdown = createDropdown(
                curY, innerW, pad, Component.literal("Effect Mode")
        );
        effDropdown.setOptions(effectTypes);
        effDropdown.selectByValue(curMode);
        final String finalCurMode = curMode;
        effDropdown.setOnSelect(sel -> {
            entry.setScreenEffectId("fractured_utils:" + sel.getValue().toLowerCase(Locale.ROOT));
            entry.setSubAction(sel.getValue());
            parent.updateScreenEffectEntryCommand(entry);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewScreenEffects();
            rebuildWidgets();
        });
        curY += 24;

        // 2. Quick Cinematic Presets
        addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 10, Component.literal("QUICK PRESETS:"), TEXT_LABEL), curY);
        curY += 12;
        int presetBtnW = (innerW - 8) / 3;
        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, presetBtnW, 16, Component.literal("⚡ Lightn"), b -> {
            applyEffectPreset(entry, "LIGHTNING");
        }, 0xFFFF2255, false, Component.literal("Impact Frame DRAW 30FPS (White/Black)")), curY);
        addWidget(new CyberpunkButton(x + pad + presetBtnW + 4, y + 28 + curY, presetBtnW, 16, Component.literal("🚨 Siren"), b -> {
            applyEffectPreset(entry, "SIREN");
        }, 0xFFFFD700, false, Component.literal("Red Strobe Flash 8Hz")), curY);
        addWidget(new CyberpunkButton(x + pad + (presetBtnW * 2) + 8, y + 28 + curY, presetBtnW, 16, Component.literal("🎬 2.39:1"), b -> {
            applyEffectPreset(entry, "ANAMORPHIC");
        }, 0xFFAA55FF, false, Component.literal("Cinematic Letterbox Bars 12.5%")), curY);
        curY += 18;

        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, presetBtnW, 16, Component.literal("💥 Quake"), b -> {
            applyEffectPreset(entry, "QUAKE");
        }, 0xFFFF8800, false, Component.literal("Screen Shake 4.0 Amp")), curY);
        addWidget(new CyberpunkButton(x + pad + presetBtnW + 4, y + 28 + curY, presetBtnW, 16, Component.literal("🌈 Rainbow"), b -> {
            applyEffectPreset(entry, "RAINBOW");
        }, 0xFF00E5FF, false, Component.literal("Continuous Spectrum Hue Rotation")), curY);
        addWidget(new CyberpunkButton(x + pad + (presetBtnW * 2) + 8, y + 28 + curY, presetBtnW, 16, Component.literal("⚡ Invert"), b -> {
            applyEffectPreset(entry, "INVERT");
        }, 0xFF00FFCC, false, Component.literal("Solid Invert Flash")), curY);
        curY += 22;

        // 3. Hero Action: Live Client-Side Preview
        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, innerW, 20, Component.literal("▶ PREVIEW " + curMode), b -> {
            previewScreenEffect(entry);
        }, modeThemeClr, false, Component.literal("Trigger a real-time preview of this effect on your screen")), curY);
        curY += 26;

        // 4. Mode-Specific Detailed Parameter Controls
        if ("INVERT_COLORS".equalsIgnoreCase(curMode)) {
            // Invert Mode: Solid Invert vs Pulsating
            addWidget(new CyberpunkButton(x + pad, y + 28 + curY, innerW, 18,
                    Component.literal(entry.isScreenEffectPulse() ? "MODE: PULSATING" : "MODE: SOLID INVERT"),
                    b -> {
                        entry.setScreenEffectPulse(!entry.isScreenEffectPulse());
                        parent.updateScreenEffectEntryCommand(entry);
                        parent.saveCurrentSequenceToWorkingMap();
                        parent.updatePreviewScreenEffects();
                        rebuildWidgets();
                    }, entry.isScreenEffectPulse() ? 0xFF00FFCC : 0xFF8899AA, false, Component.literal("Toggle continuous inversion vs rhythmic pulses")), curY);
            curY += 22;

            if (entry.isScreenEffectPulse()) {
                int halfW = (innerW - 4) / 2;
                addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, halfW, 18, Component.literal("Pulse Freq (Hz):"), TEXT_LABEL), curY);
                EditBox freqBox = addWidget(new EditBox(font, x + pad + halfW + 4, y + 28 + curY, halfW, 18, Component.literal("Frequency")), curY);
                freqBox.setValue(String.format(Locale.US, "%.1f", entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 2.0f));
                freqBox.setResponder(v -> {
                    try {
                        entry.setScreenEffectFrequency(Float.parseFloat(v.trim()));
                        parent.updateScreenEffectEntryCommand(entry);
                        parent.saveCurrentSequenceToWorkingMap();
                        parent.updatePreviewScreenEffects();
                    } catch (Exception ignored) {}
                });
                curY += 22;
            }
        } else if ("STROBE".equalsIgnoreCase(curMode)) {
            // Wave shape toggle
            addWidget(new CyberpunkButton(x + pad, y + 28 + curY, innerW, 18,
                    Component.literal(entry.isScreenEffectSmooth() ? "WAVE: SMOOTH SINE" : "WAVE: HARD FLASH"),
                    b -> {
                        entry.setScreenEffectSmooth(!entry.isScreenEffectSmooth());
                        parent.updateScreenEffectEntryCommand(entry);
                        parent.saveCurrentSequenceToWorkingMap();
                        parent.updatePreviewScreenEffects();
                        rebuildWidgets();
                    }, entry.isScreenEffectSmooth() ? 0xFFFFD700 : 0xFF8899AA, false, Component.literal("Smooth sinusoidal pulse vs abrupt square flash")), curY);
            curY += 22;

            // Frequency (Hz) and Opacity
            int halfW = (innerW - 4) / 2;
            EditBox freqBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, halfW, 18, Component.literal("Speed (Hz)")), curY);
            freqBox.setValue(String.format(Locale.US, "%.1f", entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 10.0f));
            freqBox.setHint(Component.literal("Speed (Hz)"));
            freqBox.setResponder(v -> {
                try {
                    entry.setScreenEffectFrequency(Float.parseFloat(v.trim()));
                    parent.updateScreenEffectEntryCommand(entry);
                    parent.saveCurrentSequenceToWorkingMap();
                    parent.updatePreviewScreenEffects();
                } catch (Exception ignored) {}
            });

            EditBox alphaBox = addWidget(new EditBox(font, x + pad + halfW + 4, y + 28 + curY, halfW, 18, Component.literal("Opacity")), curY);
            float curA = entry.getScreenEffectMaxAlpha() > 0 ? entry.getScreenEffectMaxAlpha() : 0.85f;
            alphaBox.setValue(String.format(Locale.US, "%.2f", curA));
            alphaBox.setHint(Component.literal("Opacity (0-1)"));
            alphaBox.setResponder(v -> {
                try {
                    float a = Math.max(0.05f, Math.min(1.0f, Float.parseFloat(v.trim())));
                    entry.setScreenEffectMaxAlpha(a);
                    parent.updateScreenEffectEntryCommand(entry);
                    parent.saveCurrentSequenceToWorkingMap();
                    parent.updatePreviewScreenEffects();
                } catch (Exception ignored) {}
            });
            curY += 22;

            // Strobe Color: Hex Box + Interactive Cyberpunk Color Picker
            int curClr = entry.getScreenEffectColor() != 0 ? entry.getScreenEffectColor() : 0xFFFFFFFF;
            EditBox colorBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Hex Color")), curY);
            colorBox.setValue(String.format(Locale.ROOT, "#%06X", curClr & 0x00FFFFFF));
            colorBox.setHint(Component.literal("#FFFFFF"));
            curY += 22;

            CyberpunkColorPicker picker = addWidget(new CyberpunkColorPicker(x + pad, y + 28 + curY, innerW, curClr, c -> {
                entry.setScreenEffectColor(c);
                parent.updateScreenEffectEntryCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
                parent.updatePreviewScreenEffects();
            }), curY);
            picker.bindHexBox(colorBox);
            curY += 60;
        } else if ("HUE_SHIFT".equalsIgnoreCase(curMode)) {
            // Mode: Continuous spectrum rotation vs fixed angle
            addWidget(new CyberpunkButton(x + pad, y + 28 + curY, innerW, 18,
                    Component.literal(entry.isScreenEffectContinuous() ? "MODE: SPECTRUM CYCLE" : "MODE: FIXED ANGLE"),
                    b -> {
                        entry.setScreenEffectContinuous(!entry.isScreenEffectContinuous());
                        parent.updateScreenEffectEntryCommand(entry);
                        parent.saveCurrentSequenceToWorkingMap();
                        parent.updatePreviewScreenEffects();
                        rebuildWidgets();
                    }, entry.isScreenEffectContinuous() ? 0xFF00E5FF : 0xFFFFD700, false, Component.literal("Continuous spectrum rotation vs fixed hue angle")), curY);
            curY += 22;

            if (entry.isScreenEffectContinuous()) {
                int halfW = (innerW - 4) / 2;
                addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, halfW, 18, Component.literal("Speed (rot/s):"), TEXT_LABEL), curY);
                EditBox speedBox = addWidget(new EditBox(font, x + pad + halfW + 4, y + 28 + curY, halfW, 18, Component.literal("Speed")), curY);
                speedBox.setValue(String.format(Locale.US, "%.1f", entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 0.5f));
                speedBox.setResponder(v -> {
                    try {
                        entry.setScreenEffectFrequency(Float.parseFloat(v.trim()));
                        parent.updateScreenEffectEntryCommand(entry);
                        parent.saveCurrentSequenceToWorkingMap();
                        parent.updatePreviewScreenEffects();
                    } catch (Exception ignored) {}
                });
                curY += 22;
            } else {
                int btnW = (innerW - 8) / 3;
                addWidget(new CyberpunkButton(x + pad, y + 28 + curY, btnW, 18, Component.literal("90° (Warm)"), b -> {
                    entry.setScreenEffectAngle(90.0f);
                    parent.updateScreenEffectEntryCommand(entry);
                    parent.saveCurrentSequenceToWorkingMap();
                    parent.updatePreviewScreenEffects();
                }, 0xFF00E5FF, false, Component.literal("Warm / Alien hue rotation")), curY);

                addWidget(new CyberpunkButton(x + pad + btnW + 4, y + 28 + curY, btnW, 18, Component.literal("180° (Invert)"), b -> {
                    entry.setScreenEffectAngle(180.0f);
                    parent.updateScreenEffectEntryCommand(entry);
                    parent.saveCurrentSequenceToWorkingMap();
                    parent.updatePreviewScreenEffects();
                }, 0xFFFF00CC, false, Component.literal("180° complementary colors")), curY);

                addWidget(new CyberpunkButton(x + pad + (btnW * 2) + 8, y + 28 + curY, btnW, 18, Component.literal("270° (Cool)"), b -> {
                    entry.setScreenEffectAngle(270.0f);
                    parent.updateScreenEffectEntryCommand(entry);
                    parent.saveCurrentSequenceToWorkingMap();
                    parent.updatePreviewScreenEffects();
                }, 0xFFFFD700, false, Component.literal("Cool / Cyber hue rotation")), curY);
                curY += 22;
            }

            int halfW = (innerW - 4) / 2;
            addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, halfW, 18, Component.literal("Intensity:"), TEXT_LABEL), curY);
            EditBox intBox = addWidget(new EditBox(font, x + pad + halfW + 4, y + 28 + curY, halfW, 18, Component.literal("Intensity")), curY);
            intBox.setValue(String.format(Locale.US, "%.2f", entry.getScreenEffectIntensity() > 0 ? entry.getScreenEffectIntensity() : 1.0f));
            intBox.setResponder(v -> {
                try {
                    entry.setScreenEffectIntensity(Float.parseFloat(v.trim()));
                    parent.updateScreenEffectEntryCommand(entry);
                    parent.saveCurrentSequenceToWorkingMap();
                    parent.updatePreviewScreenEffects();
                } catch (Exception ignored) {}
            });
            curY += 22;
        } else if ("IMPACT_FRAME".equalsIgnoreCase(curMode)) {
            // Style Cycle Button
            String curStyle = entry.getScreenEffectStyle() != null && !entry.getScreenEffectStyle().isBlank() ? entry.getScreenEffectStyle() : "NOIR";
            addWidget(new CyberpunkButton(x + pad, y + 28 + curY, innerW, 18,
                    Component.literal("STYLE: " + curStyle),
                    b -> {
                        String next = switch (curStyle.toUpperCase(Locale.ROOT)) {
                            case "NOIR" -> "CHROMATIC";
                            case "CHROMATIC" -> "THERMAL";
                            case "THERMAL" -> "HYBRID";
                            case "HYBRID" -> "DRAW";
                            default -> "NOIR";
                        };
                        entry.setScreenEffectStyle(next);
                        parent.updateScreenEffectEntryCommand(entry);
                        parent.saveCurrentSequenceToWorkingMap();
                        parent.updatePreviewScreenEffects();
                        rebuildWidgets();
                    }, 0xFFFF2255, false, Component.literal("Cycle impact style: Noir (Inversion), Chromatic Burst, Thermal Speedlines, Hybrid, Draw")), curY);
            curY += 22;

            int halfW = (innerW - 4) / 2;
            // Negative cut toggle
            addWidget(new CyberpunkButton(x + pad, y + 28 + curY, halfW, 18,
                    Component.literal(entry.isScreenEffectPulse() ? "INVERT: YES" : "INVERT: NO"),
                    b -> {
                        entry.setScreenEffectPulse(!entry.isScreenEffectPulse());
                        parent.updateScreenEffectEntryCommand(entry);
                        parent.saveCurrentSequenceToWorkingMap();
                        parent.updatePreviewScreenEffects();
                        rebuildWidgets();
                    }, entry.isScreenEffectPulse() ? 0xFF00FFCC : 0xFF8899AA, false, Component.literal("Toggle negative/inverted cut flashes")), curY);

            // Cut FPS
            EditBox fpsBox = addWidget(new EditBox(font, x + pad + halfW + 4, y + 28 + curY, halfW, 18, Component.literal("Cut FPS")), curY);
            fpsBox.setValue(String.format(Locale.US, "%.0f", entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 28.0f));
            fpsBox.setHint(Component.literal("Cut FPS"));
            fpsBox.setResponder(v -> {
                try {
                    entry.setScreenEffectFrequency(Float.parseFloat(v.trim()));
                    parent.updateScreenEffectEntryCommand(entry);
                    parent.saveCurrentSequenceToWorkingMap();
                    parent.updatePreviewScreenEffects();
                } catch (Exception ignored) {}
            });
            curY += 22;

            // Color Target Dual Selection: Paper vs Ink
            int pColor = entry.getScreenEffectColor() != 0 ? entry.getScreenEffectColor() : 0xFFFFFFFF;
            int sColor = entry.getScreenEffectSecondaryColor() != 0 ? entry.getScreenEffectSecondaryColor() : 0xFF000000;

            addWidget(new CyberpunkButton(x + pad, y + 28 + curY, halfW, 18,
                    Component.literal("📄 PAPER COLOR"),
                    b -> {
                        impactEditingSecondary = false;
                        rebuildWidgets();
                    }, !impactEditingSecondary ? 0xFF00E5FF : 0xFF445566, false,
                    Component.literal("Edit paper/burst cut color")), curY);

            addWidget(new CyberpunkButton(x + pad + halfW + 4, y + 28 + curY, halfW, 18,
                    Component.literal("🖋️ INK COLOR"),
                    b -> {
                        impactEditingSecondary = true;
                        rebuildWidgets();
                    }, impactEditingSecondary ? 0xFFFF00CC : 0xFF445566, false,
                    Component.literal("Edit ink/shadow background color")), curY);
            curY += 22;

            int activeColor = impactEditingSecondary ? sColor : pColor;
            EditBox colorBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Hex Color")), curY);
            colorBox.setValue(String.format(Locale.ROOT, "#%06X", activeColor & 0x00FFFFFF));
            colorBox.setHint(Component.literal("#FFFFFF"));
            curY += 22;

            CyberpunkColorPicker picker = addWidget(new CyberpunkColorPicker(x + pad, y + 28 + curY, innerW, activeColor, c -> {
                if (impactEditingSecondary) {
                    entry.setScreenEffectSecondaryColor(c);
                } else {
                    entry.setScreenEffectColor(c);
                }
                parent.updateScreenEffectEntryCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
                parent.updatePreviewScreenEffects();
            }), curY);
            picker.bindHexBox(colorBox);
            curY += 60;
        } else if ("CINEMATIC_BARS".equalsIgnoreCase(curMode)) {
            // Letterbox Bar Height %
            float curH = entry.getScreenEffectBarHeight() > 0 ? entry.getScreenEffectBarHeight() * 100.0f : 12.5f;
            addWidget(new CyberpunkSlider(x + pad, y + 28 + curY, innerW, 18, "Bar Height", (curH - 2.0) / 33.0, val -> {
                if (isUpdatingFields) return;
                float pct = (float) (2.0 + (val * 33.0));
                entry.setScreenEffectBarHeight(pct / 100.0f);
                parent.updateScreenEffectEntryCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
                parent.updatePreviewScreenEffects();
            }, v -> String.format(Locale.US, "Height: %.1f%%", 2.0 + (v * 33.0))), curY);
            curY += 22;

            // SlideIn / SlideOut (ms)
            int halfW = (innerW - 4) / 2;
            EditBox inBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, halfW, 18, Component.literal("SlideIn")), curY);
            inBox.setValue(String.valueOf(entry.getScreenEffectTransitionInMs() > 0 ? entry.getScreenEffectTransitionInMs() : 500));
            inBox.setHint(Component.literal("In (ms)"));
            inBox.setResponder(v -> {
                try {
                    entry.setScreenEffectTransitionInMs(Integer.parseInt(v.trim()));
                    parent.updateScreenEffectEntryCommand(entry);
                    parent.saveCurrentSequenceToWorkingMap();
                    parent.updatePreviewScreenEffects();
                } catch (Exception ignored) {}
            });

            EditBox outBox = addWidget(new EditBox(font, x + pad + halfW + 4, y + 28 + curY, halfW, 18, Component.literal("SlideOut")), curY);
            outBox.setValue(String.valueOf(entry.getScreenEffectTransitionOutMs() > 0 ? entry.getScreenEffectTransitionOutMs() : 500));
            outBox.setHint(Component.literal("Out (ms)"));
            outBox.setResponder(v -> {
                try {
                    entry.setScreenEffectTransitionOutMs(Integer.parseInt(v.trim()));
                    parent.updateScreenEffectEntryCommand(entry);
                    parent.saveCurrentSequenceToWorkingMap();
                    parent.updatePreviewScreenEffects();
                } catch (Exception ignored) {}
            });
            curY += 24;

            // Bar Color: Hex Box + Cyberpunk Color Picker
            int curClr = entry.getScreenEffectColor() != 0 ? entry.getScreenEffectColor() : 0xFF000000;
            EditBox colorBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Bar Color")), curY);
            colorBox.setValue(String.format(Locale.ROOT, "#%06X", curClr & 0x00FFFFFF));
            colorBox.setHint(Component.literal("#000000"));
            curY += 22;

            CyberpunkColorPicker picker = addWidget(new CyberpunkColorPicker(x + pad, y + 28 + curY, innerW, curClr, c -> {
                entry.setScreenEffectColor(c);
                parent.updateScreenEffectEntryCommand(entry);
                parent.saveCurrentSequenceToWorkingMap();
                parent.updatePreviewScreenEffects();
            }), curY);
            picker.bindHexBox(colorBox);
            curY += 60;
        } else {
            // SCREEN_SHAKE (Default)
            int halfW = (innerW - 4) / 2;
            addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, halfW, 18, Component.literal("Intensity / Amp:"), TEXT_LABEL), curY);
            EditBox intBox = addWidget(new EditBox(font, x + pad + halfW + 4, y + 28 + curY, halfW, 18, Component.literal("Intensity")), curY);
            intBox.setValue(String.format(Locale.US, "%.1f", entry.getScreenEffectIntensity() > 0 ? entry.getScreenEffectIntensity() : 2.0f));
            intBox.setResponder(v -> {
                try {
                    entry.setScreenEffectIntensity(Float.parseFloat(v.trim()));
                    parent.updateScreenEffectEntryCommand(entry);
                    parent.saveCurrentSequenceToWorkingMap();
                    parent.updatePreviewScreenEffects();
                } catch (Exception ignored) {}
            });
            curY += 22;

            // Quick Intensity Presets (1.0, 2.0, 3.5, 5.0)
            int qW = (innerW - 12) / 4;
            float[] presets = {1.0f, 2.0f, 3.5f, 5.0f};
            for (int i = 0; i < 4; i++) {
                final float pVal = presets[i];
                addWidget(new CyberpunkButton(x + pad + (i * (qW + 4)), y + 28 + curY, qW, 16, Component.literal(String.format(Locale.US, "%.1f", pVal)), b -> {
                    entry.setScreenEffectIntensity(pVal);
                    intBox.setValue(String.format(Locale.US, "%.1f", pVal));
                    parent.updateScreenEffectEntryCommand(entry);
                    parent.saveCurrentSequenceToWorkingMap();
                    parent.updatePreviewScreenEffects();
                }, 0xFF00E5FF, false, Component.literal("Set intensity to " + pVal)), curY);
            }
            curY += 20;

            addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, halfW, 18, Component.literal("Frequency (Hz):"), TEXT_LABEL), curY);
            EditBox freqBox = addWidget(new EditBox(font, x + pad + halfW + 4, y + 28 + curY, halfW, 18, Component.literal("Frequency")), curY);
            freqBox.setValue(String.format(Locale.US, "%.0f", entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 20.0f));
            freqBox.setResponder(v -> {
                try {
                    entry.setScreenEffectFrequency(Float.parseFloat(v.trim()));
                    parent.updateScreenEffectEntryCommand(entry);
                    parent.saveCurrentSequenceToWorkingMap();
                    parent.updatePreviewScreenEffects();
                } catch (Exception ignored) {}
            });
            curY += 22;

            addWidget(new CyberpunkButton(x + pad, y + 28 + curY, innerW, 18,
                    Component.literal(entry.isScreenEffectDecay() ? "DECAY: SMOOTH" : "DECAY: CONSTANT"),
                    b -> {
                        entry.setScreenEffectDecay(!entry.isScreenEffectDecay());
                        parent.updateScreenEffectEntryCommand(entry);
                        parent.saveCurrentSequenceToWorkingMap();
                        parent.updatePreviewScreenEffects();
                        rebuildWidgets();
                    }, entry.isScreenEffectDecay() ? 0xFF00FF88 : 0xFF8899AA, false, Component.literal("Taper shake amplitude towards end of duration")), curY);
            curY += 22;
        }

        // Reset to Defaults Button
        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, innerW, 18,
                Component.literal("↺ RESET TO DEFAULTS"),
                b -> {
                    resetScreenEffectDefaults(entry, finalCurMode);
                    parent.updateScreenEffectEntryCommand(entry);
                    parent.saveCurrentSequenceToWorkingMap();
                    parent.updatePreviewScreenEffects();
                    rebuildWidgets();
                }, 0xFF8899AA, false, Component.literal("Reset this effect's parameters to default factory values")), curY);
        curY += 24;

        return curY;
    }

    private void resetScreenEffectDefaults(MusicSequenceEntry entry, String mode) {
        if (entry == null) return;
        if ("INVERT_COLORS".equalsIgnoreCase(mode)) {
            entry.setScreenEffectPulse(false);
            entry.setScreenEffectFrequency(2.0f);
        } else if ("STROBE".equalsIgnoreCase(mode)) {
            entry.setScreenEffectFrequency(10.0f);
            entry.setScreenEffectMaxAlpha(0.85f);
            entry.setScreenEffectColor(0xFFFFFFFF);
            entry.setScreenEffectSmooth(false);
        } else if ("HUE_SHIFT".equalsIgnoreCase(mode)) {
            entry.setScreenEffectContinuous(true);
            entry.setScreenEffectFrequency(0.5f);
            entry.setScreenEffectAngle(0.0f);
            entry.setScreenEffectIntensity(1.0f);
        } else if ("IMPACT_FRAME".equalsIgnoreCase(mode)) {
            entry.setScreenEffectStyle("DRAW");
            entry.setScreenEffectPulse(true);
            entry.setScreenEffectFrequency(28.0f);
            entry.setScreenEffectColor(0xFFFFFFFF);
            entry.setScreenEffectSecondaryColor(0xFF000000);
        } else if ("CINEMATIC_BARS".equalsIgnoreCase(mode)) {
            entry.setScreenEffectBarHeight(0.125f);
            entry.setScreenEffectTransitionInMs(500);
            entry.setScreenEffectTransitionOutMs(500);
            entry.setScreenEffectColor(0xFF000000);
        } else {
            // SCREEN_SHAKE
            entry.setScreenEffectIntensity(2.0f);
            entry.setScreenEffectFrequency(20.0f);
            entry.setScreenEffectDecay(true);
        }
    }

    private void applyEffectPreset(MusicSequenceEntry entry, String preset) {
        if (entry == null) return;
        if ("LIGHTNING".equalsIgnoreCase(preset)) {
            entry.setScreenEffectId("fractured_utils:impact_frame");
            entry.setSubAction("IMPACT_FRAME");
            entry.setScreenEffectStyle("DRAW");
            entry.setScreenEffectPulse(true);
            entry.setScreenEffectFrequency(30.0f);
            entry.setScreenEffectColor(0xFFFFFFFF);
            entry.setScreenEffectSecondaryColor(0xFF000000);
            if (entry.getDurationMs() <= 0) entry.setDurationMs(300);
        } else if ("SIREN".equalsIgnoreCase(preset)) {
            entry.setScreenEffectId("fractured_utils:strobe");
            entry.setSubAction("STROBE");
            entry.setScreenEffectSmooth(false);
            entry.setScreenEffectFrequency(8.0f);
            entry.setScreenEffectMaxAlpha(0.90f);
            entry.setScreenEffectColor(0xFFFF0055);
            if (entry.getDurationMs() <= 0) entry.setDurationMs(1200);
        } else if ("ANAMORPHIC".equalsIgnoreCase(preset)) {
            entry.setScreenEffectId("fractured_utils:cinematic_bars");
            entry.setSubAction("CINEMATIC_BARS");
            entry.setScreenEffectBarHeight(0.125f);
            entry.setScreenEffectTransitionInMs(400);
            entry.setScreenEffectTransitionOutMs(400);
            entry.setScreenEffectColor(0xFF000000);
            if (entry.getDurationMs() <= 0) entry.setDurationMs(2500);
        } else if ("QUAKE".equalsIgnoreCase(preset)) {
            entry.setScreenEffectId("fractured_utils:screen_shake");
            entry.setSubAction("SCREEN_SHAKE");
            entry.setScreenEffectIntensity(4.0f);
            entry.setScreenEffectFrequency(24.0f);
            entry.setScreenEffectDecay(true);
            if (entry.getDurationMs() <= 0) entry.setDurationMs(1200);
        } else if ("RAINBOW".equalsIgnoreCase(preset)) {
            entry.setScreenEffectId("fractured_utils:hue_shift");
            entry.setSubAction("HUE_SHIFT");
            entry.setScreenEffectContinuous(true);
            entry.setScreenEffectFrequency(0.5f);
            entry.setScreenEffectIntensity(1.0f);
            if (entry.getDurationMs() <= 0) entry.setDurationMs(2000);
        } else if ("INVERT".equalsIgnoreCase(preset)) {
            entry.setScreenEffectId("fractured_utils:invert_colors");
            entry.setSubAction("INVERT_COLORS");
            entry.setScreenEffectPulse(false);
            if (entry.getDurationMs() <= 0) entry.setDurationMs(250);
        }
        parent.updateScreenEffectEntryCommand(entry);
        parent.saveCurrentSequenceToWorkingMap();
        parent.updatePreviewScreenEffects();
        rebuildWidgets();
    }

    private void previewScreenEffect(MusicSequenceEntry entry) {
        if (entry == null) return;
        int duration = entry.getTotalDurationMs() > 0 ? entry.getTotalDurationMs() : (entry.getDurationMs() > 0 ? entry.getDurationMs() : 1000);
        String mode = entry.getSubAction() != null && !entry.getSubAction().isBlank() ? entry.getSubAction().toUpperCase(Locale.ROOT) : "SCREEN_SHAKE";
        ScreenEffectInstance inst;
        if ("INVERT_COLORS".equalsIgnoreCase(mode)) {
            inst = new InvertColorsEffect.InvertColorsInstance(duration, entry.isScreenEffectPulse(), entry.getScreenEffectFrequency());
        } else if ("STROBE".equalsIgnoreCase(mode)) {
            float freq = entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 10.0f;
            int clr = entry.getScreenEffectColor() != 0 ? entry.getScreenEffectColor() : 0xFFFFFFFF;
            float alpha = entry.getScreenEffectMaxAlpha() > 0 ? entry.getScreenEffectMaxAlpha() : 0.85f;
            inst = new StrobeEffect.StrobeInstance(duration, freq, clr, entry.isScreenEffectSmooth(), alpha);
        } else if ("HUE_SHIFT".equalsIgnoreCase(mode)) {
            float freq = entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 0.5f;
            float intensity = entry.getScreenEffectIntensity() > 0 ? entry.getScreenEffectIntensity() : 1.0f;
            inst = new HueShiftEffect.HueShiftInstance(duration, freq, entry.isScreenEffectContinuous(), entry.getScreenEffectAngle(), intensity);
        } else if ("IMPACT_FRAME".equalsIgnoreCase(mode)) {
            int pColor = entry.getScreenEffectColor() != 0 ? entry.getScreenEffectColor() : 0xFFFFFFFF;
            int sColor = entry.getScreenEffectSecondaryColor() != 0 ? entry.getScreenEffectSecondaryColor() : 0xFF000000;
            int interval = entry.getScreenEffectFrequency() > 0 ? (int)(1000.0f / entry.getScreenEffectFrequency()) : 35;
            String style = entry.getScreenEffectStyle() != null && !entry.getScreenEffectStyle().isBlank() ? entry.getScreenEffectStyle() : "NOIR";
            inst = new ImpactFrameEffect.ImpactFrameInstance(duration, pColor, sColor, interval, entry.isScreenEffectPulse(), style);
        } else if ("CINEMATIC_BARS".equalsIgnoreCase(mode)) {
            int inMs = entry.getScreenEffectTransitionInMs() > 0 ? entry.getScreenEffectTransitionInMs() : 500;
            int outMs = entry.getScreenEffectTransitionOutMs() > 0 ? entry.getScreenEffectTransitionOutMs() : 500;
            float barH = entry.getScreenEffectBarHeight() > 0 ? entry.getScreenEffectBarHeight() : 0.125f;
            int clr = entry.getScreenEffectColor() != 0 ? entry.getScreenEffectColor() : 0xFF000000;
            inst = new CinematicBarsEffect.CinematicBarsInstance(duration, inMs, outMs, barH, clr);
        } else {
            float intensity = entry.getScreenEffectIntensity() > 0 ? entry.getScreenEffectIntensity() : 2.0f;
            float freq = entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 20.0f;
            inst = new ScreenShakeEffect.ScreenShakeInstance(duration, intensity, freq, entry.isScreenEffectDecay());
        }
        ClientScreenEffectHandler.playEffect(inst);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
    }

    // =========================================================================
    // 6. SOUND & COMMAND CONTROLS
    // =========================================================================

    private int buildSoundControls(Font font, MusicSequenceEntry entry, int pad, int innerW, int curY) {
        int playW = 48;
        EditBox sndBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW - playW - 4, 18, Component.literal("Sound Event")), curY);
        sndBox.setMaxLength(256);
        sndBox.setValue(entry.getCommand() != null ? entry.getCommand() : "minecraft:entity.experience_orb.pickup");
        sndBox.setHint(Component.literal("Sound event ID"));
        sndBox.setResponder(val -> {
            if (isUpdatingFields) return;
            entry.setCommand(val.trim());
            parent.saveCurrentSequenceToWorkingMap();
        });

        addWidget(new CyberpunkButton(x + pad + innerW - playW, y + 28 + curY, playW, 18, Component.literal("▶ PLAY"), b -> {
            previewSound(sndBox.getValue(), 1.0f, 1.0f);
        }, CYAN_MAIN, false, Component.literal("Play sound preview")), curY);
        curY += 24;

        return curY;
    }

    private int buildCommandControls(Font font, MusicSequenceEntry entry, int pad, int innerW, int curY) {
        String blockType = entry.getSubAction();
        if (blockType == null || blockType.isBlank()) {
            blockType = "IMPULSE";
        }
        blockType = blockType.toUpperCase(Locale.ROOT);
        if (!blockType.equals("IMPULSE") && !blockType.equals("CHAIN") && !blockType.equals("REPEAT")) {
            blockType = "IMPULSE";
        }

        int blockAccent = switch (blockType) {
            case "CHAIN" -> 0xFF38B580;
            case "REPEAT" -> 0xFFA26BE8;
            default -> 0xFFE07A48;
        };

        // 1. Command Block Chassis Header with authentic icon and live status
        addWidget(new CommandBlockChassisWidget(x + pad, y + 28 + curY, innerW, 28, blockType, blockAccent), curY);
        curY += 32;

        // 2. Block Type Segmented Switcher (IMPULSE, CHAIN, REPEAT)
        int thirdW = (innerW - 8) / 3;
        int thirdW3 = innerW - (thirdW * 2) - 8;
        boolean isImpulse = "IMPULSE".equals(blockType);
        boolean isChain = "CHAIN".equals(blockType);
        boolean isRepeat = "REPEAT".equals(blockType);

        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, thirdW, 20, Component.literal("🟧 IMPULSE"), b -> {
            entry.setSubAction("IMPULSE");
            parent.saveCurrentSequenceToWorkingMap();
            rebuildWidgets();
        }, isImpulse ? 0xFFE07A48 : 0xFF7A6555, isImpulse, Component.literal("Impulse: Runs once on keyframe timestamp")), curY);

        addWidget(new CyberpunkButton(x + pad + thirdW + 4, y + 28 + curY, thirdW, 20, Component.literal("🟩 CHAIN"), b -> {
            entry.setSubAction("CHAIN");
            parent.saveCurrentSequenceToWorkingMap();
            rebuildWidgets();
        }, isChain ? 0xFF38B580 : 0xFF557A68, isChain, Component.literal("Chain: Runs sequentially in sequence pipeline")), curY);

        addWidget(new CyberpunkButton(x + pad + (thirdW * 2) + 8, y + 28 + curY, thirdW3, 20, Component.literal("🟪 REPEAT"), b -> {
            entry.setSubAction("REPEAT");
            parent.saveCurrentSequenceToWorkingMap();
            rebuildWidgets();
        }, isRepeat ? 0xFFA26BE8 : 0xFF6C557A, isRepeat, Component.literal("Repeat: Loops continuously throughout keyframe duration")), curY);
        curY += 24;

        // 3. Condition & Execution Context Mode (UNCONDITIONAL vs CONDITIONAL, SERVER vs PLAYER)
        int halfW = (innerW - 4) / 2;
        int remHalfW = innerW - halfW - 4;

        boolean isConditional = "true".equalsIgnoreCase(entry.getPuppetParam("conditional", "false"));
        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, halfW, 18, Component.literal(isConditional ? "⚡ CONDITIONAL" : "⚡ UNCONDITIONAL"), b -> {
            entry.putPuppetParam("conditional", isConditional ? "false" : "true");
            parent.saveCurrentSequenceToWorkingMap();
            rebuildWidgets();
        }, isConditional ? 0xFFFFD700 : 0xFF8899AA, isConditional, Component.literal(isConditional ? "Only executes if preceding command succeeded" : "Always executes without condition")), curY);

        boolean isPlayerRun = "player".equalsIgnoreCase(entry.getPuppetParam("run_as", "server"));
        addWidget(new CyberpunkButton(x + pad + halfW + 4, y + 28 + curY, remHalfW, 18, Component.literal(isPlayerRun ? "👤 RUN AS @p" : "🌐 RUN AS SERVER"), b -> {
            entry.putPuppetParam("run_as", isPlayerRun ? "server" : "player");
            parent.saveCurrentSequenceToWorkingMap();
            rebuildWidgets();
        }, isPlayerRun ? 0xFF00FF88 : CYAN_MAIN, false, Component.literal(isPlayerRun ? "Executes command with player permissions" : "Executes with server console operator permissions")), curY);
        curY += 26;

        // 4. Command Console Terminal Textbox with Autocomplete
        addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 14, Component.literal("✦ CONSOLE COMMAND"), blockAccent), curY);
        curY += 16;

        // Command Prompt Chevron Button + Input EditBox
        int promptW = 20;
        int cmdBoxW = innerW - promptW - 4;

        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, promptW, 20, Component.literal("/>"), b -> {
            if (activeCommandBox != null) {
                String val = activeCommandBox.getValue();
                if (!val.startsWith("/")) {
                    activeCommandBox.setValue("/" + val);
                }
                activeCommandBox.moveCursorToEnd();
                if (activeCommandSuggestions != null) {
                    activeCommandSuggestions.updateCommandInfo();
                }
            }
        }, blockAccent, false, Component.literal("Ensure leading slash (/)")), curY);

        EditBox cmdBox = addWidget(new EditBox(font, x + pad + promptW + 4, y + 28 + curY, cmdBoxW, 20, Component.literal("Command")), curY);
        cmdBox.setMaxLength(2048);
        cmdBox.setValue(entry.getCommand() != null ? entry.getCommand() : "");
        cmdBox.setHint(Component.literal("/say, /title, /particle, /playsound..."));
        this.activeCommandBox = cmdBox;

        this.activeCommandSuggestions = new CommandSuggestions(
                Minecraft.getInstance(),
                parent,
                cmdBox,
                font,
                true,   // commandsOnly
                true,   // onlyShowIfCursorPastError
                0,      // minLinesCount
                7,      // maxLinesCount
                false,  // anchorToBottom
                0xEE12100E // Command Block slate background
        );
        this.activeCommandSuggestions.setAllowSuggestions(true);
        this.activeCommandSuggestions.updateCommandInfo();

        cmdBox.setResponder(val -> {
            if (isUpdatingFields) return;
            entry.setCommand(val);
            parent.saveCurrentSequenceToWorkingMap();
            if (this.activeCommandSuggestions != null) {
                this.activeCommandSuggestions.updateCommandInfo();
            }
        });
        curY += 26;

        // 5. Target Selectors Bar (@p, @a, @s, @e, @r)
        addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 12, Component.literal("✦ TARGET SELECTORS"), 0xFFAABBCC), curY);
        curY += 14;

        int tokW = (innerW - 16) / 5;
        String[] tokens = new String[]{"@p", "@a", "@s", "@e", "@r"};
        String[] tokenTips = new String[]{
                "Nearest Player (@p)",
                "All Players (@a)",
                "Executing Entity (@s)",
                "All Entities (@e)",
                "Random Player (@r)"
        };
        for (int i = 0; i < tokens.length; i++) {
            String tok = tokens[i];
            String tip = tokenTips[i];
            int tx = x + pad + i * (tokW + 4);
            int tw = (i == 4) ? (innerW - (tokW * 4) - 16) : tokW;
            addWidget(new CyberpunkButton(tx, y + 28 + curY, tw, 16, Component.literal(tok), b -> {
                insertCommandToken(cmdBox, tok);
            }, 0xFF356A88, false, Component.literal("Insert " + tip)), curY);
        }
        curY += 22;

        // 6. Cinematic Command Starter Templates
        addWidget(new CyberpunkLabel(x + pad, y + 28 + curY, innerW, 12, Component.literal("✦ CINEMATIC SNIPPETS"), 0xFFAABBCC), curY);
        curY += 14;

        // Row 1 of snippets
        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, thirdW, 16, Component.literal("✦ /title"), b -> {
            applyCommandSnippet(cmdBox, "/title @a actionbar {\"text\":\"Cinematic Keyframe Triggered\",\"color\":\"gold\"}");
        }, 0xFF285570, false, Component.literal("Actionbar title announcement")), curY);

        addWidget(new CyberpunkButton(x + pad + thirdW + 4, y + 28 + curY, thirdW, 16, Component.literal("✦ /playsound"), b -> {
            applyCommandSnippet(cmdBox, "/playsound minecraft:ui.toast.challenge_complete master @a ~ ~ ~ 1.0 1.0");
        }, 0xFF285570, false, Component.literal("Play sound at player coordinates")), curY);

        addWidget(new CyberpunkButton(x + pad + (thirdW * 2) + 8, y + 28 + curY, thirdW3, 16, Component.literal("✦ /particle"), b -> {
            applyCommandSnippet(cmdBox, "/particle minecraft:end_rod ~ ~1 ~ 0.5 0.5 0.5 0.05 40");
        }, 0xFF285570, false, Component.literal("Spawn particle burst")), curY);
        curY += 18;

        // Row 2 of snippets
        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, thirdW, 16, Component.literal("✦ /effect"), b -> {
            applyCommandSnippet(cmdBox, "/effect give @a minecraft:slowness 3 1 true");
        }, 0xFF285570, false, Component.literal("Apply status effect")), curY);

        addWidget(new CyberpunkButton(x + pad + thirdW + 4, y + 28 + curY, thirdW, 16, Component.literal("✦ /weather"), b -> {
            applyCommandSnippet(cmdBox, "/weather thunder");
        }, 0xFF285570, false, Component.literal("Change weather condition")), curY);

        addWidget(new CyberpunkButton(x + pad + (thirdW * 2) + 8, y + 28 + curY, thirdW3, 16, Component.literal("✦ /summon"), b -> {
            applyCommandSnippet(cmdBox, "/summon minecraft:lightning_bolt ~ ~ ~");
        }, 0xFF285570, false, Component.literal("Summon entity or strike")), curY);
        curY += 24;

        // 7. Test Run Command & Diagnostic Previous Output Console
        addWidget(new CyberpunkButton(x + pad, y + 28 + curY, innerW, 18, Component.literal("⚡ TEST RUN IN CHAT / CONSOLE"), b -> {
            testRunCommand(cmdBox);
        }, blockAccent, false, Component.literal("Dispatch command now to test syntax & permissions")), curY);
        curY += 22;

        addWidget(new CommandOutputConsoleWidget(x + pad, y + 28 + curY, innerW, 20, () -> lastCommandExecutionStatus, blockAccent), curY);
        curY += 26;

        return curY;
    }

    private void insertCommandToken(EditBox cmdBox, String token) {
        if (cmdBox == null) return;
        String current = cmdBox.getValue();
        if (!current.isEmpty() && !current.endsWith(" ")) {
            cmdBox.setValue(current + " " + token);
        } else {
            cmdBox.setValue(current + token);
        }
        cmdBox.moveCursorToEnd();
        if (activeCommandSuggestions != null) {
            activeCommandSuggestions.updateCommandInfo();
        }
        parent.saveCurrentSequenceToWorkingMap();
    }

    private void applyCommandSnippet(EditBox cmdBox, String template) {
        if (cmdBox == null) return;
        cmdBox.setValue(template);
        cmdBox.moveCursorToEnd();
        if (activeCommandSuggestions != null) {
            activeCommandSuggestions.updateCommandInfo();
        }
        parent.saveCurrentSequenceToWorkingMap();
    }

    private void testRunCommand(EditBox cmdBox) {
        if (cmdBox == null) return;
        String cmd = cmdBox.getValue().trim();
        if (cmd.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) {
            try {
                if (cmd.startsWith("/")) {
                    mc.player.connection.sendUnsignedCommand(cmd.substring(1));
                } else {
                    mc.player.connection.sendChat(cmd);
                }
                String shortCmd = cmd.length() > 28 ? cmd.substring(0, 28) + "..." : cmd;
                this.lastCommandExecutionStatus = "✔ [Executed]: " + shortCmd;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.4f));
            } catch (Exception ex) {
                this.lastCommandExecutionStatus = "✕ [Error]: " + ex.getMessage();
            }
        } else {
            this.lastCommandExecutionStatus = "⚠ [Warning]: Player not connected";
        }
    }

    // =========================================================================
    // 7. CHANNEL TAB & SEQUENCE TAB
    // =========================================================================

    private void rebuildChannelWidgets(Font font, int pad, int innerW) {
        MusicSequenceChannel channel = parent.getSelectedChannel();
        int curY = 8;

        if (channel != null) {
            EditBox nameBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Track Name")), curY);
            nameBox.setValue(channel.getName() != null ? channel.getName() : "");
            nameBox.setHint(Component.literal("Track Name"));
            nameBox.setResponder(val -> {
                if (isUpdatingFields) return;
                channel.setName(val.trim());
                parent.saveCurrentSequenceToWorkingMap();
            });
            curY += 24;

            if (MusicSequenceChannel.TYPE_PUPPET.equalsIgnoreCase(channel.getType())) {
                EditBox actorTagBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("Actor Tag")), curY);
                actorTagBox.setValue(channel.getActorTag() != null ? channel.getActorTag() : "");
                actorTagBox.setHint(Component.literal("Actor Tag (#hero, #boss)"));
                actorTagBox.setResponder(val -> {
                    if (isUpdatingFields) return;
                    channel.setActorTag(val.trim());
                    parent.saveCurrentSequenceToWorkingMap();
                });
                curY += 24;

                addWidget(new CyberpunkButton(x + pad, y + 28 + curY, innerW, 18, Component.literal("+ ADD SUB-CHANNEL"), b -> {
                    parent.addSubChannel(channel);
                }, CYAN_MAIN, false, Component.literal("Create a synchronized action sub-track for this puppet entity")), curY);
                curY += 24;
            }

            addWidget(new CyberpunkButton(x + pad, y + 28 + curY, innerW, 20, Component.literal("DELETE CHANNEL"), b -> {
                parent.openConfirmDeleteChannelModal(channel);
            }, RED_DELETE, false, Component.literal("Permanently delete this track and its keyframes")), curY);
            curY += 28;
        }

        this.contentHeight = curY + 40;
    }

    private void rebuildSequenceWidgets(Font font, int pad, int innerW) {
        MusicSequence seq = parent.getCurrentSequence();
        int curY = 8;

        if (seq != null) {
            EditBox bpmBox = addWidget(new EditBox(font, x + pad, y + 28 + curY, innerW, 18, Component.literal("BPM")), curY);
            bpmBox.setValue(String.valueOf(seq.getBpm()));
            bpmBox.setHint(Component.literal("Song Tempo BPM"));
            bpmBox.setResponder(val -> {
                if (isUpdatingFields) return;
                try {
                    int b = Integer.parseInt(val.trim());
                    if (b >= 20 && b <= 400) {
                        seq.setBpm(b);
                        parent.saveCurrentSequenceToWorkingMap();
                    }
                } catch (Exception ignored) {}
            });
            curY += 24;

            addWidget(new CyberpunkCheckbox(x + pad, y + 28 + curY, innerW, 16, Component.literal("Loop Sequence Playback"), seq.isLooping(), chk -> {
                seq.setLooping(chk);
                parent.saveCurrentSequenceToWorkingMap();
            }), curY);
            curY += 22;
        }

        this.contentHeight = curY + 40;
    }

    // =========================================================================
    // 8. HELPERS & RENDERING
    // =========================================================================

    private void parseDouble(String str, Consumer<Double> consumer) {
        if (isUpdatingFields) return;
        try {
            double v = Double.parseDouble(str.trim().replace(",", "."));
            consumer.accept(v);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
        } catch (Exception ignored) {}
    }

    private void parseFloat(String str, Consumer<Float> consumer) {
        if (isUpdatingFields) return;
        try {
            float v = Float.parseFloat(str.trim().replace(",", "."));
            consumer.accept(v);
            parent.saveCurrentSequenceToWorkingMap();
            parent.updatePreviewCamera();
        } catch (Exception ignored) {}
    }

    private void previewSound(String soundId, float volume, float pitch) {
        if (soundId == null || soundId.isBlank()) return;
        try {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(soundId.trim());
            if (rl != null) {
                net.minecraft.sounds.SoundEvent se = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(rl);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(se, pitch, volume));
            }
        } catch (Exception ignored) {}
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Panel Background & Left Divider Line
        guiGraphics.fill(x, y, x + width, y + height, CYAN_BG);
        guiGraphics.fill(x, y, x + 1, y + height, BORDER_CYAN);

        // Tab Bar Header
        renderTabBar(guiGraphics, mouseX, mouseY);

        // Content Area Scissored
        int contentY = y + 28;
        int contentH = height - 28;

        guiGraphics.enableScissor(x + 1, contentY, x + width, y + height);

        if (currentTab == InspectorTab.ACTION && parent.getSelectedEntry() == null) {
            // Render Empty State
            Font font = Minecraft.getInstance().font;
            int cx = x + (width / 2);
            int cy = y + 80;
            guiGraphics.drawCenteredString(font, "✦ NO KEYFRAME SELECTED ✦", cx, cy, TEXT_MUTED);
            guiGraphics.drawCenteredString(font, "Click any keyframe on the timeline", cx, cy + 16, 0xFF557788);
            guiGraphics.drawCenteredString(font, "or click [+ ENTRY] to configure.", cx, cy + 28, 0xFF557788);
            guiGraphics.fill(x + 20, cy + 50, x + width - 20, cy + 51, 0x3300E5FF);
            guiGraphics.drawCenteredString(font, "Press [C] in Viewport to stamp", cx, cy + 62, GOLD_ACCENT);
            guiGraphics.drawCenteredString(font, "camera keyframe at playhead!", cx, cy + 74, GOLD_ACCENT);
        } else {
            // Render active widgets within visible scroll viewport
            for (PositionedWidget pw : activeWidgets) {
                if (pw.widget.getY() + pw.widget.getHeight() >= contentY && pw.widget.getY() <= y + height) {
                    pw.widget.render(guiGraphics, mouseX, mouseY, partialTick);
                }
            }
        }

        guiGraphics.disableScissor();

        // Scrollbar Track & Thumb
        int maxScroll = Math.max(0, contentHeight - (height - 36));
        if (maxScroll > 0) {
            int trackH = height - 36;
            int thumbH = Math.max(16, (int) ((double) trackH / contentHeight * trackH));
            int thumbY = contentY + 2 + (int) ((scrollY / maxScroll) * (trackH - thumbH));
            guiGraphics.fill(x + width - 4, contentY + 2, x + width - 1, contentY + 2 + trackH, 0x3300E5FF);
            guiGraphics.fill(x + width - 4, thumbY, x + width - 1, thumbY + thumbH, 0xAA00E5FF);
        }

        // Render Open Dropdowns on top outside scissor
        for (PositionedWidget pw : activeWidgets) {
            if (pw.widget instanceof CyberpunkDropdown<?> dd && dd.isOpen()) {
                dd.renderOverlay(guiGraphics, mouseX, mouseY);
            }
        }
    }

    public void renderOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Render command suggestions outside scissor so autocomplete popup and usage tooltip are visible
        if (activeCommandSuggestions != null && activeCommandBox != null) {
            int contentY = y + 28;
            int contentBottom = y + height;
            if (activeCommandBox.getY() + activeCommandBox.getHeight() >= contentY && activeCommandBox.getY() <= contentBottom) {
                activeCommandSuggestions.render(guiGraphics, mouseX, mouseY);
            }
        }

        for (PositionedWidget pw : activeWidgets) {
            if (pw.widget instanceof CyberpunkDropdown<?> dd && dd.isOpen()) {
                dd.renderOverlay(guiGraphics, mouseX, mouseY);
            }
        }
    }

    private void renderTabBar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        guiGraphics.fill(x, y, x + width, y + 26, 0xEE091624);
        guiGraphics.fill(x, y + 25, x + width, y + 26, 0x5500E5FF);

        int tabW = width / 3;
        String[] tabs = {"ACTION", "CHANNEL", "PROJECT"};
        InspectorTab[] tabTypes = {InspectorTab.ACTION, InspectorTab.CHANNEL, InspectorTab.SEQUENCE};

        for (int i = 0; i < 3; i++) {
            int tx = x + (i * tabW);
            int tw = (i == 2) ? (width - (tabW * 2)) : tabW;
            boolean isSel = (currentTab == tabTypes[i]);
            boolean isHov = mouseX >= tx && mouseX < (tx + tw) && mouseY >= y && mouseY < (y + 26);

            int tabBg = isSel ? 0xFF0D2538 : (isHov ? 0x880B1E2C : 0x00000000);
            if (tabBg != 0) {
                guiGraphics.fill(tx, y, tx + tw, y + 25, tabBg);
            }
            if (isSel) {
                guiGraphics.fill(tx, y + 24, tx + tw, y + 26, CYAN_MAIN);
            }

            int textCol = isSel ? CYAN_MAIN : (isHov ? 0xFFFFFFFF : TEXT_MUTED);
            int twStr = font.width(tabs[i]);
            guiGraphics.drawString(font, tabs[i], tx + (tw - twStr) / 2, y + 9, textCol, false);
        }
    }

    // =========================================================================
    // 9. EVENT PROPAGATION & INTERACTION
    // =========================================================================

    public void focusEditBox(EditBox target) {
        for (EditBox eb : activeEditBoxes) {
            eb.setFocused(eb == target);
        }
    }

    public void unfocusAll() {
        for (EditBox eb : activeEditBoxes) {
            eb.setFocused(false);
        }
        if (activeCommandSuggestions != null) {
            activeCommandSuggestions.updateCommandInfo();
        }
    }

    public void tick() {
        for (EditBox eb : activeEditBoxes) {
            eb.tick();
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. Open Dropdowns Clicked First (floating menus)
        for (PositionedWidget pw : activeWidgets) {
            if (pw.widget instanceof CyberpunkDropdown<?> dd && dd.isOpen()) {
                if (dd.isMouseOverMenu(mouseX, mouseY) || dd.isMouseOverHeader(mouseX, mouseY)) {
                    return dd.mouseClicked(mouseX, mouseY, button);
                } else {
                    dd.setOpen(false);
                    return true;
                }
            }
        }

        // 2. Command Suggestions Clicked (floating autocomplete popup)
        if (activeCommandSuggestions != null && activeCommandSuggestions.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }

        // 3. Tab Bar Click
        if (mouseY >= y && mouseY < (y + 26)) {
            unfocusAll();
            int tabW = width / 3;
            int tabIdx = (int) ((mouseX - x) / tabW);
            if (tabIdx == 0) setTab(InspectorTab.ACTION);
            else if (tabIdx == 1) setTab(InspectorTab.CHANNEL);
            else if (tabIdx >= 2) setTab(InspectorTab.SEQUENCE);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }

        // 4. Content Area Click
        int contentY = y + 28;
        int contentBottom = y + height;
        if (mouseY >= contentY && mouseY <= contentBottom) {
            // First: Check if an EditBox was clicked directly
            EditBox clickedEditBox = null;
            for (EditBox eb : activeEditBoxes) {
                if (eb.getY() + eb.getHeight() >= contentY && eb.getY() <= contentBottom) {
                    if (mouseX >= eb.getX() && mouseX < eb.getX() + eb.getWidth() &&
                        mouseY >= eb.getY() && mouseY < eb.getY() + eb.getHeight()) {
                        clickedEditBox = eb;
                        break;
                    }
                }
            }

            if (clickedEditBox != null) {
                focusEditBox(clickedEditBox);
                clickedEditBox.mouseClicked(mouseX, mouseY, button);
                if (clickedEditBox == activeCommandBox && activeCommandSuggestions != null) {
                    activeCommandSuggestions.updateCommandInfo();
                }
                return true;
            } else {
                unfocusAll();
            }

            // Check other widgets (buttons, timing preview bar, sliders, etc.)
            for (PositionedWidget pw : activeWidgets) {
                if (pw.widget.getY() + pw.widget.getHeight() >= contentY && pw.widget.getY() <= contentBottom) {
                    if (pw.widget.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }
        }

        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (PositionedWidget pw : activeWidgets) {
            if (pw.widget instanceof CyberpunkDropdown<?> dd && dd.isOpen()) {
                if (dd.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                    return true;
                }
            }
        }
        for (PositionedWidget pw : activeWidgets) {
            if (pw.widget instanceof CyberpunkColorPicker picker && picker.isDraggingActive()) {
                if (picker.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                    return true;
                }
            }
        }
        for (PositionedWidget pw : activeWidgets) {
            if (pw.widget.isHoveredOrFocused()) {
                if (pw.widget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (PositionedWidget pw : activeWidgets) {
            if (pw.widget.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Dropdowns scroll check
        for (PositionedWidget pw : activeWidgets) {
            if (pw.widget instanceof CyberpunkDropdown<?> dd && dd.isOpen()) {
                if (dd.mouseScrolled(mouseX, mouseY, delta)) {
                    return true;
                }
            }
        }

        // Command Suggestions scroll check (scrolling long suggestions list when over panel or suggestions)
        if (activeCommandSuggestions != null && activeCommandBox != null && activeCommandBox.isFocused()) {
            if (activeCommandSuggestions.mouseScrolled(delta)) {
                return true;
            }
        }

        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }

        // Focused numeric edit box quick adjust via scroll wheel
        for (EditBox eb : activeEditBoxes) {
            if (eb.isFocused() && mouseX >= eb.getX() && mouseX < eb.getX() + eb.getWidth() &&
                mouseY >= eb.getY() && mouseY < eb.getY() + eb.getHeight()) {
                try {
                    String vStr = eb.getValue().trim();
                    if (vStr.matches("\\d+")) {
                        int curVal = Integer.parseInt(vStr);
                        int step = Screen.hasShiftDown() ? 100 : 25;
                        int nextVal = Math.max(0, curVal + (delta > 0 ? step : -step));
                        eb.setValue(String.valueOf(nextVal));
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        }

        // Content Area Vertical Scrolling
        int maxScroll = Math.max(0, contentHeight - (height - 36));
        if (maxScroll > 0) {
            closeAllDropdowns();
            scrollY = Math.max(0.0, Math.min(maxScroll, scrollY - (delta * 24.0)));
            applyScrollOffset();
            return true;
        }

        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeCommandSuggestions != null && activeCommandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        for (EditBox eb : activeEditBoxes) {
            if (eb.isFocused()) {
                if (eb.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        for (EditBox eb : activeEditBoxes) {
            if (eb.isFocused()) {
                if (eb.charTyped(codePoint, modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isAnyFieldFocused() {
        for (EditBox eb : activeEditBoxes) {
            if (eb.isFocused()) {
                return true;
            }
        }
        return false;
    }
}
