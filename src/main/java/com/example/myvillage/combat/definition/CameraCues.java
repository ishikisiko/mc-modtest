package com.example.myvillage.combat.definition;

/**
 * Attacker-local camera cues for one move, in degrees. Presentation only.
 *
 * <ul>
 *     <li>{@code hitPitchKick}: pitch kick on the first confirmed hit of an action (up positive).</li>
 *     <li>{@code hitRollKick}: roll kick on the first confirmed hit.</li>
 *     <li>{@code hitFovPunch}: FOV punch on the first confirmed hit (negative narrows).</li>
 *     <li>{@code swingLeanDegrees}: signed roll lean as the blade starts moving (0 = none).</li>
 *     <li>{@code stepFovSurge}: FOV widening when the move's step launches (0 = none).</li>
 * </ul>
 */
public record CameraCues(
        float hitPitchKick,
        float hitRollKick,
        float hitFovPunch,
        float swingLeanDegrees,
        float stepFovSurge) {
    public static final CameraCues NONE = new CameraCues(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

    public CameraCues {
        if (!within(hitPitchKick, 10.0F) || !within(hitRollKick, 10.0F)) {
            throw new IllegalArgumentException("Hit kicks must be in -10..10 degrees");
        }
        if (!within(hitFovPunch, 15.0F)) {
            throw new IllegalArgumentException("Hit FOV punch must be in -15..15 degrees");
        }
        if (!within(swingLeanDegrees, 5.0F)) {
            throw new IllegalArgumentException("Swing lean must be in -5..5 degrees");
        }
        if (!(stepFovSurge >= 0.0F && stepFovSurge <= 15.0F)) {
            throw new IllegalArgumentException("Step FOV surge must be in 0..15 degrees");
        }
    }

    private static boolean within(float value, float bound) {
        return value >= -bound && value <= bound;
    }
}
