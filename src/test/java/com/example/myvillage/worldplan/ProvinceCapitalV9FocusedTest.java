package com.example.myvillage.worldplan;

import com.example.myvillage.region.runtime.RegionQueries;
import com.example.myvillage.worldplan.capital.CapitalGameBundle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** V9 冻结版本、旧档身份与正式资源源集回归；不覆盖交易或玩家外观验收。 */
class ProvinceCapitalV9FocusedTest {
    private static final String V8_FINGERPRINT = "8fef106e9ab3ffbebd9bd61e6d72cea952476fc97e15e5f403aecd07041c1826";

    private static JsonObject json(String resource) throws Exception {
        try (var input = ProvinceCapitalV9FocusedTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test void onlySevenCityReferencesChangeAndV8IdentityStaysFrozen() throws Exception {
        JsonObject old = json(WorldPlanRules.V8_RESOURCE), current = json(WorldPlanRules.V9_RESOURCE);
        var v8 = new WorldPlanRules(old.toString()); var v9 = new WorldPlanRules(current.toString());
        assertEquals(V8_FINGERPRINT, v8.fingerprint());
        assertEquals(192, v8.capitals.lifePlaceLimit); assertEquals(192, v9.capitals.lifePlaceLimit);
        assertTrue(WorldPlanRules.compressed(v9.algorithm)); assertNull(v9.city); assertNotNull(v9.settlements);
        assertEquals(8, v9.capitals.bundles.size());
        assertEquals(7, v9.capitals.bundles.stream().filter(b -> b.resourceVersion.equals("v2")).count());
        for (var bundle : v9.capitals.bundles) {
            var legacy = v8.capitals.bundles.stream().filter(b -> b.cityId.equals(bundle.cityId)).findFirst().orElseThrow();
            assertEquals("v1", legacy.resourceVersion);
            if (bundle.regionId.equals("zhongzhou")) {
                assertEquals(legacy.resource, bundle.resource); assertEquals(legacy.manifestHash, bundle.manifestHash);
                assertEquals(legacy.bundleHash, bundle.bundleHash); assertEquals(0, bundle.anchorY);
            } else {
                assertEquals("v2", bundle.resourceVersion);
                assertEquals("province_capitals/" + bundle.regionId + "_authored_v2", bundle.layoutFamily);
                assertEquals(bundle.cityId + "_blocks_v2", bundle.lifeLayout);
                assertNotEquals(legacy.bundleHash, bundle.bundleHash);
            }
        }
        // 比较所有其它字段，包含世界、选址、出生、避让、life opener 预算及旧算法输入。
        current.addProperty("algorithm", WorldPlanRules.V8_ALGORITHM);
        current.getAsJsonObject("capitals").remove("assets");
        current.getAsJsonObject("capitals").add("bundles", old.getAsJsonObject("capitals").get("bundles"));
        assertEquals(old, current);
    }

    @Test void v9AndSavedV8UseTheirOwnStablePlansAndEntryVolumes() throws Exception {
        var old = new WorldPlanRules(json(WorldPlanRules.V8_RESOURCE).toString());
        var current = new WorldPlanRules(json(WorldPlanRules.V9_RESOURCE).toString());
        for (long seed : new long[]{0, 20261029, -42}) {
            for (var rules : new WorldPlanRules[]{old, current}) {
                var plan = new WorldPlan(seed, rules);
                var restored = new WorldPlan(seed, new WorldPlanRules(FrozenRulesEncoding.decode(FrozenRulesEncoding.encode(rules.json()))));
                assertEquals(plan.fingerprint(), restored.fingerprint()); assertEquals(plan.manifest(), restored.manifest());
                assertEquals(8, plan.cities().size());
                assertEquals(Set.of("beiyuan", "donghai", "donghuang", "lingyue", "moyu", "nanjiang", "ximo", "zhongzhou"),
                        Set.copyOf(plan.cities().stream().map(c -> c.regionId()).toList()));
                for (var city : plan.cities()) {
                    var entry = city.entrance(); int x = entry.x(), z = entry.z();
                    assertEquals(city.regionId(), RegionQueries.regionAt(plan.graph(), x, z).orElseThrow());
                    assertEquals(city.cityId(), plan.capitalAt(x, z).orElseThrow().cityId());
                    assertTrue(plan.reserved(x, entry.y(), z));
                    assertTrue(city.originY() >= rules.capitals.minY);
                    assertTrue(city.originY() + city.bundle().height <= rules.capitals.maxY);
                }
            }
        }
    }

    @Test void runtimeClasspathKeepsOldFrozenAndAssetsButHasOneV9Default() throws Exception {
        for (int version = 1; version <= 9; version++) json("/data/myvillage/worldplan/frozen_v" + version + ".json");
        String preset = "data/myvillage/worldgen/world_preset/cultivation.json";
        assertEquals(1, Collections.list(getClass().getClassLoader().getResources(preset)).size(), "默认预设源必须唯一");
        var generator = json("/" + preset).getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
        assertEquals("myvillage:planned_v9", generator.get("type").getAsString());
        assertEquals(json(WorldPlanRules.V9_RESOURCE), JsonParser.parseString(FrozenRulesEncoding.decode(generator.get("rules").getAsString())));
        for (String resource : new String[]{WorldPlanRules.V8_RESOURCE, WorldPlanRules.V9_RESOURCE}) {
            var rules = new WorldPlanRules(json(resource).toString());
            for (var bundle : rules.capitals.bundles) {
                CapitalGameBundle.requireHash(CapitalGameBundle.readResource(bundle.terrainResource, 16 * 1024 * 1024), bundle.terrainHash, bundle.terrainResource);
                for (var tile : bundle.tiles) {
                    String file = CapitalGameBundle.templateFile(tile.template());
                    CapitalGameBundle.requireHash(CapitalGameBundle.readResource(file, 8 * 1024 * 1024), tile.sha256(), file);
                }
            }
        }
    }

    @Test void wrongFrozenVersionMappingAndV2ReferencesInV8AreRejected() throws Exception {
        JsonObject current = json(WorldPlanRules.V9_RESOURCE);
        var wrong = current.deepCopy();
        wrong.getAsJsonObject("capitals").getAsJsonObject("assets").getAsJsonArray("cities").get(0).getAsJsonObject().addProperty("layout_family_id", "province_capitals/wrong");
        assertThrows(IllegalArgumentException.class, () -> new WorldPlanRules(wrong.toString()));
        var missing = current.deepCopy(); missing.getAsJsonObject("capitals").remove("assets");
        assertThrows(IllegalArgumentException.class, () -> new WorldPlanRules(missing.toString()));
        var legacy = json(WorldPlanRules.V8_RESOURCE);
        legacy.getAsJsonObject("capitals").add("bundles", current.getAsJsonObject("capitals").get("bundles"));
        assertThrows(IllegalArgumentException.class, () -> new WorldPlanRules(legacy.toString()));
    }
}
