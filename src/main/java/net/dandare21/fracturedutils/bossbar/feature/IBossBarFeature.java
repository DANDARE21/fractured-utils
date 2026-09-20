package net.dandare21.fracturedutils.bossbar.feature;

import net.dandare21.fracturedutils.bossbar.BossHealthBar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;

/**
 * Modular interface for custom bossbar features.
 * Implementations can attach custom behavior, telemetry, phase triggers,
 * shields, or graphical overlays to bossbars.
 */
public interface IBossBarFeature {

    /**
     * Unique identifier for this feature type (e.g. "phase_markers").
     */
    String getFeatureId();

    /**
     * Called every server tick for the owning bossbar.
     */
    void tick(BossHealthBar bar, MinecraftServer server);

    /**
     * Save feature state to NBT.
     */
    CompoundTag serializeNbt();

    /**
     * Load feature state from NBT.
     */
    void deserializeNbt(CompoundTag tag);

    /**
     * Write feature data to client sync buffer.
     */
    void toNetwork(FriendlyByteBuf buf);

    /**
     * Read feature data from client sync buffer.
     */
    void fromNetwork(FriendlyByteBuf buf);
}
