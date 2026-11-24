package net.dandare21.fracturedutils.npc;

import java.util.Locale;

public enum NpcModelType {
    GECKOLIB,
    PLAYER;

    public static NpcModelType fromString(String str) {
        if (str == null || str.isBlank()) return GECKOLIB;
        try {
            return NpcModelType.valueOf(str.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GECKOLIB;
        }
    }
}
