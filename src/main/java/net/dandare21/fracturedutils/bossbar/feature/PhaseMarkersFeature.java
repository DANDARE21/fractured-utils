package net.dandare21.fracturedutils.bossbar.feature;

import net.dandare21.fracturedutils.bossbar.BossHealthBar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modular feature that renders phase threshold markers on the bossbar (e.g. 0.75, 0.50, 0.25).
 */
public class PhaseMarkersFeature implements IBossBarFeature {
    public static final String ID = "phase_markers";

    private final List<Float> thresholds = new ArrayList<>();

    public PhaseMarkersFeature() {
    }

    public PhaseMarkersFeature(float... markers) {
        for (float m : markers) {
            this.thresholds.add(Math.max(0.0f, Math.min(1.0f, m)));
        }
        Collections.sort(this.thresholds);
    }

    public List<Float> getThresholds() {
        return thresholds;
    }

    public void addThreshold(float threshold) {
        float clamped = Math.max(0.0f, Math.min(1.0f, threshold));
        if (!thresholds.contains(clamped)) {
            thresholds.add(clamped);
            Collections.sort(thresholds);
        }
    }

    public void removeThreshold(float threshold) {
        thresholds.remove(Float.valueOf(threshold));
    }

    public void clearThresholds() {
        thresholds.clear();
    }

    @Override
    public String getFeatureId() {
        return ID;
    }

    @Override
    public void tick(BossHealthBar bar, MinecraftServer server) {
        // Can be used to trigger phase change events if desired
    }

    @Override
    public CompoundTag serializeNbt() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (float f : thresholds) {
            list.add(FloatTag.valueOf(f));
        }
        tag.put("Thresholds", list);
        return tag;
    }

    @Override
    public void deserializeNbt(CompoundTag tag) {
        thresholds.clear();
        if (tag.contains("Thresholds", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Thresholds", Tag.TAG_FLOAT);
            for (int i = 0; i < list.size(); i++) {
                thresholds.add(list.getFloat(i));
            }
        }
        Collections.sort(thresholds);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeVarInt(thresholds.size());
        for (float f : thresholds) {
            buf.writeFloat(f);
        }
    }

    @Override
    public void fromNetwork(FriendlyByteBuf buf) {
        thresholds.clear();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            thresholds.add(buf.readFloat());
        }
        Collections.sort(thresholds);
    }
}
