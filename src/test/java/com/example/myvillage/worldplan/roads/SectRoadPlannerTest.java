package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SectRoadPlannerTest {
    private WorldPlan world()throws Exception{return new WorldPlan(20261103,new WorldPlanRules(new String(getClass().getResourceAsStream("/data/myvillage/worldplan/frozen_v11.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)));}
    @Test void eachRegionContainsThreeFormsAndTwoActualScales()throws Exception{
        var world=world();
        for(var region:world.regions())if(!world.rules().profile(region.profile().id()).role().equals("walled")){
            var sites=world.sectSites().stream().filter(s->s.regionId().equals(region.id())).toList();
            assertEquals(6,sites.stream().map(s->s.blueprintId()).distinct().count());
            assertEquals(3,sites.stream().map(s->world.rules().sects.layout(s.blueprintId()).archetype).distinct().count());
            assertEquals(2,sites.stream().map(s->world.rules().sects.layout(s.blueprintId()).scale).distinct().count());
        }
    }
    @Test void onlyTheDeclaredExteriorCollarGrantsRoadPermission()throws Exception{
        var world=world();var rules=RoadRules.load();
        for(var site:world.sectSites()){
            var exit=SectRoadPlanner.exit(world,rules,site);var f=exit.frame();
            assertFalse(SectRoadPlanner.columnAllowed(world,rules,site,f.gateX(),f.gateZ()));
            assertTrue(SectRoadPlanner.columnAllowed(world,rules,site,f.edgeX(),f.edgeZ()));
            assertFalse(SectRoadPlanner.columnAllowed(world,rules,site,f.edgeX()+f.tx()*(rules.sectSpurs.approachHalfWidth()+1),f.edgeZ()+f.tz()*(rules.sectSpurs.approachHalfWidth()+1)));
            assertTrue(exit.existing().stream().allMatch(c->c.existing()&&c.feet2()==exit.feet2()));
        }
    }
}
