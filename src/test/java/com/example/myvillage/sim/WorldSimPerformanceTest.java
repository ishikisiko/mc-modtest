package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Performance (design §7): a day at about 300 people costs on the order of a millisecond and 1000
 * people leaves headroom. Measured after a warm-up; the limits are several times the observed cost
 * so a slow CI host does not flake.
 */
class WorldSimPerformanceTest {
    private static final int DPY = 6;

    private static double msPerDay(String tier, long seed, int days) {
        WorldSim sim = SimFixtures.genesis(seed, tier, DPY);
        SimFixtures.run(sim, 60L * DPY, DPY);
        long t0 = System.nanoTime();
        SimFixtures.run(sim, days, DPY);
        return (System.nanoTime() - t0) / 1e6 / days;
    }

    @Test
    void dailySettlementIsCheap() {
        double medium = msPerDay("medium", 4, 600);
        double large = msPerDay("large", 4, 300);
        System.out.printf("performance: medium (300) %.3f ms/day, large (1000) %.3f ms/day%n", medium, large);
        assertTrue(medium < 5.0, "a day at 300 people took " + medium + " ms");
        assertTrue(large < 20.0, "a day at 1000 people took " + large + " ms");
    }
}
