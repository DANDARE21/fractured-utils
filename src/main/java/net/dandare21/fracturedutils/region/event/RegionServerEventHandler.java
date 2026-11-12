package net.dandare21.fracturedutils.region.event;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.region.RegionManager;
import net.dandare21.fracturedutils.region.data.Polygon2D;
import net.dandare21.fracturedutils.region.data.Region;
import net.dandare21.fracturedutils.region.data.Vec2d;
import net.dandare21.fracturedutils.region.data.WorldRegionSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side enforcement event listener for region locks:
 * - Movement pushback & mount dismounts
 * - Teleport interception (Ender Pearl, Chorus Fruit, /tp)
 * - Block break/place & interaction protection
 * - Player join synchronization
 */
@Mod.EventBusSubscriber(modid = FracturedUtils.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RegionServerEventHandler {

    private static final Map<UUID, Long> LAST_DENIAL_MSG_TIME = new ConcurrentHashMap<>();
    private static final long DENIAL_MSG_COOLDOWN_MS = 1500;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }

        if (event.player instanceof ServerPlayer player) {
            if (player.isSpectator() || player.hasPermissions(2)) {
                return;
            }

            if (player.getServer() == null) return;
            WorldRegionSavedData data = WorldRegionSavedData.get(player.getServer());
            Region locked = data.findLockedZoneAt(player.getX(), player.getZ());

            if (locked != null && locked.getPolygon() != null) {
                // 1. Dismount vehicle/mount if riding
                if (player.isPassenger()) {
                    Entity vehicle = player.getVehicle();
                    player.stopRiding();
                    if (vehicle != null) {
                        Polygon2D.ClosestEdgeResult vEdge = locked.getPolygon().findNearestEdge(vehicle.getX(), vehicle.getZ());
                        Vec2d vNormal = vEdge.outwardNormal();
                        Vec2d vTarget = vEdge.closestPoint().add(vNormal.scale(1.5));
                        vehicle.teleportTo(vTarget.x(), vehicle.getY(), vTarget.z());
                        vehicle.setDeltaMovement(vNormal.x() * 0.4, 0.1, vNormal.z() * 0.4);
                        vehicle.hurtMarked = true;
                    }
                }

                // 2. Nearest-edge calculation and outward pushback
                Polygon2D.ClosestEdgeResult edge = locked.getPolygon().findNearestEdge(player.getX(), player.getZ());
                Vec2d normal = edge.outwardNormal();
                Vec2d targetPos = edge.closestPoint().add(normal.scale(1.2));

                player.teleportTo(targetPos.x(), player.getY(), targetPos.z());
                player.setDeltaMovement(normal.x() * 0.45, 0.1, normal.z() * 0.45);
                player.hurtMarked = true;

                // 3. Display lore denial message in action bar (rate-limited)
                long now = System.currentTimeMillis();
                long lastMsg = LAST_DENIAL_MSG_TIME.getOrDefault(player.getUUID(), 0L);
                if (now - lastMsg > DENIAL_MSG_COOLDOWN_MS) {
                    LAST_DENIAL_MSG_TIME.put(player.getUUID(), now);
                    String msg = locked.getDeniedMessage();
                    if (msg == null || msg.isBlank()) {
                        msg = "A magical barrier prevents you from entering " + locked.getName();
                    }
                    player.displayClientMessage(Component.literal("§c[Locked] " + msg), true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityTeleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide() || entity.getServer() == null) {
            return;
        }

        // Allow ops to teleport freely
        if (entity instanceof ServerPlayer player && player.hasPermissions(2)) {
            return;
        }

        WorldRegionSavedData data = WorldRegionSavedData.get(entity.getServer());
        Region locked = data.findLockedZoneAt(event.getTargetX(), event.getTargetZ());
        if (locked != null) {
            event.setCanceled(true);
            if (entity instanceof ServerPlayer player) {
                String msg = locked.getDeniedMessage();
                if (msg == null || msg.isBlank()) {
                    msg = "A magical barrier prevents you from teleporting into " + locked.getName();
                }
                player.displayClientMessage(Component.literal("§c[Locked] " + msg), true);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            if (player.hasPermissions(2) || player.getServer() == null) return;
            WorldRegionSavedData data = WorldRegionSavedData.get(player.getServer());
            Region locked = data.findLockedZoneAt(event.getPos().getX(), event.getPos().getZ());
            if (locked != null) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal("§c[Locked] You cannot break blocks inside " + locked.getName()), true);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.hasPermissions(2) || player.getServer() == null) return;
            WorldRegionSavedData data = WorldRegionSavedData.get(player.getServer());
            Region locked = data.findLockedZoneAt(event.getPos().getX(), event.getPos().getZ());
            if (locked != null) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal("§c[Locked] You cannot place blocks inside " + locked.getName()), true);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.hasPermissions(2) || player.getServer() == null) return;
            WorldRegionSavedData data = WorldRegionSavedData.get(player.getServer());
            Region locked = data.findLockedZoneAt(event.getPos().getX(), event.getPos().getZ());
            if (locked != null) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal("§c[Locked] You cannot interact with objects inside " + locked.getName()), true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RegionManager.getInstance().onPlayerJoin(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_DENIAL_MSG_TIME.remove(event.getEntity().getUUID());
    }
}

