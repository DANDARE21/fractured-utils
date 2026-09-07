package net.dandare21.fracturedutils.puppet.boss;

import net.dandare21.fracturedutils.puppet.action.AbyssalBarrageAction;
import net.dandare21.fracturedutils.puppet.action.LeapSlamAction;
import net.dandare21.fracturedutils.puppet.capability.IPuppetHandler;
import net.dandare21.fracturedutils.puppet.capability.PuppetCapabilityProvider;
import net.dandare21.fracturedutils.puppet.registry.ModPuppetActions;
import net.dandare21.fracturedutils.puppet.target.ActionTarget;
import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.dandare21.fracturedutils.puppet.capability.PuppetHandlerImpl;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Void Herald Boss - Reference Implementation for Boss Puppet Framework v2.0.
 * Demonstrates GeoEntity GeckoLib integration, standard AI combat goals, and autonomous/orchestrated
 * puppet action dispatching (Void Leap Slam & Abyssal Barrage) via IPuppetHandler capability.
 */
public class VoidHeraldBoss extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final IPuppetHandler puppetHandler = new PuppetHandlerImpl(this);
    private final LazyOptional<IPuppetHandler> puppetHandlerOptional = LazyOptional.of(() -> this.puppetHandler);

    private int specialAttackCooldown = 120;

    public VoidHeraldBoss(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public IPuppetHandler getPuppetHandler() {
        return this.puppetHandler;
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == PuppetCapabilityProvider.PUPPET_HANDLER) {
            return this.puppetHandlerOptional.cast();
        }
        return super.getCapability(cap, side);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 350.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    public static boolean checkSpawnRules(EntityType<VoidHeraldBoss> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (!ServerConfig.isEnableDebugBoss()) {
            return false;
        }
        return Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!this.level().isClientSide && !ServerConfig.isEnableDebugBoss()) {
            FracturedUtils.LOGGER.info("[VoidHeraldBoss] Debug boss entity at {} discarded because 'enableDebugBoss' is false in config.", this.blockPosition());
            this.discard();
        }
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && !ServerConfig.isEnableDebugBoss()) {
            this.discard();
            return;
        }
        super.tick();

        if (!this.level().isClientSide) {
            this.puppetHandler.tick();
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> !(e instanceof VoidHeraldBoss) && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)));
    }

    @Override
    public void checkDespawn() {
        // Boss entities must not despawn when players die, respawn, or move away
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        // Enforce immediate suppression after goalSelector ticks ONLY if explicitly suppressed by the override action:
        if (this.puppetHandler.isAiSuppressed()) {
            this.setTarget(null);
            this.getNavigation().stop();
            this.setSpeed(0.0F);
            this.zza = 0.0F;
            this.xxa = 0.0F;
            Vec3 delta = this.getDeltaMovement();
            if (delta.x != 0.0 || delta.z != 0.0) {
                this.setDeltaMovement(0.0, delta.y, 0.0);
            }
            return;
        }

        if (this.puppetHandler.isNavigationSuppressed() && !this.puppetHandler.hasMoveTarget()) {
            this.getNavigation().stop();
            this.setSpeed(0.0F);
            this.zza = 0.0F;
            this.xxa = 0.0F;
            Vec3 delta = this.getDeltaMovement();
            if (delta.x != 0.0 || delta.z != 0.0) {
                this.setDeltaMovement(0.0, delta.y, 0.0);
            }
        }

        if (this.puppetHandler.isTargetingSuppressed()) {
            this.setTarget(null);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide) {
            // Keep boss active and prevent idle dormancy from vanilla noActionTime >= 100
            this.noActionTime = 0;

            // If AI or actions are suppressed by the override action, do not decrement cooldown or roll autonomous special attacks!
            if (this.puppetHandler.isAiSuppressed() || this.puppetHandler.isActionsSuppressed()) {
                return;
            }

            LivingEntity target = this.getTarget();

            // If target is dead or removed, clear target immediately so boss doesn't get stuck fixated on a dead entity
            if (target != null && (!target.isAlive() || target.isRemoved())) {
                this.setTarget(null);
                target = null;
            }

            if (target != null && target.isAlive()) {
                if (this.specialAttackCooldown > 0) {
                    this.specialAttackCooldown--;
                } else {
                    if (!this.puppetHandler.isPuppetingActive()) {
                        double distanceSq = this.distanceToSqr(target);
                        boolean canLeap = !this.puppetHandler.isNavigationSuppressed();

                        // Choose attack:
                        // If distance > 7 blocks (49 sq), leap slam to close distance if navigation is allowed.
                        // If navigation is suppressed, barrage instead.
                        // If close, 50% chance between super leap slam and abyssal barrage (if can leap).
                        boolean useLeapSlam = canLeap && (distanceSq > 49.0 || this.random.nextBoolean());

                        if (useLeapSlam) {
                            this.puppetHandler.dispatch(
                                    ModPuppetActions.LEAP_SLAM,
                                    new LeapSlamAction.LeapSlamParams(
                                            ActionTarget.fromEntity(target.getUUID()),
                                            6.0,
                                            20.0F,
                                            60,
                                            30
                                    )
                            );
                            this.specialAttackCooldown = 150;
                        } else {
                            this.puppetHandler.dispatch(
                                    ModPuppetActions.ABYSSAL_BARRAGE,
                                    new AbyssalBarrageAction.AbyssalBarrageParams(
                                            100,
                                            20,
                                            0.8
                                    )
                            );
                            this.specialAttackCooldown = 150;
                        }
                    }
                }
            }
        }
    }

    /**
     * External orchestrator entry point allowing sequences or commands to trigger any registered action.
     */
    public void triggerOrchestratedAction(String actionId, ActionTarget target) {
        if ("leap_slam".equalsIgnoreCase(actionId) || "mymod:leap_slam".equalsIgnoreCase(actionId) || "fractured_utils:leap_slam".equalsIgnoreCase(actionId)) {
            this.puppetHandler.dispatch(
                    ModPuppetActions.LEAP_SLAM,
                    new LeapSlamAction.LeapSlamParams(target, 6.0, 18.0F, 60, 25)
            );
        } else if ("abyssal_barrage".equalsIgnoreCase(actionId) || "mymod:abyssal_barrage".equalsIgnoreCase(actionId) || "fractured_utils:abyssal_barrage".equalsIgnoreCase(actionId)) {
            this.puppetHandler.dispatch(
                    ModPuppetActions.ABYSSAL_BARRAGE,
                    new AbyssalBarrageAction.AbyssalBarrageParams(100, 20, 0.75)
            );
        }
    }


    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Base locomotion controller
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("animation.void_herald.walk"));
            }
            return state.setAndContinue(RawAnimation.begin().thenLoop("animation.void_herald.idle"));
        }));

        // Attack controller triggered via ClientboundPuppetAnimPacket
        controllers.add(new AnimationController<>(this, "attack", 5, state -> PlayState.STOP)
                .triggerableAnim("charge", RawAnimation.begin().thenPlay("animation.void_herald.charge"))
                .triggerableAnim("leap", RawAnimation.begin().thenPlay("animation.void_herald.leap"))
                .triggerableAnim("stunned", RawAnimation.begin().thenLoop("animation.void_herald.stunned"))
                .triggerableAnim("barrage", RawAnimation.begin().thenLoop("animation.void_herald.barrage"))
                .triggerableAnim("idle", RawAnimation.begin().thenPlay("animation.void_herald.idle"))
        );
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
