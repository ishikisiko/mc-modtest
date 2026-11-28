package com.example.myvillage.worldplan;

import com.example.myvillage.entity.beast.BeastDefinitions;
import com.example.myvillage.entity.beast.ProvincialFauna;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.example.myvillage.wilderness.habitat.HabitatLayout;
import com.example.myvillage.worldplan.mine.StarterMineGeneration;
import com.example.myvillage.worldplan.roads.RoadLayout;
import com.example.myvillage.worldplan.roads.RoadRules;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProvincialBiomeTest {
    @Test void sampleConnectedAreasAcrossSmallMapSeeds() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var generator = json("data/yuanxian/worldgen/world_preset/cultivation_small.json")
                .getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
        var rules = new WorldPlanRules(FrozenRulesEncoding.decode(generator.get("rules").getAsString()));
        for (long seed : new long[]{0, 42, 173}) {
            var world = new WorldPlan(seed, rules);
            var roads = RoadLayout.empty(world, RoadRules.load());
            var mines = new StarterMineGeneration(Optional.empty(), Optional.of(List.of()));
            mines.bind(world, roads);
            var habitats = new HabitatLayout(rules.habitats, List.of(), Map.of());
            var fields = new FieldBiomeLayout(world, roads, mines, habitats).withProvincial(List.of(), null, null);
            assertEquals(14, fields.provincialSites().size(), "seed " + seed);
            for (var site : fields.provincialSites()) {
                int area = connectedArea(fields, site);
                assertTrue(area >= 350_000, seed + " " + site.profile().biome() + " " + area);
                System.out.printf("%d %s %d %d area=%d%n", seed, site.profile().biome(), site.x(), site.z(), area);
            }
        }
    }

    private static int connectedArea(FieldBiomeLayout fields, ProvincialBiomeLayout.Site site) {
        int half = 18, step = 64, edge = 2 * half + 1;
        boolean[][] belongs = new boolean[edge][edge];
        for (int z = 0; z < edge; z++) for (int x = 0; x < edge; x++) {
            var region = fields.surface(site.x() + (x - half) * step, site.z() + (z - half) * step);
            belongs[z][x] = site.profile().biome().equals(region.biome());
        }
        var queue = new ArrayDeque<Integer>();
        if (belongs[half][half]) queue.add(half * edge + half);
        belongs[half][half] = false;
        int area = 0;
        final int[] dx = {1, 0, -1, 0}, dz = {0, 1, 0, -1};
        while (!queue.isEmpty()) {
            int cell = queue.removeFirst();
            area += step * step;
            for (int dir = 0; dir < 4; dir++) {
                int x = cell % edge + dx[dir], z = cell / edge + dz[dir];
                if (x < 0 || z < 0 || x >= edge || z >= edge || !belongs[z][x]) continue;
                belongs[z][x] = false;
                queue.add(z * edge + x);
            }
        }
        return area;
    }

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
