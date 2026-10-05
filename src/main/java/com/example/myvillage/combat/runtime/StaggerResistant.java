package com.example.myvillage.combat.runtime;

/**
 * A hit target that can resist stagger for a while (poise, 韧性). While {@link #resistsStagger()}
 * is true a combat hit still deals its damage and still freezes the target for the hit-stop, but
 * applies no hitstun and drops its knockback impulse. Outside that window the reaction is the
 * normal one and the target can see it through {@link CombatReactionService#isStaggered}.
 */
public interface StaggerResistant {
    /** Asked on the server at the moment a combat hit lands. */
    boolean resistsStagger();

    /** True when {@code target} implements this interface and resists right now. */
    static boolean resists(Object target) {
        return target instanceof StaggerResistant resistant && resistant.resistsStagger();
    }
}
