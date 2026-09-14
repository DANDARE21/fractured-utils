package net.dandare21.fracturedutils.screeneffect.effects;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectInstance;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ScreenShakeEffect {
    public static final ResourceLocation ID = new ResourceLocation(FracturedUtils.MOD_ID, "screen_shake");

    public static final ScreenEffectType<ScreenShakeInstance> TYPE = new ScreenEffectType<>(
            ID,
            ScreenShakeInstance::fromNetwork,
            ScreenShakeInstance::fromTag
    );

    public static class ScreenShakeInstance extends ScreenEffectInstance {
        private float intensity = 2.0f;
        private float frequency = 20.0f;
        private boolean decay = true;
        private float yawFactor = 1.0f;
        private float pitchFactor = 1.0f;
        private float rollFactor = 0.5f;

        public ScreenShakeInstance(int durationMs, float intensity, float frequency, boolean decay) {
            super(TYPE, durationMs);
            this.intensity = intensity;
            this.frequency = frequency;
            this.decay = decay;
        }

        public ScreenShakeInstance(int durationMs, float intensity, float frequency, boolean decay,
                                   float yawFactor, float pitchFactor, float rollFactor) {
            super(TYPE, durationMs);
            this.intensity = intensity;
            this.frequency = frequency;
            this.decay = decay;
            this.yawFactor = yawFactor;
            this.pitchFactor = pitchFactor;
            this.rollFactor = rollFactor;
        }

        public float getIntensity() { return intensity; }
        public void setIntensity(float intensity) { this.intensity = intensity; }

        public float getFrequency() { return frequency; }
        public void setFrequency(float frequency) { this.frequency = frequency; }

        public boolean isDecay() { return decay; }
        public void setDecay(boolean decay) { this.decay = decay; }

        public float getYawFactor() { return yawFactor; }
        public void setYawFactor(float yawFactor) { this.yawFactor = yawFactor; }

        public float getPitchFactor() { return pitchFactor; }
        public void setPitchFactor(float pitchFactor) { this.pitchFactor = pitchFactor; }

        public float getRollFactor() { return rollFactor; }
        public void setRollFactor(float rollFactor) { this.rollFactor = rollFactor; }

        @Override
        public void toNetwork(FriendlyByteBuf buf) {
            super.toNetwork(buf);
            buf.writeFloat(intensity);
            buf.writeFloat(frequency);
            buf.writeBoolean(decay);
            buf.writeFloat(yawFactor);
            buf.writeFloat(pitchFactor);
            buf.writeFloat(rollFactor);
        }

        public static ScreenShakeInstance fromNetwork(FriendlyByteBuf buf) {
            int duration = buf.readVarInt();
            float intensity = buf.readFloat();
            float frequency = buf.readFloat();
            boolean decay = buf.readBoolean();
            float yawFactor = buf.readFloat();
            float pitchFactor = buf.readFloat();
            float rollFactor = buf.readFloat();
            return new ScreenShakeInstance(duration, intensity, frequency, decay, yawFactor, pitchFactor, rollFactor);
        }

        @Override
        public CompoundTag toTag() {
            CompoundTag tag = super.toTag();
            tag.putFloat("Intensity", intensity);
            tag.putFloat("Frequency", frequency);
            tag.putBoolean("Decay", decay);
            tag.putFloat("YawFactor", yawFactor);
            tag.putFloat("PitchFactor", pitchFactor);
            tag.putFloat("RollFactor", rollFactor);
            return tag;
        }

        public static ScreenShakeInstance fromTag(CompoundTag tag) {
            int duration = tag.getInt("DurationMs");
            float intensity = tag.contains("Intensity") ? tag.getFloat("Intensity") : 2.0f;
            float frequency = tag.contains("Frequency") ? tag.getFloat("Frequency") : 20.0f;
            boolean decay = !tag.contains("Decay") || tag.getBoolean("Decay");
            float yawFactor = tag.contains("YawFactor") ? tag.getFloat("YawFactor") : 1.0f;
            float pitchFactor = tag.contains("PitchFactor") ? tag.getFloat("PitchFactor") : 1.0f;
            float rollFactor = tag.contains("RollFactor") ? tag.getFloat("RollFactor") : 0.5f;
            return new ScreenShakeInstance(duration, intensity, frequency, decay, yawFactor, pitchFactor, rollFactor);
        }
    }
}
