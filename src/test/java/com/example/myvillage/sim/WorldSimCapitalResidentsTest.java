package com.example.myvillage.sim;

import com.example.myvillage.sim.combat.NpcCombatProfile;
import com.example.myvillage.sim.life.*;
import com.example.myvillage.sim.model.*;
import com.example.myvillage.story.capital.CapitalOpeningRules;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldSimCapitalResidentsTest {
    private static final long SEED = 42;
    private List<CapitalResidentDefinition> definitions() throws Exception {
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/data/myvillage/story/capital_opening.json"))) {
            return CapitalOpeningRules.read(reader).residents();
        }
    }
    private WorldSim fresh() {
        var state = new WorldState(); state.seed = SEED; state.tierId = "small"; state.genesisDaysPerYear = 24; state.day = 1;
        for (var r : SimFixtures.graph(SEED).regions()) state.regions.put(r.id(), new RegionState(r.id()));
        return WorldSim.fromBytes(StateCodec.toBytes(state, SimFixtures.data().realms()), SimFixtures.graph(SEED), SimFixtures.data());
    }
    private LifeSite site() throws Exception {
        var places = new ArrayList<LifePlace>(); int x = 0;
        for (var d : definitions()) {
            places.add(LifePlace.free(d.home(), "rest", x, 64, 0));
            places.add(LifePlace.free(d.work(), d.role().equals("guide") ? "entry" : "work", x+1, 64, 0)); x += 4;
        }
        return new LifeSite("capital_test", "首城", "minecraft:overworld", SimFixtures.graph(SEED).regions().getFirst().id(), 0, 64, 0, places);
    }
    private WorldSim reserved() throws Exception {
        var sim = fresh(); sim.registerLifeSite(site()); assertTrue(sim.ensureCapitalResidents(site().id(), definitions())); return sim;
    }
    private WorldSim restore(WorldSim sim) { return WorldSim.fromBytes(sim.toBytes(), SimFixtures.graph(SEED), SimFixtures.data()); }

    @Test void unloadedFacilitiesAndEmptyPopulationStillReserveFourDurableOrdinaryPeople() throws Exception {
        var sim = reserved(); assertEquals(4, sim.lifeResidents().size()); assertTrue(sim.storyActors().isEmpty());
        byte[] before = sim.toBytes(); assertFalse(sim.ensureCapitalResidents(site().id(), definitions())); assertArrayEquals(before, sim.toBytes());
        var loaded = restore(sim); assertArrayEquals(before, loaded.toBytes());
        for (var d : definitions()) {
            int id = sim.capitalResident(site().id(), d.role()).orElseThrow();
            assertEquals(id, loaded.capitalResident(site().id(), d.role()).orElseThrow());
            assertEquals(d.home(), loaded.life(id).orElseThrow().homeId());
            assertEquals(d.work(), loaded.life(id).orElseThrow().workId());
            assertFalse(loaded.storyDamageProtected(id));
        }
        assertFalse(loaded.ensureCapitalResidents(site().id(), definitions()));
    }
    @Test void commerceUsesReservedPeopleAndDoesNotCreateDuplicateStaff() throws Exception {
        var sim = reserved(); assertTrue(sim.initializeCommerce(site().id())); assertTrue(sim.staffCommerce(site().id()).isEmpty());
        assertEquals(4, sim.lifeResidents().size());
        sim.observeLifeDaylight(0.5);
        for (var place : site().places()) sim.setLifePlaceAvailability(site().id(), place.id(), "available");
        sim.advanceLife(0,24_000); sim.advanceLife(200,24_000);
        for (var d : definitions()) {
            var r = sim.life(sim.capitalResident(site().id(), d.role()).orElseThrow()).orElseThrow();
            assertEquals(d.role().equals("guide") ? "social" : "work", r.activity());
            assertEquals(d.work(), r.targetId());
        }
    }
    @Test void deathAndUnassignmentDoNotCreateReplacementPeopleAfterRestart() throws Exception {
        var sim = reserved(); int id = sim.capitalResident(site().id(), "herbalist").orElseThrow();
        long epoch = sim.claimCombat(id, new NpcCombatProfile(15,20,80,"",0,1,"",0,null));
        assertTrue(sim.combatDeath(id,epoch,"","","generic",0));
        int guide = sim.capitalResident(site().id(), "guide").orElseThrow(); assertTrue(sim.unassignLife(guide));
        var loaded = restore(sim); assertFalse(loaded.ensureCapitalResidents(site().id(), definitions()));
        assertEquals(id, loaded.capitalResident(site().id(), "herbalist").orElseThrow());
        assertTrue(loaded.life(id).isEmpty()); assertTrue(loaded.life(guide).isEmpty()); assertEquals(2, loaded.lifeResidents().size());
    }
    @Test void badLastPlaceRejectsTheWholeReservationBeforeAnyPersonOrRoleIsWritten() throws Exception {
        var sim = fresh(); var base = site();
        sim.registerLifeSite(new LifeSite(base.id(), base.name(), base.dimension(), base.regionId(), 0,64,0,
                base.places().stream().filter(p -> !p.id().equals("smith_work")).toList()));
        byte[] before = sim.toBytes();
        assertThrows(IllegalArgumentException.class, () -> sim.ensureCapitalResidents(base.id(), definitions()));
        assertArrayEquals(before, sim.toBytes()); assertTrue(sim.lifeResidents().isEmpty());
    }
    @Test void missingAndPartialSavedRosterAreRejected() throws Exception {
        var sim = reserved(); var json = JsonParser.parseString(new String(sim.toBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        var roster = json.getAsJsonObject("life").getAsJsonObject("capital_residents"); roster.remove("capital_test|smith");
        assertThrows(SimFormatException.class, () -> WorldSim.fromBytes(json.toString().getBytes(StandardCharsets.UTF_8), SimFixtures.graph(SEED), SimFixtures.data()));
        json.getAsJsonObject("life").remove("capital_residents");
        assertThrows(SimFormatException.class, () -> WorldSim.fromBytes(json.toString().getBytes(StandardCharsets.UTF_8), SimFixtures.graph(SEED), SimFixtures.data()));
    }
    @Test void existingServiceResidentsAreAdoptedWithoutResettingThemOrAddingPeople() throws Exception {
        var sim = reserved(); var json = JsonParser.parseString(new String(sim.toBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        json.getAsJsonObject("life").add("capital_residents",new com.google.gson.JsonObject());
        var existing = WorldSim.fromBytes(json.toString().getBytes(StandardCharsets.UTF_8), SimFixtures.graph(SEED), SimFixtures.data());
        var people = existing.lifeResidents(); assertTrue(existing.ensureCapitalResidents(site().id(), definitions()));
        assertEquals(people,existing.lifeResidents()); assertEquals(4,existing.lifeResidents().size());
        assertArrayEquals(sim.toBytes(),existing.toBytes());
    }
    @Test void reassignedHistoricalGuideRejectsAdoptionBeforeAnyWrite() throws Exception {
        var sim = reserved(); var json = JsonParser.parseString(new String(sim.toBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        json.getAsJsonObject("life").add("capital_residents",new com.google.gson.JsonObject());
        for (var r : json.getAsJsonObject("life").getAsJsonArray("residents"))
            if (r.getAsJsonObject().get("role").getAsString().equals("guide")) r.getAsJsonObject().addProperty("role","visitor");
        var existing = WorldSim.fromBytes(json.toString().getBytes(StandardCharsets.UTF_8), SimFixtures.graph(SEED), SimFixtures.data());
        byte[] before=existing.toBytes();
        assertThrows(IllegalStateException.class,()->existing.ensureCapitalResidents(site().id(),definitions()));
        assertArrayEquals(before,existing.toBytes());
    }
}
