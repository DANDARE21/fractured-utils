package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.dialog.DialogFormatUtil;
import net.dandare21.fracturedutils.dialog.DialogLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DialogListWidget extends ObjectSelectionList<DialogListWidget.DialogLineEntry> {
    private final DialogScreen screen;
    private final int topPos;
    private final int bottomPos;

    private int draggingIndex = -1;
    private double dragMouseX = 0;
    private double dragMouseY = 0;
    private int targetDropIndex = -1;

    public DialogListWidget(DialogScreen screen, Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
        super(minecraft, width, height, top, bottom, itemHeight);
        this.screen = screen;
        this.topPos = top;
        this.bottomPos = bottom;
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
    }

    public void updateEntries(List<DialogLine> lines) {
        this.clearEntries();
        for (int i = 0; i < lines.size(); i++) {
            this.addEntry(new DialogLineEntry(screen, lines.get(i), i, lines.size()));
        }
    }

    public int getTopPos() {
        return topPos;
    }

    public int getItemHeight() {
        return itemHeight;
    }

    public int getDraggingIndex() {
        return draggingIndex;
    }

    @Override
    public int getRowWidth() {
        return this.width - 24;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getRowLeft() + this.getRowWidth() + 6;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int rowLeft = getRowLeft();
            int rowWidth = getRowWidth();
            int listTop = this.topPos;
            int listBottom = this.bottomPos;

            if (mouseX >= rowLeft && mouseX <= rowLeft + rowWidth && mouseY >= listTop && mouseY <= listBottom) {
                int relativeY = (int) (mouseY - listTop + this.getScrollAmount());
                int index = relativeY / this.itemHeight;

                if (index >= 0 && index < this.children().size()) {
                    DialogLineEntry entry = this.children().get(index);
                    if (entry.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                    // Start drag-and-drop
                    this.draggingIndex = index;
                    this.dragMouseX = mouseX;
                    this.dragMouseY = mouseY;
                    this.targetDropIndex = index;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingIndex != -1) {
            this.dragMouseX = mouseX;
            this.dragMouseY = mouseY;

            int listTop = this.topPos;
            int relativeY = (int) (mouseY - listTop + this.getScrollAmount());
            int row = (relativeY + this.itemHeight / 2) / this.itemHeight;
            this.targetDropIndex = Math.max(0, Math.min(this.children().size(), row));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingIndex != -1) {
            if (targetDropIndex != -1 && targetDropIndex != draggingIndex && targetDropIndex != draggingIndex + 1) {
                screen.reorderLines(draggingIndex, targetDropIndex);
            }
            draggingIndex = -1;
            targetDropIndex = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int listLeft = this.getRowLeft();
        int listWidth = this.getRowWidth();
        int listTop = this.topPos;
        int listBottom = this.bottomPos;
        int listHeight = listBottom - listTop;

        double scale = screen.getLayoutScale();

        // Enable scaled scissor region so item contents adapt to screen dimensions on all resolutions
        graphics.enableScissor(
            (int) Math.round(listLeft * scale),
            (int) Math.round(listTop * scale),
            (int) Math.round((listLeft + listWidth + 12) * scale),
            (int) Math.round(listBottom * scale)
        );

        int count = this.children().size();
        int scroll = (int) this.getScrollAmount();

        for (int i = 0; i < count; i++) {
            int itemTop = listTop + (i * this.itemHeight) - scroll;
            int itemBottom = itemTop + this.itemHeight;

            if (itemBottom >= listTop && itemTop <= listBottom) {
                DialogLineEntry entry = this.children().get(i);
                boolean isHovered = (mouseX >= listLeft && mouseX <= listLeft + listWidth && mouseY >= itemTop && mouseY < itemBottom && mouseY >= listTop && mouseY <= listBottom);
                entry.render(graphics, i, itemTop, listLeft, listWidth, this.itemHeight, mouseX, mouseY, isHovered, partialTick);
            }
        }

        graphics.disableScissor();

        // Cyberpunk Scrollbar Rendering
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int sbX = listLeft + listWidth + 4;
            int sbY = listTop;
            int sbW = 6;
            int sbH = listHeight;

            graphics.fill(sbX, sbY, sbX + sbW, sbY + sbH, 0x77050B10);
            graphics.fill(sbX, sbY, sbX + 1, sbY + sbH, 0xAA00E5FF);
            graphics.fill(sbX + sbW - 1, sbY, sbX + sbW, sbY + sbH, 0xAA00E5FF);

            int totalH = count * this.itemHeight;
            int thumbH = Math.max(12, (int) ((float) sbH / totalH * sbH));
            int thumbY = sbY + (int) ((float) scroll / maxScroll * (sbH - thumbH));

            graphics.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0xFF00E5FF);
        }

        // Drop Indicator Line during Drag
        if (draggingIndex != -1 && targetDropIndex != -1) {
            int dropY = listTop + (targetDropIndex * this.itemHeight) - scroll;
            if (dropY >= listTop && dropY <= listBottom) {
                graphics.fill(listLeft - 4, dropY - 2, listLeft + listWidth + 4, dropY + 2, 0xFF00E5FF);
                graphics.fill(listLeft - 2, dropY - 1, listLeft + listWidth + 2, dropY + 1, 0xFFFFFFFF);
            }
        }
    }

    public static class DialogLineEntry extends ObjectSelectionList.Entry<DialogLineEntry> {
        private final DialogScreen screen;
        private final DialogLine line;
        private final int index;
        private final int totalCount;

        public DialogLineEntry(DialogScreen screen, DialogLine line, int index, int totalCount) {
            this.screen = screen;
            this.line = line;
            this.index = index;
            this.totalCount = totalCount;
        }

        public DialogLine getLine() {
            return line;
        }

        @Override
        public Component getNarration() {
            return Component.literal("Line " + (index + 1));
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            Font font = Minecraft.getInstance().font;

            boolean isBeingDragged = (screen.getDialogListWidget() != null && screen.getDialogListWidget().getDraggingIndex() == index);

            int accentColor = 0xFF00E5FF;
            int bg = isBeingDragged ? 0x4408121B : (isHovered ? 0xEE0A2030 : 0xBB08121B);
            int borderColor = isBeingDragged ? 0x5500E5FF : (isHovered ? 0xFF00E5FF : 0xAA00E5FF);

            // Card Fill & Border
            graphics.fill(left, top, left + width, top + height - 2, bg);
            graphics.fill(left, top, left + width, top + 1, borderColor);
            graphics.fill(left, top + height - 3, left + width, top + height - 2, borderColor);
            graphics.fill(left, top, left + 1, top + height - 2, borderColor);
            graphics.fill(left + width - 1, top, left + width, top + height - 2, borderColor);

            // Left Accent Indicator
            graphics.fill(left + 2, top + 2, left + 5, top + height - 4, accentColor);

            // Drag Handle Icon ≡
            graphics.drawString(font, Component.literal("≡"), left + 8, top + (height - 8) / 2, isHovered ? 0xFF00E5FF : 0x8800E5FF, false);

            // Line Number Tag & Details
            String headerText = "[" + (index + 1) + "] Delay: " + line.getDelayTicks() + "t | Speed: " + line.getCharSpeedTicks() + "t/char";
            if (line.getSound() != null && !line.getSound().isEmpty()) {
                headerText += " | 🔊 " + line.getSound();
            }
            if (line.getLetterSound() != null && !line.getLetterSound().isEmpty()) {
                headerText += " | 💬 " + line.getLetterSound();
            }

            int maxHeaderWidth = width - 110;
            if (font.width(headerText) > maxHeaderWidth) {
                headerText = font.plainSubstrByWidth(headerText, Math.max(10, maxHeaderWidth - 6)) + "..";
            }
            graphics.drawString(font, Component.literal(headerText), left + 22, top + 5, 0xAA00E5FF, false);

            // Formatted Dialog Preview Text (Speaker + Message)
            Component formattedText = DialogFormatUtil.formatLine(line.getSpeaker(), line.getText());
            String rawPreviewStr = formattedText.getString();
            int maxPreviewWidth = width - 30;
            if (font.width(rawPreviewStr) > maxPreviewWidth) {
                rawPreviewStr = font.plainSubstrByWidth(rawPreviewStr, Math.max(10, maxPreviewWidth - 6)) + "..";
                formattedText = Component.literal(rawPreviewStr);
            }
            graphics.drawString(font, formattedText, left + 22, top + 20, 0xFFFFFFFF, false);

            // Mini Action Buttons: Edit (✎), Duplicate (⧉), Up (▲), Down (▼), Delete (🗑)
            int btnY = top + 10;
            int btnH = 18;
            int btnW = 18;

            int deleteX = left + width - 24;
            int downX = left + width - 44;
            int upX = left + width - 64;
            int dupX = left + width - 84;
            int editX = left + width - 104;

            // Render Edit Button (✎)
            boolean editHovered = mouseX >= editX && mouseX < editX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            graphics.fill(editX, btnY, editX + btnW, btnY + btnH, editHovered ? 0xFF00E5FF : 0x6600E5FF);
            graphics.drawString(font, Component.literal("✎"), editX + 5, btnY + 5, editHovered ? 0xFF000000 : 0xFFFFFFFF, false);

            // Render Duplicate Button (⧉)
            boolean dupHovered = mouseX >= dupX && mouseX < dupX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            graphics.fill(dupX, btnY, dupX + btnW, btnY + btnH, dupHovered ? 0xFF00E5FF : 0x6600E5FF);
            graphics.drawString(font, Component.literal("⧉"), dupX + 4, btnY + 5, dupHovered ? 0xFF000000 : 0xFFFFFFFF, false);

            // Render Move Up Button (▲)
            boolean upHovered = mouseX >= upX && mouseX < upX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            graphics.fill(upX, btnY, upX + btnW, btnY + btnH, index > 0 ? (upHovered ? 0xFF00E5FF : 0x4400E5FF) : 0x22555555);
            graphics.drawString(font, Component.literal("▲"), upX + 5, btnY + 5, index > 0 ? (upHovered ? 0xFF000000 : 0xFFFFFFFF) : 0x88888888, false);

            // Render Move Down Button (▼)
            boolean downHovered = mouseX >= downX && mouseX < downX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            graphics.fill(downX, btnY, downX + btnW, btnY + btnH, index < totalCount - 1 ? (downHovered ? 0xFF00E5FF : 0x4400E5FF) : 0x22555555);
            graphics.drawString(font, Component.literal("▼"), downX + 5, btnY + 5, index < totalCount - 1 ? (downHovered ? 0xFF000000 : 0xFFFFFFFF) : 0x88888888, false);

            // Render Delete Button (🗑)
            boolean deleteHovered = mouseX >= deleteX && mouseX < deleteX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            graphics.fill(deleteX, btnY, deleteX + btnW, btnY + btnH, deleteHovered ? 0xFFFF3355 : 0x66FF3355);
            graphics.drawString(font, Component.literal("🗑"), deleteX + 4, btnY + 5, deleteHovered ? 0xFFFFFFFF : 0xFFFFAAAA, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int rowLeft = screen.getDialogListWidget().getRowLeft();
                int rowWidth = screen.getDialogListWidget().getRowWidth();
                int top = screen.getDialogListWidget().getTopPos() + index * screen.getDialogListWidget().getItemHeight() - (int) screen.getDialogListWidget().getScrollAmount();
                int btnY = top + 10;
                int btnH = 18;
                int btnW = 18;

                int deleteX = rowLeft + rowWidth - 24;
                int downX = rowLeft + rowWidth - 44;
                int upX = rowLeft + rowWidth - 64;
                int dupX = rowLeft + rowWidth - 84;
                int editX = rowLeft + rowWidth - 104;

                if (mouseY >= btnY && mouseY < btnY + btnH) {
                    if (mouseX >= editX && mouseX < editX + btnW) {
                        screen.openEditLineModal(line, index);
                        return true;
                    }
                    if (mouseX >= dupX && mouseX < dupX + btnW) {
                        screen.duplicateLine(index);
                        return true;
                    }
                    if (mouseX >= upX && mouseX < upX + btnW) {
                        if (index > 0) {
                            screen.moveLine(index, index - 1);
                        }
                        return true;
                    }
                    if (mouseX >= downX && mouseX < downX + btnW) {
                        if (index < totalCount - 1) {
                            screen.moveLine(index, index + 1);
                        }
                        return true;
                    }
                    if (mouseX >= deleteX && mouseX < deleteX + btnW) {
                        screen.deleteLine(index);
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
