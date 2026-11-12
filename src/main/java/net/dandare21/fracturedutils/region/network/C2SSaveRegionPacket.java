package net.dandare21.fracturedutils.region.network;

import net.dandare21.fracturedutils.region.RegionManager;
import net.dandare21.fracturedutils.region.data.Region;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SSaveRegionPacket {

    private final Region region;
    private final String previousId;

    public C2SSaveRegionPacket(Region region) {
        this(region, region != null ? region.getId() : "");
    }

    public C2SSaveRegionPacket(Region region, String previousId) {
        this.region = region;
        this.previousId = previousId != null ? previousId : (region != null ? region.getId() : "");
    }

    public C2SSaveRegionPacket(FriendlyByteBuf buf) {
        this.region = Region.decode(buf);
        this.previousId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        region.encode(buf);
        buf.writeUtf(previousId != null ? previousId : "");
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null && sender.hasPermissions(2)) {
                RegionManager.getInstance().saveRegion(region, previousId);
                String msg = "§a[Region Editor] Saved region: §f" + region.getName() + " §7(" + region.getId() + ")";
                if (previousId != null && !previousId.isEmpty() && !previousId.equals(region.getId())) {
                    msg += " §8[renamed from " + previousId + "]";
                }
                sender.displayClientMessage(Component.literal(msg), false);
            }
        });
        ctx.setPacketHandled(true);
    }
}

