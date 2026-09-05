package net.dandare21.fracturedutils.util;

import com.mojang.brigadier.StringReader;
import net.dandare21.fracturedutils.FracturedUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.dandare21.fracturedutils.puppet.IPuppetEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SelectorUtils {

    /**
     * Resolves a target selector string (e.g. "@a", "@a[team=TEAM1]", "@a[tag=dungeon]")
     * into a list of matching online ServerPlayers.
     */
    public static List<ServerPlayer> getTargetPlayers(MinecraftServer server, String selectorStr) {
        if (server == null) return Collections.emptyList();
        
        List<ServerPlayer> allPlayers = new ArrayList<>(server.getPlayerList().getPlayers());
        if (selectorStr == null || selectorStr.isBlank() || selectorStr.trim().equalsIgnoreCase("@a")) {
            return allPlayers;
        }

        try {
            CommandSourceStack source = server.createCommandSourceStack();
            StringReader reader = new StringReader(selectorStr.trim());
            EntitySelectorParser parser = new EntitySelectorParser(reader);
            EntitySelector selector = parser.parse();
            return selector.findPlayers(source);
        } catch (Exception e) {
            FracturedUtils.LOGGER.warn("[SelectorUtils] Failed to parse target selector '{}', falling back to all players: {}", selectorStr, e.getMessage());
            return allPlayers;
        }
    }

    /**
     * Checks if a specific player matches the target selector string.
     */
    public static boolean isPlayerMatching(MinecraftServer server, ServerPlayer player, String selectorStr) {
        if (server == null || player == null) return false;
        if (selectorStr == null || selectorStr.isBlank() || selectorStr.trim().equalsIgnoreCase("@a")) {
            return true;
        }
        List<ServerPlayer> matching = getTargetPlayers(server, selectorStr);
        return matching.contains(player);
    }

    /**
     * Resolves a target selector string (e.g. "@e[tag=puppet]", "@e[type=mymod:boss]")
     * into a list of matching Entities.
     */
    public static List<Entity> getTargetEntities(MinecraftServer server, String selectorStr) {
        if (server == null) return Collections.emptyList();
        if (selectorStr == null || selectorStr.isBlank()) {
            List<Entity> all = new ArrayList<>();
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    all.add(entity);
                }
            }
            return all;
        }

        try {
            CommandSourceStack source = server.createCommandSourceStack();
            StringReader reader = new StringReader(selectorStr.trim());
            EntitySelectorParser parser = new EntitySelectorParser(reader);
            EntitySelector selector = parser.parse();
            List<Entity> list = new ArrayList<>();
            for (Entity entity : selector.findEntities(source)) {
                list.add(entity);
            }
            return list;
        } catch (Exception e) {
            FracturedUtils.LOGGER.warn("[SelectorUtils] Failed to parse target selector '{}': {}", selectorStr, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Resolves IPuppetEntity instances based on optional UUID string or target selector string.
     * Fallbacks to all active IPuppetEntity instances if neither UUID nor target selector is provided.
     */
    public static List<IPuppetEntity> getPuppetEntities(MinecraftServer server, String entityUuid, String targetSelector) {
        List<IPuppetEntity> puppets = new ArrayList<>();
        if (server == null) return puppets;

        if (entityUuid != null && !entityUuid.isBlank()) {
            try {
                UUID uuid = UUID.fromString(entityUuid.trim());
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = level.getEntity(uuid);
                    if (entity instanceof IPuppetEntity puppet) {
                        puppets.add(puppet);
                    }
                }
                if (!puppets.isEmpty()) {
                    return puppets;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (targetSelector != null && !targetSelector.isBlank()) {
            List<Entity> entities = getTargetEntities(server, targetSelector);
            for (Entity entity : entities) {
                if (entity instanceof IPuppetEntity puppet) {
                    puppets.add(puppet);
                }
            }
            return puppets;
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof IPuppetEntity puppet) {
                    puppets.add(puppet);
                }
            }
        }
        return puppets;
    }
}
