package net.dandare21.fracturedutils.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class ClientCameraHandler {
    private static long overrideEndTimeMs = 0L;

    public static void handleCameraOverride(boolean active, String mode, double cameraX, double cameraY, double cameraZ,
                                            float cameraYaw, float cameraPitch, float cameraRoll, double cameraFov,
                                            int durationMs, boolean interpolate, int targetEntityId,
                                            double heightOffset, double backDistance, double shoulderOffset) {
        if (!active || "CLEAR".equalsIgnoreCase(mode)) {
            clearCameraOverride();
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if ("FOLLOW".equalsIgnoreCase(mode)) {
            Entity target = null;
            if (targetEntityId >= 0 && mc.level != null) {
                target = mc.level.getEntity(targetEntityId);
            }
            if (target == null) {
                target = mc.player;
            }
            if (target != null) {
                CustomCameraManager.setTargetEntity(target, heightOffset, cameraPitch);
                CustomCameraManager.setCustomFov(cameraFov);
            }
        } else if ("OVER_THE_SHOULDER".equalsIgnoreCase(mode)) {
            Entity target = null;
            if (targetEntityId >= 0 && mc.level != null) {
                target = mc.level.getEntity(targetEntityId);
            }
            if (target == null) {
                target = mc.player;
            }
            if (target != null) {
                CustomCameraManager.setOverTheShoulderTarget(target, backDistance, shoulderOffset, heightOffset, cameraPitch);
                CustomCameraManager.setCustomFov(cameraFov);
            }
        } else {
            // Default: STATIC
            CustomCameraManager.setCustomCamera(new Vec3(cameraX, cameraY, cameraZ), cameraYaw, cameraPitch, cameraRoll, true);
            CustomCameraManager.setCustomFov(cameraFov);
        }

        if (durationMs > 0) {
            overrideEndTimeMs = System.currentTimeMillis() + durationMs;
        } else {
            overrideEndTimeMs = 0L;
        }
    }

    public static void clientTick() {
        if (overrideEndTimeMs > 0L && System.currentTimeMillis() >= overrideEndTimeMs) {
            clearCameraOverride();
        }
    }

    public static void clearCameraOverride() {
        overrideEndTimeMs = 0L;
        CustomCameraManager.clearCustomCamera();
    }
}
