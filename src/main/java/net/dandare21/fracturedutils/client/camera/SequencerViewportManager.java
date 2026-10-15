package net.dandare21.fracturedutils.client.camera;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Manages the Axiom-style 3D camera viewport for the Music Sequence Orchestrator.
 * Handles glViewport & glScissor calculations, aspect ratio overrides,
 * and interactive 3D navigation (Work Camera orbit, pan, and flight physics).
 */
public class SequencerViewportManager {
    public enum CameraMode {
        SEQUENCE,
        WORK_CAMERA
    }

    private static boolean active = false;

    // Viewport bounds in GUI-scaled coordinates
    private static int viewportX = 0;
    private static int viewportY = 0;
    private static int viewportWidth = 100;
    private static int viewportHeight = 100;

    // Camera Mode (Sequence Playback Camera vs Free Work Camera)
    private static CameraMode currentMode = CameraMode.SEQUENCE;

    // Work Camera Transform State
    private static Vec3 workCameraPos = Vec3.ZERO;
    private static float workCameraYaw = 0.0f;
    private static float workCameraPitch = 0.0f;
    private static float workCameraRoll = 0.0f;
    private static double workCameraFov = 70.0;

    // Work Camera Flight Momentum
    private static Vec3 flightVelocity = Vec3.ZERO;
    private static double flightSpeedMultiplier = 1.0;

    private static boolean renderingLevel = false;

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        SequencerViewportManager.active = active;
        if (!active) {
            renderingLevel = false;
        }
    }

    public static boolean isRenderingLevel() {
        return renderingLevel;
    }

    public static void setRenderingLevel(boolean rendering) {
        renderingLevel = rendering;
    }

    public static void setViewportBounds(int x, int y, int width, int height) {
        viewportX = x;
        viewportY = y;
        viewportWidth = Math.max(10, width);
        viewportHeight = Math.max(10, height);
    }

    public static int getViewportX() {
        return viewportX;
    }

    public static int getViewportY() {
        return viewportY;
    }

    public static int getViewportWidth() {
        return viewportWidth;
    }

    public static int getViewportHeight() {
        return viewportHeight;
    }

    public static boolean isMouseOverViewport(double mouseX, double mouseY) {
        return mouseX >= viewportX && mouseX <= (viewportX + viewportWidth)
                && mouseY >= viewportY && mouseY <= (viewportY + viewportHeight);
    }

    public static int getGlX() {
        Minecraft mc = Minecraft.getInstance();
        return (int) Math.round(viewportX * mc.getWindow().getGuiScale());
    }

    public static int getGlY() {
        Minecraft mc = Minecraft.getInstance();
        double guiScale = mc.getWindow().getGuiScale();
        int pixelYBottom = (int) Math.round((viewportY + viewportHeight) * guiScale);
        return Math.max(0, mc.getWindow().getHeight() - pixelYBottom);
    }

    public static int getGlWidth() {
        Minecraft mc = Minecraft.getInstance();
        double guiScale = mc.getWindow().getGuiScale();
        int pixelXRight = (int) Math.round((viewportX + viewportWidth) * guiScale);
        int pixelXLeft = (int) Math.round(viewportX * guiScale);
        return Math.max(1, pixelXRight - pixelXLeft);
    }

    public static int getGlHeight() {
        Minecraft mc = Minecraft.getInstance();
        double guiScale = mc.getWindow().getGuiScale();
        int pixelYBottom = (int) Math.round((viewportY + viewportHeight) * guiScale);
        int pixelYTop = (int) Math.round(viewportY * guiScale);
        return Math.max(1, pixelYBottom - pixelYTop);
    }

    public static float getAspectRatio() {
        if (viewportWidth > 0 && viewportHeight > 0) {
            return (float) viewportWidth / (float) viewportHeight;
        }
        Minecraft mc = Minecraft.getInstance();
        return (float) mc.getWindow().getWidth() / (float) mc.getWindow().getHeight();
    }

    public static void setupViewport() {
        int x = getGlX();
        int y = getGlY();
        int w = getGlWidth();
        int h = getGlHeight();
        RenderSystem.viewport(x, y, w, h);
        RenderSystem.enableScissor(x, y, w, h);
    }

    public static void restoreViewport() {
        RenderSystem.disableScissor();
        Minecraft mc = Minecraft.getInstance();
        RenderSystem.viewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    // --- Work Camera Management ---

    public static CameraMode getCurrentMode() {
        return currentMode;
    }

    public static void setCurrentMode(CameraMode mode) {
        if (currentMode != mode) {
            currentMode = mode;
            if (currentMode == CameraMode.WORK_CAMERA) {
                initWorkCameraFromCurrent();
            }
            applyCameraState();
        }
    }

    public static void toggleMode() {
        setCurrentMode(currentMode == CameraMode.SEQUENCE ? CameraMode.WORK_CAMERA : CameraMode.SEQUENCE);
    }

    public static void initWorkCameraFromCurrent() {
        CameraTransform current = CustomCameraManager.getCurrentTransform();
        if (current != null) {
            workCameraPos = current.getPosition();
            workCameraYaw = current.getYaw();
            workCameraPitch = current.getPitch();
            workCameraRoll = current.getRoll();
            workCameraFov = current.getFov();
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                workCameraPos = mc.player.getEyePosition();
                workCameraYaw = mc.player.getYRot();
                workCameraPitch = mc.player.getXRot();
                workCameraRoll = 0.0f;
                workCameraFov = 70.0;
            }
        }
    }

    public static void setWorkCamera(Vec3 pos, float yaw, float pitch, float roll, double fov) {
        workCameraPos = pos != null ? pos : Vec3.ZERO;
        workCameraYaw = yaw;
        workCameraPitch = Mth.clamp(pitch, -90.0f, 90.0f);
        workCameraRoll = roll;
        workCameraFov = Math.max(10.0, Math.min(130.0, fov));
        if (currentMode == CameraMode.WORK_CAMERA) {
            applyCameraState();
        }
    }

    public static void applyCameraState() {
        if (!active) return;
        if (currentMode == CameraMode.WORK_CAMERA) {
            CameraTransform transform = CameraTransform.fromEuler(workCameraPos, workCameraYaw, workCameraPitch, workCameraRoll, workCameraFov);
            CustomCameraManager.setCustomTransform(transform, true);
        }
    }

    public static Vec3 getWorkCameraPos() {
        return workCameraPos;
    }

    public static float getWorkCameraYaw() {
        return workCameraYaw;
    }

    public static float getWorkCameraPitch() {
        return workCameraPitch;
    }

    public static float getWorkCameraRoll() {
        return workCameraRoll;
    }

    public static double getWorkCameraFov() {
        return workCameraFov;
    }

    public static void adjustWorkCameraAngles(float deltaYaw, float deltaPitch) {
        workCameraYaw = (workCameraYaw + deltaYaw) % 360.0f;
        workCameraPitch = Mth.clamp(workCameraPitch + deltaPitch, -89.9f, 89.9f);
        applyCameraState();
    }

    public static void panWorkCamera(double deltaX, double deltaY) {
        float yawRad = (float) Math.toRadians(workCameraYaw);
        float pitchRad = (float) Math.toRadians(workCameraPitch);

        Vec3 forward = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad)).normalize();
        Vec3 right = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad)).normalize();
        Vec3 up = right.cross(forward).normalize();

        double panSpeed = 0.05 * flightSpeedMultiplier;
        workCameraPos = workCameraPos.add(right.scale(-deltaX * panSpeed)).add(up.scale(deltaY * panSpeed));
        applyCameraState();
    }

    public static void updateFlight(float dt, boolean forward, boolean backward, boolean left, boolean right, boolean up, boolean down) {
        if (currentMode != CameraMode.WORK_CAMERA) return;

        float yawRad = (float) Math.toRadians(workCameraYaw);
        float pitchRad = (float) Math.toRadians(workCameraPitch);

        Vec3 look = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad)).normalize();
        Vec3 strafe = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad)).normalize();
        Vec3 vertical = new Vec3(0, 1, 0);

        Vec3 inputDir = Vec3.ZERO;
        if (forward) inputDir = inputDir.add(look);
        if (backward) inputDir = inputDir.subtract(look);
        if (right) inputDir = inputDir.add(strafe);
        if (left) inputDir = inputDir.subtract(strafe);
        if (up) inputDir = inputDir.add(vertical);
        if (down) inputDir = inputDir.subtract(vertical);

        if (inputDir.lengthSqr() > 0.0001) {
            inputDir = inputDir.normalize();
        }

        double maxSpeed = 16.0 * flightSpeedMultiplier;
        double accel = 50.0;
        Vec3 targetVel = inputDir.scale(maxSpeed);

        flightVelocity = flightVelocity.lerp(targetVel, Math.min(1.0, accel * dt));
        workCameraPos = workCameraPos.add(flightVelocity.scale(dt));
        applyCameraState();
    }

    public static void resetWorkCameraRoll() {
        workCameraRoll = 0.0f;
        applyCameraState();
    }

    public static void adjustWorkCameraRoll(float deltaRoll) {
        workCameraRoll = (workCameraRoll + deltaRoll) % 360.0f;
        applyCameraState();
    }

    public static void updateFlight(float dt, boolean forward, boolean backward, boolean left, boolean right, boolean up, boolean down, boolean rollLeft, boolean rollRight) {
        if (currentMode != CameraMode.WORK_CAMERA) return;

        if (rollLeft) {
            workCameraRoll = (workCameraRoll - 45.0f * dt * (float) Math.max(0.5, flightSpeedMultiplier)) % 360.0f;
        }
        if (rollRight) {
            workCameraRoll = (workCameraRoll + 45.0f * dt * (float) Math.max(0.5, flightSpeedMultiplier)) % 360.0f;
        }

        updateFlight(dt, forward, backward, left, right, up, down);
    }

    public static void setSpeedMultiplier(double multiplier) {
        flightSpeedMultiplier = Math.max(0.1, Math.min(10.0, multiplier));
    }

    public static double getSpeedMultiplier() {
        return flightSpeedMultiplier;
    }
}
