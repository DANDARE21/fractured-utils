package net.dandare21.fracturedutils.region.compat.xaero;

import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.region.data.Polygon2D;
import net.dandare21.fracturedutils.region.data.Region;
import net.dandare21.fracturedutils.region.data.RegionType;
import net.dandare21.fracturedutils.region.network.C2SDeleteRegionPacket;
import net.dandare21.fracturedutils.region.network.C2SSaveRegionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Compact floating side-panel on Xaero's GuiMap for inspecting and editing
 * region metadata.
 */
public class RegionInspectorPanel {

    public static final int[] PALETTE = {
            0xFF2255, // Crimson Red
            0xDD1111, // Dark Red / Ruby
            0xFF6600, // Flame Orange
            0xFFAA00, // Amber / Desert
            0xFFCC00, // Gold / Settlement
            0xEEDD22, // Sunlight / Yellow
            0x77DD22, // Lime / Meadow
            0x22AA44, // Forest Green
            0x11BBAA, // Teal / Coast
            0x00D0FF, // Cyan / Arcane
            0x3388FF, // Sky Blue
            0x2244CC, // Royal / Deep Blue
            0x8833DD, // Mystic Purple
            0xCC22AA, // Nether Magenta
            0xFF66AA, // Blossom Pink
            0xEEEEEE // Pale Silver / White
    };

    private final int width = 195;
    private final int height = 302;
    private int x;
    private int y;

    private Region selectedRegion;
    private String originalRegionId;
    private final BiConsumer<Region, String> onSaveCallback;
    private final Consumer<String> onDeleteCallback;
    private final Runnable onCloseCallback;

    private EditBox idBox;
    private EditBox nameBox;
    private EditBox subtitleBox;
    private EditBox parentBox;
    private EditBox flagBox;
    private EditBox deniedBox;
    private EditBox colorBox;

    private RegionType selectedType = RegionType.MAP_ZONE;
    private boolean isLocked = false;
    private boolean mobSpawning = true;
    private boolean blockPlacingAndBreaking = true;
    private boolean active = false;

    public RegionInspectorPanel(BiConsumer<Region, String> onSaveCallback, Consumer<String> onDeleteCallback,
            Runnable onCloseCallback) {
        this.onSaveCallback = onSaveCallback;
        this.onDeleteCallback = onDeleteCallback;
        this.onCloseCallback = onCloseCallback;
    }

    public void init(int screenWidth, int screenHeight, Font font) {
        this.x = screenWidth - width - 10;
        this.y = Math.max(5, Math.min(20, screenHeight - height - 10));

        idBox = new EditBox(font, x + 10, y + 34, width - 20, 15, Component.literal("ID"));
        idBox.setMaxLength(64);
        idBox.setCanLoseFocus(true);

        nameBox = new EditBox(font, x + 10, y + 61, width - 20, 15, Component.literal("Name"));
        nameBox.setMaxLength(64);
        nameBox.setCanLoseFocus(true);

        subtitleBox = new EditBox(font, x + 10, y + 88, width - 20, 15, Component.literal("Subtitle"));
        subtitleBox.setMaxLength(128);
        subtitleBox.setCanLoseFocus(true);

        parentBox = new EditBox(font, x + 10, y + 135, width - 20, 15, Component.literal("Parent"));
        parentBox.setMaxLength(64);
        parentBox.setCanLoseFocus(true);

        flagBox = new EditBox(font, x + 10, y + 182, width - 20, 15, Component.literal("Flag"));
        flagBox.setMaxLength(64);
        flagBox.setCanLoseFocus(true);

        deniedBox = new EditBox(font, x + 10, y + 209, width - 20, 15, Component.literal("Denied Message"));
        deniedBox.setMaxLength(128);
        deniedBox.setCanLoseFocus(true);

        colorBox = new EditBox(font, x + 10, y + 236, 75, 15, Component.literal("Color"));
        colorBox.setMaxLength(16);
        colorBox.setCanLoseFocus(true);

        if (selectedRegion != null) {
            populateFields(selectedRegion);
        }
    }

    public int getCurrentColor() {
        int color = selectedType != null ? selectedType.getDefaultColor() : 0x00D0FF;
        if (colorBox != null) {
            try {
                String colStr = colorBox.getValue().trim().replace("0x", "").replace("#", "");
                if (!colStr.isEmpty()) {
                    color = Integer.parseUnsignedInt(colStr, 16);
                }
            } catch (Exception ignored) {
            }
        }
        return color;
    }

    public boolean isAnyBoxFocused() {
        return active && ((idBox != null && idBox.isFocused()) ||
                (nameBox != null && nameBox.isFocused()) ||
                (subtitleBox != null && subtitleBox.isFocused()) ||
                (parentBox != null && parentBox.isFocused()) ||
                (flagBox != null && flagBox.isFocused()) ||
                (deniedBox != null && deniedBox.isFocused()) ||
                (colorBox != null && colorBox.isFocused()));
    }

    public void open(Region region, String suggestedParentId, Font font, int screenWidth, int screenHeight) {
        this.selectedRegion = region;
        this.originalRegionId = region != null ? region.getId() : "";
        this.active = true;
        init(screenWidth, screenHeight, font);

        if (suggestedParentId != null && !suggestedParentId.isEmpty()
                && (region.getParentId() == null || region.getParentId().isEmpty())) {
            parentBox.setValue(suggestedParentId);
        }
    }

    public void close() {
        this.active = false;
        this.selectedRegion = null;
        if (onCloseCallback != null) {
            onCloseCallback.run();
        }
    }

    public boolean isActive() {
        return active;
    }

    public Region getSelectedRegion() {
        return selectedRegion;
    }

    private void populateFields(Region r) {
        idBox.setValue(r.getId() != null ? r.getId() : "");
        nameBox.setValue(r.getName() != null ? r.getName() : "");
        subtitleBox.setValue(r.getSubtitle() != null ? r.getSubtitle() : "");
        selectedType = r.getType() != null ? r.getType() : RegionType.MAP_ZONE;
        parentBox.setValue(r.getParentId() != null ? r.getParentId() : "");
        isLocked = r.isLocked();
        mobSpawning = r.isMobSpawningAllowed();
        blockPlacingAndBreaking = r.isBlockPlacingAndBreakingAllowed();
        flagBox.setValue(r.getRequiredFlag() != null ? r.getRequiredFlag() : "");
        deniedBox.setValue(r.getDeniedMessage() != null ? r.getDeniedMessage() : "");
        colorBox.setValue(String.format("0x%06X", (r.getColor() & 0x00FFFFFF)));
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return active && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !isMouseOver(mouseX, mouseY)) {
            return false;
        }

        idBox.setFocused(idBox.isMouseOver(mouseX, mouseY));
        idBox.mouseClicked(mouseX, mouseY, button);

        nameBox.setFocused(nameBox.isMouseOver(mouseX, mouseY));
        nameBox.mouseClicked(mouseX, mouseY, button);

        subtitleBox.setFocused(subtitleBox.isMouseOver(mouseX, mouseY));
        subtitleBox.mouseClicked(mouseX, mouseY, button);

        parentBox.setFocused(parentBox.isMouseOver(mouseX, mouseY));
        parentBox.mouseClicked(mouseX, mouseY, button);

        flagBox.setFocused(flagBox.isMouseOver(mouseX, mouseY));
        flagBox.mouseClicked(mouseX, mouseY, button);

        deniedBox.setFocused(deniedBox.isMouseOver(mouseX, mouseY));
        deniedBox.mouseClicked(mouseX, mouseY, button);

        colorBox.setFocused(colorBox.isMouseOver(mouseX, mouseY));
        colorBox.mouseClicked(mouseX, mouseY, button);

        // Type toggle button
        if (mouseY >= y + 107 && mouseY <= y + 121 && mouseX >= x + 10 && mouseX <= x + width - 10) {
            int nextOrdinal = (selectedType.ordinal() + 1) % RegionType.values().length;
            selectedType = RegionType.values()[nextOrdinal];
            return true;
        }

        // Locked checkbox button (left)
        if (mouseY >= y + 153 && mouseY <= y + 167 && mouseX >= x + 10 && mouseX <= x + 65) {
            isLocked = !isLocked;
            return true;
        }

        // Mob Spawning checkbox button (middle)
        if (mouseY >= y + 153 && mouseY <= y + 167 && mouseX >= x + 69 && mouseX <= x + 124) {
            mobSpawning = !mobSpawning;
            return true;
        }

        // Block Placement & Breaking checkbox button (right)
        if (mouseY >= y + 153 && mouseY <= y + 167 && mouseX >= x + 128 && mouseX <= x + width - 10) {
            blockPlacingAndBreaking = !blockPlacingAndBreaking;
            return true;
        }

        // Palette swatches click
        if (mouseY >= y + 253 && mouseY <= y + 277 && mouseX >= x + 11 && mouseX <= x + 184) {
            int col = (int) ((mouseX - (x + 11)) / 22);
            int row = (mouseY >= y + 265) ? 1 : 0;
            if (col >= 0 && col < 8) {
                int idx = row * 8 + col;
                if (idx >= 0 && idx < PALETTE.length) {
                    int chosen = PALETTE[idx];
                    colorBox.setValue(String.format("0x%06X", chosen & 0xFFFFFF));
                    if (selectedRegion != null) {
                        selectedRegion.setColor(chosen);
                    }
                    return true;
                }
            }
        }

        // Save & Sync button
        if (mouseY >= y + 280 && mouseY <= y + 296 && mouseX >= x + 10 && mouseX <= x + 95) {
            commitSave();
            return true;
        }

        // Delete button
        if (mouseY >= y + 280 && mouseY <= y + 296 && mouseX >= x + 100 && mouseX <= x + width - 10) {
            commitDelete();
            return true;
        }

        // Close button (X in top right)
        if (mouseX >= x + width - 20 && mouseX <= x + width - 4 && mouseY >= y + 4 && mouseY <= y + 18) {
            close();
            return true;
        }

        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active)
            return false;

        if (idBox.keyPressed(keyCode, scanCode, modifiers) ||
                nameBox.keyPressed(keyCode, scanCode, modifiers) ||
                subtitleBox.keyPressed(keyCode, scanCode, modifiers) ||
                parentBox.keyPressed(keyCode, scanCode, modifiers) ||
                flagBox.keyPressed(keyCode, scanCode, modifiers) ||
                deniedBox.keyPressed(keyCode, scanCode, modifiers) ||
                colorBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (keyCode == 257) { // Enter key
            commitSave();
            return true;
        }
        if (keyCode == 256) { // Escape key
            close();
            return true;
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!active)
            return false;
        return idBox.charTyped(codePoint, modifiers) ||
                nameBox.charTyped(codePoint, modifiers) ||
                subtitleBox.charTyped(codePoint, modifiers) ||
                parentBox.charTyped(codePoint, modifiers) ||
                flagBox.charTyped(codePoint, modifiers) ||
                deniedBox.charTyped(codePoint, modifiers) ||
                colorBox.charTyped(codePoint, modifiers);
    }

    private void commitSave() {
        String id = idBox.getValue().trim();
        if (id.isEmpty()) {
            return;
        }

        String name = nameBox.getValue().trim();
        if (name.isEmpty())
            name = id;

        String subtitle = subtitleBox != null ? subtitleBox.getValue().trim() : "";
        String parent = parentBox.getValue().trim();
        String flag = flagBox.getValue().trim();
        String denied = deniedBox.getValue().trim();
        int color = getCurrentColor();

        Polygon2D polygon = (selectedRegion != null && selectedRegion.getPolygon() != null)
                ? selectedRegion.getPolygon()
                : new Polygon2D(java.util.List.of());

        String ambientMusic = (selectedRegion != null && selectedRegion.getAmbientMusic() != null)
                ? selectedRegion.getAmbientMusic()
                : "";

        Region updated = new Region(id, name, selectedType, parent, polygon, isLocked, flag, denied, color, subtitle,
                ambientMusic, mobSpawning, blockPlacingAndBreaking);
        if (selectedRegion != null) {
            updated.setCitizenCap(selectedRegion.getCitizenCap());
        }
        ModMessages.sendToServer(new C2SSaveRegionPacket(updated, originalRegionId));

        if (onSaveCallback != null) {
            onSaveCallback.accept(updated, originalRegionId);
        }
        close();
    }

    private void commitDelete() {
        if (selectedRegion != null) {
            String id = selectedRegion.getId();
            ModMessages.sendToServer(new C2SDeleteRegionPacket(id));
            if (onDeleteCallback != null) {
                onDeleteCallback.accept(id);
            }
        }
        close();
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, Font font) {
        if (!active)
            return;

        // Background panel
        guiGraphics.fill(x, y, x + width, y + height, 0xEE16161E);
        guiGraphics.fill(x, y, x + width, y + 2, 0xFF00D0FF); // Top accent border
        guiGraphics.fill(x, y, x + 1, y + height, 0x5500D0FF);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0x5500D0FF);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0x5500D0FF);

        // Title and close button
        guiGraphics.drawString(font, "REGION INSPECTOR", x + 10, y + 8, 0xFFE082, true);
        guiGraphics.drawString(font, "§c✕", x + width - 16, y + 6, 0xFFFFFFFF, true);

        // Field labels & EditBoxes
        guiGraphics.drawString(font, "§7ID:", x + 10, y + 24, 0xFFCCCCCC, false);
        idBox.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(font, "§7Display Name:", x + 10, y + 51, 0xFFCCCCCC, false);
        nameBox.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(font, "§7Subtitle (Banner):", x + 10, y + 78, 0xFFCCCCCC, false);
        subtitleBox.render(guiGraphics, mouseX, mouseY, partialTick);

        // Type Button
        guiGraphics.drawString(font, "§7Type: §e" + selectedType.getDisplayName(), x + 10, y + 108, 0xFFFFFFFF, false);
        guiGraphics.fill(x + 10, y + 119, x + width - 10, y + 121, (selectedType.getDefaultColor() | 0xFF000000));

        guiGraphics.drawString(font, "§7Parent Region:", x + 10, y + 125, 0xFFCCCCCC, false);
        parentBox.render(guiGraphics, mouseX, mouseY, partialTick);

        // Locked checkbox (Left toggle)
        boolean hoverLock = mouseX >= x + 10 && mouseX <= x + 65 && mouseY >= y + 153 && mouseY <= y + 167;
        int lockBg = isLocked ? 0x44FF2222 : 0x22FFFFFF;
        if (hoverLock)
            lockBg = isLocked ? 0x66FF3333 : 0x44FFFFFF;
        guiGraphics.fill(x + 10, y + 153, x + 65, y + 167, lockBg);
        guiGraphics.fill(x + 10, y + 153, x + 65, y + 154, hoverLock ? 0xFFAAAAAA : 0xFF444455);
        guiGraphics.fill(x + 10, y + 166, x + 65, y + 167, hoverLock ? 0xFFAAAAAA : 0xFF444455);
        guiGraphics.fill(x + 10, y + 153, x + 11, y + 167, hoverLock ? 0xFFAAAAAA : 0xFF444455);
        guiGraphics.fill(x + 64, y + 153, x + 65, y + 167, hoverLock ? 0xFFAAAAAA : 0xFF444455);

        String lockLabel = isLocked ? "§c[✔] Lock" : "§a[ ] Lock";
        guiGraphics.drawCenteredString(font, lockLabel, x + 37, y + 156, 0xFFFFFFFF);

        // Mob Spawning checkbox (Middle toggle)
        boolean hoverMobs = mouseX >= x + 69 && mouseX <= x + 124 && mouseY >= y + 153 && mouseY <= y + 167;
        int mobsBg = mobSpawning ? 0x3322AA44 : 0x44FF2222;
        if (hoverMobs)
            mobsBg = mobSpawning ? 0x5533CC55 : 0x66FF3333;
        guiGraphics.fill(x + 69, y + 153, x + 124, y + 167, mobsBg);
        guiGraphics.fill(x + 69, y + 153, x + 124, y + 154, hoverMobs ? 0xFFAAAAAA : 0xFF444455);
        guiGraphics.fill(x + 69, y + 166, x + 124, y + 167, hoverMobs ? 0xFFAAAAAA : 0xFF444455);
        guiGraphics.fill(x + 69, y + 153, x + 70, y + 167, hoverMobs ? 0xFFAAAAAA : 0xFF444455);
        guiGraphics.fill(x + 123, y + 153, x + 124, y + 167, hoverMobs ? 0xFFAAAAAA : 0xFF444455);

        String mobLabel = mobSpawning ? "§a[✔] Mobs" : "§c[✖] Mobs";
        guiGraphics.drawCenteredString(font, mobLabel, x + 96, y + 156, 0xFFFFFFFF);

        // Block Placement & Breaking checkbox (Right toggle)
        boolean hoverBuild = mouseX >= x + 128 && mouseX <= x + width - 10 && mouseY >= y + 153 && mouseY <= y + 167;
        int buildBg = blockPlacingAndBreaking ? 0x3322AA44 : 0x44FF2222;
        if (hoverBuild)
            buildBg = blockPlacingAndBreaking ? 0x5533CC55 : 0x66FF3333;
        guiGraphics.fill(x + 128, y + 153, x + width - 10, y + 167, buildBg);
        guiGraphics.fill(x + 128, y + 153, x + width - 10, y + 154, hoverBuild ? 0xFFAAAAAA : 0xFF444455);
        guiGraphics.fill(x + 128, y + 166, x + width - 10, y + 167, hoverBuild ? 0xFFAAAAAA : 0xFF444455);
        guiGraphics.fill(x + 128, y + 153, x + 129, y + 167, hoverBuild ? 0xFFAAAAAA : 0xFF444455);
        guiGraphics.fill(x + width - 11, y + 153, x + width - 10, y + 167, hoverBuild ? 0xFFAAAAAA : 0xFF444455);

        String buildLabel = blockPlacingAndBreaking ? "§a[✔] Build" : "§c[✖] Build";
        guiGraphics.drawCenteredString(font, buildLabel, x + 156, y + 156, 0xFFFFFFFF);

        guiGraphics.drawString(font, "§7Required Global Flag:", x + 10, y + 172, 0xFFCCCCCC, false);
        flagBox.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(font, "§7Denial Message:", x + 10, y + 199, 0xFFCCCCCC, false);
        deniedBox.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(font, "§7Color (Hex / Swatches):", x + 10, y + 226, 0xFFCCCCCC, false);
        colorBox.render(guiGraphics, mouseX, mouseY, partialTick);

        int curColor = getCurrentColor();

        // Live preview swatch block
        int previewX = x + 90;
        int previewY = y + 236;
        int previewW = width - 100; // 95
        int previewH = 15;
        guiGraphics.fill(previewX - 1, previewY - 1, previewX + previewW + 1, previewY + previewH + 1, 0xFF444455);
        guiGraphics.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0xFF000000 | curColor);
        String hexStr = String.format("#%06X", curColor & 0xFFFFFF);
        int lum = ((curColor >> 16) & 0xFF) * 299 + ((curColor >> 8) & 0xFF) * 587 + (curColor & 0xFF) * 114;
        int textColor = (lum > 140000) ? 0xFF000000 : 0xFFFFFFFF;
        guiGraphics.drawCenteredString(font, hexStr, previewX + previewW / 2, previewY + 3, textColor);

        // Palette swatches grid (2 rows of 8)
        for (int i = 0; i < PALETTE.length; i++) {
            int col = i % 8;
            int row = i / 8;
            int swX = x + 11 + col * 22;
            int swY = y + 254 + row * 12;
            int swW = 19;
            int swH = 10;
            int pColor = PALETTE[i];

            boolean isHovered = mouseX >= swX && mouseX <= swX + swW && mouseY >= swY && mouseY <= swY + swH;
            boolean isCurrent = (curColor & 0xFFFFFF) == (pColor & 0xFFFFFF);

            // Swatch background fill
            guiGraphics.fill(swX, swY, swX + swW, swY + swH, 0xFF000000 | pColor);

            // Border
            if (isCurrent) {
                // Active selection border
                guiGraphics.fill(swX - 1, swY - 1, swX + swW + 1, swY, 0xFFFFFFFF);
                guiGraphics.fill(swX - 1, swY + swH, swX + swW + 1, swY + swH + 1, 0xFFFFFFFF);
                guiGraphics.fill(swX - 1, swY, swX, swY + swH, 0xFFFFFFFF);
                guiGraphics.fill(swX + swW, swY, swX + swW + 1, swY + swH, 0xFFFFFFFF);
            } else if (isHovered) {
                // Hover highlight
                guiGraphics.fill(swX - 1, swY - 1, swX + swW + 1, swY, 0xFFAAAAAA);
                guiGraphics.fill(swX - 1, swY + swH, swX + swW + 1, swY + swH + 1, 0xFFAAAAAA);
                guiGraphics.fill(swX - 1, swY, swX, swY + swH, 0xFFAAAAAA);
                guiGraphics.fill(swX + swW, swY, swX + swW + 1, swY + swH, 0xFFAAAAAA);
            } else {
                // Subtle border
                guiGraphics.fill(swX - 1, swY - 1, swX + swW + 1, swY, 0xFF333344);
                guiGraphics.fill(swX - 1, swY + swH, swX + swW + 1, swY + swH + 1, 0xFF333344);
                guiGraphics.fill(swX - 1, swY, swX, swY + swH, 0xFF333344);
                guiGraphics.fill(swX + swW, swY, swX + swW + 1, swY + swH, 0xFF333344);
            }
        }

        // Save & Delete Buttons
        boolean hoverSave = mouseY >= y + 280 && mouseY <= y + 296 && mouseX >= x + 10 && mouseX <= x + 95;
        guiGraphics.fill(x + 10, y + 280, x + 95, y + 296, hoverSave ? 0xFF009944 : 0xFF00662E);
        guiGraphics.drawCenteredString(font, "Save & Sync", x + 52, y + 284, 0xFFFFFFFF);

        boolean hoverDel = mouseY >= y + 280 && mouseY <= y + 296 && mouseX >= x + 100 && mouseX <= x + width - 10;
        guiGraphics.fill(x + 100, y + 280, x + width - 10, y + 296, hoverDel ? 0xFF992222 : 0xFF661111);
        guiGraphics.drawCenteredString(font, "Delete", x + 142, y + 284, 0xFFFFFFFF);

        // Tooltips (rendered on top of all elements)
        if (hoverLock) {
            guiGraphics.renderTooltip(font,
                    Component.literal(isLocked ? "Barrier Active: Prevents unauthorized players from entering."
                            : "Unlocked: Free player access."),
                    mouseX, mouseY);
        } else if (hoverMobs) {
            guiGraphics.renderTooltip(font,
                    Component.literal(mobSpawning ? "Mob Spawning: Allowed (natural & spawner mobs can spawn)"
                            : "Mob Spawning: Blocked (prevents mobs from spawning)"),
                    mouseX, mouseY);
        } else if (hoverBuild) {
            guiGraphics.renderTooltip(font,
                    Component.literal(blockPlacingAndBreaking
                            ? "Block Placement & Breaking: Allowed"
                            : "Block Placement & Breaking: Blocked (interactions like chests/levers kept)"),
                    mouseX, mouseY);
        }
    }
}
