package net.dandare21.fracturedutils.npc.entity.ai;

import net.dandare21.fracturedutils.npc.entity.CustomNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class NpcFollowPlayerGoal extends Goal {
    private final CustomNpcEntity npc;
    private Player followPlayer;
    private int timeToRecalcPath = 0;

    public NpcFollowPlayerGoal(CustomNpcEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (npc.getCommandedFollowPlayer() == null) {
            return false;
        }
        Player p = npc.level().getPlayerByUUID(npc.getCommandedFollowPlayer());
        if (p == null || !p.isAlive()) {
            return false;
        }
        this.followPlayer = p;
        return npc.distanceToSqr(p) > 16.0D; // > 4 blocks
    }

    @Override
    public boolean canContinueToUse() {
        if (npc.getCommandedFollowPlayer() == null || followPlayer == null || !followPlayer.isAlive()) {
            return false;
        }
        return npc.distanceToSqr(followPlayer) > 9.0D; // Keep moving until within 3 blocks
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        this.followPlayer = null;
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (followPlayer == null) return;

        this.npc.getLookControl().setLookAt(followPlayer, 10.0F, (float) this.npc.getMaxHeadXRot());
        double distSq = this.npc.distanceToSqr(followPlayer);

        // Teleport if too far (> 24 blocks away)
        if (distSq > 576.0D) {
            teleportToPlayer();
            return;
        }

        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = 10;
            this.npc.getNavigation().moveTo(followPlayer, 1.25D);
        }
    }

    private void teleportToPlayer() {
        BlockPos playerPos = followPlayer.blockPosition();
        for (int i = 0; i < 10; i++) {
            int dx = npc.getRandom().nextInt(5) - 2;
            int dz = npc.getRandom().nextInt(5) - 2;
            BlockPos targetPos = playerPos.offset(dx, 0, dz);
            if (npc.level().getBlockState(targetPos).isAir() && npc.level().getBlockState(targetPos.above()).isAir()) {
                npc.moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, npc.getYRot(), npc.getXRot());
                npc.getNavigation().stop();
                return;
            }
        }
    }
}
