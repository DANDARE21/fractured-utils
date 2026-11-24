package net.dandare21.fracturedutils.npc.pipeline;

import java.util.Locale;

public enum NpcTriggerType {
    ON_RIGHT_CLICK,
    ON_DIALOG_END,
    ON_MID_DIALOG_EVENT,
    ON_PROXIMITY,
    ON_DAMAGED;

    public static NpcTriggerType fromString(String str) {
        if (str == null || str.isBlank()) return ON_RIGHT_CLICK;
        try {
            return NpcTriggerType.valueOf(str.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ON_RIGHT_CLICK;
        }
    }
}
