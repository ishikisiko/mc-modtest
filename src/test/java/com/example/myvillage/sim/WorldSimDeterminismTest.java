package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorldSimDeterminismTest {
    private static final int DPY = WorldSimHealthTest.DPY;

    private static SimFixtures.Collector runCollect(long seed, String tier, int dpy, int years, byte[][] bytesOut) {
        SimFixtures.Collector c = new SimFixtures.Collector();
        WorldSim sim = WorldSim.genesis(seed, SimFixtures.graph(seed), SimFixtures.data(), tier, dpy, c);
        SimFixtures.run(sim, (long) years * dpy, dpy);
        bytesOut[0] = sim.toBytes();
        return c;
    }

    @ParameterizedTest(name = "{0} days per year")
    @ValueSource(ints = {24, 6})
    void sameInputsGiveIdenticalBytesAndEvents(int dpy) {
        byte[][] a = new byte[1][];
        byte[][] b = new byte[1][];
        SimFixtures.Collector ea = runCollect(7, "small", dpy, 60, a);
        SimFixtures.Collector eb = runCollect(7, "small", dpy, 60, b);
        assertArrayEquals(a[0], b[0]);
        assertEquals(ea.events, eb.events);
        assertTrue(ea.events.size() > 100, "a living world emits events, got " + ea.events.size());
    }

    @Test
    void differentSeedDiffers() {
        byte[][] a = new byte[1][];
        byte[][] b = new byte[1][];
        runCollect(7, "small", DPY, 10, a);
        runCollect(8, "small", DPY, 10, b);
        assertFalse(Arrays.equals(a[0], b[0]));
    }

    @ParameterizedTest(name = "{0} days per year")
    @ValueSource(ints = {24, 6})
    void genesisRunsThePrehistory(int dpy) {
        WorldSim sim = SimFixtures.genesis(3, "small", dpy);
        long prehistory = (long) SimFixtures.data().rules().time().prehistoryYears() * dpy;
        assertEquals(prehistory, sim.day());
        assertEquals(prehistory, sim.prehistoryDays());
        SimDate date = sim.date(dpy);
        assertFalse(date.beforeEra());
        assertEquals(1, date.year());
        assertEquals(0, date.dayOfYear());
    }

    @Test
    void otherDaysPerYearAlsoRuns() {
        byte[][] a = new byte[1][];
        byte[][] b = new byte[1][];
        SimFixtures.Collector ea = runCollect(11, "small", 12, 20, a);
        SimFixtures.Collector eb = runCollect(11, "small", 12, 20, b);
        assertArrayEquals(a[0], b[0]);
        assertEquals(ea.events, eb.events);
    }

    @Test
    void eraDates() {
        assertEquals(new SimDate(0, true, 100, 0), SimDate.of(0, 600, 6));
        assertEquals(new SimDate(599, true, 1, 5), SimDate.of(599, 600, 6));
        assertEquals(new SimDate(594, true, 1, 0), SimDate.of(594, 600, 6));
        assertEquals(new SimDate(593, true, 2, 5), SimDate.of(593, 600, 6));
        assertEquals(new SimDate(600, false, 1, 0), SimDate.of(600, 600, 6));
        assertEquals(new SimDate(605, false, 1, 5), SimDate.of(605, 600, 6));
        assertEquals(new SimDate(606, false, 2, 0), SimDate.of(606, 600, 6));
    }

    /** 24 days per year, the default: 100 prehistory years are 2400 days. */
    @Test
    void eraDatesAtTwentyFourDaysPerYear() {
        long prehistory = (long) SimFixtures.data().rules().time().prehistoryYears() * 24;
        assertEquals(2400, prehistory);
        assertEquals(new SimDate(0, true, 100, 0), SimDate.of(0, 2400, 24));
        assertEquals(new SimDate(23, true, 100, 23), SimDate.of(23, 2400, 24));
        assertEquals(new SimDate(24, true, 99, 0), SimDate.of(24, 2400, 24));
        assertEquals(new SimDate(2399, true, 1, 23), SimDate.of(2399, 2400, 24));
        assertEquals(new SimDate(2376, true, 1, 0), SimDate.of(2376, 2400, 24));
        assertEquals(new SimDate(2375, true, 2, 23), SimDate.of(2375, 2400, 24));
        assertEquals(new SimDate(2400, false, 1, 0), SimDate.of(2400, 2400, 24));
        assertEquals(new SimDate(2423, false, 1, 23), SimDate.of(2423, 2400, 24));
        assertEquals(new SimDate(2424, false, 2, 0), SimDate.of(2424, 2400, 24));
        assertEquals(-100, SimDate.of(0, 2400, 24).signedYear());
        assertEquals(300, SimDate.of(2400 + 299 * 24 + 23, 2400, 24).year());
    }
}
