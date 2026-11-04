package com.example.myvillage.worldplan;

import com.google.gson.JsonParser;
import java.util.Comparator;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StarterCapitalSpawnTest {
    @Test void seaCityRemainsPresentButNeverBecomesTheInitialCity()throws Exception{
        var rules=WorldPlanTest.rules();
        assertEquals(Set.of("donghai"),rules.spawnExcludedRegions);
        for(long seed:new long[]{0,1,-1,42,20261105,20261029,Long.MIN_VALUE,Long.MAX_VALUE}){
            var world=new WorldPlan(seed,rules);
            var expected=world.regions().stream().filter(r->!r.profile().role().equals("walled")&&!rules.spawnExcludedRegions.contains(r.id()))
                .min(Comparator.comparingInt(WorldPlan.Region::tier).thenComparing(WorldPlan.Region::id)).orElseThrow();
            assertEquals(expected.id(),world.spawn().regionId(),"seed="+seed);
            assertNotEquals("donghai",world.spawn().regionId());assertEquals(8,world.cities().size());
            assertTrue(world.capitalOf("donghai").isPresent());
            var entry=world.capitalOf(expected.id()).orElseThrow().entrance();
            assertEquals(entry,new WorldPlan.Position(world.spawn().x(),world.spawn().y(),world.spawn().z()));
            var restored=new WorldPlan(seed,new WorldPlanRules(FrozenRulesEncoding.decode(FrozenRulesEncoding.encode(rules.json()))));
            assertEquals(world.spawn(),restored.spawn());assertEquals(world.fingerprint(),restored.fingerprint());
        }
    }
    @Test void exclusionRulesRejectUnknownDuplicateAndExhaustedCandidates()throws Exception{
        var source=WorldPlanTest.rules().json();
        for(String values:new String[]{"[\"unknown\"]","[\"donghai\",\"donghai\"]","[1]",
                "[\"beiyuan\",\"donghai\",\"donghuang\",\"lingyue\",\"nanjiang\",\"ximo\",\"zhongzhou\"]"}){
            var json=JsonParser.parseString(source).getAsJsonObject();
            json.getAsJsonObject("spawn").add("excluded_regions",JsonParser.parseString(values));
            assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(json.toString()),values);
        }
    }
}
