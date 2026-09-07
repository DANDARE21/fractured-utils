package net.dandare21.fracturedutils.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public class CyberpunkButton extends Button {
    private int accentColor;
    private final boolean isSelected;

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
    }

    public void setColor(int color) {
        this.accentColor = color;
    }

    public int getAccentColor() {
        return accentColor;
    }

    public CyberpunkButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, 0xFF00E5FF, false, null);
    }

    public CyberpunkButton(int x, int y, int width, int height, Component message, OnPress onPress, Component tooltip) {
        this(x, y, width, height, message, onPress, 0xFF00E5FF, false, tooltip);
    }

    public CyberpunkButton(int x, int y, int width, int height, Component message, OnPress onPress, int accentColor, boolean isSelected) {
        this(x, y, width, height, message, onPress, accentColor, isSelected, null);
    }

    public CyberpunkButton(int x, int y, int width, int height, Component message, OnPress onPress, int accentColor, boolean isSelected, Component tooltip) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.accentColor = accentColor;
        this.isSelected = isSelected;
        if (tooltip != null) {
            this.setTooltip(Tooltip.create(tooltip));
        }
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean isHovered = this.active && this.isHoveredOrFocused();

        int borderColor;
        int fillColor;
        int textColor;

        if (!this.active) {
            borderColor = 0x44445566;
            fillColor = 0xEE0A0F14;
            textColor = 0xFF556677;
        } else if (isSelected) {
            borderColor = 0xFFFFFFFF;
            fillColor = 0xEE008599;
            textColor = 0xFFFFFFFF;
        } else if (isHovered) {
            borderColor = 0xFFFFFFFF;
            fillColor = 0xEE082535;
            textColor = 0xFFFFFFFF;
        } else {
            borderColor = accentColor;
            fillColor = 0xEE081622;
            textColor = accentColor;
        }

        // Fill background
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, fillColor);

        // Cyberpunk border lines
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, borderColor);
        guiGraphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, borderColor);
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, borderColor);
        guiGraphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, borderColor);

        // Corner notch accents on hover or selected
        if (isHovered || isSelected) {
            guiGraphics.fill(this.getX() + 2, this.getY() + 2, this.getX() + 5, this.getY() + 3, borderColor);
            guiGraphics.fill(this.getX() + 2, this.getY() + 2, this.getX() + 3, this.getY() + 5, borderColor);

            guiGraphics.fill(this.getX() + this.width - 5, this.getY() + this.height - 3, this.getX() + this.width - 2, this.getY() + this.height - 2, borderColor);
            guiGraphics.fill(this.getX() + this.width - 3, this.getY() + this.height - 5, this.getX() + this.width - 2, this.getY() + this.height - 2, borderColor);
        }

        // Render Centered Text with text width clamping
        Font font = Minecraft.getInstance().font;
        Component msg = this.getMessage();
        int maxW = this.width - 4;
        if (maxW > 6 && font.width(msg) > maxW) {
            String str = font.plainSubstrByWidth(msg.getString(), maxW - font.width("..")) + "..";
            msg = Component.literal(str);
        }
        guiGraphics.drawCenteredString(font, msg, this.getX() + (this.width / 2), this.getY() + (this.height - 8) / 2, textColor);
    }
}
