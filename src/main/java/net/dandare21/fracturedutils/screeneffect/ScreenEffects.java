package net.dandare21.fracturedutils.screeneffect;

import net.dandare21.fracturedutils.screeneffect.effects.HueShiftEffect;
import net.dandare21.fracturedutils.screeneffect.effects.ImpactFrameEffect;
import net.dandare21.fracturedutils.screeneffect.effects.InvertColorsEffect;
import net.dandare21.fracturedutils.screeneffect.effects.ScreenShakeEffect;
import net.dandare21.fracturedutils.screeneffect.effects.StrobeEffect;

/**
 * Public convenience builder & factory methods for default screen effects.
 * Designed for clean, quick utilization by third-party mods adding Fractured Utils as a dependency.
 */
public final class ScreenEffects {
    private ScreenEffects() {}

    /**
     * Creates a screen shake effect instance.
     *
     * @param durationMs Duration in milliseconds.
     * @param intensity  Shake intensity / amplitude in degrees (e.g. 1.0 - 5.0).
     * @param frequency  Oscillation speed (e.g. 15.0 - 25.0).
     * @param decay      Whether the shake decays smoothly towards the end.
     */
    public static ScreenShakeEffect.ScreenShakeInstance shake(int durationMs, float intensity, float frequency, boolean decay) {
        return new ScreenShakeEffect.ScreenShakeInstance(durationMs, intensity, frequency, decay);
    }

    /**
     * Creates a standard screen shake with decaying intensity.
     */
    public static ScreenShakeEffect.ScreenShakeInstance shake(int durationMs, float intensity) {
        return new ScreenShakeEffect.ScreenShakeInstance(durationMs, intensity, 20.0f, true);
    }

    /**
     * Creates a solid color inversion effect.
     *
     * @param durationMs Duration in milliseconds.
     */
    public static InvertColorsEffect.InvertColorsInstance invert(int durationMs) {
        return new InvertColorsEffect.InvertColorsInstance(durationMs, false, 2.0f, 1.0f);
    }

    /**
     * Creates a pulsating color inversion effect.
     *
     * @param durationMs Duration in milliseconds.
     * @param frequency  Pulse frequency in Hz.
     */
    public static InvertColorsEffect.InvertColorsInstance invertPulse(int durationMs, float frequency) {
        return new InvertColorsEffect.InvertColorsInstance(durationMs, true, frequency, 1.0f);
    }

    /**
     * Creates a strobe flash effect with custom opacity.
     *
     * @param durationMs Duration in milliseconds.
     * @param frequency  Flashes per second (e.g. 8.0 - 15.0).
     * @param color      Color of the flash (RGB/ARGB hex, e.g. 0xFFFFFFFF for white).
     * @param smooth     false for abrupt strobe flash, true for smooth sine pulse.
     * @param maxAlpha   Peak opacity (0.0 to 1.0).
     */
    public static StrobeEffect.StrobeInstance strobe(int durationMs, float frequency, int color, boolean smooth, float maxAlpha) {
        return new StrobeEffect.StrobeInstance(durationMs, frequency, color, smooth, maxAlpha);
    }

    /**
     * Creates a strobe flash effect.
     */
    public static StrobeEffect.StrobeInstance strobe(int durationMs, float frequency, int color, boolean smooth) {
        return new StrobeEffect.StrobeInstance(durationMs, frequency, color, smooth, 0.85f);
    }

    /**
     * Creates a white strobe flash effect.
     */
    public static StrobeEffect.StrobeInstance strobe(int durationMs, float frequency) {
        return new StrobeEffect.StrobeInstance(durationMs, frequency, 0xFFFFFFFF, false, 0.85f);
    }

    /**
     * Creates a continuous hue shift (spectrum rotation) effect.
     *
     * @param durationMs Duration in milliseconds.
     * @param speed      Rotations per second (e.g. 0.5f - 1.0f).
     */
    public static HueShiftEffect.HueShiftInstance hueShift(int durationMs, float speed) {
        return new HueShiftEffect.HueShiftInstance(durationMs, speed, true, 0.0f, 1.0f);
    }

    /**
     * Creates a fixed angular hue shift effect (e.g. 90° warm, 180° complementary/inverted, 270° cool).
     */
    public static HueShiftEffect.HueShiftInstance hueShiftAngle(int durationMs, float hueAngle, float intensity) {
        return new HueShiftEffect.HueShiftInstance(durationMs, 0.0f, false, hueAngle, intensity);
    }

    /**
     * Creates a custom hue shift effect.
     */
    public static HueShiftEffect.HueShiftInstance hueShift(int durationMs, float speed, boolean continuous, float hueAngle, float intensity) {
        return new HueShiftEffect.HueShiftInstance(durationMs, speed, continuous, hueAngle, intensity);
    }

    /**
     * Creates an anime-style high contrast manga "draw" impact frame effect in black & white.
     *
     * @param durationMs Duration in milliseconds (typically 100 - 300ms).
     */
    public static ImpactFrameEffect.ImpactFrameInstance impactFrame(int durationMs) {
        return new ImpactFrameEffect.ImpactFrameInstance(durationMs, 0xFFFFFFFF, 0xFF000000, 35, true, "DRAW");
    }

    /**
     * Creates an impact frame effect with custom primary and secondary colors in "draw" style.
     */
    public static ImpactFrameEffect.ImpactFrameInstance impactFrame(int durationMs, int primaryColor, int secondaryColor, int frameIntervalMs) {
        return new ImpactFrameEffect.ImpactFrameInstance(durationMs, primaryColor, secondaryColor, frameIntervalMs, true, "DRAW");
    }

    /**
     * Creates an impact frame effect with custom colors, interval, inversion, and style.
     */
    public static ImpactFrameEffect.ImpactFrameInstance impactFrame(int durationMs, int primaryColor, int secondaryColor, int frameIntervalMs, boolean invertWorld, String style) {
        return new ImpactFrameEffect.ImpactFrameInstance(durationMs, primaryColor, secondaryColor, frameIntervalMs, invertWorld, style);
    }
}
