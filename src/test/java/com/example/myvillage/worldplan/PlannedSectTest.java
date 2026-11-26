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
        for(String tier:com.example.myvillage.sim.data.SectProgressionRules.IDS)
            for(int region:new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE,0,4})
                for(int salt:new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE-4}){
                    Set<String> ids=new HashSet<>();
                    for(int i=0;i<r.slots;i++){
                        var layout=r.choose(region,salt,i,tier);
                        assertEquals(tier,layout.tier);
                        ids.add(layout.id);
                    }
                    assertEquals(6,ids.size());
                }
    }
    @Test void fullLotsAreReservedBeforeAnyChunksForManySeeds() throws Exception {
        var r=rules();
        for(long seed:new long[]{0,1,-1,42,20261017,20261102,Long.MIN_VALUE,Long.MAX_VALUE}){
            var plan=new WorldPlan(seed,r);
            assertEquals(23,plan.sectSites().size());
            assertEquals(20,plan.builtSectSites().size());
            assertEquals(plan.sectSites(),plan.graph().geometry().sectSites());
            assertEquals(plan.manifest(),new WorldPlan(seed,r).manifest());
            for(var s:plan.sectSites()){
                var layout=r.sects.layout(s.blueprintId());
                assertEquals(r.sects.tier(plan.graph(),s.regionId()),s.tier());
                assertEquals(0,Math.floorMod(s.x(),16));assertEquals(0,Math.floorMod(s.z(),16));
                assertEquals(s.baseY()+layout.entryY,s.gateY());
                assertTrue(s.baseY()>r.sects.minY&&s.baseY()+layout.maxBlockY<r.sects.maxY);
                int m=s.margin()+r.sects.buffer;
                assertFalse(plan.intersectsCity(s.x()-m,s.z()-m,s.x()+s.width()+m-1,s.z()+s.depth()+m-1));
                assertFalse(plan.intersectsSettlements(s.x()-m,s.z()-m,s.x()+s.width()+m-1,s.z()+s.depth()+m-1));
                for(int z=s.z()-s.margin();z<s.z()+s.depth()+s.margin();z+=8)for(int x=s.x()-s.margin();x<s.x()+s.width()+s.margin();x+=8){
                    assertEquals(s.regionId(),plan.coreRegionAt(x,z).orElseThrow().id());
                    assertEquals(!s.foundingOnly()&&layout.terrain.weight(x-s.x(),z-s.z())>0,plan.reserved(x,-63,z));
                    if(layout.terrain.weight(x-s.x(),z-s.z())==0)assertFalse(plan.reserved(x,319,z));
                    assertTrue(plan.graph().geometry().reserved(x,z,0));
                }
                for(var other:plan.sectSites())if(s!=other)assertFalse(s.intersects(other.x()-other.margin(),other.z()-other.margin(),other.x()+other.width()+other.margin()-1,other.z()+other.depth()+other.margin()-1,0));
                assertFalse(plan.allowsStructure("yuanxian:sect",s.gateX(),s.gateZ()));
            }
        }
    }
    @Test void terrainMatchesCompiledHeightFieldAndDoesNotDependOnVisitOrder()throws Exception{
        var plan=new WorldPlan(-20261102,rules());var r=plan.rules().sects;
        var sampled=new ArrayList<int[]>();var heights=new ArrayList<Integer>();int preservedSeafloor=0;
        for(var s:plan.sectSites())for(int z=0;z<s.depth();z+=13)for(int x=0;x<s.width();x+=17){
            var terrain=r.layout(s.blueprintId()).terrain;int wx=s.x()+x,wz=s.z()+z;
            // 零施工权重必须保留真实海底；自然水面只供非零权重的接坡使用。
            int expected=s.foundingOnly()||!terrain.affects(x,z)?plan.naturalTerrainY(wx,wz):terrain.firstAir(s.baseY(),x,z,plan.naturalSurfaceY(wx,wz));
            assertEquals(expected,plan.surfaceY(wx,wz),s.id()+" @ "+wx+","+wz);
            if(!terrain.affects(x,z)&&plan.naturalTerrainY(wx,wz)<plan.rules().seaLevel){assertTrue(plan.surfaceY(wx,wz)<plan.rules().seaLevel);preservedSeafloor++;}
            sampled.add(new int[]{s.x()+x,s.z()+z});heights.add(plan.surfaceY(s.x()+x,s.z()+z));
        }
        for(int i=sampled.size()-1;i>=0;i--){var p=sampled.get(i);assertEquals(heights.get(i).intValue(),plan.surfaceY(p[0],p[1]));}
        assertTrue(preservedSeafloor>0,"固定种子必须覆盖未施工海底的保留断言");
        for(var s:plan.sectSites())assertEquals(plan.naturalTerrainY(s.x()-s.margin(),s.z()),plan.surfaceY(s.x()-s.margin(),s.z()));
    }
    @Test void valleyMainLeavesUnoccupiedGroundOpenToNaturalVegetation()throws Exception{
        var plan=new WorldPlan(0,rules());
        var site=plan.builtSectSites().stream().filter(s->s.blueprintId().equals("yuanxian:valley_retreat_main")).findFirst().orElseThrow();
        var terrain=plan.rules().sects.layout(site.blueprintId()).terrain;
        assertNotNull(terrain.forms());
        int open=0, protectedColumns=0;
        for(int z=0;z<site.depth();z++)for(int x=0;x<site.width();x++){
            if(terrain.weight(x,z)!=1)continue;
            int wx=site.x()+x,wz=site.z()+z;
            int ground=plan.surfaceY(wx,wz);
            if(plan.reserved(wx,ground+3,wz))protectedColumns++;else open++;
        }
        assertTrue(open>0&&protectedColumns>0);
    }
    @Test void exhaustionFailsTheWholePlanRatherThanFallingBackToAnUnsafeGate()throws Exception{
        var json=JsonParser.parseString(rules().json()).getAsJsonObject();
        var r=json.getAsJsonObject("sects");r.addProperty("placement_radius",512);r.addProperty("slots_per_region",32);r.addProperty("placement_attempts",16);
        assertThrows(IllegalArgumentException.class,()->new WorldPlan(0,new WorldPlanRules(json.toString())));
        json.remove("sects");assertThrows(RuntimeException.class,()->new WorldPlanRules(json.toString()));
    }
    @Test void foundingSiteSelectionsAreValidatedAndFrozen()throws Exception{
        var defaults=rules();
        var choices=new LinkedHashMap<>(defaults.sectOpening.foundingSites());
        choices.put("branch",new SectOpeningRules.SiteChoice("lingyue","lake_retreat"));
        choices.put("headquarters",new SectOpeningRules.SiteChoice("donghai","cloud_gate"));
        choices.put("main",new SectOpeningRules.SiteChoice("zhongzhou","valley_courts"));
        var selected=SectOpeningRules.withChoices(defaults,choices);
        var plan=new WorldPlan(0,selected);
        for(var site:plan.sectSites())if(site.foundingOnly()){
            var choice=choices.get(site.tier());
            assertEquals(choice.region(),site.regionId());
            assertEquals(choice.style(),selected.sects.layout(site.blueprintId()).family);
            assertEquals(plan.naturalTerrainY(site.gateX(),site.gateZ()),plan.surfaceY(site.gateX(),site.gateZ()));
        }
        assertEquals(plan.manifest(),new WorldPlan(0,new WorldPlanRules(
                FrozenRulesEncoding.decode(FrozenRulesEncoding.encode(selected.json())))).manifest());
        choices.put("branch",new SectOpeningRules.SiteChoice("moyu","lake_retreat"));
        var invalid=SectOpeningRules.withChoices(defaults,choices);
        assertThrows(IllegalArgumentException.class,()->new WorldPlan(0,invalid));
    }

    @Test void laterLotsCannotBlockAnExistingGatesExteriorTurningArea()throws Exception{
        var r=rules();
        for(long seed:new long[]{0,1,20261103,20261104}){
            var plan=new WorldPlan(seed,r);
            for(var site:plan.sectSites()){
                var access=r.sects.exitClearance(site);
                assertFalse(plan.intersectsCity(access.minX(),access.minZ(),access.maxX(),access.maxZ()));
                assertFalse(plan.intersectsSettlements(access.minX(),access.minZ(),access.maxX(),access.maxZ()));
                for(var other:plan.sectSites())if(site!=other){
                    var body=r.sects.footprint(other);
                    assertFalse(overlaps(access,body),"seed="+seed+" "+other.id()+" blocks "+site.id());
                    assertFalse(site.intersects(body.minX(),body.minZ(),body.maxX(),body.maxZ(),r.sects.buffer));
                }
            }
        }
    }
    private static boolean overlaps(com.example.myvillage.region.runtime.RegionGeometry.Reservation a,
                                    com.example.myvillage.region.runtime.RegionGeometry.Reservation b){
        return a.minX()<=b.maxX()&&a.maxX()>=b.minX()&&a.minZ()<=b.maxZ()&&a.maxZ()>=b.minZ();
    }
}
