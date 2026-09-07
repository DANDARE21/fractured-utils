package net.dandare21.fracturedutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.dandare21.fracturedutils.config.ServerConfig;
import net.dandare21.fracturedutils.puppet.boss.VoidHeraldBoss;
import net.dandare21.fracturedutils.puppet.registry.ModEntities;
import net.dandare21.fracturedutils.puppet.target.ActionTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Command for debugging and controlling the Void Herald Boss presence and actions.
 * Usage:
 *   /puppetboss status
 *   /puppetboss enable
 *   /puppetboss disable
 *   /puppetboss spawn
 *   /puppetboss action <action_id>
 */
public class BossPuppetCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("puppetboss")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("status").executes(ctx -> showStatus(ctx.getSource())))
                        .then(Commands.literal("enable").executes(ctx -> setEnabled(ctx.getSource(), true)))
                        .then(Commands.literal("disable").executes(ctx -> setEnabled(ctx.getSource(), false)))
                        .then(Commands.literal("spawn").executes(ctx -> spawnBoss(ctx.getSource())))
                        .then(Commands.literal("action")
                                .then(Commands.argument("action_name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("leap_slam");
                                            builder.suggest("abyssal_barrage");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> triggerAction(ctx.getSource(), StringArgumentType.getString(ctx, "action_name")))))
        );
    }

    private static int showStatus(CommandSourceStack source) {
        boolean enabled = ServerConfig.isEnableDebugBoss();
        int activeCount = 0;
        if (source.getServer() != null) {
            for (ServerLevel level : source.getServer().getAllLevels()) {
                for (Entity e : level.getAllEntities()) {
                    if (e instanceof VoidHeraldBoss && e.isAlive()) {
                        activeCount++;
                    }
                }
            }
        }

        source.sendSuccess(() -> Component.literal("=== Void Herald Debug Boss ===").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("enableDebugBoss Config: ")
                .append(Component.literal(enabled ? "ENABLED" : "DISABLED")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        int finalActiveCount = activeCount;
        source.sendSuccess(() -> Component.literal("Active World Instances: " + finalActiveCount).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int setEnabled(CommandSourceStack source, boolean enabled) {
        ServerConfig.setEnableDebugBoss(enabled);

        if (!enabled && source.getServer() != null) {
            int discarded = 0;
            for (ServerLevel level : source.getServer().getAllLevels()) {
                for (Entity e : level.getAllEntities()) {
                    if (e instanceof VoidHeraldBoss) {
                        e.discard();
                        discarded++;
                    }
                }
            }
            int finalDiscarded = discarded;
            source.sendSuccess(() -> Component.literal("[PuppetBoss] enableDebugBoss set to false. Discarded " + finalDiscarded + " active instance(s).")
                    .withStyle(ChatFormatting.YELLOW), true);
        } else {
            source.sendSuccess(() -> Component.literal("[PuppetBoss] enableDebugBoss set to true. Void Herald can now spawn and exist.")
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return 1;
    }

    private static int spawnBoss(CommandSourceStack source) {
        if (!ServerConfig.isEnableDebugBoss()) {
            source.sendFailure(Component.literal("Cannot spawn Void Herald: 'enableDebugBoss' is disabled in server config! Run '/puppetboss enable' first.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();

        VoidHeraldBoss boss = ModEntities.VOID_HERALD.get().create(level);
        if (boss != null) {
            boss.moveTo(pos.x, pos.y, pos.z, source.getRotation().y, 0.0F);
            level.addFreshEntity(boss);
            source.sendSuccess(() -> Component.literal("Successfully spawned Void Herald Boss at " + String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z))
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to instantiate Void Herald Boss entity.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int triggerAction(CommandSourceStack source, String actionName) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();

        AABB searchBox = new AABB(pos.x - 32, pos.y - 16, pos.z - 32, pos.x + 32, pos.y + 16, pos.z + 32);
        List<VoidHeraldBoss> nearby = level.getEntitiesOfClass(VoidHeraldBoss.class, searchBox, Entity::isAlive);

        if (nearby.isEmpty()) {
            source.sendFailure(Component.literal("No active Void Herald Boss found within 32 blocks.").withStyle(ChatFormatting.RED));
            return 0;
        }

        VoidHeraldBoss boss = nearby.get(0);
        ActionTarget target = source.getEntity() instanceof ServerPlayer player
                ? ActionTarget.fromEntity(player.getUUID())
                : ActionTarget.fromPos(pos);

        boss.triggerOrchestratedAction(actionName, target);
        source.sendSuccess(() -> Component.literal("Triggered action '" + actionName + "' on Void Herald boss.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return 1;
    }
}
