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
}
