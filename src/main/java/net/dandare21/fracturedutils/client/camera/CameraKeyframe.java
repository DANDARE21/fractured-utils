package net.dandare21.fracturedutils.client.camera;

import net.dandare21.fracturedutils.sound.sequence.MusicSequenceEntry;
import net.minecraft.world.phys.Vec3;

public class CameraKeyframe {
    private final long timestampMs;
    private final long durationMs;
    private final CameraTransform transform;
    private final SplineInterpolationType splineMode;
    private final CameraEasing easing;
    private final boolean interpolate;

    // Bézier tangent handles (relative offsets)
    private final Vec3 inHandle;
    private final Vec3 outHandle;

    // Decoupled Look-At Target (Inverse Kinematics)
    private final boolean lookAtEnabled;
    private final String lookAtMode; // "COORDINATE" or "ENTITY"
    private final Vec3 lookAtCoord;
    private final String lookAtTarget;
    private final double lookAtWeight;

    // Legacy / Third-Person camera modes
    private final String cameraMode; // STATIC, FOLLOW, OVER_THE_SHOULDER, CLEAR
    private final String cameraTarget;
    private final double heightOffset;
    private final double backDistance;
    private final double shoulderOffset;

    public CameraKeyframe(long timestampMs, long durationMs, CameraTransform transform,
                          SplineInterpolationType splineMode, CameraEasing easing,
                          Vec3 inHandle, Vec3 outHandle,
                          boolean lookAtEnabled, String lookAtMode, Vec3 lookAtCoord, String lookAtTarget, double lookAtWeight,
                          String cameraMode, String cameraTarget, double heightOffset, double backDistance, double shoulderOffset) {
        this(timestampMs, durationMs, transform, splineMode, easing, true, inHandle, outHandle,
                lookAtEnabled, lookAtMode, lookAtCoord, lookAtTarget, lookAtWeight,
                cameraMode, cameraTarget, heightOffset, backDistance, shoulderOffset);
    }

    public CameraKeyframe(long timestampMs, long durationMs, CameraTransform transform,
                          SplineInterpolationType splineMode, CameraEasing easing, boolean interpolate,
                          Vec3 inHandle, Vec3 outHandle,
                          boolean lookAtEnabled, String lookAtMode, Vec3 lookAtCoord, String lookAtTarget, double lookAtWeight,
                          String cameraMode, String cameraTarget, double heightOffset, double backDistance, double shoulderOffset) {
        this.timestampMs = timestampMs;
        this.durationMs = durationMs;
        this.transform = transform;
        this.splineMode = splineMode != null ? splineMode : SplineInterpolationType.CATMULL_ROM;
        this.easing = easing != null ? easing : CameraEasing.EASE_IN_OUT_CUBIC;
        this.interpolate = interpolate;
        this.inHandle = inHandle != null ? inHandle : Vec3.ZERO;
        this.outHandle = outHandle != null ? outHandle : Vec3.ZERO;
        this.lookAtEnabled = lookAtEnabled;
        this.lookAtMode = lookAtMode != null ? lookAtMode : "COORDINATE";
        this.lookAtCoord = lookAtCoord != null ? lookAtCoord : Vec3.ZERO;
        this.lookAtTarget = lookAtTarget != null ? lookAtTarget : "@p";
        this.lookAtWeight = Math.max(0.0, Math.min(1.0, lookAtWeight));
        this.cameraMode = cameraMode != null ? cameraMode : "STATIC";
        this.cameraTarget = cameraTarget != null ? cameraTarget : "@p";
        this.heightOffset = heightOffset;
        this.backDistance = backDistance;
        this.shoulderOffset = shoulderOffset;
    }

    public static CameraKeyframe fromEntry(MusicSequenceEntry entry) {
        if (entry == null) {
            return new CameraKeyframe(0L, 3000L, CameraTransform.fromEuler(0, 64, 0, 0, 0, 0, 70),
                    SplineInterpolationType.CATMULL_ROM, CameraEasing.EASE_IN_OUT_CUBIC, true,
                    Vec3.ZERO, Vec3.ZERO, false, "COORDINATE", Vec3.ZERO, "@p", 1.0,
                    "STATIC", "@p", 5.5, 2.4, 0.55);
        }

        CameraTransform transform = CameraTransform.fromEuler(
                entry.getCameraX(), entry.getCameraY(), entry.getCameraZ(),
                entry.getCameraYaw(), entry.getCameraPitch(), entry.getCameraRoll(),
                entry.getCameraFov()
        );

        SplineInterpolationType spline = SplineInterpolationType.fromString(entry.getSplineMode());
        CameraEasing easing = CameraEasing.fromString(entry.getCameraEasing());
        boolean interpolate = entry.isCameraInterpolate();

        Vec3 inH = new Vec3(entry.getInHandleX(), entry.getInHandleY(), entry.getInHandleZ());
        Vec3 outH = new Vec3(entry.getOutHandleX(), entry.getOutHandleY(), entry.getOutHandleZ());
        Vec3 lookAtPos = new Vec3(entry.getLookAtX(), entry.getLookAtY(), entry.getLookAtZ());

        String mode = entry.getCameraMode();
        if (mode == null || mode.isBlank()) {
            mode = entry.getSubAction();
        }
        if (mode == null || mode.isBlank()) {
            String cmd = entry.getCommand();
            if (cmd != null) {
                if (cmd.contains("camera enable")) mode = "ENABLE";
                else if (cmd.contains("camera disable")) mode = "DISABLE";
                else if (cmd.contains("camera ots")) mode = "OVER_THE_SHOULDER";
                else if (cmd.contains("camera follow")) mode = "FOLLOW";
                else mode = "KEYFRAME";
            }
        }
        if (mode == null || mode.isBlank()) {
            mode = "KEYFRAME";
        }
        if ("STATIC".equalsIgnoreCase(mode)) {
            mode = "KEYFRAME";
        } else if ("CLEAR".equalsIgnoreCase(mode)) {
            mode = "DISABLE";
        }

        long dur = entry.getDurationMs() > 0 ? entry.getDurationMs() : entry.getTotalDurationMs();

        return new CameraKeyframe(
                entry.getTimestampMs(),
                Math.max(0L, dur),
                transform,
                spline,
                easing,
                interpolate,
                inH,
                outH,
                entry.isLookAtEnabled(),
                entry.getLookAtMode(),
                lookAtPos,
                entry.getLookAtTarget(),
                entry.getLookAtWeight(),
                mode,
                entry.getCameraTarget(),
                entry.getCameraHeightOffset(),
                entry.getCameraBackDistance(),
                entry.getCameraShoulderOffset()
        );
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public CameraTransform getTransform() {
        return transform;
    }

    public SplineInterpolationType getSplineMode() {
        return splineMode;
    }

    public CameraEasing getEasing() {
        return easing;
    }

    public boolean isInterpolate() {
        return interpolate;
    }

    public Vec3 getInHandle() {
        return inHandle;
    }

    public Vec3 getOutHandle() {
        return outHandle;
    }

    public boolean isLookAtEnabled() {
        return lookAtEnabled;
    }

    public String getLookAtMode() {
        return lookAtMode;
    }

    public Vec3 getLookAtCoord() {
        return lookAtCoord;
    }

    public String getLookAtTarget() {
        return lookAtTarget;
    }

    public double getLookAtWeight() {
        return lookAtWeight;
    }

    public String getCameraMode() {
        return cameraMode;
    }

    public String getCameraTarget() {
        return cameraTarget;
    }

    public double getHeightOffset() {
        return heightOffset;
    }

    public double getBackDistance() {
        return backDistance;
    }

    public double getShoulderOffset() {
        return shoulderOffset;
    }

    public boolean isEnable() {
        return "ENABLE".equalsIgnoreCase(cameraMode);
    }

    public boolean isDisable() {
        return "DISABLE".equalsIgnoreCase(cameraMode) || "CLEAR".equalsIgnoreCase(cameraMode);
    }

    public boolean isKeyframe() {
        return !isEnable() && !isDisable();
    }
}
