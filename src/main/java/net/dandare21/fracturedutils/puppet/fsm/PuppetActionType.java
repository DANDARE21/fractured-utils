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
    private final java.util.List<ActionParameter<?>> parameters;

    public PuppetActionType(ResourceLocation id, Codec<T> codec, ActionFactory<T> factory) {
        this(id, codec, factory, java.util.Collections.emptyList());
    }

    public PuppetActionType(ResourceLocation id, Codec<T> codec, ActionFactory<T> factory, java.util.List<ActionParameter<?>> parameters) {
        this.id = id;
        this.codec = codec;
        this.factory = factory;
        this.parameters = parameters != null ? java.util.List.copyOf(parameters) : java.util.Collections.emptyList();
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

    /**
     * Returns the configurable parameters declared for this action (e.g. booleans, speeds, radiuses).
     * Subclasses can override to define their own parameters or supply them via the constructor.
     */
    public java.util.List<ActionParameter<?>> getParameters() {
        return parameters;
    }

    public java.util.Optional<ActionParameter<?>> getParameter(String key) {
        if (key == null) return java.util.Optional.empty();
        for (ActionParameter<?> param : getParameters()) {
            if (param.getKey().equalsIgnoreCase(key)) {
                return java.util.Optional.of(param);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Returns the lifecycle timing phases / states defined for this action.
     * Subclasses can override to define their own custom states, default durations, colors, and execution points.
     */
    public java.util.List<ActionTimingPhase> getTimingPhases() {
        return java.util.List.of(
                new ActionTimingPhase("windup", "Windup", 500, 0xFFFF9900, false),
                new ActionTimingPhase("jump", "Movement", 0, 0xFF4A69BD, false),
                new ActionTimingPhase("duration", "Active", 1000, 0xDDAA55FF, true),
                new ActionTimingPhase("recovery", "Recovery", 500, 0xFF00E5FF, false)
        );
    }

    /**
     * Returns the short label displayed at the keyframe impact / execution timestamp (e.g. SLAM, BARRAGE, HIT).
     */
    public String getExecutionLabel() {
        return "HIT";
    }

    @Override
    public String toString() {
        return "PuppetActionType{" + id + '}';
    }
}
