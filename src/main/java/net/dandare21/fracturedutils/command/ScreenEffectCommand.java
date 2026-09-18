package net.dandare21.fracturedutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectManager;
import net.dandare21.fracturedutils.screeneffect.ScreenEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Collections;

public class ScreenEffectCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("screeneffect")
                .requires(s -> s.hasPermission(2))
                // /screeneffect play ...
                .then(Commands.literal("play")
                        .then(Commands.literal("impact_frame")
                                .executes(ctx -> playImpact(ctx.getSource(), getSelf(ctx.getSource()), 250, 0xFFFFFFFF, 0xFF000000))
                                .then(Commands.argument("durationMs", IntegerArgumentType.integer(20, 5000))
                                        .executes(ctx -> playImpact(ctx.getSource(), getSelf(ctx.getSource()), IntegerArgumentType.getInteger(ctx, "durationMs"), 0xFFFFFFFF, 0xFF000000))
                                        .then(Commands.argument("primaryColor", StringArgumentType.word())
                                                .executes(ctx -> playImpact(ctx.getSource(), getSelf(ctx.getSource()), IntegerArgumentType.getInteger(ctx, "durationMs"), parseColor(StringArgumentType.getString(ctx, "primaryColor"), 0xFFFFFFFF), 0xFF000000))
                                                .then(Commands.argument("secondaryColor", StringArgumentType.word())
                                                        .executes(ctx -> playImpact(ctx.getSource(), getSelf(ctx.getSource()), IntegerArgumentType.getInteger(ctx, "durationMs"), parseColor(StringArgumentType.getString(ctx, "primaryColor"), 0xFFFFFFFF), parseColor(StringArgumentType.getString(ctx, "secondaryColor"), 0xFF000000)))
                                                        .then(Commands.argument("targets", EntityArgument.players())
                                                                .executes(ctx -> playImpact(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), IntegerArgumentType.getInteger(ctx, "durationMs"), parseColor(StringArgumentType.getString(ctx, "primaryColor"), 0xFFFFFFFF), parseColor(StringArgumentType.getString(ctx, "secondaryColor"), 0xFF000000))))))))
                        .then(Commands.literal("shake")
                                .executes(ctx -> playShake(ctx.getSource(), getSelf(ctx.getSource()), 500, 2.5f))
                                .then(Commands.argument("durationMs", IntegerArgumentType.integer(50, 10000))
                                        .executes(ctx -> playShake(ctx.getSource(), getSelf(ctx.getSource()), IntegerArgumentType.getInteger(ctx, "durationMs"), 2.5f))))
                        .then(Commands.literal("invert")
                                .executes(ctx -> playInvert(ctx.getSource(), getSelf(ctx.getSource()), 300))
                                .then(Commands.argument("durationMs", IntegerArgumentType.integer(50, 10000))
                                        .executes(ctx -> playInvert(ctx.getSource(), getSelf(ctx.getSource()), IntegerArgumentType.getInteger(ctx, "durationMs"))))))
                // /screeneffect stop ...
                .then(Commands.literal("stop")
                        .executes(ctx -> stopAll(ctx.getSource(), getSelf(ctx.getSource())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> stopAll(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
        );
    }

    private static Collection<ServerPlayer> getSelf(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return Collections.singletonList(player);
        }
        return source.getServer().getPlayerList().getPlayers();
    }

    private static int playImpact(CommandSourceStack source, Collection<ServerPlayer> targets, int durationMs, int pColor, int sColor) {
        var effect = ScreenEffects.impactFrame(durationMs, pColor, sColor, 35);
        ScreenEffectManager.playEffect(targets, effect);
        source.sendSuccess(() -> Component.literal("Triggered Manga Draw Impact Frame (" + durationMs + "ms, colors: #" + Integer.toHexString(pColor).toUpperCase() + " / #" + Integer.toHexString(sColor).toUpperCase() + ") on " + targets.size() + " player(s)").withStyle(ChatFormatting.AQUA), true);
        return targets.size();
    }

    private static int playShake(CommandSourceStack source, Collection<ServerPlayer> targets, int durationMs, float intensity) {
        var effect = ScreenEffects.shake(durationMs, intensity);
        ScreenEffectManager.playEffect(targets, effect);
        source.sendSuccess(() -> Component.literal("Triggered Screen Shake (" + durationMs + "ms) on " + targets.size() + " player(s)").withStyle(ChatFormatting.GREEN), true);
        return targets.size();
    }

    private static int playInvert(CommandSourceStack source, Collection<ServerPlayer> targets, int durationMs) {
        var effect = ScreenEffects.invert(durationMs);
        ScreenEffectManager.playEffect(targets, effect);
        source.sendSuccess(() -> Component.literal("Triggered Invert Colors (" + durationMs + "ms) on " + targets.size() + " player(s)").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return targets.size();
    }

    private static int stopAll(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer target : targets) {
            ScreenEffectManager.stopAllEffects(target);
        }
        source.sendSuccess(() -> Component.literal("Stopped all screen effects on " + targets.size() + " player(s)").withStyle(ChatFormatting.YELLOW), true);
        return targets.size();
    }

    private static int parseColor(String hex, int fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        try {
            String clean = hex.trim();
            if (clean.startsWith("#")) clean = clean.substring(1);
            if (clean.startsWith("0x") || clean.startsWith("0X")) clean = clean.substring(2);

            long val = Long.parseLong(clean, 16);
            if (clean.length() <= 6) {
                return (int) (0xFF000000L | val);
            }
            return (int) val;
        } catch (Exception e) {
            return fallback;
        }
    }
}
