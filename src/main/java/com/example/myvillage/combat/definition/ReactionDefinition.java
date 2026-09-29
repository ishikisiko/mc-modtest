package com.example.myvillage.combat.definition;

/**
 * Server-authoritative reaction a successful hit of one move imposes on its target.
 *
 * <p>{@code slideDistance} is the intended ground slide in blocks (the server converts it to an
 * initial speed that accounts for ground drag and airtime), {@code lift} is an upward velocity in
 * blocks per tick added on top, and {@code lateralBias} tilts the push away from the attacker's
 * facing toward the attacker's right (positive) or left (negative), which is the swing direction of
 * the shipped left-to-right horizontal cut.
 */
public record ReactionDefinition(int hitstunTicks, double slideDistance, double lift, double lateralBias) {
    public static final ReactionDefinition NONE = new ReactionDefinition(0, 0.0, 0.0, 0.0);

    public ReactionDefinition {
        if (hitstunTicks < 0 || hitstunTicks > 60) {
            throw new IllegalArgumentException("Hitstun must be in 0..60 ticks");
        }
        if (!Double.isFinite(slideDistance) || slideDistance < 0.0 || slideDistance > 4.0) {
            throw new IllegalArgumentException("Slide distance must be in 0..4 blocks");
        }
        if (!Double.isFinite(lift) || lift < 0.0 || lift > 0.6) {
            throw new IllegalArgumentException("Lift must be in 0..0.6 blocks per tick");
        }
        if (!Double.isFinite(lateralBias) || lateralBias < -1.0 || lateralBias > 1.0) {
            throw new IllegalArgumentException("Lateral bias must be in -1..1");
        }
    }
}
