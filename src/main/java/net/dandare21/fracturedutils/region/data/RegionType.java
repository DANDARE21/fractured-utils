package net.dandare21.fracturedutils.region.data;

/**
 * Three-tier region hierarchy enum.
 * Tier 1: MAP_ZONE (Macro-Region, grand HUD title banners, ambient audio, map territory fills)
 * Tier 2: CITY (Districts/Settlements, mid-tier HUD sub-banners)
 * Tier 3: EVENT_ZONE (Event/Story Zones, access locks, red forcefield borders)
 */
public enum RegionType {
    MAP_ZONE(1, 0x00D0FF, "Macro-Region"),
    CITY(2, 0xFFBB00, "City / District"),
    EVENT_ZONE(3, 0xFF2255, "Story / Event");

    private final int tier;
    private final int defaultColor;
    private final String displayName;

    RegionType(int tier, int defaultColor, String displayName) {
        this.tier = tier;
        this.defaultColor = defaultColor;
        this.displayName = displayName;
    }

    public int getTier() {
        return tier;
    }

    public int getDefaultColor() {
        return defaultColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static RegionType fromString(String name) {
        for (RegionType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return MAP_ZONE;
    }
}

