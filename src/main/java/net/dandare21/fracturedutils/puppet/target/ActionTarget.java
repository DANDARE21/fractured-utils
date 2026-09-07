package net.dandare21.fracturedutils.puppet.target;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.dandare21.fracturedutils.util.SelectorUtils;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Polymorphic target representation for Boss Puppet Framework actions.
 * Seamlessly resolves static positions (Vec3), explicit entities (UUID), and command selectors (@p, etc.).
 */
public interface ActionTarget {

    Vec3 getPosition(ServerLevel level);

    Optional<LivingEntity> getEntity(ServerLevel level);

    record PositionTarget(Vec3 pos) implements ActionTarget {
        public static final Codec<PositionTarget> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.DOUBLE.fieldOf("x").forGetter(t -> t.pos.x),
                        Codec.DOUBLE.fieldOf("y").forGetter(t -> t.pos.y),
                        Codec.DOUBLE.fieldOf("z").forGetter(t -> t.pos.z)
                ).apply(instance, (x, y, z) -> new PositionTarget(new Vec3(x, y, z)))
        );

        public static final Codec<PositionTarget> LIST_CODEC = Codec.DOUBLE.listOf().comapFlatMap(
                list -> list.size() == 3
                        ? DataResult.success(new PositionTarget(new Vec3(list.get(0), list.get(1), list.get(2))))
                        : DataResult.error(() -> "Expected 3 doubles [x, y, z] for PositionTarget"),
                target -> List.of(target.pos.x, target.pos.y, target.pos.z)
        );

        @Override
        public Vec3 getPosition(ServerLevel level) {
            return this.pos;
        }

        @Override
        public Optional<LivingEntity> getEntity(ServerLevel level) {
            return Optional.empty();
        }
    }

    record EntityTarget(UUID uuid) implements ActionTarget {
        public static final Codec<EntityTarget> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        UUIDUtil.STRING_CODEC.fieldOf("uuid").forGetter(EntityTarget::uuid)
                ).apply(instance, EntityTarget::new)
        );

        @Override
        public Vec3 getPosition(ServerLevel level) {
            return getEntity(level).map(Entity::position).orElse(Vec3.ZERO);
        }

        @Override
        public Optional<LivingEntity> getEntity(ServerLevel level) {
            if (level == null || this.uuid == null) return Optional.empty();
            Entity entity = level.getEntity(this.uuid);
            if (entity == null && level.getServer() != null) {
                for (ServerLevel sLevel : level.getServer().getAllLevels()) {
                    entity = sLevel.getEntity(this.uuid);
                    if (entity != null) break;
                }
            }
            return entity instanceof LivingEntity living ? Optional.of(living) : Optional.empty();
        }
    }

    record SelectorTarget(String selector) implements ActionTarget {
        public static final Codec<SelectorTarget> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.fieldOf("selector").forGetter(SelectorTarget::selector)
                ).apply(instance, SelectorTarget::new)
        );

        @Override
        public Vec3 getPosition(ServerLevel level) {
            return getEntity(level).map(Entity::position).orElse(Vec3.ZERO);
        }

        @Override
        public Optional<LivingEntity> getEntity(ServerLevel level) {
            if (level == null || level.getServer() == null || this.selector == null || this.selector.isBlank()) {
                return Optional.empty();
            }
            List<Entity> matches = SelectorUtils.getTargetEntities(level.getServer(), this.selector);
            for (Entity entity : matches) {
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    return Optional.of(living);
                }
            }
            if (!matches.isEmpty() && matches.get(0) instanceof LivingEntity living) {
                return Optional.of(living);
            }
            return Optional.empty();
        }
    }

    // Direct String parser: parses as UUID if valid, otherwise SelectorTarget
    Codec<ActionTarget> STRING_CODEC = Codec.STRING.xmap(
            str -> {
                try {
                    return (ActionTarget) new EntityTarget(UUID.fromString(str.trim()));
                } catch (IllegalArgumentException e) {
                    return (ActionTarget) new SelectorTarget(str.trim());
                }
            },
            target -> {
                if (target instanceof EntityTarget entityTarget) {
                    return entityTarget.uuid().toString();
                } else if (target instanceof SelectorTarget selectorTarget) {
                    return selectorTarget.selector();
                }
                return "";
            }
    );

    // Tagged Union Codec supporting {"type": "pos"|"position"|"entity"|"selector", ...}
    Codec<ActionTarget> TAGGED_CODEC = Codec.STRING.dispatch(
            "type",
            target -> {
                if (target instanceof PositionTarget) return "position";
                if (target instanceof EntityTarget) return "entity";
                return "selector";
            },
            type -> switch (type.toLowerCase()) {
                case "pos", "position" -> PositionTarget.CODEC;
                case "entity", "uuid" -> EntityTarget.CODEC;
                default -> SelectorTarget.CODEC;
            }
    );

    // Unified Codec: First tries Tagged representation, then List representation [x,y,z], then String (UUID / Selector)
    Codec<ActionTarget> CODEC = Codec.either(
            TAGGED_CODEC,
            Codec.either(PositionTarget.LIST_CODEC, STRING_CODEC)
    ).xmap(
            either -> either.map(
                    tagged -> tagged,
                    subEither -> subEither.map(listPos -> (ActionTarget) listPos, strTarget -> strTarget)
            ),
            target -> {
                if (target instanceof PositionTarget) {
                    return Either.left(target);
                }
                return Either.left(target);
            }
    );

    static ActionTarget fromPos(Vec3 pos) {
        return new PositionTarget(pos);
    }

    static ActionTarget fromEntity(UUID uuid) {
        return new EntityTarget(uuid);
    }

    static ActionTarget fromSelector(String selector) {
        return new SelectorTarget(selector);
    }
}
