package net.dandare21.fracturedutils.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.awt.Color;
import java.util.function.Consumer;

/**
 * Cyberpunk interactive HSB Color Disc Picker with Value/Brightness bar,
 * preset chips, invert button, and bidirectional hex synchronization.
 */
public class CyberpunkColorPicker extends AbstractWidget {
    private static final int DISC_RADIUS = 22;
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
    private boolean isDraggingBri = false;

    private Consumer<Integer> onColorChanged;
    private EditBox boundHexBox;
    private boolean updatingFromHex = false;

    public CyberpunkColorPicker(int x, int y, int initialColor, Consumer<Integer> onColorChanged) {
        this(x, y, 174, initialColor, onColorChanged);
    }

    public CyberpunkColorPicker(int x, int y, int width, int initialColor, Consumer<Integer> onColorChanged) {
        super(x, y, Math.max(135, width), 56, Component.literal("Color Picker"));
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
        this.currentBri = Math.max(0.0f, Math.min(1.0f, hsb[2]));

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

    public boolean isDraggingActive() {
        return isDraggingDisc || isDraggingBri;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int discCenterX = getX() + DISC_RADIUS + 4;
        int discCenterY = getY() + DISC_RADIUS + 5;

        // 1. Draw outer glowing disc frame
        int discBorderColor = (isDraggingDisc || isHoveredOrFocused()) ? 0xFF00E5FF : 0xFF334455;
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

        // 4. Vertical Brightness / Value Slider Bar
        int briBarX = getX() + DISC_RADIUS * 2 + 11;
        int briBarY = getY() + 5;
        int briBarW = 6;
        int briBarH = 46;

        // Border around Brightness bar
        int briBorderColor = (isDraggingBri) ? 0xFF00E5FF : 0xFF223344;
        graphics.fill(briBarX - 1, briBarY - 1, briBarX + briBarW + 1, briBarY + briBarH + 1, briBorderColor);

        // Render vertical brightness gradient (top = 1.0, bottom = 0.0)
        for (int py = 0; py < briBarH; py++) {
            float b = 1.0f - ((float) py / (float) (briBarH - 1));
            int rgb = Color.HSBtoRGB(currentHue, currentSat, b);
            graphics.fill(briBarX, briBarY + py, briBarX + briBarW, briBarY + py + 1, 0xFF000000 | rgb);
        }

        // Indicator needle for current brightness
        int needleY = briBarY + Math.round((1.0f - currentBri) * (briBarH - 1));
        graphics.fill(briBarX - 2, needleY - 1, briBarX + briBarW + 2, needleY + 2, 0xFFFFFFFF);
        graphics.fill(briBarX - 1, needleY, briBarX + briBarW + 1, needleY + 1, 0xFF000000);

        // 5. Color Swatch & Preview
        int swatchX = briBarX + briBarW + 6;
        int swatchY = getY() + 5;
        int swatchW = 20;
        int swatchH = 20;

        // Swatch background & border
        graphics.fill(swatchX - 1, swatchY - 1, swatchX + swatchW + 1, swatchY + swatchH + 1, 0xFF00E5FF);
        graphics.fill(swatchX, swatchY, swatchX + swatchW, swatchY + swatchH, currentColor | 0xFF000000);

        // Invert Button (⇄) right next to swatch
        int invBtnX = swatchX + swatchW + 4;
        int invBtnY = swatchY;
        int invBtnW = 16;
        int invBtnH = 20;
        boolean invHovered = mouseX >= invBtnX && mouseX < invBtnX + invBtnW && mouseY >= invBtnY && mouseY < invBtnY + invBtnH;
        graphics.fill(invBtnX, invBtnY, invBtnX + invBtnW, invBtnY + invBtnH, invHovered ? 0xFF1B3B50 : 0xFF0B1924);
        graphics.fill(invBtnX, invBtnY, invBtnX + invBtnW, invBtnY + 1, invHovered ? 0xFF00E5FF : 0xFF224455);
        graphics.fill(invBtnX, invBtnY + invBtnH - 1, invBtnX + invBtnW, invBtnY + invBtnH, invHovered ? 0xFF00E5FF : 0xFF224455);
        graphics.fill(invBtnX, invBtnY, invBtnX + 1, invBtnY + invBtnH, invHovered ? 0xFF00E5FF : 0xFF224455);
        graphics.fill(invBtnX + invBtnW - 1, invBtnY, invBtnX + invBtnW, invBtnY + invBtnH, invHovered ? 0xFF00E5FF : 0xFF224455);

        Font font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, "⇄", invBtnX + (invBtnW / 2), invBtnY + 5, invHovered ? 0xFF00E5FF : 0xFFAABBCC);

        // Hex Label
        String hexLabel = String.format("#%06X", currentColor & 0x00FFFFFF);
        int hexX = invBtnX + invBtnW + 5;
        if (hexX + font.width(hexLabel) <= getX() + width) {
            graphics.drawString(font, hexLabel, hexX, swatchY + 6, 0xFFFFFFFF, false);
        }

        // 6. Preset Chips
        int chipsStartX = swatchX;
        int chipsY = getY() + 32;
        int chipSize = 9;
        int chipGap = 3;

        int availChipsW = (getX() + width) - chipsStartX - 2;
        boolean multiRow = availChipsW < (PRESETS.length * (chipSize + chipGap));

        for (int i = 0; i < PRESETS.length; i++) {
            int cx;
            int cy;
            if (multiRow) {
                int col = i % 4;
                int row = i / 4;
                cx = chipsStartX + col * (chipSize + chipGap);
                cy = chipsY + row * (chipSize + 2);
            } else {
                cx = chipsStartX + i * (chipSize + chipGap);
                cy = chipsY;
            }

            int color = PRESETS[i];
            boolean isSelected = (color & 0x00FFFFFF) == (currentColor & 0x00FFFFFF);
            boolean isChipHovered = mouseX >= cx && mouseX <= cx + chipSize && mouseY >= cy && mouseY <= cy + chipSize;

            int border = isSelected ? 0xFF00E5FF : (isChipHovered ? 0xFFFFFFFF : 0xFF334455);
            graphics.fill(cx - 1, cy - 1, cx + chipSize + 1, cy + chipSize + 1, border);
            graphics.fill(cx, cy, cx + chipSize, cy + chipSize, color | 0xFF000000);
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
            int discCenterY = getY() + DISC_RADIUS + 5;
            double dx = mouseX - discCenterX;
            double dy = mouseY - discCenterY;
            double dist = Math.sqrt(dx * dx + dy * dy);

            // 1. Clicked inside disc
            if (dist <= DISC_RADIUS + 2) {
                this.isDraggingDisc = true;
                updateColorFromDisc(dx, dy, dist);
                return true;
            }

            // 2. Clicked on Brightness bar
            int briBarX = getX() + DISC_RADIUS * 2 + 11;
            int briBarY = getY() + 5;
            int briBarW = 6;
            int briBarH = 46;
            if (mouseX >= briBarX - 3 && mouseX <= briBarX + briBarW + 3 && mouseY >= briBarY && mouseY <= briBarY + briBarH) {
                this.isDraggingBri = true;
                updateColorFromBri(mouseY - briBarY, briBarH);
                return true;
            }

            // 3. Clicked Invert Button
            int swatchX = briBarX + briBarW + 6;
            int swatchW = 20;
            int invBtnX = swatchX + swatchW + 4;
            int invBtnY = getY() + 5;
            int invBtnW = 16;
            int invBtnH = 20;
            if (mouseX >= invBtnX && mouseX <= invBtnX + invBtnW && mouseY >= invBtnY && mouseY <= invBtnY + invBtnH) {
                int inverted = 0xFF000000 | ((~currentColor) & 0x00FFFFFF);
                setColor(inverted);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
                return true;
            }

            // 4. Clicked preset chips
            int chipsStartX = swatchX;
            int chipsY = getY() + 32;
            int chipSize = 9;
            int chipGap = 3;

            int availChipsW = (getX() + width) - chipsStartX - 2;
            boolean multiRow = availChipsW < (PRESETS.length * (chipSize + chipGap));

            for (int i = 0; i < PRESETS.length; i++) {
                int cx;
                int cy;
                if (multiRow) {
                    int col = i % 4;
                    int row = i / 4;
                    cx = chipsStartX + col * (chipSize + chipGap);
                    cy = chipsY + row * (chipSize + 2);
                } else {
                    cx = chipsStartX + i * (chipSize + chipGap);
                    cy = chipsY;
                }

                if (mouseX >= cx && mouseX <= cx + chipSize && mouseY >= cy && mouseY <= cy + chipSize) {
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
        if (button == 0) {
            if (isDraggingDisc) {
                int discCenterX = getX() + DISC_RADIUS + 4;
                int discCenterY = getY() + DISC_RADIUS + 5;
                double dx = mouseX - discCenterX;
                double dy = mouseY - discCenterY;
                double dist = Math.sqrt(dx * dx + dy * dy);
                updateColorFromDisc(dx, dy, dist);
                return true;
            }

            if (isDraggingBri) {
                int briBarY = getY() + 5;
                int briBarH = 46;
                updateColorFromBri(mouseY - briBarY, briBarH);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && (isDraggingDisc || isDraggingBri)) {
            isDraggingDisc = false;
            isDraggingBri = false;
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

    private void updateColorFromBri(double relY, int height) {
        float b = 1.0f - (float) Math.max(0.0, Math.min(1.0, relY / (double) height));
        this.currentBri = b;
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
