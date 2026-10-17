package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.sim.life.LifePlace;
import com.example.myvillage.sim.life.LifeResidentView;
import com.example.myvillage.sim.life.LifeRules;
import com.example.myvillage.sim.life.LifeSite;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.RegionState;
import com.example.myvillage.sim.model.StateCodec;
import com.example.myvillage.sim.model.WorldState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** State/authority regressions. Does not claim to exercise pathfinding or the game event bus. */
class WorldSimLifeTest {
    private static final long SEED = 42;
    private static final int DAY = 24_000;

    private WorldSim fresh() {
        WorldState state = new WorldState();
        state.seed = SEED;
        state.tierId = "small";
        state.genesisDaysPerYear = 24;
        String region = SimFixtures.graph(SEED).regions().getFirst().id();
        for (var r : SimFixtures.graph(SEED).regions()) state.regions.put(r.id(), new RegionState(r.id()));
        for (int id = 1; id <= 3; id++) {
            Person person = new Person();
            person.id = id;
            person.surname = "林";
            person.given = Integer.toString(id);
            person.regionId = region;
            person.homeRegionId = region;
            person.birthDay = -24 * 20;
            person.root = new int[] {2000, 2000, 2000, 2000, 2000};
            person.ambition = 70;
            person.wanderlust = 20;
            state.persons.put(id, person);
        }
        state.nextPersonId = 4;
        return WorldSim.fromBytes(StateCodec.toBytes(state, SimFixtures.data().realms()), SimFixtures.graph(SEED), SimFixtures.data());
    }

    private LifeSite site(WorldSim sim) {
        String region = sim.person(1).orElseThrow().regionId();
        return new LifeSite("test_town", "试镇", "minecraft:overworld", region, 0, 64, 0, List.of(
                new LifePlace("home", "rest", 0, 64, 0, 2, "", 0, 64, 0),
                LifePlace.free("home2", "rest", 1, 64, 0),
                LifePlace.free("herbalist", "work", 2, 64, 0),
                LifePlace.free("gather", "gather", 3, 64, 0),
                LifePlace.free("market", "market", 8, 64, 0),
                LifePlace.free("cultivate", "cultivate", 12, 64, 0),
                LifePlace.free("social", "social", 16, 64, 0)));
    }

    private WorldSim resident(boolean observe) {
        WorldSim sim = fresh();
        sim.registerLifeSite(site(sim));
        sim.assignLife(1, "test_town", "rogue", "home", "gather");
        if (observe) for (LifePlace p : sim.lifeSite("test_town").orElseThrow().places())
            sim.setLifePlaceAvailability("test_town", p.id(), "available");
        sim.advanceLife(0, DAY);
        return sim;
    }

    private LifeResidentView life(WorldSim sim) { return sim.life(1).orElseThrow(); }
    private JsonObject json(WorldSim sim) { return JsonParser.parseString(new String(sim.toBytes(), StandardCharsets.UTF_8)).getAsJsonObject(); }
    private WorldSim restore(JsonObject json) { return WorldSim.fromBytes(json.toString().getBytes(StandardCharsets.UTF_8), SimFixtures.graph(SEED), SimFixtures.data()); }
    private WorldSim restore(WorldSim sim) { return WorldSim.fromBytes(sim.toBytes(), SimFixtures.graph(SEED), SimFixtures.data()); }

    private long arriveAtGather(WorldSim sim) {
        long epoch = sim.activateLife(1);
        sim.advanceLife(20, DAY);
        assertEquals("gather", life(sim).activity());
        assertEquals("moving", life(sim).phase());
        sim.checkpointLife(1, epoch, 3, 64, 0);
        assertTrue(sim.arriveLife(1, epoch, life(sim).revision()));
        return epoch;
    }

    @Test void registrationAndAssignmentAreIdempotentAndCapacityBounded() {
        WorldSim sim = resident(true);
        assertFalse(sim.registerLifeSite(site(sim)));
        assertFalse(sim.assignLife(1, "test_town", "rogue", "home", "gather"));
        assertThrows(IllegalArgumentException.class, () -> sim.assignLife(1, "test_town", "visitor", "home", "market"));
        assertTrue(sim.assignLife(2, "test_town", "herbalist", "home", "herbalist"));
        assertThrows(IllegalArgumentException.class, () -> sim.assignLife(3, "test_town", "visitor", "home", "market"));
        assertTrue(sim.assignLife(3, "test_town", "visitor", "home2", "market"));
        assertEquals(3, sim.lifeResidents().size());
        assertEquals(3, sim.overview(24).population(), "a life assignment must not create people");
        assertTrue(sim.unassignLife(2));
        assertTrue(sim.person(2).orElseThrow().alive());
        assertFalse(sim.unassignLife(2));
    }

    @Test void unobservedPlacesNeverProduceAndUnloadedDoesNotEraseConfirmedObservation() {
        WorldSim sim = resident(false);
        sim.advanceLife(1200, DAY);
        assertEquals(0, life(sim).completedActivities());
        assertEquals("no_available_place", life(sim).reason());
        assertFalse(sim.setLifePlaceAvailability("test_town", "gather", "unloaded"));
        assertTrue(sim.setLifePlaceAvailability("test_town", "gather", "available"));
        assertFalse(sim.setLifePlaceAvailability("test_town", "gather", "unloaded"));
        sim.advanceLife(2400, DAY);
        assertTrue(life(sim).herbs() > 0);
    }

    @Test void coldEconomyHasFiniteGatheringAndConservesItsMoney() {
        WorldSim sim = resident(true);
        sim.assignLife(2, "test_town", "herbalist", "home", "herbalist");
        long startingMoney = sim.lifeSupply("test_town").orElseThrow().treasury()
                + sim.lifeResidents().stream().mapToInt(LifeResidentView::coins).sum();
        for (long tick = 20; tick < DAY; tick += 20) sim.advanceLife(tick, DAY);
        var supply = sim.lifeSupply("test_town").orElseThrow();
        assertTrue(supply.gatheredToday() > 0);
        assertTrue(supply.gatheredToday() <= sim.lifeRules().gatherQuotaPerDay());
        assertEquals(supply.gatheredToday(), supply.stock() + supply.medicine()
                + sim.lifeResidents().stream().mapToInt(LifeResidentView::herbs).sum());
        assertEquals(startingMoney, supply.treasury() + sim.lifeResidents().stream().mapToInt(LifeResidentView::coins).sum());
        assertTrue(supply.medicine() > 0, "delivered herbs must support actual ledger work");
        byte[] before = sim.toBytes();
        assertFalse(sim.advanceLife(DAY - 20, DAY));
        assertArrayEquals(before, sim.toBytes(), "the same clock cannot settle twice");
    }

    @Test void embodiedTravelRequiresARealArrivalCallback() {
        WorldSim sim = resident(true);
        long epoch = sim.activateLife(1);
        sim.advanceLife(1200, DAY);
        LifeResidentView moving = life(sim);
        assertEquals("moving", moving.phase());
        assertEquals(0, moving.completedActivities());
        assertEquals(0, moving.herbs());
        assertFalse(sim.arriveLife(1, epoch, moving.revision()), "home is not the gather site");
        assertTrue(sim.checkpointLife(1, epoch, 3, 64, 0));
        assertTrue(sim.arriveLife(1, epoch, moving.revision()));
        assertFalse(sim.arriveLife(1, epoch, moving.revision()));
        sim.advanceLife(1600, DAY);
        assertEquals(sim.lifeRules().gatherUnits(), life(sim).herbs());
        assertFalse(sim.arriveLife(1, epoch, moving.revision()), "an old activity cannot complete again");
    }

    @Test void workProgressSurvivesWithdrawalReloadAndNewEpoch() {
        WorldSim sim = resident(true);
        long epoch = arriveAtGather(sim);
        sim.advanceLife(120, DAY);
        long progress = life(sim).progressTicks(), revision = life(sim).revision();
        assertTrue(sim.releaseLife(1, epoch, 3, 64, 0));
        WorldSim loaded = restore(sim);
        assertArrayEquals(sim.toBytes(), loaded.toBytes());
        assertEquals(progress, life(loaded).progressTicks());
        assertEquals(revision, life(loaded).revision());
        long newEpoch = loaded.activateLife(1);
        assertTrue(newEpoch > epoch);
        assertFalse(loaded.checkpointLife(1, epoch, 20, 64, 0));
        assertFalse(loaded.failLife(1, epoch, revision, "stuck"));
        assertFalse(loaded.releaseLife(1, epoch, 0, 64, 0));
        loaded.advanceLife(420, DAY);
        assertEquals(sim.lifeRules().gatherUnits(), life(loaded).herbs());
    }

    @Test void aLeaseFromBeforeUnassignCannotControlTheSamePersonAfterReassignment() {
        WorldSim sim = resident(true);
        long oldEpoch = sim.activateLife(1);
        sim.unassignLife(1);
        sim.assignLife(1, "test_town", "rogue", "home", "gather");
        long newEpoch = sim.activateLife(1);
        assertTrue(newEpoch > oldEpoch);
        assertFalse(sim.releaseLife(1, oldEpoch, 0, 64, 0));
        assertTrue(life(sim).embodied());
    }

    @Test void conversationPausesEarnedProgressAndRepeatedPauseDoesNotAddTime() {
        WorldSim sim = resident(true);
        arriveAtGather(sim);
        sim.advanceLife(120, DAY);
        long earned = life(sim).progressTicks(), revision = life(sim).revision();
        assertTrue(sim.pauseLife(1, sim.lifeRules().conversationPauseTicks()));
        assertFalse(sim.pauseLife(1, sim.lifeRules().conversationPauseTicks()));
        sim.advanceLife(180, DAY);
        assertEquals(earned, life(sim).progressTicks());
        assertEquals(0, life(sim).pauseTicks());
        assertEquals(revision, life(sim).revision());
        sim.advanceLife(200, DAY);
        assertEquals(earned + 20, life(sim).progressTicks());
    }

    @Test void removedFacilityCannotPayForUnfinishedWork() {
        WorldSim sim = resident(true);
        long epoch = arriveAtGather(sim);
        sim.advanceLife(400, DAY);
        long revision = life(sim).revision();
        assertTrue(sim.setLifePlaceAvailability("test_town", "gather", "blocked"));
        sim.advanceLife(420, DAY);
        assertEquals(0, life(sim).herbs());
        assertEquals("waiting", life(sim).phase());
        assertFalse(sim.arriveLife(1, epoch, revision));
    }

    @Test void failedRouteCannotBeConvertedToColdGatheringOnWithdrawal() {
        WorldSim sim = resident(true);
        long epoch = sim.activateLife(1);
        sim.advanceLife(20, DAY);
        assertTrue(sim.failLife(1, epoch, life(sim).revision(), "path_unavailable"));
        assertFalse(sim.failLife(1, epoch, life(sim).revision(), "path_unavailable"));
        sim.releaseLife(1, epoch, 0, 64, 0);
        for (int tick = 40; tick < 2000; tick += 20) sim.advanceLife(tick, DAY);
        assertEquals(0, life(sim).herbs());
        assertEquals(0, sim.lifeSupply("test_town").orElseThrow().gatheredToday());
    }

    @Test void boundedCatchUpMatchesTheSameNormalIntervals() {
        WorldSim normal = resident(true), batched = restore(normal);
        for (int tick = 20; tick <= 1200; tick += 20) normal.advanceLife(tick, DAY);
        batched.advanceLife(1200, DAY);
        assertArrayEquals(normal.toBytes(), batched.toBytes());
    }

    @Test void globalPauseAndClockRewindNeverAwardMissedWork() {
        WorldSim sim = resident(true);
        sim.advanceLife(200, DAY);
        long progress = life(sim).progressTicks();
        sim.scheduler().setPaused(true);
        sim.advanceLife(2400, DAY);
        assertEquals(progress, life(sim).progressTicks());
        sim.scheduler().setPaused(false);
        sim.advanceLife(2420, DAY);
        assertEquals(progress + 20, life(sim).progressTicks());
        sim.advanceLife(2000, DAY);
        assertEquals(progress + 20, life(sim).progressTicks());
    }

    @Test void restartDropsOnlyTransientPresenceAndConversationPause() {
        WorldSim sim = resident(true);
        long epoch = arriveAtGather(sim);
        sim.pauseLife(1, sim.lifeRules().conversationPauseTicks());
        sim.setLifeObserved(1, true);
        WorldSim loaded = restore(sim);
        assertFalse(life(loaded).embodied());
        assertFalse(life(loaded).observed());
        assertEquals(0, life(loaded).pauseTicks());
        assertEquals(life(sim).revision(), life(loaded).revision());
        assertEquals(life(sim).progressTicks(), life(loaded).progressTicks());
        assertTrue(loaded.activateLife(1) > epoch);
    }

    @Test void nearbyResidentWithoutAvatarCannotEarnInvisibleWorkOrCatchUpItLater() {
        WorldSim sim = resident(true);
        sim.advanceLife(200, DAY);
        long earned = life(sim).progressTicks(), revision = life(sim).revision();
        sim.setLifeObserved(1, true); // for example, the global avatar budget is full
        sim.advanceLife(1400, DAY);
        assertEquals(earned, life(sim).progressTicks());
        assertEquals(revision, life(sim).revision());
        assertEquals(0, life(sim).herbs());
        sim.setLifeObserved(1, false);
        sim.advanceLife(1420, DAY);
        assertEquals(earned + 20, life(sim).progressTicks());
        assertEquals(0, life(sim).herbs(), "blocked time must not become catch-up income");
    }

    @Test void oldSaveLoadsWithoutInventingResidentsAndRejectsNewerOrBrokenLife() {
        WorldSim sim = resident(true);
        JsonObject old = json(sim);
        old.addProperty("version", 3); old.remove("life"); old.remove("social");
        assertTrue(restore(old).lifeResidents().isEmpty());
        JsonObject newer = json(sim); newer.getAsJsonObject("life").addProperty("schema", 2);
        assertThrows(SimFormatException.class, () -> restore(newer));
        JsonObject broken = json(sim);
        broken.getAsJsonObject("life").getAsJsonArray("residents").get(0).getAsJsonObject().addProperty("home_id", "missing");
        assertThrows(SimFormatException.class, () -> restore(broken));
        JsonObject negative = json(sim);
        negative.getAsJsonObject("life").getAsJsonArray("residents").get(0).getAsJsonObject().addProperty("coins", -1);
        assertThrows(SimFormatException.class, () -> restore(negative));
        JsonObject invalidCapacity = json(sim);
        invalidCapacity.getAsJsonObject("life").getAsJsonArray("sites").get(0).getAsJsonObject()
                .getAsJsonArray("places").get(0).getAsJsonObject().addProperty("capacity", 0);
        SimFormatException rejected = assertThrows(SimFormatException.class, () -> restore(invalidCapacity));
        assertNotNull(rejected.getCause(), "record constructor failure must remain available for diagnosis");
        JsonObject invalidId = json(sim);
        invalidId.getAsJsonObject("life").getAsJsonArray("sites").get(0).getAsJsonObject()
                .getAsJsonArray("places").get(0).getAsJsonObject().addProperty("id", "invalid id");
        assertThrows(SimFormatException.class, () -> restore(invalidId));
    }

    @Test void tuningIsStrictAndNamesUnknownFields() throws Exception {
        JsonObject rules = JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/world_sim/life_rules.json"))).getAsJsonObject();
        assertEquals(6, LifeRules.parse("life_rules.json", rules).durations().size());
        rules.addProperty("unknown_tuning", 1);
        assertEquals("unknown_tuning", assertThrows(SimDataException.class,
                () -> LifeRules.parse("life_rules.json", rules)).field());
        rules.remove("unknown_tuning"); rules.addProperty("runtime_interval_ticks", 1.5);
        assertThrows(SimDataException.class, () -> LifeRules.parse("life_rules.json", rules));
    }
}
