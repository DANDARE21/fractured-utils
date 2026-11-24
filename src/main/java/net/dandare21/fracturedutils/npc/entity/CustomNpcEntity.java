package net.dandare21.fracturedutils.npc.entity;

import net.dandare21.fracturedutils.npc.NpcDefinition;
import net.dandare21.fracturedutils.npc.NpcDefinitionManager;
import net.dandare21.fracturedutils.npc.NpcModelType;
import net.dandare21.fracturedutils.npc.NpcMovementMode;
import net.dandare21.fracturedutils.npc.pipeline.NpcRule;
import net.dandare21.fracturedutils.npc.pipeline.NpcTriggerType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class CustomNpcEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<String> DATA_NPC_ID =
            SynchedEntityData.defineId(CustomNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_IS_SPEAKING =
            SynchedEntityData.defineId(CustomNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_MODEL_TYPE =
            SynchedEntityData.defineId(CustomNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<CompoundTag> DATA_NBT_OVERRIDE =
            SynchedEntityData.defineId(CustomNpcEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Overrides directly saved in NBT (for one-off NPCs or custom placed entities)
    private CompoundTag nbtOverride = null;
    private NpcDefinition cachedDefinition = null;

    // Triggered animation state
    private String triggeredAnimation = null;
    private int triggeredAnimTicks = 0;

    // Client-side lip sync override
    private boolean clientSpeaking = false;

    // Proximity check interval
    private int proximityCheckTimer = 0;

    // Commanded companion behavior state
    private java.util.UUID commandedFollowPlayerUUID = null;
    private java.util.UUID commandedDefendPlayerUUID = null;

    public CustomNpcEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_NPC_ID, "fractured_utils:default_npc");
        this.entityData.define(DATA_IS_SPEAKING, false);
        this.entityData.define(DATA_MODEL_TYPE, "PLAYER");
        this.entityData.define(DATA_NBT_OVERRIDE, new CompoundTag());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.25D, true));
        this.goalSelector.addGoal(3, new net.dandare21.fracturedutils.npc.entity.ai.NpcFollowPlayerGoal(this));
        this.goalSelector.addGoal(5, new net.dandare21.fracturedutils.npc.entity.ai.NpcPatrolGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D) {
            @Override
            public boolean canUse() {
                return commandedFollowPlayerUUID == null && getMovementMode() == NpcMovementMode.WANDER && super.canUse();
            }
        });
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return getLookRadius() > 0 && super.canUse();
            }
        });
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return commandedFollowPlayerUUID == null && getMovementMode() != NpcMovementMode.STATIONARY && super.canUse();
            }
        });

        this.targetSelector.addGoal(1, new net.dandare21.fracturedutils.npc.entity.ai.NpcDefendPlayerGoal(this));
    }

    public NpcDefinition getDefinition() {
        if (cachedDefinition != null) return cachedDefinition;
        CompoundTag synchedOverride = this.entityData.get(DATA_NBT_OVERRIDE);
        if (synchedOverride != null && !synchedOverride.isEmpty()) {
            Optional<NpcDefinition> defOpt = NpcDefinition.fromNbt(synchedOverride);
            if (defOpt.isPresent()) {
                cachedDefinition = defOpt.get();
                return cachedDefinition;
            }
        }
        if (nbtOverride != null && !nbtOverride.isEmpty()) {
            Optional<NpcDefinition> defOpt = NpcDefinition.fromNbt(nbtOverride);
            if (defOpt.isPresent()) {
                cachedDefinition = defOpt.get();
                return cachedDefinition;
            }
        }
        String idStr = this.entityData.get(DATA_NPC_ID);
        try {
            ResourceLocation loc = new ResourceLocation(idStr);
            NpcDefinition def = level().isClientSide
                    ? net.dandare21.fracturedutils.npc.client.ClientNpcData.getDefinition(loc)
                    : NpcDefinitionManager.getInstance().getDefinition(loc);
            if (def != null) {
                cachedDefinition = def;
                return def;
            }
        } catch (Exception ignored) {}
        return NpcDefinition.createDefault(new ResourceLocation("fractured_utils", "default_npc"));
    }

    public void setDefinition(NpcDefinition definition, boolean saveAsOverride) {
        if (definition == null) return;
        this.entityData.set(DATA_NPC_ID, definition.id() != null ? definition.id().toString() : "fractured_utils:default_npc");
        this.entityData.set(DATA_MODEL_TYPE, definition.modelType().name());
        this.cachedDefinition = definition;
        this.cachedIdleAnim = null;
        this.cachedWalkAnim = null;

        if (saveAsOverride) {
            this.nbtOverride = definition.toNbt();
            this.entityData.set(DATA_NBT_OVERRIDE, this.nbtOverride);
        } else {
            this.nbtOverride = null;
        }

        // Apply properties
        this.setCustomName(Component.literal(definition.name().replace('&', '§')));
        this.setCustomNameVisible(definition.showNameTag() && !definition.name().isEmpty());
        this.setInvulnerable(definition.invulnerable());
        this.setNoGravity(definition.noGravity());

        var maxHpAttr = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr != null) maxHpAttr.setBaseValue(Math.max(1.0, definition.maxHealth()));
        var dmgAttr = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmgAttr != null) dmgAttr.setBaseValue(Math.max(0.0, definition.attackDamage()));
        var kbAttr = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbAttr != null) kbAttr.setBaseValue(Math.max(0.0, Math.min(1.0, definition.knockbackResistance())));
        var spdAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spdAttr != null) spdAttr.setBaseValue(Math.max(0.05, definition.moveSpeed()));
        this.setHealth((float) definition.maxHealth());
        this.refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_NBT_OVERRIDE.equals(key) || DATA_NPC_ID.equals(key) || DATA_MODEL_TYPE.equals(key)) {
            this.cachedDefinition = null;
            this.refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        NpcDefinition def = getDefinition();
        if (def != null && def.width() > 0 && def.height() > 0) {
            return EntityDimensions.scalable(def.width(), def.height());
        }
        return super.getDimensions(pose);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        NpcDefinition def = getDefinition();
        if (def != null && def.eyeHeight() > 0) {
            return def.eyeHeight();
        }
        return super.getStandingEyeHeight(pose, dimensions);
    }

    public String getNpcId() {
        return this.entityData.get(DATA_NPC_ID);
    }

    public void setNpcId(String id) {
        String newId = id != null ? id : "fractured_utils:default_npc";
        if (!newId.equals(this.entityData.get(DATA_NPC_ID))) {
            this.entityData.set(DATA_NPC_ID, newId);
            this.cachedDefinition = null;
        }
    }

    public NpcModelType getModelType() {
        return NpcModelType.fromString(this.entityData.get(DATA_MODEL_TYPE));
    }

    public boolean isSpeaking() {
        return this.clientSpeaking || this.entityData.get(DATA_IS_SPEAKING);
    }

    public void setSpeaking(boolean speaking) {
        this.entityData.set(DATA_IS_SPEAKING, speaking);
    }

    public void setClientSpeaking(boolean speaking) {
        this.clientSpeaking = speaking;
    }

    public Vec3 getCameraOffset() {
        NpcDefinition def = getDefinition();
        return def != null ? def.cameraOffset() : new Vec3(0.0, 1.6, 0.0);
    }

    public NpcMovementMode getMovementMode() {
        NpcDefinition def = getDefinition();
        return def != null ? def.movementMode() : NpcMovementMode.STATIONARY;
    }

    public float getLookRadius() {
        NpcDefinition def = getDefinition();
        return def != null ? def.lookRadius() : 8.0F;
    }

    public boolean isShowNameTag() {
        NpcDefinition def = getDefinition();
        return def != null ? def.showNameTag() : true;
    }

    public void setShowNameTag(boolean visible) {
        NpcDefinition def = getDefinition();
        if (def != null) {
            NpcDefinition updated = new NpcDefinition(
                    def.id(), def.name(), def.modelType(), def.modelLocation(),
                    def.textureLocation(), def.animationLocation(), def.skinValue(),
                    def.skinModel(), def.cameraOffset(), def.eyeHeight(),
                    def.width(), def.height(), def.shadowRadius(), def.invulnerable(),
                    def.immortal(), def.maxHealth(), def.attackDamage(),
                    def.knockbackResistance(), def.noGravity(), def.pushable(),
                    def.lookRadius(), def.movementMode(), def.patrolWaypoints(),
                    def.moveSpeed(), def.rules(), def.defaultDialog(), visible
            );
            setDefinition(updated, this.nbtOverride != null);
        }
        this.setCustomNameVisible(visible && (this.getCustomName() != null && !this.getCustomName().getString().isEmpty()));
    }

    public void playTriggeredAnimation(String animName) {
        this.triggeredAnimation = animName;
        this.triggeredAnimTicks = 40;
    }

    public void setCommandedFollowPlayer(java.util.UUID uuid) {
        this.commandedFollowPlayerUUID = uuid;
    }

    public java.util.UUID getCommandedFollowPlayer() {
        return this.commandedFollowPlayerUUID;
    }

    public void setCommandedDefendPlayer(java.util.UUID uuid) {
        this.commandedDefendPlayerUUID = uuid;
    }

    public java.util.UUID getCommandedDefendPlayer() {
        return this.commandedDefendPlayerUUID;
    }

    public void clearCommandedBehaviors() {
        this.commandedFollowPlayerUUID = null;
        this.commandedDefendPlayerUUID = null;
        this.getNavigation().stop();
        this.setTarget(null);
    }

    @Override
    public boolean isPushable() {
        NpcDefinition def = getDefinition();
        return def != null && def.pushable();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof ServerPlayer player) {
            fireTrigger(NpcTriggerType.ON_DAMAGED, null, player);
        }
        NpcDefinition def = getDefinition();
        if (def != null && def.invulnerable()) {
            return false;
        }
        if (isInvulnerableTo(source)) {
            return false;
        }
        if (def != null && def.immortal()) {
            if (amount >= this.getHealth()) {
                this.setHealth(1.0F);
                this.playHurtSound(source);
                return true;
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer) {
            // Check if player is holding NPC wand
            var held = player.getItemInHand(hand);
            ResourceLocation wandId = new ResourceLocation("fractured_utils", "npc_wand");
            if (net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(held.getItem()).equals(wandId)) {
                return InteractionResult.PASS; // Allow wand to handle
            }

            boolean handled = fireTrigger(NpcTriggerType.ON_RIGHT_CLICK, null, serverPlayer);
            if (handled) {
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }
        return super.mobInteract(player, hand);
    }

    public boolean fireTrigger(NpcTriggerType trigger, String filter, ServerPlayer player) {
        NpcDefinition def = getDefinition();
        if (def == null || def.rules().isEmpty()) {
            // Check default dialog sequence fallback
            if (trigger == NpcTriggerType.ON_RIGHT_CLICK && def != null && def.defaultDialog().isPresent()) {
                net.dandare21.fracturedutils.dialog.DialogManager.getInstance()
                        .startSequence(def.defaultDialog().get(), List.of(player));
                return true;
            }
            return false;
        }

        boolean matchedAny = false;
        for (NpcRule rule : def.rules()) {
            if (rule.matches(trigger, filter, player)) {
                rule.execute(this, player);
                matchedAny = true;
            }
        }
        return matchedAny;
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (this.triggeredAnimTicks > 0) {
            this.triggeredAnimTicks--;
            if (this.triggeredAnimTicks <= 0) {
                this.triggeredAnimation = null;
            }
        }

        if (!this.level().isClientSide) {
            proximityCheckTimer++;
            if (proximityCheckTimer >= 20) {
                proximityCheckTimer = 0;
                float radius = getLookRadius();
                if (radius > 0) {
                    List<ServerPlayer> players = this.level().getEntitiesOfClass(
                            ServerPlayer.class,
                            this.getBoundingBox().inflate(radius)
                    );
                    for (ServerPlayer p : players) {
                        fireTrigger(NpcTriggerType.ON_PROXIMITY, null, p);
                    }
                }
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("NpcId", getNpcId());
        if (nbtOverride != null && !nbtOverride.isEmpty()) {
            tag.put("NpcDefinitionOverride", nbtOverride);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("NpcId")) {
            setNpcId(tag.getString("NpcId"));
        }
        if (tag.contains("NpcDefinitionOverride")) {
            this.nbtOverride = tag.getCompound("NpcDefinitionOverride");
            NpcDefinition.fromNbt(this.nbtOverride).ifPresent(def -> setDefinition(def, true));
        }
    }

    private String cachedIdleAnim = null;
    private String cachedWalkAnim = null;

    public String getModelBaseName() {
        NpcDefinition def = getDefinition();
        if (def != null && def.modelLocation().isPresent()) {
            String path = def.modelLocation().get().getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (name.endsWith(".geo.json")) {
                name = name.substring(0, name.length() - ".geo.json".length());
            }
            return name;
        }
        return "void_herald";
    }

    public RawAnimation getIdleAnimation() {
        if (cachedIdleAnim == null) {
            String base = getModelBaseName();
            if (this.level().isClientSide) {
                cachedIdleAnim = net.dandare21.fracturedutils.npc.client.DynamicNpcGeoModel.resolveAnimationName(this, base, "idle");
            } else {
                cachedIdleAnim = "animation." + base + ".idle";
            }
        }
        return RawAnimation.begin().thenLoop(cachedIdleAnim);
    }

    public RawAnimation getMovementAnimation() {
        if (cachedWalkAnim == null) {
            String base = getModelBaseName();
            if (this.level().isClientSide) {
                cachedWalkAnim = net.dandare21.fracturedutils.npc.client.DynamicNpcGeoModel.resolveMovementAnimationName(this, base);
            } else {
                cachedWalkAnim = "animation." + base + ".walk";
            }
        }
        return RawAnimation.begin().thenLoop(cachedWalkAnim);
    }

    public RawAnimation getSpeakingAnimation() {
        String base = getModelBaseName();
        String anim = this.level().isClientSide
                ? net.dandare21.fracturedutils.npc.client.DynamicNpcGeoModel.resolveAnimationName(this, base, "speak")
                : "animation." + base + ".speak";
        return RawAnimation.begin().thenLoop(anim);
    }

    public RawAnimation getActionAnimation(String animName) {
        String base = getModelBaseName();
        String resolved = animName;
        if (!animName.startsWith("animation.") && this.level().isClientSide) {
            resolved = net.dandare21.fracturedutils.npc.client.DynamicNpcGeoModel.resolveAnimationName(this, base, animName);
        }
        return RawAnimation.begin().thenPlay(resolved);
    }

    // GeckoLib GeoEntity implementation
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, event -> {
            if (event.isMoving()) {
                return event.setAndContinue(getMovementAnimation());
            }
            return event.setAndContinue(getIdleAnimation());
        }));

        controllers.add(new AnimationController<>(this, "speaking", 5, event -> {
            if (isSpeaking()) {
                return event.setAndContinue(getSpeakingAnimation());
            }
            return PlayState.STOP;
        }));

        controllers.add(new AnimationController<>(this, "action", 5, event -> {
            if (triggeredAnimation != null && !triggeredAnimation.isEmpty()) {
                return event.setAndContinue(getActionAnimation(triggeredAnimation));
            }
            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
