package net.dandare21.fracturedutils.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Represents an active, keyframe-based camera shot spanning from startMs to endMs.
 * Directly inspired by 3D animation timelines (Mine-imator / Blender).
 */
public class CameraShot {
    private final long startMs;
    private final long endMs;
    private final List<CameraKeyframe> keyframes;
    private final CameraKeyframe enableKeyframe;
    private final CameraKeyframe disableKeyframe;
    private final long blendInMs;
    private final long blendOutMs;

    public CameraShot(long startMs, long endMs, List<CameraKeyframe> keyframes, CameraKeyframe enableKeyframe, CameraKeyframe disableKeyframe) {
        this(startMs, endMs, keyframes, enableKeyframe, disableKeyframe, 0L, 0L);
    }

    public CameraShot(long startMs, long endMs, List<CameraKeyframe> keyframes, CameraKeyframe enableKeyframe, CameraKeyframe disableKeyframe, long blendInMs, long blendOutMs) {
        this.startMs = startMs;
        this.endMs = Math.max(startMs, endMs);
        List<CameraKeyframe> sortedKfs = new ArrayList<>(keyframes != null ? keyframes : List.of());
        sortedKfs.sort(Comparator.comparingLong(CameraKeyframe::getTimestampMs));
        this.keyframes = Collections.unmodifiableList(sortedKfs);
        this.enableKeyframe = enableKeyframe;
        this.disableKeyframe = disableKeyframe;
        this.blendInMs = Math.max(0L, blendInMs);
        this.blendOutMs = Math.max(0L, blendOutMs);
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public long getDurationMs() {
        return Math.max(0L, endMs - startMs);
    }

    public List<CameraKeyframe> getKeyframes() {
        return keyframes;
    }

    public CameraKeyframe getEnableKeyframe() {
        return enableKeyframe;
    }

    public CameraKeyframe getDisableKeyframe() {
        return disableKeyframe;
    }

    public long getBlendInMs() {
        return blendInMs;
    }

    public long getBlendOutMs() {
        return blendOutMs;
    }

    public boolean contains(double timeMs) {
        return timeMs >= startMs && timeMs <= endMs;
    }

    /**
     * Evaluates the 6-DOF camera transform at the given sequence time.
     *
     * @param timeMs      Current sequence timestamp in milliseconds
     * @param partialTick Render partial tick for entity interpolation
     * @param mc          Minecraft client instance
     * @return Evaluated CameraTransform or null if shot has no valid data
     */
    public CameraTransform evaluate(double timeMs, float partialTick, Minecraft mc) {
        if (keyframes.isEmpty()) {
            if (enableKeyframe != null) {
                CameraTransform base = applyLookAtIfEnabled(enableKeyframe.getTransform(), enableKeyframe, partialTick, mc);
                return applyBlending(base, timeMs, partialTick, mc);
            }
            return null;
        }

        CameraTransform base = evaluateBaseTransform(timeMs, partialTick, mc);
        return applyBlending(base, timeMs, partialTick, mc);
    }

    private CameraTransform evaluateBaseTransform(double timeMs, float partialTick, Minecraft mc) {
        int n = keyframes.size();

        // 1. If timeMs <= first keyframe, hold first keyframe (or blend in from player if lead-in exists)
        if (timeMs <= keyframes.get(0).getTimestampMs()) {
            CameraKeyframe first = keyframes.get(0);
            CameraTransform firstTr = evaluateKeyframeOrThirdPerson(first, partialTick, mc);
            long firstTs = first.getTimestampMs();
            if (firstTs > startMs && timeMs < firstTs) {
                CameraTransform playerTr = getPlayerTransform(partialTick, mc, firstTr);
                long blendDuration = (blendInMs > 0 && blendInMs < (firstTs - startMs)) ? blendInMs : (firstTs - startMs);
                long blendStart = startMs;
                long blendEnd = startMs + blendDuration;
                if (timeMs >= blendEnd) {
                    return firstTr;
                }
                CameraEasing easing = (enableKeyframe != null && enableKeyframe.getEasing() != null)
                        ? enableKeyframe.getEasing()
                        : CameraEasing.EASE_IN_OUT_CUBIC;
                boolean smooth = enableKeyframe == null || enableKeyframe.isInterpolate();
                if (easing == CameraEasing.INSTANT) smooth = false;
                double rawT = Mth.clamp((timeMs - blendStart) / (double) blendDuration, 0.0, 1.0);
                double easedT = smooth ? easing.ease(rawT) : (rawT >= 1.0 ? 1.0 : 0.0);
                return playerTr.interpolate(firstTr, easedT);
            }
            return firstTr;
        }

        // 2. If timeMs >= last keyframe, hold last keyframe until shot ends (or blend out to player if disable exists)
        if (timeMs >= keyframes.get(n - 1).getTimestampMs()) {
            CameraKeyframe last = keyframes.get(n - 1);
            CameraTransform lastTr = evaluateKeyframeOrThirdPerson(last, partialTick, mc);
            long lastTs = last.getTimestampMs();
            if (disableKeyframe != null && endMs > lastTs && timeMs > lastTs) {
                CameraTransform playerTr = getPlayerTransform(partialTick, mc, lastTr);
                long blendDuration = (blendOutMs > 0 && blendOutMs < (endMs - lastTs)) ? blendOutMs : (endMs - lastTs);
                long blendEnd = lastTs + blendDuration;
                if (timeMs >= blendEnd) {
                    return playerTr;
                }
                CameraEasing easing = disableKeyframe.getEasing() != null ? disableKeyframe.getEasing() : CameraEasing.EASE_IN_OUT_CUBIC;
                boolean smooth = disableKeyframe.isInterpolate();
                if (easing == CameraEasing.INSTANT) smooth = false;
                double rawT = Mth.clamp((timeMs - lastTs) / (double) blendDuration, 0.0, 1.0);
                double easedT = smooth ? easing.ease(rawT) : (rawT >= 1.0 ? 1.0 : 0.0);
                return lastTr.interpolate(playerTr, easedT);
            }
            return lastTr;
        }

        // 3. Find active transition segment between keyframes.get(i) and keyframes.get(i + 1)
        int segIdx = 0;
        for (int i = 0; i < n - 1; i++) {
            if (timeMs >= keyframes.get(i).getTimestampMs() && timeMs < keyframes.get(i + 1).getTimestampMs()) {
                segIdx = i;
                break;
            }
        }

        CameraKeyframe kA = keyframes.get(segIdx);
        CameraKeyframe kB = keyframes.get(segIdx + 1);

        long tA = kA.getTimestampMs();
        long tB = kB.getTimestampMs();
        double span = tB - tA;

        if (span <= 0.0) {
            return evaluateKeyframeOrThirdPerson(kA, partialTick, mc);
        }

        // Compute normalized segment progress [0, 1]
        double rawT = Mth.clamp((timeMs - tA) / span, 0.0, 1.0);
        double easedT = kA.isInterpolate() ? kA.getEasing().ease(rawT) : (rawT >= 1.0 ? 1.0 : 0.0);

        boolean isTrackingA = "FOLLOW".equalsIgnoreCase(kA.getCameraMode()) || "OVER_THE_SHOULDER".equalsIgnoreCase(kA.getCameraMode());
        boolean isTrackingB = "FOLLOW".equalsIgnoreCase(kB.getCameraMode()) || "OVER_THE_SHOULDER".equalsIgnoreCase(kB.getCameraMode());

        if (isTrackingA || isTrackingB) {
            CameraTransform trA = evaluateKeyframeOrThirdPerson(kA, partialTick, mc);
            CameraTransform trB = evaluateKeyframeOrThirdPerson(kB, partialTick, mc);
            return trA.interpolate(trB, easedT);
        }

        // Check if current segment is a stationary hold (same position, rotation, and FOV)
        if (kA.getTransform().hasSameSpecs(kB.getTransform())) {
            return applyLookAtIfEnabled(kA.getTransform(), kA, partialTick, mc);
        }

        // Position evaluation along spline
        Vec3 pos;
        SplineInterpolationType splineType = kA.getSplineMode();
        Vec3 p1 = kA.getTransform().getPosition();
        Vec3 p2 = kB.getTransform().getPosition();

        if (p1.distanceToSqr(p2) < 1e-6) {
            // Position is stationary during this segment
            pos = p1;
        } else if (splineType == SplineInterpolationType.CATMULL_ROM) {
            Vec3 p0;
            if (segIdx > 0) {
                p0 = keyframes.get(segIdx - 1).getTransform().getPosition();
            } else {
                p0 = p1.add(p1.subtract(p2)); // Reflect backward
            }

            Vec3 p3;
            if (segIdx + 2 < n) {
                p3 = keyframes.get(segIdx + 2).getTransform().getPosition();
            } else {
                p3 = p2.add(p2.subtract(p1)); // Reflect forward
            }

            pos = SplineMath.evaluateCentripetalCatmullRom(p0, p1, p2, p3, easedT);
        } else if (splineType == SplineInterpolationType.BEZIER) {
            Vec3 p0 = p1;
            Vec3 p1H = p0.add(kA.getOutHandle());
            Vec3 p3 = p2;
            Vec3 p2H = p3.add(kB.getInHandle());

            pos = SplineMath.evaluateCubicBezier(p0, p1H, p2H, p3, easedT);
        } else if (splineType == SplineInterpolationType.LINEAR) {
            pos = SplineMath.evaluateLinear(p1, p2, easedT);
        } else {
            // STEP (Instant Cut)
            pos = p1;
        }

        // Rotation evaluation via Quaternion SLERP
        Quaternionf rot;
        if (splineType == SplineInterpolationType.STEP) {
            rot = kA.getTransform().getRotation();
        } else {
            float dot = Math.abs(kA.getTransform().getRotation().x * kB.getTransform().getRotation().x
                    + kA.getTransform().getRotation().y * kB.getTransform().getRotation().y
                    + kA.getTransform().getRotation().z * kB.getTransform().getRotation().z
                    + kA.getTransform().getRotation().w * kB.getTransform().getRotation().w);
            if ((1.0f - dot) <= 1e-4f) {
                rot = kA.getTransform().getRotation();
            } else {
                rot = CameraMath.slerp(kA.getTransform().getRotation(), kB.getTransform().getRotation(), easedT);
            }
        }

        // FOV evaluation
        double fov;
        if (Math.abs(kA.getTransform().getFov() - kB.getTransform().getFov()) < 1e-3) {
            fov = kA.getTransform().getFov();
        } else {
            fov = kA.getTransform().getFov() + (kB.getTransform().getFov() - kA.getTransform().getFov()) * easedT;
        }

        CameraTransform result = CameraTransform.fromQuaternion(pos, rot, fov);

        // Apply Decoupled Look-At IK if configured on current segment
        return applyLookAtIfEnabled(result, kA, partialTick, mc);
    }

    private CameraTransform evaluateKeyframeOrThirdPerson(CameraKeyframe kf, float partialTick, Minecraft mc) {
        if ("FOLLOW".equalsIgnoreCase(kf.getCameraMode()) || "OVER_THE_SHOULDER".equalsIgnoreCase(kf.getCameraMode())) {
            return evaluateThirdPerson(kf, partialTick, mc);
        }
        return applyLookAtIfEnabled(kf.getTransform(), kf, partialTick, mc);
    }

    private CameraTransform applyBlending(CameraTransform base, double timeMs, float partialTick, Minecraft mc) {
        CameraTransform result = base;

        // 1. Blend in from player's view at start of shot
        // Only apply if there was no pre-keyframe lead-in (i.e. first keyframe timestamp <= startMs)
        long firstTs = !keyframes.isEmpty() ? keyframes.get(0).getTimestampMs() : startMs;
        if (firstTs <= startMs && blendInMs > 0 && timeMs < startMs + blendInMs) {
            CameraTransform playerTr = getPlayerTransform(partialTick, mc, base);
            CameraEasing easing = (enableKeyframe != null && enableKeyframe.getEasing() != null)
                    ? enableKeyframe.getEasing()
                    : CameraEasing.EASE_IN_OUT_CUBIC;
            boolean smooth = enableKeyframe == null || enableKeyframe.isInterpolate();
            if (easing == CameraEasing.INSTANT) smooth = false;
            double rawT = Mth.clamp((timeMs - startMs) / (double) blendInMs, 0.0, 1.0);
            double easedT = smooth ? easing.ease(rawT) : (rawT >= 1.0 ? 1.0 : 0.0);
            result = playerTr.interpolate(result, easedT);
        }

        // 2. Blend out back to player's view at end of shot
        // Only apply if there was no post-keyframe lead-out (i.e. last keyframe timestamp >= endMs)
        long lastTs = !keyframes.isEmpty() ? keyframes.get(keyframes.size() - 1).getTimestampMs() : endMs;
        if (disableKeyframe != null && lastTs >= endMs && blendOutMs > 0 && timeMs > endMs - blendOutMs) {
            CameraTransform playerTr = getPlayerTransform(partialTick, mc, base);
            CameraEasing easing = disableKeyframe.getEasing() != null
                    ? disableKeyframe.getEasing()
                    : CameraEasing.EASE_IN_OUT_CUBIC;
            boolean smooth = disableKeyframe.isInterpolate();
            if (easing == CameraEasing.INSTANT) smooth = false;
            double rawT = Mth.clamp((timeMs - (endMs - blendOutMs)) / (double) blendOutMs, 0.0, 1.0);
            double easedT = smooth ? easing.ease(rawT) : (rawT >= 1.0 ? 1.0 : 0.0);
            result = result.interpolate(playerTr, easedT);
        }

        return result;
    }

    private CameraTransform getPlayerTransform(float partialTick, Minecraft mc, CameraTransform fallback) {
        if (mc == null || mc.player == null) {
            return fallback != null ? fallback : CameraTransform.fromEuler(0, 64, 0, 0, 0, 0, 70);
        }
        double x = Mth.lerp(partialTick, mc.player.xo, mc.player.getX());
        double y = Mth.lerp(partialTick, mc.player.yo, mc.player.getY()) + mc.player.getEyeHeight();
        double z = Mth.lerp(partialTick, mc.player.zo, mc.player.getZ());
        float yaw = mc.player.getViewYRot(partialTick);
        float pitch = mc.player.getViewXRot(partialTick);
        double fov = (mc != null && mc.options != null && mc.options.fov() != null) ? mc.options.fov().get() : 70.0;
        return CameraTransform.fromEuler(x, y, z, yaw, pitch, 0.0f, fov);
    }

    private CameraTransform applyLookAtIfEnabled(CameraTransform base, CameraKeyframe kf, float partialTick, Minecraft mc) {
        if (!kf.isLookAtEnabled()) {
            return base;
        }

        Vec3 targetPos = CameraSequenceTrack.resolveLookAtPosition(kf, partialTick, mc);
        if (targetPos == null) {
            return base;
        }

        return base.withLookAt(targetPos, kf.getLookAtWeight());
    }

    private CameraTransform evaluateThirdPerson(CameraKeyframe kf, float partialTick, Minecraft mc) {
        Entity target = CameraSequenceTrack.resolveTargetEntity(kf.getCameraTarget(), mc);
        if (target == null) {
            return kf.getTransform();
        }

        double targetX = Mth.lerp(partialTick, target.xo, target.getX());
        double targetY = Mth.lerp(partialTick, target.yo, target.getY());
        double targetZ = Mth.lerp(partialTick, target.zo, target.getZ());

        float headYaw = target.getYRot();
        float headPitch = target.getXRot();
        if (target instanceof net.minecraft.world.entity.LivingEntity living) {
            headYaw = Mth.rotLerp(partialTick, living.yHeadRotO, living.getYHeadRot());
            headPitch = Mth.rotLerp(partialTick, target.xRotO, target.getXRot());
        }

        float yaw = headYaw;
        float pitch = (float) kf.getTransform().getPitch();
        float roll = (float) kf.getTransform().getRoll();

        Vec3 pos;
        if ("OVER_THE_SHOULDER".equalsIgnoreCase(kf.getCameraMode())) {
            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);

            double forwardX = -Math.sin(yawRad) * Math.cos(pitchRad);
            double forwardY = -Math.sin(pitchRad);
            double forwardZ = Math.cos(yawRad) * Math.cos(pitchRad);

            double rightX = Math.cos(yawRad);
            double rightZ = Math.sin(yawRad);

            double back = kf.getBackDistance();
            double shoulder = kf.getShoulderOffset();
            double headY = targetY + target.getEyeHeight() + kf.getHeightOffset();

            double camX = targetX - (forwardX * back) + (rightX * shoulder);
            double camY = headY - (forwardY * back);
            double camZ = targetZ - (forwardZ * back) + (rightZ * shoulder);
            pos = new Vec3(camX, camY, camZ);
        } else {
            // FOLLOW
            pos = new Vec3(targetX, targetY + kf.getHeightOffset(), targetZ);
        }

        return CameraTransform.fromEuler(pos, yaw, pitch, roll, kf.getTransform().getFov());
    }
}
