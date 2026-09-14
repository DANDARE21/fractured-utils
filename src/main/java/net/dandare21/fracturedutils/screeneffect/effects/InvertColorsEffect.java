package net.dandare21.fracturedutils.screeneffect.effects;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectInstance;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class InvertColorsEffect {
    public static final ResourceLocation ID = new ResourceLocation(FracturedUtils.MOD_ID, "invert_colors");

    public static final ScreenEffectType<InvertColorsInstance> TYPE = new ScreenEffectType<>(
            ID,
            InvertColorsInstance::fromNetwork,
            InvertColorsInstance::fromTag
    );

    public static class InvertColorsInstance extends ScreenEffectInstance {
        private boolean pulse = false;
        private float frequency = 2.0f;
        private float intensity = 1.0f;

        public InvertColorsInstance(int durationMs) {
            this(durationMs, false, 2.0f, 1.0f);
        }

        public InvertColorsInstance(int durationMs, boolean pulse, float frequency) {
            this(durationMs, pulse, frequency, 1.0f);
        }

        public InvertColorsInstance(int durationMs, boolean pulse, float frequency, float intensity) {
            super(TYPE, durationMs);
            this.pulse = pulse;
            this.frequency = frequency;
            this.intensity = intensity;
        }

        public boolean isPulse() { return pulse; }
        public void setPulse(boolean pulse) { this.pulse = pulse; }

        public float getFrequency() { return frequency; }
        public void setFrequency(float frequency) { this.frequency = frequency; }

        public float getIntensity() { return intensity; }
        public void setIntensity(float intensity) { this.intensity = intensity; }

        @Override
        public void toNetwork(FriendlyByteBuf buf) {
            super.toNetwork(buf);
            buf.writeBoolean(pulse);
            buf.writeFloat(frequency);
            buf.writeFloat(intensity);
        }

        public static InvertColorsInstance fromNetwork(FriendlyByteBuf buf) {
            int duration = buf.readVarInt();
            boolean pulse = buf.readBoolean();
            float frequency = buf.readFloat();
            float intensity = buf.readFloat();
            return new InvertColorsInstance(duration, pulse, frequency, intensity);
        }

        @Override
        public CompoundTag toTag() {
            CompoundTag tag = super.toTag();
            tag.putBoolean("Pulse", pulse);
            tag.putFloat("Frequency", frequency);
            tag.putFloat("Intensity", intensity);
            return tag;
        }

        public static InvertColorsInstance fromTag(CompoundTag tag) {
            int duration = tag.getInt("DurationMs");
            boolean pulse = tag.getBoolean("Pulse");
            float frequency = tag.contains("Frequency") ? tag.getFloat("Frequency") : 2.0f;
            float intensity = tag.contains("Intensity") ? tag.getFloat("Intensity") : 1.0f;
            return new InvertColorsInstance(duration, pulse, frequency, intensity);
        }
    }
}
