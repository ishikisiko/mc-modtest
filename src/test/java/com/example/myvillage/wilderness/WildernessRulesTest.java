package com.example.myvillage.wilderness;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.StatsRules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import org.junit.jupiter.api.Test;

class WildernessRulesTest {
    private static JsonObject json() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/wilderness/rules.json"))).getAsJsonObject();
    }
    @Test void catalogRangesHaveInclusiveReachableEndpointsAndStableDraws() {
        WildernessRules rules = WildernessRules.bundled();
        assertEquals(Set.of("ximo", "donghai", "nanjiang", "beiyuan", "donghuang", "lingyue", "moyu", "zhongzhou"), rules.regions().keySet());
        assertTrue(rules.region("unknown").isEmpty());
        for (var e : rules.regions().entrySet()) {
            var range = e.getValue().beast();
            Set<RealmKey> reached = new HashSet<>();
            for (long input = 0; input < 4096; input++) {
                RealmKey result = range.sample(42, e.getKey(), input);
                assertEquals(result, range.sample(42, e.getKey(), input));
                assertTrue(range.contains(result)); reached.add(result);
            }
            assertTrue(reached.contains(range.min())); assertTrue(reached.contains(range.max()));
            assertEquals(new HashSet<>(range.values()), reached);
        }
        assertEquals("myvillage:qi_refining", rules.region("ximo").orElseThrow().beast().min().realmId());
        assertEquals(0, rules.region("ximo").orElseThrow().beast().min().stage());
    }
    @Test void samplingInputAndRegionAffectResultsWithoutRngState() {
        var range = WildernessRules.bundled().region("lingyue").orElseThrow().beast();
        long equalSeeds = java.util.stream.LongStream.range(0, 64).filter(i -> range.sample(1, "lingyue", i).equals(range.sample(2, "lingyue", i))).count();
        long equalRegions = java.util.stream.LongStream.range(0, 64).filter(i -> range.sample(1, "lingyue", i).equals(range.sample(1, "moyu", i))).count();
        assertTrue(equalSeeds < 48); assertTrue(equalRegions < 48);
    }
    @Test void invalidRangeRealmAndBoundedSettingsFailAsOneRuleset() throws Exception {
        JsonObject root = json();
        JsonObject endpoint = root.getAsJsonObject("regions").getAsJsonObject("ximo").getAsJsonObject("beast").getAsJsonObject("min");
        endpoint.addProperty("realm", "myvillage:nascent_soul");
        assertThrows(IllegalArgumentException.class, () -> WildernessRules.parse(root.toString(), StatsRules.bundled()));
        JsonObject invalidStage = json();
        invalidStage.getAsJsonObject("regions").getAsJsonObject("ximo").getAsJsonObject("beast").getAsJsonObject("min").addProperty("stage", 9);
        assertThrows(IllegalArgumentException.class, () -> WildernessRules.parse(invalidStage.toString(), StatsRules.bundled()));
        JsonObject unknown = json();
        unknown.getAsJsonObject("regions").getAsJsonObject("ximo").getAsJsonObject("beast").getAsJsonObject("max").addProperty("realm", "myvillage:made_up");
        assertThrows(IllegalArgumentException.class, () -> WildernessRules.parse(unknown.toString(), StatsRules.bundled()));
        for (String field : new String[]{"interval_ticks", "max_total", "attempts_per_pass", "probes_per_person"}) {
            JsonObject bad = json(); bad.getAsJsonObject("rogues").addProperty(field, 0);
            assertThrows(IllegalArgumentException.class, () -> WildernessRules.parse(bad.toString(), StatsRules.bundled()));
        }
        JsonObject fractional = json(); fractional.getAsJsonObject("rogues").addProperty("max_total", 1.5);
        assertThrows(IllegalArgumentException.class, () -> WildernessRules.parse(fractional.toString(), StatsRules.bundled()));
        JsonObject slowPass = json(); slowPass.getAsJsonObject("rogues").addProperty("interval_ticks", 21);
        assertThrows(IllegalArgumentException.class, () -> WildernessRules.parse(slowPass.toString(), StatsRules.bundled()));
        JsonObject extras = json(); extras.addProperty("fallback", "nascent_soul");
        assertThrows(IllegalArgumentException.class, () -> WildernessRules.parse(extras.toString(), StatsRules.bundled()));
    }
    @Test void duplicateFieldsAndTrailingDocumentsAreRejectedAtStartup() throws Exception {
        String input = json().toString();
        String duplicateRoot = input.replace("\"schema\":1", "\"schema\":1,\"schema\":1");
        assertNotEquals(input, duplicateRoot);
        assertThrows(IllegalArgumentException.class, () -> WildernessRules.parse(duplicateRoot, StatsRules.bundled()));
        String duplicateNested = input.replace("\"max_total\":12", "\"max_total\":12,\"max_total\":12");
        assertNotEquals(input, duplicateNested);
        assertThrows(IllegalArgumentException.class, () -> WildernessRules.parse(duplicateNested, StatsRules.bundled()));
        assertThrows(IllegalArgumentException.class, () -> WildernessRules.parse(input + " {}", StatsRules.bundled()));
    }
    @Test void directRangeConstructionCannotSupplyEmptyDuplicateOrDisorderedStages() {
        RealmKey a = new RealmKey("qi_refining", 0), b = new RealmKey("qi_refining", 1);
        assertThrows(IllegalArgumentException.class, () -> new WildernessRules.RealmRange(a, b, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new WildernessRules.RealmRange(a, b, List.of(a, a, b)));
        assertThrows(IllegalArgumentException.class, () -> new WildernessRules.RealmRange(b, a, List.of(b, a)));
        assertThrows(IllegalArgumentException.class, () -> new WildernessRules.RealmRange(a, b, List.of(b, a)));
        assertDoesNotThrow(() -> new WildernessRules.RealmRange(a, b, List.of(a, b)));
    }
}
