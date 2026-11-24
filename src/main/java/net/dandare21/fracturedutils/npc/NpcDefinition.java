package net.dandare21.fracturedutils.npc;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.dandare21.fracturedutils.npc.pipeline.NpcRule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record NpcDefinition(
        ResourceLocation id,
        String name,
        NpcModelType modelType,
        Optional<ResourceLocation> modelLocation,
        Optional<ResourceLocation> textureLocation,
        Optional<ResourceLocation> animationLocation,
        String skinValue,
        String skinModel,
        Vec3 cameraOffset,
        float eyeHeight,
        float width,
        float height,
        float shadowRadius,
        boolean invulnerable,
        boolean immortal,
        double maxHealth,
        double attackDamage,
        double knockbackResistance,
        boolean noGravity,
        boolean pushable,
        float lookRadius,
        NpcMovementMode movementMode,
        List<Vec3> patrolWaypoints,
        float moveSpeed,
        List<NpcRule> rules,
        Optional<ResourceLocation> defaultDialog,
        boolean showNameTag
) {
    private static final Codec<Vec3> VEC3_CODEC = Codec.DOUBLE.listOf().xmap(
            list -> list.size() >= 3 ? new Vec3(list.get(0), list.get(1), list.get(2)) : Vec3.ZERO,
            vec -> List.of(vec.x, vec.y, vec.z)
    );

    // Sub-records to stay within Mojang RecordCodecBuilder parameter limits
    private record VisualData(
            NpcModelType modelType,
            Optional<ResourceLocation> modelLocation,
            Optional<ResourceLocation> textureLocation,
            Optional<ResourceLocation> animationLocation,
            String skinValue,
            String skinModel,
            Vec3 cameraOffset,
            float eyeHeight,
            float shadowRadius,
            boolean showNameTag
    ) {}

    private record AiData(
            float width,
            float height,
            boolean invulnerable,
            boolean immortal,
            double maxHealth,
            double attackDamage,
            double knockbackResistance,
            boolean noGravity,
            boolean pushable,
            float lookRadius,
            NpcMovementMode movementMode,
            List<Vec3> patrolWaypoints,
            float moveSpeed,
            List<NpcRule> rules
    ) {}

    private static final MapCodec<VisualData> VISUAL_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("modelType", "GECKOLIB")
                    .xmap(NpcModelType::fromString, t -> t.name().toLowerCase(Locale.ROOT))
                    .forGetter(VisualData::modelType),
            ResourceLocation.CODEC.optionalFieldOf("modelLocation").forGetter(VisualData::modelLocation),
            ResourceLocation.CODEC.optionalFieldOf("textureLocation").forGetter(VisualData::textureLocation),
            ResourceLocation.CODEC.optionalFieldOf("animationLocation").forGetter(VisualData::animationLocation),
            Codec.STRING.optionalFieldOf("skinValue", "").forGetter(VisualData::skinValue),
            Codec.STRING.optionalFieldOf("skinModel", "default").forGetter(VisualData::skinModel),
            VEC3_CODEC.optionalFieldOf("cameraOffset", new Vec3(0.0, 1.6, 0.0)).forGetter(VisualData::cameraOffset),
            Codec.FLOAT.optionalFieldOf("eyeHeight", 1.62F).forGetter(VisualData::eyeHeight),
            Codec.FLOAT.optionalFieldOf("shadowRadius", 0.5F).forGetter(VisualData::shadowRadius),
            Codec.BOOL.optionalFieldOf("showNameTag", true).forGetter(VisualData::showNameTag)
    ).apply(instance, VisualData::new));

    private static final MapCodec<AiData> AI_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("width", 0.6F).forGetter(AiData::width),
            Codec.FLOAT.optionalFieldOf("height", 1.8F).forGetter(AiData::height),
            Codec.BOOL.optionalFieldOf("invulnerable", false).forGetter(AiData::invulnerable),
            Codec.BOOL.optionalFieldOf("immortal", false).forGetter(AiData::immortal),
            Codec.DOUBLE.optionalFieldOf("maxHealth", 20.0).forGetter(AiData::maxHealth),
            Codec.DOUBLE.optionalFieldOf("attackDamage", 5.0).forGetter(AiData::attackDamage),
            Codec.DOUBLE.optionalFieldOf("knockbackResistance", 0.0).forGetter(AiData::knockbackResistance),
            Codec.BOOL.optionalFieldOf("noGravity", false).forGetter(AiData::noGravity),
            Codec.BOOL.optionalFieldOf("pushable", false).forGetter(AiData::pushable),
            Codec.FLOAT.optionalFieldOf("lookRadius", 8.0F).forGetter(AiData::lookRadius),
            Codec.STRING.optionalFieldOf("movementMode", "STATIONARY")
                    .xmap(NpcMovementMode::fromString, m -> m.name().toLowerCase(Locale.ROOT))
                    .forGetter(AiData::movementMode),
            VEC3_CODEC.listOf().optionalFieldOf("patrolWaypoints", List.of()).forGetter(AiData::patrolWaypoints),
            Codec.FLOAT.optionalFieldOf("moveSpeed", 0.25F).forGetter(AiData::moveSpeed),
            NpcRule.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(AiData::rules)
    ).apply(instance, AiData::new));

    public static final Codec<NpcDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(NpcDefinition::id),
            Codec.STRING.optionalFieldOf("name", "Custom NPC").forGetter(NpcDefinition::name),
            ResourceLocation.CODEC.optionalFieldOf("defaultDialog").forGetter(NpcDefinition::defaultDialog),
            VISUAL_CODEC.forGetter(NpcDefinition::toVisualData),
            AI_CODEC.forGetter(NpcDefinition::toAiData)
    ).apply(instance, NpcDefinition::fromData));

    private VisualData toVisualData() {
        return new VisualData(modelType, modelLocation, textureLocation, animationLocation, skinValue, skinModel, cameraOffset, eyeHeight, shadowRadius, showNameTag);
    }

    private AiData toAiData() {
        return new AiData(width, height, invulnerable, immortal, maxHealth, attackDamage, knockbackResistance, noGravity, pushable, lookRadius, movementMode, patrolWaypoints, moveSpeed, rules);
    }

    private static NpcDefinition fromData(ResourceLocation id, String name, Optional<ResourceLocation> defaultDialog, VisualData visual, AiData ai) {
        return new NpcDefinition(
                id, name, visual.modelType(), visual.modelLocation(), visual.textureLocation(), visual.animationLocation(),
                visual.skinValue(), visual.skinModel(), visual.cameraOffset(), visual.eyeHeight(),
                ai.width(), ai.height(), visual.shadowRadius(), ai.invulnerable(), ai.immortal(),
                ai.maxHealth(), ai.attackDamage(), ai.knockbackResistance(), ai.noGravity(), ai.pushable(),
                ai.lookRadius(), ai.movementMode(), ai.patrolWaypoints(), ai.moveSpeed(), ai.rules(), defaultDialog,
                visual.showNameTag()
        );
    }

    public static NpcDefinition createDefault(ResourceLocation id) {
        return new NpcDefinition(
                id,
                "Custom NPC",
                NpcModelType.PLAYER,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "",
                "default",
                new Vec3(0.0, 1.6, 0.0),
                1.62F,
                0.6F,
                1.8F,
                0.5F,
                false,
                false,
                20.0,
                5.0,
                0.0,
                false,
                false,
                8.0F,
                NpcMovementMode.STATIONARY,
                new ArrayList<>(),
                0.25F,
                new ArrayList<>(),
                Optional.empty(),
                true
        );
    }

    public CompoundTag toNbt() {
        Tag tag = CODEC.encodeStart(NbtOps.INSTANCE, this).result().orElse(new CompoundTag());
        return tag instanceof CompoundTag ct ? ct : new CompoundTag();
    }

    public static Optional<NpcDefinition> fromNbt(CompoundTag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag).result();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeUtf(name);
        buf.writeEnum(modelType);
        buf.writeOptional(modelLocation, FriendlyByteBuf::writeResourceLocation);
        buf.writeOptional(textureLocation, FriendlyByteBuf::writeResourceLocation);
        buf.writeOptional(animationLocation, FriendlyByteBuf::writeResourceLocation);
        buf.writeUtf(skinValue);
        buf.writeUtf(skinModel);
        buf.writeDouble(cameraOffset.x);
        buf.writeDouble(cameraOffset.y);
        buf.writeDouble(cameraOffset.z);
        buf.writeFloat(eyeHeight);
        buf.writeFloat(width);
        buf.writeFloat(height);
        buf.writeFloat(shadowRadius);
        buf.writeBoolean(invulnerable);
        buf.writeBoolean(immortal);
        buf.writeDouble(maxHealth);
        buf.writeDouble(attackDamage);
        buf.writeDouble(knockbackResistance);
        buf.writeBoolean(noGravity);
        buf.writeBoolean(pushable);
        buf.writeFloat(lookRadius);
        buf.writeEnum(movementMode);
        buf.writeVarInt(patrolWaypoints.size());
        for (Vec3 wp : patrolWaypoints) {
            buf.writeDouble(wp.x);
            buf.writeDouble(wp.y);
            buf.writeDouble(wp.z);
        }
        buf.writeFloat(moveSpeed);
        buf.writeOptional(defaultDialog, FriendlyByteBuf::writeResourceLocation);
        buf.writeBoolean(showNameTag);

        JsonElement rulesJson = NpcRule.CODEC.listOf().encodeStart(JsonOps.INSTANCE, rules).result().orElseGet(com.google.gson.JsonArray::new);
        buf.writeUtf(rulesJson.toString(), 262144);
    }

    public static NpcDefinition decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        String name = buf.readUtf();
        NpcModelType modelType = buf.readEnum(NpcModelType.class);
        Optional<ResourceLocation> modelLoc = buf.readOptional(FriendlyByteBuf::readResourceLocation);
        Optional<ResourceLocation> texLoc = buf.readOptional(FriendlyByteBuf::readResourceLocation);
        Optional<ResourceLocation> animLoc = buf.readOptional(FriendlyByteBuf::readResourceLocation);
        String skinVal = buf.readUtf();
        String skinModel = buf.readUtf();
        Vec3 cameraOffset = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float eyeHeight = buf.readFloat();
        float width = buf.readFloat();
        float height = buf.readFloat();
        float shadowRadius = buf.readFloat();
        boolean invulnerable = buf.readBoolean();
        boolean immortal = buf.readBoolean();
        double maxHealth = buf.readDouble();
        double attackDamage = buf.readDouble();
        double knockbackResistance = buf.readDouble();
        boolean noGravity = buf.readBoolean();
        boolean pushable = buf.readBoolean();
        float lookRadius = buf.readFloat();
        NpcMovementMode movementMode = buf.readEnum(NpcMovementMode.class);
        int wpCount = buf.readVarInt();
        List<Vec3> waypoints = new ArrayList<>(wpCount);
        for (int i = 0; i < wpCount; i++) {
            waypoints.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        float moveSpeed = buf.readFloat();
        Optional<ResourceLocation> defaultDialog = buf.readOptional(FriendlyByteBuf::readResourceLocation);
        boolean showNameTag = buf.readBoolean();

        String rulesJsonStr = buf.readUtf(262144);
        List<NpcRule> rules = new ArrayList<>();
        try {
            JsonElement parsed = com.google.gson.JsonParser.parseString(rulesJsonStr);
            rules = NpcRule.CODEC.listOf().parse(JsonOps.INSTANCE, parsed).result().orElse(List.of());
        } catch (Exception ignored) {}

        return new NpcDefinition(
                id, name, modelType, modelLoc, texLoc, animLoc, skinVal, skinModel,
                cameraOffset, eyeHeight, width, height, shadowRadius, invulnerable,
                immortal, maxHealth, attackDamage, knockbackResistance,
                noGravity, pushable, lookRadius, movementMode, waypoints, moveSpeed,
                rules, defaultDialog, showNameTag
        );
    }
}
