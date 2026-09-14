package net.dandare21.fracturedutils.screeneffect;

import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.screeneffect.packet.S2CPlayScreenEffectPacket;
import net.dandare21.fracturedutils.screeneffect.packet.S2CStopScreenEffectPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * Server-side API for playing and stopping screen effects on player screens.
 * Other mods can utilize this class to trigger effects for abilities, cutscenes, boss mechanics, etc.
 */
public class ScreenEffectManager {

    /**
     * Plays a screen effect on a single player's screen.
     */
    public static void playEffect(ServerPlayer player, ScreenEffectInstance effect) {
        if (player == null || effect == null) return;
        ModMessages.sendToPlayer(new S2CPlayScreenEffectPacket(effect), player);
    }

    /**
     * Plays a screen effect on multiple players' screens.
     */
    public static void playEffect(Collection<ServerPlayer> players, ScreenEffectInstance effect) {
        if (players == null || players.isEmpty() || effect == null) return;
        S2CPlayScreenEffectPacket packet = new S2CPlayScreenEffectPacket(effect);
        for (ServerPlayer player : players) {
            if (player != null) {
                ModMessages.sendToPlayer(packet, player);
            }
        }
    }

    /**
     * Broadcasts a screen effect to all players on the server.
     */
    public static void playEffect(MinecraftServer server, ScreenEffectInstance effect) {
        if (server == null || effect == null) return;
        playEffect(server.getPlayerList().getPlayers(), effect);
    }

    /**
     * Plays a screen effect on all players within a given sphere radius of a position in a level.
     */
    public static void playEffectNearby(ServerLevel level, Vec3 center, double radius, ScreenEffectInstance effect) {
        if (level == null || center == null || effect == null || radius <= 0) return;
        double rSq = radius * radius;
        S2CPlayScreenEffectPacket packet = new S2CPlayScreenEffectPacket(effect);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= rSq) {
                ModMessages.sendToPlayer(packet, player);
            }
        }
    }

    /**
     * Stops a specific screen effect type on a player's screen.
     */
    public static void stopEffect(ServerPlayer player, ResourceLocation typeId) {
        if (player == null || typeId == null) return;
        ModMessages.sendToPlayer(new S2CStopScreenEffectPacket(typeId), player);
    }

    /**
     * Stops all active screen effects on a player's screen.
     */
    public static void stopAllEffects(ServerPlayer player) {
        if (player == null) return;
        ModMessages.sendToPlayer(new S2CStopScreenEffectPacket(), player);
    }

    /**
     * Stops all active screen effects on all players across the server.
     */
    public static void stopAllEffects(MinecraftServer server) {
        if (server == null) return;
        S2CStopScreenEffectPacket packet = new S2CStopScreenEffectPacket();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ModMessages.sendToPlayer(packet, player);
        }
    }
}
