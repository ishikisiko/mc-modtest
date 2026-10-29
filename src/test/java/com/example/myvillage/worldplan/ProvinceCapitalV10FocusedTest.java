package com.example.myvillage.worldplan;

import com.example.myvillage.worldplan.capital.CapitalGameBundle;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProvinceCapitalV10FocusedTest {
    private static JsonObject json(String path) throws Exception {
        try(var in=ProvinceCapitalV10FocusedTest.class.getResourceAsStream(path)) {
            assertNotNull(in,path);return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void v9BytesStayFrozenAndOnlyV10CapitalReferencesChange() throws Exception {
        var old=json(WorldPlanRules.V9_RESOURCE);var current=json(WorldPlanRules.V10_RESOURCE);
        CapitalGameBundle.requireHash(CapitalGameBundle.readResource("myvillage:worldplan/frozen_v9.json",4*1024*1024),"3c4e6e63fdea47e7f73f16359d0b4a2d8df940e49a0d14a26328f1a9ee6505a3","frozen_v9");
        var rules=new WorldPlanRules(current.toString());assertEquals(8,rules.capitals.bundles.size());assertEquals(192,rules.capitals.lifePlaceLimit);
        assertEquals(Set.of("v3"),Set.copyOf(rules.capitals.bundles.stream().map(b->b.resourceVersion).toList()));
        for(var b:rules.capitals.bundles){
            assertEquals("province_capitals/"+b.regionId+"_environment_v3",b.layoutFamily);
            assertEquals(b.cityId+"_blocks_v3",b.lifeLayout);
            assertTrue(b.life().getAsJsonArray("places").size()<=192);
        }
        current.addProperty("algorithm",WorldPlanRules.V9_ALGORITHM);
        current.getAsJsonObject("capitals").add("assets",old.getAsJsonObject("capitals").get("assets"));
        current.getAsJsonObject("capitals").add("bundles",old.getAsJsonObject("capitals").get("bundles"));
        assertEquals(old,current);
    }
    @Test void v10HasStableSaveRoundTripAndEightCities() throws Exception {
        var rules=new WorldPlanRules(json(WorldPlanRules.V10_RESOURCE).toString());
        var plan=new WorldPlan(20261029L,rules);
        var restored=new WorldPlan(20261029L,new WorldPlanRules(FrozenRulesEncoding.decode(FrozenRulesEncoding.encode(rules.json()))));
        assertEquals(plan.fingerprint(),restored.fingerprint());assertEquals(plan.manifest(),restored.manifest());assertEquals(8,plan.cities().size());
        for(var c:plan.cities()){assertTrue(c.originY()>=rules.capitals.minY);assertTrue(c.originY()+c.bundle().height<=rules.capitals.maxY);}
    }
    @Test void v9CannotOptIntoV3AndV10CannotMixOldLayouts() throws Exception {
        var old=json(WorldPlanRules.V9_RESOURCE);old.getAsJsonObject("capitals").getAsJsonObject("assets").getAsJsonArray("accepted_versions").set(0,new JsonPrimitive("v3"));
        assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(old.toString()));
        var current=json(WorldPlanRules.V10_RESOURCE);current.getAsJsonObject("capitals").getAsJsonObject("assets").getAsJsonArray("accepted_versions").set(0,new JsonPrimitive("v2"));
        assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(current.toString()));
    }
    @Test void currentDefaultIsUniqueAndEveryNewTileMatchesItsHash() throws Exception {
        String preset="data/myvillage/worldgen/world_preset/cultivation.json";
        assertEquals(1,Collections.list(getClass().getClassLoader().getResources(preset)).size());
        var generator=json("/"+preset).getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
        assertEquals("myvillage:planned_v10",generator.get("type").getAsString());
        assertEquals(json(WorldPlanRules.V10_RESOURCE),JsonParser.parseString(FrozenRulesEncoding.decode(generator.get("rules").getAsString())));
        for(var b:new WorldPlanRules(json(WorldPlanRules.V10_RESOURCE).toString()).capitals.bundles){
            CapitalGameBundle.requireHash(CapitalGameBundle.readResource(b.terrainResource,16*1024*1024),b.terrainHash,b.terrainResource);
            for(var t:b.tiles)CapitalGameBundle.requireHash(CapitalGameBundle.readResource(CapitalGameBundle.templateFile(t.template()),8*1024*1024),t.sha256(),t.template());
        }
    }
}
