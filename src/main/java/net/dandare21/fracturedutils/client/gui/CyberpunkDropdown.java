package net.dandare21.fracturedutils.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class CyberpunkDropdown<T> extends AbstractWidget {
    private static final int DEFAULT_ACCENT = 0xFF00E5FF;
    private static final int DEFAULT_BG = 0xEE060C12;
    private static final int MENU_BG = 0xF709121B;
    private static final int ITEM_HOVER_BG = 0xEE0E2B3D;
    private static final int ITEM_SELECTED_BG = 0xEE005C6E;

    public static class DropdownEntry<T> {
        private final T value;
        private final Component label;
        private final Component subtitle;
        private final int accentColor;

        public DropdownEntry(T value, Component label) {
            this(value, label, null, DEFAULT_ACCENT);
        }

        public DropdownEntry(T value, Component label, Component subtitle) {
            this(value, label, subtitle, DEFAULT_ACCENT);
        }

        public DropdownEntry(T value, Component label, Component subtitle, int accentColor) {
            this.value = value;
            this.label = label;
            this.subtitle = subtitle;
            this.accentColor = accentColor;
        }

        public T getValue() {
            return value;
        }

        public Component getLabel() {
            return label;
        }

        public Component getSubtitle() {
            return subtitle;
        }

        public int getAccentColor() {
            return accentColor;
        }
    }

    private final List<DropdownEntry<T>> entries = new ArrayList<>();
    private DropdownEntry<T> selectedEntry;
    private Consumer<DropdownEntry<T>> onSelect;
    private Runnable onOpenListener;
    private boolean isOpen = false;
    private int maxVisibleItems = 5;
    private int itemHeight = 22;
    private int scrollOffset = 0;
    private boolean isDraggingScrollbar = false;
    private double dragOffsetY = 0;
    private int accentColor = DEFAULT_ACCENT;

    public CyberpunkDropdown(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    public CyberpunkDropdown<T> setOptions(List<DropdownEntry<T>> newEntries) {
        this.entries.clear();
        if (newEntries != null) {
            this.entries.addAll(newEntries);
        }
        if (!this.entries.isEmpty() && (this.selectedEntry == null || !containsEntry(this.selectedEntry))) {
            this.selectedEntry = this.entries.get(0);
        }
        this.scrollOffset = 0;
        return this;
    }

    private boolean containsEntry(DropdownEntry<T> entry) {
        for (DropdownEntry<T> e : entries) {
            if (Objects.equals(e.getValue(), entry.getValue())) return true;
        }
        return false;
    }

    public CyberpunkDropdown<T> setAccentColor(int color) {
        this.accentColor = color;
        return this;
    }

    public CyberpunkDropdown<T> setMaxVisibleItems(int maxVisibleItems) {
        this.maxVisibleItems = Math.max(1, maxVisibleItems);
        return this;
    }

    public CyberpunkDropdown<T> setItemHeight(int itemHeight) {
        this.itemHeight = Math.max(14, itemHeight);
        return this;
    }

    public CyberpunkDropdown<T> setOnSelect(Consumer<DropdownEntry<T>> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    public CyberpunkDropdown<T> setOnOpenListener(Runnable onOpenListener) {
        this.onOpenListener = onOpenListener;
        return this;
    }

    public void selectByValue(T value) {
        for (DropdownEntry<T> entry : entries) {
            if (Objects.equals(entry.getValue(), value)) {
                this.selectedEntry = entry;
                return;
            }
        }
    }

    public DropdownEntry<T> getSelectedEntry() {
        return selectedEntry;
    }

    public T getSelectedValue() {
        return selectedEntry != null ? selectedEntry.getValue() : null;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        this.isOpen = open;
        if (!open) {
            this.isDraggingScrollbar = false;
        } else if (onOpenListener != null) {
            onOpenListener.run();
        }
    }

    public void toggleOpen() {
        setOpen(!this.isOpen);
    }

    public int getMenuX() {
        return this.getX();
    }

    public int getMenuY() {
        return this.getY() + this.height + 2;
    }

    public int getMenuWidth() {
        return this.width;
    }

    public int getEffectiveMaxVisibleItems() {
        int maxVis = Math.max(4, maxVisibleItems);
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            int screenH = mc.getWindow().getGuiScaledHeight();
            int availableH = screenH - (this.getY() + this.height + 8);
            if (availableH > itemHeight * 2) {
                int maxPossible = Math.max(4, availableH / itemHeight);
                maxVis = Math.min(maxVis, maxPossible);
            } else {
                maxVis = Math.max(4, Math.min(maxVis, 6));
            }
        }
        return Math.max(3, maxVis);
    }

    public int getVisibleItemCount() {
        return Math.min(entries.size(), getEffectiveMaxVisibleItems());
    }

    public int getMenuHeight() {
        return getVisibleItemCount() * itemHeight + 4;
    }

    public int getMaxScroll() {
        return Math.max(0, entries.size() - getEffectiveMaxVisibleItems());
    }

    public boolean isMouseOverHeader(double mouseX, double mouseY) {
        return mouseX >= this.getX() && mouseX <= this.getX() + this.width &&
               mouseY >= this.getY() && mouseY <= this.getY() + this.height;
    }

    public boolean isMouseOverMenu(double mouseX, double mouseY) {
        if (!isOpen || entries.isEmpty()) return false;
        int mx = getMenuX();
        int my = getMenuY();
        int mw = getMenuWidth();
        int mh = getMenuHeight();
        return mouseX >= mx && mouseX <= mx + mw && mouseY >= my && mouseY <= my + mh;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible) return false;

        // 1. Check header bar click
        if (isMouseOverHeader(mouseX, mouseY)) {
            this.toggleOpen();
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }

        // 2. Check menu item or scrollbar click when open
        if (isOpen) {
            if (isMouseOverMenu(mouseX, mouseY)) {
                int mx = getMenuX();
                int my = getMenuY() + 2;
                int mw = getMenuWidth();
                int visibleCount = getVisibleItemCount();
                boolean hasScrollbar = getMaxScroll() > 0;
                int scrollbarW = hasScrollbar ? 6 : 0;
                int listW = mw - scrollbarW - 4;

                // Check scrollbar drag start
                if (hasScrollbar && mouseX >= mx + mw - scrollbarW - 4 && mouseX <= mx + mw) {
                    this.isDraggingScrollbar = true;
                    this.dragOffsetY = mouseY;
                    return true;
                }

                // Check option item click
                for (int i = 0; i < visibleCount; i++) {
                    int itemIndex = scrollOffset + i;
                    if (itemIndex >= entries.size()) break;

                    int itemY = my + i * itemHeight;
                    if (mouseY >= itemY && mouseY < itemY + itemHeight && mouseX >= mx + 2 && mouseX <= mx + 2 + listW) {
                        DropdownEntry<T> clicked = entries.get(itemIndex);
                        this.selectedEntry = clicked;
                        this.playDownSound(Minecraft.getInstance().getSoundManager());
                        this.setOpen(false);
                        if (onSelect != null) {
                            onSelect.accept(clicked);
                        }
                        return true;
                    }
                }
                return true;
            }

            // 3. Click outside closes menu and consumes event to block background widgets
            this.setOpen(false);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (isOpen) {
            int maxScroll = getMaxScroll();
            if (maxScroll > 0 && (isMouseOverMenu(mouseX, mouseY) || isMouseOverHeader(mouseX, mouseY))) {
                if (amount > 0) {
                    this.scrollOffset = Math.max(0, this.scrollOffset - 1);
                } else if (amount < 0) {
                    this.scrollOffset = Math.min(maxScroll, this.scrollOffset + 1);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isOpen && isDraggingScrollbar && getMaxScroll() > 0) {
            int menuH = getMenuHeight() - 4;
            int totalH = entries.size() * itemHeight;
            int thumbH = Math.max(12, (int) ((float) menuH / totalH * menuH));
            double scrollableDist = menuH - thumbH;
            if (scrollableDist > 0) {
                double deltaY = mouseY - dragOffsetY;
                double scrollRatio = deltaY / scrollableDist;
                int scrollDelta = (int) Math.round(scrollRatio * getMaxScroll());
                if (scrollDelta != 0) {
                    this.scrollOffset = Math.max(0, Math.min(getMaxScroll(), this.scrollOffset + scrollDelta));
                    this.dragOffsetY = mouseY;
                }
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean isHovered = isMouseOverHeader(mouseX, mouseY);

        int borderColor = !this.active ? 0x44445566 : (isOpen || isHovered ? 0xFFFFFFFF : accentColor);
        int fillColor = !this.active ? 0xEE0A0F14 : (isOpen ? 0xEE0C1C29 : (isHovered ? 0xEE0B2233 : DEFAULT_BG));
        int textColor = !this.active ? 0xFF556677 : (isOpen || isHovered ? 0xFFFFFFFF : accentColor);

        // Fill background & borders
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, fillColor);
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, borderColor);
        graphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, borderColor);
        graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, borderColor);
        graphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, borderColor);

        // Notch accents
        if (isHovered || isOpen) {
            graphics.fill(this.getX() + 2, this.getY() + 2, this.getX() + 5, this.getY() + 3, borderColor);
            graphics.fill(this.getX() + 2, this.getY() + 2, this.getX() + 3, this.getY() + 5, borderColor);
        }

        // Render main label text & arrow
        Font font = Minecraft.getInstance().font;
        String labelText = selectedEntry != null ? selectedEntry.getLabel().getString() : this.getMessage().getString();
        int arrowW = 12;
        int maxLabelW = this.width - arrowW - 12;
        if (font.width(labelText) > maxLabelW) {
            labelText = font.plainSubstrByWidth(labelText, maxLabelW - 6) + "..";
        }

        graphics.drawString(font, labelText, this.getX() + 8, this.getY() + (this.height - 8) / 2, textColor, false);

        // Arrow indicator
        String arrow = isOpen ? "▲" : "▼";
        graphics.drawString(font, arrow, this.getX() + this.width - 14, this.getY() + (this.height - 8) / 2, borderColor, false);
    }

    /**
     * Renders open menu popup with elevated Z depth.
     */
    public void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isOpen || !this.visible || entries.isEmpty()) return;

        Font font = Minecraft.getInstance().font;
        int mx = getMenuX();
        int my = getMenuY();
        int mw = getMenuWidth();
        int mh = getMenuHeight();
        int visibleCount = getVisibleItemCount();
        boolean hasScrollbar = getMaxScroll() > 0;
        int scrollbarW = hasScrollbar ? 6 : 0;
        int listW = mw - scrollbarW - 4;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);

        // Dropdown menu background & borders
        graphics.fill(mx, my, mx + mw, my + mh, MENU_BG);
        graphics.fill(mx, my, mx + mw, my + 1, accentColor);
        graphics.fill(mx, my + mh - 1, mx + mw, my + mh, accentColor);
        graphics.fill(mx, my, mx + 1, my + mh, accentColor);
        graphics.fill(mx + mw - 1, my, mx + mw, my + mh, accentColor);

        // Render visible items
        for (int i = 0; i < visibleCount; i++) {
            int itemIndex = scrollOffset + i;
            if (itemIndex >= entries.size()) break;

            DropdownEntry<T> entry = entries.get(itemIndex);
            boolean isSelected = (selectedEntry == entry);
            int iy = my + 2 + i * itemHeight;

            boolean isHovered = mouseX >= mx + 2 && mouseX <= mx + 2 + listW && mouseY >= iy && mouseY < iy + itemHeight;

            int itemBg = isSelected ? ITEM_SELECTED_BG : (isHovered ? ITEM_HOVER_BG : 0x00000000);
            int itemTextColor = isSelected ? 0xFFFFFFFF : (isHovered ? 0xFF00E5FF : 0xFFCCCCCC);

            if (itemBg != 0) {
                graphics.fill(mx + 2, iy, mx + 2 + listW, iy + itemHeight - 1, itemBg);
            }

            // Highlight bar on left
            if (isSelected || isHovered) {
                graphics.fill(mx + 2, iy, mx + 4, iy + itemHeight - 1, entry.getAccentColor());
            }

            // Item text / subtitle
            int textX = mx + 8;
            if (entry.getSubtitle() != null && !entry.getSubtitle().getString().isEmpty() && itemHeight >= 22) {
                graphics.drawString(font, entry.getLabel(), textX, iy + 3, itemTextColor, false);
                graphics.drawString(font, entry.getSubtitle(), textX, iy + 12, 0x88AABBCC, false);
            } else {
                graphics.drawString(font, entry.getLabel(), textX, iy + (itemHeight - 8) / 2, itemTextColor, false);
            }
        }

        // Render Scrollbar
        if (hasScrollbar) {
            int sbX = mx + mw - scrollbarW - 2;
            int sbY = my + 2;
            int sbH = mh - 4;

            graphics.fill(sbX, sbY, sbX + scrollbarW, sbY + sbH, 0x77050B10);
            graphics.fill(sbX, sbY, sbX + 1, sbY + sbH, accentColor);
            graphics.fill(sbX + scrollbarW - 1, sbY, sbX + scrollbarW, sbY + sbH, accentColor);

            int totalH = entries.size() * itemHeight;
            int thumbH = Math.max(10, (int) ((float) sbH / totalH * sbH));
            int thumbY = sbY + (int) ((float) scrollOffset / getMaxScroll() * (sbH - thumbH));

            graphics.fill(sbX, thumbY, sbX + scrollbarW, thumbY + thumbH, isDraggingScrollbar ? 0xFFFFFFFF : accentColor);
        }

        graphics.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
