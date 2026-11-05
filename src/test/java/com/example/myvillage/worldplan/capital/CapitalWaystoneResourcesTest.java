package com.example.myvillage.worldplan.capital;

import com.example.myvillage.worldplan.WorldPlanRules;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapitalWaystoneResourcesTest {
    @Test void allEightFrozenCitiesCarryTheAcceptedStationCounts() {
        var raw=CapitalGameBundle.readResource("myvillage:worldplan/frozen_v11.json",4*1024*1024);
        var rules=new WorldPlanRules(new String(raw,StandardCharsets.UTF_8));
        var expected=Map.of("zhongzhou",6,"lingyue",3,"donghuang",3,"donghai",3,"ximo",2,"nanjiang",3,"beiyuan",2,"moyu",3);
        var ids=new HashSet<String>();int total=0;
        assertEquals(expected.keySet(),new HashSet<>(rules.capitals.bundles.stream().map(b->b.regionId).toList()));
        for(var city:rules.capitals.bundles) {
            assertEquals(expected.get(city.regionId).intValue(),city.waystones.size());
            var manifest=JsonParser.parseString(new String(CapitalGameBundle.readResource(city.resource,8*1024*1024),StandardCharsets.UTF_8)).getAsJsonObject();
            city.requireManifest(manifest);
            for(var station:city.waystones) {
                assertTrue(ids.add(station.id()));total++;
                assertTrue(city.tile(station.x()/16*16,station.z()/16*16).isPresent(),station.id());
            }
            var modified=manifest.deepCopy();modified.getAsJsonArray("waystones").get(0).getAsJsonObject().addProperty("name","被修改的石碑");
            assertThrows(IllegalStateException.class,()->city.requireManifest(modified));
        }
        assertEquals(25,total);
    }
}
