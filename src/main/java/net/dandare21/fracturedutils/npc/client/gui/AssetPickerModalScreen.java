package net.dandare21.fracturedutils.npc.client.gui;

import net.dandare21.fracturedutils.client.gui.CyberpunkButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AssetPickerModalScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xF705090C;
    private static final int CARD_BORDER = 0xAA00E5FF;
    private static final int ITEM_HOVER = 0x6600E5FF;
    private static final int ITEM_SELECTED = 0xAA005C6E;

    private final Screen parent;
    private final String titleText;
    private final List<String> allItems;
    private final Consumer<String> onSelect;

    private EditBox searchBox;
    private final List<String> filteredItems = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 9;
    private static final int ROW_HEIGHT = 18;

    public AssetPickerModalScreen(Screen parent, String titleText, List<String> allItems, Consumer<String> onSelect) {
        super(Component.literal(titleText));
        this.parent = parent;
        this.titleText = titleText;
        this.allItems = allItems != null ? allItems : List.of();
        this.onSelect = onSelect;
        this.filteredItems.addAll(this.allItems);
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int boxW = 380;
        int boxH = 260;
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;

        searchBox = new EditBox(this.font, boxX + 15, boxY + 28, boxW - 30, 18, Component.literal("Search"));
        searchBox.setMaxLength(128);
        searchBox.setResponder(this::updateFilter);
        this.addRenderableWidget(searchBox);

        // Action Buttons at bottom
        int btnY = boxY + boxH - 26;
        this.addRenderableWidget(new CyberpunkButton(boxX + 15, btnY, 160, 20,
                Component.literal("✓ Select"), b -> confirmSelection(), CYAN_MAIN, false));

        this.addRenderableWidget(new CyberpunkButton(boxX + boxW - 175, btnY, 160, 20,
                Component.literal("Cancel"), b -> this.onClose(), 0xFFFF5555, false));
    }

    private void updateFilter(String query) {
        filteredItems.clear();
        String q = query.trim().toLowerCase();
        for (String item : allItems) {
            if (q.isEmpty() || item.toLowerCase().contains(q)) {
                filteredItems.add(item);
            }
        }
        scrollOffset = 0;
        selectedIndex = filteredItems.isEmpty() ? -1 : 0;
    }

    private void confirmSelection() {
        if (selectedIndex >= 0 && selectedIndex < filteredItems.size()) {
            String chosen = filteredItems.get(selectedIndex);
            onSelect.accept(chosen);
            this.onClose();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
            return true;
        } else if (delta < 0) {
            int maxScroll = Math.max(0, filteredItems.size() - VISIBLE_ROWS);
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int boxW = 380;
        int boxH = 260;
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;
        int listY = boxY + 52;

        if (mouseX >= boxX + 15 && mouseX <= boxX + boxW - 15) {
            for (int i = 0; i < VISIBLE_ROWS; i++) {
                int itemIdx = scrollOffset + i;
                if (itemIdx >= filteredItems.size()) break;
                int rowY = listY + i * ROW_HEIGHT;
                if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                    if (selectedIndex == itemIdx && button == 0) {
                        confirmSelection();
                        return true;
                    }
                    selectedIndex = itemIdx;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        int boxW = 380;
        int boxH = 260;
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;

        // Frame
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, CYAN_BG);
        guiGraphics.renderOutline(boxX, boxY, boxW, boxH, CARD_BORDER);

        // Title
        guiGraphics.drawString(this.font, "✦ " + titleText, boxX + 15, boxY + 12, CYAN_MAIN, false);
        guiGraphics.drawString(this.font, "(" + filteredItems.size() + " items)", boxX + boxW - 80, boxY + 12, 0xFF888888, false);

        // List frame
        int listY = boxY + 52;
        int listH = VISIBLE_ROWS * ROW_HEIGHT;
        guiGraphics.fill(boxX + 15, listY, boxX + boxW - 15, listY + listH, 0x88040810);
        guiGraphics.renderOutline(boxX + 15, listY, boxW - 30, listH, 0x4400E5FF);

        // Rows
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int itemIdx = scrollOffset + i;
            if (itemIdx >= filteredItems.size()) break;

            int rowY = listY + i * ROW_HEIGHT;
            boolean isHovered = mouseX >= boxX + 15 && mouseX <= boxX + boxW - 15 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            boolean isSelected = (itemIdx == selectedIndex);

            if (isSelected) {
                guiGraphics.fill(boxX + 16, rowY + 1, boxX + boxW - 16, rowY + ROW_HEIGHT - 1, ITEM_SELECTED);
            } else if (isHovered) {
                guiGraphics.fill(boxX + 16, rowY + 1, boxX + boxW - 16, rowY + ROW_HEIGHT - 1, ITEM_HOVER);
            }

            String text = filteredItems.get(itemIdx);
            int color = isSelected ? 0xFFFFFFFF : (isHovered ? CYAN_MAIN : 0xFFCCCCCC);
            guiGraphics.drawString(this.font, text, boxX + 22, rowY + 5, color, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
