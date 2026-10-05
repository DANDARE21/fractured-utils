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
import java.util.ArrayList;
import java.util.List;

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
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 3 && params[0] == double.class && params[1] == double.class && params[2] == double.class) {
                    setPositionMethodDouble = m;
                } else if (params.length == 1 && params[0] == Vec3.class) {
                    setPositionMethodVec3 = m;
                } else if (params.length == 2 && params[0] == float.class && params[1] == float.class) {
                    setRotationMethod = m;
                }
            }

            // Find fields on Camera.class
            List<Field> vec3fFields = new ArrayList<>();
            List<Field> floatFields = new ArrayList<>();
            for (Field f : Camera.class.getDeclaredFields()) {
                f.setAccessible(true);
                Class<?> type = f.getType();
                if (type == Vec3.class) {
                    positionField = f;
                } else if (BlockPos.class.isAssignableFrom(type)) {
                    blockPositionField = f;
                } else if (type == boolean.class) {
                    if (f.getName().equals("detached") || f.getName().equals("m_90581_") || detachedField == null) {
                        detachedField = f;
                    }
                } else if (type == Quaternionf.class) {
                    rotationField = f;
                } else if (type == Vector3f.class) {
                    vec3fFields.add(f);
                    if (f.getName().equals("forwards") || f.getName().equals("m_90587_")) {
                        forwardsField = f;
                    } else if (f.getName().equals("up") || f.getName().equals("m_90588_")) {
                        upField = f;
                    } else if (f.getName().equals("left") || f.getName().equals("m_90589_")) {
                        leftField = f;
                    }
                } else if (type == float.class) {
                    floatFields.add(f);
                }
            }

            if (vec3fFields.size() >= 3) {
                if (forwardsField == null) forwardsField = vec3fFields.get(0);
                if (upField == null) upField = vec3fFields.get(1);
                if (leftField == null) leftField = vec3fFields.get(2);
            }
            if (floatFields.size() >= 2) {
                xRotField = floatFields.get(0);
                yRotField = floatFields.get(1);
            }

            FracturedUtils.LOGGER.info("[CameraUtils] Initialized reflection: positionField={}, blockPosField={}, setPosD={}, setPosV={}, setRot={}, rotField={}, detachedField={}",
                    positionField != null, blockPositionField != null,
                    setPositionMethodDouble != null, setPositionMethodVec3 != null,
                    setRotationMethod != null, rotationField != null, detachedField != null);
        } catch (Exception e) {
            FracturedUtils.LOGGER.error("[CameraUtils] Failed to initialize reflection", e);
        }
    }

    public static void applyCameraOverride(Camera camera, ViewportEvent.ComputeCameraAngles event) {
        if (camera == null || !CustomCameraManager.isActive()) return;
        initReflection();

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
        } else {
            pos = transform.getPosition();
            yaw = transform.getYaw();
            pitch = transform.getPitch();
            roll = transform.getRoll();
            rot = transform.getRotation();
        }

        event.setPitch(pitch);
        event.setYaw(yaw);
        event.setRoll(roll);

        try {
            // 1. Direct field assignment for position
            if (positionField != null) {
                positionField.set(camera, pos);
            }
            if (blockPositionField != null) {
                Object bp = blockPositionField.get(camera);
                if (bp instanceof BlockPos.MutableBlockPos mbp) {
                    mbp.set(pos.x, pos.y, pos.z);
                }
            }

            // 2. Invoke setPosition methods if available
            if (setPositionMethodDouble != null) {
                setPositionMethodDouble.invoke(camera, pos.x, pos.y, pos.z);
            } else if (setPositionMethodVec3 != null) {
                setPositionMethodVec3.invoke(camera, pos);
            }

            // 3. Rotation methods & fields
            if (setRotationMethod != null) {
                setRotationMethod.invoke(camera, yaw, pitch);
            }
            if (xRotField != null) {
                xRotField.setFloat(camera, pitch);
            }
            if (yRotField != null) {
                yRotField.setFloat(camera, yaw);
            }
            if (rotationField != null && rot != null) {
                Quaternionf camRot = (Quaternionf) rotationField.get(camera);
                if (camRot != null) {
                    camRot.set(rot);
                }
                if (forwardsField != null) {
                    Vector3f f = (Vector3f) forwardsField.get(camera);
                    if (f != null) {
                        FORWARDS.rotate(rot, f);
                    }
                }
                if (upField != null) {
                    Vector3f u = (Vector3f) upField.get(camera);
                    if (u != null) {
                        UP.rotate(rot, u);
                    }
                }
                if (leftField != null) {
                    Vector3f l = (Vector3f) leftField.get(camera);
                    if (l != null) {
                        LEFT.rotate(rot, l);
                    }
                }
            }

            // 4. Detached field for player model rendering (hide player if too close to camera to avoid clipping inside)
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            boolean tooClose = mc.player != null && isPlayerTooCloseToCamera(mc.player, pos, partialTick);
            lastPlayerTooClose = tooClose;
            if (detachedField != null) {
                boolean shouldRender = CustomCameraManager.shouldRenderPlayerModel() && !tooClose;
                detachedField.setBoolean(camera, shouldRender);
            }
        } catch (Exception e) {
            FracturedUtils.LOGGER.error("[CameraUtils] Failed to apply camera transform", e);
        }
    }

    public static final double PLAYER_TOO_CLOSE_DISTANCE = 0.85;
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
