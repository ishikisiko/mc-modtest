package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WorldSimDeterminismTest {
    private static final int DPY = 6;

    private static SimFixtures.Collector runCollect(long seed, String tier, int dpy, int years, byte[][] bytesOut) {
        SimFixtures.Collector c = new SimFixtures.Collector();
        WorldSim sim = WorldSim.genesis(seed, SimFixtures.graph(seed), SimFixtures.data(), tier, dpy, c);
        SimFixtures.run(sim, (long) years * dpy, dpy);
        bytesOut[0] = sim.toBytes();
        return c;
    }

    @Test
    void sameInputsGiveIdenticalBytesAndEvents() {
        byte[][] a = new byte[1][];
        byte[][] b = new byte[1][];
        SimFixtures.Collector ea = runCollect(7, "small", DPY, 60, a);
        SimFixtures.Collector eb = runCollect(7, "small", DPY, 60, b);
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

    @Test
    void genesisRunsThePrehistory() {
        WorldSim sim = SimFixtures.genesis(3, "small", DPY);
        long prehistory = (long) SimFixtures.data().rules().time().prehistoryYears() * DPY;
        assertEquals(prehistory, sim.day());
        assertEquals(prehistory, sim.prehistoryDays());
        SimDate date = sim.date(DPY);
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
}
