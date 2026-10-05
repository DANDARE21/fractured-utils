package net.dandare21.fracturedutils.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class CustomCameraManager {
    private static boolean active = false;
    private static CameraTransform currentTransform = null;
    private static boolean renderPlayerModel = true;

    // Active Sequence Track for continuous spline playback
    private static CameraSequenceTrack activeTrack = null;
    private static long trackStartTimeMs = 0L;
    private static double manualTrackTimeMs = -1.0;

    // Legacy / Third-person settings
    private static Entity targetEntity = null;
    private static double heightOffset = 5.5;
    private static boolean overTheShoulder = false;
    private static double backDistance = 2.4;
    private static double shoulderOffset = 0.55;
    private static Vec3 currentSmoothPos = null;
    private static float currentSmoothYaw = Float.NaN;
    private static float currentSmoothPitch = Float.NaN;

    private static boolean customFovActive = false;
    private static double customFov = 70.0;

    public static void setCustomTransform(CameraTransform transform, boolean renderPlayerModel) {
        CustomCameraManager.targetEntity = null;
        CustomCameraManager.overTheShoulder = false;
        CustomCameraManager.activeTrack = null;
        CustomCameraManager.currentTransform = transform;
        CustomCameraManager.renderPlayerModel = renderPlayerModel;
        if (transform != null) {
            CustomCameraManager.customFov = transform.getFov();
            CustomCameraManager.customFovActive = true;
            CustomCameraManager.active = true;
        } else {
            CustomCameraManager.customFovActive = false;
            CustomCameraManager.active = false;
        }
    }

    public static void setCustomCamera(Vec3 pos, float yaw, float pitch, float roll, boolean renderPlayerModel) {
        CameraTransform transform = CameraTransform.fromEuler(pos, yaw, pitch, roll, customFovActive ? customFov : 70.0);
        setCustomTransform(transform, renderPlayerModel);
    }

    public static void setCustomCamera(double x, double y, double z, float yaw, float pitch, boolean renderPlayerModel) {
        setCustomCamera(new Vec3(x, y, z), yaw, pitch, 0.0f, renderPlayerModel);
    }

    public static void setCustomCamera(double x, double y, double z, float yaw, float pitch) {
        setCustomCamera(x, y, z, yaw, pitch, true);
    }

    public static void startTrackPlayback(CameraSequenceTrack track, long startTimeMs) {
        CustomCameraManager.activeTrack = track;
        CustomCameraManager.trackStartTimeMs = startTimeMs;
        CustomCameraManager.manualTrackTimeMs = -1.0;
        CustomCameraManager.targetEntity = null;
        CustomCameraManager.overTheShoulder = false;
        CustomCameraManager.renderPlayerModel = true;
        CustomCameraManager.currentTransform = null;
        CustomCameraManager.customFovActive = false;
        CustomCameraManager.active = track != null && !track.isEmpty();
    }

    public static void setManualTrackTime(CameraSequenceTrack track, double timeMs) {
        CustomCameraManager.activeTrack = track;
        CustomCameraManager.manualTrackTimeMs = timeMs;
        CustomCameraManager.targetEntity = null;
        CustomCameraManager.overTheShoulder = false;
        CustomCameraManager.renderPlayerModel = true;
        CustomCameraManager.currentTransform = null;
        CustomCameraManager.customFovActive = false;
        CustomCameraManager.active = track != null && !track.isEmpty();
    }

    public static void setTargetEntity(Entity target, double heightOffset, float pitch) {
        CustomCameraManager.activeTrack = null;
        CustomCameraManager.targetEntity = target;
        CustomCameraManager.overTheShoulder = false;
        CustomCameraManager.heightOffset = heightOffset;
        CustomCameraManager.currentTransform = null;
        if (target != null) {
            CustomCameraManager.currentTransform = CameraTransform.fromEuler(
                    target.position().add(0, heightOffset, 0),
                    target.getYRot(), pitch, 0.0f, customFovActive ? customFov : 70.0
            );
        }
        CustomCameraManager.renderPlayerModel = true;
        CustomCameraManager.active = target != null;
    }

    public static void setOverTheShoulderTarget(Entity target, double backDistance, double shoulderOffset, double heightOffset, float pitch) {
        CustomCameraManager.activeTrack = null;
        CustomCameraManager.targetEntity = target;
        CustomCameraManager.overTheShoulder = true;
        CustomCameraManager.backDistance = backDistance;
        CustomCameraManager.shoulderOffset = shoulderOffset;
        CustomCameraManager.heightOffset = heightOffset;
        CustomCameraManager.currentTransform = null;
        if (target != null) {
            CustomCameraManager.currentTransform = CameraTransform.fromEuler(
                    target.position(), target.getYRot(), pitch, 0.0f, customFovActive ? customFov : 70.0
            );
        }
        CustomCameraManager.renderPlayerModel = true;
        CustomCameraManager.active = target != null;
    }

    public static void setCustomFov(double fov) {
        CustomCameraManager.customFov = fov > 0 ? fov : 70.0;
        CustomCameraManager.customFovActive = true;
    }

    public static void clearCustomFov() {
        CustomCameraManager.customFovActive = false;
        CustomCameraManager.customFov = 70.0;
    }

    public static boolean isFovActive() {
        return isCameraActive() && customFovActive;
    }

    public static double getCustomFov() {
        if (currentTransform != null) {
            return currentTransform.getFov();
        }
        return customFov;
    }

    public static void clearCustomCamera() {
        CustomCameraManager.active = false;
        CustomCameraManager.activeTrack = null;
        CustomCameraManager.currentTransform = null;
        CustomCameraManager.manualTrackTimeMs = -1.0;
        CustomCameraManager.targetEntity = null;
        CustomCameraManager.overTheShoulder = false;
        CustomCameraManager.currentSmoothPos = null;
        CustomCameraManager.currentSmoothYaw = Float.NaN;
        CustomCameraManager.currentSmoothPitch = Float.NaN;
        CustomCameraManager.customFovActive = false;
        CustomCameraManager.customFov = 70.0;
        CameraUtils.resetProximity();
    }

    public static double getCurrentTrackTimeMs() {
        if (manualTrackTimeMs >= 0.0) {
            return manualTrackTimeMs;
        }
        if (activeTrack != null && trackStartTimeMs > 0L) {
            long now = System.currentTimeMillis();
            return Math.max(0.0, now - trackStartTimeMs);
        }
        return -1.0;
    }

    public static boolean hasActiveTrack() {
        return activeTrack != null && !activeTrack.isEmpty();
    }

    public static boolean isCameraActive() {
        if (activeTrack != null && !activeTrack.isEmpty()) {
            double evalTimeMs = getCurrentTrackTimeMs();
            if (evalTimeMs < 0.0) {
                currentTransform = null;
                customFovActive = false;
                return false;
            }
            CameraShot shot = activeTrack.getActiveShot(evalTimeMs);
            if (shot == null) {
                currentTransform = null;
                customFovActive = false;
                return false;
            }
            return true;
        }
        return (currentTransform != null || targetEntity != null);
    }

    public static boolean isActive() {
        return isCameraActive();
    }

    public static CameraTransform getCurrentTransform() {
        return currentTransform;
    }

    public static CameraSequenceTrack getActiveTrack() {
        return activeTrack;
    }

    public static void updateTrackFrame(float partialTick) {
        if (activeTrack == null) return;
        Minecraft mc = Minecraft.getInstance();

        double evalTimeMs = getCurrentTrackTimeMs();
        if (evalTimeMs < 0.0) {
            currentTransform = null;
            customFovActive = false;
            return;
        }

        CameraTransform frame = activeTrack.evaluate(evalTimeMs, partialTick, mc);
        if (frame != null) {
            currentTransform = frame;
            customFov = frame.getFov();
            customFovActive = true;
        } else {
            currentTransform = null;
            customFovActive = false;
            long endLimit = activeTrack.getLastShotEndMs() > 0 ? activeTrack.getLastShotEndMs() : activeTrack.getTotalEndMs();
            if (!activeTrack.isLooping() && evalTimeMs > endLimit + 500L && manualTrackTimeMs < 0.0) {
                clearCustomCamera();
            }
        }
    }

    public static Entity getTargetEntity() {
        return targetEntity;
    }

    public static boolean isOverTheShoulder() {
        return overTheShoulder;
    }

    public static double getBackDistance() {
        return backDistance;
    }

    public static double getShoulderOffset() {
        return shoulderOffset;
    }

    public static double getHeightOffset() {
        return heightOffset;
    }

    public static Vec3 getCurrentSmoothPos() {
        return currentSmoothPos;
    }

    public static void setCurrentSmoothPos(Vec3 pos) {
        currentSmoothPos = pos;
    }

    public static float getCurrentSmoothYaw() {
        return currentSmoothYaw;
    }

    public static float getCurrentSmoothPitch() {
        return currentSmoothPitch;
    }

    public static void setCurrentSmoothRotation(float yaw, float pitch) {
        currentSmoothYaw = yaw;
        currentSmoothPitch = pitch;
    }

    public static Vec3 getCustomPosition() {
        return currentTransform != null ? currentTransform.getPosition() : Vec3.ZERO;
    }

    public static Quaternionf getCustomRotation() {
        return currentTransform != null ? currentTransform.getRotation() : new Quaternionf();
    }

    public static float getCustomYaw() {
        return currentTransform != null ? currentTransform.getYaw() : 0.0f;
    }

    public static float getCustomPitch() {
        return currentTransform != null ? currentTransform.getPitch() : 0.0f;
    }

    public static float getCustomRoll() {
        return currentTransform != null ? currentTransform.getRoll() : 0.0f;
    }

    public static boolean shouldRenderPlayerModel() {
        return renderPlayerModel;
    }
}
