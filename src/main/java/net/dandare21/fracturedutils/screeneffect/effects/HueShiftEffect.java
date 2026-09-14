package net.dandare21.fracturedutils.screeneffect.effects;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectInstance;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class HueShiftEffect {
    public static final ResourceLocation ID = new ResourceLocation(FracturedUtils.MOD_ID, "hue_shift");

    public static final ScreenEffectType<HueShiftInstance> TYPE = new ScreenEffectType<>(
            ID,
            HueShiftInstance::fromNetwork,
            HueShiftInstance::fromTag
    );

    public static class HueShiftInstance extends ScreenEffectInstance {
        private float speed = 1.0f;           // Hue rotations per second
        private boolean continuous = true;     // true = continuous rainbow cycle, false = static angle shift
        private float hueAngle = 0.0f;         // Static hue angle in degrees (0 - 360)
        private float intensity = 0.65f;       // Blend opacity (0.0 - 1.0)

        public HueShiftInstance(int durationMs) {
            this(durationMs, 1.0f, true, 0.0f, 0.65f);
        }

        public HueShiftInstance(int durationMs, float speed, boolean continuous, float hueAngle, float intensity) {
            super(TYPE, durationMs);
            this.speed = speed;
            this.continuous = continuous;
            this.hueAngle = hueAngle;
            this.intensity = Math.max(0.0f, Math.min(1.0f, intensity));
        }

        public float getSpeed() { return speed; }
        public void setSpeed(float speed) { this.speed = speed; }

        public boolean isContinuous() { return continuous; }
        public void setContinuous(boolean continuous) { this.continuous = continuous; }

        public float getHueAngle() { return hueAngle; }
        public void setHueAngle(float hueAngle) { this.hueAngle = hueAngle; }

        public float getIntensity() { return intensity; }
        public void setIntensity(float intensity) { this.intensity = Math.max(0.0f, Math.min(1.0f, intensity)); }

        @Override
        public void toNetwork(FriendlyByteBuf buf) {
            super.toNetwork(buf);
            buf.writeFloat(speed);
            buf.writeBoolean(continuous);
            buf.writeFloat(hueAngle);
            buf.writeFloat(intensity);
        }

        public static HueShiftInstance fromNetwork(FriendlyByteBuf buf) {
            int duration = buf.readVarInt();
            float speed = buf.readFloat();
            boolean continuous = buf.readBoolean();
            float hueAngle = buf.readFloat();
            float intensity = buf.readFloat();
            return new HueShiftInstance(duration, speed, continuous, hueAngle, intensity);
        }

        @Override
        public CompoundTag toTag() {
            CompoundTag tag = super.toTag();
            tag.putFloat("Speed", speed);
            tag.putBoolean("Continuous", continuous);
            tag.putFloat("HueAngle", hueAngle);
            tag.putFloat("Intensity", intensity);
            return tag;
        }

        public static HueShiftInstance fromTag(CompoundTag tag) {
            int duration = tag.getInt("DurationMs");
            float speed = tag.contains("Speed") ? tag.getFloat("Speed") : 1.0f;
            boolean continuous = !tag.contains("Continuous") || tag.getBoolean("Continuous");
            float hueAngle = tag.contains("HueAngle") ? tag.getFloat("HueAngle") : 0.0f;
            float intensity = tag.contains("Intensity") ? tag.getFloat("Intensity") : 0.65f;
            return new HueShiftInstance(duration, speed, continuous, hueAngle, intensity);
        }
    }
}
