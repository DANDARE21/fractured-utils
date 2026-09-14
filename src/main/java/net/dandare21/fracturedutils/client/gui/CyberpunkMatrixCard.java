package net.dandare21.fracturedutils.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class CyberpunkMatrixCard extends AbstractWidget {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG_ACTIVE = 0xEE09202E;
    private static final int CYAN_BG_INACTIVE = 0xEE060F17;
    private static final int BORDER_ACTIVE = 0xFF00E5FF;
    private static final int BORDER_INACTIVE = 0xFF162B3A;
    private static final int TEXT_ACTIVE = 0xFFFFFFFF;
    private static final int TEXT_INACTIVE = 0xFF769FB3;
    private static final int BADGE_OFF_TEXT = 0xFF436578;
    private static final int BADGE_OFF_BORDER = 0xFF142430;

    private boolean checked;
    private final Consumer<Boolean> onToggle;
    private String activeBadgeText = "ACTIVE";
    private String inactiveBadgeText = "OFF";

    public CyberpunkMatrixCard(int x, int y, int width, int height, Component message, boolean initialValue, Consumer<Boolean> onToggle) {
        super(x, y, width, height, message);
        this.checked = initialValue;
        this.onToggle = onToggle;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public void setBadgeLabels(String active, String inactive) {
        this.activeBadgeText = active;
        this.inactiveBadgeText = inactive;
    }

    public void toggle() {
        this.checked = !this.checked;
        if (onToggle != null) {
            onToggle.accept(this.checked);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.playDownSound(Minecraft.getInstance().getSoundManager());
        toggle();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean isHovered = this.active && this.isHoveredOrFocused();

        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;

        int borderColor;
        int fillColor;
        int textColor;

        if (!this.active) {
            borderColor = 0xFF14222E;
            fillColor = 0xEE040A0F;
            textColor = 0xFF3D5768;
        } else if (isHovered) {
            borderColor = checked ? 0xFFFFFFFF : 0xFF00B0CC;
            fillColor = checked ? 0xEE0D2C40 : 0xEE091824;
            textColor = 0xFFFFFFFF;
        } else {
            borderColor = checked ? BORDER_ACTIVE : BORDER_INACTIVE;
            fillColor = checked ? CYAN_BG_ACTIVE : CYAN_BG_INACTIVE;
            textColor = checked ? TEXT_ACTIVE : TEXT_INACTIVE;
        }

        // Card outer fill
        guiGraphics.fill(x, y, x + w, y + h, fillColor);

        // Cyberpunk border lines
        guiGraphics.fill(x, y, x + w, y + 1, borderColor);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        guiGraphics.fill(x, y, x + 1, y + h, borderColor);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, borderColor);

        // Checkbox box
        int boxSize = 12;
        int boxX = x + 6;
        int boxY = y + (h - boxSize) / 2;

        int boxBorder = checked ? CYAN_MAIN : (isHovered ? 0xFF58849E : 0xFF244154);
        int boxFill = checked ? 0xFF003F4D : 0xFF040A0E;

        guiGraphics.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, boxFill);
        guiGraphics.fill(boxX, boxY, boxX + boxSize, boxY + 1, boxBorder);
        guiGraphics.fill(boxX, boxY + boxSize - 1, boxX + boxSize, boxY + boxSize, boxBorder);
        guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxSize, boxBorder);
        guiGraphics.fill(boxX + boxSize - 1, boxY, boxX + boxSize, boxY + boxSize, boxBorder);

        Font font = Minecraft.getInstance().font;
        if (checked) {
            // Checkmark symbol
            guiGraphics.drawString(font, "✔", boxX + 2, boxY + 2, CYAN_MAIN, false);
        }

        // Status Badge Pill on Right
        String badgeText = checked ? activeBadgeText : inactiveBadgeText;
        int badgeTextW = font.width(badgeText);
        int badgeW = Math.max(34, badgeTextW + 10);
        int badgeH = 13;
        int badgeX = x + w - badgeW - 6;
        int badgeY = y + (h - badgeH) / 2;

        int badgeBorderColor = checked ? (isHovered ? 0xFFFFFFFF : CYAN_MAIN) : BADGE_OFF_BORDER;
        int badgeFillColor = checked ? 0xEE002633 : 0xEE050B10;
        int badgeTextColor = checked ? CYAN_MAIN : BADGE_OFF_TEXT;

        guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, badgeFillColor);
        guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, badgeBorderColor);
        guiGraphics.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, badgeBorderColor);
        guiGraphics.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, badgeBorderColor);
        guiGraphics.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH, badgeBorderColor);

        int badgeStrX = badgeX + (badgeW - badgeTextW) / 2;
        int badgeStrY = badgeY + (badgeH - 8) / 2;
        guiGraphics.drawString(font, badgeText, badgeStrX, badgeStrY, badgeTextColor, false);

        // Label Text
        int labelX = boxX + boxSize + 6;
        int labelY = y + (h - 8) / 2;
        int maxLabelW = badgeX - labelX - 4;

        Component msg = this.getMessage();
        if (maxLabelW > 10 && font.width(msg) > maxLabelW) {
            String truncated = font.plainSubstrByWidth(msg.getString(), maxLabelW - font.width("..")) + "..";
            guiGraphics.drawString(font, truncated, labelX, labelY, textColor, false);
        } else {
            guiGraphics.drawString(font, msg, labelX, labelY, textColor, false);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
