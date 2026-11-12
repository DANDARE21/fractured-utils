package net.dandare21.fracturedutils.region.network;

import net.dandare21.fracturedutils.region.RegionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SDeleteRegionPacket {

    private final String regionId;

    public C2SDeleteRegionPacket(String regionId) {
        this.regionId = regionId;
    }

    public C2SDeleteRegionPacket(FriendlyByteBuf buf) {
        this.regionId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(regionId != null ? regionId : "");
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null && sender.hasPermissions(2)) {
                boolean deleted = RegionManager.getInstance().deleteRegion(regionId);
                if (deleted) {
                    sender.displayClientMessage(Component.literal("§c[Region Editor] Deleted region: §f" + regionId), false);
                } else {
                    sender.displayClientMessage(Component.literal("§e[Region Editor] Region not found: §f" + regionId), false);
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}

