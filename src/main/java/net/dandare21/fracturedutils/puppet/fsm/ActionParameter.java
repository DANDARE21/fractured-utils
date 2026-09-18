package net.dandare21.fracturedutils.puppet.fsm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Defines a configurable parameter for a puppet action (e.g. spin direction, spin speed, radius, damage).
 *
 * @param <T> The value type of the parameter.
 */
public class ActionParameter<T> {

    public enum Type {
        BOOLEAN,
        INT,
        FLOAT,
        DOUBLE,
        STRING,
        OPTIONS
    }

    private final String key;
    private final Component label;
    private final Type type;
    private final T defaultValue;
    private final List<String> options;
    private final Component description;

    public ActionParameter(String key, Component label, Type type, T defaultValue, List<String> options, Component description) {
        this.key = Objects.requireNonNull(key, "key");
        this.label = label != null ? label : Component.literal(key);
        this.type = Objects.requireNonNull(type, "type");
        this.defaultValue = defaultValue;
        this.options = options != null ? List.copyOf(options) : Collections.emptyList();
        this.description = description != null ? description : Component.empty();
    }

    public String getKey() {
        return key;
    }

    public Component getLabel() {
        return label;
    }

    public Type getType() {
        return type;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public String getDefaultValueString() {
        return defaultValue != null ? String.valueOf(defaultValue) : "";
    }

    public List<String> getOptions() {
        return options;
    }

    public Component getDescription() {
        return description;
    }

    public static ActionParameter<Boolean> ofBoolean(String key, String label, boolean defaultValue, String description) {
        return new ActionParameter<>(key, Component.literal(label), Type.BOOLEAN, defaultValue, null, Component.literal(description));
    }

    public static ActionParameter<Integer> ofInt(String key, String label, int defaultValue, String description) {
        return new ActionParameter<>(key, Component.literal(label), Type.INT, defaultValue, null, Component.literal(description));
    }

    public static ActionParameter<Float> ofFloat(String key, String label, float defaultValue, String description) {
        return new ActionParameter<>(key, Component.literal(label), Type.FLOAT, defaultValue, null, Component.literal(description));
    }

    public static ActionParameter<Double> ofDouble(String key, String label, double defaultValue, String description) {
        return new ActionParameter<>(key, Component.literal(label), Type.DOUBLE, defaultValue, null, Component.literal(description));
    }

    public static ActionParameter<String> ofString(String key, String label, String defaultValue, String description) {
        return new ActionParameter<>(key, Component.literal(label), Type.STRING, defaultValue, null, Component.literal(description));
    }

    public static ActionParameter<String> ofOptions(String key, String label, List<String> options, String defaultValue, String description) {
        return new ActionParameter<>(key, Component.literal(label), Type.OPTIONS, defaultValue, options, Component.literal(description));
    }

    /**
     * Serializes a value into a CompoundTag with the correct NBT type so Mojang Codecs parse cleanly.
     */
    public void writeToTag(CompoundTag tag, String rawValue) {
        if (tag == null || key == null || key.isBlank()) return;
        String val = (rawValue != null && !rawValue.isBlank()) ? rawValue.trim() : getDefaultValueString();
        switch (type) {
            case BOOLEAN -> {
                boolean b = "true".equalsIgnoreCase(val) || "1".equals(val);
                tag.putBoolean(key, b);
            }
            case INT -> {
                try {
                    tag.putInt(key, Integer.parseInt(val));
                } catch (NumberFormatException e) {
                    if (defaultValue instanceof Number n) {
                        tag.putInt(key, n.intValue());
                    }
                }
            }
            case FLOAT -> {
                try {
                    tag.putFloat(key, Float.parseFloat(val.replace(',', '.')));
                } catch (NumberFormatException e) {
                    if (defaultValue instanceof Number n) {
                        tag.putFloat(key, n.floatValue());
                    }
                }
            }
            case DOUBLE -> {
                try {
                    tag.putDouble(key, Double.parseDouble(val.replace(',', '.')));
                } catch (NumberFormatException e) {
                    if (defaultValue instanceof Number n) {
                        tag.putDouble(key, n.doubleValue());
                    }
                }
            }
            case STRING, OPTIONS -> tag.putString(key, val);
        }
    }
}
