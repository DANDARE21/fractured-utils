package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.dialog.DialogFormatUtil;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

public class CyberpunkMultilineEditBox extends AbstractWidget {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BORDER = 0xAA00E5FF;
    private static final int BG_COLOR = 0xEE05090C;
    private static final int SELECTION_COLOR = 0x6600E5FF;

    private String value = "";
    private String undoHistory = "";
    private int maxLength = 512;
    private int cursorPos = 0;
    private int selectionStart = 0;
    private int selectionEnd = 0;
    private boolean isDraggingMouse = false;

    private long lastClickTime = 0;
    private int clickCount = 0;

    private Consumer<String> responder;

    public CyberpunkMultilineEditBox(int x, int y, int width, int height, Component title) {
        super(x, y, width, height, title);
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
        if (this.value.length() > maxLength) {
            this.value = this.value.substring(0, maxLength);
            clampCursorAndSelection();
        }
    }

    public String getValue() {
        return value;
    }

    public void setValue(String val) {
        if (val == null) val = "";
        if (val.length() > maxLength) {
            val = val.substring(0, maxLength);
        }
        this.undoHistory = this.value;
        this.value = val;
        this.cursorPos = this.value.length();
        clearSelection();
        if (this.responder != null) {
            this.responder.accept(this.value);
        }
    }

    public void setResponder(Consumer<String> responder) {
        this.responder = responder;
    }

    public boolean hasSelection() {
        return selectionStart != selectionEnd;
    }

    public void clearSelection() {
        this.selectionStart = this.cursorPos;
        this.selectionEnd = this.cursorPos;
    }

    public String getSelectedText() {
        if (!hasSelection()) return "";
        int selMin = Math.min(selectionStart, selectionEnd);
        int selMax = Math.max(selectionStart, selectionEnd);
        return this.value.substring(selMin, selMax);
    }

    public boolean deleteSelectedText() {
        if (hasSelection()) {
            this.undoHistory = this.value;
            int selMin = Math.min(selectionStart, selectionEnd);
            int selMax = Math.max(selectionStart, selectionEnd);
            StringBuilder sb = new StringBuilder(this.value);
            sb.delete(selMin, selMax);
            this.value = sb.toString();
            this.cursorPos = selMin;
            clearSelection();
            if (this.responder != null) {
                this.responder.accept(this.value);
            }
            return true;
        }
        return false;
    }

    public void insertText(String text) {
        if (text == null || text.isEmpty()) return;
        this.undoHistory = this.value;
        if (hasSelection()) {
            deleteSelectedText();
        }

        StringBuilder sb = new StringBuilder(this.value);
        clampCursorAndSelection();

        sb.insert(this.cursorPos, text);
        if (sb.length() > maxLength) {
            sb.setLength(maxLength);
        }
        this.value = sb.toString();
        this.cursorPos = Math.min(this.value.length(), this.cursorPos + text.length());
        clearSelection();

        if (this.responder != null) {
            this.responder.accept(this.value);
        }
    }

    private void clampCursorAndSelection() {
        this.cursorPos = Math.max(0, Math.min(this.value.length(), this.cursorPos));
        this.selectionStart = Math.max(0, Math.min(this.value.length(), this.selectionStart));
        this.selectionEnd = Math.max(0, Math.min(this.value.length(), this.selectionEnd));
    }

    public int getCharIndexAtMouse(double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        int padX = 6;
        int padY = 5;
        int maxW = this.width - (padX * 2);

        if (this.value.isEmpty()) return 0;

        Component textComp = DialogFormatUtil.formatText(this.value);
        List<FormattedCharSequence> lines = font.split(textComp, maxW);
        if (lines.isEmpty()) return 0;

        int lineIdx = Math.max(0, Math.min(lines.size() - 1, (int) ((mouseY - (this.getY() + padY)) / 10)));
        int relX = (int) (mouseX - (this.getX() + padX));

        int len = this.value.length();
        int lastMatchIdx = 0;

        for (int i = 0; i <= len; i++) {
            String sub = this.value.substring(0, i);
            List<FormattedCharSequence> subLines = font.split(DialogFormatUtil.formatText(sub), maxW);
            if (subLines.isEmpty()) {
                if (lineIdx == 0) {
                    lastMatchIdx = 0;
                }
                continue;
            }
            int subLineIdx = Math.max(0, subLines.size() - 1);
            if (subLineIdx == lineIdx) {
                int lineW = font.width(subLines.get(subLineIdx));
                if (lineW <= relX + 3) {
                    lastMatchIdx = i;
                }
            } else if (subLineIdx > lineIdx) {
                break;
            }
        }
        return lastMatchIdx;
    }

    private void selectWordAtCursor() {
        if (this.value.isEmpty()) return;
        int start = Math.max(0, Math.min(this.value.length() - 1, this.cursorPos));
        int end = start;

        while (start > 0 && Character.isLetterOrDigit(this.value.charAt(start - 1))) {
            start--;
        }
        while (end < this.value.length() && Character.isLetterOrDigit(this.value.charAt(end))) {
            end++;
        }
        this.selectionStart = start;
        this.selectionEnd = end;
        this.cursorPos = end;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.active && this.visible) {
            boolean hovered = mouseX >= this.getX() && mouseX < this.getX() + this.width && mouseY >= this.getY() && mouseY < this.getY() + this.height;
            this.setFocused(hovered);

            if (hovered && button == 0) {
                long now = System.currentTimeMillis();
                if (now - lastClickTime < 300) {
                    clickCount++;
                } else {
                    clickCount = 1;
                }
                lastClickTime = now;

                int clickIdx = getCharIndexAtMouse(mouseX, mouseY);
                this.cursorPos = clickIdx;

                if (clickCount == 2) {
                    selectWordAtCursor();
                } else if (clickCount >= 3) {
                    this.selectionStart = 0;
                    this.selectionEnd = this.value.length();
                    this.cursorPos = this.value.length();
                } else {
                    if (Screen.hasShiftDown()) {
                        this.selectionEnd = clickIdx;
                    } else {
                        this.selectionStart = clickIdx;
                        this.selectionEnd = clickIdx;
                    }
                    this.isDraggingMouse = true;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isFocused() && this.isDraggingMouse && button == 0) {
            int dragIdx = getCharIndexAtMouse(mouseX, mouseY);
            this.cursorPos = dragIdx;
            this.selectionEnd = dragIdx;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDraggingMouse = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!this.isFocused() || !this.active) return false;
        if (SharedConstants.isAllowedChatCharacter(codePoint)) {
            insertText(Character.toString(codePoint));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.isFocused() || !this.active) return false;

        boolean shift = Screen.hasShiftDown();

        if (Screen.isSelectAll(keyCode)) {
            this.selectionStart = 0;
            this.selectionEnd = this.value.length();
            this.cursorPos = this.value.length();
            return true;
        } else if (Screen.isCopy(keyCode)) {
            String copyStr = hasSelection() ? getSelectedText() : this.value;
            Minecraft.getInstance().keyboardHandler.setClipboard(copyStr);
            return true;
        } else if (Screen.isCut(keyCode)) {
            if (hasSelection()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
                deleteSelectedText();
            }
            return true;
        } else if (Screen.isPaste(keyCode)) {
            insertText(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_Z && Screen.hasControlDown()) {
            if (this.undoHistory != null) {
                String prev = this.value;
                this.value = this.undoHistory;
                this.undoHistory = prev;
                this.cursorPos = this.value.length();
                clearSelection();
                if (this.responder != null) {
                    this.responder.accept(this.value);
                }
            }
            return true;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) {
                    deleteSelectedText();
                } else if (this.cursorPos > 0 && !this.value.isEmpty()) {
                    this.undoHistory = this.value;
                    StringBuilder sb = new StringBuilder(this.value);
                    sb.deleteCharAt(this.cursorPos - 1);
                    this.value = sb.toString();
                    this.cursorPos--;
                    clearSelection();
                    if (this.responder != null) {
                        this.responder.accept(this.value);
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) {
                    deleteSelectedText();
                } else if (this.cursorPos < this.value.length()) {
                    this.undoHistory = this.value;
                    StringBuilder sb = new StringBuilder(this.value);
                    sb.deleteCharAt(this.cursorPos);
                    this.value = sb.toString();
                    clearSelection();
                    if (this.responder != null) {
                        this.responder.accept(this.value);
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (this.cursorPos > 0) {
                    this.cursorPos--;
                    if (shift) {
                        this.selectionEnd = this.cursorPos;
                    } else {
                        clearSelection();
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (this.cursorPos < this.value.length()) {
                    this.cursorPos++;
                    if (shift) {
                        this.selectionEnd = this.cursorPos;
                    } else {
                        clearSelection();
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                this.cursorPos = 0;
                if (shift) {
                    this.selectionEnd = this.cursorPos;
                } else {
                    clearSelection();
                }
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                this.cursorPos = this.value.length();
                if (shift) {
                    this.selectionEnd = this.cursorPos;
                } else {
                    clearSelection();
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                insertText(" ");
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        boolean isHovered = mouseX >= this.getX() && mouseX < this.getX() + this.width && mouseY >= this.getY() && mouseY < this.getY() + this.height;
        int borderColor = this.isFocused() ? CYAN_MAIN : (isHovered ? 0xFF00B0FF : CYAN_BORDER);

        // Box Fill & Cyberpunk Glowing Border
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, BG_COLOR);
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, borderColor);
        guiGraphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, borderColor);
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, borderColor);
        guiGraphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, borderColor);

        int padX = 6;
        int padY = 5;
        int maxW = this.width - (padX * 2);

        // Multiline Formatted Wrapping Render
        Component textComp = DialogFormatUtil.formatText(this.value);
        List<FormattedCharSequence> lines = font.split(textComp, maxW);

        int renderY = this.getY() + padY;
        int maxLines = (this.height - (padY * 2)) / 10;

        // Render Selection Highlight Overlay
        if (this.isFocused() && hasSelection()) {
            int selMin = Math.min(selectionStart, selectionEnd);
            int selMax = Math.max(selectionStart, selectionEnd);

            for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
                int lineY = this.getY() + padY + (i * 10);
                // Compute character range on line i
                int lineStartIdx = getCharIndexAtLineStart(i, lines, maxW);
                int lineEndIdx = getCharIndexAtLineStart(i + 1, lines, maxW);

                if (selMax > lineStartIdx && selMin < lineEndIdx) {
                    int highlightStartChar = Math.max(selMin, lineStartIdx);
                    int highlightEndChar = Math.min(selMax, lineEndIdx);

                    int hStartX = getXOffsetForCharIndex(highlightStartChar, i, lines, maxW);
                    int hEndX = getXOffsetForCharIndex(highlightEndChar, i, lines, maxW);

                    guiGraphics.fill(this.getX() + padX + hStartX, lineY - 1, this.getX() + padX + hEndX, lineY + 9, SELECTION_COLOR);
                }
            }
        }

        // Render Text Lines
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            guiGraphics.drawString(font, lines.get(i), this.getX() + padX, this.getY() + padY + (i * 10), 0xFFFFFFFF, false);
        }

        // Render Blinking Cursor at exact cursorPos Position when Focused
        if (this.isFocused()) {
            long now = System.currentTimeMillis();
            boolean cursorBlink = (now / 400) % 2 == 0;
            if (cursorBlink) {
                int cursorLineIdx = getLineIdxForCharPos(this.cursorPos, lines, maxW);
                int cursorXOffset = getXOffsetForCharIndex(this.cursorPos, cursorLineIdx, lines, maxW);
                int cursorX = this.getX() + padX + cursorXOffset;
                int cursorY = this.getY() + padY + (cursorLineIdx * 10);

                guiGraphics.fill(cursorX, cursorY - 1, cursorX + 1, cursorY + 9, CYAN_MAIN);
            }
        }
    }

    private int getCharIndexAtLineStart(int lineIdx, List<FormattedCharSequence> lines, int maxW) {
        if (lineIdx <= 0) return 0;
        if (lineIdx >= lines.size()) return this.value.length();

        Font font = Minecraft.getInstance().font;
        int len = this.value.length();

        for (int i = 0; i <= len; i++) {
            String sub = this.value.substring(0, i);
            List<FormattedCharSequence> subLines = font.split(DialogFormatUtil.formatText(sub), maxW);
            if (subLines.size() > lineIdx) {
                return i;
            }
        }
        return this.value.length();
    }

    private int getLineIdxForCharPos(int charPos, List<FormattedCharSequence> lines, int maxW) {
        if (charPos <= 0 || lines.isEmpty()) return 0;
        Font font = Minecraft.getInstance().font;
        String sub = this.value.substring(0, Math.min(charPos, this.value.length()));
        List<FormattedCharSequence> subLines = font.split(DialogFormatUtil.formatText(sub), maxW);
        return Math.max(0, Math.min(lines.size() - 1, subLines.size() - 1));
    }

    private int getXOffsetForCharIndex(int charPos, int lineIdx, List<FormattedCharSequence> lines, int maxW) {
        if (charPos <= 0 || lines.isEmpty() || lineIdx >= lines.size()) return 0;
        Font font = Minecraft.getInstance().font;
        String sub = this.value.substring(0, Math.min(charPos, this.value.length()));
        List<FormattedCharSequence> subLines = font.split(DialogFormatUtil.formatText(sub), maxW);
        if (!subLines.isEmpty()) {
            int targetSubIdx = Math.min(lineIdx, subLines.size() - 1);
            return font.width(subLines.get(targetSubIdx));
        }
        return 0;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }
}
