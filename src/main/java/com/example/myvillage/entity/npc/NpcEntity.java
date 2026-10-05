package com.example.myvillage.entity.npc;

import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A humanoid NPC drawn from its own model, clip and texture files (see {@code NpcRenderer}).
 *
 * <p>This base only gives the figure a body in the world: it floats, strolls, and looks at nearby
 * players. It has no target, attack, trade or dialogue; whether an NPC is a friend or a foe is
 * decided by what a subclass adds later. It never despawns by distance.
 *
 * <p>Client: {@link #idleAnimationState()} runs from the first client tick; the walk clip is driven
 * by vanilla limb swing.
 */
public abstract class NpcEntity extends PathfinderMob {
    public static final String IDLE_CLIP = "idle";
    public static final String WALK_CLIP = "walk";
    static final float LOOK_DISTANCE = 8.0F;

    private final AnimationState idleAnimationState = new AnimationState();

    protected NpcEntity(EntityType<? extends NpcEntity> entityType, Level level) {
        super(entityType, level);
    }

    /** Speed modifier of the stroll goal; the walk clip's rate does not depend on it. */
    protected abstract double strollSpeedModifier();

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, strollSpeedModifier()));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, LOOK_DISTANCE));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            idleAnimationState.startIfStopped(tickCount);
        }
    }

    public AnimationState idleAnimationState() {
        return idleAnimationState;
    }
}
