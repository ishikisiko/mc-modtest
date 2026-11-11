package com.example.myvillage.worldplan;

import com.example.myvillage.region.runtime.*;
import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FixedRegionProgressionTest {
    private static final List<String> ORDER=List.of("ximo","donghai","nanjiang","beiyuan","donghuang","lingyue","moyu","zhongzhou");

    @Test void fixedProgressionHasPhysicalBoundariesGatesAndMatchingQueriesAcrossSeeds()throws Exception {
        var rules=WorldPlanTest.rules();assertEquals(ORDER,rules.progressionOrder);
        Set<String> locations=new HashSet<>();
        List<Long> seeds=new ArrayList<>(List.of(Long.MIN_VALUE,Long.MAX_VALUE,20261112L,20261029L));
        for(long seed=-32;seed<32;seed++)seeds.add(seed);
        for(long seed:seeds){
            var world=new WorldPlan(seed,rules);var graph=world.graph();
            assertEquals(ORDER,graph.progressionOrder());assertEquals(8,world.cities().size());
            assertEquals("ximo",world.spawn().regionId(),"seed="+seed);
            assertEquals(List.of(8,9,10,11,12,14,16,20),RungLadder.ladder(graph));
            assertEquals(2,world.borders().stream().filter(WorldPlan.Border::gate).count());
            assertTrue(world.sectSites().stream().noneMatch(s->s.regionId().equals("moyu")));
            for(int i=0;i<ORDER.size();i++){
                String id=ORDER.get(i);var region=world.regions().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow();
                var point=world.geometry().unwarp(region.x(),region.z());int x=(int)Math.round(point[0]),z=(int)Math.round(point[1]);
                assertEquals(Optional.of(id),RungLadder.currentRegion(graph,x,z));
                assertEquals(Optional.of(rules.progressionTier(id)),RungLadder.currentRung(graph,x,z));
                assertEquals(i==ORDER.size()-1?Set.of():Set.of(ORDER.get(i+1)),RungLadder.nextRungRegions(graph,x,z));
                if(i==0)continue;
                String before=ORDER.get(i-1);
                var boundary=world.borders().stream().filter(b->Set.of(b.a(),b.b()).equals(Set.of(id,before))).findFirst().orElseThrow();
                assertTrue(boundary.passable(),"seed="+seed+" "+before+" / "+id);
                assertTrue(Math.hypot(boundary.q().x()-boundary.p().x(),boundary.q().z()-boundary.p().z())>1);
                assertEquals(id.equals("moyu")||before.equals("moyu"),boundary.gate());
                // The boundary midpoint must separate these actual region cells on its two sides.
                double mx=(boundary.p().x()+boundary.q().x())/2,mz=(boundary.p().z()+boundary.q().z())/2;
                double dx=region.x()-mx,dz=region.z()-mz,n=Math.hypot(dx,dz);
                Set<String> sides=new HashSet<>();
                for(int sign:new int[]{-1,1}){var p=world.geometry().unwarp(mx+sign*dx/n*8,mz+sign*dz/n*8);
                    sides.add(RegionQueries.regionAt(graph,(int)Math.round(p[0]),(int)Math.round(p[1])).orElseThrow());}
                assertEquals(Set.of(id,before),sides);
            }
            for(var b:world.borders())if(b.a().equals("moyu")||b.b().equals("moyu"))assertEquals(b.gate(),b.passable());
            var first=graph.regionById("ximo");locations.add(first.posX()+","+first.posZ());
        }
        assertEquals(seeds.size(),locations.size(),"seeds still change geographic placement");
    }

    @Test void frozenRulesRoundTripPreservesOrderPositionsAndBoundaries()throws Exception {
        var rules=WorldPlanTest.rules();var encoded=FrozenRulesEncoding.encode(rules.json());
        var restored=new WorldPlanRules(FrozenRulesEncoding.decode(encoded));
        var a=new WorldPlan(20261112,rules);var b=new WorldPlan(20261112,restored);
        assertEquals(ORDER,restored.progressionOrder);assertEquals(a.manifest(),b.manifest());
        assertEquals(ORDER,JsonParser.parseString(b.graph().toCanonicalJson()).getAsJsonObject().getAsJsonArray("progression_order").asList().stream().map(e->e.getAsString()).toList());
    }

    @Test void invalidProgressionsFailBeforeGeneration()throws Exception {
        var source=WorldPlanTest.rules().json();
        for(String order:List.of("[]","[\"ximo\"]","[\"ximo\",\"donghai\",\"nanjiang\",\"beiyuan\",\"donghuang\",\"lingyue\",\"moyu\",\"moyu\"]",
                "[\"missing\",\"donghai\",\"nanjiang\",\"beiyuan\",\"donghuang\",\"lingyue\",\"moyu\",\"zhongzhou\"]",
                "[\"donghai\",\"ximo\",\"nanjiang\",\"beiyuan\",\"donghuang\",\"lingyue\",\"moyu\",\"zhongzhou\"]",
                "[\"ximo\",\"donghai\",\"nanjiang\",\"beiyuan\",\"donghuang\",\"lingyue\",\"zhongzhou\",\"moyu\"]")){
            var json=JsonParser.parseString(source).getAsJsonObject();json.getAsJsonObject("region_progression").add("order",JsonParser.parseString(order));
            assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(json.toString()),order);
        }
        var json=JsonParser.parseString(source).getAsJsonObject();json.remove("region_progression");
        assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(json.toString()));
    }
}
