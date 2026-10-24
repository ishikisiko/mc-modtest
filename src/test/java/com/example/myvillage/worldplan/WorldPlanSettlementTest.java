package com.example.myvillage.worldplan;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.regiongen.BayTerracesLayout;
import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;

class WorldPlanSettlementTest {
    @Test void sitesAreSeededBoundedAndSharedWithSectReservations() throws Exception {
        var rules=WorldPlanV4Test.load(WorldPlanRules.V5_RESOURCE);var r=rules.settlements;
        Set<Integer> rotations=new HashSet<>();Set<String> positions=new HashSet<>();
        for(long seed:new long[]{0,1,-1,42,-42,20261017,20261025,Long.MIN_VALUE,Long.MAX_VALUE}) {
            var plan=new WorldPlan(seed,rules);
            assertFalse(plan.settlements().isEmpty(),"No suitable settlement for seed "+seed);
            assertTrue(plan.settlements().size()<=r.maximumSites);
            Set<String> hosts=new HashSet<>();
            for(var site:plan.settlements()) {
                assertTrue(hosts.add(site.host()));assertNotEquals("walled",rules.profile(site.host()).role());
                assertEquals(0,Math.floorMod(site.x(),16));assertEquals(0,Math.floorMod(site.z(),16));
                assertFalse(plan.intersectsCity(site.x()-r.margin,site.z()-r.margin,site.x()+r.size+r.margin-1,site.z()+r.size+r.margin-1));
                assertTrue(plan.graph().geometry().reserved(site.x(),site.z(),0),"GatePlacement must see the same reservation");
                assertTrue(plan.intersectsSettlements(site.x()-r.margin-r.structureBuffer,site.z(),site.x(),site.z()));
                assertTrue(plan.reserved(site.x()+r.inset,site.offsetY()+r.baseY,site.z()+r.inset));
                assertFalse(plan.reserved(site.x()+r.inset,site.offsetY()+r.baseY-1,site.z()+r.inset),"Underground remains native");
                for(int dx:new int[]{-r.margin,r.size+r.margin-1})for(int dz:new int[]{-r.margin,r.size+r.margin-1})
                    assertEquals(site.host(),plan.sample(site.x()+dx,site.z()+dz).regionId());
                rotations.add(site.rotation());positions.add(site.x()+","+site.z());
                int chunks=0;
                for(int z=site.z()-r.margin;z<site.z()+r.size+r.margin;z+=16)
                    for(int x=site.x()-r.margin;x<site.x()+r.size+r.margin;x+=16){
                        assertEquals(site,plan.settlementChunk(x>>4,z>>4).orElseThrow());chunks++;
                    }
                assertEquals(256,chunks);
            }
            assertEquals(plan.manifest(),new WorldPlan(seed,new WorldPlanRules(FrozenRulesEncoding.decode(FrozenRulesEncoding.encode(rules.json())))).manifest());
        }
        assertEquals(Set.of(0,1,2,3),rotations);assertTrue(positions.size()>9);
    }
    @Test void earlierWorldsKeepTheirPlanSchemaAndCityAssets() throws Exception {
        for(String source:List.of(WorldPlanRules.LEGACY_RESOURCE,WorldPlanRules.V2_RESOURCE,WorldPlanRules.V3_RESOURCE,WorldPlanRules.RESOURCE)) {
            var rules=WorldPlanV4Test.load(source);var plan=new WorldPlan(20261025,rules);
            assertNull(rules.settlements);assertTrue(plan.settlements().isEmpty());
            assertFalse(plan.manifest().has("settlements"));assertEquals(1,plan.graph().geometry().reservations().size());
        }
        var old=WorldPlanV4Test.load(WorldPlanRules.RESOURCE);var current=WorldPlanV4Test.load(WorldPlanRules.V5_RESOURCE);
        assertEquals(old.city.layoutHash(),current.city.layoutHash());assertEquals(old.city.tiles(),current.city.tiles());
        assertEquals(new WorldPlan(20261025,old).spawn(),new WorldPlan(20261025,current).spawn());
        assertEquals(new WorldPlan(20261025,old).city(),new WorldPlan(20261025,current).city());
    }
    @Test void frozenSitingUsesTheOwnedAssetAndRejectsMalformedBounds() throws Exception {
        var rules=WorldPlanV4Test.load(WorldPlanRules.V5_RESOURCE);var r=rules.settlements;var asset=BayTerracesLayout.current();
        assertEquals(asset.layoutHash,r.layoutHash);assertEquals(asset.size,r.size);assertEquals(asset.margin,r.margin);
        assertArrayEquals(asset.sampleOffsets(),r.sampleOffsets());
        for(String field:List.of("attempts_per_region","maximum_sites","search_radius")){
            var json=JsonParser.parseString(rules.json()).getAsJsonObject();json.getAsJsonObject("settlements").addProperty(field,Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(json.toString()));
        }
        var json=JsonParser.parseString(rules.json()).getAsJsonObject();json.remove("settlements");
        assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(json.toString()));
        String encoded=FrozenRulesEncoding.encode(rules.json());assertTrue(encoded.length()<65000);
    }
}
