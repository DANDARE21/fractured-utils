package net.dandare21.fracturedutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.dandare21.fracturedutils.threshold.HealthThreshold;
import net.dandare21.fracturedutils.threshold.HealthThresholdManager;
import net.dandare21.fracturedutils.threshold.HealthValue;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Command suite for managing entity health thresholds.
 *
 * Usage:
 *   /healththreshold set <entities> <min> <umbral> [id]
 *   /healththreshold add <entities> <min> <umbral> [id]
 *   /healththreshold remove <entities> [id]
 *   /healththreshold clear <entities>
 *   /healththreshold get <entity>
 *   /healththreshold list
 *
 * Alias: /threshold
 */
public class HealthThresholdCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("healththreshold")
                .requires(src -> src.hasPermission(2))
                // 1. SET
                .then(Commands.literal("set")
                        .then(Commands.argument("entities", EntityArgument.entities())
                                .then(Commands.argument("min", StringArgumentType.word())
                                        .suggests(HealthThresholdCommand::suggestMinValues)
                                        .then(Commands.argument("umbral", StringArgumentType.word())
                                                .suggests(HealthThresholdCommand::suggestUmbralValues)
                                                .executes(ctx -> setThresholds(
                                                        ctx.getSource(),
                                                        EntityArgument.getEntities(ctx, "entities"),
                                                        StringArgumentType.getString(ctx, "min"),
                                                        StringArgumentType.getString(ctx, "umbral"),
                                                        "default",
                                                        true
                                                ))
                                                .then(Commands.argument("id", StringArgumentType.word())
                                                        .suggests(HealthThresholdCommand::suggestIds)
                                                        .executes(ctx -> setThresholds(
                                                                ctx.getSource(),
                                                                EntityArgument.getEntities(ctx, "entities"),
                                                                StringArgumentType.getString(ctx, "min"),
                                                                StringArgumentType.getString(ctx, "umbral"),
                                                                StringArgumentType.getString(ctx, "id"),
                                                                true
                                                        )))))))
                // 2. ADD (Appends to threshold stack for multi-phase progression)
                .then(Commands.literal("add")
                        .then(Commands.argument("entities", EntityArgument.entities())
                                .then(Commands.argument("min", StringArgumentType.word())
                                        .suggests(HealthThresholdCommand::suggestMinValues)
                                        .then(Commands.argument("umbral", StringArgumentType.word())
                                                .suggests(HealthThresholdCommand::suggestUmbralValues)
                                                .executes(ctx -> setThresholds(
                                                        ctx.getSource(),
                                                        EntityArgument.getEntities(ctx, "entities"),
                                                        StringArgumentType.getString(ctx, "min"),
                                                        StringArgumentType.getString(ctx, "umbral"),
                                                        "phase_" + System.currentTimeMillis() % 1000,
                                                        false
                                                ))
                                                .then(Commands.argument("id", StringArgumentType.word())
                                                        .suggests(HealthThresholdCommand::suggestIds)
                                                        .executes(ctx -> setThresholds(
                                                                ctx.getSource(),
                                                                EntityArgument.getEntities(ctx, "entities"),
                                                                StringArgumentType.getString(ctx, "min"),
                                                                StringArgumentType.getString(ctx, "umbral"),
                                                                StringArgumentType.getString(ctx, "id"),
                                                                false
                                                        )))))))
                // 3. REMOVE
                .then(Commands.literal("remove")
                        .then(Commands.argument("entities", EntityArgument.entities())
                                .executes(ctx -> removeThresholds(
                                        ctx.getSource(),
                                        EntityArgument.getEntities(ctx, "entities"),
                                        null
                                ))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(HealthThresholdCommand::suggestIds)
                                        .executes(ctx -> removeThresholds(
                                                ctx.getSource(),
                                                EntityArgument.getEntities(ctx, "entities"),
                                                StringArgumentType.getString(ctx, "id")
                                        )))))
                // 4. CLEAR
                .then(Commands.literal("clear")
                        .then(Commands.argument("entities", EntityArgument.entities())
                                .executes(ctx -> clearThresholds(
                                        ctx.getSource(),
                                        EntityArgument.getEntities(ctx, "entities")
                                ))))
                // 5. GET / INFO
                .then(Commands.literal("get")
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(ctx -> getEntityThresholdInfo(
                                        ctx.getSource(),
                                        EntityArgument.getEntity(ctx, "entity")
                                ))))
                // 6. LIST
                .then(Commands.literal("list")
                        .executes(ctx -> listThresholdEntities(ctx.getSource())));

        dispatcher.register(root);

        // Register alias /threshold
        dispatcher.register(Commands.literal("threshold")
                .requires(src -> src.hasPermission(2))
                .redirect(dispatcher.getRoot().getChild("healththreshold")));
    }

    private static CompletableFuture<Suggestions> suggestMinValues(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        builder.suggest("25%");
        builder.suggest("50%");
        builder.suggest("75%");
        builder.suggest("10");
        builder.suggest("20");
        builder.suggest("50");
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestUmbralValues(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        builder.suggest("35%");
        builder.suggest("60%");
        builder.suggest("85%");
        builder.suggest("15");
        builder.suggest("30");
        builder.suggest("75");
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestIds(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        builder.suggest("default");
        builder.suggest("phase1");
        builder.suggest("phase2");
        builder.suggest("phase3");
        return builder.buildFuture();
    }

    private static int setThresholds(CommandSourceStack source, Collection<? extends Entity> targets,
                                     String minStr, String umbralStr, String id, boolean replaceAll) {
        HealthValue minVal;
        HealthValue umbralVal;
        try {
            minVal = HealthValue.parse(minStr);
            umbralVal = HealthValue.parse(umbralStr);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Invalid health format: " + e.getMessage()));
            return 0;
        }

        // Validate relative magnitude if both are of same unit type
        if (minVal.isPercentage() == umbralVal.isPercentage()) {
            if (umbralVal.getRawValue() < minVal.getRawValue()) {
                source.sendFailure(Component.literal(String.format(
                        "Umbral (%s) cannot be smaller than minimum health threshold (%s)!", umbralStr, minStr)));
                return 0;
            }
        }

        HealthThreshold threshold = new HealthThreshold(id, minVal, umbralVal);
        int affectedCount = 0;

        for (Entity e : targets) {
            if (e instanceof LivingEntity living) {
                if (replaceAll) {
                    HealthThresholdManager.setThreshold(living, threshold);
                } else {
                    HealthThresholdManager.addThreshold(living, threshold);
                }
                affectedCount++;
            }
        }

        if (affectedCount == 0) {
            source.sendFailure(Component.literal("No living entities matched the target selector."));
            return 0;
        }

        int finalCount = affectedCount;
        source.sendSuccess(() -> Component.literal(String.format(Locale.US,
                "%s threshold '%s' for %d %s: Min: %s | Umbral: %s",
                replaceAll ? "Set" : "Added",
                id,
                finalCount,
                finalCount == 1 ? "entity" : "entities",
                minVal,
                umbralVal
        )).withStyle(ChatFormatting.GREEN), true);

        return affectedCount;
    }

    private static int removeThresholds(CommandSourceStack source, Collection<? extends Entity> targets, String id) {
        int removedCount = 0;
        for (Entity e : targets) {
            if (e instanceof LivingEntity living) {
                if (HealthThresholdManager.removeThreshold(living, id)) {
                    removedCount++;
                }
            }
        }

        if (removedCount == 0) {
            source.sendFailure(Component.literal("No matching thresholds found on target entities."));
            return 0;
        }

        int finalCount = removedCount;
        source.sendSuccess(() -> Component.literal(String.format(
                "Removed threshold '%s' from %d entities.",
                id != null ? id : "<primary>",
                finalCount
        )).withStyle(ChatFormatting.YELLOW), true);

        return removedCount;
    }

    private static int clearThresholds(CommandSourceStack source, Collection<? extends Entity> targets) {
        int clearedCount = 0;
        for (Entity e : targets) {
            if (e instanceof LivingEntity living) {
                if (HealthThresholdManager.hasThresholds(living)) {
                    HealthThresholdManager.clearThresholds(living);
                    clearedCount++;
                }
            }
        }

        if (clearedCount == 0) {
            source.sendFailure(Component.literal("No target entities had active thresholds."));
            return 0;
        }

        int finalCount = clearedCount;
        source.sendSuccess(() -> Component.literal(String.format(
                "Cleared all health thresholds from %d entities.", finalCount
        )).withStyle(ChatFormatting.GREEN), true);

        return clearedCount;
    }

    private static int getEntityThresholdInfo(CommandSourceStack source, Entity target) {
        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.literal("Target entity is not a LivingEntity."));
            return 0;
        }

        List<HealthThreshold> thresholds = HealthThresholdManager.getThresholds(living);
        source.sendSuccess(() -> Component.literal(String.format("=== Health Thresholds for %s ===",
                living.getDisplayName().getString())).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        float curHp = living.getHealth();
        float maxHp = living.getMaxHealth();
        float pct = maxHp > 0 ? (curHp / maxHp) * 100.0f : 0.0f;

        source.sendSuccess(() -> Component.literal(String.format(Locale.US,
                "Health: %.1f / %.1f HP (%.1f%%)", curHp, maxHp, pct)).withStyle(ChatFormatting.WHITE), false);

        if (thresholds.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No thresholds active on this entity.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        Optional<HealthThreshold> activeOpt = HealthThresholdManager.getActiveThreshold(living);
        HealthThreshold active = activeOpt.orElse(null);

        source.sendSuccess(() -> Component.literal("Configured Thresholds:").withStyle(ChatFormatting.YELLOW), false);
        for (HealthThreshold t : thresholds) {
            boolean isActive = t == active;
            String prefix = isActive ? " -> [ACTIVE] " : "    ";
            ChatFormatting color = isActive ? ChatFormatting.AQUA : ChatFormatting.GRAY;
            source.sendSuccess(() -> Component.literal(prefix + t.format(living)).withStyle(color), false);
        }

        if (active != null) {
            float hMin = active.getMinHealthResolved(living);
            float hUmbral = active.getUmbralResolved(living);

            if (curHp <= hMin) {
                source.sendSuccess(() -> Component.literal("Status: At minimum threshold! Damage is 100% ineffective.")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
            } else if (curHp <= hUmbral) {
                float reductionRatio = 1.0f - ((curHp - hMin) / (hUmbral - hMin));
                source.sendSuccess(() -> Component.literal(String.format(Locale.US,
                        "Status: Inside umbral buffer! Current incoming damage reduction: %.1f%%",
                        reductionRatio * 100.0f)).withStyle(ChatFormatting.GOLD), false);
            } else {
                source.sendSuccess(() -> Component.literal("Status: Above umbral. Taking normal damage.")
                        .withStyle(ChatFormatting.GREEN), false);
            }
        }

        return 1;
    }

    private static int listThresholdEntities(CommandSourceStack source) {
        if (source.getServer() == null) return 0;

        List<LivingEntity> list = new ArrayList<>();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof LivingEntity living && HealthThresholdManager.hasThresholds(living)) {
                    list.add(living);
                }
            }
        }

        if (list.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No loaded entities currently have health thresholds.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal(String.format("Found %d entities with active health thresholds:", list.size()))
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        for (LivingEntity living : list) {
            Optional<HealthThreshold> active = HealthThresholdManager.getActiveThreshold(living);
            String info = active.map(t -> t.format(living)).orElse("None");
            source.sendSuccess(() -> Component.literal(String.format(Locale.US,
                    "- %s (UUID: %s) [HP: %.1f/%.1f]: %s",
                    living.getDisplayName().getString(),
                    living.getStringUUID(),
                    living.getHealth(),
                    living.getMaxHealth(),
                    info
            )).withStyle(ChatFormatting.YELLOW), false);
        }

        return list.size();
    }
}
