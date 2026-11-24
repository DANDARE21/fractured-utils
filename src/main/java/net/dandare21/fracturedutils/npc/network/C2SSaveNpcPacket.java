package net.dandare21.fracturedutils.npc.network;

import net.dandare21.fracturedutils.npc.NpcDefinition;
import net.dandare21.fracturedutils.npc.entity.CustomNpcEntity;
import net.dandare21.fracturedutils.npc.item.NpcWandItem;
import net.dandare21.fracturedutils.npc.pack.GeneratedPackManager;
import net.dandare21.fracturedutils.puppet.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SSaveNpcPacket {
    private final int targetEntityId;
    private final NpcDefinition definition;
    private final boolean saveToDatapack;
    private final boolean applyToEntity;
    private final boolean spawnInWorld;

    public C2SSaveNpcPacket(int targetEntityId, NpcDefinition definition, boolean saveToDatapack, boolean applyToEntity, boolean spawnInWorld) {
        this.targetEntityId = targetEntityId;
        this.definition = definition;
        this.saveToDatapack = saveToDatapack;
        this.applyToEntity = applyToEntity;
        this.spawnInWorld = spawnInWorld;
    }

    public C2SSaveNpcPacket(FriendlyByteBuf buf) {
        this.targetEntityId = buf.readVarInt();
        this.definition = NpcDefinition.decode(buf);
        this.saveToDatapack = buf.readBoolean();
        this.applyToEntity = buf.readBoolean();
        this.spawnInWorld = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(targetEntityId);
        definition.encode(buf);
        buf.writeBoolean(saveToDatapack);
        buf.writeBoolean(applyToEntity);
        buf.writeBoolean(spawnInWorld);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            if (!sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("❌ Permission Denied: OP level 2+ required to save NPCs.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            if (saveToDatapack) {
                boolean saved = GeneratedPackManager.saveNpcTemplate(sender.getServer(), definition);
                if (saved) {
                    sender.sendSystemMessage(Component.literal("✓ Saved NPC template '" + definition.id() + "' to generated DataPack.")
                            .withStyle(ChatFormatting.GREEN));
                } else {
                    sender.sendSystemMessage(Component.literal("❌ Failed to save NPC template '" + definition.id() + "'.")
                            .withStyle(ChatFormatting.RED));
                }
            }

            // Always update wand in player hand if holding one
            ItemStack held = sender.getMainHandItem();
            if (held.getItem() instanceof NpcWandItem) {
                held.getOrCreateTag().put("LastNpcDefinition", definition.toNbt());
                held.getOrCreateTag().putString("LastNpcName", definition.name());
            }

            if (applyToEntity && targetEntityId >= 0) {
                Entity target = sender.serverLevel().getEntity(targetEntityId);
                if (target instanceof CustomNpcEntity npc) {
                    npc.setDefinition(definition, true);
                    sender.sendSystemMessage(Component.literal("✓ Applied customizations to NPC in world.")
                            .withStyle(ChatFormatting.AQUA));
                }
            }

            if (spawnInWorld) {
                Vec3 spawnPos;
                HitResult hit = sender.pick(5.0D, 0.0F, false);
                if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit) {
                    var bp = bHit.getBlockPos().relative(bHit.getDirection());
                    spawnPos = new Vec3(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                } else {
                    Vec3 look = sender.getLookAngle();
                    spawnPos = sender.position().add(look.x * 2.0, 0, look.z * 2.0);
                }

                CustomNpcEntity npc = ModEntities.CUSTOM_NPC.get().create(sender.serverLevel());
                if (npc != null) {
                    npc.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, sender.getYRot() + 180.0F, 0.0F);
                    npc.setDefinition(definition, true);
                    sender.serverLevel().addFreshEntity(npc);
                    sender.sendSystemMessage(Component.literal("✓ Spawned NPC '" + definition.name() + "' (" + definition.id() + ")!")
                            .withStyle(ChatFormatting.GREEN));
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
