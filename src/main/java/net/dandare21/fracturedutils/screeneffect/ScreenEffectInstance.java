package net.dandare21.fracturedutils.screeneffect;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Base class for all running screen effect instances.
 * Stores timing information and provides network / NBT serialization.
 */
public abstract class ScreenEffectInstance {
    protected final ScreenEffectType<?> type;
    protected int durationMs;
    protected long startTimeMs;

    public ScreenEffectInstance(ScreenEffectType<?> type, int durationMs) {
        this.type = type;
        this.durationMs = Math.max(0, durationMs);
        this.startTimeMs = System.currentTimeMillis();
    }

    public ScreenEffectType<?> getType() {
        return type;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(int durationMs) {
        this.durationMs = Math.max(0, durationMs);
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public void setStartTimeMs(long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }

    public long getEndTimeMs() {
        return startTimeMs + durationMs;
    }

    public boolean isExpired() {
        return durationMs > 0 && System.currentTimeMillis() >= getEndTimeMs();
    }

    /**
     * Returns progress from 0.0 (start) to 1.0 (end).
     */
    public float getProgress(long currentTimeMs) {
        if (durationMs <= 0) return 0.0f;
        long elapsed = currentTimeMs - startTimeMs;
        if (elapsed <= 0) return 0.0f;
        if (elapsed >= durationMs) return 1.0f;
        return (float) elapsed / (float) durationMs;
    }

    /**
     * Returns remaining fraction from 1.0 (start) down to 0.0 (end).
     */
    public float getRemainingProgress(long currentTimeMs) {
        return Math.max(0.0f, 1.0f - getProgress(currentTimeMs));
    }

    /**
     * Serializes this instance's parameters to a network buffer.
     */
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeVarInt(durationMs);
    }

    /**
     * Reads common parameters from network buffer.
     */
    protected void readCommonNetwork(FriendlyByteBuf buf) {
        this.durationMs = buf.readVarInt();
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Serializes this instance's parameters to an NBT tag.
     */
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.getId().toString());
        tag.putInt("DurationMs", durationMs);
        return tag;
    }

    /**
     * Reads common parameters from NBT tag.
     */
    protected void readCommonTag(CompoundTag tag) {
        this.durationMs = tag.getInt("DurationMs");
        this.startTimeMs = System.currentTimeMillis();
    }
}
