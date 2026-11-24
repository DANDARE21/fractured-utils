package net.dandare21.fracturedutils.npc.item;

import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.npc.NpcDefinition;
import net.dandare21.fracturedutils.npc.entity.CustomNpcEntity;
import net.dandare21.fracturedutils.npc.network.S2COpenNpcStudioPacket;
import net.dandare21.fracturedutils.puppet.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class NpcWandItem extends Item {
    public NpcWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof CustomNpcEntity npc) {
            if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (!serverPlayer.hasPermissions(2)) {
                    serverPlayer.sendSystemMessage(Component.literal("❌ Permission Denied: OP level 2+ required to edit NPCs.")
                            .withStyle(ChatFormatting.RED));
                    return InteractionResult.FAIL;
                }

                NpcDefinition def = npc.getDefinition();
                stack.getOrCreateTag().put("LastNpcDefinition", def.toNbt());
                stack.getOrCreateTag().putString("LastNpcName", def.name());

                ModMessages.sendToPlayer(new S2COpenNpcStudioPacket(npc.getId(), def), serverPlayer);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        return super.interactLivingEntity(stack, player, target, hand);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!serverPlayer.hasPermissions(2)) {
                serverPlayer.sendSystemMessage(Component.literal("❌ Permission Denied: OP level 2+ required to create NPCs.")
                        .withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }

            ResourceLocation newId = new ResourceLocation("fractured_utils", "npc_" + (System.currentTimeMillis() % 100000));
            NpcDefinition def = NpcDefinition.createDefault(newId);
            ModMessages.sendToPlayer(new S2COpenNpcStudioPacket(-1, def), serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();

        if (player.isCrouching()) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (!serverPlayer.hasPermissions(2)) {
                    serverPlayer.sendSystemMessage(Component.literal("❌ Permission Denied: OP level 2+ required to spawn NPCs.")
                            .withStyle(ChatFormatting.RED));
                    return InteractionResult.FAIL;
                }

                if (stack.hasTag() && stack.getTag().contains("LastNpcDefinition")) {
                    CompoundTag defTag = stack.getTag().getCompound("LastNpcDefinition");
                    Optional<NpcDefinition> defOpt = NpcDefinition.fromNbt(defTag);
                    if (defOpt.isPresent()) {
                        NpcDefinition def = defOpt.get();
                        var blockPos = context.getClickedPos().relative(context.getClickedFace());
                        CustomNpcEntity npc = ModEntities.CUSTOM_NPC.get().create(level);
                        if (npc != null) {
                            npc.moveTo(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
                            npc.setDefinition(def, true);
                            level.addFreshEntity(npc);
                            player.sendSystemMessage(Component.literal("✓ Placed cloned NPC: " + def.name())
                                    .withStyle(ChatFormatting.GREEN));
                        }
                    }
                } else {
                    player.sendSystemMessage(Component.literal("❌ Wand clipboard is empty! Right-click an NPC or create one in Studio first.")
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            // Right-click block (not crouching): Open Studio to create & spawn NPC
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (!serverPlayer.hasPermissions(2)) {
                    serverPlayer.sendSystemMessage(Component.literal("❌ Permission Denied: OP level 2+ required to create NPCs.")
                            .withStyle(ChatFormatting.RED));
                    return InteractionResult.FAIL;
                }

                ResourceLocation newId = new ResourceLocation("fractured_utils", "npc_" + (System.currentTimeMillis() % 100000));
                NpcDefinition def = NpcDefinition.createDefault(newId);
                ModMessages.sendToPlayer(new S2COpenNpcStudioPacket(-1, def), serverPlayer);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("✦ NPC Studio Wand").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        tooltip.add(Component.literal("• Right-Click NPC: Edit in Studio").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("• Right-Click Air / Block: Open Studio & Spawn").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("• Shift + Right-Click Block: Clone / Place Copied NPC").withStyle(ChatFormatting.GREEN));

        if (stack.hasTag() && stack.getTag().contains("LastNpcName")) {
            String last = stack.getTag().getString("LastNpcName");
            tooltip.add(Component.literal("Clipboard: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(last).withStyle(ChatFormatting.WHITE)));
        }
    }
}
