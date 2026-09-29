package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SwordTrailShapeTest {
    @Test
    void trailTapersTowardTheTipWithAge() {
        assertEquals(0.55F, SwordTrailShape.innerFraction(0.0F), 1.0E-6F);
        assertEquals(0.97F, SwordTrailShape.innerFraction(1.0F), 1.0E-6F);
        float previous = 0.0F;
        for (float age = 0.0F; age <= 1.0F; age += 0.05F) {
            float inner = SwordTrailShape.innerFraction(age);
            assertTrue(inner >= previous);
            assertTrue(SwordTrailShape.edgeFraction(age) >= inner);
            assertTrue(SwordTrailShape.edgeFraction(age) >= SwordTrailShape.EDGE_FRACTION);
            previous = inner;
        }
    }

    @Test
    void alphaFallsQuadraticallyFromPeak() {
        assertEquals(0.7F, SwordTrailShape.alpha(0.0F, 1.0F), 1.0E-6F);
        assertEquals(0.7F * 0.25F, SwordTrailShape.alpha(0.5F, 1.0F), 1.0E-6F);
        assertEquals(0.0F, SwordTrailShape.alpha(1.0F, 1.0F), 1.0E-6F);
        assertEquals(0.35F, SwordTrailShape.alpha(0.0F, 0.5F), 1.0E-6F);
    }

    @Test
    void fadeHoldsThroughTheWindowThenRunsOut() {
        assertEquals(1.0F, SwordTrailShape.fade(5.0F, 6.0F, 2.4F), 1.0E-6F);
        assertEquals(0.5F, SwordTrailShape.fade(7.2F, 6.0F, 2.4F), 1.0E-5F);
        assertEquals(0.0F, SwordTrailShape.fade(9.0F, 6.0F, 2.4F), 1.0E-6F);
    }

    @Test
    void thrustStreakFadesOverThreeTicks() {
        assertEquals(0.0F, SwordTrailShape.streakAlpha(2.9F, 3.0F, 3.0F), 1.0E-6F);
        assertEquals(0.8F, SwordTrailShape.streakAlpha(3.0F, 3.0F, 3.0F), 1.0E-6F);
        assertEquals(0.0F, SwordTrailShape.streakAlpha(6.0F, 3.0F, 3.0F), 1.0E-6F);
    }
}
