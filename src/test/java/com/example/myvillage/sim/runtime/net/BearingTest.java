package com.example.myvillage.sim.runtime.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link WorldSimSnapshots#bearing}: eight 45-degree sectors clockwise from north, +x east, +z south. */
class BearingTest {
    @Test
    void bearingIsEightWayClockwiseFromNorthWithPlusZSouth() {
        assertEquals("n", WorldSimSnapshots.bearing(0, 0), "no offset reads north");
        assertEquals("n", WorldSimSnapshots.bearing(0, -10));
        assertEquals("ne", WorldSimSnapshots.bearing(10, -10));
        assertEquals("e", WorldSimSnapshots.bearing(10, 0));
        assertEquals("se", WorldSimSnapshots.bearing(10, 10));
        assertEquals("s", WorldSimSnapshots.bearing(0, 10));
        assertEquals("sw", WorldSimSnapshots.bearing(-10, 10));
        assertEquals("w", WorldSimSnapshots.bearing(-10, 0));
        assertEquals("nw", WorldSimSnapshots.bearing(-10, -10));
        assertEquals("s", WorldSimSnapshots.bearing(-0.0, 5));
        assertEquals("e", WorldSimSnapshots.bearing(5, -0.0));
        // each bearing covers 22.5 degrees either side of its direction
        double edge = Math.tan(Math.toRadians(22.4));
        assertEquals("n", WorldSimSnapshots.bearing(edge, -1));
        assertEquals("n", WorldSimSnapshots.bearing(-edge, -1));
        double past = Math.tan(Math.toRadians(22.6));
        assertEquals("ne", WorldSimSnapshots.bearing(past, -1));
        assertEquals("nw", WorldSimSnapshots.bearing(-past, -1));
        assertEquals("s", WorldSimSnapshots.bearing(edge, 1));
        assertEquals("se", WorldSimSnapshots.bearing(past, 1));
        // the full circle, sampled every degree, visits each bearing over 45 consecutive degrees
        Map<String, Integer> span = new HashMap<>();
        for (int degree = 0; degree < 360; degree++) {
            double r = Math.toRadians(degree + 0.25);
            String b = WorldSimSnapshots.bearing(Math.sin(r) * 100, -Math.cos(r) * 100);
            assertTrue(WorldSimSnapshots.BEARINGS.contains(b));
            span.merge(b, 1, Integer::sum);
        }
        for (String b : WorldSimSnapshots.BEARINGS) {
            assertEquals(45, span.get(b), b);
        }
    }
}
