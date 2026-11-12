package net.dandare21.fracturedutils.client.camera;

import net.dandare21.fracturedutils.FracturedUtils;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CameraUtils {
    private static Method setPositionMethodDouble = null;
    private static Method setPositionMethodVec3 = null;
    private static Method setRotationMethod = null;
    private static Field positionField = null;
    private static Field blockPositionField = null;
    private static Field xRotField = null;
    private static Field yRotField = null;
    private static Field detachedField = null;
    private static Field rotationField = null;
    private static Field forwardsField = null;
    private static Field upField = null;
    private static Field leftField = null;
    private static boolean reflectionInitialized = false;

    private static final Vector3f FORWARDS = new Vector3f(0.0F, 0.0F, 1.0F);
    private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final Vector3f LEFT = new Vector3f(1.0F, 0.0F, 0.0F);

    private static void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        try {
            // Find methods on Camera.class
            for (Method m : Camera.class.getDeclaredMethods()) {
                m.setAccessible(true);
                String name = m.getName();
                Class<?>[] params = m.getParameterTypes();
                // Never match 'move' (SRG m_90568_) which applies relative camera-space translation
                if (!name.equals("move") && !name.equals("m_90568_")) {
                    if (params.length == 3 && params[0] == double.class && params[1] == double.class && params[2] == double.class) {
                        if (name.equals("setPosition") || name.equals("m_90575_") || setPositionMethodDouble == null) {
                            setPositionMethodDouble = m;
                        }
                    } else if (params.length == 1 && params[0] == Vec3.class) {
                        if (name.equals("setPosition") || name.equals("m_90576_") || setPositionMethodVec3 == null) {
                            setPositionMethodVec3 = m;
                        }
                    }
                }
                if (params.length == 2 && params[0] == float.class && params[1] == float.class) {
                    if (name.equals("setRotation") || name.equals("m_90573_") || setRotationMethod == null) {
                        setRotationMethod = m;
                    }
                }
            }

            // Find fields on Camera.class
            for (Field f : Camera.class.getDeclaredFields()) {
                f.setAccessible(true);
                String name = f.getName();
                Class<?> type = f.getType();
                if (type == Vec3.class) {
                    positionField = f;
                } else if (BlockPos.class.isAssignableFrom(type)) {
                    blockPositionField = f;
                } else if (type == boolean.class) {
                    // Match 'detached' (SRG f_90560_), but NOT 'initialized' (SRG f_90549_)
                    if (name.equals("detached") || name.equals("f_90560_")) {
                        detachedField = f;
                    }
                } else if (type == Quaternionf.class) {
                    rotationField = f;
                } else if (type == Vector3f.class) {
                    if (name.equals("forwards") || name.equals("f_90565_")) {
                        forwardsField = f;
                    } else if (name.equals("up") || name.equals("f_90566_")) {
                        upField = f;
                    } else if (name.equals("left") || name.equals("f_90567_")) {
                        leftField = f;
                    }
                } else if (type == float.class) {
                    if (name.equals("xRot") || name.equals("f_90570_")) {
                        xRotField = f;
                    } else if (name.equals("yRot") || name.equals("f_90571_")) {
                        yRotField = f;
                    }
                }
            }

            FracturedUtils.LOGGER.info("[CameraUtils] Initialized reflection fallback: positionField={}, blockPosField={}, setPosD={}, setPosV={}, setRot={}, rotField={}, detachedField={}",
                    positionField != null, blockPositionField != null,
                    setPositionMethodDouble != null, setPositionMethodVec3 != null,
                    setRotationMethod != null, rotationField != null, detachedField != null);
        } catch (Exception e) {
            FracturedUtils.LOGGER.error("[CameraUtils] Failed to initialize reflection fallback", e);
        }
    }

    public static void applyCameraOverride(Camera camera, ViewportEvent.ComputeCameraAngles event) {
        if (camera == null || !CustomCameraManager.isActive()) return;

        float partialTick = (float) event.getPartialTick();
        CustomCameraManager.updateTrackFrame(partialTick);

        Entity target = CustomCameraManager.getTargetEntity();
        CameraTransform transform = CustomCameraManager.getCurrentTransform();

        // If not targeting an entity and no active transform (e.g. outside camera shot), do not override
        if (target == null && transform == null) {
            return;
        }

        Vec3 pos;
        float yaw;
        float pitch;
        float roll;
        Quaternionf rot;

        if (target != null && target.isAlive()) {
            double targetX = Mth.lerp(partialTick, target.xo, target.getX());
            double targetY = Mth.lerp(partialTick, target.yo, target.getY());
            double targetZ = Mth.lerp(partialTick, target.zo, target.getZ());

            float yHeadRot;
            float yHeadRotO;
            if (target instanceof net.minecraft.world.entity.LivingEntity living) {
                yHeadRot = living.getYHeadRot();
                yHeadRotO = living.yHeadRotO;
            } else {
                yHeadRot = target.getYRot();
                yHeadRotO = target.yRotO;
            }

            float headYaw = Mth.rotLerp(partialTick, yHeadRotO, yHeadRot);
            float headPitch = Mth.rotLerp(partialTick, target.xRotO, target.getXRot());

            float targetPitch = CustomCameraManager.isOverTheShoulder() ? Mth.clamp(headPitch + 10.0f, -80.0f, 80.0f) : CustomCameraManager.getCustomPitch();

            float smoothYaw = CustomCameraManager.getCurrentSmoothYaw();
            float smoothPitch = CustomCameraManager.getCurrentSmoothPitch();

            if (Float.isNaN(smoothYaw)) {
                smoothYaw = headYaw;
                smoothPitch = targetPitch;
            } else {
                smoothYaw = Mth.rotLerp(0.25f, smoothYaw, headYaw);
                smoothPitch = Mth.rotLerp(0.25f, smoothPitch, targetPitch);
            }
            CustomCameraManager.setCurrentSmoothRotation(smoothYaw, smoothPitch);

            yaw = smoothYaw;
            pitch = smoothPitch;
            roll = CustomCameraManager.getCustomRoll();

            Vec3 targetPos;
            if (CustomCameraManager.isOverTheShoulder()) {
                double yawRad = Math.toRadians(smoothYaw);
                double pitchRad = Math.toRadians(smoothPitch);

                double forwardX = -Math.sin(yawRad) * Math.cos(pitchRad);
                double forwardY = -Math.sin(pitchRad);
                double forwardZ = Math.cos(yawRad) * Math.cos(pitchRad);

                double rightX = Math.cos(yawRad);
                double rightZ = Math.sin(yawRad);

                double back = CustomCameraManager.getBackDistance();
                double shoulder = CustomCameraManager.getShoulderOffset();
                double headY = targetY + target.getEyeHeight() + CustomCameraManager.getHeightOffset();

                double camX = targetX - (forwardX * back) + (rightX * shoulder);
                double camY = headY - (forwardY * back);
                double camZ = targetZ - (forwardZ * back) + (rightZ * shoulder);
                targetPos = new Vec3(camX, camY, camZ);
            } else {
                double camY = targetY + CustomCameraManager.getHeightOffset();
                targetPos = new Vec3(targetX, camY, targetZ);
            }

            Vec3 current = CustomCameraManager.getCurrentSmoothPos();
            if (current == null) {
                current = targetPos;
            } else {
                double x = current.x + (targetPos.x - current.x) * 0.25;
                double y = current.y + (targetPos.y - current.y) * 0.25;
                double z = current.z + (targetPos.z - current.z) * 0.25;
                current = new Vec3(x, y, z);
            }
            CustomCameraManager.setCurrentSmoothPos(current);
            pos = current;
            rot = CameraMath.eulerToQuaternion(yaw, pitch, roll);
        } else if (CustomCameraManager.getDialogTargetEntity() != null && CustomCameraManager.getDialogTargetEntity().isAlive()) {
            Entity dTarget = CustomCameraManager.getDialogTargetEntity();
            double targetX = Mth.lerp(partialTick, dTarget.xo, dTarget.getX());
            double targetY = Mth.lerp(partialTick, dTarget.yo, dTarget.getY());
            double targetZ = Mth.lerp(partialTick, dTarget.zo, dTarget.getZ());

            double baseY = targetY + dTarget.getEyeHeight();
            String anchor = CustomCameraManager.getDialogAnchor();
            if ("feet".equalsIgnoreCase(anchor)) baseY = targetY;
            else if ("chest".equalsIgnoreCase(anchor)) baseY = targetY + dTarget.getBbHeight() * 0.5;

            Vec3 offset = CustomCameraManager.getDialogOffset();
            pos = new Vec3(targetX + offset.x, baseY + offset.y, targetZ + offset.z);

            if (CustomCameraManager.isDialogLookAt()) {
                double dx = targetX - pos.x;
                double dy = baseY - pos.y;
                double dz = targetZ - pos.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                pitch = (float) Math.toDegrees(Math.atan2(-dy, dist));
            } else {
                yaw = CustomCameraManager.getDialogFixedYaw();
                pitch = CustomCameraManager.getDialogFixedPitch();
            }
            roll = 0.0f;
            rot = CameraMath.eulerToQuaternion(yaw, pitch, roll);
        } else {
            pos = transform.getPosition();
            yaw = transform.getYaw();
            pitch = transform.getPitch();
            roll = transform.getRoll();
            rot = transform.getRotation();
        }

        // Apply smooth transition interpolation if active
        long durationMs = CustomCameraManager.getTransitionDurationMs();
        if (durationMs > 0 && CustomCameraManager.getTransitionStartTransform() != null) {
            long elapsed = System.currentTimeMillis() - CustomCameraManager.getTransitionStartTimeMs();
            double t = Mth.clamp((double) elapsed / durationMs, 0.0, 1.0);
            double u = CustomCameraManager.getTransitionEasing().ease(t);

            CameraTransform start = CustomCameraManager.getTransitionStartTransform();
            Vec3 startPos = start.getPosition();
            pos = new Vec3(
                    Mth.lerp(u, startPos.x, pos.x),
                    Mth.lerp(u, startPos.y, pos.y),
                    Mth.lerp(u, startPos.z, pos.z)
            );
            yaw = Mth.rotLerp((float) u, start.getYaw(), yaw);
            pitch = Mth.rotLerp((float) u, start.getPitch(), pitch);
            roll = Mth.rotLerp((float) u, start.getRoll(), roll);
            rot = CameraMath.eulerToQuaternion(yaw, pitch, roll);

            double startFov = start.getFov();
            double targetFov = transform != null ? transform.getFov() : CustomCameraManager.getCustomFov();
            double curFov = Mth.lerp(u, startFov, targetFov);
            CustomCameraManager.setCustomFov(curFov);
        }

        event.setPitch(pitch);
        event.setYaw(yaw);
        event.setRoll(roll);

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        boolean tooClose = mc.player != null && isPlayerTooCloseToCamera(mc.player, pos, partialTick);
        lastPlayerTooClose = tooClose;
        boolean shouldRender = CustomCameraManager.shouldRenderPlayerModel() && !tooClose;

        // 1. Set angles via Forge's public Camera method
        camera.setAnglesInternal(yaw, pitch);

        // 2. Set orientation quaternion and direction vectors via Camera public methods
        if (rot != null) {
            camera.rotation().set(rot);
            camera.getLookVector().set(0.0F, 0.0F, 1.0F).rotate(rot);
            camera.getUpVector().set(0.0F, 1.0F, 0.0F).rotate(rot);
            camera.getLeftVector().set(1.0F, 0.0F, 0.0F).rotate(rot);
        }

        // 3. Set camera position and detached state
        initReflection();
        try {
            if (positionField != null) {
                positionField.set(camera, pos);
            }
            if (blockPositionField != null) {
                Object bp = blockPositionField.get(camera);
                if (bp instanceof BlockPos.MutableBlockPos mbp) {
                    mbp.set(pos.x, pos.y, pos.z);
                }
            }
            if (positionField == null) {
                if (setPositionMethodDouble != null) {
                    setPositionMethodDouble.invoke(camera, pos.x, pos.y, pos.z);
                } else if (setPositionMethodVec3 != null) {
                    setPositionMethodVec3.invoke(camera, pos);
                }
            }
            if (setRotationMethod != null) {
                setRotationMethod.invoke(camera, yaw, pitch);
            }
            if (detachedField != null) {
                detachedField.setBoolean(camera, shouldRender);
            }
        } catch (Exception e) {
            FracturedUtils.LOGGER.error("[CameraUtils] Failed to apply camera transform", e);
        }
    }

    public static final double PLAYER_TOO_CLOSE_DISTANCE = 0.35;
    private static boolean lastPlayerTooClose = false;

    public static boolean isPlayerTooCloseToCamera(Entity player, Vec3 cameraPos, float partialTick) {
        if (player == null || cameraPos == null) return false;
        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double py = Mth.lerp(partialTick, player.yo, player.getY());
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());

        // Closest point on player vertical cylinder between feet and top of head
        double clampedY = Mth.clamp(cameraPos.y, py, py + player.getBbHeight());
        Vec3 closestPointOnPlayer = new Vec3(px, clampedY, pz);

        double distSq = cameraPos.distanceToSqr(closestPointOnPlayer);
        return distSq < (PLAYER_TOO_CLOSE_DISTANCE * PLAYER_TOO_CLOSE_DISTANCE);
    }

    public static boolean isPlayerTooClose(Entity player, float partialTick) {
        if (lastPlayerTooClose) return true;
        CameraTransform transform = CustomCameraManager.getCurrentTransform();
        if (transform != null && player != null) {
            return isPlayerTooCloseToCamera(player, transform.getPosition(), partialTick);
        }
        return false;
    }

    public static boolean isPlayerTooClose() {
        return lastPlayerTooClose;
    }

    public static void resetProximity() {
        lastPlayerTooClose = false;
    }

    public static void setOverheadCamera(Entity target, double heightOffset, float pitch) {
        if (target == null) return;
        CustomCameraManager.setTargetEntity(target, heightOffset, pitch);
    }

    public static void setModernThirdPersonCamera(Entity target) {
        if (target == null) return;
        CustomCameraManager.setOverTheShoulderTarget(target, 2.2, 0.45, 0.1, 15.0f);
    }
}
