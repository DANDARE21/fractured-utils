package net.dandare21.fracturedutils.npc;

import java.util.Locale;

public enum NpcMovementMode {
    STATIONARY,
    WANDER,
    PATROL;

    public static NpcMovementMode fromString(String str) {
        if (str == null || str.isBlank()) return STATIONARY;
        try {
            return NpcMovementMode.valueOf(str.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return STATIONARY;
        }
    }
}
