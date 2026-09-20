package net.dandare21.fracturedutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.dandare21.fracturedutils.bossbar.BossHealthBar;
import net.dandare21.fracturedutils.bossbar.BossHealthBarManager;
import net.dandare21.fracturedutils.bossbar.feature.PhaseMarkersFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command suite for the modular Boss Healthbars utility.
 * Usage:
 *   /bosshealthbar create <id> <name>
 *   /bosshealthbar remove <id>
 *   /bosshealthbar list
 *   /bosshealthbar show <id>
 *   /bosshealthbar hide <id>
 *   /bosshealthbar link <id> <entities>
 *   /bosshealthbar unlink <id> [entities]
 *   /bosshealthbar get <id> (health|max_health|percent|entities|visible)
 *   /bosshealthbar set <id> name <name>
 *   /bosshealthbar set <id> color <color>
 *   /bosshealthbar set <id> overlay <overlay>
 *   /bosshealthbar set <id> style <style>
 *   /bosshealthbar set <id> texture <resource_location>
 *   /bosshealthbar set <id> dimensions <width> <height>
 *   /bosshealthbar set <id> visible <boolean>
 *   /bosshealthbar set <id> autohide <boolean>
 *   /bosshealthbar set <id> autodelete <boolean>
 *   /bosshealthbar set <id> text_display <none|value|percent|both>
 *   /bosshealthbar set <id> ghost_bar <boolean>
 *   /bosshealthbar set <id> players (all|<targets>)
 *   /bosshealthbar set <id> range <radius>
 *   /bosshealthbar phase <id> (add|remove|clear) [threshold]
 *   /bosshealthbar info <id>
 */
public class BossHealthBarCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("bosshealthbar")
                        .requires(src -> src.hasPermission(2))
                        // 1. Create
                        .then(Commands.literal("create")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("name", ComponentArgument.textComponent())
                                                .executes(ctx -> createBossBar(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        ComponentArgument.getComponent(ctx, "name"))))))
                        // 2. Remove
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(BossHealthBarCommand::suggestBossBarIds)
                                        .executes(ctx -> removeBossBar(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                        // 3. List
                        .then(Commands.literal("list")
                                .executes(ctx -> listBossBars(ctx.getSource())))
                        // Show
                        .then(Commands.literal("show")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(BossHealthBarCommand::suggestBossBarIds)
                                        .executes(ctx -> setVisible(ctx.getSource(), StringArgumentType.getString(ctx, "id"), true))))
                        // Hide
                        .then(Commands.literal("hide")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(BossHealthBarCommand::suggestBossBarIds)
                                        .executes(ctx -> setVisible(ctx.getSource(), StringArgumentType.getString(ctx, "id"), false))))
                        // 4. Link
                        .then(Commands.literal("link")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(BossHealthBarCommand::suggestBossBarIds)
                                        .then(Commands.argument("entities", EntityArgument.entities())
                                                .executes(ctx -> linkEntities(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        EntityArgument.getEntities(ctx, "entities"))))))
                        // 5. Unlink
                        .then(Commands.literal("unlink")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(BossHealthBarCommand::suggestBossBarIds)
                                        .executes(ctx -> unlinkEntities(ctx.getSource(), StringArgumentType.getString(ctx, "id"), null))
                                        .then(Commands.argument("entities", EntityArgument.entities())
                                                .executes(ctx -> unlinkEntities(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        EntityArgument.getEntities(ctx, "entities"))))))
                        // 6. Get
                        .then(Commands.literal("get")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(BossHealthBarCommand::suggestBossBarIds)
                                        .then(Commands.literal("health").executes(ctx -> getHealth(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))
                                        .then(Commands.literal("max_health").executes(ctx -> getMaxHealth(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))
                                        .then(Commands.literal("percent").executes(ctx -> getPercent(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))
                                        .then(Commands.literal("entities").executes(ctx -> getEntityCount(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))
                                        .then(Commands.literal("visible").executes(ctx -> getVisible(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))))
                        // 7. Set
                        .then(Commands.literal("set")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(BossHealthBarCommand::suggestBossBarIds)
                                        // set name
                                        .then(Commands.literal("name")
                                                .then(Commands.argument("name", ComponentArgument.textComponent())
                                                        .executes(ctx -> setName(ctx.getSource(), StringArgumentType.getString(ctx, "id"), ComponentArgument.getComponent(ctx, "name")))))
                                        // set color
                                        .then(Commands.literal("color")
                                                .then(Commands.argument("color", StringArgumentType.word())
                                                        .suggests(BossHealthBarCommand::suggestColors)
                                                        .executes(ctx -> setColor(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "color")))))
                                        // set overlay
                                        .then(Commands.literal("overlay")
                                                .then(Commands.argument("overlay", StringArgumentType.word())
                                                        .suggests(BossHealthBarCommand::suggestOverlays)
                                                        .executes(ctx -> setOverlay(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "overlay")))))
                                        // set style
                                        .then(Commands.literal("style")
                                                .then(Commands.argument("style", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            builder.suggest("default");
                                                            builder.suggest("fractured");
                                                            builder.suggest("custom_texture");
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(ctx -> setStyle(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "style")))))
                                        // set texture
                                        .then(Commands.literal("texture")
                                                .then(Commands.argument("texture", ResourceLocationArgument.id())
                                                        .executes(ctx -> setTexture(ctx.getSource(), StringArgumentType.getString(ctx, "id"), ResourceLocationArgument.getId(ctx, "texture")))))
                                        // set dimensions
                                        .then(Commands.literal("dimensions")
                                                .then(Commands.argument("width", IntegerArgumentType.integer(10, 500))
                                                        .then(Commands.argument("height", IntegerArgumentType.integer(1, 50))
                                                                .executes(ctx -> setDimensions(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                                                                        IntegerArgumentType.getInteger(ctx, "width"),
                                                                        IntegerArgumentType.getInteger(ctx, "height"))))))
                                        // set visible
                                        .then(Commands.literal("visible")
                                                .then(Commands.argument("value", BoolArgumentType.bool())
                                                        .executes(ctx -> setVisible(ctx.getSource(), StringArgumentType.getString(ctx, "id"), BoolArgumentType.getBool(ctx, "value")))))
                                        // set autohide
                                        .then(Commands.literal("autohide")
                                                .then(Commands.argument("value", BoolArgumentType.bool())
                                                        .executes(ctx -> setAutoHide(ctx.getSource(), StringArgumentType.getString(ctx, "id"), BoolArgumentType.getBool(ctx, "value")))))
                                        // set autodelete
                                        .then(Commands.literal("autodelete")
                                                .then(Commands.argument("value", BoolArgumentType.bool())
                                                        .executes(ctx -> setAutoDelete(ctx.getSource(), StringArgumentType.getString(ctx, "id"), BoolArgumentType.getBool(ctx, "value")))))
                                        // set text_display
                                        .then(Commands.literal("text_display")
                                                .then(Commands.argument("mode", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            builder.suggest("none");
                                                            builder.suggest("value");
                                                            builder.suggest("percent");
                                                            builder.suggest("both");
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(ctx -> setTextDisplay(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "mode")))))
                                        // set ghost_bar
                                        .then(Commands.literal("ghost_bar")
                                                .then(Commands.argument("value", BoolArgumentType.bool())
                                                        .executes(ctx -> setGhostBar(ctx.getSource(), StringArgumentType.getString(ctx, "id"), BoolArgumentType.getBool(ctx, "value")))))
                                        // set players
                                        .then(Commands.literal("players")
                                                .then(Commands.literal("all")
                                                        .executes(ctx -> setPlayersAll(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))
                                                .then(Commands.argument("targets", EntityArgument.players())
                                                        .executes(ctx -> setPlayersSpecific(ctx.getSource(), StringArgumentType.getString(ctx, "id"), EntityArgument.getPlayers(ctx, "targets")))))
                                        // set range
                                        .then(Commands.literal("range")
                                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.0, 1000.0))
                                                        .executes(ctx -> setRange(ctx.getSource(), StringArgumentType.getString(ctx, "id"), DoubleArgumentType.getDouble(ctx, "radius")))))))
                        // 8. Phase
                        .then(Commands.literal("phase")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(BossHealthBarCommand::suggestBossBarIds)
                                        .then(Commands.literal("add")
                                                .then(Commands.argument("threshold", FloatArgumentType.floatArg(0.01f, 0.99f))
                                                        .executes(ctx -> addPhase(ctx.getSource(), StringArgumentType.getString(ctx, "id"), FloatArgumentType.getFloat(ctx, "threshold")))))
                                        .then(Commands.literal("remove")
                                                .then(Commands.argument("threshold", FloatArgumentType.floatArg(0.01f, 0.99f))
                                                        .executes(ctx -> removePhase(ctx.getSource(), StringArgumentType.getString(ctx, "id"), FloatArgumentType.getFloat(ctx, "threshold")))))
                                        .then(Commands.literal("clear")
                                                .executes(ctx -> clearPhases(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))))
                        // 9. Info
                        .then(Commands.literal("info")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(BossHealthBarCommand::suggestBossBarIds)
                                        .executes(ctx -> showInfo(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
        );
    }

    private static CompletableFuture<Suggestions> suggestBossBarIds(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (BossHealthBar bar : BossHealthBarManager.getInstance().getAllBossBars()) {
            builder.suggest(bar.getId());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestColors(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (BossEvent.BossBarColor color : BossEvent.BossBarColor.values()) {
            builder.suggest(color.name().toLowerCase(Locale.ROOT));
        }
        builder.suggest("#FF0055");
        builder.suggest("#00FF66");
        builder.suggest("#00E5FF");
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestOverlays(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (BossEvent.BossBarOverlay overlay : BossEvent.BossBarOverlay.values()) {
            builder.suggest(overlay.name().toLowerCase(Locale.ROOT));
        }
        return builder.buildFuture();
    }

    private static int createBossBar(CommandSourceStack source, String id, Component name) {
        BossHealthBar existing = BossHealthBarManager.getInstance().getBossBar(id);
        if (existing != null) {
            source.sendFailure(Component.literal("A boss healthbar with ID '" + id + "' already exists.").withStyle(ChatFormatting.RED));
            return 0;
        }

        BossHealthBar created = BossHealthBarManager.getInstance().createBossBar(id, name);
        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        source.sendSuccess(() -> Component.literal("Successfully created boss healthbar: ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(id).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                .append(name)
                .append(Component.literal(") [Hidden by default, use '/bosshealthbar show ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(id).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("' to display]").withStyle(ChatFormatting.GRAY)), true);
        return 1;
    }

    private static int removeBossBar(CommandSourceStack source, String id) {
        boolean removed = BossHealthBarManager.getInstance().removeBossBar(id);
        if (removed) {
            BossHealthBarManager.getInstance().saveToWorld(source.getServer());
            source.sendSuccess(() -> Component.literal("Removed boss healthbar: " + id).withStyle(ChatFormatting.YELLOW), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("No boss healthbar found with ID '" + id + "'.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int listBossBars(CommandSourceStack source) {
        Collection<BossHealthBar> bars = BossHealthBarManager.getInstance().getAllBossBars();
        if (bars.isEmpty()) {
            source.sendSuccess(() -> Component.literal("There are currently no active boss healthbars.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== Active Boss Healthbars (" + bars.size() + ") ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (BossHealthBar bar : bars) {
            String status = bar.isVisible() ? "VISIBLE" : "HIDDEN";
            ChatFormatting statusColor = bar.isVisible() ? ChatFormatting.GREEN : ChatFormatting.RED;
            source.sendSuccess(() -> Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(bar.getId()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(bar.getName())
                    .append(Component.literal(String.format(Locale.US, " [%.0f/%.0f HP (%.0f%%)]", bar.getCurrentHealth(), bar.getMaxHealth(), bar.getPercent() * 100.0f)).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" (" + bar.getLinkedEntityUuids().size() + " entities, " + bar.getStyleId() + ", ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(status).withStyle(statusColor))
                    .append(Component.literal(")").withStyle(ChatFormatting.GRAY)), false);
        }
        return bars.size();
    }

    private static int linkEntities(CommandSourceStack source, String id, Collection<? extends Entity> entities) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) {
            source.sendFailure(Component.literal("No boss healthbar found with ID '" + id + "'.").withStyle(ChatFormatting.RED));
            return 0;
        }

        int count = 0;
        for (Entity e : entities) {
            if (e instanceof LivingEntity living) {
                BossHealthBarManager.getInstance().linkEntity(id, living);
                count++;
            }
        }

        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        int finalCount = count;
        source.sendSuccess(() -> Component.literal(String.format(Locale.US, "Linked %d living entities to bossbar '%s'. Total Health: %.0f / %.0f",
                finalCount, id, bar.getCurrentHealth(), bar.getMaxHealth())).withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    private static int unlinkEntities(CommandSourceStack source, String id, Collection<? extends Entity> entities) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) {
            source.sendFailure(Component.literal("No boss healthbar found with ID '" + id + "'.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (entities == null || entities.isEmpty()) {
            int prev = bar.getLinkedEntityUuids().size();
            bar.clearLinkedEntities();
            BossHealthBarManager.getInstance().saveToWorld(source.getServer());
            source.sendSuccess(() -> Component.literal("Unlinked all (" + prev + ") entities from bossbar '" + id + "'.").withStyle(ChatFormatting.YELLOW), true);
            return prev;
        } else {
            int count = 0;
            for (Entity e : entities) {
                if (bar.getLinkedEntityUuids().contains(e.getUUID())) {
                    bar.unlinkEntity(e.getUUID());
                    count++;
                }
            }
            BossHealthBarManager.getInstance().saveToWorld(source.getServer());
            int finalCount = count;
            source.sendSuccess(() -> Component.literal(String.format(Locale.US, "Unlinked %d entities from bossbar '%s'.", finalCount, id)).withStyle(ChatFormatting.YELLOW), true);
            return count;
        }
    }

    private static int getHealth(CommandSourceStack source, String id) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        int hp = Math.round(bar.getCurrentHealth());
        source.sendSuccess(() -> Component.literal("Bossbar '" + id + "' health: " + hp).withStyle(ChatFormatting.AQUA), false);
        return hp;
    }

    private static int getMaxHealth(CommandSourceStack source, String id) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        int max = Math.round(bar.getMaxHealth());
        source.sendSuccess(() -> Component.literal("Bossbar '" + id + "' max health: " + max).withStyle(ChatFormatting.AQUA), false);
        return max;
    }

    private static int getPercent(CommandSourceStack source, String id) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        int pct = Math.round(bar.getPercent() * 100.0f);
        source.sendSuccess(() -> Component.literal("Bossbar '" + id + "' percent: " + pct + "%").withStyle(ChatFormatting.AQUA), false);
        return pct;
    }

    private static int getEntityCount(CommandSourceStack source, String id) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        int count = bar.getLinkedEntityUuids().size();
        source.sendSuccess(() -> Component.literal("Bossbar '" + id + "' linked entities: " + count).withStyle(ChatFormatting.AQUA), false);
        return count;
    }

    private static int getVisible(CommandSourceStack source, String id) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        int val = bar.isVisible() ? 1 : 0;
        source.sendSuccess(() -> Component.literal("Bossbar '" + id + "' visible: " + bar.isVisible()).withStyle(ChatFormatting.AQUA), false);
        return val;
    }

    private static int setName(CommandSourceStack source, String id, Component name) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        bar.setName(name);
        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        source.sendSuccess(() -> Component.literal("Updated name for bossbar '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setColor(CommandSourceStack source, String id, String colorStr) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;

        if (colorStr.startsWith("#")) {
            bar.setCustomColorHex(colorStr);
            BossHealthBarManager.getInstance().saveToWorld(source.getServer());
            source.sendSuccess(() -> Component.literal("Set custom hex color " + colorStr + " for bossbar '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
            return 1;
        }

        try {
            BossEvent.BossBarColor color = BossEvent.BossBarColor.valueOf(colorStr.toUpperCase(Locale.ROOT));
            bar.setColor(color);
            bar.setCustomColorHex("");
            BossHealthBarManager.getInstance().saveToWorld(source.getServer());
            source.sendSuccess(() -> Component.literal("Set color to " + color.name() + " for bossbar '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Unknown bossbar color: " + colorStr).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setOverlay(CommandSourceStack source, String id, String overlayStr) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;

        try {
            BossEvent.BossBarOverlay overlay = BossEvent.BossBarOverlay.valueOf(overlayStr.toUpperCase(Locale.ROOT));
            bar.setOverlay(overlay);
            BossHealthBarManager.getInstance().saveToWorld(source.getServer());
            source.sendSuccess(() -> Component.literal("Set overlay to " + overlay.name() + " for bossbar '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Unknown bossbar overlay: " + overlayStr).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setStyle(CommandSourceStack source, String id, String style) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        bar.setStyleId(style);
        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        source.sendSuccess(() -> Component.literal("Set style to '" + style + "' for bossbar '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setTexture(CommandSourceStack source, String id, ResourceLocation texture) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        bar.setCustomTexture(texture);
        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        source.sendSuccess(() -> Component.literal("Set custom texture to " + texture + " for bossbar '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setDimensions(CommandSourceStack source, String id, int width, int height) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        bar.setBarWidth(width);
        bar.setBarHeight(height);
        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        source.sendSuccess(() -> Component.literal(String.format(Locale.US, "Set dimensions to %dx%d for bossbar '%s'.", width, height, id)).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setVisible(CommandSourceStack source, String id, boolean visible) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) {
            source.sendFailure(Component.literal("No boss healthbar found with ID '" + id + "'.").withStyle(ChatFormatting.RED));
            return 0;
        }
        bar.setVisible(visible);
        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        if (visible) {
            source.sendSuccess(() -> Component.literal("Boss healthbar '" + id + "' is now visible.").withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendSuccess(() -> Component.literal("Boss healthbar '" + id + "' is now hidden.").withStyle(ChatFormatting.YELLOW), true);
        }
        return 1;
    }

    private static int setAutoHide(CommandSourceStack source, String id, boolean autohide) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        bar.setAutoHideWhenZero(autohide);
        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        source.sendSuccess(() -> Component.literal("Set autohide to " + autohide + " for bossbar '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setAutoDelete(CommandSourceStack source, String id, boolean autodelete) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        bar.setAutoDeleteWhenDead(autodelete);
        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        source.sendSuccess(() -> Component.literal("Set autodelete to " + autodelete + " for bossbar '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setTextDisplay(CommandSourceStack source, String id, String modeStr) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        BossHealthBar.TextDisplayMode mode = BossHealthBar.TextDisplayMode.fromString(modeStr);
        bar.setTextDisplayMode(mode);
        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        source.sendSuccess(() -> Component.literal("Set text_display to " + mode.name() + " for bossbar '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setGhostBar(CommandSourceStack source, String id, boolean ghostBar) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        bar.setShowGhostBar(ghostBar);
        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        source.sendSuccess(() -> Component.literal("Set ghost_bar to " + ghostBar + " for bossbar '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setPlayersAll(CommandSourceStack source, String id) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        bar.setAllPlayers(true);
        bar.clearAssignedPlayers();
        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        source.sendSuccess(() -> Component.literal("Bossbar '" + id + "' is now visible to all players.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setPlayersSpecific(CommandSourceStack source, String id, Collection<ServerPlayer> players) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        bar.setAllPlayers(false);
        bar.clearAssignedPlayers();
        for (ServerPlayer sp : players) {
            bar.addAssignedPlayer(sp.getUUID());
        }
        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        source.sendSuccess(() -> Component.literal("Assigned " + players.size() + " specific players to bossbar '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
        return players.size();
    }

    private static int setRange(CommandSourceStack source, String id, double range) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        bar.setRange(range);
        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        if (range <= 0.0) {
            source.sendSuccess(() -> Component.literal("Removed proximity range restriction for bossbar '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendSuccess(() -> Component.literal("Set proximity range to " + range + " blocks for bossbar '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
        }
        return 1;
    }

    private static int addPhase(CommandSourceStack source, String id, float threshold) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        PhaseMarkersFeature pm = (PhaseMarkersFeature) bar.getFeatures().computeIfAbsent(PhaseMarkersFeature.ID, k -> new PhaseMarkersFeature());
        pm.addThreshold(threshold);
        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        source.sendSuccess(() -> Component.literal(String.format(Locale.US, "Added phase marker threshold at %.0f%% to bossbar '%s'.", threshold * 100.0f, id)).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removePhase(CommandSourceStack source, String id, float threshold) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        if (bar.getFeatures().containsKey(PhaseMarkersFeature.ID)) {
            PhaseMarkersFeature pm = (PhaseMarkersFeature) bar.getFeatures().get(PhaseMarkersFeature.ID);
            pm.removeThreshold(threshold);
            BossHealthBarManager.getInstance().saveToWorld(source.getServer());
            source.sendSuccess(() -> Component.literal(String.format(Locale.US, "Removed phase marker threshold at %.0f%% from bossbar '%s'.", threshold * 100.0f, id)).withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        return 0;
    }

    private static int clearPhases(CommandSourceStack source, String id) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) return 0;
        bar.getFeatures().remove(PhaseMarkersFeature.ID);
        BossHealthBarManager.getInstance().saveToWorld(source.getServer());
        source.sendSuccess(() -> Component.literal("Cleared all phase markers from bossbar '" + id + "'.").withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int showInfo(CommandSourceStack source, String id) {
        BossHealthBar bar = BossHealthBarManager.getInstance().getBossBar(id);
        if (bar == null) {
            source.sendFailure(Component.literal("No boss healthbar found with ID '" + id + "'.").withStyle(ChatFormatting.RED));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== Boss Healthbar Info: " + id + " ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("Name: ").withStyle(ChatFormatting.GRAY).append(bar.getName()), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.US, "Health: %.1f / %.1f (%.1f%%)", bar.getCurrentHealth(), bar.getMaxHealth(), bar.getPercent() * 100.0f)).withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("Style: " + bar.getStyleId() + " | Color: " + (bar.getCustomColorHex().isEmpty() ? bar.getColor().name() : bar.getCustomColorHex()) + " | Overlay: " + bar.getOverlay().name()).withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("Visible: " + bar.isVisible() + " | AutoHide: " + bar.isAutoHideWhenZero() + " | AutoDelete: " + bar.isAutoDeleteWhenDead()).withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal("Dimensions: " + bar.getBarWidth() + "x" + bar.getBarHeight() + " | Text: " + bar.getTextDisplayMode().name() + " | GhostBar: " + bar.isShowGhostBar()).withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal("Players: " + (bar.isAllPlayers() ? "ALL" : bar.getAssignedPlayers().size() + " assigned") + (bar.getRange() > 0 ? " (within " + bar.getRange() + " blocks)" : "")).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("Linked Entities (" + bar.getLinkedEntityUuids().size() + "): " + bar.getLinkedEntityUuids()).withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }
}
