package com.example.myvillage.entity.beast;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Chases the target and starts the beast's moves. The move itself runs in
 * {@link BeastEntity}'s own AI step; this goal only keeps other MOVE/LOOK goals out while it does.
 */
final class BeastAttackGoal extends Goal {
    private final BeastEntity beast;
    private int ticksUntilRepath;

    BeastAttackGoal(BeastEntity beast) {
        this.beast = beast;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = beast.getTarget();
        return target != null && target.isAlive() && beast.canAttack(target);
    }

    @Override
    public boolean canContinueToUse() {
        return beast.isMoveRunning() || canUse();
    }

    @Override
    public void start() {
        ticksUntilRepath = 0;
        beast.setAggressive(true);
    }

    @Override
    public void stop() {
        if (!beast.isMoveRunning()) {
            beast.getNavigation().stop();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (beast.isMoveRunning()) {
            // Re-path as soon as the move ends.
            ticksUntilRepath = 0;
            return;
        }
        LivingEntity target = beast.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        beast.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (beast.tryStartMove(target)) {
            return;
        }
        if (--ticksUntilRepath <= 0) {
            ticksUntilRepath = adjustedTickDelay(4 + beast.getRandom().nextInt(7));
            if (!beast.getNavigation().moveTo(target, beast.definition().chase().speedModifier())) {
                // A path cannot start in mid-air (knocked back, still landing); retry as soon as it lands.
                ticksUntilRepath = beast.onGround() ? ticksUntilRepath + 15 : 1;
            }
        }
    }
}
