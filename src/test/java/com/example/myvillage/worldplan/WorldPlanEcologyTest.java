package com.example.myvillage.worldplan;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldPlanEcologyTest {
    static WorldPlanRules rules(String resource) throws Exception {
        try(var in=WorldPlanEcologyTest.class.getResourceAsStream(resource)) {
            assertNotNull(in);return new WorldPlanRules(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    @Test void v1MatchesReleasedJarGoldensIncludingExtremeSeedAndOuterBiomes() throws Exception {
        // Captured from the verified 0.59.0 delivery, SHA-256
        // 5d8ffe8e7b3ea8d6213d3be930e9c16beb3b3bf39b993531f0a9c75fc715d6ae.
        var legacy=rules(WorldPlanRules.LEGACY_RESOURCE);assertNull(legacy.ecology);
        try(var in=getClass().getResourceAsStream("/worldplan/v1-goldens.json")) {
            for(var item:JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonArray()) {
                var o=item.getAsJsonObject();var p=new WorldPlan(Long.parseLong(o.get("seed").getAsString()),legacy);
                assertEquals(o.get("plan_sha256").getAsString(),p.fingerprint());
                for(var sample:o.getAsJsonArray("samples")) {
                    var s=sample.getAsJsonObject();
                    assertEquals(s.get("context"),WorldPlanRules.JSON.toJsonTree(p.sample(s.get("x").getAsInt(),s.get("z").getAsInt())));
                }
            }
        }
    }
    @Test void newEcologyDoesNotMoveTerrainCitiesSpawnOrRegionIdentity() throws Exception {
        var oldRules=rules(WorldPlanRules.LEGACY_RESOURCE);var current=rules(WorldPlanRules.RESOURCE);
        for(long seed:new long[]{20261017,0,Long.MIN_VALUE}) {
            var a=new WorldPlan(seed,oldRules);var b=new WorldPlan(seed,current);
            assertEquals(a.city(),b.city());assertEquals(a.spawn(),b.spawn());
            for(int x=-4600;x<=4600;x+=230)for(int z=-4600;z<=4600;z+=230) {
                var old=a.sample(x,z);var now=b.sample(x,z);
                assertEquals(old.surfaceY(),now.surfaceY());assertEquals(old.regionId(),now.regionId());
                assertEquals(old.qi(),now.qi());assertEquals(old.danger(),now.danger());assertEquals(old.tier(),now.tier());
            }
        }
    }
    @Test void reportedJungleSnowBoundaryHasTemperateBufferInsteadOfSpeckles() throws Exception {
        var p=new WorldPlan(20261017,rules(WorldPlanRules.RESOURCE));
        Set<String> temperate=Set.of("minecraft:forest","minecraft:dark_forest","minecraft:meadow","minecraft:taiga","minecraft:savanna");
        for(int x=3990;x<=4050;x+=4)for(int z=-48;z<=48;z+=4)
            assertTrue(temperate.contains(p.sample(x,z).biome()),"buffer at "+x+","+z);
        for(int z=-800;z<800;z+=4) {
            var a=p.climate(4020,z);var b=p.climate(4020,z+4);
            assertTrue(Math.abs(a.temperature()-b.temperature())<.025,"continuous temperature");
            assertTrue(Math.abs(a.moisture()-b.moisture())<.025,"continuous moisture");
        }
    }
    @Test void climateRemainsContinuousAtCoreAndNegativeOuterCellBoundaries() throws Exception {
        var p=new WorldPlan(20261017,rules(WorldPlanRules.RESOURCE));
        for(int x:new int[]{-29990000,-4800,-4320,-4000,-3680,-2400,0,2400,3680,4000,4320,4800,29990000})
            for(int z:new int[]{-4000,0,4000,999999}) {
                var a=p.climate(x-.001,z);var b=p.climate(x+.001,z);
                assertTrue(Math.abs(a.temperature()-b.temperature())<.001);
                assertTrue(Math.abs(a.moisture()-b.moisture())<.001);
            }
    }
    @Test void badClimateCannotSilentlyChangeFrozenWorld() throws Exception {
        var base=JsonParser.parseString(rules(WorldPlanRules.RESOURCE).json()).getAsJsonObject();
        var wide=base.deepCopy();wide.getAsJsonObject("ecology").addProperty("blend",100000);
        assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(wide.toString()));
        var missing=base.deepCopy();missing.remove("ecology");
        assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(missing.toString()));
        var reversed=base.deepCopy();reversed.getAsJsonObject("ecology").getAsJsonArray("bands").get(1).getAsJsonObject().addProperty("max_temperature",.1);
        assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(reversed.toString()));
    }
    @Test void cityAuditOnlyClassifiesCoveredGrassDecayAsNatural() {
        assertTrue(CityAuditPolicy.naturalChange("minecraft:grass_block","minecraft:dirt",true));
        assertFalse(CityAuditPolicy.naturalChange("minecraft:grass_block","minecraft:dirt",false),"uncovered player excavation remains a difference");
        for(String changed:List.of("minecraft:air","minecraft:gravel","minecraft:gold_block","minecraft:coarse_dirt"))
            assertFalse(CityAuditPolicy.naturalChange("minecraft:grass_block",changed,true));
        assertFalse(CityAuditPolicy.naturalChange("minecraft:stone_bricks","minecraft:air",true));
        assertFalse(CityAuditPolicy.naturalChange("minecraft:stone_bricks","minecraft:dirt",true));
    }
}
