package com.example.myvillage.sim.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.sim.PlayerQualification;
import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.cli.SimCli;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.PlayerMember;
import com.example.myvillage.sim.model.Sect;
import com.example.myvillage.sim.model.StateCodec;
import com.example.myvillage.sim.model.WorldState;
import com.example.myvillage.sim.sect.SectTournament.Enrollment;
import com.example.myvillage.sim.sect.SectTournament.Fixture;
import com.example.myvillage.sim.sect.SectTournament.Outcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The yearly review of players and what happens to them when their sect is gone. */
class PlayerAffairsTest {
    private static final int DPY = 6;
    private static final long SEED = 21;
    private static final String ALICE = "00000000-0000-0000-0000-00000000000a";
    private static final PlayerQualification ABLE = new PlayerQualification("qi_refining", 0, true, 5000);

    private static SimData data;
    private static RegionGraph graph;
    private static byte[] genesis;

    @BeforeAll
    static void world() throws IOException {
        Path resources = Path.of("src/main/resources");
        data = WorldSim.loadData(path -> {
            Path file = resources.resolve(path);
            return Files.isRegularFile(file) ? Files.newInputStream(file) : null;
        });
        graph = SimCli.buildGraph(Path.of("."), SEED);
        genesis = WorldSim.genesis(SEED, graph, data, "small", DPY).toBytes();
    }

    /** A fresh working set over the genesis world (its next step starts a year). */
    private static SimContext ctx() {
        WorldState state = StateCodec.fromBytes(genesis, data.realms());
        SimContext ctx = new SimContext(state, data, graph, DPY);
        assertTrue(ctx.newYear(), "the world after the prehistory stands at a year start");
        return ctx;
    }

    /** The strongest active sect (most members), with an ordinary prestige so admission is plain. */
    private static Sect home(SimContext ctx) {
        Sect sect = ctx.activeSects().stream()
                .max(Comparator.comparingInt((Sect s) -> ctx.members(s.id).size()).thenComparingInt(s -> -s.id))
                .orElseThrow();
        sect.prestige = 10;
        sect.tierId = "branch";
        return sect;
    }

    /** Steps up to and including the next year start; returns that day's events. */
    private static List<SimEvent> nextYearStart(SimContext ctx) {
        Engine.step(ctx);
        while (!ctx.newYear()) {
            Engine.step(ctx);
        }
        return Engine.step(ctx);
    }

    private static List<SimEvent> ofType(List<SimEvent> events, String type) {
        return events.stream().filter(e -> e.type().equals(type)).toList();
    }

    @Test
    void tournamentWinsPromoteOnceAndSurviveSave() {
        SimContext ctx = ctx();
        Sect sect = home(ctx);
        var bar = data.rules().player().promotion().inner();
        var qualified = new PlayerQualification(bar.stage().realm(), bar.stage().stage(), true, 5000);
        PlayerAffairs.join(ctx, ALICE, "Alice", sect.id, qualified);
        SectTournaments.advanceMonth(ctx, 1);
        prepareTournamentPeers(ctx, sect, bar, 3);
        PlayerAffairs.yearly(ctx);
        assertEquals("outer", ctx.state.playerMembers.get(ALICE).rank);
        assertEquals(Enrollment.ENTERED, SectTournaments.enroll(ctx, sect.id, ALICE));
        assertEquals(Enrollment.ALREADY_ENTERED, SectTournaments.enroll(ctx, sect.id, ALICE));
        List<Fixture> matches = SectTournaments.fixtures(ctx, sect.id).stream().filter(Fixture::hasPlayer).toList();
        assertEquals(3, matches.size());
        assertEquals(3, matches.stream().map(m -> m.second().person()).distinct().count());
        assertFalse(SectTournaments.simulate(ctx, matches.getFirst()));
        assertTrue(SectTournaments.record(ctx, matches.getFirst().ticket(), Outcome.FIRST));
        byte[] saved = StateCodec.toBytes(ctx.state, ctx.realms);
        var restored = new SimContext(StateCodec.fromBytes(saved, ctx.realms), data, graph, DPY);
        assertArrayEquals(saved, StateCodec.toBytes(restored.state, ctx.realms));
        assertFalse(SectTournaments.record(restored, matches.getFirst().ticket(), Outcome.FIRST));
        assertTrue(SectTournaments.record(restored, matches.get(1).ticket(), Outcome.FIRST));
        assertEquals("outer", restored.state.playerMembers.get(ALICE).rank);
        assertTrue(SectTournaments.record(restored, matches.get(2).ticket(), Outcome.SECOND));
        var standing = SectTournaments.standing(restored, sect.id, ALICE).orElseThrow();
        assertTrue(standing.promoted());
        assertEquals(2, standing.wins());
        assertEquals("inner", restored.state.playerMembers.get(ALICE).rank);
        var promoted = ofType(restored.state.chronicle, "player_promotion");
        assertEquals(1, promoted.size());
        assertEquals(List.of("Alice", sect.name), promoted.getFirst().params());
        assertTrue(SectTournaments.advanceMonth(restored, 2));
        assertFalse(SectTournaments.record(restored, matches.get(2).ticket(), Outcome.FIRST));
        assertEquals(Enrollment.NOT_OUTER, SectTournaments.eligibility(restored, sect.id, ALICE));
    }

    @Test
    void contributionBarHoldsAtRegistration() {
        var ctx = ctx();
        var sect = home(ctx);
        PlayerAffairs.join(ctx, ALICE, "Alice", sect.id, ABLE);
        var m = ctx.state.playerMembers.get(ALICE);
        var inner = data.rules().player().promotion().inner();
        m.realmId = inner.stage().realm();
        m.stageIndex = inner.stage().stage();
        m.contribution = inner.contribution() - 1;
        SectTournaments.advanceMonth(ctx, 0);
        assertEquals(Enrollment.BELOW_THRESHOLD, SectTournaments.eligibility(ctx, sect.id, ALICE));
        m.contribution = inner.contribution();
        assertEquals(Enrollment.ENTERED, SectTournaments.eligibility(ctx, sect.id, ALICE));
        assertEquals("outer", m.rank);
    }

    @Test
    void npcFixturesUseExistingPeopleAndHaveNoLethalConsequences() {
        var ctx = ctx();
        var sect = home(ctx);
        prepareTournamentPeers(ctx, sect, data.rules().player().promotion().inner(), 4);
        SectTournaments.advanceMonth(ctx, 1);
        assertTrue(SectTournaments.arrangeNpc(ctx, sect.id));
        assertFalse(SectTournaments.arrangeNpc(ctx, sect.id));
        var matches = SectTournaments.fixtures(ctx, sect.id);
        assertEquals(6, matches.size());
        int dead = ctx.state.tombstones.size();
        int population = ctx.state.persons.size();
        for (Fixture match : matches) assertTrue(SectTournaments.simulate(ctx, match));
        assertFalse(SectTournaments.simulate(ctx, matches.getFirst()));
        assertEquals(dead, ctx.state.tombstones.size());
        assertEquals(population, ctx.state.persons.size());
        assertTrue(SectTournaments.fixtures(ctx, sect.id).isEmpty());
    }

    private static void prepareTournamentPeers(SimContext ctx, Sect sect, Rules.PlayerThreshold bar, int count) {
        var people = ctx.members(sect.id).stream().filter(p -> !p.rank.equals("sect_master")).limit(count).toList();
        assertEquals(count, people.size());
        for (Person p : people) {
            p.realm = ctx.realms.indexOf(bar.stage().realm());
            p.stage = bar.stage().stage();
            p.rank = "outer";
            p.status = "at_sect";
            p.regionId = sect.homeRegionId;
            NpcCultivation.reached(ctx, p);
        }
    }

    @Test
    void negativeStandingsRecoverTowardZeroEachYear() {
        SimContext ctx = ctx();
        PlayerMember m = new PlayerMember();
        m.playerId = ALICE;
        m.playerName = "Alice";
        m.standings.put(1, -25);
        m.standings.put(2, 30);
        m.standings.put(3, -100);
        ctx.state.playerMembers.put(ALICE, m);
        int r = data.rules().player().leave().standingRecoveryPerYear();
        Engine.step(ctx);
        assertEquals(Math.min(0, -25 + r), m.standings.get(1));
        assertEquals(30, m.standings.get(2), "a positive standing does not decay");
        assertEquals(-100 + r, m.standings.get(3));
        for (int i = 0; i < 20; i++) {
            nextYearStart(ctx);
        }
        assertEquals(0, m.standings.get(1));
        assertEquals(0, m.standings.get(3));
        assertEquals(30, m.standings.get(2));
    }

    @Test
    void aMasterWhoLeftOrDiedIsDropped() {
        SimContext ctx = ctx();
        Sect sect = home(ctx);
        PlayerAffairs.join(ctx, ALICE, "Alice", sect.id, ABLE);
        PlayerMember m = ctx.state.playerMembers.get(ALICE);
        Engine.step(ctx); // past the year start, so the daily check (not the yearly backstop) decides
        Person stranger = ctx.state.persons.values().stream().filter(p -> p.sectId != sect.id).findFirst()
                .orElseThrow();
        m.masterId = stranger.id;
        List<SimEvent> lost = ofType(Engine.step(ctx), "player_master_lost");
        assertEquals(-1, m.masterId, "a master of another sect is no master");
        assertEquals(1, lost.size(), "losing the master is told the same day");
        assertEquals(List.of("Alice", stranger.name()), lost.get(0).params());
        assertEquals(TextKeys.PLAYER_MASTER_LOST + ".1", lost.get(0).textKey());
        assertEquals(List.of(stranger.id), lost.get(0).actors());

        m.masterId = 1_000_000;
        lost = ofType(Engine.step(ctx), "player_master_lost");
        assertEquals(-1, m.masterId, "a master nobody knows (or dead) is no master");
        assertEquals(1, lost.size());
        assertTrue(lost.get(0).actors().isEmpty(), "an unknown id is not named as an actor");

        Person fellow = ctx.members(sect.id).stream().filter(ctx::alive).findFirst().orElseThrow();
        m.masterId = fellow.id;
        lost = ofType(Engine.step(ctx), "player_master_lost");
        if (ctx.alive(fellow) && fellow.sectId == sect.id) {
            assertEquals(fellow.id, m.masterId, "a living master of the same sect stays");
            assertTrue(lost.isEmpty());
        }
        nextYearStart(ctx);
        if (ctx.alive(fellow) && fellow.sectId == sect.id) {
            assertEquals(fellow.id, m.masterId, "the yearly backstop keeps a living master of the same sect");
        }
    }

    @Test
    void theDailyCheckTellsOfADeadMasterOnTheDayOfDeath() {
        SimContext ctx = ctx();
        Sect sect = home(ctx);
        PlayerAffairs.join(ctx, ALICE, "Alice", sect.id, ABLE);
        PlayerMember m = ctx.state.playerMembers.get(ALICE);
        Engine.step(ctx);
        Person master = ctx.members(sect.id).stream()
                .filter(p -> ctx.alive(p) && !p.rank.equals("sect_master")).findFirst().orElseThrow();
        m.masterId = master.id;
        String name = master.name();
        Deaths.bury(ctx, master, "old_age", -1, -1);
        List<SimEvent> today = Engine.step(ctx);
        assertEquals(-1, m.masterId);
        List<SimEvent> lost = ofType(today, "player_master_lost");
        assertEquals(1, lost.size());
        assertEquals(List.of("Alice", name), lost.get(0).params(), "a dead master is named from the tombstone");
        assertEquals(List.of(sect.id), lost.get(0).sects());
        assertEquals(2, lost.get(0).importance());
        assertTrue(ofType(Engine.step(ctx), "player_master_lost").isEmpty(), "told once");
    }

    @Test
    void aDestroyedSectFoundAtTheReviewReleasesItsPlayers() {
        SimContext ctx = ctx();
        Sect sect = home(ctx);
        PlayerAffairs.join(ctx, ALICE, "Alice", sect.id, ABLE);
        PlayerMember m = ctx.state.playerMembers.get(ALICE);
        sect.state = Sect.DESTROYED;
        List<SimEvent> events = Engine.step(ctx);
        assertReleased(ctx, m, sect, events);
    }

    @Test
    void dissolvingASectReleasesItsPlayersWithoutPenalty() {
        SimContext ctx = ctx();
        Sect sect = home(ctx);
        Sect other = ctx.activeSects().stream()
                .filter(s -> s.id != sect.id && s.tierId.equals("branch")).findFirst().orElseThrow();
        other.prestige = 10;
        PlayerAffairs.join(ctx, ALICE, "Alice", sect.id, ABLE);
        String bob = "00000000-0000-0000-0000-00000000000b";
        PlayerAffairs.join(ctx, bob, "Bob", other.id, ABLE);
        PlayerMember m = ctx.state.playerMembers.get(ALICE);
        long before = ctx.state.nextEventId;
        SectPolitics.dissolve(ctx, sect, false, -1);
        List<SimEvent> events = ctx.state.chronicle.stream().filter(e -> e.id() >= before).toList();
        assertReleased(ctx, m, sect, events);
        assertEquals(other.id, ctx.state.playerMembers.get(bob).sectId, "a player of another sect stays");
        assertTrue(PlayerAffairs.admission(ctx, bob, sect.id, ABLE).reason().equals("sect_inactive"));
    }

    private static void assertReleased(SimContext ctx, PlayerMember m, Sect sect, List<SimEvent> events) {
        assertFalse(m.inSect());
        assertEquals("outer", m.rank);
        assertEquals(-1, m.masterId);
        assertEquals(sect.id, m.leftSectId);
        assertEquals(20, m.standings.get(sect.id), "not their fault: no standing penalty");
        List<SimEvent> gone = events.stream()
                .filter(e -> e.textKey().startsWith(TextKeys.PLAYER_LEAVE_SECT_GONE + ".")).toList();
        assertEquals(1, gone.size(), events.toString());
        SimEvent e = gone.get(0);
        assertEquals("player_leave", e.type());
        assertEquals(2, e.importance());
        assertEquals(List.of(sect.id), e.sects());
        assertEquals(List.of("Alice", sect.name), e.params());
        assertTrue(ctx.state.chronicle.contains(e));
    }

    @Test
    void reachedRanksMortalBelowEveryLedgerRealm() {
        var realms = data.realms();
        Rules.PlayerRealmStage qi5 = new Rules.PlayerRealmStage("qi_refining", 4);
        assertFalse(PlayerAffairs.reached(realms, "mortal", 99, qi5));
        assertFalse(PlayerAffairs.reached(realms, "qi_refining", 3, qi5));
        assertTrue(PlayerAffairs.reached(realms, "qi_refining", 4, qi5));
        assertTrue(PlayerAffairs.reached(realms, "foundation_establishment", 0, qi5));
        assertFalse(PlayerAffairs.reached(realms, "unknown_realm", 99, qi5));
        Rules.PlayerRealmStage mortal1 = new Rules.PlayerRealmStage("mortal", 1);
        assertFalse(PlayerAffairs.reached(realms, "mortal", 0, mortal1));
        assertTrue(PlayerAffairs.reached(realms, "mortal", 1, mortal1));
        assertTrue(PlayerAffairs.reached(realms, "qi_refining", 0, mortal1));
    }
}
