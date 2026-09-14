package net.dandare21.fracturedutils.screeneffect;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Represents a registered type of screen effect.
 * Other mods can define and register new ScreenEffectTypes.
 *
 * @param <T> The instance class handling this effect's parameters.
 */
public class ScreenEffectType<T extends ScreenEffectInstance> {
    private final ResourceLocation id;
    private final Function<FriendlyByteBuf, T> networkDecoder;
    private final Function<CompoundTag, T> tagDecoder;

    public ScreenEffectType(ResourceLocation id,
                            Function<FriendlyByteBuf, T> networkDecoder,
                            Function<CompoundTag, T> tagDecoder) {
        this.id = id;
        this.networkDecoder = networkDecoder;
        this.tagDecoder = tagDecoder;
    }

    public ResourceLocation getId() {
        return id;
    }

    public T fromNetwork(FriendlyByteBuf buf) {
        return networkDecoder.apply(buf);
    }

    public T fromTag(CompoundTag tag) {
        return tagDecoder.apply(tag);
    }

    @Override
    public String toString() {
        return "ScreenEffectType{" + id + '}';
    }
}
