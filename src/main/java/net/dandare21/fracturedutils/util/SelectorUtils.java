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
import net.minecraft.world.entity.Mob;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
        List<Entity> result = new ArrayList<>();
        if (server == null || selectorStr == null || selectorStr.isBlank()) return result;

        String trimmed = selectorStr.trim();

        // 1. Direct UUID match across all levels
        try {
            UUID uuid = UUID.fromString(trimmed);
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) {
                    result.add(entity);
                    return result;
                }
                for (Entity e : level.getAllEntities()) {
                    if (e.getUUID().equals(uuid)) {
                        result.add(e);
                        return result;
                    }
                }
            }
            if (!result.isEmpty()) return result;
        } catch (IllegalArgumentException ignored) {}

        // 2. Official Vanilla EntitySelectorParser for @ selectors
        if (trimmed.startsWith("@")) {
            String normalizedSelector = normalizeSelector(trimmed);
            for (ServerLevel level : server.getAllLevels()) {
                try {
                    CommandSourceStack source = server.createCommandSourceStack().withLevel(level);
                    StringReader reader = new StringReader(normalizedSelector);
                    EntitySelectorParser parser = new EntitySelectorParser(reader, true);
                    EntitySelector selector = parser.parse();
                    List<? extends Entity> found = selector.findEntities(source);
                    if (!found.isEmpty()) {
                        result.addAll(found);
                        if (selector.getMaxResults() == 1 || normalizedSelector.contains("limit=1")) {
                            return Collections.singletonList(result.get(0));
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (!result.isEmpty()) {
                return result;
            }
        }

        // 3. Custom matching for bare types, tags, or shorthand selectors
        String typeFilter = null;
        String tagFilter = null;
        String nameFilter = null;
        Integer limitFilter = null;
        boolean isSelector = trimmed.startsWith("@e[") && trimmed.endsWith("]");

        if (isSelector) {
            String inside = trimmed.substring(3, trimmed.length() - 1);
            String[] parts = inside.split(",");
            for (String part : parts) {
                String p = part.trim();
                if (p.startsWith("type=")) {
                    typeFilter = p.substring(5).trim();
                } else if (p.startsWith("tag=")) {
                    tagFilter = p.substring(4).trim();
                } else if (p.startsWith("name=")) {
                    nameFilter = p.substring(5).trim();
                    if (nameFilter.startsWith("\"") && nameFilter.endsWith("\"") && nameFilter.length() >= 2) {
                        nameFilter = nameFilter.substring(1, nameFilter.length() - 1);
                    }
                } else if (p.startsWith("limit=") || p.startsWith("c=")) {
                    try {
                        limitFilter = Integer.parseInt(p.substring(p.indexOf('=') + 1).trim());
                    } catch (Exception ignored) {}
                }
            }
        } else if (!trimmed.startsWith("@")) {
            // Bare string: could be type OR tag OR name
            typeFilter = trimmed;
            tagFilter = trimmed;
            nameFilter = trimmed;
        } else {
            // Malformed @ selector (e.g. unclosed bracket) - do NOT match all entities!
            return result;
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                boolean matches = true;

                if (typeFilter != null) {
                    ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                    String keyStr = key != null ? key.toString().toLowerCase(Locale.ROOT) : "";
                    String keyPath = key != null ? key.getPath().toLowerCase(Locale.ROOT) : "";
                    String cleanFilter = typeFilter.toLowerCase(Locale.ROOT);

                    boolean typeMatch = keyStr.equals(cleanFilter) || keyPath.equals(cleanFilter)
                            || (!cleanFilter.contains(":") && (keyPath.equals(cleanFilter) || keyStr.equals(FracturedUtils.MOD_ID + ":" + cleanFilter) || keyStr.equals("minecraft:" + cleanFilter)));

                    if (!isSelector) {
                        boolean tagMatch = entity.getTags().contains(trimmed);
                        boolean nameMatch = entity.getName().getString().equalsIgnoreCase(trimmed);
                        if (!typeMatch && !tagMatch && !nameMatch) {
                            matches = false;
                        }
                    } else {
                        if (!typeMatch) {
                            matches = false;
                        }
                    }
                }

                if (isSelector && tagFilter != null) {
                    if (!entity.getTags().contains(tagFilter)) {
                        matches = false;
                    }
                }

                if (isSelector && nameFilter != null) {
                    if (!entity.getName().getString().equalsIgnoreCase(nameFilter)) {
                        matches = false;
                    }
                }

                if (matches) {
                    result.add(entity);
                    if (limitFilter != null && result.size() >= limitFilter) {
                        return result;
                    }
                }
            }
        }

        return result;
    }

    private static String normalizeSelector(String selector) {
        if (selector.contains("type=void_herald")) {
            return selector.replace("type=void_herald", "type=" + FracturedUtils.MOD_ID + ":void_herald");
        }
        if (selector.contains("type=!void_herald")) {
            return selector.replace("type=!void_herald", "type=!" + FracturedUtils.MOD_ID + ":void_herald");
        }
        return selector;
    }

    /**
     * Resolves IPuppetEntity instances based on optional UUID string or target selector string.
     */
    public static List<IPuppetEntity> getPuppetEntities(MinecraftServer server, String entityUuid, String targetSelector) {
        List<IPuppetEntity> puppets = new ArrayList<>();
        if (server == null) return puppets;

        boolean singleEntityExpected = (entityUuid != null && !entityUuid.isBlank())
                || (targetSelector != null && (targetSelector.contains("limit=1") || targetSelector.contains("c=1")));

        if (entityUuid != null && !entityUuid.isBlank()) {
            try {
                UUID uuid = UUID.fromString(entityUuid.trim());
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = level.getEntity(uuid);
                    if (entity instanceof IPuppetEntity puppet) {
                        puppets.add(puppet);
                        return puppets;
                    }
                    for (Entity e : level.getAllEntities()) {
                        if (e.getUUID().equals(uuid) && e instanceof IPuppetEntity puppet) {
                            puppets.add(puppet);
                            return puppets;
                        }
                    }
                }
            } catch (IllegalArgumentException ignored) {}
        }

        if (targetSelector != null && !targetSelector.isBlank()) {
            List<Entity> entities = getTargetEntities(server, targetSelector);
            for (Entity entity : entities) {
                if (entity instanceof IPuppetEntity puppet) {
                    puppets.add(puppet);
                    if (singleEntityExpected) {
                        return puppets;
                    }
                }
            }
            return puppets;
        }

        return puppets;
    }

    /**
     * Resolves IPuppetHandler capabilities attached to Mobs based on optional UUID string or target selector string.
     * When a specific entity was targeted via UUID or limit=1, ensures ONLY that single entity is controlled.
     */
    public static List<net.dandare21.fracturedutils.puppet.capability.IPuppetHandler> getPuppetHandlers(MinecraftServer server, String entityUuid, String targetSelector) {
        List<net.dandare21.fracturedutils.puppet.capability.IPuppetHandler> handlers = new ArrayList<>();
        if (server == null) return handlers;

        boolean singleEntityExpected = (entityUuid != null && !entityUuid.isBlank())
                || (targetSelector != null && (targetSelector.contains("limit=1") || targetSelector.contains("c=1")));

        // 1. Match by UUID if provided
        if (entityUuid != null && !entityUuid.isBlank()) {
            try {
                UUID uuid = UUID.fromString(entityUuid.trim());
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = level.getEntity(uuid);
                    if (entity instanceof Mob mob) {
                        mob.getCapability(net.dandare21.fracturedutils.puppet.capability.PuppetCapabilityProvider.PUPPET_HANDLER)
                                .ifPresent(handlers::add);
                        if (!handlers.isEmpty()) break;
                    }
                    if (handlers.isEmpty()) {
                        for (Entity e : level.getAllEntities()) {
                            if (e.getUUID().equals(uuid) && e instanceof Mob mob) {
                                mob.getCapability(net.dandare21.fracturedutils.puppet.capability.PuppetCapabilityProvider.PUPPET_HANDLER)
                                        .ifPresent(handlers::add);
                                if (!handlers.isEmpty()) break;
                            }
                        }
                    }
                    if (!handlers.isEmpty()) break;
                }
                if (!handlers.isEmpty()) {
                    FracturedUtils.LOGGER.info("[SelectorUtils] Resolved specific selected puppet handler by UUID '{}'", entityUuid);
                    return handlers;
                }
            } catch (IllegalArgumentException ignored) {}
        }

        // 2. Match by targetSelector if provided
        if (targetSelector != null && !targetSelector.isBlank()) {
            List<Entity> entities = getTargetEntities(server, targetSelector);
            for (Entity entity : entities) {
                if (entity instanceof Mob mob) {
                    mob.getCapability(net.dandare21.fracturedutils.puppet.capability.PuppetCapabilityProvider.PUPPET_HANDLER)
                            .ifPresent(h -> {
                                if (!handlers.contains(h)) {
                                    handlers.add(h);
                                }
                            });
                    // If a single selected entity was expected or selector limits to 1, stop immediately
                    if (singleEntityExpected && !handlers.isEmpty()) {
                        FracturedUtils.LOGGER.info("[SelectorUtils] Target resolved single selected entity: {}", entity);
                        return handlers;
                    }
                }
            }
            if (!handlers.isEmpty()) {
                FracturedUtils.LOGGER.info("[SelectorUtils] Resolved {} puppet handler(s) by selector '{}'", handlers.size(), targetSelector);
                return handlers;
            }
        }

        if (entityUuid != null && !entityUuid.isBlank()) {
            FracturedUtils.LOGGER.warn("[SelectorUtils] Selected entity with UUID '{}' could not be found; skipping to avoid controlling unintended entities.", entityUuid);
        }

        return handlers;
    }

}
