package com.example.myvillage.worldplan;

import com.example.myvillage.region.runtime.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldPlanTest {
    static WorldPlanRules rules()throws IOException{
        try(var in=WorldPlanTest.class.getResourceAsStream(WorldPlanRules.RESOURCE)){
            assertNotNull(in,"generated frozen rules are packaged");return new WorldPlanRules(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    @Test void deterministicFrozenPlansAndExtremeSeeds()throws Exception{
        var r=rules();Set<String> hashes=new HashSet<>();
        for(long seed:new long[]{0,1,-1,20261017,Long.MIN_VALUE,Long.MAX_VALUE}){
            var a=new WorldPlan(seed,r);var b=new WorldPlan(seed,new WorldPlanRules(r.json()));
            assertEquals(a.manifest(),b.manifest());assertTrue(hashes.add(a.fingerprint()));assertEquals(r.fingerprint(),b.rules().fingerprint());
        }
        var json=JsonParser.parseString(r.json()).getAsJsonObject();json.addProperty("ridge_lift",r.ridgeLift+1);
        assertNotEquals(new WorldPlan(1,r).fingerprint(),new WorldPlan(1,new WorldPlanRules(json.toString())).fingerprint());
    }
    @Test void manySeedsHavePhysicalConnectivityLegalCityAndSafeStart()throws Exception{
        var r=rules();Set<String> arrangements=new HashSet<>();
        for(long seed=-10;seed<500;seed++){
            var p=new WorldPlan(seed,r);assertTrue(p.regions().size()>=5&&p.regions().size()<=7);
            var normal=p.regions().stream().filter(q->!q.profile().role().equals("walled")).toList();
            var start=p.regions().stream().filter(q->q.profile().role().equals("anchor")).findFirst().orElseThrow();Set<String> seen=new HashSet<>(Set.of(start.id()));boolean changed;
            do{changed=false;for(var e:p.borders())if(e.passable()){
                if(seen.contains(e.a()))changed|=seen.add(e.b());if(seen.contains(e.b()))changed|=seen.add(e.a());
                var a=p.regions().stream().filter(q->q.id().equals(e.a())).findFirst().orElseThrow();var b=p.regions().stream().filter(q->q.id().equals(e.b())).findFirst().orElseThrow();
                assertTrue(Math.abs(a.tier()-b.tier())<=r.stepMax);
            }}while(changed);
            assertTrue(seen.containsAll(normal.stream().map(WorldPlan.Region::id).toList()),"connected seed "+seed);
            assertTrue(p.borders().stream().filter(WorldPlan.Border::gate).count()<=1);
            assertEquals(0,Math.floorMod(p.city().x(),16));assertEquals(0,Math.floorMod(p.city().z(),16));assertTrue(p.graph().geometry().reserved(p.city().x(),p.city().z(),0));
            var spawn=p.spawn();assertEquals(spawn.regionId(),RegionQueries.regionAt(p.graph(),spawn.x(),spawn.z()).orElseThrow());
            assertEquals(normal.stream().mapToInt(WorldPlan.Region::tier).min().orElseThrow(),p.sample(spawn.x(),spawn.z()).tier());
            assertTrue(spawn.y()>r.seaLevel);assertEquals(spawn.y(),p.surfaceY(spawn.x(),spawn.z()));arrangements.add(start.x()+","+start.z());
            for(int x=-4200;x<=4200;x+=700)for(int z=-4200;z<=4200;z+=700){
                var c=p.sample(x,z);var id=RegionQueries.regionAt(p.graph(),x,z);assertEquals(c.core(),id.isPresent());if(c.core())assertEquals(c.regionId(),id.orElseThrow());
            }
        }
        assertTrue(arrangements.size()>450,"centres do not repeat a fixed ring");
    }
    @Test void noGeneratedOrderOrCoreOuterChunkSeams()throws Exception{
        var p=new WorldPlan(20261017,rules());List<int[]> coordinates=new ArrayList<>();
        for(int x:new int[]{-29990000,-4800,-4000,-2400,-16,0,16,2400,4000,4800,29990000})for(int z:new int[]{-4000,0,4000,999999})coordinates.add(new int[]{x,z});
        Map<String,WorldPlan.Context> expected=new HashMap<>();for(int[] pos:coordinates)expected.put(Arrays.toString(pos),p.sample(pos[0],pos[1]));Collections.reverse(coordinates);
        for(int[] pos:coordinates){assertEquals(expected.get(Arrays.toString(pos)),p.sample(pos[0],pos[1]));assertTrue(Math.abs(p.height(pos[0]-.0001,pos[1])-p.height(pos[0]+.0001,pos[1]))<.02,"continuous at "+Arrays.toString(pos));}
        assertFalse(p.sample(100000,100000).core());assertEquals(p.sample(-2401,40000),new WorldPlan(20261017,rules()).sample(-2401,40000));
    }
    @Test void exactOneOwnerPerCityChunkNoBoundsLeak()throws Exception{
        var p=new WorldPlan(-42,rules());var city=p.city();int count=0;
        for(int cx=(city.x()>>4)-2;cx<((city.x()+city.width())>>4)+2;cx++)for(int cz=(city.z()>>4)-2;cz<((city.z()+city.depth())>>4)+2;cz++){
            var t=p.cityTile(cx,cz);assertEquals(city.contains(cx*16,cz*16,0),t.isPresent());
            if(t.isPresent()){count++;assertEquals(cx*16,city.x()+t.get().x());assertEquals(cz*16,city.z()+t.get().z());}
        }
        assertEquals(city.width()*city.depth()/256,count);assertEquals(count,p.rules().city.tiles().size());
    }
    @Test void invalidFrozenRulesFailInsteadOfMovingOldWorld()throws Exception{
        String source=rules().json();
        for(String field:new String[]{"warp_amplitude","outer_jitter","region_count"}){var o=JsonParser.parseString(source).getAsJsonObject();o.addProperty(field,100000);assertThrows(RuntimeException.class,()->new WorldPlanRules(o.toString()));}
        var bad=JsonParser.parseString(source).getAsJsonObject();bad.addProperty("algorithm","myvillage:unknown_v9");assertThrows(RuntimeException.class,()->new WorldPlanRules(bad.toString()));
        var o=JsonParser.parseString(source).getAsJsonObject();o.getAsJsonObject("city").getAsJsonObject("layout").getAsJsonArray("tiles").remove(0);
        String incomplete=o.toString();assertThrows(RuntimeException.class,()->new WorldPlanRules(incomplete));
    }
}
