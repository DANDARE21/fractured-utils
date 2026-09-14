package net.dandare21.fracturedutils.network.packet;

import net.dandare21.fracturedutils.puppet.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class C2SSpawnPuppetPacket {
    private final String entityTypeId;
    private final String customName;
    private final String tag;
    private final double x;
    private final double y;
    private final double z;

    public C2SSpawnPuppetPacket(String entityTypeId, String customName, String tag, double x, double y, double z) {
        this.entityTypeId = entityTypeId != null ? entityTypeId : "fracturedutils:void_herald";
        this.customName = customName != null ? customName : "Puppet Actor";
        this.tag = tag != null ? tag : "puppet_actor";
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public C2SSpawnPuppetPacket(FriendlyByteBuf buf) {
        this.entityTypeId = buf.readUtf(256);
        this.customName = buf.readUtf(256);
        this.tag = buf.readUtf(256);
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(entityTypeId, 256);
        buf.writeUtf(customName, 256);
        buf.writeUtf(tag, 256);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            boolean isSingleplayer = player.server.isSingleplayer();
            if (!player.hasPermissions(2) && !isSingleplayer) {
                player.sendSystemMessage(Component.literal("❌ Permission Denied: Operator level 2+ required to spawn puppet entities.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            EntityType<?> type = null;
            if (entityTypeId.equalsIgnoreCase("void_herald") || entityTypeId.equalsIgnoreCase("fracturedutils:void_herald") || entityTypeId.isBlank()) {
                type = ModEntities.VOID_HERALD.get();
            } else {
                try {
                    ResourceLocation rl = ResourceLocation.tryParse(entityTypeId);
                    if (rl != null) {
                        type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
                    }
                } catch (Exception ignored) {}
            }

            if (type == null) {
                type = ModEntities.VOID_HERALD.get();
            }

            Entity entity = type.create(player.serverLevel());
            if (entity instanceof Mob mob) {
                mob.moveTo(x, y, z, player.getYRot(), 0.0f);
                if (customName != null && !customName.isBlank()) {
                    mob.setCustomName(Component.literal(customName));
                    mob.setCustomNameVisible(true);
                }
                if (tag != null && !tag.isBlank()) {
                    mob.addTag(tag);
                }
                mob.addTag("puppet_actor");
                player.serverLevel().addFreshEntity(mob);

                player.sendSystemMessage(Component.literal("🎭 Spawned Puppet Entity '" + mob.getDisplayName().getString() + "' at ("
                        + String.format("%.1f, %.1f, %.1f", x, y, z) + ") [UUID: " + mob.getStringUUID() + "]")
                        .withStyle(ChatFormatting.GREEN));
            } else {
                player.sendSystemMessage(Component.literal("❌ Failed to spawn mob for entity type: " + entityTypeId)
                        .withStyle(ChatFormatting.RED));
            }
        });
        ctx.setPacketHandled(true);
    }
}
