package com.example.myvillage.wilderness.habitat;

import com.example.myvillage.worldplan.*;
import com.example.myvillage.worldplan.roads.*;
import com.example.myvillage.sim.data.SectProgressionRules;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HabitatLayoutTest {
    private WorldPlan world(long seed)throws Exception{try(var in=getClass().getResourceAsStream(WorldPlanRules.RESOURCE)){assertNotNull(in);return new WorldPlan(seed,new WorldPlanRules(new String(in.readAllBytes(),StandardCharsets.UTF_8)));}}
    @Test void deterministicSitesAreEntirelyEastAndAvoidEveryReservedPlotAcrossSeeds()throws Exception{
        for(long seed:new long[]{42,20261115,202611141,-20261115}){
            var world=world(seed);var roads=new RoadLayout(world.fingerprint(),"0".repeat(64),List.of(),List.of());
            var a=HabitatLayout.plan(world,roads);assertEquals(a,HabitatLayout.plan(world,roads));assertEquals(12,a.sites().size());
            for(var site:a.sites()){
                int radius=a.rules().integer("site_radius");
                for(int dx:new int[]{-radius,0,radius})for(int dz:new int[]{-radius,0,radius}){
                    assertEquals("donghuang",world.coreRegionAt(site.x()+dx,site.z()+dz).orElseThrow().id());
                    assertFalse(world.graph().geometry().reserved(site.x()+dx,site.z()+dz,a.rules().integer("reservation_margin")));
                }
            }
            assertEquals(Set.of("wolf_den","lizard_shelf","spore_grove","bandit_camp"),a.sites().stream().map(HabitatLayout.Site::kind).collect(java.util.stream.Collectors.toSet()));
        }
    }
    @Test void peerRegionsShareTheSameSectTierAndEastIsTheInitialRegion()throws Exception{
        var world=world(20261115);assertEquals("donghuang",world.spawn().regionId());
        var rules=new SectProgressionRules(2,2,Map.of(),null);
        assertEquals("branch",rules.forRegion(world.graph(),"donghuang"));assertEquals("branch",rules.forRegion(world.graph(),"lingyue"));assertEquals("branch",rules.forRegion(world.graph(),"beiyuan"));
        assertEquals("headquarters",rules.forRegion(world.graph(),"donghai"));assertEquals(rules.forRegion(world.graph(),"donghai"),rules.forRegion(world.graph(),"ximo"));
        assertEquals("headquarters",rules.forRegion(world.graph(),"nanjiang"));assertEquals("main",rules.forRegion(world.graph(),"zhongzhou"));
    }
}
