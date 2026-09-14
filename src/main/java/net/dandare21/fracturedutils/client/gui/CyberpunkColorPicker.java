package net.dandare21.fracturedutils.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.function.Consumer;

/**
 * Cyberpunk interactive HSB Color Disc Picker with preset chips and hex synchronization.
 */
public class CyberpunkColorPicker extends AbstractWidget {
    private static final int DISC_RADIUS = 24;
    private static final int[] PRESETS = {
            0xFFFFFFFF, // Pure White
            0xFF00E5FF, // Cyber Cyan
            0xFFFF00CC, // Neon Magenta
            0xFFFFD700, // Electric Gold
            0xFFFF2255, // Crimson Red
            0xFF00FF88, // Matrix Green
            0xFF000000, // Pitch Black
            0xFF7700FF  // Ultraviolet
    };

    private int currentColor = 0xFFFFFFFF;
    private float currentHue = 0.0f;
    private float currentSat = 0.0f;
    private float currentBri = 1.0f;
    private boolean isDraggingDisc = false;
    private Consumer<Integer> onColorChanged;
    private EditBox boundHexBox;
    private boolean updatingFromHex = false;

    public CyberpunkColorPicker(int x, int y, int initialColor, Consumer<Integer> onColorChanged) {
        super(x, y, 174, 56, Component.literal("Color Picker"));
        this.onColorChanged = onColorChanged;
        setColor(initialColor);
    }

    public void bindHexBox(EditBox hexBox) {
        this.boundHexBox = hexBox;
        if (this.boundHexBox != null) {
            updateHexBox();
            this.boundHexBox.setResponder(text -> {
                if (updatingFromHex) return;
                try {
                    String clean = text.trim().replace("#", "");
                    if (clean.length() == 6 || clean.length() == 8) {
                        int parsed = (int) Long.parseLong(clean, 16);
                        if (clean.length() == 6) {
                            parsed |= 0xFF000000;
                        }
                        setColorInternal(parsed, false);
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    public int getColor() {
        return currentColor;
    }

    public void setColor(int color) {
        setColorInternal(color, true);
    }

    private void setColorInternal(int color, boolean syncHexBox) {
        this.currentColor = color;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        this.currentHue = hsb[0];
        this.currentSat = hsb[1];
        this.currentBri = hsb[2] > 0.05f ? hsb[2] : 1.0f;

        if (syncHexBox && boundHexBox != null) {
            updateHexBox();
        }
        if (onColorChanged != null) {
            onColorChanged.accept(this.currentColor);
        }
    }

    private void updateHexBox() {
        if (boundHexBox != null) {
            updatingFromHex = true;
            boundHexBox.setValue(String.format("#%06X", currentColor & 0x00FFFFFF));
            updatingFromHex = false;
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int discCenterX = getX() + DISC_RADIUS + 4;
        int discCenterY = getY() + DISC_RADIUS + 4;

        // 1. Draw outer glowing disc frame
        int discBorderColor = isHoveredOrFocused() ? 0xFF00E5FF : 0xFF334455;
        drawCircleOutline(graphics, discCenterX, discCenterY, DISC_RADIUS + 2, discBorderColor);

        // 2. Render HSB Disc Pixels
        renderDisc(graphics, discCenterX, discCenterY);

        // 3. Render Reticle / Crosshair at (currentHue, currentSat)
        double angle = currentHue * Math.PI * 2.0;
        double radius = currentSat * DISC_RADIUS;
        int retX = (int) (discCenterX + Math.cos(angle) * radius);
        int retY = (int) (discCenterY + Math.sin(angle) * radius);

        graphics.fill(retX - 3, retY, retX + 4, retY + 1, 0xFF000000);
        graphics.fill(retX, retY - 3, retX + 1, retY + 4, 0xFF000000);
        graphics.fill(retX - 2, retY, retX + 3, retY + 1, 0xFFFFFFFF);
        graphics.fill(retX, retY - 2, retX + 1, retY + 3, 0xFFFFFFFF);

        // 4. Color Swatch & Preview
        int swatchX = getX() + DISC_RADIUS * 2 + 14;
        int swatchY = getY() + 4;
        int swatchW = 26;
        int swatchH = 26;

        // Swatch background & border
        graphics.fill(swatchX - 1, swatchY - 1, swatchX + swatchW + 1, swatchY + swatchH + 1, 0xFF00E5FF);
        graphics.fill(swatchX, swatchY, swatchX + swatchW, swatchY + swatchH, currentColor | 0xFF000000);

        Font font = Minecraft.getInstance().font;
        String hexLabel = String.format("#%06X", currentColor & 0x00FFFFFF);
        graphics.drawString(font, hexLabel, swatchX + swatchW + 6, swatchY + 4, 0xFFFFFFFF, false);
        graphics.drawString(font, "PRESETS:", swatchX + swatchW + 6, swatchY + 16, 0xFF8899AA, false);

        // 5. Render Preset Chips
        int chipsStartX = swatchX;
        int chipsY = getY() + 34;
        int chipSize = 10;
        int chipGap = 3;

        for (int i = 0; i < PRESETS.length; i++) {
            int cx = chipsStartX + i * (chipSize + chipGap);
            int color = PRESETS[i];
            boolean isSelected = (color & 0x00FFFFFF) == (currentColor & 0x00FFFFFF);
            int border = isSelected ? 0xFF00E5FF : 0xFF445566;

            graphics.fill(cx - 1, chipsY - 1, cx + chipSize + 1, chipsY + chipSize + 1, border);
            graphics.fill(cx, chipsY, cx + chipSize, chipsY + chipSize, color | 0xFF000000);
        }
    }

    private void renderDisc(GuiGraphics graphics, int cx, int cy) {
        RenderSystem.enableBlend();
        for (int dy = -DISC_RADIUS; dy <= DISC_RADIUS; dy += 2) {
            for (int dx = -DISC_RADIUS; dx <= DISC_RADIUS; dx += 2) {
                int distSq = dx * dx + dy * dy;
                if (distSq <= DISC_RADIUS * DISC_RADIUS) {
                    double dist = Math.sqrt(distSq) / DISC_RADIUS;
                    double angle = Math.atan2(dy, dx);
                    float h = (float) ((angle / (Math.PI * 2.0) + 1.0) % 1.0);
                    float s = (float) Math.min(1.0, dist);
                    int rgb = Color.HSBtoRGB(h, s, currentBri);
                    graphics.fill(cx + dx, cy + dy, cx + dx + 2, cy + dy + 2, 0xFF000000 | rgb);
                }
            }
        }
    }

    private void drawCircleOutline(GuiGraphics graphics, int cx, int cy, int r, int color) {
        int rSq = r * r;
        int innerSq = (r - 1) * (r - 1);
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int distSq = dx * dx + dy * dy;
                if (distSq <= rSq && distSq >= innerSq) {
                    graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.active) return false;
        if (button == 0) {
            int discCenterX = getX() + DISC_RADIUS + 4;
            int discCenterY = getY() + DISC_RADIUS + 4;
            double dx = mouseX - discCenterX;
            double dy = mouseY - discCenterY;
            double dist = Math.sqrt(dx * dx + dy * dy);

            // Clicked inside disc
            if (dist <= DISC_RADIUS + 2) {
                this.isDraggingDisc = true;
                updateColorFromDisc(dx, dy, dist);
                return true;
            }

            // Clicked preset chips
            int swatchX = getX() + DISC_RADIUS * 2 + 14;
            int chipsY = getY() + 34;
            int chipSize = 10;
            int chipGap = 3;

            for (int i = 0; i < PRESETS.length; i++) {
                int cx = swatchX + i * (chipSize + chipGap);
                if (mouseX >= cx && mouseX <= cx + chipSize && mouseY >= chipsY && mouseY <= chipsY + chipSize) {
                    setColor(PRESETS[i]);
                    playDownSound(Minecraft.getInstance().getSoundManager());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingDisc && button == 0) {
            int discCenterX = getX() + DISC_RADIUS + 4;
            int discCenterY = getY() + DISC_RADIUS + 4;
            double dx = mouseX - discCenterX;
            double dy = mouseY - discCenterY;
            double dist = Math.sqrt(dx * dx + dy * dy);
            updateColorFromDisc(dx, dy, dist);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDraggingDisc) {
            isDraggingDisc = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateColorFromDisc(double dx, double dy, double dist) {
        double angle = Math.atan2(dy, dx);
        this.currentHue = (float) ((angle / (Math.PI * 2.0) + 1.0) % 1.0);
        this.currentSat = (float) Math.max(0.0, Math.min(1.0, dist / DISC_RADIUS));
        int rgb = Color.HSBtoRGB(currentHue, currentSat, currentBri);
        this.currentColor = 0xFF000000 | rgb;
        updateHexBox();
        if (onColorChanged != null) {
            onColorChanged.accept(this.currentColor);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
