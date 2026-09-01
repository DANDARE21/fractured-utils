package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.sound.sequence.MusicSequenceEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EditMusicEntryModalScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xFF05090C;
    private static final int RED_CANCEL = 0xFFFF3355;

    private final Screen parentScreen;
    private final MusicSequenceEntry entry;
    private final Consumer<MusicSequenceEntry> onSave;

    private EditBox timestampBox;
    private CyberpunkDropdown<String> typeDropdown;
    private EditBox commandBox;
    private EditBox descriptionBox;

    public EditMusicEntryModalScreen(Screen parentScreen, MusicSequenceEntry entry, Consumer<MusicSequenceEntry> onSave) {
        super(Component.literal("Edit Timed Sequence Entry"));
        this.parentScreen = parentScreen;
        this.entry = entry != null ? entry.copy() : new MusicSequenceEntry();
        this.onSave = onSave;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private double getLayoutScale() {
        int targetW = 460;
        int targetH = 300;
        if (this.width <= 0 || this.height <= 0) return 1.0;
        double scaleX = (double) this.width / targetW;
        double scaleY = (double) this.height / targetH;
        return Math.min(1.0, Math.min(scaleX, scaleY));
    }

    @Override
    protected void init() {
        double scale = getLayoutScale();
        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        int panelWidth = 440;
        int panelHeight = 280;

        int panelLeft = (effWidth - panelWidth) / 2;
        int panelTop = (effHeight - panelHeight) / 2;

        int currentY = panelTop + 32;

        // 1. Timestamp (Ms) EditBox
        this.timestampBox = new EditBox(this.font, panelLeft + 20, currentY, 140, 18, Component.literal("Timestamp (ms)"));
        this.timestampBox.setMaxLength(10);
        this.timestampBox.setValue(String.valueOf(entry.getTimestampMs()));
        this.addRenderableWidget(this.timestampBox);

        // Quick adjustment buttons
        int quickX = panelLeft + 170;
        int btnW = 55;
        this.addRenderableWidget(new CyberpunkButton(quickX, currentY, btnW, 18, Component.literal("+500ms"), b -> adjustTimestamp(500)));
        this.addRenderableWidget(new CyberpunkButton(quickX + 60, currentY, btnW, 18, Component.literal("+1sec"), b -> adjustTimestamp(1000)));
        this.addRenderableWidget(new CyberpunkButton(quickX + 120, currentY, btnW, 18, Component.literal("+5sec"), b -> adjustTimestamp(5000)));
        this.addRenderableWidget(new CyberpunkButton(quickX + 180, currentY, btnW, 18, Component.literal("-1sec"), b -> adjustTimestamp(-1000)));
        currentY += 34;

        // 2. Action Type Dropdown
        List<CyberpunkDropdown.DropdownEntry<String>> typeEntries = new ArrayList<>();
        typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("COMMAND", Component.literal("COMMAND"), Component.literal("Execute server command")));
        typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("DIALOG", Component.literal("DIALOG"), Component.literal("Trigger dialog sequence")));
        typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("OBJECTIVE", Component.literal("OBJECTIVE"), Component.literal("Display HUD objective")));
        typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("CAMERA", Component.literal("CAMERA"), Component.literal("Set camera position")));
        typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("SOUND_EFFECT", Component.literal("SOUND_EFFECT"), Component.literal("Play sound effect")));
        typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("OPERATOR_RESUME", Component.literal("OPERATOR_RESUME"), Component.literal("Resume sequence")));

        this.typeDropdown = new CyberpunkDropdown<>(panelLeft + 20, currentY, panelWidth - 40, 20, Component.literal("Action Type"));
        this.typeDropdown.setOptions(typeEntries);
        this.typeDropdown.selectByValue(entry.getActionType());
        this.typeDropdown.setOnSelect(selected -> entry.setActionType(selected.getValue()));
        this.addRenderableWidget(this.typeDropdown);
        currentY += 36;

        // 3. Command / Payload EditBox
        this.commandBox = new EditBox(this.font, panelLeft + 20, currentY, panelWidth - 40, 20, Component.literal("Command / Payload"));
        this.commandBox.setMaxLength(512);
        this.commandBox.setValue(entry.getCommand());
        this.addRenderableWidget(this.commandBox);
        currentY += 36;

        // 4. Description / Note EditBox
        this.descriptionBox = new EditBox(this.font, panelLeft + 20, currentY, panelWidth - 40, 18, Component.literal("Description / Note"));
        this.descriptionBox.setMaxLength(128);
        this.descriptionBox.setValue(entry.getDescription());
        this.addRenderableWidget(this.descriptionBox);
        currentY += 36;

        // 5. Save & Cancel Buttons
        int footerY = panelTop + panelHeight - 30;
        CyberpunkButton saveBtn = new CyberpunkButton(panelLeft + panelWidth - 110, footerY, 90, 20, Component.literal("SAVE ENTRY"), b -> saveAndClose(), CYAN_MAIN, false);
        CyberpunkButton cancelBtn = new CyberpunkButton(panelLeft + 20, footerY, 90, 20, Component.literal("CANCEL"), b -> this.onClose(), RED_CANCEL, false);

        this.addRenderableWidget(saveBtn);
        this.addRenderableWidget(cancelBtn);
    }

    private void adjustTimestamp(long deltaMs) {
        try {
            long current = Long.parseLong(this.timestampBox.getValue().trim());
            long updated = Math.max(0L, current + deltaMs);
            this.timestampBox.setValue(String.valueOf(updated));
        } catch (NumberFormatException ignored) {
            this.timestampBox.setValue("0");
        }
    }

    private void saveAndClose() {
        long ts = 0L;
        try {
            ts = Math.max(0L, Long.parseLong(this.timestampBox.getValue().trim()));
        } catch (NumberFormatException ignored) {}

        entry.setTimestampMs(ts);
        if (typeDropdown != null && typeDropdown.getSelectedValue() != null) {
            entry.setActionType(typeDropdown.getSelectedValue());
        }
        entry.setCommand(this.commandBox.getValue().trim());
        entry.setDescription(this.descriptionBox.getValue().trim());

        if (onSave != null) {
            onSave.accept(entry);
        }
        this.onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
            dragX /= scale;
            dragY /= scale;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        double scale = getLayoutScale();
        guiGraphics.pose().pushPose();
        int scaledMouseX = mouseX;
        int scaledMouseY = mouseY;
        if (scale < 1.0) {
            guiGraphics.pose().scale((float) scale, (float) scale, 1.0f);
            scaledMouseX = (int) (mouseX / scale);
            scaledMouseY = (int) (mouseY / scale);
        }

        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        guiGraphics.fill(0, 0, effWidth, effHeight, 0xBB000000);

        int panelWidth = 440;
        int panelHeight = 280;
        int panelLeft = (effWidth - panelWidth) / 2;
        int panelTop = (effHeight - panelHeight) / 2;

        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, CYAN_BG);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 1, CYAN_MAIN);
        guiGraphics.fill(panelLeft, panelTop + panelHeight - 1, panelLeft + panelWidth, panelTop + panelHeight, CYAN_MAIN);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 1, panelTop + panelHeight, CYAN_MAIN);
        guiGraphics.fill(panelLeft + panelWidth - 1, panelTop, panelLeft + panelWidth, panelTop + panelHeight, CYAN_MAIN);

        guiGraphics.drawString(this.font, "TIMED SEQUENCE ENTRY EDITOR", panelLeft + 16, panelTop + 12, CYAN_MAIN, false);

        int currentY = panelTop + 22;
        guiGraphics.drawString(this.font, "Timestamp (ms):", panelLeft + 20, currentY, 0xFFAABBCC, false);
        currentY += 34;
        guiGraphics.drawString(this.font, "Action Type:", panelLeft + 20, currentY, 0xFFAABBCC, false);
        currentY += 36;
        guiGraphics.drawString(this.font, "Command / Payload (e.g. /say drop! or dialog_file.json):", panelLeft + 20, currentY, 0xFFAABBCC, false);
        currentY += 36;
        guiGraphics.drawString(this.font, "Description / Note:", panelLeft + 20, currentY, 0xFFAABBCC, false);

        super.render(guiGraphics, scaledMouseX, scaledMouseY, partialTick);

        if (typeDropdown != null) {
            typeDropdown.renderOverlay(guiGraphics, scaledMouseX, scaledMouseY);
        }

        guiGraphics.pose().popPose();
    }
}
