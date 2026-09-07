package net.dandare21.fracturedutils.puppet.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.puppet.fsm.Phase;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionInstance;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.phys.Vec3;

/**
 * Abyssal Barrage Action (mymod:abyssal_barrage / fractured_utils:abyssal_barrage).
 * Roots mob in place, channeling multiple waves of 8 WitherSkull projectiles in a rotating ring pattern.
 */
public class AbyssalBarrageAction extends PuppetActionType<AbyssalBarrageAction.AbyssalBarrageParams> {

    public static final ResourceLocation ID = new ResourceLocation(FracturedUtils.MOD_ID, "abyssal_barrage");

    public record AbyssalBarrageParams(
            int channelTicks,
            int waveInterval,
            double projectileSpeed
    ) {
        public static final Codec<AbyssalBarrageParams> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.INT.optionalFieldOf("channelTicks", 100).forGetter(AbyssalBarrageParams::channelTicks),
                        Codec.INT.optionalFieldOf("waveInterval", 20).forGetter(AbyssalBarrageParams::waveInterval),
                        Codec.DOUBLE.optionalFieldOf("projectileSpeed", 0.75).forGetter(AbyssalBarrageParams::projectileSpeed)
                ).apply(instance, AbyssalBarrageParams::new)
        );
    }

    public AbyssalBarrageAction() {
        super(ID, AbyssalBarrageParams.CODEC, Instance::new);
    }

    public static class Instance extends PuppetActionInstance<AbyssalBarrageParams> {
        private int waveCount = 0;

        public Instance(Mob mob, AbyssalBarrageParams params) {
            super(mob, params);
        }

        @Override
        public void onStart() {
            // Direct transition to ACTIVE channeling
            transitionTo(Phase.ACTIVE);
        }

        @Override
        public void onPhaseTransition(Phase newPhase) {
            switch (newPhase) {
                case ACTIVE -> {
                    broadcastAnim("attack", "barrage");
                    if (this.mob != null) {
                        this.mob.setDeltaMovement(Vec3.ZERO);
                    }
                }
                case IDLE -> {
                    broadcastAnim("attack", "idle");
                }
                default -> {}
            }
        }

        @Override
        public void onWindupTick(int tick) {
            transitionTo(Phase.ACTIVE);
        }

        @Override
        public void onActiveTick(int tick) {
            if (this.mob == null || !(this.mob.level() instanceof ServerLevel serverLevel)) return;

            // Keep mob rooted during channeling
            this.mob.setDeltaMovement(new Vec3(0, Math.min(0, this.mob.getDeltaMovement().y), 0));

            // Atmospheric particles around boss
            serverLevel.sendParticles(
                    ParticleTypes.SMOKE,
                    this.mob.getX(), this.mob.getY() + 0.5, this.mob.getZ(),
                    3, 0.5, 0.5, 0.5, 0.02
            );

            // Fire wave every waveInterval ticks
            int interval = Math.max(1, params.waveInterval());
            if (tick % interval == 0) {
                fireProjectileWave(serverLevel);
            }

            if (tick >= params.channelTicks()) {
                transitionTo(Phase.IDLE);
            }
        }

        private void fireProjectileWave(ServerLevel serverLevel) {
            int projectileCount = 8;
            double rotationOffset = this.waveCount * (Math.PI / 8.0);
            this.waveCount++;

            double speed = params.projectileSpeed();
            double spawnY = this.mob.getY() + this.mob.getEyeHeight() * 0.7;

            for (int i = 0; i < projectileCount; i++) {
                double angle = rotationOffset + (2 * Math.PI * i) / projectileCount;
                double dirX = Math.cos(angle);
                double dirZ = Math.sin(angle);

                double spawnX = this.mob.getX() + dirX * 1.2;
                double spawnZ = this.mob.getZ() + dirZ * 1.2;

                WitherSkull skull = new WitherSkull(serverLevel, this.mob, dirX * speed, 0.0, dirZ * speed);
                skull.setPos(spawnX, spawnY, spawnZ);
                skull.setDangerous(false);
                serverLevel.addFreshEntity(skull);
            }

            serverLevel.playSound(
                    null,
                    this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                    SoundEvents.WITHER_SHOOT,
                    SoundSource.HOSTILE,
                    1.5F,
                    0.9F + (this.waveCount % 3) * 0.1F
            );
        }

        @Override
        public void onRecoveryTick(int tick) {
            transitionTo(Phase.IDLE);
        }
    }
}
