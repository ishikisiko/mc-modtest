package com.example.myvillage.combat.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.ReactionDefinition;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CombatReactionMathTest {
    @Test
    void groundSlideSpeedCompensatesDrag() {
        assertEquals(0.30 * 0.454, CombatReactionMath.slideSpeed(0.30, 0.0), 1.0E-4);
        assertEquals(0.0, CombatReactionMath.slideSpeed(0.0, 0.25), 1.0E-9);
    }

    @Test
    void liftedSlideNeedsLessSpeedBecauseAirDragIsLower() {
        double grounded = CombatReactionMath.slideSpeed(2.0, 0.0);
        double lifted = CombatReactionMath.slideSpeed(2.0, 0.25);
        assertTrue(lifted < grounded);
        assertEquals(2.0, lifted * CombatReactionMath.travelPerUnitSpeed(0.25), 1.0E-9);
        assertTrue(lifted > 0.3 && lifted < 0.6, "lifted launch speed " + lifted);
    }

    @Test
    void impulseFollowsFacingAndResistance() {
        ReactionDefinition straight = new ReactionDefinition(9, 0.30, 0.0, 0.0);
        Vec3 south = CombatReactionMath.impulse(straight, 0.0F, 0.0, 0.0);
        assertEquals(0.0, south.x, 1.0E-9);
        assertTrue(south.z > 0.0);
        assertEquals(0.0, south.y, 1.0E-9);

        Vec3 halved = CombatReactionMath.impulse(straight, 0.0F, 0.0, 0.5);
        assertEquals(south.z * 0.5, halved.z, 1.0E-9);
        assertEquals(Vec3.ZERO, CombatReactionMath.impulse(straight, 0.0F, 0.0, 1.0));

        Vec3 knockbackEnchanted = CombatReactionMath.impulse(straight, 0.0F, 0.5, 0.0);
        assertEquals(south.z + 0.5, knockbackEnchanted.z, 1.0E-9);
    }

    @Test
    void lateralBiasPushesTowardTheAttackersRight() {
        // Facing south (+Z), the attacker's right is west (-X), the direction of the shipped
        // left-to-right horizontal cut.
        Vec3 impulse = CombatReactionMath.impulse(new ReactionDefinition(9, 0.35, 0.0, 0.3), 0.0F, 0.0, 0.0);
        assertTrue(impulse.x < 0.0);
        assertTrue(impulse.z > 0.0);
        assertEquals(0.3 / 0.7, -impulse.x / impulse.z, 1.0E-9);
    }

    @Test
    void liftIsCarriedAsVerticalImpulse() {
        Vec3 impulse = CombatReactionMath.impulse(new ReactionDefinition(16, 2.0, 0.25, 0.0), 90.0F, 0.0, 0.0);
        assertEquals(0.25, impulse.y, 1.0E-9);
        assertTrue(impulse.x < 0.0, "yaw 90 faces west");
    }

    @Test
    void stunAntiLockScalesRepeatedStunsAndHeavyTargets() {
        assertEquals(9, CombatReactionMath.stunTicks(9, 0, 20.0));
        assertEquals(6, CombatReactionMath.stunTicks(9, 1, 20.0));
        assertEquals(4, CombatReactionMath.stunTicks(9, 2, 20.0));
        assertEquals(5, CombatReactionMath.stunTicks(16, 0, 100.0));
        assertEquals(0, CombatReactionMath.stunTicks(0, 0, 20.0));
    }

    @Test
    void freezeRoundsTheHitStop() {
        assertEquals(2, CombatReactionMath.freezeTicks(1.5F));
        assertEquals(2, CombatReactionMath.freezeTicks(2.0F));
        assertEquals(3, CombatReactionMath.freezeTicks(3.0F));
        assertEquals(4, CombatReactionMath.freezeTicks(4.0F));
        assertEquals(0, CombatReactionMath.freezeTicks(0.0F));
    }
}
