package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.meditation.MeditationState;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeridianLookTest {
    @Test
    void closedChannelsAreDormantAndAMissingStatusLooksLikeRest() {
        MeridianLook dormant = MeridianLook.of(MeditationState.IDLE, false);
        assertEquals(MeridianLook.Phase.DORMANT, dormant.phase());
        assertFalse(dormant.flowing());
        assertEquals(MeridianLook.Phase.REST, MeridianLook.of(null, true).phase());
        assertEquals(MeridianLook.of(MeditationState.IDLE, true), MeridianLook.of(null, true));
    }

    @Test
    void restIsDimAndStill() {
        MeridianLook rest = MeridianLook.of(MeditationState.IDLE, true);
        MeridianLook meditating = MeridianLook.of(MeditationState.MEDITATING_NORMAL, true);
        assertFalse(rest.flowing());
        assertFalse(rest.gathering());
        assertTrue(rest.figureLight() < meditating.figureLight());
        assertTrue(rest.circuitLevel() < meditating.circuitLevel());
    }

    @Test
    void preparationGathersWithoutCirculatingOrCountingDown() {
        for (MeditationState state : new MeditationState[] {MeditationState.PREPARING_NORMAL, MeditationState.PREPARING_SPIRIT}) {
            MeridianLook look = MeridianLook.of(state, true);
            assertEquals(MeridianLook.Phase.PREPARING, look.phase());
            assertTrue(look.gathering());
            assertFalse(look.flowing());
            assertFalse(look.halo());
        }
    }

    @Test
    void everyActiveStateLooksDifferentAndModesKeepTheirColour() {
        Set<MeridianLook> looks = new HashSet<>();
        for (MeditationState state : MeditationState.values()) {
            assertTrue(looks.add(MeridianLook.of(state, true)), state.name());
        }
        MeridianLook normal = MeridianLook.of(MeditationState.MEDITATING_NORMAL, true);
        MeridianLook spirit = MeridianLook.of(MeditationState.MEDITATING_SPIRIT, true);
        MeridianLook ordinary = MeridianLook.of(MeditationState.ADVANCING_ORDINARY, true);
        MeridianLook bottleneck = MeridianLook.of(MeditationState.ADVANCING_BOTTLENECK, true);
        assertEquals(normal.color(), MeridianLook.of(MeditationState.PREPARING_NORMAL, true).color());
        assertEquals(spirit.color(), MeridianLook.of(MeditationState.PREPARING_SPIRIT, true).color());
        assertNotEquals(normal.color(), spirit.color());
        assertNotEquals(normal.color(), ordinary.color());
        assertNotEquals(spirit.color(), ordinary.color());
        assertNotEquals(ordinary.color(), bottleneck.color());
        assertTrue(normal.flowing() && spirit.flowing() && ordinary.flowing() && bottleneck.flowing());
        assertEquals(0, normal.limbMotes());
        assertTrue(spirit.limbMotes() > 0);
        assertTrue(spirit.level(MeridianChart.Vessel.HAND) > normal.level(MeridianChart.Vessel.HAND));
        assertTrue(ordinary.halo() && bottleneck.halo());
        assertFalse(normal.halo() || spirit.halo());
        assertTrue(bottleneck.barrier());
        assertFalse(ordinary.barrier());
    }

    @Test
    void circuitLevelsApplyToBothCircuitVesselsAndLimbLevelsToLimbs() {
        MeridianLook spirit = MeridianLook.of(MeditationState.MEDITATING_SPIRIT, true);
        assertEquals(spirit.circuitLevel(), spirit.level(MeridianChart.Vessel.GOVERNING));
        assertEquals(spirit.circuitLevel(), spirit.level(MeridianChart.Vessel.CONCEPTION));
        assertEquals(spirit.limbLevel(), spirit.level(MeridianChart.Vessel.FOOT));
    }

    @Test
    void motesAreEvenlySpacedAndLapOncePerPeriod() {
        MeridianLook normal = MeridianLook.of(MeditationState.MEDITATING_NORMAL, true);
        double period = normal.circuitPeriodSeconds();
        int motes = normal.circuitMotes();
        assertEquals(0.0D, normal.circuitPosition(0.0D, 0, 0.5D), 1.0E-9D);
        assertEquals(1.0D / motes, normal.circuitPosition(0.0D, 1, 0.5D), 1.0E-9D);
        assertEquals(0.25D, normal.circuitPosition(period / 4.0D, 0, 0.5D), 1.0E-9D);
        assertEquals(normal.circuitPosition(1.3D, 2, 0.5D), normal.circuitPosition(1.3D + period, 2, 0.5D), 1.0E-9D);
        assertEquals(0.0D, MeridianLook.of(MeditationState.IDLE, true).circuitPosition(3.0D, 0, 0.5D));
    }

    @Test
    void aBottleneckSlowsTheQiAtItsPassWithoutLosingALap() {
        double at = 0.3D;
        double strength = MeridianLook.BARRIER_SQUEEZE;
        // the pass and the point half a lap from it stay where they are
        assertEquals(at, MeridianLook.squeeze(at, at, strength), 1.0E-9D);
        assertEquals(at + 0.5D, MeridianLook.squeeze(at + 0.5D, at, strength), 1.0E-9D);
        // one lap in is one lap out, always moving forward
        double travelled = 0.0D;
        double previous = MeridianLook.squeeze(0.0D, at, strength);
        for (int step = 1; step <= 1000; step++) {
            double warped = MeridianLook.squeeze(step / 1000.0D, at, strength);
            assertTrue(warped >= 0.0D && warped < 1.0D);
            double advance = warped - previous;
            advance -= Math.floor(advance);
            assertTrue(advance > 0.0D && advance < 0.01D, "forward at " + step);
            travelled += advance;
            previous = warped;
        }
        assertEquals(1.0D, travelled, 1.0E-9D);
        double step = 1.0E-4D;
        double slopeAtPass = (MeridianLook.squeeze(at + step, at, strength) - MeridianLook.squeeze(at - step, at, strength)) / (2 * step);
        double slopeOpposite = (MeridianLook.squeeze(at + 0.5D + step, at, strength)
                - MeridianLook.squeeze(at + 0.5D - step, at, strength)) / (2 * step);
        assertEquals(1.0D - strength, slopeAtPass, 1.0E-4D);
        assertEquals(1.0D + strength, slopeOpposite, 1.0E-4D);
    }
}
