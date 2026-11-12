package net.dandare21.fracturedutils.region;

import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.region.data.Region;
import net.dandare21.fracturedutils.region.data.WorldRegionSavedData;
import net.dandare21.fracturedutils.region.network.S2CSyncRegionsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Server-side manager singleton managing region lifecycle, spatial queries, and client network synchronization.
 */
public class RegionManager {

    private static final RegionManager INSTANCE = new RegionManager();

    private MinecraftServer server;

    private RegionManager() {
    }

    public static RegionManager getInstance() {
        return INSTANCE;
    }

    public void onServerStarting(MinecraftServer server) {
        this.server = server;
        // Preload saved data
        WorldRegionSavedData.get(server);
    }

    public void onServerStopping(MinecraftServer server) {
        this.server = null;
    }

    public WorldRegionSavedData getSavedData() {
        if (server == null) return null;
        return WorldRegionSavedData.get(server);
    }

    public void onPlayerJoin(ServerPlayer player) {
        syncToPlayer(player);
    }

    public void syncToPlayer(ServerPlayer player) {
        if (server == null) return;
        WorldRegionSavedData data = WorldRegionSavedData.get(server);
        ModMessages.sendToPlayer(new S2CSyncRegionsPacket(data.getAllRegions(), data.getActiveFlags()), player);
    }

    public void syncToAll() {
        if (server == null) return;
        WorldRegionSavedData data = WorldRegionSavedData.get(server);
        ModMessages.sendToAllPlayers(new S2CSyncRegionsPacket(data.getAllRegions(), data.getActiveFlags()), server);
    }

    public void saveRegion(Region region) {
        saveRegion(region, region != null ? region.getId() : "");
    }

    public void saveRegion(Region region, String previousId) {
        if (server == null || region == null) return;
        WorldRegionSavedData data = WorldRegionSavedData.get(server);
        data.renameOrUpdateRegion(previousId, region);
        syncToAll();
    }

    public boolean deleteRegion(String id) {
        if (server == null) return false;
        WorldRegionSavedData data = WorldRegionSavedData.get(server);
        boolean removed = data.removeRegion(id);
        if (removed) {
            syncToAll();
        }
        return removed;
    }

    public boolean setRegionLocked(String id, boolean locked) {
        if (server == null) return false;
        WorldRegionSavedData data = WorldRegionSavedData.get(server);
        boolean changed = data.setRegionLocked(id, locked);
        if (changed) {
            syncToAll();
        }
        return changed;
    }

    public void setFlag(String flag, boolean active) {
        if (server == null) return;
        WorldRegionSavedData data = WorldRegionSavedData.get(server);
        data.setFlag(flag, active);
        syncToAll();
    }
}

