package net.dandare21.fracturedutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.dandare21.fracturedutils.dialog.DialogManager;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.S2CSendDialogSequenceDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DialogCommand {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_DIALOG_SEQUENCES = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(DialogManager.getInstance().getSequenceIds(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dialog")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("run")
                        .then(Commands.argument("file_name", StringArgumentType.string())
                                .suggests(SUGGEST_DIALOG_SEQUENCES)
                                .executes(ctx -> executeRun(ctx, StringArgumentType.getString(ctx, "file_name"), null))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> executeRun(ctx, StringArgumentType.getString(ctx, "file_name"), EntityArgument.getPlayers(ctx, "targets"))))))
                .then(Commands.literal("skip")
                        .executes(DialogCommand::executeSkip))
                .then(Commands.literal("next")
                        .executes(DialogCommand::executeSkip))
                .then(Commands.literal("stop")
                        .executes(DialogCommand::executeStop))
                .then(Commands.literal("cancel")
                        .executes(DialogCommand::executeStop))
                .then(Commands.literal("list")
                        .executes(DialogCommand::executeList))
                .then(Commands.literal("ui")
                        .executes(DialogCommand::openUi))
        );
    }

    private static int executeRun(CommandContext<CommandSourceStack> ctx, String fileName, Collection<ServerPlayer> targets) {
        CommandSourceStack source = ctx.getSource();
        boolean started = DialogManager.getInstance().startSequence(fileName, targets);
        if (started) {
            int targetCount = targets != null ? targets.size() : source.getServer().getPlayerList().getPlayers().size();
            source.sendSuccess(() -> Component.literal("Started dialog sequence '" + fileName + "' for " + targetCount + " targeted player(s).")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to start dialog sequence '" + fileName + "'. Sequence not found or invalid.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int executeSkip(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        boolean skipped = DialogManager.getInstance().skipCurrentLine(source.getServer());
        if (skipped) {
            source.sendSuccess(() -> Component.literal("Skipped current active dialog line.")
                    .withStyle(ChatFormatting.AQUA), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("No active dialog sequence running.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        DialogManager.getInstance().stopAllSequences(source.getServer());
        source.sendSuccess(() -> Component.literal("Stopped all active dialog sequences.")
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Collection<net.minecraft.resources.ResourceLocation> ids = DialogManager.getInstance().getSequenceIds();
        if (ids.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No dialog sequences found.")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            List<String> list = ids.stream().map(net.minecraft.resources.ResourceLocation::toString).sorted().toList();
            source.sendSuccess(() -> Component.literal("Available dialog sequences (" + list.size() + "): " + String.join(", ", list))
                    .withStyle(ChatFormatting.AQUA), false);
        }
        return ids.size();
    }

    private static int openUi(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (source.getEntity() instanceof ServerPlayer player) {
            Map<String, String> files = DialogManager.getInstance().getAllSequenceFiles();
            ModMessages.sendToPlayer(new S2CSendDialogSequenceDataPacket(files), player);
            return 1;
        } else {
            source.sendFailure(Component.literal("This command must be executed by a player."));
            return 0;
        }
    }
}
