package net.dandare21.fracturedutils.screeneffect.effects;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectInstance;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class StrobeEffect {
    public static final ResourceLocation ID = new ResourceLocation(FracturedUtils.MOD_ID, "strobe");

    public static final ScreenEffectType<StrobeInstance> TYPE = new ScreenEffectType<>(
            ID,
            StrobeInstance::fromNetwork,
            StrobeInstance::fromTag
    );

    public static class StrobeInstance extends ScreenEffectInstance {
        private float frequency = 10.0f; // Flashes per second
        private int color = 0xFFFFFFFF;  // Default white flash
        private boolean smooth = false;  // false = sharp flash (square wave), true = pulse (sine wave)
        private float maxAlpha = 0.85f;  // Peak opacity (0.0 to 1.0)

        public StrobeInstance(int durationMs, float frequency, int color) {
            this(durationMs, frequency, color, false, 0.85f);
        }

        public StrobeInstance(int durationMs, float frequency, int color, boolean smooth) {
            this(durationMs, frequency, color, smooth, 0.85f);
        }

        public StrobeInstance(int durationMs, float frequency, int color, boolean smooth, float maxAlpha) {
            super(TYPE, durationMs);
            this.frequency = frequency;
            this.color = color;
            this.smooth = smooth;
            this.maxAlpha = Math.max(0.0f, Math.min(1.0f, maxAlpha));
        }

        public float getFrequency() { return frequency; }
        public void setFrequency(float frequency) { this.frequency = frequency; }

        public int getColor() { return color; }
        public void setColor(int color) { this.color = color; }

        public boolean isSmooth() { return smooth; }
        public void setSmooth(boolean smooth) { this.smooth = smooth; }

        public float getMaxAlpha() { return maxAlpha; }
        public void setMaxAlpha(float maxAlpha) { this.maxAlpha = Math.max(0.0f, Math.min(1.0f, maxAlpha)); }

        @Override
        public void toNetwork(FriendlyByteBuf buf) {
            super.toNetwork(buf);
            buf.writeFloat(frequency);
            buf.writeInt(color);
            buf.writeBoolean(smooth);
            buf.writeFloat(maxAlpha);
        }

        public static StrobeInstance fromNetwork(FriendlyByteBuf buf) {
            int duration = buf.readVarInt();
            float frequency = buf.readFloat();
            int color = buf.readInt();
            boolean smooth = buf.readBoolean();
            float maxAlpha = buf.readFloat();
            return new StrobeInstance(duration, frequency, color, smooth, maxAlpha);
        }

        @Override
        public CompoundTag toTag() {
            CompoundTag tag = super.toTag();
            tag.putFloat("Frequency", frequency);
            tag.putInt("Color", color);
            tag.putBoolean("Smooth", smooth);
            tag.putFloat("MaxAlpha", maxAlpha);
            return tag;
        }

        public static StrobeInstance fromTag(CompoundTag tag) {
            int duration = tag.getInt("DurationMs");
            float frequency = tag.contains("Frequency") ? tag.getFloat("Frequency") : 10.0f;
            int color = tag.contains("Color") ? tag.getInt("Color") : 0xFFFFFFFF;
            boolean smooth = tag.getBoolean("Smooth");
            float maxAlpha = tag.contains("MaxAlpha") ? tag.getFloat("MaxAlpha") : 0.85f;
            return new StrobeInstance(duration, frequency, color, smooth, maxAlpha);
        }
    }
}
