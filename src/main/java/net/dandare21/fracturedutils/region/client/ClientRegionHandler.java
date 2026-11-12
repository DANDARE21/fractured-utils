package net.dandare21.fracturedutils.region.client;

import net.dandare21.fracturedutils.region.data.Region;
import net.dandare21.fracturedutils.region.data.RegionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages client region state tracking, region transition detection, and anti-spam hysteresis.
 */
public class ClientRegionHandler {

    private static final Map<String, Long> LAST_BANNER_TIMES = new HashMap<>();
    private static final long BANNER_COOLDOWN_MS = 180_000; // 3-minute hysteresis

    private static String lastPrimaryRegionId = null;
    private static String lastMapZoneId = null;
    private static String lastCityId = null;
    private static String lastEventZoneId = null;

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        double px = player.getX();
        double pz = player.getZ();

        ClientRegionData data = ClientRegionData.getInstance();
        Region primary = data.findPrimaryRegionAt(px, pz);

        Region mapZone = null;
        Region city = null;
        Region eventZone = null;

        for (Region r : data.findAllRegionsAt(px, pz)) {
            if (r.getType() == RegionType.EVENT_ZONE && eventZone == null) {
                eventZone = r;
            } else if (r.getType() == RegionType.CITY && city == null) {
                city = r;
            } else if (r.getType() == RegionType.MAP_ZONE && mapZone == null) {
                mapZone = r;
            }
        }

        data.setCurrentPrimaryRegion(primary);
        data.setCurrentMapZone(mapZone);
        data.setCurrentCity(city);
        data.setCurrentEventZone(eventZone);

        String currentPrimaryId = primary != null ? primary.getId() : null;
        String currentMapZoneId = mapZone != null ? mapZone.getId() : null;
        String currentCityId = city != null ? city.getId() : null;
        String currentEventZoneId = eventZone != null ? eventZone.getId() : null;

        long now = System.currentTimeMillis();

        // 1. Check Macro-Region (MAP_ZONE) entry
        if (currentMapZoneId != null && !currentMapZoneId.equals(lastMapZoneId)) {
            long lastTime = LAST_BANNER_TIMES.getOrDefault(currentMapZoneId, 0L);
            if (now - lastTime > BANNER_COOLDOWN_MS) {
                LAST_BANNER_TIMES.put(currentMapZoneId, now);
                RegionHudOverlay.showBanner(mapZone);
            }
        }
        // 2. Check City / Event entry if primary wasn't already triggered as a map zone
        else if (primary != null && !primary.getId().equals(lastPrimaryRegionId) && primary.getType() != RegionType.MAP_ZONE) {
            long lastTime = LAST_BANNER_TIMES.getOrDefault(primary.getId(), 0L);
            if (now - lastTime > BANNER_COOLDOWN_MS) {
                LAST_BANNER_TIMES.put(primary.getId(), now);
                RegionHudOverlay.showBanner(primary);
            }
        }

        lastPrimaryRegionId = currentPrimaryId;
        lastMapZoneId = currentMapZoneId;
        lastCityId = currentCityId;
        lastEventZoneId = currentEventZoneId;
    }
}

