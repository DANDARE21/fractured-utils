package net.dandare21.fracturedutils.dialog.camera;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record CameraTarget(
        TargetType type,
        String tagValue,
        String selectorValue,
        AnchorPoint anchor,
        Vec3 offset,
        boolean lookAtTarget,
        float fixedYaw,
        float fixedPitch
) {
    public CameraTarget(TargetType type, Optional<String> tag, Optional<String> selector, AnchorPoint anchor, Vec3 offset, boolean lookAtTarget, float fixedYaw, float fixedPitch) {
        this(type, tag != null ? tag.orElse(null) : null, selector != null ? selector.orElse(null) : null, anchor, offset, lookAtTarget, fixedYaw, fixedPitch);
    }

    public Optional<String> tag() {
        return Optional.ofNullable(tagValue);
    }

    public Optional<String> selector() {
        return Optional.ofNullable(selectorValue);
    }

    public enum TargetType {
        ENTITY_TAG,
        SELECTOR,
        FIXED;

        public static TargetType fromString(String str) {
            if (str == null) return ENTITY_TAG;
            try {
                return TargetType.valueOf(str.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ENTITY_TAG;
            }
        }
    }

    public enum AnchorPoint {
        EYES,
        HEAD,
        CHEST,
        FEET;

        public static AnchorPoint fromString(String str) {
            if (str == null) return EYES;
            try {
                return AnchorPoint.valueOf(str.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return EYES;
            }
        }
    }

    private static final Codec<Vec3> VEC3_CODEC = Codec.DOUBLE.listOf().xmap(
            list -> {
                if (list.size() >= 3) {
                    return new Vec3(list.get(0), list.get(1), list.get(2));
                }
                return Vec3.ZERO;
            },
            vec -> List.of(vec.x, vec.y, vec.z)
    );

    public static final Codec<CameraTarget> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.optionalFieldOf("type", "entity_tag")
                            .xmap(TargetType::fromString, t -> t.name().toLowerCase(Locale.ROOT))
                            .forGetter(CameraTarget::type),
                    Codec.STRING.optionalFieldOf("tag").forGetter(CameraTarget::tag),
                    Codec.STRING.optionalFieldOf("selector").forGetter(CameraTarget::selector),
                    Codec.STRING.optionalFieldOf("bone", "eyes")
                            .xmap(AnchorPoint::fromString, a -> a.name().toLowerCase(Locale.ROOT))
                            .forGetter(CameraTarget::anchor),
                    VEC3_CODEC.optionalFieldOf("offset", Vec3.ZERO).forGetter(CameraTarget::offset),
                    Codec.BOOL.optionalFieldOf("lookAtTarget", true).forGetter(CameraTarget::lookAtTarget),
                    Codec.FLOAT.optionalFieldOf("fixedYaw", 0.0f).forGetter(CameraTarget::fixedYaw),
                    Codec.FLOAT.optionalFieldOf("fixedPitch", 0.0f).forGetter(CameraTarget::fixedPitch)
            ).apply(instance, CameraTarget::new)
    );

    public static CameraTarget fromEntityTag(String tag, AnchorPoint anchor, Vec3 offset) {
        return new CameraTarget(TargetType.ENTITY_TAG, tag, (String) null, anchor != null ? anchor : AnchorPoint.EYES, offset != null ? offset : Vec3.ZERO, true, 0.0f, 0.0f);
    }

    public static CameraTarget fromSelector(String selector, AnchorPoint anchor, Vec3 offset) {
        return new CameraTarget(TargetType.SELECTOR, (String) null, selector, anchor != null ? anchor : AnchorPoint.EYES, offset != null ? offset : Vec3.ZERO, true, 0.0f, 0.0f);
    }

    public static CameraTarget fixed(Vec3 position, float yaw, float pitch) {
        return new CameraTarget(TargetType.FIXED, (String) null, (String) null, AnchorPoint.FEET, position != null ? position : Vec3.ZERO, false, yaw, pitch);
    }
}
