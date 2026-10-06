package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.engine.TextKeys;
import com.example.myvillage.sim.model.StateCodec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** The player-membership facade: admission, join, leave, admin promotion, the snapshot and the codec. */
class WorldSimPlayerMembersTest {
    private static final int DPY = 6;
    private static final long SEED = 21;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static final String ALICE = "00000000-0000-0000-0000-00000000000a";
    private static final String BOB = "00000000-0000-0000-0000-00000000000b";
    /** Awakened, qi sensed (mortal stage 1), a modest root: enough for an ordinary sect only. */
    private static final PlayerQualification PLAIN = new PlayerQualification("mortal", 1, true, 2500);

    private static WorldSim world() {
        return SimFixtures.genesis(SEED, "small", DPY);
    }

    private static WorldSim reload(WorldSim sim) {
        return WorldSim.fromBytes(sim.toBytes(), SimFixtures.graph(SEED), SimFixtures.data());
    }

    private static JsonObject json(WorldSim sim) {
        return JsonParser.parseString(new String(sim.toBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static WorldSim load(JsonObject json) {
        return WorldSim.fromBytes(GSON.toJson(json).getBytes(StandardCharsets.UTF_8), SimFixtures.graph(SEED),
                SimFixtures.data());
    }

    /** Rewrites one sect's saved fields and reloads. */
    private static WorldSim editSect(WorldSim sim, int sectId, Consumer<JsonObject> edit) {
        JsonObject json = json(sim);
        for (JsonElement s : json.getAsJsonArray("sects")) {
            if (s.getAsJsonObject().get("id").getAsInt() == sectId) {
                edit.accept(s.getAsJsonObject());
            }
        }
        return load(json);
    }

    /** Rewrites one player's saved record and reloads. */
    private static WorldSim editPlayer(WorldSim sim, String playerId, Consumer<JsonObject> edit) {
        JsonObject json = json(sim);
        for (JsonElement m : json.getAsJsonArray("player_members")) {
            if (m.getAsJsonObject().get("player").getAsString().equals(playerId)) {
                edit.accept(m.getAsJsonObject());
            }
        }
        return load(json);
    }

    /** Two active sects, both set to an ordinary prestige so only the rule under test decides. */
    private static WorldSim ordinary(WorldSim sim, int a, int b) {
        sim = editSect(sim, a, s -> s.addProperty("prestige", 10.0));
        return editSect(sim, b, s -> s.addProperty("prestige", 10.0));
    }

    private static int[] twoActiveSects(WorldSim sim) {
        List<SectView> active = sim.sects(false);
        assertTrue(active.size() >= 2, "the fixture world has at least two active sects");
        return new int[] {active.get(0).id(), active.get(1).id()};
    }

    private static String reason(WorldSim sim, String player, int sectId, PlayerQualification q) {
        Admission a = sim.admission(player, sectId, q);
        assertEquals(a.ok(), a.reason().equals(Admission.OK));
        return a.reason();
    }

    @Test
    void admissionCoversEveryReason() {
        WorldSim sim = world();
        int[] ids = twoActiveSects(sim);
        int a = ids[0];
        int b = ids[1];
        sim = ordinary(sim, a, b);

        assertEquals(Admission.OK, reason(sim, ALICE, a, PLAIN));
        assertEquals(Admission.SECT_INACTIVE, reason(sim, ALICE, 9999, PLAIN));
        WorldSim razed = editSect(sim, a, s -> s.addProperty("state", "destroyed"));
        assertEquals(Admission.SECT_INACTIVE, reason(razed, ALICE, a, PLAIN));
        assertEquals(Admission.NOT_AWAKENED, reason(sim, ALICE, a, new PlayerQualification("mortal", 1, false, 9000)));
        assertEquals(Admission.REALM_TOO_LOW, reason(sim, ALICE, a, new PlayerQualification("mortal", 0, true, 9000)));
        // An unknown realm id ranks as mortal; a ledger realm ranks above every mortal stage.
        assertEquals(Admission.REALM_TOO_LOW, reason(sim, ALICE, a, new PlayerQualification("nowhere", 0, true, 0)));
        assertEquals(Admission.OK, reason(sim, ALICE, a, new PlayerQualification("qi_refining", 0, true, 0)));

        WorldSim famous = editSect(sim, a, s -> s.addProperty("prestige", 60.0));
        assertEquals(Admission.SELECTIVE, reason(famous, ALICE, a, PLAIN));
        assertEquals(Admission.OK, reason(famous, ALICE, a, new PlayerQualification("mortal", 1, true, 3000)));
        assertEquals(Admission.OK, reason(famous, ALICE, a, new PlayerQualification("qi_refining", 0, true, 100)));
        assertEquals(Admission.OK, reason(famous, ALICE, b, PLAIN), "only the famous sect is selective");
        // Order: awakening and realm are judged before the selective bar.
        assertEquals(Admission.NOT_AWAKENED, reason(famous, ALICE, a, new PlayerQualification("mortal", 1, false, 0)));

        sim.joinSect(ALICE, "Alice", a, PLAIN);
        assertEquals(Admission.ALREADY_MEMBER, reason(sim, ALICE, a, PLAIN));
        assertEquals(Admission.MEMBER_ELSEWHERE, reason(sim, ALICE, b, PLAIN));
        assertEquals(Admission.OK, reason(sim, BOB, a, PLAIN), "another player is judged on their own record");

        sim.leaveSect(ALICE, "Alice");
        assertEquals(Admission.REJOIN_COOLDOWN, reason(sim, ALICE, a, PLAIN));
        assertEquals(Admission.OK, reason(sim, ALICE, b, PLAIN), "the cooldown is for the sect left only");
        // Past the cooldown, the standing (20 - 40 = -20) still bars a return.
        WorldSim later = editPlayer(sim, ALICE, m -> m.addProperty("left_day", -1000));
        assertEquals(Admission.STANDING_TOO_LOW, reason(later, ALICE, a, PLAIN));
        assertEquals(Admission.SECT_INACTIVE,
                reason(editSect(later, a, s -> s.addProperty("state", "destroyed")), ALICE, a, PLAIN));
    }

    @Test
    void joinWritesTheRecordAndAChronicleLine() {
        WorldSim sim = world();
        int a = twoActiveSects(sim)[0];
        sim = editSect(sim, a, s -> s.addProperty("prestige", 10.0));
        SectView sect = sim.sect(a).orElseThrow();
        PlayerQualification q = new PlayerQualification("qi_refining", 2, true, 4100);

        SimEvent e = sim.joinSect(ALICE, "Alice", a, q);

        PlayerMemberView v = sim.playerMember(ALICE).orElseThrow();
        assertEquals(ALICE, v.playerId());
        assertEquals("Alice", v.playerName());
        assertEquals(a, v.sectId());
        assertEquals(sect.name(), v.sectName());
        assertEquals("outer", v.rank());
        assertEquals(sim.day(), v.joinedDay());
        assertEquals(-1, v.masterId());
        assertEquals("", v.masterName());
        assertEquals(Map.of(a, 20), v.standings());
        assertEquals(-1, v.leftSectId());
        assertEquals("qi_refining", v.realmId());
        assertEquals(2, v.stageIndex());
        assertTrue(v.awakened());
        assertEquals(4100, v.rootPeakBp());
        assertTrue(v.inSect());
        assertEquals(List.of(v), sim.playerMembers());

        assertEquals("player_join", e.type());
        assertEquals(2, e.importance());
        assertEquals(List.of(a), e.sects());
        assertTrue(e.actors().isEmpty());
        assertEquals(sect.regionId(), e.regionId());
        assertEquals(sim.day(), e.day());
        assertTrue(e.textKey().startsWith(TextKeys.PLAYER_JOIN + "."), e.textKey());
        assertEquals(List.of("Alice", sect.name()), e.params());

        List<SimEvent> recent = sim.recentEvents(2, 5);
        assertEquals(e, recent.get(recent.size() - 1), "the event is in the chronicle at once");
        assertEquals(Optional.of(e), sim.event(e.id()));

        // The next settled day neither returns it again nor loses it.
        List<SimEvent> day = sim.step(DPY);
        assertFalse(day.contains(e));
        assertEquals(Optional.of(e), sim.event(e.id()));
        assertEquals(1, sim.recentEvents(1, Integer.MAX_VALUE, x -> x.id() == e.id()).size());
    }

    @Test
    void playersAreNotCountedAsPeople() {
        WorldSim a = world();
        int sectId = twoActiveSects(a)[0];
        a = editSect(a, sectId, s -> s.addProperty("prestige", 10.0));
        WorldSim b = reload(a);
        b.joinSect(ALICE, "Alice", sectId, PLAIN);
        assertEquals(a.sect(sectId).orElseThrow().memberCount(), b.sect(sectId).orElseThrow().memberCount());
        assertEquals(a.membersAt(sectId), b.membersAt(sectId));
        assertEquals(a.overview(DPY).population(), b.overview(DPY).population());
        assertEquals(a.stewardOf(sectId), b.stewardOf(sectId));
    }

    @Test
    void stewardIsTheLowestRankThenLowestIdAtTheSect() {
        WorldSim sim = world();
        List<String> order = List.of("outer", "inner", "elder", "sect_master");
        int checked = 0;
        for (SectView sect : sim.sects(false)) {
            List<PersonView> at = sim.membersAt(sect.id());
            Optional<PersonView> steward = sim.stewardOf(sect.id());
            assertEquals(at.isEmpty(), steward.isEmpty());
            if (at.isEmpty()) {
                continue;
            }
            PersonView expected = at.stream()
                    .min(java.util.Comparator.comparingInt((PersonView p) -> order.contains(p.rank())
                            ? order.indexOf(p.rank()) : order.size()).thenComparingInt(PersonView::id))
                    .orElseThrow();
            assertEquals(expected, steward.orElseThrow());
            checked++;
        }
        assertTrue(checked > 0);
        assertTrue(sim.stewardOf(9999).isEmpty());
    }

    @Test
    void refusedJoinThrowsTheReasonAndChangesNothing() {
        WorldSim sim = world();
        int a = twoActiveSects(sim)[0];
        byte[] before = sim.toBytes();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> sim.joinSect(ALICE, "Alice", a, new PlayerQualification("mortal", 1, false, 0)));
        assertEquals(Admission.NOT_AWAKENED, e.getMessage());
        assertArrayEquals(before, sim.toBytes());
        assertTrue(sim.playerMember(ALICE).isEmpty());
    }

    @Test
    void leaveKeepsTheRecordAndCostsStanding() {
        WorldSim sim = world();
        int[] ids = twoActiveSects(sim);
        sim = ordinary(sim, ids[0], ids[1]);
        int a = ids[0];
        SectView sect = sim.sect(a).orElseThrow();
        sim.joinSect(ALICE, "Alice", a, PLAIN);
        sim.step(DPY);
        SimEvent e = sim.leaveSect(ALICE, "Alice");

        PlayerMemberView v = sim.playerMember(ALICE).orElseThrow();
        assertFalse(v.inSect());
        assertEquals(-1, v.sectId());
        assertEquals("", v.sectName());
        assertEquals("outer", v.rank());
        assertEquals(-1, v.masterId());
        assertEquals(a, v.leftSectId());
        assertEquals(sim.day(), v.leftDay());
        assertEquals(Map.of(a, -20), v.standings());

        assertEquals("player_leave", e.type());
        assertEquals(2, e.importance());
        assertEquals(List.of(a), e.sects());
        assertEquals(sect.regionId(), e.regionId());
        assertTrue(e.textKey().startsWith(TextKeys.PLAYER_LEAVE + "."), e.textKey());
        assertFalse(e.textKey().startsWith(TextKeys.PLAYER_LEAVE_SECT_GONE), e.textKey());
        assertEquals(List.of("Alice", sect.name()), e.params());
        List<SimEvent> recent = sim.recentEvents(2, 1);
        assertEquals(List.of(e), recent);

        WorldSim fsim = sim;
        IllegalArgumentException twice = assertThrows(IllegalArgumentException.class,
                () -> fsim.leaveSect(ALICE, "Alice"));
        assertEquals("not_member", twice.getMessage());
        assertEquals("not_member",
                assertThrows(IllegalArgumentException.class, () -> fsim.leaveSect(BOB, "Bob")).getMessage());

        // Joining another sect reuses the record: the old standing stays, the left sect is remembered.
        sim.joinSect(ALICE, "Alice", ids[1], PLAIN);
        PlayerMemberView again = sim.playerMember(ALICE).orElseThrow();
        assertEquals(Map.of(a, -20, ids[1], 20), again.standings());
        assertEquals(a, again.leftSectId());
    }

    @Test
    void rejoinWaitsOutTheCooldownAndTheStanding() {
        WorldSim sim = world();
        int[] ids = twoActiveSects(sim);
        sim = ordinary(sim, ids[0], ids[1]);
        int a = ids[0];
        // A root strong enough for any sect, since prestige moves over the years waited.
        PlayerQualification strong = new PlayerQualification("mortal", 1, true, 9000);
        sim.joinSect(ALICE, "Alice", a, strong);
        sim.leaveSect(ALICE, "Alice");
        long left = sim.day();
        int rejoinYears = SimFixtures.data().rules().player().leave().rejoinYears();
        while (sim.day() - left < (long) rejoinYears * DPY) {
            assertEquals(Admission.REJOIN_COOLDOWN, reason(sim, ALICE, a, strong), "day " + sim.day());
            sim.step(DPY);
        }
        // -20 recovers 10 a year, so after three year starts the standing is back to 0.
        assertEquals(0, sim.playerMember(ALICE).orElseThrow().standings().get(a));
        if (sim.sect(a).orElseThrow().state().equals("active")) {
            assertEquals(Admission.OK, reason(sim, ALICE, a, strong));
            sim.joinSect(ALICE, "Alice", a, strong);
            assertEquals(20, sim.playerMember(ALICE).orElseThrow().standings().get(a));
        }
    }

    @Test
    void forcedJoinSkipsTheCooldownButNotALivingSect() {
        WorldSim sim = world();
        int[] ids = twoActiveSects(sim);
        sim = ordinary(sim, ids[0], ids[1]);
        int a = ids[0];
        sim.joinSect(ALICE, "Alice", a, PLAIN);
        sim.leaveSect(ALICE, "Alice");
        assertEquals(Admission.REJOIN_COOLDOWN, reason(sim, ALICE, a, PLAIN));
        PlayerQualification unawakened = new PlayerQualification("mortal", 0, false, 0);
        WorldSim fsim = sim;
        assertEquals(Admission.REJOIN_COOLDOWN, assertThrows(IllegalArgumentException.class,
                () -> fsim.joinSect(ALICE, "Alice", a, unawakened, false)).getMessage());

        SimEvent e = sim.joinSect(ALICE, "Alice", a, unawakened, true);
        assertEquals("player_join", e.type());
        assertEquals(List.of(a), e.sects());
        assertEquals(e, sim.recentEvents(2, 1).get(0));
        PlayerMemberView v = sim.playerMember(ALICE).orElseThrow();
        assertEquals(a, v.sectId());
        assertEquals("outer", v.rank());
        assertFalse(v.awakened(), "the snapshot is written as given");
        assertEquals(0, v.standings().get(a), "-20 plus the join standing");

        assertEquals(Admission.ALREADY_MEMBER, assertThrows(IllegalArgumentException.class,
                () -> fsim.joinSect(ALICE, "Alice", a, PLAIN, true)).getMessage());
        assertEquals(Admission.SECT_INACTIVE, assertThrows(IllegalArgumentException.class,
                () -> fsim.joinSect(BOB, "Bob", 9999, PLAIN, true)).getMessage());
        WorldSim razed = editSect(sim, ids[1], s -> s.addProperty("state", "destroyed"));
        assertEquals(Admission.SECT_INACTIVE, assertThrows(IllegalArgumentException.class,
                () -> razed.joinSect(ALICE, "Alice", ids[1], PLAIN, true)).getMessage());
        assertEquals(a, razed.playerMember(ALICE).orElseThrow().sectId(), "a refused force changes nothing");
    }

    @Test
    void forcedJoinFromAnotherSectLeavesItWithoutPenalty() {
        WorldSim sim = world();
        int[] ids = twoActiveSects(sim);
        sim = ordinary(sim, ids[0], ids[1]);
        sim.joinSect(ALICE, "Alice", ids[0], PLAIN);
        sim.promotePlayer(ALICE, "Alice", "inner");
        long before = sim.recentEvents(1, Integer.MAX_VALUE).size();

        SimEvent e = sim.joinSect(ALICE, "Alice", ids[1], PLAIN, true);

        PlayerMemberView v = sim.playerMember(ALICE).orElseThrow();
        assertEquals(ids[1], v.sectId());
        assertEquals("outer", v.rank());
        assertEquals(ids[0], v.leftSectId());
        assertEquals(sim.day(), v.leftDay());
        assertEquals(Map.of(ids[0], 20, ids[1], 20), v.standings(), "no penalty for the sect left");
        List<SimEvent> added = sim.recentEvents(1, Integer.MAX_VALUE).subList((int) before,
                sim.recentEvents(1, Integer.MAX_VALUE).size());
        assertEquals(List.of(e), added, "only the join is recorded, no player_leave");
        assertEquals("player_join", e.type());
        assertFalse(sim.step(DPY).contains(e));
    }

    @Test
    void adminPromotion() {
        WorldSim sim = world();
        int a = twoActiveSects(sim)[0];
        sim = editSect(sim, a, s -> s.addProperty("prestige", 10.0));
        WorldSim fsim = sim;
        assertEquals("not_member", assertThrows(IllegalArgumentException.class,
                () -> fsim.promotePlayer(ALICE, "Alice", "inner")).getMessage());
        sim.joinSect(ALICE, "Alice", a, PLAIN);
        assertThrows(IllegalArgumentException.class, () -> fsim.promotePlayer(ALICE, "Alice", "sect_master"));
        assertThrows(IllegalArgumentException.class, () -> fsim.promotePlayer(ALICE, "Alice", "rogue"));
        assertTrue(sim.promotePlayer(ALICE, "Alice", "outer").isEmpty());

        SimEvent inner = sim.promotePlayer(ALICE, "Alice", "inner").orElseThrow();
        assertEquals("inner", sim.playerMember(ALICE).orElseThrow().rank());
        assertEquals("player_promotion", inner.type());
        assertEquals(2, inner.importance());
        assertEquals(List.of(a), inner.sects());
        assertEquals(TextKeys.PLAYER_PROMOTE_INNER + ".1", inner.textKey());
        assertEquals(List.of("Alice", sim.sect(a).orElseThrow().name()), inner.params());

        SimEvent elder = sim.promotePlayer(ALICE, "Alice", "elder").orElseThrow();
        assertEquals(TextKeys.PLAYER_PROMOTE_ELDER + ".1", elder.textKey());
        assertEquals("elder", sim.playerMember(ALICE).orElseThrow().rank());
        assertFalse(sim.step(DPY).contains(elder));

        long events = sim.recentEvents(1, Integer.MAX_VALUE).size();
        assertTrue(sim.promotePlayer(ALICE, "Alice", "outer").isEmpty(), "a demotion records nothing");
        assertEquals("outer", sim.playerMember(ALICE).orElseThrow().rank());
        assertEquals(events, sim.recentEvents(1, Integer.MAX_VALUE).size());
    }

    @Test
    void qualificationSnapshotRefreshesOnlyAnExistingRecord() {
        WorldSim sim = world();
        int a = twoActiveSects(sim)[0];
        sim = editSect(sim, a, s -> s.addProperty("prestige", 10.0));
        sim.updatePlayerQualification(ALICE, "Alice", PLAIN);
        assertTrue(sim.playerMember(ALICE).isEmpty());
        sim.joinSect(ALICE, "Alice", a, PLAIN);
        sim.updatePlayerQualification(ALICE, "Alicia", new PlayerQualification("foundation_establishment", 1, true,
                6000));
        PlayerMemberView v = sim.playerMember(ALICE).orElseThrow();
        assertEquals("Alicia", v.playerName());
        assertEquals("foundation_establishment", v.realmId());
        assertEquals(1, v.stageIndex());
        assertEquals(6000, v.rootPeakBp());
        assertEquals(a, v.sectId());
    }

    @Test
    void membersRoundTripThroughTheCodec() {
        WorldSim sim = world();
        int[] ids = twoActiveSects(sim);
        sim = ordinary(sim, ids[0], ids[1]);
        sim.joinSect(BOB, "Bob", ids[1], new PlayerQualification("qi_refining", 4, true, 3300));
        sim.joinSect(ALICE, "Alice", ids[0], PLAIN);
        sim.leaveSect(ALICE, "Alice");
        sim.joinSect(ALICE, "Alice", ids[1], PLAIN);
        sim.promotePlayer(BOB, "Bob", "inner");

        JsonObject json = json(sim);
        assertEquals(3, json.get("version").getAsInt());
        assertEquals(StateCodec.VERSION, json.get("version").getAsInt());
        var players = json.getAsJsonArray("player_members");
        assertEquals(2, players.size());
        assertEquals(ALICE, players.get(0).getAsJsonObject().get("player").getAsString(), "player-id order");
        assertEquals(BOB, players.get(1).getAsJsonObject().get("player").getAsString());

        WorldSim restored = reload(sim);
        assertArrayEquals(sim.toBytes(), restored.toBytes());
        assertEquals(sim.playerMembers(), restored.playerMembers());
        assertEquals(sim.recentEvents(2, 10), restored.recentEvents(2, 10));

        // The restored world goes on exactly like the original, players included.
        SimFixtures.run(sim, 4L * DPY, DPY);
        SimFixtures.run(restored, 4L * DPY, DPY);
        assertArrayEquals(sim.toBytes(), restored.toBytes());
    }

    @Test
    void aVersionTwoPayloadReadsWithNoPlayers() {
        WorldSim sim = world();
        int a = twoActiveSects(sim)[0];
        sim = editSect(sim, a, s -> s.addProperty("prestige", 10.0));
        sim.joinSect(ALICE, "Alice", a, PLAIN);
        JsonObject json = json(sim);
        json.addProperty("version", 2);
        json.remove("player_members");
        WorldSim old = load(json);
        assertTrue(old.playerMembers().isEmpty());
        assertTrue(old.playerMember(ALICE).isEmpty());
        JsonObject resaved = json(old);
        assertEquals(StateCodec.VERSION, resaved.get("version").getAsInt());
        assertEquals(0, resaved.getAsJsonArray("player_members").size());
        SimFixtures.run(old, 2L * DPY, DPY);
    }

    @Test
    void aMalformedPlayerRecordFailsLoudly() {
        WorldSim sim = world();
        int a = twoActiveSects(sim)[0];
        sim = editSect(sim, a, s -> s.addProperty("prestige", 10.0));
        sim.joinSect(ALICE, "Alice", a, PLAIN);
        JsonObject json = json(sim);
        json.getAsJsonArray("player_members").get(0).getAsJsonObject().remove("player");
        assertThrows(SimFormatException.class, () -> load(json));
        JsonObject twice = json(sim);
        twice.getAsJsonArray("player_members").add(twice.getAsJsonArray("player_members").get(0).deepCopy());
        assertThrows(SimFormatException.class, () -> load(twice));
    }
}
