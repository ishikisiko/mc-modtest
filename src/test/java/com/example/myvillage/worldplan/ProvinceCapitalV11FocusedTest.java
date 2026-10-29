package com.example.myvillage.worldplan;

import com.example.myvillage.worldplan.capital.CapitalGameBundle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProvinceCapitalV11FocusedTest {
    private static JsonObject json(String path)throws Exception{
        try(var in=ProvinceCapitalV11FocusedTest.class.getResourceAsStream(path)){assertNotNull(in,path);return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    @Test void v10StaysFrozenAndV11ChangesOnlyAlgorithmAndAssetReferences()throws Exception{
        CapitalGameBundle.requireHash(CapitalGameBundle.readResource("myvillage:worldplan/frozen_v10.json",4*1024*1024),"9308a842d9dfedb64c43622cac1aaf4d46b07c614ed66c40d3e3d3484537fbc1","frozen_v10");
        var old=json(WorldPlanRules.V10_RESOURCE);var current=json(WorldPlanRules.V11_RESOURCE);var rules=new WorldPlanRules(current.toString());
        assertEquals(8,rules.capitals.bundles.size());assertEquals(192,rules.capitals.lifePlaceLimit);
        assertEquals(Set.of("v4"),Set.copyOf(rules.capitals.bundles.stream().map(b->b.resourceVersion).toList()));
        current.addProperty("algorithm",WorldPlanRules.V10_ALGORITHM);current.getAsJsonObject("capitals").add("assets",old.getAsJsonObject("capitals").get("assets"));current.getAsJsonObject("capitals").add("bundles",old.getAsJsonObject("capitals").get("bundles"));
        assertEquals(old,current);
    }
    @Test void v10RejectsV4AndV11RejectsTheOldResourceVersion()throws Exception{
        var old=json(WorldPlanRules.V10_RESOURCE);old.getAsJsonObject("capitals").getAsJsonObject("assets").getAsJsonArray("accepted_versions").set(0,new JsonPrimitive("v4"));
        assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(old.toString()));
        var current=json(WorldPlanRules.V11_RESOURCE);current.getAsJsonObject("capitals").getAsJsonObject("assets").getAsJsonArray("accepted_versions").set(0,new JsonPrimitive("v3"));
        assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(current.toString()));
    }
    @Test void v11RoundTripKeepsThePlanAndAllEightCities()throws Exception{
        var rules=new WorldPlanRules(json(WorldPlanRules.V11_RESOURCE).toString());var plan=new WorldPlan(20261029L,rules);
        var restored=new WorldPlan(20261029L,new WorldPlanRules(FrozenRulesEncoding.decode(FrozenRulesEncoding.encode(rules.json()))));
        assertEquals(plan.fingerprint(),restored.fingerprint());assertEquals(plan.manifest(),restored.manifest());assertEquals(8,plan.cities().size());
    }
    @Test void defaultV11IsUniqueAndEveryTemplateMatchesItsFrozenHash()throws Exception{
        String preset="data/myvillage/worldgen/world_preset/cultivation.json";assertEquals(1,Collections.list(getClass().getClassLoader().getResources(preset)).size());
        var generator=json("/"+preset).getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
        assertEquals("myvillage:planned_v11",generator.get("type").getAsString());
        assertEquals(json(WorldPlanRules.V11_RESOURCE),JsonParser.parseString(FrozenRulesEncoding.decode(generator.get("rules").getAsString())));
        for(var bundle:new WorldPlanRules(json(WorldPlanRules.V11_RESOURCE).toString()).capitals.bundles){
            CapitalGameBundle.requireHash(CapitalGameBundle.readResource(bundle.terrainResource,16*1024*1024),bundle.terrainHash,bundle.terrainResource);
            for(var tile:bundle.tiles)CapitalGameBundle.requireHash(CapitalGameBundle.readResource(CapitalGameBundle.templateFile(tile.template()),8*1024*1024),tile.sha256(),tile.template());
        }
    }
}
