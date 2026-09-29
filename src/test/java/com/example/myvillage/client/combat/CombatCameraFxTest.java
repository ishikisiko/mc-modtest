package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CombatCameraFxTest {
    @Test
    void traumaDecaysAtOnePointSixPerSecond() {
        assertEquals(0.8F, CombatCameraFx.decayedTrauma(0.8F, 0.0), 1.0E-6F);
        assertEquals(0.0F, CombatCameraFx.decayedTrauma(0.8F, 10.0), 1.0E-6F);
        assertEquals(0.8F - 0.08F * 5.0F, CombatCameraFx.decayedTrauma(0.8F, 5.0), 1.0E-6F);
        // A light hit settles in 3-4 ticks, the finisher in about 10.
        assertEquals(0.0F, CombatCameraFx.decayedTrauma(0.25F, 4.0), 1.0E-6F);
        assertTrue(CombatCameraFx.decayedTrauma(0.8F, 9.0) > 0.0F);
    }

    @Test
    void shakeIsTraumaSquared() {
        assertEquals(0.0625F, CombatCameraFx.shake(0.25F), 1.0E-6F);
        assertEquals(1.0F, CombatCameraFx.shake(2.0F), 1.0E-6F);
        assertEquals(0.0F, CombatCameraFx.shake(-1.0F), 1.0E-6F);
    }

    @Test
    void noiseIsSmoothAndBounded() {
        float previous = CombatCameraFx.noise(0, 0.0F);
        for (float time = 0.0F; time < 50.0F; time += 0.01F) {
            float value = CombatCameraFx.noise(0, time);
            assertTrue(value >= -1.0F && value <= 1.0F);
            assertTrue(Math.abs(value - previous) < 0.05F);
            previous = value;
        }
        assertTrue(CombatCameraFx.noise(0, 3.5F) != CombatCameraFx.noise(1, 3.5F));
    }

    @Test
    void kickEnvelopeRisesThenSettles() {
        assertEquals(0.0F, CombatCameraFx.envelope(-0.5F, 2.0F, 5.0F), 1.0E-6F);
        assertEquals(1.0F, CombatCameraFx.envelope(2.0F, 2.0F, 5.0F), 1.0E-6F);
        assertTrue(CombatCameraFx.envelope(1.0F, 2.0F, 5.0F) > 0.5F);
        assertTrue(CombatCameraFx.envelope(4.0F, 2.0F, 5.0F) < 1.0F);
        assertEquals(0.0F, CombatCameraFx.envelope(7.0F, 2.0F, 5.0F), 1.0E-6F);
    }

    @Test
    void combatSlowDoesNotZoomTheFov() {
        // Walking speed 0.1 slowed to 25% by the swing commitment: vanilla term 0.625, restored to 1.0.
        assertEquals(1.6F, CombatCameraFx.fovCorrection(0.025, 0.25, 0.1F), 1.0E-5F);
        assertEquals(1.0F, CombatCameraFx.fovCorrection(0.1, 1.0, 0.1F), 1.0E-6F);
        assertEquals(1.0F, CombatCameraFx.fovCorrection(0.1, 0.0, 0.1F), 1.0E-6F);
    }
}
