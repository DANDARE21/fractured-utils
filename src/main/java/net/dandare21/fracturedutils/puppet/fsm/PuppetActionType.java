package net.dandare21.fracturedutils.puppet.fsm;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * Defines a puppet action type and its parameter codec and instance factory.
 *
 * @param <T> The typed parameter record for this action.
 */
public class PuppetActionType<T> {

    @FunctionalInterface
    public interface ActionFactory<T> {
        PuppetActionInstance<T> create(Mob mob, T params);
    }

    private final ResourceLocation id;
    private final Codec<T> codec;
    private final ActionFactory<T> factory;

    public PuppetActionType(ResourceLocation id, Codec<T> codec, ActionFactory<T> factory) {
        this.id = id;
        this.codec = codec;
        this.factory = factory;
    }

    public ResourceLocation getId() {
        return id;
    }

    public Codec<T> getCodec() {
        return codec;
    }

    public PuppetActionInstance<T> createInstance(Mob mob, T params) {
        return factory.create(mob, params);
    }

    @Override
    public String toString() {
        return "PuppetActionType{" + id + '}';
    }
}
