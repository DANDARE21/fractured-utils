package net.dandare21.fracturedutils.npc.entity.ai;

import net.dandare21.fracturedutils.npc.NpcDefinition;
import net.dandare21.fracturedutils.npc.NpcMovementMode;
import net.dandare21.fracturedutils.npc.entity.CustomNpcEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class NpcPatrolGoal extends Goal {
    private final CustomNpcEntity npc;
    private int currentWaypointIndex = 0;
    private int pauseTicks = 0;

    public NpcPatrolGoal(CustomNpcEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (npc.getMovementMode() != NpcMovementMode.PATROL) {
            return false;
        }
        NpcDefinition def = npc.getDefinition();
        return def != null && def.patrolWaypoints() != null && !def.patrolWaypoints().isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.pauseTicks = 0;
        navigateToCurrentWaypoint();
    }

    @Override
    public void tick() {
        NpcDefinition def = npc.getDefinition();
        if (def == null || def.patrolWaypoints().isEmpty()) return;

        List<Vec3> waypoints = def.patrolWaypoints();
        if (currentWaypointIndex >= waypoints.size()) {
            currentWaypointIndex = 0;
        }

        Vec3 targetWp = waypoints.get(currentWaypointIndex);
        double distSq = npc.position().distanceToSqr(targetWp);

        if (distSq <= 2.25) { // within 1.5 blocks
            if (pauseTicks < 40) { // pause for 2 seconds at each waypoint
                pauseTicks++;
                npc.getNavigation().stop();
                return;
            }
            pauseTicks = 0;
            currentWaypointIndex = (currentWaypointIndex + 1) % waypoints.size();
            navigateToCurrentWaypoint();
        } else {
            if (npc.getNavigation().isDone()) {
                navigateToCurrentWaypoint();
            }
        }
    }

    private void navigateToCurrentWaypoint() {
        NpcDefinition def = npc.getDefinition();
        if (def == null || def.patrolWaypoints().isEmpty()) return;
        Vec3 wp = def.patrolWaypoints().get(currentWaypointIndex % def.patrolWaypoints().size());
        double speed = Math.max(0.5, def.moveSpeed() * 4.0);
        npc.getNavigation().moveTo(wp.x, wp.y, wp.z, speed);
    }
}
