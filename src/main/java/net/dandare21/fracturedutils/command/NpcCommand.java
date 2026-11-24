package net.dandare21.fracturedutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.dandare21.fracturedutils.dialog.DialogManager;
import net.dandare21.fracturedutils.npc.entity.CustomNpcEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NpcCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("npc")
                .requires(s -> s.hasPermission(2))
                // /npc <targets> ...
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.literal("move")
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> executeMove(ctx, EntityArgument.getEntities(ctx, "targets"), Vec3Argument.getVec3(ctx, "pos"), 1.0))
                                        .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.1, 5.0))
                                                .executes(ctx -> executeMove(ctx, EntityArgument.getEntities(ctx, "targets"), Vec3Argument.getVec3(ctx, "pos"), DoubleArgumentType.getDouble(ctx, "speed"))))))
                        .then(Commands.literal("follow")
                                .then(Commands.literal("stop")
                                        .executes(ctx -> executeFollowStop(ctx, EntityArgument.getEntities(ctx, "targets"))))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeFollow(ctx, EntityArgument.getEntities(ctx, "targets"), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("defend")
                                .then(Commands.literal("stop")
                                        .executes(ctx -> executeDefendStop(ctx, EntityArgument.getEntities(ctx, "targets"))))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeDefend(ctx, EntityArgument.getEntities(ctx, "targets"), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("stop")
                                .executes(ctx -> executeStop(ctx, EntityArgument.getEntities(ctx, "targets"))))
                        .then(Commands.literal("tp")
                                .executes(ctx -> executeTeleportEntity(ctx, EntityArgument.getEntities(ctx, "targets"), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> executeTeleportPos(ctx, EntityArgument.getEntities(ctx, "targets"), Vec3Argument.getVec3(ctx, "pos"))))
                                .then(Commands.argument("destination", EntityArgument.entity())
                                        .executes(ctx -> executeTeleportEntity(ctx, EntityArgument.getEntities(ctx, "targets"), EntityArgument.getEntity(ctx, "destination")))))
                        .then(Commands.literal("teleport")
                                .executes(ctx -> executeTeleportEntity(ctx, EntityArgument.getEntities(ctx, "targets"), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> executeTeleportPos(ctx, EntityArgument.getEntities(ctx, "targets"), Vec3Argument.getVec3(ctx, "pos"))))
                                .then(Commands.argument("destination", EntityArgument.entity())
                                        .executes(ctx -> executeTeleportEntity(ctx, EntityArgument.getEntities(ctx, "targets"), EntityArgument.getEntity(ctx, "destination")))))
                        .then(Commands.literal("nametag")
                                .then(Commands.argument("visible", BoolArgumentType.bool())
                                        .executes(ctx -> executeNametag(ctx, EntityArgument.getEntities(ctx, "targets"), BoolArgumentType.getBool(ctx, "visible")))))
                        .then(Commands.literal("look")
                                .then(Commands.argument("target_player", EntityArgument.player())
                                        .executes(ctx -> executeLookPlayer(ctx, EntityArgument.getEntities(ctx, "targets"), EntityArgument.getPlayer(ctx, "target_player"))))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> executeLookPos(ctx, EntityArgument.getEntities(ctx, "targets"), Vec3Argument.getVec3(ctx, "pos")))))
                        .then(Commands.literal("anim")
                                .then(Commands.argument("animation", StringArgumentType.string())
                                        .executes(ctx -> executeAnim(ctx, EntityArgument.getEntities(ctx, "targets"), StringArgumentType.getString(ctx, "animation")))))
                        .then(Commands.literal("dialog")
                                .then(Commands.argument("sequence", StringArgumentType.string())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(DialogManager.getInstance().getSequenceIds(), builder))
                                        .executes(ctx -> executeDialog(ctx, EntityArgument.getEntities(ctx, "targets"), StringArgumentType.getString(ctx, "sequence")))))
                )
                // /npc id <npc_id> ...
                .then(Commands.literal("id")
                        .then(Commands.argument("npc_id", StringArgumentType.string())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                        net.dandare21.fracturedutils.npc.NpcDefinitionManager.getInstance().getAllIds(), builder))
                                .then(Commands.literal("stop")
                                        .executes(ctx -> executeStop(ctx, findNpcsById(ctx.getSource(), StringArgumentType.getString(ctx, "npc_id")))))
                                .then(Commands.literal("follow")
                                        .then(Commands.literal("stop")
                                                .executes(ctx -> executeFollowStop(ctx, findNpcsById(ctx.getSource(), StringArgumentType.getString(ctx, "npc_id")))))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> executeFollow(ctx, findNpcsById(ctx.getSource(), StringArgumentType.getString(ctx, "npc_id")), EntityArgument.getPlayer(ctx, "player")))))
                                .then(Commands.literal("defend")
                                        .then(Commands.literal("stop")
                                                .executes(ctx -> executeDefendStop(ctx, findNpcsById(ctx.getSource(), StringArgumentType.getString(ctx, "npc_id")))))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> executeDefend(ctx, findNpcsById(ctx.getSource(), StringArgumentType.getString(ctx, "npc_id")), EntityArgument.getPlayer(ctx, "player")))))
                                .then(Commands.literal("move")
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(ctx -> executeMove(ctx, findNpcsById(ctx.getSource(), StringArgumentType.getString(ctx, "npc_id")), Vec3Argument.getVec3(ctx, "pos"), 1.0))))
                                .then(Commands.literal("tp")
                                        .executes(ctx -> executeTeleportEntity(ctx, findNpcsById(ctx.getSource(), StringArgumentType.getString(ctx, "npc_id")), ctx.getSource().getPlayerOrException()))
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(ctx -> executeTeleportPos(ctx, findNpcsById(ctx.getSource(), StringArgumentType.getString(ctx, "npc_id")), Vec3Argument.getVec3(ctx, "pos"))))
                                        .then(Commands.argument("destination", EntityArgument.entity())
                                                .executes(ctx -> executeTeleportEntity(ctx, findNpcsById(ctx.getSource(), StringArgumentType.getString(ctx, "npc_id")), EntityArgument.getEntity(ctx, "destination")))))
                                .then(Commands.literal("teleport")
                                        .executes(ctx -> executeTeleportEntity(ctx, findNpcsById(ctx.getSource(), StringArgumentType.getString(ctx, "npc_id")), ctx.getSource().getPlayerOrException()))
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(ctx -> executeTeleportPos(ctx, findNpcsById(ctx.getSource(), StringArgumentType.getString(ctx, "npc_id")), Vec3Argument.getVec3(ctx, "pos"))))
                                        .then(Commands.argument("destination", EntityArgument.entity())
                                                .executes(ctx -> executeTeleportEntity(ctx, findNpcsById(ctx.getSource(), StringArgumentType.getString(ctx, "npc_id")), EntityArgument.getEntity(ctx, "destination")))))
                                .then(Commands.literal("nametag")
                                        .then(Commands.argument("visible", BoolArgumentType.bool())
                                                .executes(ctx -> executeNametag(ctx, findNpcsById(ctx.getSource(), StringArgumentType.getString(ctx, "npc_id")), BoolArgumentType.getBool(ctx, "visible")))))
                        )
                )
                // Top-level /npc tp & /npc teleport
                .then(Commands.literal("tp")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> executeTeleportEntity(ctx, EntityArgument.getEntities(ctx, "targets"), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> executeTeleportPos(ctx, EntityArgument.getEntities(ctx, "targets"), Vec3Argument.getVec3(ctx, "pos"))))
                                .then(Commands.argument("destination", EntityArgument.entity())
                                        .executes(ctx -> executeTeleportEntity(ctx, EntityArgument.getEntities(ctx, "targets"), EntityArgument.getEntity(ctx, "destination"))))))
                .then(Commands.literal("teleport")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> executeTeleportEntity(ctx, EntityArgument.getEntities(ctx, "targets"), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> executeTeleportPos(ctx, EntityArgument.getEntities(ctx, "targets"), Vec3Argument.getVec3(ctx, "pos"))))
                                .then(Commands.argument("destination", EntityArgument.entity())
                                        .executes(ctx -> executeTeleportEntity(ctx, EntityArgument.getEntities(ctx, "targets"), EntityArgument.getEntity(ctx, "destination"))))))
                // /npc spawn <template> [pos]
                .then(Commands.literal("spawn")
                        .then(Commands.argument("template", StringArgumentType.string())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                        net.dandare21.fracturedutils.npc.NpcDefinitionManager.getInstance().getAllIds(), builder))
                                .executes(ctx -> executeSpawn(ctx, StringArgumentType.getString(ctx, "template"), ctx.getSource().getPosition()))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> executeSpawn(ctx, StringArgumentType.getString(ctx, "template"), Vec3Argument.getVec3(ctx, "pos"))))))
                // /npc list
                .then(Commands.literal("list")
                        .executes(NpcCommand::executeList))
                // /npc reload
                .then(Commands.literal("reload")
                        .executes(NpcCommand::executeReload))
        );
    }

    private static List<CustomNpcEntity> filterNpcs(Collection<? extends Entity> entities) {
        List<CustomNpcEntity> npcs = new ArrayList<>();
        if (entities != null) {
            for (Entity e : entities) {
                if (e instanceof CustomNpcEntity npc) {
                    npcs.add(npc);
                }
            }
        }
        return npcs;
    }

    private static List<CustomNpcEntity> findNpcsById(CommandSourceStack source, String id) {
        List<CustomNpcEntity> result = new ArrayList<>();
        if (source.getServer() != null) {
            for (var level : source.getServer().getAllLevels()) {
                for (Entity e : level.getAllEntities()) {
                    if (e instanceof CustomNpcEntity npc && npc.getNpcId().equalsIgnoreCase(id)) {
                        result.add(npc);
                    }
                }
            }
        }
        return result;
    }

    private static int executeMove(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities, Vec3 pos, double speed) {
        List<CustomNpcEntity> npcs = filterNpcs(entities);
        for (CustomNpcEntity npc : npcs) {
            npc.setCommandedFollowPlayer(null);
            npc.getNavigation().moveTo(pos.x, pos.y, pos.z, speed);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Ordered " + npcs.size() + " NPC(s) to move to ("
                + String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z) + ") at speed " + speed).withStyle(ChatFormatting.GREEN), true);
        return npcs.size();
    }

    private static int executeFollow(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities, ServerPlayer player) {
        List<CustomNpcEntity> npcs = filterNpcs(entities);
        for (CustomNpcEntity npc : npcs) {
            npc.setCommandedFollowPlayer(player.getUUID());
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Ordered " + npcs.size() + " NPC(s) to follow " + player.getScoreboardName())
                .withStyle(ChatFormatting.GREEN), true);
        return npcs.size();
    }

    private static int executeFollowStop(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities) {
        List<CustomNpcEntity> npcs = filterNpcs(entities);
        for (CustomNpcEntity npc : npcs) {
            npc.setCommandedFollowPlayer(null);
            npc.getNavigation().stop();
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Stopped following behavior for " + npcs.size() + " NPC(s).")
                .withStyle(ChatFormatting.AQUA), true);
        return npcs.size();
    }

    private static int executeDefend(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities, ServerPlayer player) {
        List<CustomNpcEntity> npcs = filterNpcs(entities);
        for (CustomNpcEntity npc : npcs) {
            npc.setCommandedDefendPlayer(player.getUUID());
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Ordered " + npcs.size() + " NPC(s) to defend " + player.getScoreboardName())
                .withStyle(ChatFormatting.GREEN), true);
        return npcs.size();
    }

    private static int executeDefendStop(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities) {
        List<CustomNpcEntity> npcs = filterNpcs(entities);
        for (CustomNpcEntity npc : npcs) {
            npc.setCommandedDefendPlayer(null);
            npc.setTarget(null);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Stopped defend behavior for " + npcs.size() + " NPC(s).")
                .withStyle(ChatFormatting.AQUA), true);
        return npcs.size();
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities) {
        List<CustomNpcEntity> npcs = filterNpcs(entities);
        for (CustomNpcEntity npc : npcs) {
            npc.clearCommandedBehaviors();
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Halted all tasks for " + npcs.size() + " NPC(s).")
                .withStyle(ChatFormatting.YELLOW), true);
        return npcs.size();
    }

    private static int executeLookPlayer(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities, ServerPlayer player) {
        List<CustomNpcEntity> npcs = filterNpcs(entities);
        for (CustomNpcEntity npc : npcs) {
            npc.getLookControl().setLookAt(player, 30.0F, 30.0F);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Ordered " + npcs.size() + " NPC(s) to look at " + player.getScoreboardName())
                .withStyle(ChatFormatting.GREEN), true);
        return npcs.size();
    }

    private static int executeLookPos(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities, Vec3 pos) {
        List<CustomNpcEntity> npcs = filterNpcs(entities);
        for (CustomNpcEntity npc : npcs) {
            npc.getLookControl().setLookAt(pos.x, pos.y, pos.z, 30.0F, 30.0F);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Ordered " + npcs.size() + " NPC(s) to look at ("
                + String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z) + ")").withStyle(ChatFormatting.GREEN), true);
        return npcs.size();
    }

    private static int executeAnim(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities, String animation) {
        List<CustomNpcEntity> npcs = filterNpcs(entities);
        for (CustomNpcEntity npc : npcs) {
            npc.playTriggeredAnimation(animation);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Played animation '" + animation + "' on " + npcs.size() + " NPC(s).")
                .withStyle(ChatFormatting.GREEN), true);
        return npcs.size();
    }

    private static int executeDialog(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities, String sequence) {
        List<CustomNpcEntity> npcs = filterNpcs(entities);
        ResourceLocation seqLoc = new ResourceLocation(sequence.trim());
        CommandSourceStack src = ctx.getSource();
        if (src.getPlayer() != null) {
            DialogManager.getInstance().startSequence(seqLoc, List.of(src.getPlayer()));
            src.sendSuccess(() -> Component.literal("Started dialog sequence '" + sequence + "' with NPC.").withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        return 0;
    }

    private static int executeTeleportPos(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities, Vec3 pos) {
        List<CustomNpcEntity> npcs = filterNpcs(entities);
        if (npcs.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("❌ No NPC target found."));
            return 0;
        }
        for (CustomNpcEntity npc : npcs) {
            npc.getNavigation().stop();
            npc.teleportTo(pos.x, pos.y, pos.z);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Teleported " + npcs.size() + " NPC(s) to ("
                + String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z) + ")").withStyle(ChatFormatting.GREEN), true);
        return npcs.size();
    }

    private static int executeTeleportEntity(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities, Entity destination) {
        List<CustomNpcEntity> npcs = filterNpcs(entities);
        if (npcs.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("❌ No NPC target found."));
            return 0;
        }
        for (CustomNpcEntity npc : npcs) {
            npc.getNavigation().stop();
            if (npc.level() != destination.level() && destination.level() instanceof net.minecraft.server.level.ServerLevel destLevel) {
                npc.changeDimension(destLevel);
            }
            npc.teleportTo(destination.getX(), destination.getY(), destination.getZ());
            npc.setYRot(destination.getYRot());
            npc.setXRot(destination.getXRot());
            npc.setYHeadRot(destination.getYRot());
        }
        String destName = destination.getDisplayName().getString();
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Teleported " + npcs.size() + " NPC(s) to " + destName)
                .withStyle(ChatFormatting.GREEN), true);
        return npcs.size();
    }

    private static int executeNametag(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities, boolean visible) {
        List<CustomNpcEntity> npcs = filterNpcs(entities);
        if (npcs.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("❌ No NPC target found."));
            return 0;
        }
        for (CustomNpcEntity npc : npcs) {
            npc.setShowNameTag(visible);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Set nametag visibility to " + visible + " for " + npcs.size() + " NPC(s).")
                .withStyle(ChatFormatting.GREEN), true);
        return npcs.size();
    }

    private static int executeSpawn(CommandContext<CommandSourceStack> ctx, String templateIdStr, Vec3 pos) {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation id = ResourceLocation.tryParse(templateIdStr.trim());
        if (id == null) {
            source.sendFailure(Component.literal("❌ Invalid template ID: " + templateIdStr));
            return 0;
        }

        net.dandare21.fracturedutils.npc.NpcDefinition def = net.dandare21.fracturedutils.npc.NpcDefinitionManager.getInstance().getDefinition(id);
        if (def == null) {
            source.sendFailure(Component.literal("❌ Unknown NPC template: " + templateIdStr));
            return 0;
        }

        CustomNpcEntity npc = net.dandare21.fracturedutils.puppet.registry.ModEntities.CUSTOM_NPC.get().create(source.getLevel());
        if (npc != null) {
            float yaw = source.getEntity() != null ? source.getEntity().getYRot() + 180.0F : 0.0F;
            npc.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
            npc.setDefinition(def, false);
            source.getLevel().addFreshEntity(npc);
            source.sendSuccess(() -> Component.literal("✓ Spawned NPC '" + def.name() + "' (" + id + ") at " +
                    String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z)).withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("❌ Failed to instantiate NPC entity."));
            return 0;
        }
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        var ids = net.dandare21.fracturedutils.npc.NpcDefinitionManager.getInstance().getAllIds();
        if (ids.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No NPC templates loaded yet.").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal("✦ Loaded NPC Templates (" + ids.size() + "):").withStyle(ChatFormatting.AQUA), false);
        for (ResourceLocation id : ids) {
            var def = net.dandare21.fracturedutils.npc.NpcDefinitionManager.getInstance().getDefinition(id);
            String name = def != null ? def.name() : id.toString();
            src.sendSuccess(() -> Component.literal(" • ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(id.toString()).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" (" + name + ")").withStyle(ChatFormatting.WHITE)), false);
        }
        return ids.size();
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getServer() != null) {
            src.getServer().getPackRepository().reload();
            src.sendSuccess(() -> Component.literal("✓ Reloaded NPC definitions and DataPacks.").withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        return 0;
    }
}
