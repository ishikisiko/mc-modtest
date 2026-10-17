package com.example.myvillage.worldplan;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldPlanEcotoneTest {
    static WorldPlanRules rules(String resource)throws Exception{return WorldPlanEcologyTest.rules(resource);}
    @Test void v2ReleasedSaveSamplesRemainUnchanged()throws Exception{
        // 0.59.1 release jar 2325ba2c7dd22424bd07901afb7901cbd8e0aeec60a9a20e10fc1ab36376161f.
        var r=rules(WorldPlanRules.V2_RESOURCE);
        try(var in=getClass().getResourceAsStream("/worldplan/v2-goldens.json")){
            for(var item:JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonArray()){
                var o=item.getAsJsonObject();var p=new WorldPlan(Long.parseLong(o.get("seed").getAsString()),r);
                assertEquals(o.get("plan_sha256").getAsString(),p.fingerprint());
                for(var sample:o.getAsJsonArray("samples")){
                    var s=sample.getAsJsonObject();assertEquals(s.get("context"),WorldPlanRules.JSON.toJsonTree(p.sample(s.get("x").getAsInt(),s.get("z").getAsInt())));
                }
            }
        }
    }
    @Test void halfPlaneTransitionCannotConsumeEitherInterior()throws Exception{
        var e=rules(WorldPlanRules.RESOURCE).ecotone;
        for(int x=-128;x<=128;x++){
            int at=x;String original=x<0?"minecraft:bamboo_jungle":"minecraft:grove";
            String result=e.apply(x,0,original,(px,pz)->{
                assertTrue(StrictMath.hypot(px-at,pz)<=e.radius);return px<0?"minecraft:bamboo_jungle":"minecraft:grove";
            });
            if(x < -e.radius || x>=e.radius)assertEquals(original,result);
            else assertEquals(x<0?"minecraft:forest":"minecraft:taiga",result);
        }
        assertEquals("minecraft:grove",e.apply(0,0,"minecraft:grove",(x,z)->"minecraft:snowy_plains"),"same-climate original variants must stay intact");
    }
    @Test void bothOriginalJungleAndOriginalSnowSurviveBesideReportedBoundary()throws Exception{
        var p=new WorldPlan(20261017,rules(WorldPlanRules.RESOURCE));
        assertEquals("minecraft:bamboo_jungle",p.sample(4064,-128).biome());
        assertEquals("minecraft:sparse_jungle",p.sample(4064,-80).biome());
        assertEquals("minecraft:forest",p.sample(4064,-40).biome());
        assertEquals("minecraft:taiga",p.sample(4064,-8).biome());
        assertEquals("minecraft:grove",p.sample(4064,32).biome());
        assertEquals("minecraft:grove",p.sample(4064,128).biome());
    }
    @Test void originalSnowTypeRetainsAtLeastNinetyPercentOfReproductionArea()throws Exception{
        var p=new WorldPlan(20261017,rules(WorldPlanRules.RESOURCE));int original=0,retained=0;
        Set<String> snow=Set.of("minecraft:grove","minecraft:snowy_plains");
        for(int z=-256;z<=1600;z+=16)for(int x=3800;x<=4600;x+=16){
            String before=p.originalBiomeAt(x,z);if(snow.contains(before)){original++;if(before.equals(p.sample(x,z).biome()))retained++;}
        }
        assertTrue(original>1000);assertTrue(retained>=original*.9,"original snow retained "+retained+"/"+original);
    }
    @Test void sourceGeographyAndSpawnSafetyStayStableAcrossSeeds()throws Exception{
        var old=rules(WorldPlanRules.LEGACY_RESOURCE);var current=rules(WorldPlanRules.RESOURCE);
        for(long seed:new long[]{20261017,0,-42,Long.MIN_VALUE,Long.MAX_VALUE}){
            var a=new WorldPlan(seed,old);var b=new WorldPlan(seed,current);assertEquals(a.city(),b.city());assertEquals(a.spawn(),b.spawn());
            for(int x:new int[]{-29990000,-4320,-4000,-2400,0,2400,4000,4320,29990000})for(int z:new int[]{-4000,0,4000,100000}){
                var before=a.sample(x,z);var after=b.sample(x,z);
                assertEquals(before.biome(),b.originalBiomeAt(x,z));assertEquals(before.regionId(),after.regionId());assertEquals(before.surfaceY(),after.surfaceY());assertEquals(before.qi(),after.qi());assertEquals(before.danger(),after.danger());
                assertEquals(after,new WorldPlan(seed,current).sample(x,z));
            }
            var s=b.spawn();assertTrue(current.clearSpawnCanopy());assertTrue(b.reserved(s.x(),s.y()+80,s.z()));
        }
    }
    @Test void invalidBandCannotSilentlyExpandAcrossTheRegion()throws Exception{
        var r=JsonParser.parseString(rules(WorldPlanRules.RESOURCE).json()).getAsJsonObject();
        for(double value:new double[]{0,65,720,32.5}){
            var bad=r.deepCopy();bad.getAsJsonObject("ecotone").addProperty("radius",value);assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(bad.toString()));
        }
        var missing=r.deepCopy();missing.getAsJsonObject("ecotone").getAsJsonObject("groups").remove("cold");assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(missing.toString()));
    }
}
