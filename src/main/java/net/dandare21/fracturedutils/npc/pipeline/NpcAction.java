package net.dandare21.fracturedutils.npc.pipeline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.dandare21.fracturedutils.dialog.DialogManager;
import net.dandare21.fracturedutils.npc.entity.CustomNpcEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public record NpcAction(
        String type,
        Optional<String> dialogId,
        Optional<String> animation,
        Optional<String> target,
        Optional<Double> x,
        Optional<Double> y,
        Optional<Double> z,
        Optional<String> item,
        Optional<Integer> count,
        Optional<String> command
) {
    public static final Codec<NpcAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("type", "run_command").forGetter(NpcAction::type),
            Codec.STRING.optionalFieldOf("dialogId").forGetter(NpcAction::dialogId),
            Codec.STRING.optionalFieldOf("animation").forGetter(NpcAction::animation),
            Codec.STRING.optionalFieldOf("target").forGetter(NpcAction::target),
            Codec.DOUBLE.optionalFieldOf("x").forGetter(NpcAction::x),
            Codec.DOUBLE.optionalFieldOf("y").forGetter(NpcAction::y),
            Codec.DOUBLE.optionalFieldOf("z").forGetter(NpcAction::z),
            Codec.STRING.optionalFieldOf("item").forGetter(NpcAction::item),
            Codec.INT.optionalFieldOf("count").forGetter(NpcAction::count),
            Codec.STRING.optionalFieldOf("command").forGetter(NpcAction::command)
    ).apply(instance, NpcAction::new));

    public void execute(CustomNpcEntity npc, ServerPlayer player) {
        if (npc == null || npc.level().isClientSide) return;
        String t = type.toLowerCase();
        switch (t) {
            case "start_dialog" -> {
                dialogId.ifPresent(idStr -> {
                    ResourceLocation id = new ResourceLocation(idStr);
                    if (player != null) {
                        DialogManager.getInstance().startSequence(id, List.of(player));
                    }
                });
            }
            case "play_animation" -> {
                animation.ifPresent(anim -> {
                    npc.playTriggeredAnimation(anim);
                });
            }
            case "look_at" -> {
                if (player != null) {
                    npc.getLookControl().setLookAt(player, 30.0F, 30.0F);
                }
            }
            case "move_to" -> {
                if (x.isPresent() && y.isPresent() && z.isPresent()) {
                    npc.getNavigation().moveTo(x.get(), y.get(), z.get(), 1.0);
                }
            }
            case "give_item" -> {
                if (player != null && item.isPresent()) {
                    ResourceLocation itemId = new ResourceLocation(item.get());
                    Item it = BuiltInRegistries.ITEM.get(itemId);
                    if (it != null) {
                        player.getInventory().add(new ItemStack(it, count.orElse(1)));
                    }
                }
            }
            case "run_command" -> {
                command.ifPresent(cmd -> {
                    var server = npc.getServer();
                    if (server != null) {
                        String processedCmd = cmd;
                        if (player != null) {
                            processedCmd = processedCmd
                                    .replace("%player%", player.getScoreboardName())
                                    .replace("%player_uuid%", player.getStringUUID());
                        }
                        processedCmd = processedCmd
                                .replace("%npc_name%", npc.getDisplayName().getString())
                                .replace("%npc_id%", npc.getNpcId())
                                .replace("%x%", String.format("%.2f", npc.getX()))
                                .replace("%y%", String.format("%.2f", npc.getY()))
                                .replace("%z%", String.format("%.2f", npc.getZ()));

                        CommandSourceStack source = server.createCommandSourceStack()
                                .withSuppressedOutput()
                                .withPermission(4);
                        server.getCommands().performPrefixedCommand(source, processedCmd);
                    }
                });
            }
            case "despawn" -> {
                npc.discard();
            }
            default -> {}
        }
    }
}
