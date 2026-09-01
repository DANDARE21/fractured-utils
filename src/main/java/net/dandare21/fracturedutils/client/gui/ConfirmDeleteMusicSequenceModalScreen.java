package net.dandare21.fracturedutils.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmDeleteMusicSequenceModalScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xFF05090C;
    private static final int RED_DELETE = 0xFFFF3355;

    private final Screen parentScreen;
    private final String fileName;
    private final Runnable onDeleteConfirmed;

    public ConfirmDeleteMusicSequenceModalScreen(Screen parentScreen, String fileName, Runnable onDeleteConfirmed) {
        super(Component.literal("Confirm Delete Music Sequence"));
        this.parentScreen = parentScreen;
        this.fileName = fileName != null ? fileName : "music_sequence.json";
        this.onDeleteConfirmed = onDeleteConfirmed;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private double getLayoutScale() {
        int targetW = 340;
        int targetH = 170;
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

        int panelWidth = 320;
        int panelHeight = 150;
        int left = (effWidth - panelWidth) / 2;
        int top = (effHeight - panelHeight) / 2;

        int bottomY = top + panelHeight - 34;

        this.addRenderableWidget(new CyberpunkButton(left + panelWidth / 2 - 95, bottomY, 90, 24, Component.literal("DELETE"), b -> {
            if (onDeleteConfirmed != null) {
                onDeleteConfirmed.run();
            }
            if (this.minecraft != null) {
                this.minecraft.setScreen(parentScreen);
            }
        }, RED_DELETE, false));

        this.addRenderableWidget(new CyberpunkButton(left + panelWidth / 2 + 5, bottomY, 90, 24, Component.literal("CANCEL"), b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parentScreen);
            }
        }, CYAN_MAIN, false));
    }

    private void drawGridOverlay(GuiGraphics graphics, int effW, int effH) {
        int gridSize = 32;
        long time = System.currentTimeMillis();
        int offsetX = (int) ((time / 40) % gridSize);
        int offsetY = (int) ((time / 40) % gridSize);

        for (int x = -gridSize + offsetX; x < effW + gridSize; x += gridSize) {
            graphics.fill(x, 0, x + 1, effH, 0x1200E5FF);
        }
        for (int y = -gridSize + offsetY; y < effH + gridSize; y += gridSize) {
            graphics.fill(0, y, effW, y + 1, 0x1200E5FF);
        }
    }

    private void drawBorderBox(GuiGraphics graphics, int x, int y, int w, int h, int borderColor, int fillColor) {
        graphics.fill(x, y, x + w, y + h, fillColor);
        graphics.fill(x, y, x + w, y + 1, borderColor);
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        graphics.fill(x, y, x + 1, y + h, borderColor);
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor);
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

        graphics.fill(0, 0, effWidth, effHeight, CYAN_BG);
        drawGridOverlay(graphics, effWidth, effHeight);

        int panelWidth = 320;
        int panelHeight = 150;
        int left = (effWidth - panelWidth) / 2;
        int top = (effHeight - panelHeight) / 2;

        drawBorderBox(graphics, left, top, panelWidth, panelHeight, RED_DELETE, 0xEE060C12);

        graphics.fill(left, top, left + panelWidth, top + 26, 0xEE200810);
        graphics.fill(left, top + 25, left + panelWidth, top + 26, RED_DELETE);
        graphics.drawString(this.font, "DELETE MUSIC SEQUENCE FILE", left + 12, top + 8, RED_DELETE, false);

        graphics.drawString(this.font, "Are you sure you want to delete music sequence:", left + 20, top + 42, 0xFFAABBCC, false);

        String displayFile = "'" + (fileName.length() > 28 ? fileName.substring(0, 25) + "..." : fileName) + "'";
        graphics.drawCenteredString(this.font, Component.literal(displayFile), left + (panelWidth / 2), top + 60, CYAN_MAIN);

        graphics.drawCenteredString(this.font, Component.literal("⚠ THIS ACTION CANNOT BE UNDONE!"), left + (panelWidth / 2), top + 82, RED_DELETE);

        super.render(graphics, scaledMouseX, scaledMouseY, partialTick);
        graphics.pose().popPose();
    }
}
