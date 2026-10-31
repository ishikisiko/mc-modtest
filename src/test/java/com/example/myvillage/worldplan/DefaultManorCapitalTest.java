package com.example.myvillage.worldplan;

import com.example.myvillage.worldplan.capital.CapitalGameBundle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultManorCapitalTest {
    private static JsonObject json(String resource) {
        return JsonParser.parseString(new String(CapitalGameBundle.readResource(resource,8*1024*1024),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static WorldPlanRules rules() { return new WorldPlanRules(json("myvillage:worldplan/frozen_v11.json").toString()); }

    @Test void currentDefaultReferencesSevenFormalManorsAndTheExistingZhongzhou() {
        var rules=rules();assertEquals(8,rules.capitals.bundles.size());assertEquals(192,rules.capitals.lifePlaceLimit);
        var houses=Map.of("beiyuan",55,"nanjiang",55,"lingyue",53,"ximo",53,"donghai",55,"donghuang",60,"moyu",55);
        int total=0,services=0;
        for(var bundle:rules.capitals.bundles) {
            assertEquals("v4",bundle.resourceVersion);
            if(bundle.regionId.equals("zhongzhou")) {assertEquals("province_capitals/zhongzhou_environment_v3",bundle.layoutFamily);continue;}
            assertEquals("province_capitals/"+bundle.regionId+"_manor",bundle.layoutFamily);
            assertEquals(bundle.cityId+"_manor",bundle.lifeLayout);
            var city=json(bundle.resource);var coverage=city.getAsJsonObject("coverage");
            assertEquals(houses.get(bundle.regionId).intValue(),coverage.get("houses").getAsInt());
            assertEquals(1,coverage.get("manors").getAsInt());total+=coverage.get("houses").getAsInt();services+=coverage.get("retained_services").getAsInt();
            assertTrue(city.getAsJsonObject("blueprint").getAsJsonObject("manor").get("native_architectural_cells").getAsInt()>20000);
            assertTrue(bundle.entries.stream().anyMatch(e->e.id().equals("lord_manor")));
            assertTrue(bundle.life().getAsJsonArray("places").size()<=192);
            if(bundle.regionId.equals("donghai"))assertEquals(256,bundle.width);
            if(bundle.regionId.equals("moyu"))assertEquals(128,bundle.height);
        }
        assertEquals(386,total);assertEquals(51,services);
    }

    @Test void theDefaultPresetAndAllRuntimeResourcesHaveTheSameFrozenIdentity() throws Exception {
        assertEquals(1,Collections.list(getClass().getClassLoader().getResources("data/myvillage/worldgen/world_preset/cultivation.json")).size());
        var preset=json("myvillage:worldgen/world_preset/cultivation.json").getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
        assertEquals("myvillage:planned_v11",preset.get("type").getAsString());
        assertEquals(json("myvillage:worldplan/frozen_v11.json"),JsonParser.parseString(FrozenRulesEncoding.decode(preset.get("rules").getAsString())));
        for(var bundle:rules().capitals.bundles) {
            CapitalGameBundle.requireHash(CapitalGameBundle.readResource(bundle.terrainResource,16*1024*1024),bundle.terrainHash,bundle.terrainResource);
            for(var tile:bundle.tiles)CapitalGameBundle.requireHash(CapitalGameBundle.readResource(CapitalGameBundle.templateFile(tile.template()),8*1024*1024),tile.sha256(),tile.template());
        }
    }

    @Test void theLargerManorsRemainPlaceableAndRoundTripWithoutMovingCities() {
        var rules=rules();var plan=new WorldPlan(20261031L,rules);
        var restored=new WorldPlan(20261031L,new WorldPlanRules(FrozenRulesEncoding.decode(FrozenRulesEncoding.encode(rules.json()))));
        assertEquals(8,plan.cities().size());assertEquals(plan.manifest(),restored.manifest());assertEquals(plan.fingerprint(),restored.fingerprint());
        assertEquals(Set.of("beiyuan","nanjiang","lingyue","ximo","donghai","donghuang","moyu","zhongzhou"),Set.copyOf(plan.cities().stream().map(c->c.regionId()).toList()));
    }
}
