package com.example.myvillage.entity.beast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Lunge travel under vanilla drag and gravity (see {@link BeastMotion}). */
final class BeastMotionTest {
    @Test
    void groundLungeIsTheGroundFrictionSeries() {
        // Every tick on the ground keeps 0.546 of the speed: v / (1 - 0.546).
        assertEquals(1.0 / (1.0 - 0.546), BeastMotion.horizontalTravelPerUnitSpeed(0.0), 1.0E-3);
        BeastMotion.Flight bite = BeastMotion.simulate(0.55, 0.0);
        assertEquals(1.2113, bite.restDistance(), 1.0E-3);
        assertEquals(0, bite.airborneTicks());
        assertEquals(0.0, bite.peakHeight());
    }

    @Test
    void liftedLungeFliesFartherPerUnitSpeed() {
        BeastMotion.Flight flight = BeastMotion.simulate(1.0, 0.38);
        // First tick on the ground (0.546), then ten airborne ticks at 0.91 before landing.
        assertEquals(10, flight.airborneTicks());
        assertEquals(1.057, flight.peakHeight(), 1.0E-3);
        assertEquals(4.7042, flight.landingDistance(), 1.0E-3);
        assertEquals(5.1724, flight.restDistance(), 1.0E-3);
        assertTrue(flight.restDistance() > BeastMotion.horizontalTravelPerUnitSpeed(0.0) * 2.0);
    }

    @Test
    void pounceSpeedLandsOnTheAimMinusStandoffWithinItsClamp() {
        BeastMoveDefinition pounce = BeastTestData.pounce();
        double standoff = pounce.hit().standoff();
        assertEquals(0.85, standoff, 1.0E-9);
        for (double distance = 5.0; distance <= 7.5; distance += 0.5) {
            double speed = pounce.lunge().forwardSpeed(distance - standoff);
            double rest = BeastMotion.simulate(speed, pounce.lunge().up()).restDistance();
            assertEquals(distance - standoff, rest, 1.0E-6, "aim " + distance);
        }
        // Near: clamped to forward_min, so the wolf overshoots its standoff a little.
        assertEquals(0.6, pounce.lunge().forwardSpeed(4.0 - standoff - 0.5), 1.0E-9);
        // Far: clamped to forward_max; at the far end of use_range the target is still inside the
        // box's reach (1.9 ahead plus half a player) once the wolf comes to rest.
        double maximum = pounce.lunge().forwardSpeed(pounce.useRange().maximum() - standoff);
        assertEquals(1.3, maximum, 1.0E-9);
        double rest = BeastMotion.simulate(maximum, pounce.lunge().up()).restDistance();
        assertTrue(pounce.useRange().maximum() - rest <= pounce.hit().far() + 0.3,
                "pounce at max range falls short: rest " + rest);
    }

    @Test
    void biteSpeedIsFixed() {
        BeastMoveDefinition bite = BeastTestData.bite();
        assertEquals(0.55, bite.lunge().forwardSpeed(0.0), 1.0E-9);
        assertEquals(0.55, bite.lunge().forwardSpeed(10.0), 1.0E-9);
    }
}
