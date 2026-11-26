package com.example.myvillage.worldplan;

import com.example.myvillage.worldplan.capital.CapitalGameBundle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProvinceCapitalV11FocusedTest {
    private static JsonObject json(String path)throws Exception{
        try(var in=ProvinceCapitalV11FocusedTest.class.getResourceAsStream(path)){assertNotNull(in,path);return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    @Test void currentDevelopmentResourcesContainOnlyTheCurrentWorldplan()throws Exception{
        var current=json(WorldPlanRules.V11_RESOURCE);var rules=new WorldPlanRules(current.toString());
        assertEquals(WorldPlanRules.V11_ALGORITHM,rules.algorithm);
        assertEquals(8,rules.capitals.bundles.size());assertEquals(192,rules.capitals.lifePlaceLimit);
        assertEquals(Set.of("v4"),Set.copyOf(rules.capitals.bundles.stream().map(b->b.resourceVersion).toList()));
        for(int version=1;version<=10;version++){
            String resource="yuanxian:worldplan/frozen_v"+version+".json";
            assertThrows(IllegalStateException.class,()->CapitalGameBundle.readResource(resource,4*1024*1024),resource);
        }
    }
    @Test void currentWorldplanRejectsRetiredAlgorithmsAndAssetVersions()throws Exception{
        for(int version=1;version<=10;version++){
            var current=json(WorldPlanRules.V11_RESOURCE);current.addProperty("algorithm","yuanxian:worldplan_v"+version);
            assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(current.toString()));
        }
        for(String version:new String[]{"v1","v2","v3","v5"}){
            var current=json(WorldPlanRules.V11_RESOURCE);current.getAsJsonObject("capitals").getAsJsonObject("assets").getAsJsonArray("accepted_versions").set(0,new JsonPrimitive(version));
            assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(current.toString()));
        }
    }
    @Test void v11RoundTripKeepsThePlanAndAllEightCities()throws Exception{
        var rules=new WorldPlanRules(json(WorldPlanRules.V11_RESOURCE).toString());var plan=new WorldPlan(20261029L,rules);
        var restored=new WorldPlan(20261029L,new WorldPlanRules(FrozenRulesEncoding.decode(FrozenRulesEncoding.encode(rules.json()))));
        assertEquals(plan.fingerprint(),restored.fingerprint());assertEquals(plan.manifest(),restored.manifest());assertEquals(8,plan.cities().size());
    }
    @Test void publicRoadPortsReachTheCurrentExpandedCapitalEdges() throws Exception {
        var rules = new WorldPlanRules(json(WorldPlanRules.V11_RESOURCE).toString());
        var plan = new WorldPlan(20261029L, rules);
        var roads = com.example.myvillage.worldplan.roads.RoadRules.load();
        for (var city : plan.cities()) {
            var exit = com.example.myvillage.worldplan.roads.WorldRoadPlanner.exit(plan, roads, city.regionId());
            assertFalse(city.contains(exit.frame().edgeX(), exit.frame().edgeZ(), 0), city.cityId());
            assertTrue(exit.existing().stream().allMatch(cell -> city.contains(cell.x(), cell.z(), 0)), city.cityId());
        }
    }
    @Test void defaultV11IsUniqueAndEveryTemplateMatchesItsFrozenHash()throws Exception{
        String preset="data/yuanxian/worldgen/world_preset/cultivation.json";assertEquals(1,Collections.list(getClass().getClassLoader().getResources(preset)).size());
        var generator=json("/"+preset).getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
        assertEquals("yuanxian:planned_v11",generator.get("type").getAsString());
        assertEquals(json(WorldPlanRules.V11_RESOURCE),JsonParser.parseString(FrozenRulesEncoding.decode(generator.get("rules").getAsString())));
        for(var bundle:new WorldPlanRules(json(WorldPlanRules.V11_RESOURCE).toString()).capitals.bundles){
            CapitalGameBundle.requireHash(CapitalGameBundle.readResource(bundle.terrainResource,16*1024*1024),bundle.terrainHash,bundle.terrainResource);
            for(var tile:bundle.tiles)CapitalGameBundle.requireHash(CapitalGameBundle.readResource(CapitalGameBundle.templateFile(tile.template()),8*1024*1024),tile.sha256(),tile.template());
        }
    }

    @Test void bothSizesAreSelectableAndUseTheSameGenerationResources() throws Exception {
        String tag = "data/minecraft/tags/worldgen/world_preset/normal.json";
        var selectable = new HashSet<String>();
        for (var resource : Collections.list(getClass().getClassLoader().getResources(tag))) {
            try (var in = resource.openStream()) {
                var data = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                data.getAsJsonArray("values").forEach(value -> {
                    if (value.isJsonPrimitive()) selectable.add(value.getAsString());
                });
            }
        }
        assertTrue(selectable.containsAll(Set.of("yuanxian:cultivation", "yuanxian:cultivation_medium")));
        String path = "data/yuanxian/worldgen/world_preset/cultivation_medium.json";
        assertEquals(1, Collections.list(getClass().getClassLoader().getResources(path)).size());
        var medium = json("/" + path);
        var generator = medium.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
        var packedRules = generator.get("rules").getAsString();
        var rules = new WorldPlanRules(FrozenRulesEncoding.decode(packedRules));
        var large = json("/data/yuanxian/worldgen/world_preset/cultivation.json");
        var largeGenerator = large.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
        assertEquals(10000, new WorldPlanRules(FrozenRulesEncoding.decode(largeGenerator.get("rules").getAsString())).coreRadius);
        generator.add("rules", largeGenerator.get("rules"));
        assertEquals(large, medium);

        var expected = json(WorldPlanRules.V11_RESOURCE);
        expected.addProperty("core_radius", 8000);
        expected.add("outer_radius", JsonParser.parseString("[5440,6400]"));
        expected.getAsJsonObject("capitals").addProperty("ring_min", 4560);
        expected.getAsJsonObject("capitals").addProperty("ring_max", 4670);
        assertEquals(expected, JsonParser.parseString(FrozenRulesEncoding.decode(packedRules)));

        var plan = new WorldPlan(42L, rules);
        assertEquals(8, plan.cities().size());
        assertEquals(56, plan.sectSites().size());
        assertTrue(plan.coreRegionAt(8000, 0).isPresent());
        assertTrue(plan.coreRegionAt(8001, 0).isEmpty());
        var restored = new WorldPlan(42L, new WorldPlanRules(FrozenRulesEncoding.decode(FrozenRulesEncoding.encode(rules.json()))));
        assertEquals(plan.fingerprint(), restored.fingerprint());
    }
}
