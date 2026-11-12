package net.dandare21.fracturedutils.region.network;

import net.dandare21.fracturedutils.region.client.ClientRegionData;
import net.dandare21.fracturedutils.region.compat.xaero.XaeroMapIntegration;
import net.dandare21.fracturedutils.region.data.Region;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class S2CSyncRegionsPacket {

    private final List<Region> regions;
    private final Set<String> activeFlags;

    public S2CSyncRegionsPacket(List<Region> regions, Set<String> activeFlags) {
        this.regions = regions != null ? regions : new ArrayList<>();
        this.activeFlags = activeFlags != null ? activeFlags : new HashSet<>();
    }

    public S2CSyncRegionsPacket(FriendlyByteBuf buf) {
        int regCount = buf.readVarInt();
        this.regions = new ArrayList<>(regCount);
        for (int i = 0; i < regCount; i++) {
            this.regions.add(Region.decode(buf));
        }

        int flagCount = buf.readVarInt();
        this.activeFlags = new HashSet<>(flagCount);
        for (int i = 0; i < flagCount; i++) {
            this.activeFlags.add(buf.readUtf());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(regions.size());
        for (Region r : regions) {
            r.encode(buf);
        }

        buf.writeVarInt(activeFlags.size());
        for (String flag : activeFlags) {
            buf.writeUtf(flag);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientRegionData.getInstance().update(regions, activeFlags);
                if (ModList.get().isLoaded("xaeroworldmap")) {
                    XaeroMapIntegration.onClientRegionsSynced();
                }
            });
        });
        ctx.setPacketHandled(true);
    }
}

