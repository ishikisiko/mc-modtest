package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.CombatTestData;
import com.example.myvillage.combat.definition.MoveKind;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WeaponTrailShapeTest {
    @Test
    void trailTapersTowardTheTipWithAge() {
        assertEquals(0.55F, WeaponTrailShape.innerFraction(0.0F), 1.0E-6F);
        assertEquals(0.97F, WeaponTrailShape.innerFraction(1.0F), 1.0E-6F);
        float previous = 0.0F;
        for (float age = 0.0F; age <= 1.0F; age += 0.05F) {
            float inner = WeaponTrailShape.innerFraction(age);
            assertTrue(inner >= previous);
            assertTrue(WeaponTrailShape.edgeFraction(age) >= inner);
            assertTrue(WeaponTrailShape.edgeFraction(age) >= WeaponTrailShape.EDGE_FRACTION);
            previous = inner;
        }
    }

    @Test
    void alphaFallsQuadraticallyFromPeak() {
        assertEquals(0.7F, WeaponTrailShape.alpha(0.0F, 1.0F), 1.0E-6F);
        assertEquals(0.7F * 0.25F, WeaponTrailShape.alpha(0.5F, 1.0F), 1.0E-6F);
        assertEquals(0.0F, WeaponTrailShape.alpha(1.0F, 1.0F), 1.0E-6F);
        assertEquals(0.35F, WeaponTrailShape.alpha(0.0F, 0.5F), 1.0E-6F);
    }

    @Test
    void fadeHoldsThroughTheWindowThenRunsOut() {
        assertEquals(1.0F, WeaponTrailShape.fade(5.0F, 6.0F, 2.4F), 1.0E-6F);
        assertEquals(0.5F, WeaponTrailShape.fade(7.2F, 6.0F, 2.4F), 1.0E-5F);
        assertEquals(0.0F, WeaponTrailShape.fade(9.0F, 6.0F, 2.4F), 1.0E-6F);
    }

    @Test
    void moveKindSelectsStreakOrBand() {
        assertTrue(WeaponTrailShape.streak(MoveKind.THRUST));
        assertTrue(!WeaponTrailShape.streak(MoveKind.CUT));
        // Qingfeng: the two thrusts draw a streak, the three cuts a band.
        assertEquals(
                List.of(true, false, false, false, true),
                CombatTestData.basicSword().moves().stream()
                        .map(AttackMoveDefinition::kind)
                        .map(WeaponTrailShape::streak)
                        .toList());
    }

    @Test
    void thrustStreakFadesOverThreeTicks() {
        assertEquals(0.0F, WeaponTrailShape.streakAlpha(2.9F, 3.0F, 3.0F), 1.0E-6F);
        assertEquals(0.8F, WeaponTrailShape.streakAlpha(3.0F, 3.0F, 3.0F), 1.0E-6F);
        assertEquals(0.0F, WeaponTrailShape.streakAlpha(6.0F, 3.0F, 3.0F), 1.0E-6F);
    }
}
