package net.dandare21.fracturedutils.npc.entity.ai;

import net.dandare21.fracturedutils.npc.entity.CustomNpcEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class NpcDefendPlayerGoal extends TargetGoal {
    private final CustomNpcEntity npc;
    private LivingEntity targetAttacker;
    private int timestamp;

    public NpcDefendPlayerGoal(CustomNpcEntity npc) {
        super(npc, false);
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (npc.getCommandedDefendPlayer() == null) {
            return false;
        }
        Player player = npc.level().getPlayerByUUID(npc.getCommandedDefendPlayer());
        if (player == null || !player.isAlive()) {
            return false;
        }

        // 1. Defend against what hurt the player
        LivingEntity hurtBy = player.getLastHurtByMob();
        int hurtTime = player.getLastHurtByMobTimestamp();
        if (hurtBy != null && hurtBy != npc && hurtTime != this.timestamp) {
            if (this.canAttack(hurtBy, TargetingConditions.DEFAULT)) {
                this.targetAttacker = hurtBy;
                this.timestamp = hurtTime;
                return true;
            }
        }

        // 2. Attack what the player attacks
        LivingEntity attackTarget = player.getLastHurtMob();
        int attackTime = player.getLastHurtMobTimestamp();
        if (attackTarget != null && attackTarget != npc && attackTime != this.timestamp) {
            if (this.canAttack(attackTarget, TargetingConditions.DEFAULT)) {
                this.targetAttacker = attackTarget;
                this.timestamp = attackTime;
                return true;
            }
        }

        return false;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.targetAttacker);
        super.start();
    }
}
