package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checkpoint-1 liveness: a small world stays populated for 400 years (100 prehistory + 300), keeps
 * at least one sect, changes masters and someone reaches golden core. The realm pyramid (averaged
 * over the run) and the pinned bands live in {@link WorldSimHealthTest}.
 */
class WorldSimLivenessTest {
    private static final int DPY = 6;

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3})
    void smallWorldStaysAliveFor400Years(long seed) {
        SimFixtures.Collector c = new SimFixtures.Collector();
        WorldSim sim = WorldSim.genesis(seed, SimFixtures.graph(seed), SimFixtures.data(), "small", DPY, c);
        int target = SimFixtures.data().rules().tier("small").population();
        for (int year = 0; year < 300; year++) {
            SimFixtures.run(sim, DPY, DPY);
            Overview o = sim.overview(DPY);
            assertTrue(o.population() >= target * 0.75 && o.population() <= target * 1.25,
                    "year " + year + ": population " + o.population());
            assertTrue(o.activeSects() >= 1, "year " + year + ": no sect left");
        }
        long successions = c.events.stream().filter(e -> e.type().equals("succession")).count();
        long cores = c.events.stream().filter(e -> e.textKey().startsWith("world_sim.event.breakthrough.golden_core")).count();
        assertTrue(successions >= 1, "masters change");
        assertTrue(cores >= 1, "someone reaches golden core");
    }
}
