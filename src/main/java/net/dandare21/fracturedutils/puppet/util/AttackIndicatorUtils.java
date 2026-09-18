package net.dandare21.fracturedutils.puppet.util;

import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.ClientboundAttackIndicatorPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side utility for spawning, updating, and removing circular and linear ground attack indicators.
 * Communicates with clients to render textures/misc/attack_indicator_circle.png and textures/misc/attack_indicator_line.png.
 */
public class AttackIndicatorUtils {
    // Curated ARGB color presets
    public static final int COLOR_VOID = 0xD4B026FF;   // Ethereal purple
    public static final int COLOR_RED = 0xD4FF2222;    // Danger crimson
    public static final int COLOR_ORANGE = 0xD4FF8800; // Warning orange
    public static final int COLOR_CYAN = 0xD400E5FF;   // Arcane cyan
    public static final int COLOR_YELLOW = 0xD4FFDD00; // Caution yellow

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(1000);

    public static int nextId() {
        return ID_COUNTER.incrementAndGet();
    }

    // ==========================================
    // CIRCULAR INDICATORS
    // ==========================================

    public static int spawnCircle(ServerLevel level, Vec3 pos, double radius, int durationTicks) {
        int id = nextId();
        spawnCircle(level, id, pos, radius, durationTicks, COLOR_VOID);
        return id;
    }

    public static int spawnCircle(ServerLevel level, Vec3 pos, double radius, int durationTicks, int argbColor) {
        int id = nextId();
        spawnCircle(level, id, pos, radius, durationTicks, argbColor);
        return id;
    }

    public static void spawnCircle(ServerLevel level, int id, Vec3 pos, double radius, int durationTicks, int argbColor) {
        if (level == null || pos == null) return;
        ClientboundAttackIndicatorPacket packet = new ClientboundAttackIndicatorPacket(
                ClientboundAttackIndicatorPacket.IndicatorShape.CIRCLE,
                id, pos.x, pos.y, pos.z, pos.x, pos.y, pos.z,
                (float) radius, durationTicks, argbColor, false
        );
        ModMessages.sendToNear(packet, level, pos.x, pos.y, pos.z, Math.max(96.0, radius + 32.0));
    }

    public static void updateCircle(ServerLevel level, int id, Vec3 pos, double radius) {
        if (level == null || pos == null) return;
        ClientboundAttackIndicatorPacket packet = new ClientboundAttackIndicatorPacket(
                ClientboundAttackIndicatorPacket.IndicatorShape.CIRCLE,
                id, pos.x, pos.y, pos.z, pos.x, pos.y, pos.z,
                (float) radius, 0, 0, false
        );
        ModMessages.sendToNear(packet, level, pos.x, pos.y, pos.z, Math.max(96.0, radius + 32.0));
    }

    public static void removeCircle(ServerLevel level, int id, Vec3 pos) {
        removeIndicator(level, id, pos);
    }

    // ==========================================
    // LINEAR INDICATORS
    // ==========================================

    /**
     * Calculates an end position vector given a starting point, yaw in degrees, and distance.
     */
    public static Vec3 calculateEndPos(Vec3 startPos, float yawDegrees, double length) {
        double rad = Math.toRadians(yawDegrees);
        double dx = -Math.sin(rad) * length;
        double dz = Math.cos(rad) * length;
        return new Vec3(startPos.x + dx, startPos.y, startPos.z + dz);
    }

    public static int spawnLine(ServerLevel level, Vec3 startPos, Vec3 endPos, double width, int durationTicks) {
        return spawnLine(level, startPos, endPos, width, durationTicks, COLOR_VOID);
    }

    public static int spawnLine(ServerLevel level, Vec3 startPos, Vec3 endPos, double width, int durationTicks, int argbColor) {
        int id = nextId();
        spawnLine(level, id, startPos, endPos, width, durationTicks, argbColor);
        return id;
    }

    public static void spawnLine(ServerLevel level, int id, Vec3 startPos, Vec3 endPos, double width, int durationTicks, int argbColor) {
        if (level == null || startPos == null || endPos == null) return;
        ClientboundAttackIndicatorPacket packet = new ClientboundAttackIndicatorPacket(
                ClientboundAttackIndicatorPacket.IndicatorShape.LINE,
                id, startPos.x, startPos.y, startPos.z, endPos.x, endPos.y, endPos.z,
                (float) width, durationTicks, argbColor, false
        );
        double midX = (startPos.x + endPos.x) * 0.5;
        double midY = (startPos.y + endPos.y) * 0.5;
        double midZ = (startPos.z + endPos.z) * 0.5;
        double length = startPos.distanceTo(endPos);
        ModMessages.sendToNear(packet, level, midX, midY, midZ, Math.max(96.0, length + 48.0));
    }

    public static int spawnLine(ServerLevel level, Vec3 startPos, float yawDegrees, double length, double width, int durationTicks) {
        return spawnLine(level, startPos, yawDegrees, length, width, durationTicks, COLOR_VOID);
    }

    public static int spawnLine(ServerLevel level, Vec3 startPos, float yawDegrees, double length, double width, int durationTicks, int argbColor) {
        int id = nextId();
        spawnLine(level, id, startPos, yawDegrees, length, width, durationTicks, argbColor);
        return id;
    }

    public static void spawnLine(ServerLevel level, int id, Vec3 startPos, float yawDegrees, double length, double width, int durationTicks, int argbColor) {
        Vec3 endPos = calculateEndPos(startPos, yawDegrees, length);
        spawnLine(level, id, startPos, endPos, width, durationTicks, argbColor);
    }

    /**
     * Spawns a line indicator directly forward from an Entity using its current position and facing yaw.
     */
    public static int spawnLine(Entity entity, double length, double width, int durationTicks) {
        return spawnLine(entity, length, width, durationTicks, COLOR_VOID);
    }

    /**
     * Spawns a line indicator directly forward from an Entity using its current position and facing yaw.
     */
    public static int spawnLine(Entity entity, double length, double width, int durationTicks, int argbColor) {
        if (entity != null && entity.level() instanceof ServerLevel serverLevel) {
            return spawnLine(serverLevel, entity.position(), entity.getYRot(), length, width, durationTicks, argbColor);
        }
        return -1;
    }

    /**
     * Spawns a line indicator centered at a point, extending backwards and forwards by half the length.
     */
    public static int spawnCenteredLine(ServerLevel level, Vec3 centerPos, float yawDegrees, double length, double width, int durationTicks, int argbColor) {
        double halfLength = length * 0.5;
        double rad = Math.toRadians(yawDegrees);
        double dx = -Math.sin(rad) * halfLength;
        double dz = Math.cos(rad) * halfLength;
        Vec3 startPos = new Vec3(centerPos.x - dx, centerPos.y, centerPos.z - dz);
        Vec3 endPos = new Vec3(centerPos.x + dx, centerPos.y, centerPos.z + dz);
        return spawnLine(level, startPos, endPos, width, durationTicks, argbColor);
    }

    public static void updateLine(ServerLevel level, int id, Vec3 startPos, Vec3 endPos, double width) {
        if (level == null || startPos == null || endPos == null) return;
        ClientboundAttackIndicatorPacket packet = new ClientboundAttackIndicatorPacket(
                ClientboundAttackIndicatorPacket.IndicatorShape.LINE,
                id, startPos.x, startPos.y, startPos.z, endPos.x, endPos.y, endPos.z,
                (float) width, 0, 0, false
        );
        double midX = (startPos.x + endPos.x) * 0.5;
        double midY = (startPos.y + endPos.y) * 0.5;
        double midZ = (startPos.z + endPos.z) * 0.5;
        double length = startPos.distanceTo(endPos);
        ModMessages.sendToNear(packet, level, midX, midY, midZ, Math.max(96.0, length + 48.0));
    }

    public static void updateLine(ServerLevel level, int id, Vec3 startPos, float yawDegrees, double length, double width) {
        Vec3 endPos = calculateEndPos(startPos, yawDegrees, length);
        updateLine(level, id, startPos, endPos, width);
    }

    public static void removeLine(ServerLevel level, int id, Vec3 pos) {
        removeIndicator(level, id, pos);
    }

    // ==========================================
    // GENERAL REMOVAL
    // ==========================================

    public static void removeIndicator(ServerLevel level, int id, Vec3 pos) {
        if (level == null) return;
        ClientboundAttackIndicatorPacket packet = new ClientboundAttackIndicatorPacket(
                ClientboundAttackIndicatorPacket.IndicatorShape.CIRCLE,
                id, pos != null ? pos.x : 0, pos != null ? pos.y : 0, pos != null ? pos.z : 0,
                pos != null ? pos.x : 0, pos != null ? pos.y : 0, pos != null ? pos.z : 0,
                0.0F, 0, 0, true
        );
        if (pos != null) {
            ModMessages.sendToNear(packet, level, pos.x, pos.y, pos.z, 96.0);
        } else {
            ModMessages.sendToDimension(packet, level);
        }
    }
}
