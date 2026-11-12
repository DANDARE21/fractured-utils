package net.dandare21.fracturedutils.region.data;

import net.dandare21.fracturedutils.FracturedUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative persistent data for all spatial regions and global access flags.
 * Stored in the overworld at data/fractured_utils_regions.dat.
 */
public class WorldRegionSavedData extends SavedData {

    public static final String FILE_NAME = FracturedUtils.MOD_ID + "_regions";

    private final Map<String, Region> regions = new LinkedHashMap<>();
    private final Set<String> activeFlags = new HashSet<>();

    public WorldRegionSavedData() {
    }

    public static WorldRegionSavedData load(CompoundTag tag) {
        WorldRegionSavedData data = new WorldRegionSavedData();

        if (tag.contains("regions", Tag.TAG_LIST)) {
            ListTag list = tag.getList("regions", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                Region region = Region.fromNbt(list.getCompound(i));
                data.regions.put(region.getId(), region);
            }
        }

        if (tag.contains("activeFlags", Tag.TAG_LIST)) {
            ListTag list = tag.getList("activeFlags", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                data.activeFlags.add(list.getString(i));
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Region region : regions.values()) {
            list.add(region.toNbt());
        }
        tag.put("regions", list);

        ListTag flagsList = new ListTag();
        for (String flag : activeFlags) {
            flagsList.add(StringTag.valueOf(flag));
        }
        tag.put("activeFlags", flagsList);

        return tag;
    }

    public static WorldRegionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                WorldRegionSavedData::load,
                WorldRegionSavedData::new,
                FILE_NAME
        );
    }

    public Map<String, Region> getRegions() {
        return Collections.unmodifiableMap(regions);
    }

    public List<Region> getAllRegions() {
        return new ArrayList<>(regions.values());
    }

    public Region getRegion(String id) {
        return regions.get(id);
    }

    public void addOrUpdateRegion(Region region) {
        regions.put(region.getId(), region);
        setDirty();
    }

    public void renameOrUpdateRegion(String oldId, Region updatedRegion) {
        if (oldId != null && !oldId.isEmpty() && !oldId.equals(updatedRegion.getId())) {
            regions.remove(oldId);
        }
        regions.put(updatedRegion.getId(), updatedRegion);
        setDirty();
    }

    public boolean removeRegion(String id) {
        if (regions.remove(id) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public boolean setRegionLocked(String id, boolean locked) {
        Region r = regions.get(id);
        if (r != null) {
            r.setLocked(locked);
            setDirty();
            return true;
        }
        return false;
    }

    public boolean isRegionLocked(Region region) {
        if (region == null) return false;
        if (region.isLocked()) {
            return true;
        }
        if (region.getRequiredFlag() != null && !region.getRequiredFlag().isEmpty()) {
            return !activeFlags.contains(region.getRequiredFlag());
        }
        return false;
    }

    public void setFlag(String flag, boolean active) {
        if (flag == null || flag.isEmpty()) return;
        boolean changed = active ? activeFlags.add(flag) : activeFlags.remove(flag);
        if (changed) {
            setDirty();
        }
    }

    public boolean isFlagActive(String flag) {
        return activeFlags.contains(flag);
    }

    public Set<String> getActiveFlags() {
        return Collections.unmodifiableSet(activeFlags);
    }

    public void clearFlags() {
        if (!activeFlags.isEmpty()) {
            activeFlags.clear();
            setDirty();
        }
    }

    /**
     * Resolves the primary active region at (x, z) following strict hierarchy priority:
     * EVENT_ZONE -> CITY -> MAP_ZONE.
     */
    public Region findPrimaryRegionAt(double x, double z) {
        Region bestEvent = null;
        Region bestCity = null;
        Region bestMapZone = null;

        for (Region r : regions.values()) {
            if (r.getPolygon() != null && r.getPolygon().contains(x, z)) {
                if (r.getType() == RegionType.EVENT_ZONE) {
                    if (bestEvent == null || (r.getPolygon() != null && bestEvent.getPolygon() != null && r.getPolygon().getArea() < bestEvent.getPolygon().getArea())) {
                        bestEvent = r;
                    }
                } else if (r.getType() == RegionType.CITY) {
                    if (bestCity == null || (r.getPolygon() != null && bestCity.getPolygon() != null && r.getPolygon().getArea() < bestCity.getPolygon().getArea())) {
                        bestCity = r;
                    }
                } else if (r.getType() == RegionType.MAP_ZONE) {
                    if (bestMapZone == null || (r.getPolygon() != null && bestMapZone.getPolygon() != null && r.getPolygon().getArea() < bestMapZone.getPolygon().getArea())) {
                        bestMapZone = r;
                    }
                }
            }
        }

        if (bestEvent != null) return bestEvent;
        if (bestCity != null) return bestCity;
        return bestMapZone;
    }

    /**
     * Finds all regions containing (x, z) sorted by tier descending (EVENT_ZONE -> CITY -> MAP_ZONE),
     * and by smaller area first for nested regions within the same tier.
     */
    public List<Region> findAllRegionsAt(double x, double z) {
        List<Region> result = new ArrayList<>();
        for (Region r : regions.values()) {
            if (r.getPolygon() != null && r.getPolygon().contains(x, z)) {
                result.add(r);
            }
        }
        result.sort((a, b) -> {
            int tierComp = Integer.compare(b.getType().getTier(), a.getType().getTier());
            if (tierComp != 0) return tierComp;
            double areaA = a.getPolygon() != null ? a.getPolygon().getArea() : 0.0;
            double areaB = b.getPolygon() != null ? b.getPolygon().getArea() : 0.0;
            return Double.compare(areaA, areaB);
        });
        return result;
    }

    /**
     * Checks if the position is inside any locked region. Returns the locked region (prioritizing EVENT_ZONE).
     */
    public Region findLockedZoneAt(double x, double z) {
        List<Region> atPos = findAllRegionsAt(x, z);
        for (Region r : atPos) {
            if (isRegionLocked(r)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Snaps coordinates to any vertex across all saved regions within the threshold.
     */
    public Vec2d snapVertex(double x, double z, double threshold) {
        for (Region r : regions.values()) {
            if (r.getPolygon() != null) {
                Vec2d snapped = r.getPolygon().snapVertex(x, z, threshold);
                if (snapped != null) {
                    return snapped;
                }
            }
        }
        return null;
    }
}

