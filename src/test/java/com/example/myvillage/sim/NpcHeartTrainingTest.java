package com.example.myvillage.sim;

import com.example.myvillage.sim.engine.Cultivation;
import com.example.myvillage.sim.engine.NpcCultivation;
import com.example.myvillage.sim.engine.NpcHeartTraining;
import com.example.myvillage.sim.engine.SimContext;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Sect;
import com.example.myvillage.sim.model.StateCodec;
import com.example.myvillage.sim.model.WorldState;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcHeartTrainingTest {
    private final SimContext ctx = new SimContext(new WorldState(), SimFixtures.data(), SimFixtures.graph(21), 24);

    private Person person(int stage) {
        ctx.state.seed = 21;
        ctx.state.tierId = "small";
        ctx.state.genesisDaysPerYear = 24;
        ctx.state.nextPersonId = 2;
        var person = new Person();
        person.id = 1;
        person.given = "测试";
        person.stage = stage;
        person.regionId = ctx.graph.regions().getFirst().id();
        person.homeRegionId = person.regionId;
        NpcCultivation.initialize(ctx, person);
        ctx.state.persons.put(person.id, person);
        return person;
    }

    @Test
    void everyShippedHeartUsesThePlayerSlotAndSpiritualValues() throws Exception {
        var dir = SimFixtures.RESOURCES.resolve("data/yuanxian/yuanxian/technique");
        try (var files = Files.list(dir)) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                var definition = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!definition.get("category").getAsString().equals("core")) continue;
                String id = file.getFileName().toString().replace(".json", "");
                var entry = ctx.data.npcHearts().get(id);
                assertNotNull(entry, id);
                var effects = definition.getAsJsonObject("effects");
                if (effects == null || !effects.has("core") || !effects.getAsJsonObject("core").has("heart")) continue;
                var heart = effects.getAsJsonObject("core").getAsJsonObject("heart");
                assertEquals(heart.get("slot").getAsString(), entry.slot(), id);
                if (entry.slot().equals("spirit")) {
                    assertEquals(heart.get("capacity").getAsInt(), entry.capacity(), id);
                    assertEquals(heart.get("cultivation").getAsInt(), entry.cultivation(), id);
                }
            }
        }
    }

    @Test
    void aManualWaitsForTheExactStageAptitudeAndPredecessor() {
        var person = person(3);
        person.root[Person.METAL] = 39;
        NpcHeartTraining.found(ctx, person, "jinlin_cuiti_jue");
        NpcHeartTraining.study(ctx, person);
        assertEquals("basic_breathing", person.hearts.spirit());
        assertFalse(person.hearts.learned().contains("jinlin_cuiti_jue"));

        person.stage = 4;
        person.root[Person.METAL] = 40;
        NpcHeartTraining.study(ctx, person);
        assertFalse(person.hearts.learned().contains("jinlin_cuiti_jue"));

        NpcHeartTraining.found(ctx, person, "tiegu_gong");
        person.root[Person.METAL] = 39;
        NpcHeartTraining.study(ctx, person);
        assertEquals("tiegu_gong", person.hearts.defense());
        person.root[Person.METAL] = 40;
        NpcHeartTraining.study(ctx, person);
        assertEquals("jinlin_cuiti_jue", person.hearts.defense());
        assertTrue(person.hearts.found().isEmpty());
    }

    @Test
    void theThreeSlotsAndDepletedPowerSurviveALedgerRoundTrip() {
        var person = person(0);
        long base = NpcCultivation.powerCap(ctx, person);
        person.techniqueId = "ruijin_jue";
        for (String manual : java.util.List.of("duanti_jue", "qingmu_changchun_gong"))
            NpcHeartTraining.found(ctx, person, manual);
        NpcHeartTraining.study(ctx, person);
        assertEquals("ruijin_jue", person.hearts.attack());
        assertEquals("duanti_jue", person.hearts.defense());
        assertEquals("qingmu_changchun_gong", person.hearts.spirit());
        assertEquals(base + 6, NpcCultivation.powerCap(ctx, person));
        assertEquals(base, person.spiritualPower);
        assertTrue(NpcCultivation.recover(ctx, person.id, 2));

        byte[] saved = StateCodec.toBytes(ctx.state, ctx.realms);
        var restored = StateCodec.fromBytes(saved, ctx.realms, ctx.data.npcFates(), ctx.data.npcHearts());
        assertEquals(person.hearts, restored.persons.get(person.id).hearts);
        assertEquals(base + 2, restored.persons.get(person.id).spiritualPower);
        assertArrayEquals(saved, StateCodec.toBytes(restored, ctx.realms));

        var broken = StateCodec.toJson(ctx.state, ctx.realms);
        broken.getAsJsonArray("persons").get(0).getAsJsonObject().getAsJsonObject("hearts")
                .addProperty("defense", "ruijin_jue");
        assertThrows(SimFormatException.class, () -> StateCodec.fromBytes(broken.toString().getBytes(StandardCharsets.UTF_8),
                ctx.realms, ctx.data.npcFates(), ctx.data.npcHearts()));
    }

    @Test
    void sectRankOpensTheSameHeritageAndCultivationUsesThePlayerValue() {
        var person = person(4);
        person.root[Person.METAL] = 200;
        person.sectId = 1;
        var sect = new Sect();
        sect.id = 1;
        sect.name = "测试";
        sect.heritageId = "wanjie_jinshen_mai";
        sect.homeRegionId = person.regionId;
        ctx.state.sects.put(sect.id, sect);
        person.rank = "outer";
        NpcHeartTraining.study(ctx, person);
        assertTrue(person.hearts.learned().contains("tiegu_gong"));
        assertFalse(person.hearts.learned().contains("jinlin_cuiti_jue"));
        person.rank = "inner";
        NpcHeartTraining.study(ctx, person);
        assertEquals("jinlin_cuiti_jue", person.hearts.defense());

        NpcHeartTraining.found(ctx, person, "qingmu_changchun_gong");
        NpcHeartTraining.study(ctx, person);
        var realm = ctx.realm(person);
        long cap = ctx.data.npcHearts().stageCaps().get(realm.id()).get(person.stage);
        double expected = (Cultivation.yearlyRate(ctx, person) / ctx.dpy
                + 8 * realm.stage(person.stage).cap() / cap) * 0.25;
        Cultivation.daily(ctx, person, 0.25);
        assertEquals(expected, person.progress, 1e-7);
    }
}
