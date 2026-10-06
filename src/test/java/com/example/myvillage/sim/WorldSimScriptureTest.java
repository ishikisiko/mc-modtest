package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** The scripture hall in the ledger: what a member may borrow at each rank, and the borrow record. */
class WorldSimScriptureTest {
    private static final int DPY = 6;
    private static final long SEED = 1;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static final String ALICE = "00000000-0000-0000-0000-00000000000a";
    private static final String BOB = "00000000-0000-0000-0000-00000000000b";
    private static final PlayerQualification PLAIN = new PlayerQualification("mortal", 1, true, 2500);

    private static WorldSim world() {
        return SimFixtures.genesis(SEED, "small", DPY);
    }

    private static WorldSim reload(WorldSim sim) {
        return WorldSim.fromBytes(sim.toBytes(), SimFixtures.graph(SEED), SimFixtures.data());
    }

    /** Rewrites one sect's saved fields and reloads. */
    private static WorldSim editSect(WorldSim sim, int sectId, Consumer<JsonObject> edit) {
        JsonObject json = JsonParser.parseString(new String(sim.toBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        for (JsonElement s : json.getAsJsonArray("sects")) {
            if (s.getAsJsonObject().get("id").getAsInt() == sectId) {
                edit.accept(s.getAsJsonObject());
            }
        }
        return WorldSim.fromBytes(GSON.toJson(json).getBytes(StandardCharsets.UTF_8), SimFixtures.graph(SEED),
                SimFixtures.data());
    }

    private static List<SectView> heritageSects(WorldSim sim) {
        return sim.sects(false).stream().filter(s -> !s.heritageId().isEmpty()).toList();
    }

    private static List<String> chain(SectView sect) {
        ContentTables.Heritage h = SimFixtures.data().heritage(sect.heritageId());
        assertNotNull(h, "heritage " + sect.heritageId() + " is in heritages.json");
        return h.techniques();
    }

    /** A plain sect: the first active sect without a heritage, or a heritage sect stripped of it. */
    private static WorldSim withPlainSect(WorldSim sim, int[] out) {
        Optional<SectView> plain = sim.sects(false).stream().filter(s -> s.heritageId().isEmpty()).findFirst();
        if (plain.isPresent()) {
            out[0] = plain.get().id();
            return sim;
        }
        int id = sim.sects(false).get(0).id();
        out[0] = id;
        return editSect(sim, id, s -> s.addProperty("heritage", ""));
    }

    private static List<String> clean(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            if (!id.isEmpty() && !id.equals("basic_breathing") && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    @Test
    void heritageSectLendsItsChainByRank() {
        WorldSim sim = world();
        List<SectView> heritage = heritageSects(sim);
        assertFalse(heritage.isEmpty(), "the fixture world has a heritage sect");
        SectView sect = heritage.get(0);
        List<String> chain = chain(sect);

        assertEquals(List.of(), sim.borrowable(ALICE), "no record, nothing to borrow");
        sim.joinSect(ALICE, "Alice", sect.id(), PLAIN, true);
        assertEquals(clean(chain.subList(0, 1)), sim.borrowable(ALICE));
        sim.promotePlayer(ALICE, "Alice", "inner");
        assertEquals(clean(chain.subList(0, Math.min(2, chain.size()))), sim.borrowable(ALICE));
        sim.promotePlayer(ALICE, "Alice", "elder");
        assertEquals(clean(chain), sim.borrowable(ALICE));
        List<String> list = sim.borrowable(ALICE);
        assertThrows(UnsupportedOperationException.class, () -> list.add("x"), "the list is read-only");

        sim.leaveSect(ALICE, "Alice");
        assertEquals(List.of(), sim.borrowable(ALICE), "a former member borrows nothing");
    }

    @Test
    void plainSectLendsBasicThenSignature() {
        int[] id = new int[1];
        WorldSim sim = withPlainSect(world(), id);
        SectView sect = sim.sect(id[0]).orElseThrow();
        assertTrue(sect.heritageId().isEmpty());

        sim.joinSect(ALICE, "Alice", sect.id(), PLAIN, true);
        assertEquals(clean(List.of(sect.basicTechniqueId())), sim.borrowable(ALICE));
        sim.promotePlayer(ALICE, "Alice", "inner");
        assertEquals(clean(List.of(sect.basicTechniqueId(), sect.signatureTechniqueId())), sim.borrowable(ALICE));
        sim.promotePlayer(ALICE, "Alice", "elder");
        assertEquals(clean(List.of(sect.basicTechniqueId(), sect.signatureTechniqueId())), sim.borrowable(ALICE));

        // The mortal-grade breathing method has no manual; a sect teaching it as basic lends only its signature.
        WorldSim mortal = editSect(sim, sect.id(), s -> s.addProperty("basic_technique", "basic_breathing"));
        assertEquals(clean(List.of(sect.signatureTechniqueId())), mortal.borrowable(ALICE));
        mortal.promotePlayer(ALICE, "Alice", "outer");
        assertEquals(List.of(), mortal.borrowable(ALICE));

        // A destroyed sect lends nothing.
        WorldSim razed = editSect(sim, sect.id(), s -> s.addProperty("state", "destroyed"));
        assertEquals(List.of(), razed.borrowable(ALICE));
    }

    @Test
    void borrowRecordsTheManualAndAChronicleLine() {
        WorldSim sim = world();
        SectView sect = heritageSects(sim).get(0);
        String first = chain(sect).get(0);

        assertEquals("not_member", assertThrows(IllegalArgumentException.class,
                () -> sim.recordBorrow(ALICE, "Alice", first)).getMessage());
        sim.joinSect(ALICE, "Alice", sect.id(), PLAIN, true);
        assertFalse(sim.hasBorrowed(ALICE, first));
        String later = chain(sect).get(chain(sect).size() - 1);
        assertEquals("not_borrowable", assertThrows(IllegalArgumentException.class,
                () -> sim.recordBorrow(ALICE, "Alice", later)).getMessage(), "an outer disciple gets the first only");
        assertEquals("not_borrowable", assertThrows(IllegalArgumentException.class,
                () -> sim.recordBorrow(ALICE, "Alice", "no_such_technique")).getMessage());

        SimEvent e = sim.recordBorrow(ALICE, "Alice", first);
        assertTrue(sim.hasBorrowed(ALICE, first));
        assertEquals(List.of(first), sim.playerMember(ALICE).orElseThrow().borrowed());
        assertEquals("player_borrow", e.type());
        assertEquals(2, e.importance(), "a minor event needs a person subject; a player is none");
        assertTrue(e.actors().isEmpty());
        assertEquals(List.of(sect.id()), e.sects());
        assertEquals(sect.regionId(), e.regionId());
        assertEquals(sim.day(), e.day());
        assertEquals(TextKeys.PLAYER_BORROW + ".1", e.textKey());
        assertEquals(List.of("Alice", sect.name(), SimFixtures.data().technique(first).name()), e.params());
        assertEquals(1, sim.recentEvents(1, Integer.MAX_VALUE, x -> x.id() == e.id()).size(),
                "the event is in the chronicle at once");
        assertFalse(sim.step(DPY).contains(e), "the next settled day does not return it again");
        assertEquals(1, sim.recentEvents(1, Integer.MAX_VALUE, x -> x.id() == e.id()).size());

        assertEquals("already_borrowed", assertThrows(IllegalArgumentException.class,
                () -> sim.recordBorrow(ALICE, "Alice", first)).getMessage());
        assertEquals("not_member", assertThrows(IllegalArgumentException.class,
                () -> sim.recordBorrow(BOB, "Bob", first)).getMessage());
        // not_member is judged before not_borrowable.
        assertEquals("not_member", assertThrows(IllegalArgumentException.class,
                () -> sim.recordBorrow(BOB, "Bob", "no_such_technique")).getMessage());

        // The record survives the codec and outlives membership.
        WorldSim back = reload(sim);
        assertTrue(back.hasBorrowed(ALICE, first));
        assertEquals(List.of(first), back.playerMember(ALICE).orElseThrow().borrowed());
        back.leaveSect(ALICE, "Alice");
        assertTrue(back.hasBorrowed(ALICE, first));
        assertEquals("not_member", assertThrows(IllegalArgumentException.class,
                () -> back.recordBorrow(ALICE, "Alice", first)).getMessage());
    }

    @Test
    void anElderMayBorrowEveryManualOfTheChain() {
        WorldSim sim = world();
        List<SectView> heritage = heritageSects(sim);
        assertFalse(heritage.isEmpty());
        int n = 0;
        for (SectView sect : heritage) {
            String player = String.format("00000000-0000-0000-0000-%012d", n++);
            List<String> chain = chain(sect);
            sim.joinSect(player, "P" + n, sect.id(), PLAIN, true);
            sim.promotePlayer(player, "P" + n, "elder");
            assertEquals(clean(chain), sim.borrowable(player), sect.name());
            for (String id : chain) {
                assertNotNull(SimFixtures.data().technique(id), id + " is in techniques.json");
                sim.recordBorrow(player, "P" + n, id);
                assertTrue(sim.hasBorrowed(player, id));
            }
        }
    }
}
