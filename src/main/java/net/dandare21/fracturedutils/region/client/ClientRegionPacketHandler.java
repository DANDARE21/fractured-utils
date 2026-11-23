package net.dandare21.fracturedutils.region.client;

import net.dandare21.fracturedutils.region.compat.xaero.XaeroMapIntegration;
import net.dandare21.fracturedutils.region.data.Region;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class ClientRegionPacketHandler {

    public static void handle(List<Region> regions, Set<String> activeFlags) {
        ClientRegionData.getInstance().update(regions, activeFlags);
        if (ModList.get().isLoaded("xaeroworldmap")) {
            XaeroMapIntegration.onClientRegionsSynced();
        }
    }
}