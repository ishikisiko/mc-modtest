package com.example.myvillage.worldplan;

import com.example.myvillage.region.runtime.SectSite;
import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlannedSectTest {
    static WorldPlanRules rules() throws Exception {return WorldPlanTest.rules();}
    @Test void fullLotsAreReservedBeforeAnyChunksForManySeeds() throws Exception {
        var r=rules();
        for(long seed:new long[]{0,1,-1,42,20261017,20261102,Long.MIN_VALUE,Long.MAX_VALUE}){
            var plan=new WorldPlan(seed,r);
            assertEquals(7*r.sects.slots,plan.sectSites().size());
            assertEquals(plan.sectSites(),plan.graph().geometry().sectSites());
            assertEquals(plan.manifest(),new WorldPlan(seed,r).manifest());
            for(var s:plan.sectSites()){
                assertEquals(0,Math.floorMod(s.x(),16));assertEquals(0,Math.floorMod(s.z(),16));
                assertEquals(s.baseY()+r.sects.entryY,s.gateY());
                assertTrue(s.baseY()>r.sects.minY&&s.baseY()+r.sects.maxBlockY<r.sects.maxY);
                int m=s.margin()+r.sects.buffer;
                assertFalse(plan.intersectsCity(s.x()-m,s.z()-m,s.x()+s.width()+m-1,s.z()+s.depth()+m-1));
                assertFalse(plan.intersectsSettlements(s.x()-m,s.z()-m,s.x()+s.width()+m-1,s.z()+s.depth()+m-1));
                for(int z=s.z()-s.margin();z<s.z()+s.depth()+s.margin();z+=8)for(int x=s.x()-s.margin();x<s.x()+s.width()+s.margin();x+=8){
                    assertEquals(s.regionId(),plan.coreRegionAt(x,z).orElseThrow().id());
                    assertTrue(plan.reserved(x,-63,z));assertTrue(plan.reserved(x,319,z));
                    assertTrue(plan.graph().geometry().reserved(x,z,0));
                }
                for(var other:plan.sectSites())if(s!=other)assertFalse(s.intersects(other.x()-other.margin(),other.z()-other.margin(),other.x()+other.width()+other.margin()-1,other.z()+other.depth()+other.margin()-1,0));
                assertFalse(plan.allowsStructure("myvillage:sect",s.gateX(),s.gateZ()));
            }
        }
    }
    @Test void terrainMatchesCompiledHeightFieldAndDoesNotDependOnVisitOrder()throws Exception{
        var plan=new WorldPlan(-20261102,rules());var r=plan.rules().sects;
        var sampled=new ArrayList<int[]>();var heights=new ArrayList<Integer>();
        for(var s:plan.sectSites())for(int z=0;z<s.depth();z+=13)for(int x=0;x<s.width();x+=17){
            assertEquals(s.baseY()+r.ground(x,z)+1,plan.surfaceY(s.x()+x,s.z()+z));
            sampled.add(new int[]{s.x()+x,s.z()+z});heights.add(plan.surfaceY(s.x()+x,s.z()+z));
        }
        for(int i=sampled.size()-1;i>=0;i--){var p=sampled.get(i);assertEquals(heights.get(i).intValue(),plan.surfaceY(p[0],p[1]));}
        for(var s:plan.sectSites())assertEquals(plan.naturalSurfaceY(s.x()-s.margin(),s.z()),plan.surfaceY(s.x()-s.margin(),s.z()));
    }
    @Test void exhaustionFailsTheWholePlanRatherThanFallingBackToAnUnsafeGate()throws Exception{
        var json=JsonParser.parseString(rules().json()).getAsJsonObject();
        var r=json.getAsJsonObject("sects");r.addProperty("placement_radius",512);r.addProperty("slots_per_region",32);r.addProperty("placement_attempts",16);
        assertThrows(IllegalArgumentException.class,()->new WorldPlan(0,new WorldPlanRules(json.toString())));
        json.remove("sects");assertThrows(RuntimeException.class,()->new WorldPlanRules(json.toString()));
    }
}
