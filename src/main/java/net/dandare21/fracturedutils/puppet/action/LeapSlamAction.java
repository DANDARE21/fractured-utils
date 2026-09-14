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
 * Lifecycle:
 * 1. INDICATION: Mob stands on the ground, telegraphs with the circular ground indicator, charges energy.
 * 2. JUMP: Launches into the air, ascends to the apex, and plunges down as a guided meteor.
 * 3. ATTACK (SLAM): Triggers the moment the entity slams the ground (the Keyframe Square on the timeline).
 * 4. RECOVERY: Stunned recovery before returning to idle.
 */
public class LeapSlamAction extends PuppetActionType<LeapSlamAction.LeapSlamParams> {

    public static final ResourceLocation ID = new ResourceLocation(FracturedUtils.MOD_ID, "leap_slam");

    public record LeapSlamParams(
            ActionTarget target,
            double slamRadius,
            float damage,
            int indicationTicks,
            int jumpTicks,
            int durationTicks,
            int recoveryTicks
    ) {
        public static final Codec<LeapSlamParams> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ActionTarget.CODEC.fieldOf("target").forGetter(LeapSlamParams::target),
                        Codec.DOUBLE.optionalFieldOf("slamRadius", 6.0).forGetter(LeapSlamParams::slamRadius),
                        Codec.FLOAT.optionalFieldOf("damage", 20.0F).forGetter(LeapSlamParams::damage),
                        Codec.INT.optionalFieldOf("indicationTicks", -1).forGetter(LeapSlamParams::indicationTicks),
                        Codec.INT.optionalFieldOf("windupTicks", -1).forGetter(p -> p.indicationTicks),
                        Codec.INT.optionalFieldOf("windupMs", -1).forGetter(p -> p.indicationTicks * 50),
                        Codec.INT.optionalFieldOf("jumpTicks", -1).forGetter(LeapSlamParams::jumpTicks),
                        Codec.INT.optionalFieldOf("jumpMs", -1).forGetter(p -> p.jumpTicks * 50),
                        Codec.INT.optionalFieldOf("durationTicks", -1).forGetter(LeapSlamParams::durationTicks),
                        Codec.INT.optionalFieldOf("durationMs", -1).forGetter(p -> p.durationTicks * 50),
                        Codec.INT.optionalFieldOf("recoveryTicks", -1).forGetter(LeapSlamParams::recoveryTicks),
                        Codec.INT.optionalFieldOf("recoveryMs", -1).forGetter(p -> p.recoveryTicks * 50)
                ).apply(instance, (target, slamRadius, damage, indicationTicks, windupTicks, windupMs, jumpTicks, jumpMs, durationTicks, durationMs, recoveryTicks, recoveryMs) -> {
                    // Indication / Windup: Allow 0 ticks if explicitly specified
                    int indTicks = 10;
                    if (indicationTicks >= 0) {
                        indTicks = indicationTicks;
                    } else if (windupTicks >= 0) {
                        indTicks = windupTicks;
                    } else if (windupMs >= 0) {
                        indTicks = windupMs / 50;
                    }

                    // Jump: Allow 0 ticks if explicitly specified
                    int jmpTicks = 30;
                    if (jumpTicks >= 0) {
                        jmpTicks = jumpTicks;
                    } else if (jumpMs >= 0) {
                        jmpTicks = jumpMs / 50;
                    } else if (windupTicks > 0 && indicationTicks < 0 && windupMs < 0) {
                        // Legacy single windupTicks input
                        indTicks = Math.max(0, windupTicks / 4);
                        jmpTicks = Math.max(0, windupTicks - indTicks);
                    }

                    // Duration (active slam): Allow 0 ticks if explicitly specified
                    int durTicks = 16;
                    if (durationTicks >= 0) {
                        durTicks = durationTicks;
                    } else if (durationMs >= 0) {
                        durTicks = durationMs / 50;
                    }

                    // Recovery: Allow 0 ticks if explicitly specified
                    int recTicks = 12;
                    if (recoveryTicks >= 0) {
                        recTicks = recoveryTicks;
                    } else if (recoveryMs >= 0) {
                        recTicks = recoveryMs / 50;
                    }

                    return new LeapSlamParams(target, slamRadius, damage, indTicks, jmpTicks, durTicks, recTicks);
                })
        );

        // Backward-compatibility constructors
        public LeapSlamParams(ActionTarget target, double slamRadius, float damage, int windupTicks, int durationTicks, int recoveryTicks) {
            this(target, slamRadius, damage, Math.max(0, windupTicks / 4), Math.max(0, windupTicks - (windupTicks / 4)), durationTicks, recoveryTicks);
        }

        public LeapSlamParams(ActionTarget target, double slamRadius, float damage, int windupTicks, int recoveryTicks) {
            this(target, slamRadius, damage, windupTicks, 16, recoveryTicks);
        }

        public int totalWindupTicks() {
            return Math.max(0, indicationTicks) + Math.max(0, jumpTicks);
        }

        public int totalDurationTicks() {
            return totalWindupTicks() + Math.max(0, durationTicks) + Math.max(0, recoveryTicks);
        }
    }

    public LeapSlamAction() {
        super(ID, LeapSlamParams.CODEC, Instance::new);
    }

    public static class Instance extends PuppetActionInstance<LeapSlamParams> {

        private enum LeapStage {
            INDICATION,
            ASCENT,
            DESCENT
        }

        private Vec3 markedTargetPos = null;
        private Vec3 startJumpPos = null;
        private double calculatedApexHeight = 12.0;
        private LeapStage leapStage = LeapStage.INDICATION;
        private int activeSubTicks = 0;
        private boolean hasImpacted = false;
        private int indicatorId = -1;

        public Instance(Mob mob, LeapSlamParams params) {
            super(mob, params);
        }

        @Override
        public void onStart() {
            if (params.totalWindupTicks() <= 0) {
                transitionTo(Phase.ACTIVE);
            } else {
                super.onStart();
            }
        }

        @Override
        public void onPhaseTransition(Phase newPhase) {
            switch (newPhase) {
                case WINDUP -> {
                    this.hasImpacted = false;
                    this.startJumpPos = null;
                    this.activeSubTicks = 0;
                    this.indicatorId = -1;
                    this.leapStage = params.indicationTicks() > 0 ? LeapStage.INDICATION : LeapStage.ASCENT;
                    if (this.mob != null) {
                        this.mob.setNoGravity(false);
                    }
                    if (params.indicationTicks() > 0) {
                        broadcastAnim("attack", "charge");
                        if (this.mob != null && this.mob.level() instanceof ServerLevel level) {
                            level.playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                                    SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.8F, 0.6F);
                        }
                    }
                }
                case ACTIVE -> {
                    this.activeSubTicks = 0;
                    if (this.mob != null) {
                        this.mob.setNoGravity(false);
                    }
                    // The slam attack triggers when the entity slams the ground!
                    if (!this.hasImpacted) {
                        this.hasImpacted = true;
                        Vec3 targetPos = this.markedTargetPos != null ? this.markedTargetPos : (this.mob != null ? this.mob.position() : Vec3.ZERO);
                        if (this.mob != null && this.mob.level() instanceof ServerLevel serverLevel) {
                            performSlamImpact(serverLevel, targetPos);
                        }
                    }
                    if (params.durationTicks() <= 0) {
                        transitionTo(Phase.RECOVERY);
                    }
                }
                case RECOVERY -> {
                    if (params.recoveryTicks() <= 0) {
                        transitionTo(Phase.IDLE);
                        return;
                    }
                    broadcastAnim("attack", "stunned");
                    if (this.mob != null) {
                        this.mob.setNoGravity(false);
                        this.mob.setDeltaMovement(Vec3.ZERO);
                        this.mob.fallDistance = 0.0F;
                    }
                }
                case IDLE -> {
                    broadcastAnim("attack", "idle");
                    if (this.mob != null) {
                        this.mob.setNoGravity(false);
                    }
                    if (this.indicatorId != -1) {
                        removeCircleIndicator(this.indicatorId, this.markedTargetPos);
                        this.indicatorId = -1;
                    }
                }
            }
        }

        @Override
        public void stop() {
            if (this.mob != null) {
                this.mob.setNoGravity(false);
            }
            if (this.indicatorId != -1) {
                removeCircleIndicator(this.indicatorId, this.markedTargetPos);
                this.indicatorId = -1;
            }
            super.stop();
        }

        private void triggerLaunchEffects(ServerLevel serverLevel, Vec3 launchPos) {
            // Launch explosion and rocket whoosh
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, launchPos.x, launchPos.y, launchPos.z, 1, 0, 0, 0, 0);
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, launchPos.x, launchPos.y, launchPos.z, 20, 0.8, 0.2, 0.8, 0.05);
            serverLevel.playSound(null, launchPos.x, launchPos.y, launchPos.z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.5F, 1.2F);
            serverLevel.playSound(null, launchPos.x, launchPos.y, launchPos.z,
                    SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 3.0F, 0.5F);

            this.mob.setNoGravity(true);
            this.mob.setOnGround(false);
            this.mob.hasImpulse = true;
            this.mob.hurtMarked = true;
            this.mob.fallDistance = 0.0F;
            broadcastAnim("attack", "leap");
        }

        private double calculateApexHeight(ServerLevel level, Vec3 startP, Vec3 targetP, int jumpTicks) {
            double dx = targetP.x - startP.x;
            double dz = targetP.z - startP.z;
            double horizDist = Math.sqrt(dx * dx + dz * dz);

            // Natural height scaling: preserves ~12 blocks at default 30 ticks, scales from 6 to 26 blocks
            double durationFactor = (jumpTicks - 20) * 0.12;
            double distFactor = horizDist * 0.20;
            double baseApex = Math.max(6.0, Math.min(26.0, 8.0 + distFactor + durationFactor));
            double dyOffset = Math.max(0.0, (targetP.y - startP.y) * 0.5);
            double desiredApex = baseApex + dyOffset;

            // Headroom check: Raycast up from the midpoint to detect solid ceilings
            Vec3 midP = new Vec3((startP.x + targetP.x) * 0.5, Math.max(startP.y, targetP.y), (startP.z + targetP.z) * 0.5);
            double ceilingClearance = checkCeilingClearance(level, midP, desiredApex + 2.0);
            if (ceilingClearance < desiredApex) {
                return Math.max(4.0, ceilingClearance - 1.5);
            }
            return desiredApex;
        }

        private double checkCeilingClearance(ServerLevel level, Vec3 pos, double maxCheck) {
            int blockX = Mth.floor(pos.x);
            int startY = Mth.floor(pos.y + 1.0);
            int blockZ = Mth.floor(pos.z);
            int limitY = Math.min(level.getMaxBuildHeight() - 1, startY + (int) Math.ceil(maxCheck));

            for (int y = startY; y <= limitY; y++) {
                net.minecraft.core.BlockPos bp = new net.minecraft.core.BlockPos(blockX, y, blockZ);
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(bp);
                if (state.blocksMotion()) {
                    return Math.max(2.0, y - pos.y);
                }
            }
            return maxCheck;
        }

        @Override
        public void onWindupTick(int tick) {
            if (this.mob == null || !(this.mob.level() instanceof ServerLevel serverLevel)) return;

            int indicationTicks = Math.max(0, params.indicationTicks());
            int jumpTicks = Math.max(0, params.jumpTicks());
            int totalWindup = indicationTicks + jumpTicks;

            // Target position tracking: Continuously follow the target during ground indication
            if (this.markedTargetPos == null || tick <= indicationTicks) {
                Vec3 curTarget = params.target().getPosition(serverLevel);
                if (curTarget != null) {
                    this.markedTargetPos = curTarget;
                } else if (this.markedTargetPos == null) {
                    this.markedTargetPos = this.mob.position();
                }
            }

            Vec3 targetPos = this.markedTargetPos;

            // Spawn or update circular ground attack indicator
            if (this.indicatorId == -1) {
                this.indicatorId = showCircleIndicator(
                        targetPos,
                        params.slamRadius(),
                        Math.max(1, totalWindup),
                        net.dandare21.fracturedutils.puppet.util.AttackIndicatorUtils.COLOR_VOID
                );
            } else if (tick <= indicationTicks) {
                updateCircleIndicator(this.indicatorId, targetPos, params.slamRadius());
            }

            // Boss cannot take fall damage during leap
            this.mob.fallDistance = 0.0F;

            // 1. INDICATION PHASE: Mob stands on the ground charging & telegraphing
            if (tick < indicationTicks) {
                this.leapStage = LeapStage.INDICATION;
                double dx = targetPos.x - this.mob.getX();
                double dz = targetPos.z - this.mob.getZ();
                float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                this.mob.setYRot(targetYaw);
                this.mob.setYHeadRot(targetYaw);
                this.mob.getLookControl().setLookAt(targetPos.x, targetPos.y + 1.0, targetPos.z, 40.0F, 40.0F);

                this.mob.setDeltaMovement(new Vec3(0, Math.min(0, this.mob.getDeltaMovement().y), 0));

                // Ambient portal energy particles at marked ground zone
                serverLevel.sendParticles(ParticleTypes.PORTAL, targetPos.x, targetPos.y + 0.1, targetPos.z, 3, 0.6, 0.1, 0.6, 0.05);

                // Pulsing sound with rising pitch as indication nears launch
                if (tick % 4 == 0) {
                    float pitch = 0.8F + ((float) tick / Math.max(1, indicationTicks)) * 0.8F;
                    serverLevel.playSound(null, targetPos.x, targetPos.y, targetPos.z, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.4F, pitch);
                }
            }
            // 2. JUMP LAUNCH MOMENT: Triggered when indication ends and jumpTicks > 0
            else if (this.startJumpPos == null && jumpTicks > 0) {
                this.startJumpPos = this.mob.position();
                this.calculatedApexHeight = calculateApexHeight(serverLevel, this.startJumpPos, targetPos, jumpTicks);
                triggerLaunchEffects(serverLevel, this.startJumpPos);
                this.leapStage = LeapStage.ASCENT;

                // Set initial physics velocity matching the arc tangent towards next tick
                double nextProgress = Math.min(1.0, 1.0 / jumpTicks);
                double nextArcY = 4.0 * nextProgress * (1.0 - nextProgress) * this.calculatedApexHeight;
                double nextX = this.startJumpPos.x + (targetPos.x - this.startJumpPos.x) * nextProgress;
                double nextY = this.startJumpPos.y + (targetPos.y - this.startJumpPos.y) * nextProgress + nextArcY;
                double nextZ = this.startJumpPos.z + (targetPos.z - this.startJumpPos.z) * nextProgress;

                this.mob.setDeltaMovement(new Vec3(nextX - this.startJumpPos.x, nextY - this.startJumpPos.y, nextZ - this.startJumpPos.z));
            }
            // 3. AIRBORNE LEAP TRAJECTORY: Parabolic flight lasting the EXACT duration of jumpTicks
            else if (tick < totalWindup && jumpTicks > 0) {
                this.mob.setNoGravity(true);
                this.mob.setOnGround(false);
                this.mob.fallDistance = 0.0F;

                int elapsedJump = tick - indicationTicks;
                double progress = (double) elapsedJump / (double) jumpTicks;
                double nextProgress = Math.min(1.0, (double) (elapsedJump + 1) / (double) jumpTicks);

                Vec3 startP = this.startJumpPos != null ? this.startJumpPos : this.mob.position();
                double apexHeight = this.calculatedApexHeight;

                // Smooth parabolic arc height above linear interpolation line
                double arcY = 4.0 * progress * (1.0 - progress) * apexHeight;
                double curX = startP.x + (targetPos.x - startP.x) * progress;
                double curY = startP.y + (targetPos.y - startP.y) * progress + arcY;
                double curZ = startP.z + (targetPos.z - startP.z) * progress;

                this.mob.setPos(curX, curY, curZ);

                // Compute delta movement towards next tick position for ultra-smooth client-side interpolation
                double nextArcY = 4.0 * nextProgress * (1.0 - nextProgress) * apexHeight;
                double nextX = startP.x + (targetPos.x - startP.x) * nextProgress;
                double nextY = startP.y + (targetPos.y - startP.y) * nextProgress + nextArcY;
                double nextZ = startP.z + (targetPos.z - startP.z) * nextProgress;

                this.mob.setDeltaMovement(new Vec3(nextX - curX, nextY - curY, nextZ - curZ));
                this.mob.hasImpulse = true;
                this.mob.hurtMarked = true;

                // Look towards impact target while airborne
                double dx = targetPos.x - curX;
                double dz = targetPos.z - curZ;
                float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                this.mob.setYRot(targetYaw);
                this.mob.setYHeadRot(targetYaw);

                if (progress < 0.5) {
                    this.leapStage = LeapStage.ASCENT;
                    serverLevel.sendParticles(ParticleTypes.PORTAL, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                            8, 0.5, 0.5, 0.5, 0.1);
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                            4, 0.3, 0.3, 0.3, 0.02);
                } else {
                    if (this.leapStage != LeapStage.DESCENT) {
                        this.leapStage = LeapStage.DESCENT;
                        serverLevel.playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                                SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.HOSTILE, 3.5F, 0.5F);
                    }
                    serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.mob.getX(), this.mob.getY() + 1.0, this.mob.getZ(),
                            6, 0.3, 0.6, 0.3, 0.02);
                    serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, this.mob.getX(), this.mob.getY() + 0.5, this.mob.getZ(),
                            5, 0.3, 0.3, 0.3, 0.04);

                    if (elapsedJump % 20 == 0) {
                        serverLevel.playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                                SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 1.8F, 0.6F);
                    }
                }
            }
            // 4. SLAM GROUND IMPACT: Triggers precisely at totalWindup!
            else {
                this.mob.setNoGravity(false);
                this.mob.setDeltaMovement(Vec3.ZERO);
                this.mob.setPos(targetPos.x, targetPos.y, targetPos.z);
                this.mob.setOnGround(true);
                this.mob.fallDistance = 0.0F;
                transitionTo(Phase.ACTIVE);
            }
        }

        @Override
        public void onActiveTick(int tick) {
            if (this.mob == null || !(this.mob.level() instanceof ServerLevel serverLevel)) return;

            this.activeSubTicks++;
            Vec3 targetPos = this.markedTargetPos != null ? this.markedTargetPos : this.mob.position();

            // Root mob on the ground at the slam impact position
            this.mob.setDeltaMovement(new Vec3(0, Math.min(0, this.mob.getDeltaMovement().y), 0));
            this.mob.fallDistance = 0.0F;

            int durationTicks = Math.max(1, params.durationTicks());
            double progress = (double) tick / durationTicks;

            // Ground tremor fissure effects radiating during the active slam attack duration
            if (tick % 3 == 0) {
                double radius = params.slamRadius() * Math.min(1.0, 0.3 + (0.7 * progress));
                int count = 16;
                for (int i = 0; i < count; i++) {
                    double angle = (2 * Math.PI / count) * i;
                    double px = targetPos.x + Math.cos(angle) * radius;
                    double pz = targetPos.z + Math.sin(angle) * radius;
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, targetPos.y + 0.1, pz, 1, 0, 0.02, 0, 0.01);
                    serverLevel.sendParticles(ParticleTypes.SMOKE, px, targetPos.y + 0.1, pz, 1, 0, 0.04, 0, 0.02);
                }
            }

            if (tick % 6 == 0) {
                serverLevel.playSound(null, targetPos.x, targetPos.y, targetPos.z,
                        SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 1.2F, 0.6F);
            }

            // Periodic fissure burn damage for extended duration
            if (tick % 10 == 0 && tick > 0) {
                double curRadius = params.slamRadius() * Math.min(1.0, 0.3 + (0.7 * progress));
                AABB fissureBox = new AABB(
                        targetPos.x - curRadius, targetPos.y - 1.0, targetPos.z - curRadius,
                        targetPos.x + curRadius, targetPos.y + 2.0, targetPos.z + curRadius
                );
                List<LivingEntity> inFissure = serverLevel.getEntitiesOfClass(LivingEntity.class, fissureBox,
                        e -> e != this.mob && e.isAlive() && e.distanceToSqr(targetPos.x, targetPos.y, targetPos.z) <= curRadius * curRadius);
                for (LivingEntity victim : inFissure) {
                    victim.hurt(this.mob.damageSources().mobAttack(this.mob), Math.max(1.0F, params.damage() * 0.1F));
                    victim.setSecondsOnFire(2);
                }
            }

            // End of active attack duration -> transition to RECOVERY
            if (tick >= params.durationTicks()) {
                transitionTo(Phase.RECOVERY);
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

            // Ground crack smoke particles radiating outwards
            for (int i = 0; i < 32; i++) {
                double angle = (2 * Math.PI / 32) * i;
                double dist = (i % 2 == 0) ? radius : radius * 0.5;
                double px = impactPos.x + Math.cos(angle) * dist;
                double pz = impactPos.z + Math.sin(angle) * dist;
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, impactPos.y + 0.1, pz, 1, 0, 0.08, 0, 0.02);
            }

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
