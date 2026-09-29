package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.definition.ReactionDefinition;
import net.minecraft.world.phys.Vec3;

/**
 * Pure numbers behind the server target reaction, kept free of entity and registry access so
 * they can be unit tested.
 */
public final class CombatReactionMath {
    /** Horizontal speed kept per tick by an entity that starts the tick on ordinary ground. */
    static final double GROUND_RETAIN = 0.6 * 0.91;
    /** Horizontal speed kept per tick by an entity that starts the tick airborne. */
    static final double AIR_RETAIN = 0.91;
    static final double GRAVITY = 0.08;
    static final double VERTICAL_DRAG = 0.98;
    /** Extra stuns inside this window stack the anti-lock factor. */
    public static final int STUN_STACK_WINDOW_TICKS = 40;
    static final double STUN_STACK_FACTOR = 0.7;
    static final double HEAVY_TARGET_MAX_HEALTH = 100.0;
    static final double HEAVY_TARGET_STUN_FACTOR = 0.3;
    private static final int SIMULATION_LIMIT_TICKS = 200;

    private CombatReactionMath() {
    }

    /**
     * Initial horizontal speed that slides a grounded target {@code slideDistance} blocks when it
     * is also launched upward at {@code lift} blocks per tick. It mirrors
     * {@code LivingEntity.travel}: friction is picked from the ground state at the start of each
     * tick (0.546 grounded, 0.91 airborne) and vertical speed follows {@code (vy - 0.08) * 0.98}.
     * With no lift this is {@code slideDistance * 0.454}.
     */
    public static double slideSpeed(double slideDistance, double lift) {
        if (!(slideDistance > 0.0)) {
            return 0.0;
        }
        return slideDistance / travelPerUnitSpeed(lift);
    }

    /** Horizontal blocks travelled per unit of initial horizontal speed. */
    static double travelPerUnitSpeed(double lift) {
        double travelled = 0.0;
        double speed = 1.0;
        double height = 0.0;
        double vertical = Math.max(0.0, lift);
        boolean grounded = true;
        for (int tick = 0; tick < SIMULATION_LIMIT_TICKS && speed > 1.0E-4; tick++) {
            travelled += speed;
            speed *= grounded ? GROUND_RETAIN : AIR_RETAIN;
            if (vertical > 0.0 || height > 0.0) {
                height += vertical;
                vertical = (vertical - GRAVITY) * VERTICAL_DRAG;
                if (height <= 0.0) {
                    height = 0.0;
                    vertical = 0.0;
                }
            }
            grounded = height <= 0.0;
        }
        return travelled;
    }

    /**
     * World-space knockback impulse for a reaction: x/z are the horizontal velocity, y is the
     * lift (0 keeps the target's own vertical speed). The push follows the attacker's facing,
     * tilted toward the attacker's right by {@code lateralBias}, and is scaled by
     * {@code 1 - knockbackResistance}. {@code extraSpeed} carries vanilla knockback sources
     * (the ATTACK_KNOCKBACK attribute and Knockback enchantments) in vanilla units.
     */
    public static Vec3 impulse(
            ReactionDefinition reaction,
            float facingYawDegrees,
            double extraSpeed,
            double knockbackResistance) {
        double resistance = Math.max(0.0, Math.min(1.0, knockbackResistance));
        double scale = 1.0 - resistance;
        if (scale <= 0.0) {
            return Vec3.ZERO;
        }
        double yaw = Math.toRadians(facingYawDegrees);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        // The attacker's right: forward rotated 90 degrees clockwise seen from above.
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        double bias = reaction.lateralBias();
        double directionX = forwardX * (1.0 - Math.abs(bias)) + rightX * bias;
        double directionZ = forwardZ * (1.0 - Math.abs(bias)) + rightZ * bias;
        double length = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (length < 1.0E-6) {
            directionX = forwardX;
            directionZ = forwardZ;
            length = 1.0;
        }
        double speed = (slideSpeed(reaction.slideDistance(), reaction.lift()) + Math.max(0.0, extraSpeed)) * scale;
        return new Vec3(
                directionX / length * speed,
                reaction.lift() * scale,
                directionZ / length * speed);
    }

    /**
     * Hitstun after anti-lock scaling: each earlier stun inside the stack window multiplies by
     * 0.7, and targets with at least 100 max health take 30%.
     */
    public static int stunTicks(int baseTicks, int stackedStuns, double maximumHealth) {
        if (baseTicks <= 0) {
            return 0;
        }
        double ticks = baseTicks * Math.pow(STUN_STACK_FACTOR, Math.max(0, stackedStuns));
        if (maximumHealth >= HEAVY_TARGET_MAX_HEALTH) {
            ticks *= HEAVY_TARGET_STUN_FACTOR;
        }
        return (int) Math.round(ticks);
    }

    /** Server freeze length for a move's presentation hit-stop. */
    public static int freezeTicks(float hitStopTicks) {
        return Math.max(0, Math.round(hitStopTicks));
    }
}
