package net.dandare21.fracturedutils.dialog.text;

import net.dandare21.fracturedutils.dialog.DialogFormatUtil.CustomEffect;
import net.minecraft.resources.ResourceLocation;

public class DialogTextToken {

    public sealed interface Token permits TextChar, Pause, ScreenShake, SoundTrigger, ParticleTrigger, EventTrigger {}

    public record TextChar(
            char character,
            int color,
            CustomEffect effect,
            boolean bold,
            boolean italic,
            boolean underline,
            boolean strikethrough,
            boolean obfuscated
    ) implements Token {}

    public record Pause(int ticks) implements Token {}

    public record ScreenShake(float intensity, int durationTicks) implements Token {}

    public record SoundTrigger(ResourceLocation sound, float volume, float pitch) implements Token {}

    public record ParticleTrigger(String particle, int count) implements Token {}

    public record EventTrigger(String eventName, String payload) implements Token {}
}

