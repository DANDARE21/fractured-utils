package net.dandare21.fracturedutils.region.client;

import net.dandare21.fracturedutils.region.data.Region;
import net.dandare21.fracturedutils.region.data.RegionType;
import net.dandare21.fracturedutils.region.data.Vec2d;

import java.util.*;

/**
 * Client-side synchronized repository of all regions and active flags.
 */
public class ClientRegionData {

    private static final ClientRegionData INSTANCE = new ClientRegionData();

    private final Map<String, Region> regions = new LinkedHashMap<>();
    private final Set<String> activeFlags = new HashSet<>();

    private Region currentPrimaryRegion = null;
    private Region currentMapZone = null;
    private Region currentCity = null;
    private Region currentEventZone = null;

    private ClientRegionData() {
    }

    public static ClientRegionData getInstance() {
        return INSTANCE;
    }

    public synchronized void update(List<Region> newRegions, Set<String> newFlags) {
        regions.clear();
        for (Region r : newRegions) {
            regions.put(r.getId(), r);
        }
        activeFlags.clear();
        activeFlags.addAll(newFlags);
    }

    public synchronized Map<String, Region> getRegions() {
        return Collections.unmodifiableMap(regions);
    }

    public synchronized List<Region> getAllRegions() {
        return new ArrayList<>(regions.values());
    }

    public synchronized Region getRegion(String id) {
        return regions.get(id);
    }

    public synchronized Set<String> getActiveFlags() {
        return Collections.unmodifiableSet(activeFlags);
    }

    public synchronized boolean isRegionLocked(Region region) {
        if (region == null) return false;
        if (region.isLocked()) return true;
        if (region.getRequiredFlag() != null && !region.getRequiredFlag().isEmpty()) {
            return !activeFlags.contains(region.getRequiredFlag());
        }
        return false;
    }

    public synchronized Region findPrimaryRegionAt(double x, double z) {
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

    public synchronized List<Region> findAllRegionsAt(double x, double z) {
        List<Region> list = new ArrayList<>();
        for (Region r : regions.values()) {
            if (r.getPolygon() != null && r.getPolygon().contains(x, z)) {
                list.add(r);
            }
        }
        list.sort((a, b) -> {
            int tierComp = Integer.compare(b.getType().getTier(), a.getType().getTier());
            if (tierComp != 0) return tierComp;
            double areaA = a.getPolygon() != null ? a.getPolygon().getArea() : 0.0;
            double areaB = b.getPolygon() != null ? b.getPolygon().getArea() : 0.0;
            return Double.compare(areaA, areaB);
        });
        return list;
    }

    public synchronized Region findLockedZoneAt(double x, double z) {
        List<Region> atPos = findAllRegionsAt(x, z);
        for (Region r : atPos) {
            if (isRegionLocked(r)) {
                return r;
            }
        }
        return null;
    }

    public synchronized Vec2d snapVertex(double x, double z, double threshold) {
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

    public Region getCurrentPrimaryRegion() {
        return currentPrimaryRegion;
    }

    public void setCurrentPrimaryRegion(Region currentPrimaryRegion) {
        this.currentPrimaryRegion = currentPrimaryRegion;
    }

    public Region getCurrentMapZone() {
        return currentMapZone;
    }

    public void setCurrentMapZone(Region currentMapZone) {
        this.currentMapZone = currentMapZone;
    }

    public Region getCurrentCity() {
        return currentCity;
    }

    public void setCurrentCity(Region currentCity) {
        this.currentCity = currentCity;
    }

    public Region getCurrentEventZone() {
        return currentEventZone;
    }

    public void setCurrentEventZone(Region currentEventZone) {
        this.currentEventZone = currentEventZone;
    }
}

