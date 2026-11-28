package com.example.myvillage.worldplan;

import com.example.myvillage.entity.beast.BeastDefinitions;
import com.example.myvillage.entity.beast.ProvincialFauna;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProvincialBiomeTest {
    @Test void everyNewPresetOwnsTwoDistinctBiomeIdsPerProvince() throws Exception {
        for (String preset : new String[]{"cultivation", "cultivation_medium", "cultivation_small"}) {
            var generator = json("data/yuanxian/worldgen/world_preset/" + preset + ".json")
                    .getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
            var rules = new WorldPlanRules(FrozenRulesEncoding.decode(generator.get("rules").getAsString()));
            assertNotNull(rules.provincialBiomes);
            assertTrue(rules.provincialBiomes.vanillaWeight > 0 && rules.provincialBiomes.vanillaWeight < 1);
            Map<String, Integer> totals = new HashMap<>();
            for (var group : rules.fieldBiomes.domains) totals.merge(group.region(), 1, Integer::sum);
            for (var group : rules.provincialBiomes.profiles) {
                totals.merge(group.region(), 1, Integer::sum);
                var entry = generator.getAsJsonObject("field_biomes").get(group.biome());
                assertNotNull(entry, group.biome());
                assertEquals(group.biome(), entry.getAsString());
                var id = ResourceLocation.parse(group.biome());
                json("data/" + id.getNamespace() + "/worldgen/biome/" + id.getPath() + ".json");
            }
            assertEquals(8, totals.size());
            assertTrue(totals.values().stream().allMatch(value -> value == 2));
            assertEquals(16, generator.getAsJsonObject("field_biomes").size());
            assertEquals("yuanxian:folded_ridge", generator.get("folded_ridge_biome").getAsString());
            assertTrue(rules.profiles.stream().flatMap(profile -> profile.biomes().stream()).allMatch(id -> id.startsWith("minecraft:")));
        }
    }

    @Test void nativeFaunaBindingsPointToTheMatchingBiomeAndBothLootPools() throws Exception {
        assertEquals(14, ProvincialFauna.rules().size());
        for (var rule : ProvincialFauna.rules()) {
            var species = ResourceLocation.fromNamespaceAndPath("yuanxian", rule.id());
            assertEquals(species, BeastDefinitions.bundled().require(species).entity());
            var modifier = json("data/yuanxian/neoforge/biome_modifier/" + rule.id() + ".json");
            assertEquals(rule.biome().toString(), modifier.get("biomes").getAsString());
            assertEquals(species.toString(), modifier.getAsJsonObject("spawners").get("type").getAsString());
            var pools = json("data/yuanxian/loot_table/entities/" + rule.id() + ".json").getAsJsonArray("pools");
            assertEquals(2, pools.size());
            for (int i = 0; i < 2; ++i) {
                var item = pools.get(i).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
                assertEquals("yuanxian:" + rule.materials().get(i), item.get("name").getAsString());
            }
        }
    }

    private JsonObject json(String path) throws Exception {
        try (var stream = getClass().getResourceAsStream("/" + path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
