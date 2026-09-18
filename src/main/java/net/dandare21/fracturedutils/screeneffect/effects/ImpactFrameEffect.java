package net.dandare21.fracturedutils.screeneffect.effects;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectInstance;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ImpactFrameEffect {
    public static final ResourceLocation ID = new ResourceLocation(FracturedUtils.MOD_ID, "impact_frame");

    public static final ScreenEffectType<ImpactFrameInstance> TYPE = new ScreenEffectType<>(
            ID,
            ImpactFrameInstance::fromNetwork,
            ImpactFrameInstance::fromTag
    );

    public static class ImpactFrameInstance extends ScreenEffectInstance {
        private int primaryColor = 0xFFFFFFFF;    // Primary flash color (e.g. pure white or picked color)
        private int secondaryColor = 0xFF000000;  // Secondary negative/cut color (default pitch black)
        private int frameIntervalMs = 35;         // Milliseconds per cut frame (alternating speed)
        private boolean invertWorld = true;       // Inverts world colors during impact
        private String style = "DRAW";            // DRAW (Manga sketch with pixel rays/stars), MONOCHROME_CUT, COLOR_FLASH, RADIAL_SHOCK

        public ImpactFrameInstance(int durationMs) {
            this(durationMs, 0xFFFFFFFF, 0xFF000000, 35, true, "DRAW");
        }

        public ImpactFrameInstance(int durationMs, int primaryColor, int secondaryColor, int frameIntervalMs, boolean invertWorld, String style) {
            super(TYPE, durationMs);
            this.primaryColor = primaryColor;
            this.secondaryColor = secondaryColor;
            this.frameIntervalMs = Math.max(10, frameIntervalMs);
            this.invertWorld = invertWorld;
            this.style = (style != null && !style.isBlank()) ? style : "DRAW";
        }

        public int getPrimaryColor() { return primaryColor; }
        public void setPrimaryColor(int primaryColor) { this.primaryColor = primaryColor; }

        public int getSecondaryColor() { return secondaryColor; }
        public void setSecondaryColor(int secondaryColor) { this.secondaryColor = secondaryColor; }

        public int getFrameIntervalMs() { return frameIntervalMs; }
        public void setFrameIntervalMs(int frameIntervalMs) { this.frameIntervalMs = Math.max(10, frameIntervalMs); }

        public boolean isInvertWorld() { return invertWorld; }
        public void setInvertWorld(boolean invertWorld) { this.invertWorld = invertWorld; }

        public String getStyle() { return style; }
        public void setStyle(String style) { this.style = (style != null && !style.isBlank()) ? style : "MONOCHROME_CUT"; }

        @Override
        public void toNetwork(FriendlyByteBuf buf) {
            super.toNetwork(buf);
            buf.writeInt(primaryColor);
            buf.writeInt(secondaryColor);
            buf.writeVarInt(frameIntervalMs);
            buf.writeBoolean(invertWorld);
            buf.writeUtf(style, 32);
        }

        public static ImpactFrameInstance fromNetwork(FriendlyByteBuf buf) {
            int duration = buf.readVarInt();
            int pColor = buf.readInt();
            int sColor = buf.readInt();
            int interval = buf.readVarInt();
            boolean invert = buf.readBoolean();
            String style = buf.readUtf(32);
            return new ImpactFrameInstance(duration, pColor, sColor, interval, invert, style);
        }

        @Override
        public CompoundTag toTag() {
            CompoundTag tag = super.toTag();
            tag.putInt("PrimaryColor", primaryColor);
            tag.putInt("SecondaryColor", secondaryColor);
            tag.putInt("FrameIntervalMs", frameIntervalMs);
            tag.putBoolean("InvertWorld", invertWorld);
            tag.putString("Style", style);
            return tag;
        }

        public static ImpactFrameInstance fromTag(CompoundTag tag) {
            int duration = tag.getInt("DurationMs");
            int pColor = tag.contains("PrimaryColor") ? tag.getInt("PrimaryColor") : 0xFFFFFFFF;
            int sColor = tag.contains("SecondaryColor") ? tag.getInt("SecondaryColor") : 0xFF000000;
            int interval = tag.contains("FrameIntervalMs") ? tag.getInt("FrameIntervalMs") : 35;
            boolean invert = !tag.contains("InvertWorld") || tag.getBoolean("InvertWorld");
            String style = tag.contains("Style") ? tag.getString("Style") : "MONOCHROME_CUT";
            return new ImpactFrameInstance(duration, pColor, sColor, interval, invert, style);
        }
    }
}
