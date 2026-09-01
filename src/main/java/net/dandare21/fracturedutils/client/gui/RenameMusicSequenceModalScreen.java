package net.dandare21.fracturedutils.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class RenameMusicSequenceModalScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xFF05090C;
    private static final int RED_CANCEL = 0xFFFF3355;

    private final Screen parentScreen;
    private final String currentFileName;
    private final Consumer<String> onRenameConfirmed;

    private EditBox nameEditBox;

    public RenameMusicSequenceModalScreen(Screen parentScreen, String currentFileName, Consumer<String> onRenameConfirmed) {
        super(Component.literal("Rename Music Sequence"));
        this.parentScreen = parentScreen;
        this.currentFileName = currentFileName != null ? currentFileName : "sequence.json";
        this.onRenameConfirmed = onRenameConfirmed;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private double getLayoutScale() {
        int targetW = 360;
        int targetH = 180;
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

        int panelWidth = 340;
        int panelHeight = 160;
        int left = (effWidth - panelWidth) / 2;
        int top = (effHeight - panelHeight) / 2;

        this.nameEditBox = new EditBox(this.font, left + 20, top + 56, panelWidth - 40, 20, Component.literal("New Name"));
        this.nameEditBox.setMaxLength(64);
        String baseName = currentFileName.endsWith(".json") ? currentFileName.substring(0, currentFileName.length() - 5) : currentFileName;
        this.nameEditBox.setValue(baseName);
        this.addRenderableWidget(this.nameEditBox);
        this.setInitialFocus(this.nameEditBox);

        int bottomY = top + panelHeight - 34;

        this.addRenderableWidget(new CyberpunkButton(left + panelWidth - 110, bottomY, 90, 22, Component.literal("RENAME"), b -> confirmRename(), CYAN_MAIN, false));
        this.addRenderableWidget(new CyberpunkButton(left + 20, bottomY, 90, 22, Component.literal("CANCEL"), b -> this.onClose(), RED_CANCEL, false));
    }

    private void confirmRename() {
        String newName = this.nameEditBox.getValue().trim();
        if (!newName.isEmpty()) {
            if (!newName.endsWith(".json")) {
                newName += ".json";
            }
            if (onRenameConfirmed != null) {
                onRenameConfirmed.accept(newName);
            }
        }
        this.onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parentScreen);
        }
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        double scale = getLayoutScale();
        graphics.pose().pushPose();
        int scaledMouseX = mouseX;
        int scaledMouseY = mouseY;
        if (scale < 1.0) {
            graphics.pose().scale((float) scale, (float) scale, 1.0f);
            scaledMouseX = (int) (mouseX / scale);
            scaledMouseY = (int) (mouseY / scale);
        }

        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        graphics.fill(0, 0, effWidth, effHeight, 0xBB000000);

        int panelWidth = 340;
        int panelHeight = 160;
        int left = (effWidth - panelWidth) / 2;
        int top = (effHeight - panelHeight) / 2;

        graphics.fill(left, top, left + panelWidth, top + panelHeight, CYAN_BG);
        graphics.fill(left, top, left + panelWidth, top + 1, CYAN_MAIN);
        graphics.fill(left, top + panelHeight - 1, left + panelWidth, top + panelHeight, CYAN_MAIN);
        graphics.fill(left, top, left + 1, top + panelHeight, CYAN_MAIN);
        graphics.fill(left + panelWidth - 1, top, left + panelWidth, top + panelHeight, CYAN_MAIN);

        graphics.drawString(this.font, "RENAME MUSIC SEQUENCE FILE", left + 16, top + 12, CYAN_MAIN, false);
        graphics.drawString(this.font, "Enter new file name for '" + currentFileName + "':", left + 20, top + 38, 0xFFAABBCC, false);

        super.render(graphics, scaledMouseX, scaledMouseY, partialTick);
        graphics.pose().popPose();
    }
}
