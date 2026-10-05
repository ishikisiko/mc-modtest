package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.region.runtime.RegionQueries;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorldSimGenesisTest {
    private static final int DPY = 6;

    @ParameterizedTest
    @CsvSource({"1, small", "2, small", "3, medium", "4, large"})
    void sectsAndGatesFollowTheRegionRules(long seed, String tier) {
        RegionGraph graph = SimFixtures.graph(seed);
        WorldSim sim = WorldSim.genesis(seed, graph, SimFixtures.data(), tier, DPY);
        var rules = SimFixtures.data().rules();
        List<SectView> all = sim.sects(true);
        assertTrue(all.size() >= rules.tier(tier).sects(), "every genesis sect is in the ledger");
        for (SectView s : all) {
            GenRegion home = graph.regionById(s.regionId());
            assertTrue(home.admittedSubjects().contains("sect"), s.name() + " sits in " + home.id() + ", which admits no sects");
            assertEquals(s.regionId(), RegionQueries.regionAt(graph, s.gateX(), s.gateZ()).orElse(""),
                    "gate of " + s.name() + " is inside its region");
            assertTrue(Math.hypot(s.gateX(), s.gateZ()) <= rules.gates().maxRadius());
            assertEquals(8, Math.floorMod(s.gateX(), 16));
            assertEquals(8, Math.floorMod(s.gateZ(), 16));
            assertFalse(s.gateRealized());
            for (SectView o : all) {
                if (o.id() != s.id()) {
                    assertTrue(Math.hypot(s.gateX() - o.gateX(), s.gateZ() - o.gateZ()) >= rules.gates().minSpacing(),
                            "gates of " + s.name() + " and " + o.name() + " are too close");
                }
            }
            assertFalse(s.founderName().isEmpty(), "a sect remembers its founder");
        }
        Overview o = sim.overview(DPY);
        int target = rules.tier(tier).population();
        assertTrue(o.population() > target * 0.7 && o.population() < target * 1.3,
                "population after prehistory " + o.population() + " vs target " + target);
    }

    @ParameterizedTest
    @CsvSource({"9"})
    void gateMutations(long seed) {
        RegionGraph graph = SimFixtures.graph(seed);
        WorldSim sim = WorldSim.genesis(seed, graph, SimFixtures.data(), "small", DPY);
        SectView s = sim.sects(false).get(0);
        sim.markGateRealized(s.id(), true);
        assertTrue(sim.sect(s.id()).orElseThrow().gateRealized());
        assertThrows(IllegalArgumentException.class, () -> sim.moveGate(s.id(), 100_000, 0));
        sim.moveGate(s.id(), 8, 8);
        SectView moved = sim.sect(s.id()).orElseThrow();
        assertEquals(8, moved.gateX());
        assertEquals(RegionQueries.regionAt(graph, 8, 8).orElseThrow(), moved.regionId());
        for (PersonView p : sim.membersAt(s.id())) {
            assertEquals(moved.regionId(), p.regionId());
        }
    }
}
