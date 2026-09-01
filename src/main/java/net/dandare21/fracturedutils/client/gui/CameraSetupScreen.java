package net.dandare21.fracturedutils.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.dandare21.fracturedutils.client.camera.CustomCameraManager;
import net.dandare21.fracturedutils.dialog.DialogLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.Consumer;

public class CameraSetupScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xFF05090C;
    private static final int RED_CANCEL = 0xFFFF3355;

    private final Screen parentScreen;
    private final DialogLine line;
    private final Consumer<DialogLine> onSave;

    private boolean useCamera;
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private float cameraYaw;
    private float cameraPitch;
    private double cameraFov;

    private CyberpunkCheckbox useCameraCheckbox;
    private CyberpunkSlider fovSlider;
    private EditBox posXBox, posYBox, posZBox, yawBox, pitchBox;

    private boolean updatingBoxes = false;

    public CameraSetupScreen(Screen parentScreen, DialogLine line, Consumer<DialogLine> onSave) {
        super(Component.literal("Camera Setup"));
        this.parentScreen = parentScreen;
        this.line = line != null ? line.copy() : new DialogLine();
        this.onSave = onSave;

        this.useCamera = this.line.isUseCamera();
        Minecraft mc = Minecraft.getInstance();

        // If no position saved yet, default to player's current eye position and view angles
        if (!this.useCamera || (this.line.getCameraX() == 0.0 && this.line.getCameraY() == 0.0 && this.line.getCameraZ() == 0.0)) {
            if (mc.player != null) {
                Vec3 eyePos = mc.player.getEyePosition();
                this.cameraX = eyePos.x;
                this.cameraY = eyePos.y;
                this.cameraZ = eyePos.z;
                this.cameraYaw = mc.player.getYRot();
                this.cameraPitch = mc.player.getXRot();
            } else {
                this.cameraX = 0.0;
                this.cameraY = 64.0;
                this.cameraZ = 0.0;
                this.cameraYaw = 0.0f;
                this.cameraPitch = 0.0f;
            }
            this.useCamera = true;
        } else {
            this.cameraX = this.line.getCameraX();
            this.cameraY = this.line.getCameraY();
            this.cameraZ = this.line.getCameraZ();
            this.cameraYaw = this.line.getCameraYaw();
            this.cameraPitch = this.line.getCameraPitch();
        }
        this.cameraFov = this.line.getCameraFov();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int panelWidth = 340;
        int panelHeight = 210;
        int panelLeft = (this.width - panelWidth) / 2;
        int panelTop = this.height - panelHeight - 15; // Position at bottom so preview is visible above

        int currentY = panelTop + 24;

        // 1. Enable Camera Checkbox
        this.useCameraCheckbox = new CyberpunkCheckbox(panelLeft + 15, currentY, panelWidth - 30, 18,
                Component.literal("Enable Custom Camera for this Dialog Line"),
                this.useCamera,
                checked -> {
                    this.useCamera = checked;
                    updatePreview();
                });
        this.addRenderableWidget(this.useCameraCheckbox);
        currentY += 24;

        // 2. Set/Capture Current Position Button
        CyberpunkButton captureBtn = new CyberpunkButton(panelLeft + 15, currentY, panelWidth - 30, 20,
                Component.literal("📍 Set Camera to Current Player Position"),
                b -> capturePlayerPosition());
        this.addRenderableWidget(captureBtn);
        currentY += 26;

        // 3. FOV Slider
        double initNormFov = (this.cameraFov - 10.0) / 130.0;
        initNormFov = Math.max(0.0, Math.min(1.0, initNormFov));
        this.fovSlider = new CyberpunkSlider(
                panelLeft + 15, currentY, panelWidth - 30, 20, "FOV", initNormFov,
                val -> {
                    this.cameraFov = 10.0 + val * 130.0;
                    updatePreview();
                },
                val -> String.format(Locale.US, "📷 Camera FOV: %.0f°", 10.0 + val * 130.0)
        );
        this.addRenderableWidget(this.fovSlider);
        currentY += 28;

        // 4. Coordinates Inputs (X, Y, Z, Yaw, Pitch)
        int inputW = 58;
        this.posXBox = new EditBox(this.font, panelLeft + 15, currentY, inputW, 16, Component.literal("X"));
        this.posXBox.setValue(String.format(Locale.US, "%.1f", this.cameraX));
        this.posXBox.setResponder(val -> parseCoordInputs());
        this.addRenderableWidget(this.posXBox);

        this.posYBox = new EditBox(this.font, panelLeft + 78, currentY, inputW, 16, Component.literal("Y"));
        this.posYBox.setValue(String.format(Locale.US, "%.1f", this.cameraY));
        this.posYBox.setResponder(val -> parseCoordInputs());
        this.addRenderableWidget(this.posYBox);

        this.posZBox = new EditBox(this.font, panelLeft + 141, currentY, inputW, 16, Component.literal("Z"));
        this.posZBox.setValue(String.format(Locale.US, "%.1f", this.cameraZ));
        this.posZBox.setResponder(val -> parseCoordInputs());
        this.addRenderableWidget(this.posZBox);

        this.yawBox = new EditBox(this.font, panelLeft + 204, currentY, inputW, 16, Component.literal("Yaw"));
        this.yawBox.setValue(String.format(Locale.US, "%.0f", this.cameraYaw));
        this.yawBox.setResponder(val -> parseCoordInputs());
        this.addRenderableWidget(this.yawBox);

        this.pitchBox = new EditBox(this.font, panelLeft + 267, currentY, inputW, 16, Component.literal("Pitch"));
        this.pitchBox.setValue(String.format(Locale.US, "%.0f", this.cameraPitch));
        this.pitchBox.setResponder(val -> parseCoordInputs());
        this.addRenderableWidget(this.pitchBox);

        // 5. Save & Cancel Buttons
        int btnY = panelTop + panelHeight - 24;
        CyberpunkButton saveBtn = new CyberpunkButton(panelLeft + panelWidth - 110, btnY, 95, 18, Component.literal("✓ Save Camera"), b -> {
            applyToLine();
            if (onSave != null) {
                onSave.accept(line);
            }
            CustomCameraManager.clearCustomCamera();
            this.minecraft.setScreen(parentScreen);
        });
        this.addRenderableWidget(saveBtn);

        CyberpunkButton cancelBtn = new CyberpunkButton(panelLeft + 15, btnY, 95, 18, Component.literal("✕ Cancel"), b -> {
            CustomCameraManager.clearCustomCamera();
            this.minecraft.setScreen(parentScreen);
        }, RED_CANCEL, false);
        this.addRenderableWidget(cancelBtn);

        updatePreview();
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
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        // Right Click Drag (button == 1) for camera rotation
        if (button == 1 && this.useCamera) {
            this.cameraYaw += (float) (dragX * 0.2);
            this.cameraPitch = Mth.clamp(this.cameraPitch + (float) (dragY * 0.2), -89.0f, 89.0f);
            syncBoxesAndPreview();
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.useCamera) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getWindow() == null) return;

        // If an edit box is currently focused for typing, skip WASD camera movement
        if ((posXBox != null && posXBox.isFocused()) ||
            (posYBox != null && posYBox.isFocused()) ||
            (posZBox != null && posZBox.isFocused()) ||
            (yawBox != null && yawBox.isFocused()) ||
            (pitchBox != null && pitchBox.isFocused())) {
            return;
        }

        long window = mc.getWindow().getWindow();
        boolean w = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_W);
        boolean s = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_S);
        boolean a = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A);
        boolean d = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D);
        boolean space = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE);
        boolean ctrl = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) ||
                       InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);

        boolean isSprinting = false;
        if (mc.options != null && mc.options.keySprint != null && mc.options.keySprint.getKey() != null) {
            int sprintKeyCode = mc.options.keySprint.getKey().getValue();
            if (sprintKeyCode > 0) {
                isSprinting = InputConstants.isKeyDown(window, sprintKeyCode);
            }
        }
        if (!isSprinting) {
            isSprinting = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        }

        if (w || s || a || d || space || ctrl) {
            // Fine, non-snappy adjustment step (0.04 blocks/tick), faster when sprinting (0.20 blocks/tick)
            double speed = isSprinting ? 0.20 : 0.04;

            double yawRad = Math.toRadians(this.cameraYaw);
            double pitchRad = Math.toRadians(this.cameraPitch);

            // Forward vector relative to camera rotation
            double fwdX = -Math.sin(yawRad) * Math.cos(pitchRad);
            double fwdY = -Math.sin(pitchRad);
            double fwdZ = Math.cos(yawRad) * Math.cos(pitchRad);

            // Right vector relative to camera rotation
            double rightX = Math.cos(yawRad);
            double rightY = 0.0;
            double rightZ = Math.sin(yawRad);

            // Up vector relative to camera orientation
            double upX = Math.sin(yawRad) * Math.sin(pitchRad);
            double upY = Math.cos(pitchRad);
            double upZ = -Math.cos(yawRad) * Math.sin(pitchRad);

            if (w) {
                this.cameraX += fwdX * speed;
                this.cameraY += fwdY * speed;
                this.cameraZ += fwdZ * speed;
            }
            if (s) {
                this.cameraX -= fwdX * speed;
                this.cameraY -= fwdY * speed;
                this.cameraZ -= fwdZ * speed;
            }
            if (d) {
                this.cameraX += rightX * speed;
                this.cameraY += rightY * speed;
                this.cameraZ += rightZ * speed;
            }
            if (a) {
                this.cameraX -= rightX * speed;
                this.cameraY -= rightY * speed;
                this.cameraZ -= rightZ * speed;
            }
            if (space) {
                this.cameraX += upX * speed;
                this.cameraY += upY * speed;
                this.cameraZ += upZ * speed;
            }
            if (ctrl) {
                this.cameraX -= upX * speed;
                this.cameraY -= upY * speed;
                this.cameraZ -= upZ * speed;
            }

            syncBoxesAndPreview();
        }
    }

    private void syncBoxesAndPreview() {
        updatingBoxes = true;
        if (posXBox != null) posXBox.setValue(String.format(Locale.US, "%.1f", this.cameraX));
        if (posYBox != null) posYBox.setValue(String.format(Locale.US, "%.1f", this.cameraY));
        if (posZBox != null) posZBox.setValue(String.format(Locale.US, "%.1f", this.cameraZ));
        if (yawBox != null) yawBox.setValue(String.format(Locale.US, "%.0f", this.cameraYaw));
        if (pitchBox != null) pitchBox.setValue(String.format(Locale.US, "%.0f", this.cameraPitch));
        updatingBoxes = false;
        updatePreview();
    }

    private void capturePlayerPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Vec3 eyePos = mc.player.getEyePosition();
            this.cameraX = eyePos.x;
            this.cameraY = eyePos.y;
            this.cameraZ = eyePos.z;
            this.cameraYaw = mc.player.getYRot();
            this.cameraPitch = mc.player.getXRot();

            syncBoxesAndPreview();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.3f));
        }
    }

    private void parseCoordInputs() {
        if (updatingBoxes) return;
        try { if (posXBox != null && !posXBox.getValue().isEmpty()) this.cameraX = Double.parseDouble(posXBox.getValue().trim()); } catch (Exception ignored) {}
        try { if (posYBox != null && !posYBox.getValue().isEmpty()) this.cameraY = Double.parseDouble(posYBox.getValue().trim()); } catch (Exception ignored) {}
        try { if (posZBox != null && !posZBox.getValue().isEmpty()) this.cameraZ = Double.parseDouble(posZBox.getValue().trim()); } catch (Exception ignored) {}
        try { if (yawBox != null && !yawBox.getValue().isEmpty()) this.cameraYaw = Float.parseFloat(yawBox.getValue().trim()); } catch (Exception ignored) {}
        try { if (pitchBox != null && !pitchBox.getValue().isEmpty()) this.cameraPitch = Float.parseFloat(pitchBox.getValue().trim()); } catch (Exception ignored) {}
        updatePreview();
    }

    private void updatePreview() {
        if (this.useCamera) {
            CustomCameraManager.setCustomCamera(this.cameraX, this.cameraY, this.cameraZ, this.cameraYaw, this.cameraPitch, true);
            CustomCameraManager.setCustomFov(this.cameraFov);
        } else {
            CustomCameraManager.clearCustomCamera();
        }
    }

    private void applyToLine() {
        line.setUseCamera(this.useCamera);
        line.setCameraX(this.cameraX);
        line.setCameraY(this.cameraY);
        line.setCameraZ(this.cameraZ);
        line.setCameraYaw(this.cameraYaw);
        line.setCameraPitch(this.cameraPitch);
        line.setCameraFov(this.cameraFov);
    }

    private double getLayoutScale() {
        int targetW = 360;
        int targetH = 240;
        if (this.width <= 0 || this.height <= 0) return 1.0;
        double scaleX = (double) this.width / targetW;
        double scaleY = (double) this.height / targetH;
        return Math.min(1.0, Math.min(scaleX, scaleY));
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

        int panelWidth = 340;
        int panelHeight = 210;
        int panelLeft = (effWidth - panelWidth) / 2;
        int panelTop = effHeight - panelHeight - 15;

        // Dark transparent panel overlay at bottom
        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, CYAN_BG);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 2, CYAN_MAIN);
        guiGraphics.fill(panelLeft, panelTop + panelHeight - 2, panelLeft + panelWidth, panelTop + panelHeight, CYAN_MAIN);
        guiGraphics.fill(panelLeft, panelTop + 2, panelLeft + 2, panelTop + panelHeight, CYAN_MAIN);
        guiGraphics.fill(panelLeft + panelWidth - 2, panelTop, panelLeft + panelWidth, panelTop + panelHeight, CYAN_MAIN);

        guiGraphics.drawString(this.font, Component.literal("CAMERA SETUP & LIVE PREVIEW").withStyle(net.minecraft.ChatFormatting.BOLD), panelLeft + 15, panelTop + 8, CYAN_MAIN);
        guiGraphics.fill(panelLeft + 15, panelTop + 18, panelLeft + panelWidth - 15, panelTop + 19, 0xAA00E5FF);

        guiGraphics.drawString(this.font, Component.literal("X"), panelLeft + 15, panelTop + 114, 0xAAAAAA);
        guiGraphics.drawString(this.font, Component.literal("Y"), panelLeft + 78, panelTop + 114, 0xAAAAAA);
        guiGraphics.drawString(this.font, Component.literal("Z"), panelLeft + 141, panelTop + 114, 0xAAAAAA);
        guiGraphics.drawString(this.font, Component.literal("Yaw"), panelLeft + 204, panelTop + 114, 0xAAAAAA);
        guiGraphics.drawString(this.font, Component.literal("Pitch"), panelLeft + 267, panelTop + 114, 0xAAAAAA);

        // Top Banner & Control Instructions Indicators
        String bannerText = this.useCamera ? "LIVE CAMERA PREVIEW ACTIVE" : "CAMERA OVERRIDE DISABLED";
        int bannerColor = this.useCamera ? 0xFF00E5FF : 0xFFFF3355;
        guiGraphics.drawCenteredString(this.font, Component.literal(bannerText).withStyle(net.minecraft.ChatFormatting.BOLD), effWidth / 2, 12, bannerColor);

        if (this.useCamera) {
            guiGraphics.drawCenteredString(this.font, Component.literal("💡 Right-Click Drag: Rotate | WASD / Space / Ctrl: Camera-Space Movement | Sprint Key: Fast"), effWidth / 2, 26, 0xFFFFEE55);
        }

        super.render(guiGraphics, scaledMouseX, scaledMouseY, partialTick);
        guiGraphics.pose().popPose();
    }

    @Override
    public void onClose() {
        CustomCameraManager.clearCustomCamera();
        super.onClose();
    }
}
