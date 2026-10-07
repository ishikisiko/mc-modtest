package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.engine.TextKeys;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** Sect tasks and apprenticeship in the ledger (sect entry, slice 3): offer, accept, progress, turn-in, masters. */
class WorldSimTasksTest {
    private static final int DPY = 6;
    private static final long SEED = 21;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static final String ALICE = "00000000-0000-0000-0000-00000000000a";
    private static final String BOB = "00000000-0000-0000-0000-00000000000b";
    private static final PlayerQualification PLAIN = new PlayerQualification("mortal", 1, true, 2500);

    private static WorldSim world() {
        return SimFixtures.genesis(SEED, "small", DPY);
    }

    private static JsonObject json(WorldSim sim) {
        return JsonParser.parseString(new String(sim.toBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static WorldSim load(JsonObject json, SimData data) {
        return WorldSim.fromBytes(GSON.toJson(json).getBytes(StandardCharsets.UTF_8), SimFixtures.graph(SEED), data);
    }

    private static WorldSim reload(WorldSim sim) {
        return WorldSim.fromBytes(sim.toBytes(), SimFixtures.graph(SEED), SimFixtures.data());
    }

    /** Rewrites one person's saved fields and reloads. */
    private static WorldSim editPerson(WorldSim sim, int personId, Consumer<JsonObject> edit) {
        JsonObject json = json(sim);
        for (JsonElement p : json.getAsJsonArray("persons")) {
            if (p.getAsJsonObject().get("id").getAsInt() == personId) {
                edit.accept(p.getAsJsonObject());
            }
        }
        return load(json, SimFixtures.data());
    }

    private static String reason(Executable action) {
        return assertThrows(IllegalArgumentException.class, action).getMessage();
    }

    private static String player(int i) {
        return String.format("00000000-0000-0000-0000-%012x", 0x100 + i);
    }

    private static SectView home(WorldSim sim) {
        return sim.sects(false).get(0);
    }

    private static void steps(WorldSim sim, int days) {
        SimFixtures.run(sim, days, DPY);
    }

    /** Steps to the next year start (not yet settled), so the next step opens a new year. */
    private static void toNextYear(WorldSim sim) {
        do {
            sim.step(DPY);
        } while (sim.day() % DPY != 0);
    }

    /** Joins fresh players to the sect until one is offered a task of this kind; returns that player's id. */
    private static String memberOffered(WorldSim sim, int sectId, String kind) {
        for (int i = 0; i < 200; i++) {
            String id = player(i);
            if (sim.playerMember(id).isEmpty()) {
                sim.joinSect(id, "P" + i, sectId, PLAIN, true);
            }
            Optional<TaskView> offer = sim.offerTask(id);
            if (offer.isPresent() && offer.get().kind().equals(kind)) {
                return id;
            }
        }
        throw new AssertionError("no player among 200 is offered a " + kind + " task");
    }

    @Test
    void theOfferIsFixedForTheYearAndDiffersBetweenPlayers() {
        WorldSim sim = world();
        int sect = home(sim).id();
        assertEquals(Optional.empty(), sim.offerTask(ALICE), "no offer outside a sect");
        sim.joinSect(ALICE, "Alice", sect, PLAIN, true);
        TaskView first = sim.offerTask(ALICE).orElseThrow();
        assertEquals(first, sim.offerTask(ALICE).orElseThrow(), "computed, never recorded");
        assertEquals(sim.day() / DPY, first.year());
        assertEquals(0, first.progress());
        assertFalse(first.ready());
        assertEquals("", sim.playerMember(ALICE).orElseThrow().taskId(), "offering writes nothing");
        steps(sim, DPY - 1);
        assertEquals(first, sim.offerTask(ALICE).orElseThrow(), "the same offer on another day of the year");

        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            sim.joinSect(player(i), "P" + i, sect, PLAIN, true);
            ids.add(sim.offerTask(player(i)).orElseThrow().id());
        }
        assertTrue(ids.size() > 1, "players are offered different tasks: " + ids);
    }

    @Test
    void aPatrolIsTakenAdvancedAndTurnedIn() {
        WorldSim sim = world();
        SectView sect = home(sim);
        String p = memberOffered(sim, sect.id(), ContentTables.TASK_PATROL);
        ContentTables.SectTask row = SimFixtures.data().sectTask(sim.offerTask(p).orElseThrow().id());
        int before = sim.playerMember(p).orElseThrow().contribution();

        assertFalse(sim.advanceTask(p, ContentTables.TASK_PATROL, 1), "no open task yet");
        SimEvent accept = sim.acceptTask(p, "Pat");
        assertEquals("player_task_accept", accept.type());
        assertEquals(2, accept.importance());
        assertEquals(List.of(sect.id()), accept.sects());
        assertEquals(TextKeys.PLAYER_TASK_ACCEPT + ".1", accept.textKey());
        assertEquals(List.of("Pat", sect.name(), TextKeys.taskName(row.id())), accept.params());
        assertEquals(sim.day(), accept.day());
        assertEquals(1, sim.recentEvents(1, Integer.MAX_VALUE, x -> x.id() == accept.id()).size(),
                "the event is in the chronicle at once");
        assertFalse(sim.step(DPY).contains(accept), "the next settled day does not return it again");

        assertEquals(Optional.empty(), sim.offerTask(p), "no offer while a task is open");
        assertEquals(PlayerReasons.TASK_ACTIVE, reason(() -> sim.acceptTask(p, "Pat")));
        assertEquals(PlayerReasons.NOT_READY, reason(() -> sim.completeTask(p, "Pat")));
        assertFalse(sim.advanceTask(p, ContentTables.TASK_COURIER, 1), "the wrong kind changes nothing");
        assertEquals(0, sim.task(p).orElseThrow().progress());
        assertTrue(sim.advanceTask(p, ContentTables.TASK_PATROL, 1));
        assertEquals(1, sim.task(p).orElseThrow().progress());
        assertFalse(sim.task(p).orElseThrow().ready());
        assertTrue(sim.advanceTask(p, ContentTables.TASK_PATROL, 99));
        TaskView full = sim.task(p).orElseThrow();
        assertEquals(row.count(), full.progress(), "progress is capped at the count");
        assertTrue(full.ready());

        SimEvent done = sim.completeTask(p, "Pat");
        assertEquals("player_task_done", done.type());
        assertEquals(2, done.importance());
        assertEquals(TextKeys.PLAYER_TASK_DONE + ".1", done.textKey());
        assertEquals(List.of("Pat", sect.name(), TextKeys.taskName(row.id())), done.params());
        assertFalse(sim.step(DPY).contains(done));
        PlayerMemberView m = sim.playerMember(p).orElseThrow();
        assertEquals(before + row.contribution(), m.contribution());
        assertEquals("", m.taskId());
        assertEquals(0, m.taskProgress());
        assertEquals(-1, m.taskTargetSectId());
        assertEquals(accept.day() / DPY, m.taskYear(), "the year is kept: one task a year");
        assertEquals(Optional.empty(), sim.task(p));
        assertEquals(PlayerReasons.NO_TASK, reason(() -> sim.completeTask(p, "Pat")));
        assertEquals(Optional.empty(), sim.offerTask(p));
        assertEquals(PlayerReasons.TASK_DONE_THIS_YEAR, reason(() -> sim.acceptTask(p, "Pat")));

        toNextYear(sim);
        assertTrue(sim.offerTask(p).isPresent(), "a new year brings a new task");
        sim.acceptTask(p, "Pat");
        assertEquals(sim.day() / DPY, sim.task(p).orElseThrow().year());
    }

    @Test
    void notMemberIsJudgedFirst() {
        WorldSim sim = world();
        assertEquals(PlayerReasons.NOT_MEMBER, reason(() -> sim.acceptTask(BOB, "Bob")));
        assertEquals(PlayerReasons.NOT_MEMBER, reason(() -> sim.completeTask(BOB, "Bob")));
        assertEquals(PlayerReasons.NOT_MEMBER, reason(() -> sim.apprentice(BOB, "Bob", 0)));
        assertFalse(sim.advanceTask(BOB, ContentTables.TASK_PATROL, 1));
    }

    @Test
    void aCourierGoesToAnotherActiveSect() {
        WorldSim sim = world();
        SectView sect = home(sim);
        String p = memberOffered(sim, sect.id(), ContentTables.TASK_COURIER);
        TaskView offer = sim.offerTask(p).orElseThrow();
        assertNotEquals(sect.id(), offer.targetSectId());
        SectView target = sim.sect(offer.targetSectId()).orElseThrow();
        assertEquals("active", target.state(), "the destination is a living sect");
        assertEquals(target.name(), offer.targetSectName());

        sim.acceptTask(p, "Cou");
        TaskView open = sim.task(p).orElseThrow();
        assertEquals(offer.targetSectId(), open.targetSectId());
        assertEquals(offer.targetSectId(), sim.playerMember(p).orElseThrow().taskTargetSectId());
        assertEquals(PlayerReasons.NOT_READY, reason(() -> sim.completeTask(p, "Cou")));
        assertTrue(sim.advanceTask(p, ContentTables.TASK_COURIER, open.count()));
        int before = sim.playerMember(p).orElseThrow().contribution();
        sim.completeTask(p, "Cou");
        assertEquals(before + open.contribution(), sim.playerMember(p).orElseThrow().contribution());
    }

    /** The open task, its progress and destination are gone; the year of the task stays. */
    private static void assertNoTask(WorldSim sim, String p, long year) {
        PlayerMemberView m = sim.playerMember(p).orElseThrow();
        assertEquals("", m.taskId());
        assertEquals(0, m.taskProgress());
        assertEquals(-1, m.taskTargetSectId());
        assertEquals(year, m.taskYear(), "taskYear is kept: leaving buys no second task this year");
        assertEquals(Optional.empty(), sim.task(p));
    }

    @Test
    void leavingTheSectDropsTheOpenTask() {
        WorldSim sim = world();
        SectView sect = home(sim);
        String p = memberOffered(sim, sect.id(), ContentTables.TASK_PATROL);
        sim.acceptTask(p, "Pat");
        sim.advanceTask(p, ContentTables.TASK_PATROL, 1);
        long year = sim.task(p).orElseThrow().year();

        sim.leaveSect(p, "Pat");
        assertNoTask(sim, p, year);
        assertNoTask(reload(sim), p, year);
        assertFalse(sim.advanceTask(p, ContentTables.TASK_PATROL, 1));

        sim.joinSect(p, "Pat", sect.id(), PLAIN, true);
        assertNoTask(sim, p, year);
        assertEquals(PlayerReasons.NO_TASK, reason(() -> sim.completeTask(p, "Pat")),
                "back in the sect the old task is not turned in");
        assertEquals(Optional.empty(), sim.offerTask(p), "nor a new one this year");
    }

    @Test
    void aForcedMoveToAnotherSectDropsTheOpenTask() {
        WorldSim sim = world();
        SectView sect = home(sim);
        String p = memberOffered(sim, sect.id(), ContentTables.TASK_COURIER);
        sim.acceptTask(p, "Cou");
        TaskView open = sim.task(p).orElseThrow();
        int target = open.targetSectId();
        assertNotEquals(sect.id(), target);

        sim.joinSect(p, "Cou", target, PLAIN, true); // admin move to the courier's own destination
        assertEquals(target, sim.playerMember(p).orElseThrow().sectId());
        assertNoTask(sim, p, open.year());
        assertFalse(sim.advanceTask(p, ContentTables.TASK_COURIER, open.count()));
        assertEquals(PlayerReasons.NO_TASK, reason(() -> sim.completeTask(p, "Cou")));
    }

    @Test
    void aCourierToTheOwnSectIsNeverReady() {
        WorldSim sim = world();
        SectView sect = home(sim);
        String p = memberOffered(sim, sect.id(), ContentTables.TASK_COURIER);
        sim.acceptTask(p, "Cou");
        TaskView open = sim.task(p).orElseThrow();
        // a record from before tasks were dropped on a move: the letter is addressed to the own sect
        JsonObject json = json(sim);
        for (JsonElement m : json.getAsJsonArray("player_members")) {
            if (m.getAsJsonObject().get("player").getAsString().equals(p)) {
                m.getAsJsonObject().addProperty("task_target", sect.id());
            }
        }
        WorldSim stale = load(json, SimFixtures.data());
        assertTrue(stale.advanceTask(p, ContentTables.TASK_COURIER, open.count()));
        assertEquals(PlayerReasons.NOT_READY, reason(() -> stale.completeTask(p, "Cou")));
    }

    @Test
    void aTributeIsTurnedInWithoutProgress() {
        WorldSim sim = world();
        String p = memberOffered(sim, home(sim).id(), ContentTables.TASK_TRIBUTE);
        sim.acceptTask(p, "Tri");
        TaskView open = sim.task(p).orElseThrow();
        assertEquals(0, open.progress());
        assertFalse(open.ready(), "the ledger never calls a tribute ready");
        int before = sim.playerMember(p).orElseThrow().contribution();
        SimEvent done = sim.completeTask(p, "Tri");
        assertEquals("player_task_done", done.type());
        assertEquals(before + open.contribution(), sim.playerMember(p).orElseThrow().contribution());
    }

    @Test
    void noOtherActiveSectMeansNoCourierAndNoRowsMeansNoTask() {
        WorldSim sim = world();
        SectView sect = home(sim);
        JsonObject json = json(sim);
        for (JsonElement s : json.getAsJsonArray("sects")) {
            if (s.getAsJsonObject().get("id").getAsInt() != sect.id()) {
                s.getAsJsonObject().addProperty("state", "destroyed");
            }
        }
        SimData base = SimFixtures.data();
        ContentTables.SectTask courier = base.sectTasks().stream()
                .filter(t -> t.kind().equals(ContentTables.TASK_COURIER)).findFirst().orElseThrow();
        ContentTables.SectTask patrol = base.sectTasks().stream()
                .filter(t -> t.kind().equals(ContentTables.TASK_PATROL)).findFirst().orElseThrow();

        WorldSim mixed = load(json, withTasks(base, List.of(courier, patrol)));
        for (int i = 0; i < 20; i++) {
            mixed.joinSect(player(i), "P" + i, sect.id(), PLAIN, true);
            TaskView offer = mixed.offerTask(player(i)).orElseThrow();
            assertEquals(patrol.id(), offer.id(), "with no other active sect the courier gives way");
            assertEquals(-1, offer.targetSectId());
        }

        WorldSim courierOnly = load(json, withTasks(base, List.of(courier)));
        courierOnly.joinSect(ALICE, "Alice", sect.id(), PLAIN, true);
        assertEquals(Optional.empty(), courierOnly.offerTask(ALICE));
        assertEquals(PlayerReasons.NO_TASK, reason(() -> courierOnly.acceptTask(ALICE, "Alice")));
    }

    private static SimData withTasks(SimData d, List<ContentTables.SectTask> tasks) {
        return new SimData(d.rules(), d.realms(), d.encounters(), d.names(), d.techniques(), d.lore(),
                d.heritages(), tasks);
    }

    @Test
    void theTaskSurvivesTheCodec() {
        WorldSim sim = world();
        String p = memberOffered(sim, home(sim).id(), ContentTables.TASK_COURIER);
        sim.acceptTask(p, "Cou");
        PlayerMemberView m = sim.playerMember(p).orElseThrow();
        WorldSim back = reload(sim);
        PlayerMemberView n = back.playerMember(p).orElseThrow();
        assertEquals(m.taskId(), n.taskId());
        assertEquals(m.taskProgress(), n.taskProgress());
        assertEquals(m.taskTargetSectId(), n.taskTargetSectId());
        assertEquals(m.taskYear(), n.taskYear());
        assertEquals(sim.task(p), back.task(p));

        String q = memberOffered(sim, home(sim).id(), ContentTables.TASK_PATROL);
        sim.acceptTask(q, "Pat");
        sim.advanceTask(q, ContentTables.TASK_PATROL, 2);
        assertEquals(2, reload(sim).task(q).orElseThrow().progress());
        assertTrue(java.util.Arrays.equals(sim.toBytes(), reload(sim).toBytes()), "equal states give equal bytes");
    }

    // ------------------------------------------------------------------ masters

    /** An elder of the sect who is at the sect; makes one of a plain member when there is none. */
    private static WorldSim withElderAtSect(WorldSim sim, int sectId, int[] out) {
        for (PersonView p : sim.membersAt(sectId)) {
            if (p.rank().equals("elder") && p.status().equals("at_sect")) {
                out[0] = p.id();
                return sim;
            }
        }
        PersonView any = sim.membersAt(sectId).stream().filter(p -> !p.rank().equals("sect_master"))
                .findFirst().orElseThrow();
        out[0] = any.id();
        return editPerson(sim, any.id(), o -> {
            o.addProperty("rank", "elder");
            o.addProperty("status", "at_sect");
        });
    }

    @Test
    void apprenticeshipCoversEveryReasonAndTheLossOfTheMaster() {
        WorldSim start = world();
        steps(start, 1); // off the year start: the daily check, not the yearly backstop, is under test
        SectView sect = home(start);
        int[] found = new int[1];
        WorldSim sim = withElderAtSect(start, sect.id(), found);
        int elder = found[0];
        String elderName = sim.nameOf(elder);

        assertEquals(PlayerReasons.NOT_MEMBER, reason(() -> sim.apprentice(ALICE, "Alice", elder)));
        sim.joinSect(ALICE, "Alice", sect.id(), PLAIN, true);
        assertEquals(PlayerReasons.RANK_TOO_LOW, reason(() -> sim.apprentice(ALICE, "Alice", elder)));
        sim.promotePlayer(ALICE, "Alice", "inner");

        assertEquals(PlayerReasons.MASTER_NOT_HERE, reason(() -> sim.apprentice(ALICE, "Alice", 9_999_999)));
        PersonView stranger = sim.sects(false).stream().filter(s -> s.id() != sect.id())
                .flatMap(s -> sim.membersAt(s.id()).stream()).filter(p -> p.rank().equals("elder")
                        || p.rank().equals("sect_master")).findFirst().orElseThrow();
        assertEquals(PlayerReasons.MASTER_NOT_HERE, reason(() -> sim.apprentice(ALICE, "Alice", stranger.id())));
        sim.membersAt(sect.id()).stream().filter(p -> !p.rank().equals("elder") && !p.rank().equals("sect_master"))
                .findFirst().ifPresent(junior -> assertEquals(PlayerReasons.MASTER_NOT_HERE,
                        reason(() -> sim.apprentice(ALICE, "Alice", junior.id())), "only an elder takes disciples"));
        WorldSim away = editPerson(sim, elder, o -> o.addProperty("status", "travelling"));
        assertEquals(PlayerReasons.MASTER_NOT_HERE, reason(() -> away.apprentice(ALICE, "Alice", elder)),
                "an elder away from the sect is not here");

        SimEvent e = sim.apprentice(ALICE, "Alice", elder);
        assertEquals("player_apprentice", e.type());
        assertEquals(2, e.importance());
        assertEquals(List.of(elder), e.actors());
        assertEquals(List.of(sect.id()), e.sects());
        assertEquals(TextKeys.PLAYER_APPRENTICE + ".1", e.textKey());
        assertEquals(List.of("Alice", sect.name(), elderName), e.params());
        assertEquals(1, sim.recentEvents(1, Integer.MAX_VALUE, x -> x.id() == e.id()).size());
        assertEquals(elder, sim.playerMember(ALICE).orElseThrow().masterId());
        assertEquals(PlayerReasons.HAS_MASTER, reason(() -> sim.apprentice(ALICE, "Alice", elder)));
        assertEquals(elder, reload(sim).playerMember(ALICE).orElseThrow().masterId(), "the master survives the codec");
        // The master leaves the sect (a copy of this moment): the next settled day drops them with a line.
        WorldSim left = editPerson(sim, elder, o -> {
            o.addProperty("sect", -1);
            o.addProperty("rank", "rogue");
        });

        List<SimEvent> next = sim.step(DPY);
        assertFalse(next.contains(e), "the next settled day does not return it again");
        if (sim.person(elder).map(p -> p.sectId() == sect.id()).orElse(false)) {
            assertEquals(elder, sim.playerMember(ALICE).orElseThrow().masterId(), "a master in the sect stays");
            assertTrue(next.stream().noneMatch(x -> x.type().equals("player_master_lost")));
        }
        assertNotEquals(0, left.day() % DPY, "not a year start");
        List<SimEvent> today = left.step(DPY);
        assertEquals(-1, left.playerMember(ALICE).orElseThrow().masterId());
        List<SimEvent> lost = today.stream().filter(x -> x.type().equals("player_master_lost")).toList();
        assertEquals(1, lost.size(), "told on the day it is found");
        assertEquals(TextKeys.PLAYER_MASTER_LOST + ".1", lost.get(0).textKey());
        assertEquals(List.of("Alice", elderName), lost.get(0).params());
        assertTrue(left.step(DPY).stream().noneMatch(x -> x.type().equals("player_master_lost")), "told once");
    }

    /** The reasons the facade throws (strings of the contract). */
    private static final class PlayerReasons {
        static final String NOT_MEMBER = "not_member";
        static final String TASK_ACTIVE = "task_active";
        static final String TASK_DONE_THIS_YEAR = "task_done_this_year";
        static final String NO_TASK = "no_task";
        static final String NOT_READY = "not_ready";
        static final String RANK_TOO_LOW = "rank_too_low";
        static final String HAS_MASTER = "has_master";
        static final String MASTER_NOT_HERE = "master_not_here";
    }
}
