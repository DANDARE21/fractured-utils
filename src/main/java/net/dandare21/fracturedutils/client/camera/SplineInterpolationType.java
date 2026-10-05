package net.dandare21.fracturedutils.client.camera;

import java.util.Locale;

public enum SplineInterpolationType {
    CATMULL_ROM("Catmull-Rom (Centripetal)", "Smooth curved spline passing directly through keyframe coordinates without overshoot loops"),
    BEZIER("Cubic Bézier", "Smooth curve shaped by incoming and outgoing tangent handles (Tin, Tout)"),
    LINEAR("Linear (Lerp)", "Direct mechanical straight-line transition between keyframes"),
    STEP("Step (Cut)", "Instant jump cut to keyframe coordinates without interpolation");

    private final String displayName;
    private final String description;

    SplineInterpolationType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static SplineInterpolationType fromString(String name) {
        if (name == null || name.isBlank()) return CATMULL_ROM;
        String clean = name.trim().toUpperCase(Locale.ROOT).replace(" ", "_").replace("-", "_");
        for (SplineInterpolationType t : values()) {
            if (t.name().equalsIgnoreCase(clean) || t.displayName.equalsIgnoreCase(name.trim())) {
                return t;
            }
        }
        return CATMULL_ROM;
    }
}
