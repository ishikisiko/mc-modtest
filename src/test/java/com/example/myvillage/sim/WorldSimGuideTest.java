package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.sim.life.*;
import com.example.myvillage.sim.model.*;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldSimGuideTest {
    private WorldSim fresh() {
        var graph = SimFixtures.graph(42); var state = new WorldState();
        state.seed = 42; state.tierId = "small"; state.genesisDaysPerYear = 24;
        for (var region : graph.regions()) state.regions.put(region.id(), new RegionState(region.id()));
        for (int id = 1; id <= 3; id++) {
            var p = new Person(); p.id = id; p.surname = "林"; p.given = "游" + id;
            p.regionId = graph.regions().getFirst().id(); p.homeRegionId = p.regionId;
            p.birthDay = -480; p.root = new int[]{2000,2000,2000,2000,2000}; state.persons.put(id,p);
        }
        state.nextPersonId = 4;
        var sim = WorldSim.fromBytes(StateCodec.toBytes(state,SimFixtures.data().realms()),graph,SimFixtures.data());
        sim.registerLifeSite(new LifeSite("town","试镇","minecraft:overworld",graph.regions().getFirst().id(),0,64,0,List.of(
                LifePlace.free("home","rest",0,64,0),LifePlace.free("desk","social",2,64,0))));
        return sim;
    }
    private WorldSim restore(WorldSim sim) { return WorldSim.fromBytes(sim.toBytes(),SimFixtures.graph(42),SimFixtures.data()); }
    private void observe(WorldSim sim) { sim.setLifePlaceAvailability("town","home","available"); sim.setLifePlaceAvailability("town","desk","available"); }
    @Test void onlyObservedExistingLocalPeopleAreAssignedOnce() {
        var sim = fresh(); assertEquals(-1,sim.staffTownGuide("town","home","desk"));
        observe(sim); int id = sim.staffTownGuide("town","home","desk"); assertTrue(id>0);
        assertEquals("guide",sim.life(id).orElseThrow().role());
        assertEquals(id,sim.staffTownGuide("town","home","desk"));
        var copy = restore(sim); assertEquals(id,copy.staffTownGuide("town","home","desk"));
        assertEquals(1,copy.lifeResidents().size());
    }
    @Test void vacancyDoesNotCreateAnotherIdentityAfterReload() {
        var sim=fresh();observe(sim);int id=sim.staffTownGuide("town","home","desk");sim.unassignLife(id);
        var copy=restore(sim);assertEquals(id,copy.staffTownGuide("town","home","desk"));
        assertTrue(copy.life(id).isEmpty());assertEquals(id,copy.townGuide("town").orElseThrow());
        assertThrows(IllegalArgumentException.class,()->copy.assignLife(id==1?2:1,"town","guide","home","desk"));
    }
    @Test void guideWorksByDayRestsAtNightAndDoesNotMintTradeIncome() {
        var sim=fresh();observe(sim);int id=sim.staffTownGuide("town","home","desk");
        sim.observeLifeDaylight(.25);sim.advanceLife(0,24000);
        for(int tick=20;tick<=800;tick+=20)sim.advanceLife(tick,24000);
        var day=sim.life(id).orElseThrow();assertEquals("social",day.activity());assertEquals("desk",day.targetId());
        int coins=day.coins();sim.advanceLife(1000,24000);assertEquals(coins,sim.life(id).orElseThrow().coins());
        sim.observeLifeDaylight(.7);sim.advanceLife(1020,24000);assertEquals("rest",sim.life(id).orElseThrow().activity());
    }
    @Test void oldV6WithoutGuideMetadataRemainsReadable() {
        var sim=fresh();var json=JsonParser.parseString(new String(sim.toBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        json.addProperty("version",6);json.getAsJsonObject("life").remove("guide_assignments");
        var old=WorldSim.fromBytes(json.toString().getBytes(StandardCharsets.UTF_8),SimFixtures.graph(42),SimFixtures.data());
        assertTrue(old.townGuide("town").isEmpty());observe(old);assertTrue(old.staffTownGuide("town","home","desk")>0);
        assertEquals(StateCodec.VERSION,JsonParser.parseString(new String(old.toBytes(),StandardCharsets.UTF_8)).getAsJsonObject().get("version").getAsInt());
    }
    @Test void guideDeathKeepsVacancyAcrossSaveAndDoesNotRecruitASecondPerson() {
        var sim=fresh();observe(sim);int id=sim.staffTownGuide("town","home","desk");
        var profile=new com.example.myvillage.sim.combat.NpcCombatProfile(20,20,0,"",0,0,"",0,null);
        long lease=sim.claimCombat(id,profile);assertTrue(lease>0);
        assertTrue(sim.combatDeath(id,lease,"environment:fall","","fall",0));
        assertTrue(sim.life(id).isEmpty());
        var copy=restore(sim);assertEquals(id,copy.staffTownGuide("town","home","desk"));
        assertTrue(copy.lifeResidents().isEmpty());assertFalse(copy.person(id).orElseThrow().alive());
    }
    @Test void automaticGuideNeedsAStationWithinTheExistingPathBudget() {
        var sim=fresh();observe(sim);
        sim.addLifePlace("town",LifePlace.free("distant","social",2000,64,0));
        sim.setLifePlaceAvailability("town","distant","available");
        assertEquals(-1,sim.staffTownGuide("town","home","distant"));
        assertTrue(sim.townGuide("town").isEmpty());
    }
}
