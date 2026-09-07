package net.dandare21.fracturedutils.puppet.util;

import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.ClientboundAttackIndicatorPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side utility for spawning, updating, and removing circular ground attack indicators.
 * Communicates with clients to render textures/misc/attack_indicator_circle.png.
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
                id, pos.x, pos.y, pos.z, (float) radius, durationTicks, argbColor, false
        );
        ModMessages.sendToNear(packet, level, pos.x, pos.y, pos.z, Math.max(96.0, radius + 32.0));
    }

    public static void updateCircle(ServerLevel level, int id, Vec3 pos, double radius) {
        if (level == null || pos == null) return;
        ClientboundAttackIndicatorPacket packet = new ClientboundAttackIndicatorPacket(
                id, pos.x, pos.y, pos.z, (float) radius, 0, 0, false
        );
        ModMessages.sendToNear(packet, level, pos.x, pos.y, pos.z, Math.max(96.0, radius + 32.0));
    }

    public static void removeCircle(ServerLevel level, int id, Vec3 pos) {
        if (level == null) return;
        ClientboundAttackIndicatorPacket packet = new ClientboundAttackIndicatorPacket(
                id, pos != null ? pos.x : 0, pos != null ? pos.y : 0, pos != null ? pos.z : 0,
                0.0F, 0, 0, true
        );
        if (pos != null) {
            ModMessages.sendToNear(packet, level, pos.x, pos.y, pos.z, 96.0);
        } else {
            ModMessages.sendToDimension(packet, level);
        }
    }
}
