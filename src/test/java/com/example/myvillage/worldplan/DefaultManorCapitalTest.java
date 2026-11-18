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
    private static WorldPlanRules rules() { return new WorldPlanRules(json("yuanxian:worldplan/frozen_v11.json").toString()); }

    @Test void landCityAnchorsDoNotFollowTheSeabedBelowTheWaterSurface() throws Exception {
        var rules=rules();
        for(long seed:new long[]{6166082384136082383L,-5039563940284781517L,2211984914834465989L}) {
            var plan=new WorldPlan(seed,rules);
            for(var city:plan.cities())if(city.bundle().verticalMode.equals("terrain_relative"))
                assertTrue(city.originY()+city.bundle().anchorY>=rules.seaLevel,city.regionId()+" seed="+seed);
            assertEquals(8,plan.cities().size());
        }
        var natural=WorldPlan.class.getDeclaredMethod("naturalValues",double.class,double.class);
        natural.setAccessible(true);
        int lowSites=0;
        for(long seed:new long[]{6166082384136082383L,5L,-5039563940284781517L,2211984914834465989L}){
            var plan=new WorldPlan(seed,rules);
            for(var city:plan.cities()){
                if(!city.bundle().verticalMode.equals("terrain_relative"))continue;
                var values=natural.invoke(plan,city.x()+city.bundle().width/2.0,city.z()+city.bundle().depth/2.0);
                var height=values.getClass().getDeclaredMethod("height");height.setAccessible(true);
                if((double)height.invoke(values)<rules.seaLevel){
                    assertEquals(rules.seaLevel,city.originY()+city.bundle().anchorY,city.regionId());
                    lowSites++;
                }
            }
        }
        assertTrue(lowSites>0,"扩城之后仍须有自然地面低于海面的夹具");
    }

    @Test void currentDefaultReferencesSevenFormalManorsAndTheExistingZhongzhou() {
        var rules=rules();assertEquals(8,rules.capitals.bundles.size());assertEquals(192,rules.capitals.lifePlaceLimit);
        var houses=Map.of("beiyuan",58,"nanjiang",59,"lingyue",57,"ximo",56,"donghai",60,"donghuang",64,"moyu",53);
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
            assertEquals(1,bundle.housingPlots.size());
            var homes=city.getAsJsonArray("npc_housing");
            assertEquals(homes.size(),bundle.npcHouseholds.size());
            assertEquals(switch(bundle.regionId){case "ximo"->3;case "donghai","nanjiang"->5;default->4;}, homes.size());
            if(bundle.regionId.equals("donghai"))assertEquals(384,bundle.width);
            if(bundle.regionId.equals("moyu"))assertEquals(128,bundle.height);
        }
        assertEquals(407,total);assertEquals(73,services);
    }

    @Test void theDefaultPresetAndAllRuntimeResourcesHaveTheSameFrozenIdentity() throws Exception {
        assertEquals(1,Collections.list(getClass().getClassLoader().getResources("data/yuanxian/worldgen/world_preset/cultivation.json")).size());
        var preset=json("yuanxian:worldgen/world_preset/cultivation.json").getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
        assertEquals("yuanxian:planned_v11",preset.get("type").getAsString());
        assertEquals(json("yuanxian:worldplan/frozen_v11.json"),JsonParser.parseString(FrozenRulesEncoding.decode(preset.get("rules").getAsString())));
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
