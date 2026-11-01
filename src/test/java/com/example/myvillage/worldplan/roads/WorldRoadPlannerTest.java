package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.*;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldRoadPlannerTest {
    private static WorldPlan world;
    private static RoadRules rules;
    @BeforeAll static void prepare()throws Exception {
        try(var in=WorldRoadPlannerTest.class.getResourceAsStream(WorldPlanRules.RESOURCE)) {
            assertNotNull(in);world=new WorldPlan(20261101,new WorldPlanRules(new String(in.readAllBytes(),StandardCharsets.UTF_8)));
        }
        rules=RoadRules.load();
    }
    @Test void cheapCoreOwnershipMatchesAuthoritativeContext() {
        for(int x=-8000;x<=8000;x+=731)for(int z=-8000;z<=8000;z+=733) {
            var c=world.sample(x,z);var owner=world.coreRegionAt(x,z);
            assertEquals(c.core(),owner.isPresent());if(c.core())assertEquals(c.regionId(),owner.orElseThrow().id());
        }
    }
    @Test void gateCorridorNeverPermitsCityInteriorOrAdjacentCollar() {
        var city=world.capitalOf("nanjiang").orElseThrow();int x=city.x()+rules.ports.get("nanjiang").x(),edge=city.z()+city.bundle().depth;
        assertFalse(WorldRoadPlanner.columnAllowed(world,rules,"nanjiang","ximo",x,edge-1));
        assertTrue(WorldRoadPlanner.columnAllowed(world,rules,"nanjiang","ximo",x,edge));
        assertFalse(WorldRoadPlanner.columnAllowed(world,rules,"nanjiang","ximo",x+rules.width,edge));
    }
    @Test void thirdCityAndClosedBorderRemainForbidden() {
        var spawn=world.spawn();assertFalse(WorldRoadPlanner.columnAllowed(world,rules,"nanjiang","ximo",spawn.x(),spawn.z()));
        var third=world.capitalOf("zhongzhou").orElseThrow();
        assertFalse(WorldRoadPlanner.columnAllowed(world,rules,"nanjiang","ximo",third.x(),third.z()));
        var closed=world.borders().stream().filter(b->!b.passable()).findFirst().orElseThrow();
        var a=new WorldRoadPlanner.Exit(closed.a(),0,0,0,160,List.of());
        var b=new WorldRoadPlanner.Exit(closed.b(),100,0,0,160,List.of());
        assertThrows(IllegalArgumentException.class,()->WorldRoadPlanner.plan(world,rules,a,b));
    }
    @Test @Timeout(90) void selectedPairPlansThroughItsRealSharedBorder() {
        List<WorldRoadPlanner.Exit> exits=new ArrayList<>();
        for(String region:List.of("nanjiang","ximo")) {
            var city=world.capitalOf(region).orElseThrow();var p=rules.ports.get(region);
            int center=city.x()+p.x(),edge=city.z()+city.bundle().depth;
            List<RoadPlan.Cell> anchors=new ArrayList<>();
            // Native sample fixture: Ximo's final stairs stand one block above its
            // terrain heightfield; Nanjiang's last exit row is the terrain surface.
            for(int dx=-p.gateWidth()/2;dx<=p.gateWidth()/2;dx++)anchors.add(new RoadPlan.Cell(center+dx,edge-1,
                (city.surface(center+dx,edge-1)+(region.equals("ximo")?1:0))*2,true));
            int feet=anchors.stream().filter(c->c.x()==center).findFirst().orElseThrow().feet2();
            exits.add(new WorldRoadPlanner.Exit(region,center,edge,edge+city.margin()+rules.width/2+rules.gridStep*2,feet,anchors));
        }
        var road=WorldRoadPlanner.plan(world,rules,exits.get(0),exits.get(1));
        assertEquals(Map.of("nanjiang",3,"ximo",5),road.gateWidths());
        assertTrue(road.cells().stream().filter(c->!c.existing()).allMatch(c->WorldRoadPlanner.columnAllowed(world,rules,"nanjiang","ximo",c.x(),c.z())));
    }
}
