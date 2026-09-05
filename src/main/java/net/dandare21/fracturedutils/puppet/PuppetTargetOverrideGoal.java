package net.dandare21.fracturedutils.puppet;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Priority 0 goal in mob.targetSelector that suppresses target acquisition while puppeting or target suppression is active.
 */
public class PuppetTargetOverrideGoal extends Goal {
    private final Mob mob;
    private final PuppetController controller;

    public PuppetTargetOverrideGoal(Mob mob, PuppetController controller) {
        this.mob = mob;
        this.controller = controller;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return this.controller.isPuppetingActive() || this.controller.isTargetingSuppressed() || this.controller.isAiSuppressed();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        if (this.controller.isTargetingSuppressed()) {
            this.mob.setTarget(null);
        }
    }

    @Override
    public void tick() {
        if (this.controller.isTargetingSuppressed()) {
            this.mob.setTarget(null);
        }
    }
}
