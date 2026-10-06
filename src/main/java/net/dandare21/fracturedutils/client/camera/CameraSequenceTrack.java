package net.dandare21.fracturedutils.client.camera;

import net.dandare21.fracturedutils.sound.sequence.MusicSequenceEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class CameraSequenceTrack {
    private final List<CameraKeyframe> keyframes;
    private final List<CameraShot> shots;
    private final boolean looping;
    private final long startMs;
    private final long endMs;

    public CameraSequenceTrack(List<CameraKeyframe> keyframes, List<CameraShot> shots, boolean looping, long startMs, long endMs) {
        this.keyframes = new ArrayList<>(keyframes != null ? keyframes : List.of());
        this.keyframes.sort(Comparator.comparingLong(CameraKeyframe::getTimestampMs));
        this.shots = new ArrayList<>(shots != null ? shots : List.of());
        this.looping = looping;
        this.startMs = startMs;
        this.endMs = endMs;
    }

    public static CameraSequenceTrack compile(List<MusicSequenceEntry> entries, boolean looping, long startMs, long endMs) {
        List<CameraKeyframe> allKfs = new ArrayList<>();
        if (entries != null) {
            for (MusicSequenceEntry e : entries) {
                if (e != null) {
                    allKfs.add(CameraKeyframe.fromEntry(e));
                }
            }
        }
        allKfs.sort((a, b) -> {
            int cmp = Long.compare(a.getTimestampMs(), b.getTimestampMs());
            if (cmp != 0) return cmp;
            int orderA = a.isEnable() ? 0 : (a.isDisable() ? 2 : 1);
            int orderB = b.isEnable() ? 0 : (b.isDisable() ? 2 : 1);
            return Integer.compare(orderA, orderB);
        });

        List<CameraShot> shots = new ArrayList<>();
        long currentShotStart = -1;
        CameraKeyframe currentEnable = null;
        List<CameraKeyframe> currentShotKfs = new ArrayList<>();

        for (CameraKeyframe kf : allKfs) {
            if (kf.isEnable()) {
                if (currentShotStart >= 0) {
                    long shotEnd = kf.getTimestampMs();
                    if (!currentShotKfs.isEmpty() || currentEnable != null) {
                        shots.add(buildShot(currentShotStart, shotEnd, currentShotKfs, currentEnable, null));
                    }
                    currentShotKfs.clear();
                }
                currentShotStart = kf.getTimestampMs();
                currentEnable = kf;
            } else if (kf.isDisable()) {
                if (currentShotStart >= 0) {
                    long shotEnd = kf.getTimestampMs();
                    shots.add(buildShot(currentShotStart, shotEnd, currentShotKfs, currentEnable, kf));
                    currentShotStart = -1;
                    currentEnable = null;
                    currentShotKfs.clear();
                }
            } else {
                // KEYFRAME (Position & Rotation, FOLLOW, OVER_THE_SHOULDER)
                if (currentShotStart < 0) {
                    currentShotStart = (startMs >= 0 && startMs < kf.getTimestampMs() && currentEnable == null) ? startMs : kf.getTimestampMs();
                }
                currentShotKfs.add(kf);
            }
        }

        // Close final open shot if not explicitly closed
        if (currentShotStart >= 0 && (!currentShotKfs.isEmpty() || currentEnable != null)) {
            long finalEnd = currentShotStart + 3000L;
            if (!currentShotKfs.isEmpty()) {
                CameraKeyframe last = currentShotKfs.get(currentShotKfs.size() - 1);
                long holdMs = last.getDurationMs() > 0 ? last.getDurationMs() : 3000L;
                finalEnd = Math.max(finalEnd, last.getTimestampMs() + holdMs);
            }
            if (endMs > 0 && finalEnd < endMs) {
                finalEnd = endMs;
            }
            shots.add(buildShot(currentShotStart, finalEnd, currentShotKfs, currentEnable, null));
        }

        return new CameraSequenceTrack(allKfs, shots, looping, startMs, endMs);
    }

    private static CameraShot buildShot(long startMs, long endMs, List<CameraKeyframe> kfs, CameraKeyframe enableKf, CameraKeyframe disableKf) {
        List<CameraKeyframe> shotKfs = new ArrayList<>(kfs);
        // Only if there are NO spatial keyframes at all in this shot, use enableKf as the fallback single keyframe
        if (shotKfs.isEmpty() && enableKf != null) {
            shotKfs.add(enableKf);
        }
        long blendIn = enableKf != null ? Math.max(0L, enableKf.getDurationMs()) : 0L;
        long blendOut = disableKf != null ? Math.max(0L, disableKf.getDurationMs()) : 0L;
        return new CameraShot(startMs, endMs, shotKfs, enableKf, disableKf, blendIn, blendOut);
    }

    public boolean isEmpty() {
        return keyframes.isEmpty() && shots.isEmpty();
    }

    public List<CameraKeyframe> getKeyframes() {
        return keyframes;
    }

    public List<CameraShot> getShots() {
        return shots;
    }

    public CameraShot getActiveShot(double timeMs) {
        double t = timeMs;
        if (looping && getTotalEndMs() > 0) {
            long total = getTotalEndMs();
            long start = Math.max(0, startMs);
            long span = total - start;
            if (span > 0) {
                t = start + ((long)(timeMs - start) % span);
            }
        }
        for (CameraShot shot : shots) {
            if (shot.contains(t)) {
                return shot;
            }
        }
        return null;
    }

    public boolean isLooping() {
        return looping;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public long getTotalEndMs() {
        if (endMs > 0) return endMs;
        return getLastShotEndMs();
    }

    public long getLastShotEndMs() {
        long max = 0L;
        for (CameraShot s : shots) {
            if (s.getEndMs() > max) max = s.getEndMs();
        }
        for (CameraKeyframe k : keyframes) {
            long kEnd = k.getTimestampMs() + k.getDurationMs();
            if (kEnd > max) max = kEnd;
        }
        return max;
    }

    /**
     * Evaluates the 6-DOF camera transform at the specified sequence time in milliseconds.
     * Transitions smoothly from one camera keyframe to another along splines.
     * Returns null if camera is disabled (outside active camera shots).
     *
     * @param timeMs      Current timeline or sequence playback time
     * @param partialTick Render partial tick for entity interpolation
     * @param mc          Minecraft client instance
     * @return Evaluated CameraTransform, or null if camera is inactive/disabled
     */
    public CameraTransform evaluate(double timeMs, float partialTick, Minecraft mc) {
        if (shots.isEmpty()) return null;
        double t = timeMs;
        if (looping && getTotalEndMs() > 0) {
            long total = getTotalEndMs();
            long start = Math.max(0, startMs);
            long span = total - start;
            if (span > 0) {
                t = start + ((long)(timeMs - start) % span);
            }
        }
        CameraShot activeShot = getActiveShot(t);
        if (activeShot == null) return null;
        return activeShot.evaluate(t, partialTick, mc);
    }

    /**
     * Samples 3D positions along the trajectory for in-world visualization.
     *
     * @param sampleIntervalMs Sampling step in milliseconds (e.g. 50ms)
     * @param mc               Minecraft client instance
     * @return Ordered list of 3D positions along the camera path
     */
    public List<Vec3> samplePathPoints(int sampleIntervalMs, Minecraft mc) {
        List<Vec3> points = new ArrayList<>();
        if (shots.isEmpty()) return points;
        int step = Math.max(20, sampleIntervalMs);
        for (CameraShot shot : shots) {
            for (long t = shot.getStartMs(); t <= shot.getEndMs(); t += step) {
                CameraTransform tr = shot.evaluate(t, 1.0f, mc);
                if (tr != null) {
                    points.add(tr.getPosition());
                }
            }
        }
        return points;
    }

    private CameraTransform applyLookAtIfEnabled(CameraTransform base, CameraKeyframe kf, float partialTick, Minecraft mc) {
        if (!kf.isLookAtEnabled()) {
            return base;
        }

        Vec3 targetPos = resolveLookAtPosition(kf, partialTick, mc);
        if (targetPos == null) {
            return base;
        }

        return base.withLookAt(targetPos, kf.getLookAtWeight());
    }

    public static Vec3 resolveLookAtPosition(CameraKeyframe kf, float partialTick, Minecraft mc) {
        if ("ENTITY".equalsIgnoreCase(kf.getLookAtMode())) {
            Entity target = resolveTargetEntity(kf.getLookAtTarget(), mc);
            if (target != null) {
                double x = Mth.lerp(partialTick, target.xo, target.getX());
                double y = Mth.lerp(partialTick, target.yo, target.getY()) + target.getEyeHeight();
                double z = Mth.lerp(partialTick, target.zo, target.getZ());
                return new Vec3(x, y, z);
            }
        }
        return kf.getLookAtCoord();
    }

    public static Entity resolveTargetEntity(String targetStr, Minecraft mc) {
        if (mc == null || mc.level == null) return null;
        if (targetStr == null || targetStr.isBlank() || "@p".equalsIgnoreCase(targetStr) || "player".equalsIgnoreCase(targetStr)) {
            return mc.player;
        }

        // Check by UUID
        try {
            UUID uuid = UUID.fromString(targetStr.trim());
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e.getUUID().equals(uuid)) return e;
            }
        } catch (IllegalArgumentException ignored) {}

        // Check by Tag or Name
        String clean = targetStr.startsWith("#") ? targetStr.substring(1) : targetStr;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getTags().contains(clean) || (e.getCustomName() != null && e.getCustomName().getString().equalsIgnoreCase(clean))) {
                return e;
            }
        }

        return null;
    }

    private CameraTransform evaluateThirdPerson(CameraKeyframe kf, float partialTick, Minecraft mc) {
        Entity target = resolveTargetEntity(kf.getCameraTarget(), mc);
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
