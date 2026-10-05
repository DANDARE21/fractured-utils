package net.dandare21.fracturedutils.client.camera;

import java.util.Locale;

public enum CameraEasing {
    LINEAR("Linear"),
    EASE_IN_QUAD("Ease In (Quad)"),
    EASE_OUT_QUAD("Ease Out (Quad)"),
    EASE_IN_OUT_QUAD("Ease In-Out (Quad)"),
    EASE_IN_CUBIC("Ease In (Cubic)"),
    EASE_OUT_CUBIC("Ease Out (Cubic)"),
    EASE_IN_OUT_CUBIC("Ease In-Out (Cubic)"),
    EASE_IN_SINE("Ease In (Sine)"),
    EASE_OUT_SINE("Ease Out (Sine)"),
    EASE_IN_OUT_SINE("Ease In-Out (Sine)"),
    EASE_IN_EXPO("Ease In (Expo)"),
    EASE_OUT_EXPO("Ease Out (Expo)"),
    EASE_IN_OUT_EXPO("Ease In-Out (Expo)"),
    EASE_IN_ELASTIC("Ease In (Elastic)"),
    EASE_OUT_ELASTIC("Ease Out (Elastic)"),
    EASE_IN_OUT_ELASTIC("Ease In-Out (Elastic)"),
    EASE_IN_BOUNCE("Ease In (Bounce)"),
    EASE_OUT_BOUNCE("Ease Out (Bounce)"),
    EASE_IN_OUT_BOUNCE("Ease In-Out (Bounce)"),
    INSTANT("Instant / Cut");

    private final String displayName;

    CameraEasing(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double ease(double t) {
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;

        return switch (this) {
            case INSTANT -> t >= 1.0 ? 1.0 : 0.0;
            case LINEAR -> t;
            case EASE_IN_QUAD -> t * t;
            case EASE_OUT_QUAD -> 1.0 - (1.0 - t) * (1.0 - t);
            case EASE_IN_OUT_QUAD -> t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2.0) / 2.0;

            case EASE_IN_CUBIC -> t * t * t;
            case EASE_OUT_CUBIC -> 1.0 - Math.pow(1.0 - t, 3.0);
            case EASE_IN_OUT_CUBIC -> t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;

            case EASE_IN_SINE -> 1.0 - Math.cos((t * Math.PI) / 2.0);
            case EASE_OUT_SINE -> Math.sin((t * Math.PI) / 2.0);
            case EASE_IN_OUT_SINE -> -(Math.cos(Math.PI * t) - 1.0) / 2.0;

            case EASE_IN_EXPO -> Math.pow(2.0, 10.0 * t - 10.0);
            case EASE_OUT_EXPO -> 1.0 - Math.pow(2.0, -10.0 * t);
            case EASE_IN_OUT_EXPO -> t < 0.5
                    ? Math.pow(2.0, 20.0 * t - 10.0) / 2.0
                    : (2.0 - Math.pow(2.0, -20.0 * t + 10.0)) / 2.0;

            case EASE_IN_ELASTIC -> {
                double c4 = (2.0 * Math.PI) / 3.0;
                yield -Math.pow(2.0, 10.0 * t - 10.0) * Math.sin((t * 10.0 - 10.75) * c4);
            }
            case EASE_OUT_ELASTIC -> {
                double c4 = (2.0 * Math.PI) / 3.0;
                yield Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * c4) + 1.0;
            }
            case EASE_IN_OUT_ELASTIC -> {
                double c5 = (2.0 * Math.PI) / 4.5;
                yield t < 0.5
                        ? -(Math.pow(2.0, 20.0 * t - 10.0) * Math.sin((20.0 * t - 11.125) * c5)) / 2.0
                        : (Math.pow(2.0, -20.0 * t + 10.0) * Math.sin((20.0 * t - 11.125) * c5)) / 2.0 + 1.0;
            }

            case EASE_IN_BOUNCE -> 1.0 - easeOutBounce(1.0 - t);
            case EASE_OUT_BOUNCE -> easeOutBounce(t);
            case EASE_IN_OUT_BOUNCE -> t < 0.5
                    ? (1.0 - easeOutBounce(1.0 - 2.0 * t)) / 2.0
                    : (1.0 + easeOutBounce(2.0 * t - 1.0)) / 2.0;
        };
    }

    private static double easeOutBounce(double x) {
        double n1 = 7.5625;
        double d1 = 2.75;

        if (x < 1.0 / d1) {
            return n1 * x * x;
        } else if (x < 2.0 / d1) {
            return n1 * (x -= 1.5 / d1) * x + 0.75;
        } else if (x < 2.5 / d1) {
            return n1 * (x -= 2.25 / d1) * x + 0.9375;
        } else {
            return n1 * (x -= 2.625 / d1) * x + 0.984375;
        }
    }

    public static CameraEasing fromString(String name) {
        if (name == null || name.isBlank()) return EASE_IN_OUT_CUBIC;
        String clean = name.trim().toUpperCase(Locale.ROOT).replace(" ", "_").replace("-", "_");
        for (CameraEasing e : values()) {
            if (e.name().equalsIgnoreCase(clean) || e.displayName.equalsIgnoreCase(name.trim())) {
                return e;
            }
        }
        return EASE_IN_OUT_CUBIC;
    }
}
