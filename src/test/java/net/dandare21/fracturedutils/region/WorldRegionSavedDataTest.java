package net.dandare21.fracturedutils.region;

import net.dandare21.fracturedutils.region.data.Polygon2D;
import net.dandare21.fracturedutils.region.data.Region;
import net.dandare21.fracturedutils.region.data.RegionType;
import net.dandare21.fracturedutils.region.data.Vec2d;
import net.dandare21.fracturedutils.region.data.WorldRegionSavedData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WorldRegionSavedDataTest {

    @Test
    public void testHierarchyPriorityResolution() {
        WorldRegionSavedData data = new WorldRegionSavedData();

        // 1. Macro-Region: 0..100, 0..100
        Region mapZone = new Region(
                "ashen_coast", "The Ashen Coast", RegionType.MAP_ZONE, "",
                new Polygon2D(List.of(
                        new Vec2d(0, 0),
                        new Vec2d(100, 0),
                        new Vec2d(100, 100),
                        new Vec2d(0, 100)
                )),
                false, "", "", 0x00D0FF, "", ""
        );
        data.addOrUpdateRegion(mapZone);

        // 2. City: 20..60, 20..60 (inside Map Zone)
        Region city = new Region(
                "harbor_city", "Harbor City", RegionType.CITY, "ashen_coast",
                new Polygon2D(List.of(
                        new Vec2d(20, 20),
                        new Vec2d(60, 20),
                        new Vec2d(60, 60),
                        new Vec2d(20, 60)
                )),
                false, "", "", 0xFFBB00, "", ""
        );
        data.addOrUpdateRegion(city);

        // 3. Event Zone: 30..40, 30..40 (inside City and Map Zone)
        Region eventZone = new Region(
                "sunken_vault", "Sunken Vault", RegionType.EVENT_ZONE, "harbor_city",
                new Polygon2D(List.of(
                        new Vec2d(30, 30),
                        new Vec2d(40, 30),
                        new Vec2d(40, 40),
                        new Vec2d(30, 40)
                )),
                true, "", "The vault door is sealed.", 0xFF2255, "", ""
        );
        data.addOrUpdateRegion(eventZone);

        // Position inside all three (35, 35) -> Priority must return EVENT_ZONE!
        Region primaryAtVault = data.findPrimaryRegionAt(35, 35);
        assertNotNull(primaryAtVault);
        assertEquals("sunken_vault", primaryAtVault.getId(), "EVENT_ZONE must take highest priority");

        // Position inside City and MapZone (25, 25) -> Priority must return CITY!
        Region primaryAtCity = data.findPrimaryRegionAt(25, 25);
        assertNotNull(primaryAtCity);
        assertEquals("harbor_city", primaryAtCity.getId(), "CITY must take priority over MAP_ZONE");

        // Position inside only MapZone (5, 5) -> Priority must return MAP_ZONE!
        Region primaryAtCoast = data.findPrimaryRegionAt(5, 5);
        assertNotNull(primaryAtCoast);
        assertEquals("ashen_coast", primaryAtCoast.getId(), "MAP_ZONE returned when not in child");

        // Position outside all (150, 150) -> null
        assertNull(data.findPrimaryRegionAt(150, 150));
    }

    @Test
    public void testLockAndFlagEvaluation() {
        WorldRegionSavedData data = new WorldRegionSavedData();

        Region flagLockedRegion = new Region(
                "sanctum", "Forbidden Sanctum", RegionType.EVENT_ZONE, "",
                new Polygon2D(List.of(
                        new Vec2d(0, 0),
                        new Vec2d(10, 0),
                        new Vec2d(10, 10),
                        new Vec2d(0, 10)
                )),
                false, "boss_slain", "A barrier bars the way.", 0xFF2255, "", ""
        );
        data.addOrUpdateRegion(flagLockedRegion);

        // Flag "boss_slain" is not active yet -> locked!
        assertTrue(data.isRegionLocked(flagLockedRegion));
        assertNotNull(data.findLockedZoneAt(5, 5));

        // Activate flag "boss_slain"
        data.setFlag("boss_slain", true);
        assertTrue(data.isFlagActive("boss_slain"));

        // Now region is unlocked!
        assertFalse(data.isRegionLocked(flagLockedRegion));
        assertNull(data.findLockedZoneAt(5, 5));

        // Set direct lock = true -> remains locked regardless of flag!
        data.setRegionLocked("sanctum", true);
        assertTrue(data.isRegionLocked(flagLockedRegion));
        assertNotNull(data.findLockedZoneAt(5, 5));
    }

    @Test
    public void testRenameOrUpdateRegion() {
        WorldRegionSavedData data = new WorldRegionSavedData();

        Region initial = new Region(
                "temp_id_1", "Draft Region", RegionType.MAP_ZONE, "",
                new Polygon2D(List.of(
                        new Vec2d(0, 0),
                        new Vec2d(50, 0),
                        new Vec2d(50, 50),
                        new Vec2d(0, 50)
                )),
                false, "", "", 0x00D0FF, "", ""
        );
        data.addOrUpdateRegion(initial);
        assertNotNull(data.getRegion("temp_id_1"));

        // Rename from temp_id_1 to final_id
        Region renamed = new Region(
                "final_id", "Final Region", RegionType.MAP_ZONE, "",
                initial.getPolygon(), false, "", "", 0x00D0FF, "", ""
        );
        data.renameOrUpdateRegion("temp_id_1", renamed);

        assertNull(data.getRegion("temp_id_1"), "Old ID must be removed");
        assertNotNull(data.getRegion("final_id"), "New ID must exist");
        assertEquals("Final Region", data.getRegion("final_id").getName());
        assertEquals(1, data.getAllRegions().size(), "There must be exactly 1 region, not duplicated");
    }

    @Test
    public void testSameTierNestedRegionSelection() {
        WorldRegionSavedData data = new WorldRegionSavedData();

        // Large city (area 100x100 = 10000)
        Region outerCity = new Region(
                "outer_city", "Outer City", RegionType.CITY, "",
                new Polygon2D(List.of(
                        new Vec2d(0, 0),
                        new Vec2d(100, 0),
                        new Vec2d(100, 100),
                        new Vec2d(0, 100)
                )),
                false, "", "", 0xFFBB00, "", ""
        );
        // Small inner district (area 20x20 = 400)
        Region innerDistrict = new Region(
                "inner_district", "Inner District", RegionType.CITY, "outer_city",
                new Polygon2D(List.of(
                        new Vec2d(40, 40),
                        new Vec2d(60, 40),
                        new Vec2d(60, 60),
                        new Vec2d(40, 60)
                )),
                false, "", "", 0xFFBB00, "", ""
        );

        data.addOrUpdateRegion(outerCity);
        data.addOrUpdateRegion(innerDistrict);

        // Position inside inner district (50, 50) -> must select innerDistrict because of smaller area!
        Region selected = data.findPrimaryRegionAt(50, 50);
        assertNotNull(selected);
        assertEquals("inner_district", selected.getId(), "Smaller nested region within same tier must be prioritized");
    }
}

