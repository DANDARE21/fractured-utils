package net.dandare21.fracturedutils.region.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.dandare21.fracturedutils.region.RegionManager;
import net.dandare21.fracturedutils.region.data.Region;
import net.dandare21.fracturedutils.region.data.RegionType;
import net.dandare21.fracturedutils.region.data.Vec2d;
import net.dandare21.fracturedutils.region.data.WorldRegionSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class RegionCommands {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_REGION_IDS = (ctx, builder) -> {
        WorldRegionSavedData data = WorldRegionSavedData.get(ctx.getSource().getServer());
        return SharedSuggestionProvider.suggest(data.getRegions().keySet(), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_FLAGS = (ctx, builder) -> {
        WorldRegionSavedData data = WorldRegionSavedData.get(ctx.getSource().getServer());
        return SharedSuggestionProvider.suggest(data.getActiveFlags(), builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> regionCmd = Commands.literal("region")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("lock")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(SUGGEST_REGION_IDS)
                                .executes(ctx -> setLock(ctx, StringArgumentType.getString(ctx, "id"), true))
                        )
                )
                .then(Commands.literal("unlock")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(SUGGEST_REGION_IDS)
                                .executes(ctx -> setLock(ctx, StringArgumentType.getString(ctx, "id"), false))
                        )
                )
                .then(Commands.literal("flag")
                        .then(Commands.literal("set")
                                .then(Commands.argument("flag", StringArgumentType.word())
                                        .suggests(SUGGEST_FLAGS)
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setFlag(ctx, StringArgumentType.getString(ctx, "flag"), BoolArgumentType.getBool(ctx, "value")))
                                        )
                                )
                        )
                        .then(Commands.literal("list")
                                .executes(RegionCommands::listFlags)
                        )
                        .then(Commands.literal("clear")
                                .executes(RegionCommands::clearFlags)
                        )
                )
                .then(Commands.literal("list")
                        .executes(RegionCommands::listRegions)
                )
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(SUGGEST_REGION_IDS)
                                .executes(ctx -> showInfo(ctx, StringArgumentType.getString(ctx, "id")))
                        )
                )
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(SUGGEST_REGION_IDS)
                                .executes(ctx -> deleteRegion(ctx, StringArgumentType.getString(ctx, "id")))
                        )
                )
                .then(Commands.literal("tp")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(SUGGEST_REGION_IDS)
                                .executes(ctx -> teleportToRegion(ctx, StringArgumentType.getString(ctx, "id")))
                        )
                );

        dispatcher.register(regionCmd);
    }

    private static int setLock(CommandContext<CommandSourceStack> ctx, String id, boolean locked) {
        CommandSourceStack src = ctx.getSource();
        WorldRegionSavedData data = WorldRegionSavedData.get(src.getServer());
        Region r = data.getRegion(id);
        if (r == null) {
            src.sendFailure(Component.literal("Region not found: " + id));
            return 0;
        }

        RegionManager.getInstance().setRegionLocked(id, locked);
        if (locked) {
            src.sendSuccess(() -> Component.literal("§c[Region] Locked region: §f" + r.getName() + " §7(" + id + ")"), true);
        } else {
            src.sendSuccess(() -> Component.literal("§a[Region] Unlocked region: §f" + r.getName() + " §7(" + id + ")"), true);
        }
        return 1;
    }

    private static int setFlag(CommandContext<CommandSourceStack> ctx, String flag, boolean value) {
        CommandSourceStack src = ctx.getSource();
        RegionManager.getInstance().setFlag(flag, value);
        if (value) {
            src.sendSuccess(() -> Component.literal("§a[Region] Flag §e" + flag + " §aset to §2TRUE"), true);
        } else {
            src.sendSuccess(() -> Component.literal("§e[Region] Flag §e" + flag + " §cset to §4FALSE"), true);
        }
        return 1;
    }

    private static int listFlags(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        WorldRegionSavedData data = WorldRegionSavedData.get(src.getServer());
        Set<String> flags = data.getActiveFlags();
        if (flags.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7No active flags."), false);
            return 1;
        }

        src.sendSuccess(() -> Component.literal("§6=== Active Global Flags (" + flags.size() + ") ==="), false);
        for (String f : flags) {
            src.sendSuccess(() -> Component.literal(" §e• §f" + f), false);
        }
        return 1;
    }

    private static int clearFlags(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        WorldRegionSavedData data = WorldRegionSavedData.get(src.getServer());
        data.clearFlags();
        RegionManager.getInstance().syncToAll();
        src.sendSuccess(() -> Component.literal("§c[Region] All flags cleared."), true);
        return 1;
    }

    private static int listRegions(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        WorldRegionSavedData data = WorldRegionSavedData.get(src.getServer());
        Collection<Region> regions = data.getAllRegions();
        if (regions.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7No regions configured. Create them in Xaero's World Map!"), false);
            return 1;
        }

        src.sendSuccess(() -> Component.literal("§6=== Spatial Regions (" + regions.size() + ") ==="), false);
        for (RegionType type : RegionType.values()) {
            List<Region> ofType = regions.stream().filter(r -> r.getType() == type).toList();
            if (!ofType.isEmpty()) {
                src.sendSuccess(() -> Component.literal("§e[" + type.getDisplayName() + "] (" + ofType.size() + "):"), false);
                for (Region r : ofType) {
                    boolean locked = data.isRegionLocked(r);
                    String lockText = locked ? "§c[LOCKED]" : "§a[OPEN]";
                    String flagText = (r.getRequiredFlag() != null && !r.getRequiredFlag().isEmpty()) ? " §7(Flag: §e" + r.getRequiredFlag() + "§7)" : "";
                    src.sendSuccess(() -> Component.literal("  §f" + r.getName() + " §8(§7" + r.getId() + "§8) " + lockText + flagText), false);
                }
            }
        }
        return 1;
    }

    private static int showInfo(CommandContext<CommandSourceStack> ctx, String id) {
        CommandSourceStack src = ctx.getSource();
        WorldRegionSavedData data = WorldRegionSavedData.get(src.getServer());
        Region r = data.getRegion(id);
        if (r == null) {
            src.sendFailure(Component.literal("Region not found: " + id));
            return 0;
        }

        boolean isLocked = data.isRegionLocked(r);
        Vec2d centroid = r.getPolygon() != null ? r.getPolygon().getCentroid() : Vec2d.ZERO;
        int vertexCount = r.getPolygon() != null ? r.getPolygon().vertexCount() : 0;

        src.sendSuccess(() -> Component.literal("§6=== Region Info: §f" + r.getName() + " §6==="), false);
        src.sendSuccess(() -> Component.literal(" §7ID: §f" + r.getId()), false);
        src.sendSuccess(() -> Component.literal(" §7Type: §e" + r.getType().getDisplayName() + " §8(Tier " + r.getType().getTier() + ")"), false);
        src.sendSuccess(() -> Component.literal(" §7Parent: §f" + (r.getParentId().isEmpty() ? "None" : r.getParentId())), false);
        src.sendSuccess(() -> Component.literal(" §7Status: " + (isLocked ? "§cLocked" : "§aUnlocked") + " §8(Direct lock: " + r.isLocked() + ")"), false);
        if (!r.getRequiredFlag().isEmpty()) {
            boolean flagActive = data.isFlagActive(r.getRequiredFlag());
            src.sendSuccess(() -> Component.literal(" §7Required Flag: §e" + r.getRequiredFlag() + " §8(Active: " + (flagActive ? "§aYes" : "§cNo") + "§8)"), false);
        }
        src.sendSuccess(() -> Component.literal(" §7Vertices: §f" + vertexCount + " §7| Centroid: §f(" + (int)centroid.x() + ", " + (int)centroid.z() + ")"), false);
        if (!r.getSubtitle().isEmpty()) {
            src.sendSuccess(() -> Component.literal(" §7Subtitle: §f" + r.getSubtitle()), false);
        }
        if (!r.getDeniedMessage().isEmpty()) {
            src.sendSuccess(() -> Component.literal(" §7Denial Message: §f" + r.getDeniedMessage()), false);
        }
        return 1;
    }

    private static int deleteRegion(CommandContext<CommandSourceStack> ctx, String id) {
        CommandSourceStack src = ctx.getSource();
        boolean deleted = RegionManager.getInstance().deleteRegion(id);
        if (deleted) {
            src.sendSuccess(() -> Component.literal("§a[Region] Deleted region: §f" + id), true);
            return 1;
        } else {
            src.sendFailure(Component.literal("Region not found: " + id));
            return 0;
        }
    }

    private static int teleportToRegion(CommandContext<CommandSourceStack> ctx, String id) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Command must be run by a player."));
            return 0;
        }

        WorldRegionSavedData data = WorldRegionSavedData.get(src.getServer());
        Region r = data.getRegion(id);
        if (r == null || r.getPolygon() == null) {
            src.sendFailure(Component.literal("Region not found: " + id));
            return 0;
        }

        Vec2d centroid = r.getPolygon().getCentroid();
        double targetY = player.getY();
        player.teleportTo(centroid.x(), targetY, centroid.z());
        src.sendSuccess(() -> Component.literal("§a[Region] Teleported to center of §f" + r.getName() + " §7(" + (int)centroid.x() + ", " + (int)centroid.z() + ")"), false);
        return 1;
    }
}

