package com.example.myvillage.worldplan;

import com.example.myvillage.entity.beast.FieldEncounterRules;
import com.example.myvillage.worldplan.FieldBiomeRules.Kind;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FieldBiomeTerrainTest {
    @Test void worldPresetsCarryBothNativeBiomesAndSeededTerrainRules() throws Exception {
        for (String preset : new String[]{"cultivation", "cultivation_medium", "cultivation_small"}) {
            try (var stream = getClass().getResourceAsStream("/data/yuanxian/worldgen/world_preset/" + preset + ".json")) {
                assertNotNull(stream, preset);
                var generator = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject()
                        .getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
                var rules = new WorldPlanRules(FrozenRulesEncoding.decode(generator.get("rules").getAsString()));
                for (var domain : rules.fieldBiomes.domains) {
                    var id = ResourceLocation.parse(domain.biome());
                    assertEquals(domain.biome(), generator.getAsJsonObject("field_biomes").get(domain.biome()).getAsString());
                    assertEquals(domain.region(), domain.kind() == Kind.TIDE ? "donghai" : "ximo");
                    try (var biome = getClass().getResourceAsStream("/data/" + id.getNamespace() + "/"
                            + Registries.BIOME.location().getPath() + "/" + id.getPath() + ".json")) {
                        assertNotNull(biome, domain.biome());
                    }
                }
            }
        }
    }

    @Test void tideAndWindFeaturesAreDeterministicAndRespondToTheSeed() throws Exception {
        try (var stream = getClass().getResourceAsStream(WorldPlanRules.RESOURCE)) {
            assertNotNull(stream);
            var rules = new WorldPlanRules(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            var a = new FieldBiomeTerrain(42, rules.seaLevel, rules.fieldBiomes);
            var b = new FieldBiomeTerrain(42, rules.seaLevel, rules.fieldBiomes);
            var other = new FieldBiomeTerrain(20261017, rules.seaLevel, rules.fieldBiomes);
            var bays = a.bays(4600, 1200, 2300);
            assertFalse(bays.isEmpty());
            assertEquals(bays, b.bays(4600, 1200, 2300));
            assertNotEquals(bays, other.bays(4600, 1200, 2300));
            for (var bay : bays) {
                assertEquals(3, bay.branches());
                var rising = a.bayPoint(bay, a.branch(bay, 0, -35), -35);
                int top = a.tideTop(bay, (int)Math.round(rising.x()), (int)Math.round(rising.z()));
                assertTrue(top > rules.seaLevel);
                assertTrue(top <= rules.seaLevel + rules.fieldBiomes.tide.domain().maximumRise());
            }
            var rifts = a.fractures(-4200, -1800, 2200);
            assertFalse(rifts.isEmpty());
            assertEquals(rifts, b.fractures(-4200, -1800, 2200));
            assertNotEquals(rifts, other.fractures(-4200, -1800, 2200));
            assertEquals(14, rules.fieldBiomes.wind.bridgeSpan());
            assertTrue(FieldEncounterRules.bundled().encounter().bridgeWidth() < rules.fieldBiomes.wind.bridgeSpan());
        }
    }
}
