package com.example.myvillage.sim.engine;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.cli.SimCli;
import com.example.myvillage.sim.combat.NpcCombatProfile;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.StateCodec;
import com.example.myvillage.sim.model.WorldState;
import com.example.myvillage.sim.life.LifeResident;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NpcCombatLedgerTest {
    static SimData data;
    static RegionGraph graph;
    static final String PLAYER = "player:00000000-0000-0000-0000-00000000000a";
    @BeforeAll static void setup() throws Exception {
        data = WorldSim.loadData(path -> Files.newInputStream(Path.of("src/main/resources").resolve(path)));
        graph = SimCli.buildGraph(Path.of("."), 21);
    }
    static SimContext ctx() {
        WorldState state = new WorldState(); state.seed = 21; state.tierId = "small"; state.genesisDaysPerYear = 6;
        var ctx = new SimContext(state, data, graph, 6);
        for (int i = 1; i <= 3; i++) {
            Person p = new Person(); p.id = i; p.given = "npc" + i; p.root = new int[]{10000, 0, 0, 0, 0};
            p.realm = data.realms().indexOf("qi_refining"); p.regionId = graph.regions().get(0).id();
            p.homeRegionId = p.regionId; state.persons.put(i, p);
        }
        state.nextPersonId = 4; ctx.newDay(); return ctx;
    }
    static NpcCombatProfile profile(double health, long revision) {
        return new NpcCombatProfile(health, 20, 12, "{id:\"myvillage:qingfeng_sword\",count:1}",
                400, revision, PLAYER, 320, new NpcCombatProfile.Body("minecraft:overworld", "region", 2, 70, 3,
                    0, -0.5, 0, 9, 60, false));
    }
    @Test void oneLeaseRejectsOldBodiesAndDoesNotRefreshLosses() {
        var ctx = ctx(); long first = NpcCombatLedger.claim(ctx, 1, profile(20, 0));
        assertTrue(first > 0); assertEquals(-1, NpcCombatLedger.claim(ctx, 1, profile(20, 0)));
        assertTrue(NpcCombatLedger.checkpoint(ctx, 1, first, profile(7, 1)));
        assertFalse(NpcCombatLedger.checkpoint(ctx, 1, first, profile(19, 1)));
        assertTrue(NpcCombatLedger.release(ctx, 1, first));
        long second = NpcCombatLedger.claim(ctx, 1, profile(20, 0)); assertTrue(second > first);
        assertFalse(NpcCombatLedger.checkpoint(ctx, 1, first, profile(20, 2)));
        assertFalse(NpcCombatLedger.release(ctx, 1, first));
        assertEquals(7, ctx.state.combat.profiles.get(1).health());
    }
    @Test void saveRestoresLossesEquipmentAttributionAndFallingButNoLease() {
        var ctx = ctx(); NpcCombatLedger.claim(ctx, 1, profile(7, 4));
        byte[] bytes = StateCodec.toBytes(ctx.state, data.realms());
        var restored = StateCodec.fromBytes(bytes, data.realms());
        assertEquals(profile(7, 4), restored.combat.profiles.get(1));
        assertTrue(restored.combat.leases.isEmpty()); assertTrue(restored.combat.engaged.isEmpty());
        assertArrayEquals(bytes, StateCodec.toBytes(restored, data.realms()));
    }
    @Test void oldSaveInitializesOnceWithoutMistakingZeroHealthForMissingData() {
        var ctx = ctx(); var root = StateCodec.toJson(ctx.state, data.realms());
        root.addProperty("version", 4); root.remove("combat");
        var restored = StateCodec.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8), data.realms());
        var fresh = new SimContext(restored, data, graph, 6);
        assertTrue(NpcCombatLedger.claim(fresh, 1, profile(20, 0)) > 0);
        fresh.state.combat.profiles.put(2, profile(0, 1));
        assertEquals(-1, NpcCombatLedger.claim(fresh, 2, profile(20, 0)));
    }
    @Test void realDeathIsIdempotentAndPlayerGrievancesKeepStableIdentity() {
        var ctx = ctx(); ctx.state.persons.get(1).masterId = 2;
        long lease = NpcCombatLedger.claim(ctx, 1, profile(7, 1));
        assertTrue(NpcCombatLedger.die(ctx, 1, lease, PLAYER, "Alice", "player", 40));
        assertFalse(NpcCombatLedger.die(ctx, 1, lease, PLAYER, "Alice", "player", 40));
        assertFalse(ctx.state.persons.containsKey(1)); assertFalse(ctx.state.combat.profiles.containsKey(1));
        assertEquals(PLAYER, ctx.state.tombstones.get(1).killerActor);
        assertEquals(lease, ctx.state.tombstones.get(1).combatEpoch);
        assertTrue(ctx.state.combat.grievances.get(2).containsKey(PLAYER));
        assertEquals(-1, NpcCombatLedger.claim(ctx, 1, profile(20, 0)));
        assertEquals(1, ctx.state.chronicle.stream().filter(e -> e.type().equals("slain")).count());
        var restored = StateCodec.fromBytes(StateCodec.toBytes(ctx.state, data.realms()), data.realms());
        assertEquals(PLAYER, restored.tombstones.get(1).killerActor);
    }
    @Test void npcKillUsesExistingConsequencesAndEnvironmentNeverInventsNpcKiller() {
        var ctx = ctx(); long lease = NpcCombatLedger.claim(ctx, 1, profile(7, 1));
        assertTrue(NpcCombatLedger.die(ctx, 1, lease, "npc:2", "npc2", "mob", 40));
        assertEquals(1, ctx.state.persons.get(2).kills); assertEquals(2, ctx.state.tombstones.get(1).killerId);
        long another = NpcCombatLedger.claim(ctx, 3, profile(20, 0));
        assertTrue(NpcCombatLedger.die(ctx, 3, another, "environment:fall", "", "fall", 40));
        assertEquals(-1, ctx.state.tombstones.get(3).killerId);
    }
    @Test void allNearFieldPeopleAreExcludedAsBothInitiatorAndVictimOfColdConflicts() {
        var ctx = ctx(); Person a = ctx.state.persons.get(1), b = ctx.state.persons.get(2);
        a.aggression = 100; b.aggression = 100;
        NpcCombatLedger.claim(ctx, 1, profile(20, 0));
        assertTrue(People.lifeResident(ctx, a)); assertFalse(People.lifeResident(ctx, b));
        assertFalse(Revenge.dares(ctx, a, b)); assertFalse(Revenge.dares(ctx, b, a));
    }
    @Test void releasingCombatRequiresANewWorkplaceArrival() {
        var ctx = ctx(); LifeResident resident = new LifeResident(); resident.personId = 1;
        resident.phase = "working"; resident.targetId = "shop"; resident.progressTicks = 95; resident.requiredTicks = 100;
        ctx.state.life.residents.put(1, resident);
        long lease = NpcCombatLedger.claim(ctx, 1, profile(7, 1));
        assertTrue(NpcCombatLedger.engage(ctx, 1, lease, true));
        assertEquals(95, resident.progressTicks);
        assertTrue(NpcCombatLedger.engage(ctx, 1, lease, false));
        assertEquals("waiting", resident.phase); assertEquals(0, resident.progressTicks);
        assertEquals("", resident.targetId);
    }
    @Test void savingDuringCombatCannotResumeAnOldJobOnLoad() {
        var ctx = ctx(); LifeResident resident = new LifeResident(); resident.personId = 1;
        resident.phase = "working"; resident.targetId = "shop"; resident.progressTicks = 95; resident.requiredTicks = 100;
        ctx.state.life.residents.put(1, resident);
        long lease = NpcCombatLedger.claim(ctx, 1, profile(7, 1));
        NpcCombatLedger.engage(ctx, 1, lease, true);
        var saved = com.example.myvillage.sim.combat.NpcCombatCodec.write(ctx.state.combat);
        var restored = com.example.myvillage.sim.combat.NpcCombatCodec.read(saved, ctx.state);
        assertEquals("waiting", resident.phase); assertEquals(0, resident.progressTicks);
        assertTrue(restored.leases.isEmpty()); assertTrue(restored.engaged.isEmpty());
        assertEquals(profile(7, 1), restored.profiles.get(1));
    }

    @Test void killingASectMemberRecordsPlayerEnmityEvenWithoutPlayerMembership() {
        var ctx = ctx(); var sect = new com.example.myvillage.sim.model.Sect();
        sect.id = 1; sect.name = "test"; sect.homeRegionId = ctx.state.persons.get(1).regionId;
        sect.masterId = 3; ctx.state.sects.put(1, sect); ctx.state.persons.get(1).sectId = 1;
        ctx.state.persons.get(3).sectId = 1; ctx.state.persons.get(3).rank = "sect_master";
        ctx.membershipChanged();
        long lease = NpcCombatLedger.claim(ctx, 1, profile(7, 1));
        assertTrue(NpcCombatLedger.die(ctx, 1, lease, PLAYER, "Alice", "player", 40));
        assertTrue(ctx.state.playerMembers.isEmpty());
        assertTrue(ctx.state.combat.sectGrievances.get(1).containsKey(PLAYER));
        assertEquals(com.example.myvillage.sim.Admission.STANDING_TOO_LOW,
                PlayerAffairs.admission(ctx, PLAYER.substring(7), 1,
                    new com.example.myvillage.sim.PlayerQualification("qi_refining", 0, true, 5000)).reason());
        var restored = StateCodec.fromBytes(StateCodec.toBytes(ctx.state, data.realms()), data.realms());
        assertEquals(ctx.state.combat.sectGrievances, restored.combat.sectGrievances);
    }

    @Test void malformedOrDuplicateCombatSaveFailsInsteadOfSilentlyResetting() {
        var ctx = ctx(); NpcCombatLedger.claim(ctx, 1, profile(7, 1));
        var root = StateCodec.toJson(ctx.state, data.realms());
        var entries = root.getAsJsonObject("combat").getAsJsonArray("profiles"); entries.add(entries.get(0).deepCopy());
        assertThrows(RuntimeException.class, () -> StateCodec.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8), data.realms()));
        assertThrows(IllegalArgumentException.class, () -> profile(Double.NaN, 0));
    }
}
