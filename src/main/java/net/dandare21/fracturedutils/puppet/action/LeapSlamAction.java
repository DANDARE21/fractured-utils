package net.dandare21.fracturedutils.puppet.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.puppet.fsm.Phase;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionInstance;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionType;
import net.dandare21.fracturedutils.puppet.target.ActionTarget;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Void Leap Slam Action (fractured_utils:leap_slam).
 * Winds up while marking the ground position, leaps REALLY high into the sky towards the apex,
 * plunges down as a meteor onto the marked position, explodes on impact with AoE damage/knockback,
 * and enters a brief stunned recovery phase.
 */
public class LeapSlamAction extends PuppetActionType<LeapSlamAction.LeapSlamParams> {

    public static final ResourceLocation ID = new ResourceLocation(FracturedUtils.MOD_ID, "leap_slam");

    public record LeapSlamParams(
            ActionTarget target,
            double slamRadius,
            float damage,
            int windupTicks,
            int recoveryTicks
    ) {
        public static final Codec<LeapSlamParams> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ActionTarget.CODEC.fieldOf("target").forGetter(LeapSlamParams::target),
                        Codec.DOUBLE.optionalFieldOf("slamRadius", 6.0).forGetter(LeapSlamParams::slamRadius),
                        Codec.FLOAT.optionalFieldOf("damage", 20.0F).forGetter(LeapSlamParams::damage),
                        Codec.INT.optionalFieldOf("windupTicks", 60).forGetter(LeapSlamParams::windupTicks),
                        Codec.INT.optionalFieldOf("recoveryTicks", 30).forGetter(LeapSlamParams::recoveryTicks)
                ).apply(instance, LeapSlamParams::new)
        );
    }

    public LeapSlamAction() {
        super(ID, LeapSlamParams.CODEC, Instance::new);
    }

    public static class Instance extends PuppetActionInstance<LeapSlamParams> {

        private enum LeapStage {
            ASCENT,
            DESCENT
        }

        private Vec3 markedTargetPos = null;
        private LeapStage leapStage = LeapStage.ASCENT;
        private int activeSubTicks = 0;
        private boolean hasImpacted = false;
        private int indicatorId = -1;

        public Instance(Mob mob, LeapSlamParams params) {
            super(mob, params);
        }

        @Override
        public void onPhaseTransition(Phase newPhase) {
            switch (newPhase) {
                case WINDUP -> {
                    this.hasImpacted = false;
                    this.activeSubTicks = 0;
                    this.indicatorId = -1;
                    this.leapStage = LeapStage.ASCENT;
                    broadcastAnim("attack", "charge");
                    if (this.mob != null && this.mob.level() instanceof ServerLevel level) {
                        level.playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                                SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.8F, 0.6F);
                    }
                }
                case ACTIVE -> {
                    this.activeSubTicks = 0;
                    this.leapStage = LeapStage.ASCENT;
                    broadcastAnim("attack", "leap");
                    startSuperJump();
                }
                case RECOVERY -> {
                    broadcastAnim("attack", "stunned");
                    if (this.mob != null) {
                        this.mob.setDeltaMovement(Vec3.ZERO);
                        this.mob.fallDistance = 0.0F;
                    }
                }
                case IDLE -> {
                    broadcastAnim("attack", "idle");
                    if (this.indicatorId != -1) {
                        removeCircleIndicator(this.indicatorId, this.markedTargetPos);
                        this.indicatorId = -1;
                    }
                }
            }
        }

        @Override
        public void stop() {
            if (this.indicatorId != -1) {
                removeCircleIndicator(this.indicatorId, this.markedTargetPos);
                this.indicatorId = -1;
            }
            super.stop();
        }

        private void startSuperJump() {
            if (this.mob == null || !(this.mob.level() instanceof ServerLevel serverLevel)) return;

            // Explosion and rocket whoosh at launch site
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.mob.getX(), this.mob.getY(), this.mob.getZ(), 1, 0, 0, 0, 0);
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.mob.getX(), this.mob.getY(), this.mob.getZ(), 20, 0.8, 0.2, 0.8, 0.05);
            serverLevel.playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.5F, 1.2F);
            serverLevel.playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                    SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 3.0F, 0.5F);

            // Initial powerful upward launch
            this.mob.setDeltaMovement(new Vec3(0, 1.6, 0));
            this.mob.hasImpulse = true;
            this.mob.hurtMarked = true;
            this.mob.fallDistance = 0.0F;
        }

        @Override
        public void onWindupTick(int tick) {
            if (this.mob == null || !(this.mob.level() instanceof ServerLevel serverLevel)) return;

            // Lock position very early (first 4 ticks max) so the ground indicator telegraphs the fixed danger zone immediately, giving the player ample time to escape
            int lockTick = Math.min(4, Math.max(1, (int) (params.windupTicks() * 0.08)));
            if (tick <= lockTick || this.markedTargetPos == null) {
                this.markedTargetPos = params.target().getPosition(serverLevel);
            }

            Vec3 targetPos = this.markedTargetPos;

            // Spawn or update circular ground attack indicator
            if (this.indicatorId == -1) {
                this.indicatorId = showCircleIndicator(
                        targetPos,
                        params.slamRadius(),
                        params.windupTicks() + 45,
                        net.dandare21.fracturedutils.puppet.util.AttackIndicatorUtils.COLOR_VOID
                );
            } else if (tick <= lockTick) {
                updateCircleIndicator(this.indicatorId, targetPos, params.slamRadius());
            }

            // Face mob toward marked position
            double dx = targetPos.x - this.mob.getX();
            double dz = targetPos.z - this.mob.getZ();
            float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            this.mob.setYRot(targetYaw);
            this.mob.setYHeadRot(targetYaw);
            this.mob.getLookControl().setLookAt(targetPos.x, targetPos.y + 1.0, targetPos.z, 40.0F, 40.0F);

            // Root mob in place while charging
            this.mob.setDeltaMovement(new Vec3(0, Math.min(0, this.mob.getDeltaMovement().y), 0));

            // Ambient portal energy particles at marked ground zone
            serverLevel.sendParticles(ParticleTypes.PORTAL, targetPos.x, targetPos.y + 0.1, targetPos.z, 3, 0.6, 0.1, 0.6, 0.05);

            // Pulsing sound with rising pitch as windup approaches apex launch
            if (tick % 8 == 0) {
                float pitch = 0.8F + ((float) tick / params.windupTicks()) * 1.0F;
                serverLevel.playSound(null, targetPos.x, targetPos.y, targetPos.z, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.4F, pitch);
            }

            if (tick >= params.windupTicks()) {
                transitionTo(Phase.ACTIVE);
            }
        }

        @Override
        public void onActiveTick(int tick) {
            if (this.mob == null || !(this.mob.level() instanceof ServerLevel serverLevel)) return;

            this.activeSubTicks++;
            Vec3 targetPos = this.markedTargetPos != null ? this.markedTargetPos : params.target().getPosition(serverLevel);

            // Boss cannot take fall damage during leap
            this.mob.fallDistance = 0.0F;

            if (this.leapStage == LeapStage.ASCENT) {
                // ASCENT: Rocket high into the air (~20 blocks) while guiding horizontally toward marked target
                int ascentDuration = 18;
                double progress = (double) this.activeSubTicks / ascentDuration;

                // Vertical velocity curve: strong upward thrust that smoothly eases at the apex
                double vy = Math.max(0.2, 1.4 - (progress * 1.1));

                // Horizontal velocity toward marked target so boss aligns directly above it
                double dx = targetPos.x - this.mob.getX();
                double dz = targetPos.z - this.mob.getZ();
                double remainingAscentTicks = Math.max(1, ascentDuration - this.activeSubTicks);
                double vx = dx / remainingAscentTicks;
                double vz = dz / remainingAscentTicks;

                this.mob.setDeltaMovement(new Vec3(vx, vy, vz));
                this.mob.hasImpulse = true;
                this.mob.hurtMarked = true;

                // Void trail particles while rising
                serverLevel.sendParticles(ParticleTypes.PORTAL, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                        8, 0.5, 0.5, 0.5, 0.1);
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                        4, 0.3, 0.3, 0.3, 0.02);

                // Transition to DESCENT after completing the high jump ascent
                if (this.activeSubTicks >= ascentDuration) {
                    this.leapStage = LeapStage.DESCENT;
                    this.activeSubTicks = 0;
                    serverLevel.playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                            SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.HOSTILE, 3.5F, 0.5F);
                }

            } else {
                // DESCENT: High-velocity dive directly down onto the marked ground position
                double dx = targetPos.x - this.mob.getX();
                double dz = targetPos.z - this.mob.getZ();
                double hDist = Math.sqrt(dx * dx + dz * dz);

                // Horizontal steering directly towards the target center
                double hSpeed = Math.min(1.8, hDist * 0.6);
                double vx = hDist > 0.05 ? (dx / hDist) * hSpeed : 0.0;
                double vz = hDist > 0.05 ? (dz / hDist) * hSpeed : 0.0;

                // Fast downward slam plunge
                double vy = -2.2;

                this.mob.setDeltaMovement(new Vec3(vx, vy, vz));
                this.mob.hasImpulse = true;
                this.mob.hurtMarked = true;

                // Meteor plunge particle trails
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.mob.getX(), this.mob.getY() + 1.0, this.mob.getZ(),
                        6, 0.3, 0.6, 0.3, 0.02);
                serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, this.mob.getX(), this.mob.getY() + 0.5, this.mob.getZ(),
                        5, 0.3, 0.3, 0.3, 0.04);

                // Detect ground impact
                boolean hitGround = this.mob.onGround() || this.mob.getY() <= targetPos.y + 1.2 || this.activeSubTicks > 30;
                if (!this.hasImpacted && hitGround) {
                    this.hasImpacted = true;
                    performSlamImpact(serverLevel, targetPos);
                    transitionTo(Phase.RECOVERY);
                }
            }
        }

        private void performSlamImpact(ServerLevel serverLevel, Vec3 impactPos) {
            // Remove ground attack indicator
            if (this.indicatorId != -1) {
                removeCircleIndicator(this.indicatorId, impactPos);
                this.indicatorId = -1;
            }

            // Position snap exactly onto target position
            this.mob.setPos(impactPos.x, impactPos.y, impactPos.z);
            this.mob.setDeltaMovement(Vec3.ZERO);
            this.mob.fallDistance = 0.0F;

            // Audio: thunderous multi-layered explosion
            serverLevel.playSound(null, impactPos.x, impactPos.y, impactPos.z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.6F);
            serverLevel.playSound(null, impactPos.x, impactPos.y, impactPos.z,
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 2.5F, 0.8F);
            serverLevel.playSound(null, impactPos.x, impactPos.y, impactPos.z,
                    SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 2.0F, 0.5F);

            // VFX: Center impact burst
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impactPos.x, impactPos.y, impactPos.z, 2, 0, 0, 0, 0);
            serverLevel.sendParticles(ParticleTypes.FLASH, impactPos.x, impactPos.y + 1.0, impactPos.z, 1, 0, 0, 0, 0);

            double radius = params.slamRadius();

            // AoE Damage & Violent Outward/Upward Knockback
            AABB bounds = new AABB(
                    impactPos.x - radius, impactPos.y - 2.0, impactPos.z - radius,
                    impactPos.x + radius, impactPos.y + 4.0, impactPos.z + radius
            );
            List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, bounds,
                    e -> e != this.mob && e.isAlive());

            for (LivingEntity victim : victims) {
                double kx = victim.getX() - impactPos.x;
                double kz = victim.getZ() - impactPos.z;
                double dist = Math.sqrt(kx * kx + kz * kz);

                // Strict circular check matching the visual indicator
                if (dist > radius) {
                    continue;
                }

                victim.hurt(this.mob.damageSources().mobAttack(this.mob), params.damage());

                double force = 1.6 * (1.0 - (dist / (radius + 1.0)));
                double knockX = (kx / Math.max(0.1, dist)) * Math.max(0.6, force);
                double knockZ = (kz / Math.max(0.1, dist)) * Math.max(0.6, force);
                double knockY = 0.75; // Launches victims into the air!

                victim.setDeltaMovement(new Vec3(knockX, knockY, knockZ));
                victim.hurtMarked = true;
            }
        }

        @Override
        public void onRecoveryTick(int tick) {
            if (this.mob != null) {
                this.mob.setDeltaMovement(new Vec3(0, Math.min(0, this.mob.getDeltaMovement().y), 0));
                this.mob.fallDistance = 0.0F;
            }

            if (tick >= params.recoveryTicks()) {
                transitionTo(Phase.IDLE);
            }
        }
    }
}
