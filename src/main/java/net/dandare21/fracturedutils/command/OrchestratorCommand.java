package net.dandare21.fracturedutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.dandare21.fracturedutils.checkpoint.CheckpointManager;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.S2CSendSequenceDataPacket;
import net.dandare21.fracturedutils.orchestrator.OrchestratorManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Map;

public class OrchestratorCommand {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_SEQUENCES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(OrchestratorManager.getInstance().getSequenceFileNames(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("orchestrator")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("run")
                        // /orchestrator run <file_name> [start_index]
                        .then(Commands.argument("file_name", StringArgumentType.string())
                                .suggests(SUGGEST_SEQUENCES)
                                .executes(ctx -> executeRunFileOnly(ctx, StringArgumentType.getString(ctx, "file_name"), 0))
                                .then(Commands.argument("start_index", IntegerArgumentType.integer(1))
                                        .executes(ctx -> executeRunFileOnly(ctx, StringArgumentType.getString(ctx, "file_name"), IntegerArgumentType.getInteger(ctx, "start_index") - 1)))
                                // /orchestrator run <file_name> <targets> [start_index]
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> executeRunFileAndTargets(ctx, StringArgumentType.getString(ctx, "file_name"), EntityArgument.getPlayers(ctx, "targets"), 0))
                                        .then(Commands.argument("start_index", IntegerArgumentType.integer(1))
                                                .executes(ctx -> executeRunFileAndTargets(ctx, StringArgumentType.getString(ctx, "file_name"), EntityArgument.getPlayers(ctx, "targets"), IntegerArgumentType.getInteger(ctx, "start_index") - 1)))))
                        // /orchestrator run <targets> <file_name> [start_index]
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("file_name", StringArgumentType.string())
                                        .suggests(SUGGEST_SEQUENCES)
                                        .executes(ctx -> executeRunFileAndTargets(ctx, StringArgumentType.getString(ctx, "file_name"), EntityArgument.getPlayers(ctx, "targets"), 0))
                                        .then(Commands.argument("start_index", IntegerArgumentType.integer(1))
                                                .executes(ctx -> executeRunFileAndTargets(ctx, StringArgumentType.getString(ctx, "file_name"), EntityArgument.getPlayers(ctx, "targets"), IntegerArgumentType.getInteger(ctx, "start_index") - 1))))))
                .then(Commands.literal("resume")
                        .executes(ctx -> executeResume(ctx, null, null))
                        .then(Commands.argument("trigger_id", StringArgumentType.string())
                                .executes(ctx -> executeResume(ctx, StringArgumentType.getString(ctx, "trigger_id"), null))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> executeResume(ctx, StringArgumentType.getString(ctx, "trigger_id"), EntityArgument.getPlayers(ctx, "targets")))))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> executeResume(ctx, null, EntityArgument.getPlayers(ctx, "targets")))
                                .then(Commands.argument("trigger_id", StringArgumentType.string())
                                        .executes(ctx -> executeResume(ctx, StringArgumentType.getString(ctx, "trigger_id"), EntityArgument.getPlayers(ctx, "targets"))))))
                .then(Commands.literal("pause")
                        .executes(ctx -> executePause(ctx, null, null))
                        .then(Commands.argument("file_name", StringArgumentType.string())
                                .suggests(SUGGEST_SEQUENCES)
                                .executes(ctx -> executePause(ctx, StringArgumentType.getString(ctx, "file_name"), null))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> executePause(ctx, StringArgumentType.getString(ctx, "file_name"), EntityArgument.getPlayers(ctx, "targets")))))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> executePause(ctx, null, EntityArgument.getPlayers(ctx, "targets")))
                                .then(Commands.argument("file_name", StringArgumentType.string())
                                        .suggests(SUGGEST_SEQUENCES)
                                        .executes(ctx -> executePause(ctx, StringArgumentType.getString(ctx, "file_name"), EntityArgument.getPlayers(ctx, "targets"))))))
                .then(Commands.literal("cancel")
                        .executes(ctx -> executeCancel(ctx, null, null))
                        .then(Commands.argument("file_name", StringArgumentType.string())
                                .suggests(SUGGEST_SEQUENCES)
                                .executes(ctx -> executeCancel(ctx, StringArgumentType.getString(ctx, "file_name"), null))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> executeCancel(ctx, StringArgumentType.getString(ctx, "file_name"), EntityArgument.getPlayers(ctx, "targets")))))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> executeCancel(ctx, null, EntityArgument.getPlayers(ctx, "targets")))
                                .then(Commands.argument("file_name", StringArgumentType.string())
                                        .suggests(SUGGEST_SEQUENCES)
                                        .executes(ctx -> executeCancel(ctx, StringArgumentType.getString(ctx, "file_name"), EntityArgument.getPlayers(ctx, "targets"))))))
                .then(Commands.literal("stop")
                        .executes(ctx -> executeCancel(ctx, null, null))
                        .then(Commands.argument("file_name", StringArgumentType.string())
                                .suggests(SUGGEST_SEQUENCES)
                                .executes(ctx -> executeCancel(ctx, StringArgumentType.getString(ctx, "file_name"), null))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> executeCancel(ctx, StringArgumentType.getString(ctx, "file_name"), EntityArgument.getPlayers(ctx, "targets")))))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> executeCancel(ctx, null, EntityArgument.getPlayers(ctx, "targets")))
                                .then(Commands.argument("file_name", StringArgumentType.string())
                                        .suggests(SUGGEST_SEQUENCES)
                                        .executes(ctx -> executeCancel(ctx, StringArgumentType.getString(ctx, "file_name"), EntityArgument.getPlayers(ctx, "targets"))))))
                .then(Commands.literal("checkpoint")
                        .then(Commands.literal("restore")
                                .executes(OrchestratorCommand::executeRestoreCheckpoint)))
                .then(Commands.literal("ui")
                        .executes(OrchestratorCommand::openUi))
        );
    }

    private static int executeRunFileOnly(CommandContext<CommandSourceStack> ctx, String fileName, int startIndex) {
        CommandSourceStack source = ctx.getSource();
        String targetPlayerName = source.getEntity() instanceof ServerPlayer player ? player.getScoreboardName() : null;

        boolean started = OrchestratorManager.getInstance().startSequence(fileName, targetPlayerName, startIndex);
        if (started) {
            String fromText = startIndex > 0 ? " from action #" + (startIndex + 1) : "";
            String forText = targetPlayerName != null ? " for player " + targetPlayerName : " (global server sequence)";
            source.sendSuccess(() -> Component.literal("Started sequence '" + fileName + "'" + fromText + forText)
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to start sequence '" + fileName + "'. File missing or invalid syntax.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int executeRunFileAndTargets(CommandContext<CommandSourceStack> ctx, String fileName, Collection<ServerPlayer> targets, int startIndex) {
        CommandSourceStack source = ctx.getSource();
        if (targets == null || targets.isEmpty()) {
            return executeRunFileOnly(ctx, fileName, startIndex);
        }

        int count = 0;
        for (ServerPlayer player : targets) {
            boolean started = OrchestratorManager.getInstance().startSequence(fileName, player.getScoreboardName(), startIndex);
            if (started) {
                count++;
            }
        }

        if (count > 0) {
            final int finalCount = count;
            String fromText = startIndex > 0 ? " from action #" + (startIndex + 1) : "";
            source.sendSuccess(() -> Component.literal("Started sequence '" + fileName + "'" + fromText + " for " + finalCount + " player(s).")
                    .withStyle(ChatFormatting.GREEN), true);
            return count;
        } else {
            source.sendFailure(Component.literal("Failed to start sequence '" + fileName + "'. File missing or invalid syntax.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int executeResume(CommandContext<CommandSourceStack> ctx, String triggerId, Collection<ServerPlayer> targets) {
        CommandSourceStack source = ctx.getSource();
        boolean resumedAny = false;

        if (targets != null && !targets.isEmpty()) {
            for (ServerPlayer player : targets) {
                if (OrchestratorManager.getInstance().resumeTrigger(player.getScoreboardName(), triggerId)) {
                    resumedAny = true;
                }
            }
        } else {
            String targetPlayerName = source.getEntity() instanceof ServerPlayer player ? player.getScoreboardName() : null;
            resumedAny = OrchestratorManager.getInstance().resumeTrigger(targetPlayerName, triggerId);
        }

        if (resumedAny) {
            String desc = (triggerId != null && !triggerId.isEmpty()) ? "trigger '" + triggerId + "'" : "waiting/paused sequence";
            source.sendSuccess(() -> Component.literal("Resumed " + desc + ".").withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            String desc = (triggerId != null && !triggerId.isEmpty()) ? "waiting for trigger '" + triggerId + "'" : "waiting/paused";
            source.sendSuccess(() -> Component.literal("No active sequence found " + desc + ".").withStyle(ChatFormatting.YELLOW), true);
            return 0;
        }
    }

    private static int executePause(CommandContext<CommandSourceStack> ctx, String fileName, Collection<ServerPlayer> targets) {
        CommandSourceStack source = ctx.getSource();
        boolean pausedAny = false;

        if (targets != null && !targets.isEmpty()) {
            for (ServerPlayer player : targets) {
                if (OrchestratorManager.getInstance().pauseSequence(player.getScoreboardName(), fileName)) {
                    pausedAny = true;
                }
            }
        } else {
            String targetPlayerName = source.getEntity() instanceof ServerPlayer player ? player.getScoreboardName() : null;
            pausedAny = OrchestratorManager.getInstance().pauseSequence(targetPlayerName, fileName);
        }

        if (pausedAny) {
            String targetDesc = fileName != null ? "sequence '" + fileName + "'" : "sequence(s)";
            source.sendSuccess(() -> Component.literal("Paused active " + targetDesc + ".").withStyle(ChatFormatting.YELLOW), true);
            return 1;
        } else {
            source.sendSuccess(() -> Component.literal("No matching active sequence found to pause.").withStyle(ChatFormatting.GRAY), true);
            return 0;
        }
    }

    private static int executeCancel(CommandContext<CommandSourceStack> ctx, String fileName, Collection<ServerPlayer> targets) {
        CommandSourceStack source = ctx.getSource();
        boolean cancelledAny = false;

        if (targets != null && !targets.isEmpty()) {
            for (ServerPlayer player : targets) {
                if (OrchestratorManager.getInstance().cancelSequence(player.getScoreboardName(), fileName)) {
                    cancelledAny = true;
                }
            }
        } else {
            String targetPlayerName = source.getEntity() instanceof ServerPlayer player ? player.getScoreboardName() : null;
            cancelledAny = OrchestratorManager.getInstance().cancelSequence(targetPlayerName, fileName);
        }

        if (cancelledAny) {
            String targetDesc = fileName != null ? "sequence '" + fileName + "'" : "sequence(s)";
            source.sendSuccess(() -> Component.literal("Cancelled " + targetDesc + ".").withStyle(ChatFormatting.RED), true);
            return 1;
        } else {
            source.sendSuccess(() -> Component.literal("No matching active sequence found to cancel.").withStyle(ChatFormatting.GRAY), true);
            return 0;
        }
    }

    private static int executeRestoreCheckpoint(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!CheckpointManager.getInstance().hasActiveCheckpoint()) {
            source.sendFailure(Component.literal("No active orchestrator checkpoint found to restore."));
            return 0;
        }
        CheckpointManager.getInstance().restoreCheckpoint(source.getServer());
        source.sendSuccess(() -> Component.literal("Restored active orchestrator checkpoint, despawned sequence puppets, and stopped music sequences.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int openUi(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            Map<String, String> files = OrchestratorManager.getInstance().getAllSequenceFiles();
            ModMessages.sendToPlayer(new S2CSendSequenceDataPacket(files), player);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Only players can open the orchestrator UI screen."));
            return 0;
        }
    }
}
