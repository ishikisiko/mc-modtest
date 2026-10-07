package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WorldSimSaveLoadTest {
    private static final int DPY = 6;
    private static final long SEED = 21;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static WorldSim world(int years) {
        WorldSim sim = SimFixtures.genesis(SEED, "small", DPY);
        SimFixtures.run(sim, (long) years * DPY, DPY);
        return sim;
    }

    private static WorldSim load(byte[] bytes) {
        return WorldSim.fromBytes(bytes, SimFixtures.graph(SEED), SimFixtures.data());
    }

    private static JsonObject json(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static byte[] bytes(JsonObject json) {
        return GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void roundTripIsByteIdentical() {
        WorldSim sim = world(30);
        byte[] saved = sim.toBytes();
        assertArrayEquals(saved, load(saved).toBytes());
    }

    @Test
    void restoredWorldContinuesExactlyLikeTheOriginal() {
        WorldSim original = world(25);
        WorldSim restored = load(original.toBytes());
        SimFixtures.Collector a = new SimFixtures.Collector();
        SimFixtures.Collector b = new SimFixtures.Collector();
        original.setObserver(a);
        restored.setObserver(b);
        SimFixtures.run(original, 60L * DPY, DPY);
        SimFixtures.run(restored, 60L * DPY, DPY);
        assertEquals(a.events, b.events);
        assertTrue(a.events.size() > 50, "events after restore: " + a.events.size());
        assertArrayEquals(original.toBytes(), restored.toBytes());
    }

    @Test
    void newerFormatVersionFailsLoudly() {
        JsonObject json = json(world(1).toBytes());
        json.addProperty("version", 99);
        SimFormatException e = assertThrows(SimFormatException.class, () -> load(bytes(json)));
        assertTrue(e.getMessage().contains("newer") && e.getMessage().contains("99"), e.getMessage());
    }

    @Test
    void unknownFormatOrGarbageFails() {
        JsonObject json = json(world(1).toBytes());
        json.addProperty("format", "something:else");
        assertThrows(SimFormatException.class, () -> load(bytes(json)));
        assertThrows(SimFormatException.class, () -> load("not json {".getBytes(StandardCharsets.UTF_8)));
        assertThrows(SimFormatException.class, () -> load(new byte[0]));
        JsonObject missing = json(world(1).toBytes());
        missing.remove("persons");
        SimFormatException e = assertThrows(SimFormatException.class, () -> load(bytes(missing)));
        assertTrue(e.getMessage().contains("persons"), e.getMessage());
    }

    @Test
    void addedOptionalFieldsAreTolerated() {
        byte[] saved = world(5).toBytes();
        JsonObject json = json(saved);
        json.addProperty("future_top_level", 1);
        JsonArray persons = json.getAsJsonArray("persons");
        persons.get(0).getAsJsonObject().addProperty("future_field", "x");
        json.getAsJsonArray("sects").get(0).getAsJsonObject().add("future_object", new JsonObject());
        assertArrayEquals(saved, load(bytes(json)).toBytes());
    }

    @Test
    void missingOptionalFieldsTakeDefaults() {
        JsonObject json = json(world(5).toBytes());
        JsonObject person = json.getAsJsonArray("persons").get(0).getAsJsonObject();
        person.remove("kills");
        person.remove("boons");
        person.remove("dest");
        WorldSim restored = load(bytes(json));
        int id = person.get("id").getAsInt();
        assertTrue(restored.person(id).isPresent());
    }

    @Test
    void aRestoredWorldCountsYearsWithTheCalendarItIsGiven() {
        WorldSim restored = load(world(3).toBytes());
        assertThrows(IllegalArgumentException.class, () -> restored.setDaysPerYear(0));
        int dpy = 9; // the calendar changed since genesis (DPY 6)
        restored.setDaysPerYear(dpy);
        long day = restored.day();
        assertTrue(Math.floorDiv(day, DPY) != Math.floorDiv(day, dpy), "the two calendars disagree on day " + day);
        String player = "00000000-0000-0000-0000-00000000000a";
        restored.joinSect(player, "Alice", restored.sects(false).get(0).id(),
                new PlayerQualification("mortal", 1, true, 2500), true);
        TaskView offer = restored.offerTask(player).orElseThrow();
        assertEquals(Math.floorDiv(day, dpy), offer.year(), "the task year follows the calendar set after loading");
        SimDate date = restored.date(dpy);
        assertEquals(Math.floorMod(day - restored.prehistoryDays(), dpy), date.dayOfYear(),
                "and so does the displayed date");
    }

    @Test
    void unknownRealmIsAFormatError() {
        JsonObject json = json(world(1).toBytes());
        json.getAsJsonArray("persons").get(0).getAsJsonObject().addProperty("realm", "immortal");
        SimFormatException e = assertThrows(SimFormatException.class, () -> load(bytes(json)));
        assertTrue(e.getMessage().contains("immortal"), e.getMessage());
    }
}
