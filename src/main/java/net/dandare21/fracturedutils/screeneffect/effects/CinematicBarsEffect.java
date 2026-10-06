package net.dandare21.fracturedutils.screeneffect.effects;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectInstance;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class CinematicBarsEffect {
    public static final ResourceLocation ID = new ResourceLocation(FracturedUtils.MOD_ID, "cinematic_bars");

    public static final ScreenEffectType<CinematicBarsInstance> TYPE = new ScreenEffectType<>(
            ID,
            CinematicBarsInstance::fromNetwork,
            CinematicBarsInstance::fromTag
    );

    public static class CinematicBarsInstance extends ScreenEffectInstance {
        private int transitionInMs = 500;
        private int transitionOutMs = 500;
        private float barHeightRatio = 0.125f; // Fraction of screen height per bar (e.g. 0.125 = 12.5%)
        private int color = 0xFF000000; // Default opaque black

        public CinematicBarsInstance(int durationMs) {
            this(durationMs, 0, 0, 0.125f, 0xFF000000);
        }

        public CinematicBarsInstance(int durationMs, int transitionInMs) {
            this(durationMs, transitionInMs, transitionInMs, 0.125f, 0xFF000000);
        }

        public CinematicBarsInstance(int durationMs, int transitionInMs, int transitionOutMs) {
            this(durationMs, transitionInMs, transitionOutMs, 0.125f, 0xFF000000);
        }

        public CinematicBarsInstance(int durationMs, int transitionInMs, int transitionOutMs, float barHeightRatio, int color) {
            super(TYPE, durationMs);
            this.transitionInMs = Math.max(0, transitionInMs);
            this.transitionOutMs = Math.max(0, transitionOutMs);
            this.barHeightRatio = Math.max(0.01f, Math.min(0.49f, barHeightRatio));
            this.color = color;
        }

        public int getTransitionInMs() {
            return transitionInMs;
        }

        public void setTransitionInMs(int transitionInMs) {
            this.transitionInMs = Math.max(0, transitionInMs);
        }

        public int getTransitionOutMs() {
            return transitionOutMs;
        }

        public void setTransitionOutMs(int transitionOutMs) {
            this.transitionOutMs = Math.max(0, transitionOutMs);
        }

        public float getBarHeightRatio() {
            return barHeightRatio;
        }

        public void setBarHeightRatio(float barHeightRatio) {
            this.barHeightRatio = Math.max(0.01f, Math.min(0.49f, barHeightRatio));
        }

        public float getHeightPercent() {
            return barHeightRatio * 100.0f;
        }

        public void setHeightPercent(float percent) {
            setBarHeightRatio(percent / 100.0f);
        }

        public int getColor() {
            return color;
        }

        public void setColor(int color) {
            this.color = color;
        }

        /**
         * Calculates normalized height fraction [0.0 to 1.0] at currentTimeMs,
         * applying smooth cubic easing for slide-in and slide-out transitions.
         */
        public float getCurrentHeightFraction(long currentTimeMs) {
            long elapsed = currentTimeMs - startTimeMs;
            if (elapsed < 0) return 0.0f;

            float inFraction = 1.0f;
            if (transitionInMs > 0) {
                if (elapsed < transitionInMs) {
                    float t = (float) elapsed / (float) transitionInMs;
                    inFraction = ease(Math.min(1.0f, Math.max(0.0f, t)));
                } else {
                    inFraction = 1.0f;
                }
            } else {
                inFraction = 1.0f;
            }

            float outFraction = 1.0f;
            if (durationMs > 0 && transitionOutMs > 0) {
                long remaining = (startTimeMs + durationMs) - currentTimeMs;
                if (remaining <= 0) {
                    return 0.0f;
                }
                if (remaining < transitionOutMs) {
                    float t = (float) remaining / (float) transitionOutMs;
                    outFraction = ease(Math.min(1.0f, Math.max(0.0f, t)));
                } else {
                    outFraction = 1.0f;
                }
            }

            return Math.min(inFraction, outFraction);
        }

        /**
         * Smooth cubic ease-in-out curve for cinematic sliding.
         */
        private static float ease(float t) {
            if (t <= 0.0f) return 0.0f;
            if (t >= 1.0f) return 1.0f;
            return t < 0.5f ? 4.0f * t * t * t : 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 3.0) / 2.0f;
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf) {
            super.toNetwork(buf);
            buf.writeVarInt(transitionInMs);
            buf.writeVarInt(transitionOutMs);
            buf.writeFloat(barHeightRatio);
            buf.writeInt(color);
        }

        public static CinematicBarsInstance fromNetwork(FriendlyByteBuf buf) {
            int duration = buf.readVarInt();
            int transitionIn = buf.readVarInt();
            int transitionOut = buf.readVarInt();
            float barHeight = buf.readFloat();
            int color = buf.readInt();
            return new CinematicBarsInstance(duration, transitionIn, transitionOut, barHeight, color);
        }

        @Override
        public CompoundTag toTag() {
            CompoundTag tag = super.toTag();
            tag.putInt("TransitionInMs", transitionInMs);
            tag.putInt("TransitionOutMs", transitionOutMs);
            tag.putFloat("BarHeightRatio", barHeightRatio);
            tag.putInt("Color", color);
            return tag;
        }

        public static CinematicBarsInstance fromTag(CompoundTag tag) {
            int duration = tag.getInt("DurationMs");
            int transitionIn = tag.contains("TransitionInMs") ? tag.getInt("TransitionInMs") : 500;
            int transitionOut = tag.contains("TransitionOutMs") ? tag.getInt("TransitionOutMs") : 500;
            float barHeight = tag.contains("BarHeightRatio") ? tag.getFloat("BarHeightRatio") : 0.125f;
            int color = tag.contains("Color") ? tag.getInt("Color") : 0xFF000000;
            return new CinematicBarsInstance(duration, transitionIn, transitionOut, barHeight, color);
        }
    }
}
