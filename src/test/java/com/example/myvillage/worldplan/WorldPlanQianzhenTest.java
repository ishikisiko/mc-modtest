package com.example.myvillage.worldplan;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.regiongen.QianzhenRegionLayout;
import java.util.*;
import org.junit.jupiter.api.Test;

class WorldPlanQianzhenTest {
    @Test void actualChapterSitesHaveSeededLocationsAndSharedReservations() throws Exception {
        var rules=WorldPlanV4Test.load(WorldPlanRules.V6_RESOURCE);var r=rules.qianzhen;
        Set<Integer> rotations=new HashSet<>();Set<String> places=new HashSet<>();
        for(long seed:new long[]{0,1,-1,42,-42,20261025,Long.MIN_VALUE,Long.MAX_VALUE}){
            var plan=new WorldPlan(seed,rules);var site=plan.qianzhen();assertNotNull(site,"No Chapter 1 settlement for seed "+seed);
            assertEquals("ximo",site.host());assertTrue(plan.regions().stream().anyMatch(region->region.id().equals(site.host())));
            assertEquals(0,Math.floorMod(site.x(),16));assertEquals(0,Math.floorMod(site.z(),16));
            assertEquals(Set.of("relay_inn","apothecary","canal_gardens","old_relay","spring_array","initiation_court"),
                    plan.qianzhenBuildings().stream().map(WorldPlan.BuildingLocation::id).collect(java.util.stream.Collectors.toSet()));
            assertTrue(plan.qianzhenBuildings().stream().allMatch(b->b.name().codePoints().anyMatch(c->c>127)));
            assertTrue(plan.graph().geometry().reserved(site.x(),site.z(),0));
            assertFalse(plan.intersectsCity(site.x()-r.margin,site.z()-r.margin,site.x()+r.size+r.margin-1,site.z()+r.size+r.margin-1));
            assertTrue(plan.reserved(site.entry().x(),site.entry().y(),site.entry().z()));
            assertFalse(plan.reserved(site.entry().x(),site.groundY()-1,site.entry().z()));
            for(var bay:plan.settlements()){
                int margin=rules.settlements.margin+rules.settlements.structureBuffer;
                assertFalse(plan.intersectsQianzhen(bay.x()-margin,bay.z()-margin,bay.x()+rules.settlements.size+margin-1,bay.z()+rules.settlements.size+margin-1,false));
            }
            assertEquals(plan.manifest(),new WorldPlan(seed,new WorldPlanRules(FrozenRulesEncoding.decode(FrozenRulesEncoding.encode(rules.json())))).manifest());
            rotations.add(site.rotation());places.add(site.x()+","+site.z());
        }
        assertTrue(rotations.size()>1);assertTrue(places.size()>4);
    }
    @Test void frozenBuildingAssetIdentityMatchesAndRotationsOwnExactlyOneChunk() throws Exception {
        var rules=WorldPlanV4Test.load(WorldPlanRules.V6_RESOURCE);var layout=QianzhenRegionLayout.current();
        assertEquals(rules.qianzhen.resourceHash,layout.resourceHash);assertEquals(rules.qianzhen.layoutHash,layout.layoutHash);
        for(int rotation=0;rotation<4;rotation++)for(int x=0;x<layout.size;x+=16)for(int z=0;z<layout.size;z+=16){
            Set<String> owners=new HashSet<>();
            for(int dx:new int[]{0,15})for(int dz:new int[]{0,15}){
                var at=layout.rotate(x+dx,z+dz,rotation);owners.add(Math.floorDiv(at.x(),16)+","+Math.floorDiv(at.z(),16));
                assertEquals(new QianzhenRegionLayout.Point(x+dx,z+dz),layout.inverse(at.x(),at.z(),rotation));
            }
            assertEquals(1,owners.size());
        }
        assertEquals(1,layout.weight(0,0));assertEquals(0,layout.weight(-layout.margin,0));
    }
    @Test void fiveReleasedAlgorithmsDoNotAcquireChapterBuildings() throws Exception {
        for(String resource:List.of(WorldPlanRules.LEGACY_RESOURCE,WorldPlanRules.V2_RESOURCE,WorldPlanRules.V3_RESOURCE,WorldPlanRules.RESOURCE,WorldPlanRules.V5_RESOURCE)){
            var rules=WorldPlanV4Test.load(resource);var plan=new WorldPlan(20261025,rules);
            assertNull(rules.qianzhen);assertNull(plan.qianzhen());assertTrue(plan.qianzhenBuildings().isEmpty());assertFalse(plan.manifest().has("qianzhen"));
        }
    }
}
