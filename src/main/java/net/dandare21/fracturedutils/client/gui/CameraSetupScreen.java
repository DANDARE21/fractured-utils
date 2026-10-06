package net.dandare21.fracturedutils.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.dandare21.fracturedutils.client.camera.CameraMath;
import net.dandare21.fracturedutils.client.camera.CameraTransform;
import net.dandare21.fracturedutils.client.camera.CustomCameraManager;
import net.dandare21.fracturedutils.dialog.DialogLine;
import net.minecraft.ChatFormatting;
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
    private static final int CYAN_BORDER = 0x5500E5FF;
    private static final int DOCK_BG = 0xD804070A;
    private static final int RED_CANCEL = 0xFFFF3355;
    private static final int GREEN_ACCENT = 0xFF00FF88;
    private static final int AMBER_ACCENT = 0xFFFFB300;
    private static final int PURPLE_ACCENT = 0xFFAA55FF;

    public record CameraResult(boolean useCamera, double x, double y, double z, float yaw, float pitch, float roll, double fov) {
        public CameraResult(boolean useCamera, double x, double y, double z, float yaw, float pitch, double fov) {
            this(useCamera, x, y, z, yaw, pitch, 0.0f, fov);
        }
    }

    private final Screen parentScreen;
    private final Consumer<CameraResult> onGenericSave;

    private boolean useCamera;
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private float cameraYaw;
    private float cameraPitch;
    private float cameraRoll;
    private double cameraFov;

    private boolean hudVisible = true;
    private boolean guidesVisible = true;

    // Header Controls
    private CyberpunkButton hudToggleBtn;
    private CyberpunkButton guidesToggleBtn;
    private CyberpunkButton quickSaveBtn;
    private CyberpunkButton quickCancelBtn;

    // Dock Controls
    private CyberpunkButton captureBtn;
    private CyberpunkButton resetRollBtn;
    private CyberpunkButton mainSaveBtn;
    private CyberpunkButton mainCancelBtn;
    private CyberpunkSlider fovSlider;
    private EditBox posXBox, posYBox, posZBox, yawBox, pitchBox, rollBox;

    private boolean updatingBoxes = false;

    // Spectator flight momentum & smoothing
    private Vec3 moveVelocity = Vec3.ZERO;
    private float rollVelocity = 0.0f;
    private double speedMultiplier = 1.0;
    private int speedFeedbackTimer = 0;
    private long lastFrameNanoTime = 0L;
    private boolean wasMoving = false;

    public CameraSetupScreen(Screen parentScreen, DialogLine line, Consumer<DialogLine> onSave) {
        this(parentScreen,
                line != null ? line.getCameraX() : 0.0,
                line != null ? line.getCameraY() : 0.0,
                line != null ? line.getCameraZ() : 0.0,
                line != null ? line.getCameraYaw() : 0.0f,
                line != null ? line.getCameraPitch() : 0.0f,
                0.0f,
                line != null ? line.getCameraFov() : 70.0,
                line != null && line.isUseCamera(),
                res -> {
                    if (line != null) {
                        line.setUseCamera(res.useCamera());
                        line.setCameraX(res.x());
                        line.setCameraY(res.y());
                        line.setCameraZ(res.z());
                        line.setCameraYaw(res.yaw());
                        line.setCameraPitch(res.pitch());
                        line.setCameraFov(res.fov());
                        if (onSave != null) {
                            onSave.accept(line);
                        }
                    }
                });
    }

    public CameraSetupScreen(Screen parentScreen, double initialX, double initialY, double initialZ,
                             float initialYaw, float initialPitch, double initialFov, boolean initialUseCamera,
                             Consumer<CameraResult> onGenericSave) {
        this(parentScreen, initialX, initialY, initialZ, initialYaw, initialPitch, 0.0f, initialFov, initialUseCamera, onGenericSave);
    }

    public CameraSetupScreen(Screen parentScreen, double initialX, double initialY, double initialZ,
                             float initialYaw, float initialPitch, float initialRoll, double initialFov, boolean initialUseCamera,
                             Consumer<CameraResult> onGenericSave) {
        super(Component.literal("Camera Setup"));
        this.parentScreen = parentScreen;
        this.onGenericSave = onGenericSave;
        this.useCamera = initialUseCamera;

        Minecraft mc = Minecraft.getInstance();

        // If no position saved yet or not active, default to player's current eye position and view angles
        boolean isUnset = initialX == 0.0 && initialY == 0.0 && initialZ == 0.0 && initialYaw == 0.0f && initialPitch == 0.0f;
        if (!this.useCamera || isUnset) {
            if (mc.player != null) {
                Vec3 eyePos = mc.player.getEyePosition();
                this.cameraX = eyePos.x;
                this.cameraY = eyePos.y;
                this.cameraZ = eyePos.z;
                this.cameraYaw = mc.player.getYRot();
                this.cameraPitch = mc.player.getXRot();
                this.cameraRoll = 0.0f;
            } else {
                this.cameraX = 0.0;
                this.cameraY = 64.0;
                this.cameraZ = 0.0;
                this.cameraYaw = 0.0f;
                this.cameraPitch = 0.0f;
                this.cameraRoll = 0.0f;
            }
            this.useCamera = true;
        } else {
            this.cameraX = initialX;
            this.cameraY = initialY;
            this.cameraZ = initialZ;
            this.cameraYaw = initialYaw;
            this.cameraPitch = initialPitch;
            this.cameraRoll = initialRoll;
        }
        this.cameraFov = initialFov > 0.0 ? initialFov : 70.0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.lastFrameNanoTime = 0L;
        this.moveVelocity = Vec3.ZERO;
        this.rollVelocity = 0.0f;

        // Responsive dock sizing
        int maxDockW = 460;
        int dockW = Math.min(maxDockW, this.width - 20);
        int dockH = 104;
        int dockLeft = (this.width - dockW) / 2;
        int dockTop = Math.max(10, this.height - dockH - 10);

        // 1. Top floating bar (HUD toggle, Guides toggle, Quick actions)
        int topBarY = 8;
        int toggleW = 96;
        int toggleX = this.width - toggleW - 10;
        this.hudToggleBtn = new CyberpunkButton(toggleX, topBarY, toggleW, 18,
                Component.literal(hudVisible ? "👁 HIDE UI (H)" : "👁 SHOW UI (H)"),
                b -> toggleHud(), CYAN_MAIN, false, Component.literal("Toggle Camera Controls Overlay (Hotkeys: H or Tab)"));
        this.addRenderableWidget(this.hudToggleBtn);

        int guideW = 96;
        int guideX = toggleX - guideW - 4;
        this.guidesToggleBtn = new CyberpunkButton(guideX, topBarY, guideW, 18,
                Component.literal(guidesVisible ? "📐 GUIDES: ON" : "📐 GUIDES: OFF"),
                b -> toggleGuides(), guidesVisible ? GREEN_ACCENT : 0xFF8899AA, false,
                Component.literal("Toggle Alignment Rulers & Composition Guides (Hotkey: G)"));
        this.addRenderableWidget(this.guidesToggleBtn);

        // Quick floating buttons when HUD is hidden
        this.quickCancelBtn = new CyberpunkButton(guideX - 68, topBarY, 64, 18,
                Component.literal("✕ Cancel"), b -> onCancel(), RED_CANCEL, false, Component.literal("Discard changes & exit"));
        this.quickCancelBtn.visible = !hudVisible;
        this.addRenderableWidget(this.quickCancelBtn);

        this.quickSaveBtn = new CyberpunkButton(guideX - 160, topBarY, 88, 18,
                Component.literal("✓ Save (Enter)"), b -> onSave(), GREEN_ACCENT, false, Component.literal("Save camera transform"));
        this.quickSaveBtn.visible = !hudVisible;
        this.addRenderableWidget(this.quickSaveBtn);

        // 2. Dock Row 1: Header & Quick Actions
        int btnH = 18;
        int row1Y = dockTop + 6;

        this.captureBtn = new CyberpunkButton(dockLeft + 115, row1Y, 110, btnH,
                Component.literal("📍 Snap Player"), b -> capturePlayerPosition(), GREEN_ACCENT, false,
                Component.literal("Snap camera to player eye position and look angle"));
        this.captureBtn.visible = hudVisible;
        this.addRenderableWidget(this.captureBtn);

        this.resetRollBtn = new CyberpunkButton(dockLeft + 230, row1Y, 68, btnH,
                Component.literal("↺ 0° Roll"), b -> resetRoll(), AMBER_ACCENT, false,
                Component.literal("Reset camera roll angle to level (0°)"));
        this.resetRollBtn.visible = hudVisible;
        this.addRenderableWidget(this.resetRollBtn);

        this.mainCancelBtn = new CyberpunkButton(dockLeft + dockW - 148, row1Y, 62, btnH,
                Component.literal("✕ Cancel"), b -> onCancel(), RED_CANCEL, false);
        this.mainCancelBtn.visible = hudVisible;
        this.addRenderableWidget(this.mainCancelBtn);

        this.mainSaveBtn = new CyberpunkButton(dockLeft + dockW - 82, row1Y, 74, btnH,
                Component.literal("✓ Save"), b -> onSave(), CYAN_MAIN, false);
        this.mainSaveBtn.setSolidPrimary(true);
        this.mainSaveBtn.visible = hudVisible;
        this.addRenderableWidget(this.mainSaveBtn);

        // 3. Dock Row 2: Coordinates, Angles, and FOV
        int row2Y = dockTop + 42;
        int inputH = 16;
        int availableW = dockW - 24;

        int posZoneW = (int) (availableW * 0.38);
        int rotZoneW = (int) (availableW * 0.36);
        int fovZoneW = availableW - posZoneW - rotZoneW;

        int boxW = Math.max(28, (posZoneW - 8) / 3);
        int startX = dockLeft + 12;

        this.posXBox = new EditBox(this.font, startX, row2Y, boxW, inputH, Component.literal("X"));
        this.posXBox.setValue(String.format(Locale.US, "%.1f", this.cameraX));
        this.posXBox.setResponder(val -> parseCoordInputs());
        this.posXBox.visible = hudVisible;
        this.addRenderableWidget(this.posXBox);

        this.posYBox = new EditBox(this.font, startX + boxW + 4, row2Y, boxW, inputH, Component.literal("Y"));
        this.posYBox.setValue(String.format(Locale.US, "%.1f", this.cameraY));
        this.posYBox.setResponder(val -> parseCoordInputs());
        this.posYBox.visible = hudVisible;
        this.addRenderableWidget(this.posYBox);

        this.posZBox = new EditBox(this.font, startX + (boxW + 4) * 2, row2Y, boxW, inputH, Component.literal("Z"));
        this.posZBox.setValue(String.format(Locale.US, "%.1f", this.cameraZ));
        this.posZBox.setResponder(val -> parseCoordInputs());
        this.posZBox.visible = hudVisible;
        this.addRenderableWidget(this.posZBox);

        // Angles Inputs
        int rotStartX = startX + posZoneW + 6;
        int rotBoxW = Math.max(26, (rotZoneW - 8) / 3);

        this.yawBox = new EditBox(this.font, rotStartX, row2Y, rotBoxW, inputH, Component.literal("Yaw"));
        this.yawBox.setValue(String.format(Locale.US, "%.0f", this.cameraYaw));
        this.yawBox.setResponder(val -> parseCoordInputs());
        this.yawBox.visible = hudVisible;
        this.addRenderableWidget(this.yawBox);

        this.pitchBox = new EditBox(this.font, rotStartX + rotBoxW + 4, row2Y, rotBoxW, inputH, Component.literal("Pitch"));
        this.pitchBox.setValue(String.format(Locale.US, "%.0f", this.cameraPitch));
        this.pitchBox.setResponder(val -> parseCoordInputs());
        this.pitchBox.visible = hudVisible;
        this.addRenderableWidget(this.pitchBox);

        this.rollBox = new EditBox(this.font, rotStartX + (rotBoxW + 4) * 2, row2Y, rotBoxW, inputH, Component.literal("Roll"));
        this.rollBox.setValue(String.format(Locale.US, "%.0f", this.cameraRoll));
        this.rollBox.setResponder(val -> parseCoordInputs());
        this.rollBox.visible = hudVisible;
        this.addRenderableWidget(this.rollBox);

        // FOV Slider
        int fovStartX = rotStartX + rotZoneW + 6;
        double initNormFov = Mth.clamp((this.cameraFov - 10.0) / 130.0, 0.0, 1.0);
        this.fovSlider = new CyberpunkSlider(
                fovStartX, row2Y - 1, Math.max(50, fovZoneW - 6), 18, "FOV", initNormFov,
                val -> {
                    this.cameraFov = 10.0 + val * 130.0;
                    updatePreview();
                },
                val -> String.format(Locale.US, "📷 %.0f°", 10.0 + val * 130.0)
        );
        this.fovSlider.visible = hudVisible;
        this.addRenderableWidget(this.fovSlider);

        updatePreview();
    }

    private void toggleHud() {
        this.hudVisible = !this.hudVisible;
        if (this.hudToggleBtn != null) {
            this.hudToggleBtn.setMessage(Component.literal(this.hudVisible ? "👁 HIDE UI (H)" : "👁 SHOW UI (H)"));
        }
        if (this.quickSaveBtn != null) this.quickSaveBtn.visible = !this.hudVisible;
        if (this.quickCancelBtn != null) this.quickCancelBtn.visible = !this.hudVisible;

        boolean show = this.hudVisible;
        if (this.captureBtn != null) this.captureBtn.visible = show;
        if (this.resetRollBtn != null) this.resetRollBtn.visible = show;
        if (this.mainSaveBtn != null) this.mainSaveBtn.visible = show;
        if (this.mainCancelBtn != null) this.mainCancelBtn.visible = show;
        if (this.posXBox != null) this.posXBox.visible = show;
        if (this.posYBox != null) this.posYBox.visible = show;
        if (this.posZBox != null) this.posZBox.visible = show;
        if (this.yawBox != null) this.yawBox.visible = show;
        if (this.pitchBox != null) this.pitchBox.visible = show;
        if (this.rollBox != null) this.rollBox.visible = show;
        if (this.fovSlider != null) this.fovSlider.visible = show;

        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), this.hudVisible ? 1.2f : 0.8f)
        );
    }

    private void toggleGuides() {
        this.guidesVisible = !this.guidesVisible;
        if (this.guidesToggleBtn != null) {
            this.guidesToggleBtn.setMessage(Component.literal(this.guidesVisible ? "📐 GUIDES: ON" : "📐 GUIDES: OFF"));
            this.guidesToggleBtn.setAccentColor(this.guidesVisible ? GREEN_ACCENT : 0xFF8899AA);
        }
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), this.guidesVisible ? 1.3f : 0.9f)
        );
    }

    private void onSave() {
        if (isAnyBoxFocused()) {
            parseCoordInputs();
        } else {
            syncBoxesOnly();
        }
        this.moveVelocity = Vec3.ZERO;
        this.rollVelocity = 0.0f;
        if (onGenericSave != null) {
            onGenericSave.accept(new CameraResult(this.useCamera, this.cameraX, this.cameraY, this.cameraZ, this.cameraYaw, this.cameraPitch, this.cameraRoll, this.cameraFov));
        }
        CustomCameraManager.clearCustomCamera();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parentScreen);
        }
    }

    private void onCancel() {
        this.moveVelocity = Vec3.ZERO;
        this.rollVelocity = 0.0f;
        CustomCameraManager.clearCustomCamera();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parentScreen);
        }
    }

    private void resetRoll() {
        this.cameraRoll = 0.0f;
        this.rollVelocity = 0.0f;
        syncBoxesAndPreview();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.4f));
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
            this.cameraRoll = 0.0f;
            this.moveVelocity = Vec3.ZERO;
            this.rollVelocity = 0.0f;

            syncBoxesAndPreview();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.3f));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isAnyBoxFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                clearFocus();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_H || keyCode == GLFW.GLFW_KEY_TAB) {
            toggleHud();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_G) {
            toggleGuides();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            onSave();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onCancel();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_R) {
            resetRoll();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isAnyBoxFocused() {
        return (posXBox != null && posXBox.isFocused()) ||
               (posYBox != null && posYBox.isFocused()) ||
               (posZBox != null && posZBox.isFocused()) ||
               (yawBox != null && yawBox.isFocused()) ||
               (pitchBox != null && pitchBox.isFocused()) ||
               (rollBox != null && rollBox.isFocused());
    }

    private void clearFocus() {
        if (posXBox != null) posXBox.setFocused(false);
        if (posYBox != null) posYBox.setFocused(false);
        if (posZBox != null) posZBox.setFocused(false);
        if (yawBox != null) yawBox.setFocused(false);
        if (pitchBox != null) pitchBox.setFocused(false);
        if (rollBox != null) rollBox.setFocused(false);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        // Right Click Drag (button == 1) for camera rotation / aiming
        if (button == 1 && this.useCamera) {
            this.cameraYaw += (float) (dragX * 0.2);
            this.cameraPitch = Mth.clamp(this.cameraPitch + (float) (dragY * 0.2), -89.0f, 89.0f);
            syncBoxesOnly();
            updatePreview();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (super.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        if (this.fovSlider != null && this.fovSlider.visible && this.fovSlider.isMouseOver(mouseX, mouseY)) {
            this.cameraFov = Mth.clamp(this.cameraFov - delta * 2.0, 10.0, 140.0);
            this.fovSlider.setValue((this.cameraFov - 10.0) / 130.0);
            updatePreview();
            return true;
        }
        if (this.useCamera && !isAnyBoxFocused()) {
            if (delta > 0) {
                this.speedMultiplier = Math.min(5.0, this.speedMultiplier + 0.15);
            } else if (delta < 0) {
                this.speedMultiplier = Math.max(0.15, this.speedMultiplier - 0.15);
            }
            this.speedFeedbackTimer = 50;
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.speedFeedbackTimer > 0) {
            this.speedFeedbackTimer--;
        }
        // Smoothly sync coordinate boxes when not actively typing in them
        if (!isAnyBoxFocused()) {
            boolean isMoving = this.moveVelocity.lengthSqr() > 1e-6 || Math.abs(this.rollVelocity) > 0.0f;
            if (isMoving || wasMoving) {
                syncBoxesOnly();
            }
            wasMoving = isMoving;
        }
    }

    private void updateMovement(float dt) {
        if (!this.useCamera) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getWindow() == null) return;

        // If an edit box is focused for typing, smoothly decelerate flight momentum
        if (isAnyBoxFocused()) {
            float friction = 8.0f;
            float blend = 1.0f - (float) Math.exp(-friction * dt);
            this.moveVelocity = this.moveVelocity.lerp(Vec3.ZERO, blend);
            this.rollVelocity = Mth.lerp(blend, this.rollVelocity, 0.0f);
            if (this.moveVelocity.lengthSqr() < 1e-6) this.moveVelocity = Vec3.ZERO;
            if (Math.abs(this.rollVelocity) < 0.01f) this.rollVelocity = 0.0f;
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
        boolean q = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_Q);
        boolean e = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_E);

        if (mc.options != null) {
            if (mc.options.keyUp != null && mc.options.keyUp.getKey().getValue() > 0)
                w |= InputConstants.isKeyDown(window, mc.options.keyUp.getKey().getValue());
            if (mc.options.keyDown != null && mc.options.keyDown.getKey().getValue() > 0)
                s |= InputConstants.isKeyDown(window, mc.options.keyDown.getKey().getValue());
            if (mc.options.keyLeft != null && mc.options.keyLeft.getKey().getValue() > 0)
                a |= InputConstants.isKeyDown(window, mc.options.keyLeft.getKey().getValue());
            if (mc.options.keyRight != null && mc.options.keyRight.getKey().getValue() > 0)
                d |= InputConstants.isKeyDown(window, mc.options.keyRight.getKey().getValue());
            if (mc.options.keyJump != null && mc.options.keyJump.getKey().getValue() > 0)
                space |= InputConstants.isKeyDown(window, mc.options.keyJump.getKey().getValue());
        }

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

        // 1. Translation Movement (WASD + Space/Ctrl)
        double inputFwd = (w ? 1.0 : 0.0) - (s ? 1.0 : 0.0);
        double inputStrafe = (d ? 1.0 : 0.0) - (a ? 1.0 : 0.0);
        double inputUp = (space ? 1.0 : 0.0) - (ctrl ? 1.0 : 0.0);

        Vec3 fwd = CameraMath.getForwardVector(this.cameraYaw, this.cameraPitch);
        Vec3 right = CameraMath.getRightVector(this.cameraYaw, this.cameraPitch, this.cameraRoll);
        Vec3 up = CameraMath.getUpVector(this.cameraYaw, this.cameraPitch, this.cameraRoll);

        Vec3 targetDir = Vec3.ZERO;
        if (inputFwd != 0.0 || inputStrafe != 0.0 || inputUp != 0.0) {
            targetDir = fwd.scale(inputFwd).add(right.scale(inputStrafe)).add(up.scale(inputUp));
            if (targetDir.lengthSqr() > 1e-4) {
                targetDir = targetDir.normalize();
            }
        }

        double baseSpeed = 4.0 * this.speedMultiplier; // 4.0 blocks/sec base
        double targetSpeed = isSprinting ? (baseSpeed * 3.0) : baseSpeed;
        Vec3 targetVel = targetDir.scale(targetSpeed);

        // Spectator mode momentum smoothing:
        // Exponential drag (friction = 6.5) gives silky acceleration ramp and smooth gliding deceleration
        float friction = 6.5f;
        float blend = 1.0f - (float) Math.exp(-friction * dt);
        this.moveVelocity = this.moveVelocity.lerp(targetVel, blend);
        if (targetDir.lengthSqr() < 1e-6 && this.moveVelocity.lengthSqr() < 1e-6) {
            this.moveVelocity = Vec3.ZERO;
        }

        if (this.moveVelocity.lengthSqr() > 1e-7) {
            this.cameraX += this.moveVelocity.x * dt;
            this.cameraY += this.moveVelocity.y * dt;
            this.cameraZ += this.moveVelocity.z * dt;
            updatePreview();
        }

        // 2. Roll Movement (Q/E)
        float inputRoll = (e ? 1.0f : 0.0f) - (q ? 1.0f : 0.0f);
        float targetRollSpeed = (isSprinting ? 65.0f : 25.0f) * (float) this.speedMultiplier; // deg/sec
        float targetRollVel = inputRoll * targetRollSpeed;
        float rollFriction = 7.5f;
        float rollBlend = 1.0f - (float) Math.exp(-rollFriction * dt);
        this.rollVelocity = Mth.lerp(rollBlend, this.rollVelocity, targetRollVel);
        if (inputRoll == 0.0f && Math.abs(this.rollVelocity) < 0.05f) {
            this.rollVelocity = 0.0f;
        }

        if (Math.abs(this.rollVelocity) > 0.0f) {
            this.cameraRoll += this.rollVelocity * dt;
            updatePreview();
        }
    }

    private void syncBoxesOnly() {
        updatingBoxes = true;
        if (posXBox != null && !posXBox.isFocused()) posXBox.setValue(String.format(Locale.US, "%.1f", this.cameraX));
        if (posYBox != null && !posYBox.isFocused()) posYBox.setValue(String.format(Locale.US, "%.1f", this.cameraY));
        if (posZBox != null && !posZBox.isFocused()) posZBox.setValue(String.format(Locale.US, "%.1f", this.cameraZ));
        if (yawBox != null && !yawBox.isFocused()) yawBox.setValue(String.format(Locale.US, "%.0f", this.cameraYaw));
        if (pitchBox != null && !pitchBox.isFocused()) pitchBox.setValue(String.format(Locale.US, "%.0f", this.cameraPitch));
        if (rollBox != null && !rollBox.isFocused()) rollBox.setValue(String.format(Locale.US, "%.0f", this.cameraRoll));
        updatingBoxes = false;
    }

    private void syncBoxesAndPreview() {
        syncBoxesOnly();
        updatePreview();
    }

    private void parseCoordInputs() {
        if (updatingBoxes) return;
        try { if (posXBox != null && !posXBox.getValue().isEmpty()) this.cameraX = Double.parseDouble(posXBox.getValue().trim().replace(",", ".")); } catch (Exception ignored) {}
        try { if (posYBox != null && !posYBox.getValue().isEmpty()) this.cameraY = Double.parseDouble(posYBox.getValue().trim().replace(",", ".")); } catch (Exception ignored) {}
        try { if (posZBox != null && !posZBox.getValue().isEmpty()) this.cameraZ = Double.parseDouble(posZBox.getValue().trim().replace(",", ".")); } catch (Exception ignored) {}
        try { if (yawBox != null && !yawBox.getValue().isEmpty()) this.cameraYaw = Float.parseFloat(yawBox.getValue().trim().replace(",", ".")); } catch (Exception ignored) {}
        try { if (pitchBox != null && !pitchBox.getValue().isEmpty()) this.cameraPitch = Float.parseFloat(pitchBox.getValue().trim().replace(",", ".")); } catch (Exception ignored) {}
        try { if (rollBox != null && !rollBox.getValue().isEmpty()) this.cameraRoll = Float.parseFloat(rollBox.getValue().trim().replace(",", ".")); } catch (Exception ignored) {}
        updatePreview();
    }

    private void updatePreview() {
        if (this.useCamera) {
            CameraTransform transform = CameraTransform.fromEuler(this.cameraX, this.cameraY, this.cameraZ, this.cameraYaw, this.cameraPitch, this.cameraRoll, this.cameraFov);
            CustomCameraManager.setCustomTransform(transform, true);
        } else {
            CustomCameraManager.clearCustomCamera();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // --- 0. SMOOTH FRAME-RATE INDEPENDENT FLIGHT PHYSICS ---
        long now = System.nanoTime();
        if (this.lastFrameNanoTime > 0L) {
            float dt = (float) ((now - this.lastFrameNanoTime) / 1_000_000_000.0);
            dt = Mth.clamp(dt, 0.0001f, 0.05f);
            updateMovement(dt);
        }
        this.lastFrameNanoTime = now;

        // --- 1. MULTI-COLORED COMPOSITION GUIDES & SCREEN RULERS ---
        if (guidesVisible) {
            renderGuides(guiGraphics);
        }

        // When HUD is hidden, render minimal floating chip hint at top center
        if (!hudVisible) {
            guiGraphics.fill(this.width / 2 - 155, 8, this.width / 2 + 155, 24, 0x99000000);
            guiGraphics.fill(this.width / 2 - 155, 8, this.width / 2 + 155, 9, 0x6600E5FF);
            String tip = String.format(Locale.US, "🎥 Flight (%.1fx) | Scroll: Speed | 'G' Guides | 'H' UI | 'Enter' Save", this.speedMultiplier);
            guiGraphics.drawCenteredString(this.font, Component.literal(tip).withStyle(ChatFormatting.BOLD), this.width / 2, 12, 0xFF00E5FF);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        // --- 2. MODERN COMPACT GLASS DOCK ---
        int maxDockW = 460;
        int dockW = Math.min(maxDockW, this.width - 20);
        int dockH = 104;
        int dockLeft = (this.width - dockW) / 2;
        int dockTop = Math.max(10, this.height - dockH - 10);

        // Glass background with glowing neon border
        guiGraphics.fill(dockLeft, dockTop, dockLeft + dockW, dockTop + dockH, DOCK_BG);
        guiGraphics.fill(dockLeft, dockTop, dockLeft + dockW, dockTop + 1, CYAN_MAIN);
        guiGraphics.fill(dockLeft, dockTop + dockH - 1, dockLeft + dockW, dockTop + dockH, CYAN_BORDER);
        guiGraphics.fill(dockLeft, dockTop + 1, dockLeft + 1, dockTop + dockH - 1, CYAN_BORDER);
        guiGraphics.fill(dockLeft + dockW - 1, dockTop + 1, dockLeft + dockW, dockTop + dockH - 1, CYAN_BORDER);

        // Row 1: Title and Pulsing Status Dot
        guiGraphics.drawString(this.font, Component.literal("📷 6-DOF RIG").withStyle(ChatFormatting.BOLD), dockLeft + 12, dockTop + 10, CYAN_MAIN, false);
        long time = System.currentTimeMillis();
        int alpha = 180 + (int)(75 * Math.sin(time / 200.0));
        int greenColor = (alpha << 24) | 0x00FF88;
        guiGraphics.fill(dockLeft + 84, dockTop + 12, dockLeft + 89, dockTop + 17, greenColor);
        guiGraphics.drawString(this.font, Component.literal("LIVE").withStyle(ChatFormatting.BOLD), dockLeft + 92, dockTop + 10, greenColor, false);

        // Row 2: Section Category Badges
        int availableW = dockW - 24;
        int posZoneW = (int) (availableW * 0.38);
        int rotZoneW = (int) (availableW * 0.36);
        int startX = dockLeft + 12;
        int rotStartX = startX + posZoneW + 6;
        int fovStartX = rotStartX + rotZoneW + 6;

        guiGraphics.drawString(this.font, Component.literal("📍 POS (XYZ)"), startX, dockTop + 30, CYAN_MAIN, false);
        guiGraphics.drawString(this.font, Component.literal("🧭 ROT (Y/P/R)"), rotStartX, dockTop + 30, AMBER_ACCENT, false);
        guiGraphics.drawString(this.font, Component.literal("📷 OPTICS (FOV)"), fovStartX, dockTop + 30, PURPLE_ACCENT, false);

        // Row 3: Cheatsheet Bar
        guiGraphics.fill(dockLeft + 8, dockTop + 64, dockLeft + dockW - 8, dockTop + 80, 0x66000000);
        String cheatsheet = String.format(Locale.US, "🖱 Aim: Right-Drag | ⌨ WASD (%.1fx) | ␣/Ctrl: Elevate | Q/E: Roll | ⚙ Scroll: Speed", this.speedMultiplier);
        int cheatsheetColor = this.speedFeedbackTimer > 0 ? GREEN_ACCENT : 0xCCFFFFFF;
        guiGraphics.drawCenteredString(this.font, Component.literal(cheatsheet), dockLeft + dockW / 2, dockTop + 68, cheatsheetColor);

        // Row 4: Live Coordinate Summary
        String liveReadout = String.format(Locale.US, "(%.1f, %.1f, %.1f) | (%.0f°, %.0f°, %.0f°) | FOV: %.0f°",
                cameraX, cameraY, cameraZ, cameraYaw, cameraPitch, cameraRoll, cameraFov);
        guiGraphics.drawCenteredString(this.font, Component.literal(liveReadout), dockLeft + dockW / 2, dockTop + 87, 0x8800E5FF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * Renders multi-colored composition guides and pixel edge rulers at low opacities
     * for cinematic framing and precision alignment.
     */
    private void renderGuides(GuiGraphics guiGraphics) {
        int w = this.width;
        int h = this.height;
        if (w <= 0 || h <= 0) return;

        int cx = w / 2;
        int cy = h / 2;

        // 1. RULE OF THIRDS (Subtle Cyan: 0x3000E5FF)
        int thirdX1 = w / 3;
        int thirdX2 = (w * 2) / 3;
        int thirdY1 = h / 3;
        int thirdY2 = (h * 2) / 3;

        int thirdsColor = 0x2A00E5FF;
        guiGraphics.fill(thirdX1, 0, thirdX1 + 1, h, thirdsColor);
        guiGraphics.fill(thirdX2, 0, thirdX2 + 1, h, thirdsColor);
        guiGraphics.fill(0, thirdY1, w, thirdY1 + 1, thirdsColor);
        guiGraphics.fill(0, thirdY2, w, thirdY2 + 1, thirdsColor);

        // Power points (cross marks at the 4 intersection points)
        int crossArm = 6;
        int powerColor = 0x5500E5FF;
        renderIntersectionCross(guiGraphics, thirdX1, thirdY1, crossArm, powerColor);
        renderIntersectionCross(guiGraphics, thirdX2, thirdY1, crossArm, powerColor);
        renderIntersectionCross(guiGraphics, thirdX1, thirdY2, crossArm, powerColor);
        renderIntersectionCross(guiGraphics, thirdX2, thirdY2, crossArm, powerColor);

        // 2. GOLDEN RATIO / PHI GRID (Subtle Amber / Gold: 0x24FFB300)
        int phiX1 = (int) (w * 0.382);
        int phiX2 = (int) (w * 0.618);
        int phiY1 = (int) (h * 0.382);
        int phiY2 = (int) (h * 0.618);

        int phiColor = 0x22FFB300;
        guiGraphics.fill(phiX1, 0, phiX1 + 1, h, phiColor);
        guiGraphics.fill(phiX2, 0, phiX2 + 1, h, phiColor);
        guiGraphics.fill(0, phiY1, w, phiY1 + 1, phiColor);
        guiGraphics.fill(0, phiY2, w, phiY2 + 1, phiColor);

        // 3. CINEMATIC SAFE ZONES & SCOPE GUIDES (Subtle Magenta & Indigo: 0x28FF33AA)
        int actionSafeInsetX = (int) (w * 0.05); // 90% Action Safe
        int actionSafeInsetY = (int) (h * 0.05);
        int safeColor = 0x26FF33AA;
        renderBracketBox(guiGraphics, actionSafeInsetX, actionSafeInsetY, w - actionSafeInsetX, h - actionSafeInsetY, 14, safeColor);

        int titleSafeInsetX = (int) (w * 0.10); // 80% Title Safe
        int titleSafeInsetY = (int) (h * 0.10);
        int titleColor = 0x20FF55CC;
        renderBracketBox(guiGraphics, titleSafeInsetX, titleSafeInsetY, w - titleSafeInsetX, h - titleSafeInsetY, 10, titleColor);

        // 2.39:1 CinemaScope Frame (Subtle Violet: 0x2E8833FF)
        double cinemaAspect = 2.39;
        int scopeH = (int) (w / cinemaAspect);
        if (scopeH < h) {
            int scopeTop = (h - scopeH) / 2;
            int scopeBottom = scopeTop + scopeH;
            int scopeColor = 0x2C8833FF;
            guiGraphics.fill(0, scopeTop, w, scopeTop + 1, scopeColor);
            guiGraphics.fill(0, scopeBottom, w, scopeBottom + 1, scopeColor);
            guiGraphics.drawString(this.font, "2.39:1", 12, scopeTop + 3, 0x448833FF, false);
        }

        // 4. CENTER RETICLE & HORIZON LINE (Subtle Mint / Emerald: 0x4400FF88)
        int reticleColor = 0x4800FF88;
        int reticleGap = 4;
        int reticleArm = 18;
        guiGraphics.fill(cx - reticleArm, cy, cx - reticleGap, cy + 1, reticleColor);
        guiGraphics.fill(cx + reticleGap + 1, cy, cx + reticleArm + 1, cy + 1, reticleColor);
        guiGraphics.fill(cx, cy - reticleArm, cx + 1, cy - reticleGap, reticleColor);
        guiGraphics.fill(cx, cy + reticleGap + 1, cx + 1, cy + reticleArm + 1, reticleColor);
        // Center Dot
        guiGraphics.fill(cx, cy, cx + 1, cy + 1, 0x7700FF88);

        // Horizon pitch wings
        int horizonWing = (int) (w * 0.12);
        int horizonGap = reticleArm + 8;
        guiGraphics.fill(cx - horizonGap - horizonWing, cy, cx - horizonGap, cy + 1, 0x2200FF88);
        guiGraphics.fill(cx + horizonGap, cy, cx + horizonGap + horizonWing, cy + 1, 0x2200FF88);

        // 5. TOP & LEFT RULERS (Subtle Ice White / Cyan: 0x30FFFFFF)
        renderRulers(guiGraphics, w, h, cx, cy);
    }

    private void renderRulers(GuiGraphics guiGraphics, int w, int h, int cx, int cy) {
        int rulerColor = 0x30FFFFFF;
        int rulerTickMajor = 0x5500E5FF;
        int rulerTickMinor = 0x20FFFFFF;
        int centerColor = 0x6600FF88;

        // Top horizontal ruler along y=0 to y=6
        guiGraphics.fill(0, 0, w, 1, rulerColor);
        for (int x = 0; x < w; x += 10) {
            boolean isMajor = (x % 50 == 0);
            boolean is100 = (x % 100 == 0);
            int tickH = is100 ? 6 : (isMajor ? 4 : 2);
            int col = is100 ? rulerTickMajor : (isMajor ? rulerColor : rulerTickMinor);
            guiGraphics.fill(x, 0, x + 1, tickH, col);
        }
        // Center notch at top
        guiGraphics.fill(cx - 1, 0, cx + 2, 7, centerColor);

        // Left vertical ruler along x=0 to x=6
        guiGraphics.fill(0, 0, 1, h, rulerColor);
        for (int y = 0; y < h; y += 10) {
            boolean isMajor = (y % 50 == 0);
            boolean is100 = (y % 100 == 0);
            int tickW = is100 ? 6 : (isMajor ? 4 : 2);
            int col = is100 ? rulerTickMajor : (isMajor ? rulerColor : rulerTickMinor);
            guiGraphics.fill(0, y, tickW, y + 1, col);
        }
        // Center notch at left
        guiGraphics.fill(0, cy - 1, 7, cy + 2, centerColor);
    }

    private void renderIntersectionCross(GuiGraphics guiGraphics, int x, int y, int arm, int color) {
        guiGraphics.fill(x - arm, y, x + arm + 1, y + 1, color);
        guiGraphics.fill(x, y - arm, x + 1, y + arm + 1, color);
    }

    private void renderBracketBox(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int arm, int color) {
        // Top-Left corner
        guiGraphics.fill(x1, y1, x1 + arm, y1 + 1, color);
        guiGraphics.fill(x1, y1, x1 + 1, y1 + arm, color);
        // Top-Right corner
        guiGraphics.fill(x2 - arm, y1, x2, y1 + 1, color);
        guiGraphics.fill(x2 - 1, y1, x2, y1 + arm, color);
        // Bottom-Left corner
        guiGraphics.fill(x1, y2 - 1, x1 + arm, y2, color);
        guiGraphics.fill(x1, y2 - arm, x1 + 1, y2, color);
        // Bottom-Right corner
        guiGraphics.fill(x2 - arm, y2 - 1, x2, y2, color);
        guiGraphics.fill(x2 - 1, y2 - arm, x2, y2, color);
    }

    @Override
    public void onClose() {
        CustomCameraManager.clearCustomCamera();
        super.onClose();
    }
}
