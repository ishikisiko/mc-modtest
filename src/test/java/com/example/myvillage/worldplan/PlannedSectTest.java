package com.example.myvillage.worldplan;

import com.example.myvillage.region.runtime.SectSite;
import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlannedSectTest {
    static WorldPlanRules rules() throws Exception {return WorldPlanTest.rules();}
    @Test void layoutCyclesRemainCompleteAcrossIntegerBoundaries()throws Exception{
        var r=rules().sects;
        for(int region:new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE,0,4})for(int salt:new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE-4}){
            Set<String> ids=new HashSet<>();for(int i=0;i<r.slots;i++)ids.add(r.choose(region,salt,i).id);
            assertEquals(r.layouts.size(),ids.size());
        }
    }
    @Test void fullLotsAreReservedBeforeAnyChunksForManySeeds() throws Exception {
        var r=rules();
        for(long seed:new long[]{0,1,-1,42,20261017,20261102,Long.MIN_VALUE,Long.MAX_VALUE}){
            var plan=new WorldPlan(seed,r);
            assertEquals(7*r.sects.slots,plan.sectSites().size());
            assertEquals(plan.sectSites(),plan.graph().geometry().sectSites());
            assertEquals(plan.manifest(),new WorldPlan(seed,r).manifest());
            for(var s:plan.sectSites()){
                var layout=r.sects.layout(s.blueprintId());
                assertEquals(0,Math.floorMod(s.x(),16));assertEquals(0,Math.floorMod(s.z(),16));
                assertEquals(s.baseY()+layout.entryY,s.gateY());
                assertTrue(s.baseY()>r.sects.minY&&s.baseY()+layout.maxBlockY<r.sects.maxY);
                int m=s.margin()+r.sects.buffer;
                assertFalse(plan.intersectsCity(s.x()-m,s.z()-m,s.x()+s.width()+m-1,s.z()+s.depth()+m-1));
                assertFalse(plan.intersectsSettlements(s.x()-m,s.z()-m,s.x()+s.width()+m-1,s.z()+s.depth()+m-1));
                for(int z=s.z()-s.margin();z<s.z()+s.depth()+s.margin();z+=8)for(int x=s.x()-s.margin();x<s.x()+s.width()+s.margin();x+=8){
                    assertEquals(s.regionId(),plan.coreRegionAt(x,z).orElseThrow().id());
                    assertEquals(layout.terrain.weight(x-s.x(),z-s.z())>0,plan.reserved(x,-63,z));
                    assertEquals(layout.terrain.weight(x-s.x(),z-s.z())>0,plan.reserved(x,319,z));
                    assertTrue(plan.graph().geometry().reserved(x,z,0));
                }
                for(var other:plan.sectSites())if(s!=other)assertFalse(s.intersects(other.x()-other.margin(),other.z()-other.margin(),other.x()+other.width()+other.margin()-1,other.z()+other.depth()+other.margin()-1,0));
                assertFalse(plan.allowsStructure("myvillage:sect",s.gateX(),s.gateZ()));
            }
        }
    }
    @Test void terrainMatchesCompiledHeightFieldAndDoesNotDependOnVisitOrder()throws Exception{
        var plan=new WorldPlan(-20261102,rules());var r=plan.rules().sects;
        var sampled=new ArrayList<int[]>();var heights=new ArrayList<Integer>();int preservedSeafloor=0;
        for(var s:plan.sectSites())for(int z=0;z<s.depth();z+=13)for(int x=0;x<s.width();x+=17){
            var terrain=r.layout(s.blueprintId()).terrain;int wx=s.x()+x,wz=s.z()+z;
            // 零施工权重必须保留真实海底；自然水面只供非零权重的接坡使用。
            int expected=terrain.weight(x,z)==0?plan.naturalTerrainY(wx,wz):terrain.firstAir(s.baseY(),x,z,plan.naturalSurfaceY(wx,wz));
            assertEquals(expected,plan.surfaceY(wx,wz),s.id()+" @ "+wx+","+wz);
            if(terrain.weight(x,z)==0&&plan.naturalTerrainY(wx,wz)<plan.rules().seaLevel){assertTrue(plan.surfaceY(wx,wz)<plan.rules().seaLevel);preservedSeafloor++;}
            sampled.add(new int[]{s.x()+x,s.z()+z});heights.add(plan.surfaceY(s.x()+x,s.z()+z));
        }
        for(int i=sampled.size()-1;i>=0;i--){var p=sampled.get(i);assertEquals(heights.get(i).intValue(),plan.surfaceY(p[0],p[1]));}
        assertTrue(preservedSeafloor>0,"固定种子必须覆盖未施工海底的保留断言");
        for(var s:plan.sectSites())assertEquals(plan.naturalTerrainY(s.x()-s.margin(),s.z()),plan.surfaceY(s.x()-s.margin(),s.z()));
    }
    @Test void exhaustionFailsTheWholePlanRatherThanFallingBackToAnUnsafeGate()throws Exception{
        var json=JsonParser.parseString(rules().json()).getAsJsonObject();
        var r=json.getAsJsonObject("sects");r.addProperty("placement_radius",512);r.addProperty("slots_per_region",32);r.addProperty("placement_attempts",16);
        assertThrows(IllegalArgumentException.class,()->new WorldPlan(0,new WorldPlanRules(json.toString())));
        json.remove("sects");assertThrows(RuntimeException.class,()->new WorldPlanRules(json.toString()));
    }
}
