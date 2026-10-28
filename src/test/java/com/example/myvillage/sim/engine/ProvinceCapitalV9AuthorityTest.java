package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.life.LifePlace;
import com.example.myvillage.sim.life.LifeSite;
import com.example.myvillage.sim.model.RegionState;
import com.example.myvillage.sim.model.StateCodec;
import com.example.myvillage.sim.model.WorldState;
import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.WorldPlanRules;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** V9 沿用已有命簿与山门语义；不增加交易细节验收。 */
class ProvinceCapitalV9AuthorityTest {
    private static WorldPlanRules rules() throws Exception {
        try (var input = ProvinceCapitalV9AuthorityTest.class.getResourceAsStream(WorldPlanRules.V9_RESOURCE)) {
            assertNotNull(input);
            return new WorldPlanRules(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
    private static SimData.ResourceOpener opener() { return p -> ProvinceCapitalV9AuthorityTest.class.getResourceAsStream("/" + p); }
    private static WorldState state(WorldPlan plan) {
        var state = new WorldState(); state.seed = plan.seed(); state.tierId = "small"; state.genesisDaysPerYear = 24;
        for (var region : plan.graph().regions()) state.regions.put(region.id(), new RegionState(region.id()));
        return state;
    }

    @Test void frozen192PlaceBudgetSurvivesRestoreAndRejectsOverflow() throws Exception {
        var rules = rules(); var plan = new WorldPlan(20261029, rules);
        var data = WorldSim.loadData(rules.capitals.lifeOpener(opener()));
        assertEquals(192, data.lifeRules().maxPlacesPerSite());
        assertEquals(128, WorldSim.loadData(opener()).lifeRules().maxPlacesPerSite());
        var sim = WorldSim.fromBytes(StateCodec.toBytes(state(plan), data.realms()), plan.graph(), data);
        var places = new ArrayList<LifePlace>();
        for (int i = 0; i < 192; i++) places.add(LifePlace.free("p" + i, "rest", i, 64, 0));
        String region = plan.graph().regions().getFirst().id();
        assertTrue(sim.registerLifeSite(new LifeSite("budget", "容量回归", "minecraft:overworld", region, 0, 64, 0, places)));
        byte[] before = sim.toBytes();
        assertEquals("place_limit", assertThrows(IllegalArgumentException.class,
                () -> sim.addLifePlace("budget", LifePlace.free("overflow", "rest", 0, 64, 0))).getMessage());
        assertArrayEquals(before, sim.toBytes());
        var restored = WorldSim.fromBytes(before, plan.graph(), data);
        assertEquals(192, restored.lifeSite("budget").orElseThrow().places().size());
    }

    @Test void genesisEntrantsAndGatesUseTheSameEightRegionSemanticsAsV8() throws Exception {
        var rules = rules(); var plan = new WorldPlan(20261029, rules);
        var data = WorldSim.loadData(rules.capitals.lifeOpener(opener()));
        var ctx = new SimContext(state(plan), data, plan.graph(), 24);
        Genesis.populate(ctx);
        Set<String> regionIds = Set.copyOf(plan.graph().regions().stream().map(r -> r.id()).toList());
        assertEquals(regionIds, Set.copyOf(ctx.state.persons.values().stream().filter(p -> p.sectId < 0).map(p -> p.regionId).toList()));
        var haunts = new HashSet<String>();
        for (int i = 0; i < 256; i++) haunts.add(Entrants.rogueHaunt(ctx, SimRng.at(plan.seed(), 0, i, 37)));
        assertEquals(regionIds, haunts);
        assertTrue(ctx.state.sects.values().stream().anyMatch(s -> StrictMath.hypot(s.gateX, s.gateZ) > ctx.rules.gates().maxRadius()));
        for (var sect : ctx.state.sects.values()) {
            assertTrue(GatePlacement.valid(ctx, sect.homeRegionId, sect.gateX, sect.gateZ, sect.id), sect.name);
            assertFalse(plan.graph().geometry().reserved(sect.gateX, sect.gateZ, ctx.rules.gates().minSpacing()));
            assertTrue(SimContext.admitsSects(ctx.region(sect.homeRegionId)));
        }
    }
}
