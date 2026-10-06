package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.SimDataLoader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Heritages (传承): genesis assignment, the lost pool, rekindling, the save format. */
class WorldSimHeritageTest {
    private static final int DPY = 6;
    /**
     * With every genesis sect rolling a heritage, seed 7 loses a heritage in year 77 and a founder
     * rekindles it in year 87. The history depends on the shipped data (the technique pool size
     * among other things): when a data change breaks this, scan seeds for one that loses and
     * rekindles a heritage within 150 years and record it here (seed 2 did until 0.40.0 added two
     * movement techniques).
     */
    private static final long SEED = 7;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static SimData always;

    /** The shipped data with {@code genesis.heritage_chance} set to {@code chance}. */
    private static SimData withChance(String chance) {
        SimData.ResourceOpener base = SimFixtures.opener();
        return WorldSim.loadData(path -> {
            InputStream in = base.open(path);
            if (!path.equals(SimDataLoader.RULES) || in == null) {
                return in;
            }
            String text;
            try (in) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            String edited = text.replaceFirst("\"heritage_chance\": [0-9.]+", "\"heritage_chance\": " + chance);
            return new ByteArrayInputStream(edited.getBytes(StandardCharsets.UTF_8));
        });
    }

    private static synchronized SimData always() {
        if (always == null) {
            always = withChance("1.0");
        }
        return always;
    }

    @Test
    void shippedHeritagesLoadAsChainsOfKnownTechniques() {
        SimData data = SimFixtures.data();
        assertFalse(data.heritages().isEmpty());
        for (ContentTables.Heritage h : data.heritages()) {
            assertTrue(h.techniques().size() >= 2, h.id());
            for (String t : h.techniques()) {
                assertNotNull(data.technique(t), h.id() + " names " + t);
                assertEquals(h, data.heritageOfTechnique(t));
            }
            assertEquals(h, data.heritage(h.id()));
        }
        assertEquals(0.25, data.rules().genesis().heritageChance());
    }

    @Test
    void genesisGivesEachHeritageToAtMostOneSectWithItsChain() {
        SimData data = always();
        WorldSim sim = WorldSim.genesis(SEED, SimFixtures.graph(SEED), data, "small", DPY);
        Set<String> held = new HashSet<>();
        int withHeritage = 0;
        for (SectView s : sim.sects(true)) {
            if (s.foundedDay() >= 0 || s.heritageId().isEmpty()) {
                continue;
            }
            withHeritage++;
            assertTrue(held.add(s.heritageId()), "heritage " + s.heritageId() + " given twice");
            ContentTables.Heritage h = data.heritage(s.heritageId());
            assertEquals(h.last(), s.signatureTechniqueId());
            assertEquals(h.name(), s.heritageName());
        }
        assertEquals(Math.min(data.heritages().size(), data.rules().tier("small").sects()), withHeritage,
                "with chance 1 every heritage goes to a genesis sect while sects last");
        WorldSim none = WorldSim.genesis(SEED, SimFixtures.graph(SEED), withChance("0"), "small", DPY);
        for (SectView s : none.sects(true)) {
            if (s.foundedDay() < 0) {
                assertTrue(s.heritageId().isEmpty(), s.name() + " has a heritage at chance 0");
            }
        }
    }

    @Test
    void aDestroyedSectLosesItsHeritageAndAFounderRekindlesIt() {
        SimFixtures.Collector events = new SimFixtures.Collector();
        WorldSim sim = WorldSim.genesis(SEED, SimFixtures.graph(SEED), always(), "small", DPY, events);
        for (int year = 0; year < 150; year++) {
            SimFixtures.run(sim, DPY, DPY);
            Set<String> active = new HashSet<>();
            for (SectView s : sim.sects(false)) {
                if (!s.heritageId().isEmpty()) {
                    assertTrue(active.add(s.heritageId()), "two active sects hold " + s.heritageId());
                }
            }
            for (String lost : sim.lostHeritageIds()) {
                assertFalse(active.contains(lost), lost + " is both held and lost");
            }
            assertEquals(sim.lostHeritageIds().size(), new HashSet<>(sim.lostHeritageIds()).size());
        }
        SimEvent lost = find(events.events, "world_sim.event.sect.heritage_lost.");
        SimEvent rekindled = find(events.events, "world_sim.event.sect.heritage_rekindled.");
        assertNotNull(lost, "a heritage was lost");
        assertNotNull(rekindled, "a lost heritage was rekindled");
        assertTrue(lost.causeId() > 0, "the loss names the destruction");
        SectView loser = sim.sect(lost.sects().get(0)).orElseThrow();
        assertEquals("destroyed", loser.state());
        assertFalse(loser.heritageId().isEmpty(), "a destroyed sect keeps its heritage for history");
        SectView old = sim.sect(rekindled.sects().get(1)).orElseThrow();
        SectView heir = sim.sect(rekindled.sects().get(0)).orElseThrow();
        assertEquals("destroyed", old.state());
        assertEquals(old.heritageId(), heir.heritageId());
        assertEquals(rekindled.day(), heir.foundedDay());
        assertEquals(rekindled.causeId(), events.events.stream()
                .filter(e -> e.day() == rekindled.day() && e.sects().contains(heir.id())
                        && (e.type().equals("sect_founded") || e.type().equals("sect_split")))
                .findFirst().orElseThrow().id());
        assertArrayEquals(sim.toBytes(), WorldSim.fromBytes(sim.toBytes(), SimFixtures.graph(SEED), always()).toBytes());
    }

    @Test
    void aVersionOnePayloadWithoutHeritagesLoads() {
        WorldSim sim = WorldSim.genesis(SEED, SimFixtures.graph(SEED), always(), "small", DPY);
        JsonObject json = JsonParser.parseString(new String(sim.toBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(2, json.get("version").getAsInt());
        json.addProperty("version", 1);
        json.remove("lost_heritages");
        for (JsonElement s : json.getAsJsonArray("sects")) {
            assertTrue(s.getAsJsonObject().has("heritage"));
            s.getAsJsonObject().remove("heritage");
        }
        WorldSim old = WorldSim.fromBytes(GSON.toJson(json).getBytes(StandardCharsets.UTF_8), SimFixtures.graph(SEED),
                always());
        for (SectView s : old.sects(true)) {
            assertEquals("", s.heritageId());
            assertEquals("", s.heritageName());
        }
        assertTrue(old.lostHeritageIds().isEmpty());
        JsonObject resaved = JsonParser.parseString(new String(old.toBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(2, resaved.get("version").getAsInt());
        SimFixtures.run(old, 5L * DPY, DPY);
    }

    private static SimEvent find(List<SimEvent> events, String keyPrefix) {
        return events.stream().filter(e -> e.textKey().startsWith(keyPrefix)).findFirst().orElse(null);
    }
}
