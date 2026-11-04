package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.*;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldRoadPlannerTest {
    @Test @Timeout(180) void anIsolatedCoastalRegionKeepsItsCompleteRoadConnectionOnTheRecordedRandomContinent()throws Exception {
        WorldPlan owner;
        try(var in=getClass().getResourceAsStream(WorldPlanRules.RESOURCE)) {
            assertNotNull(in);owner=new WorldPlan(6166082384136082383L,new WorldPlanRules(new String(in.readAllBytes(),StandardCharsets.UTF_8)));
        }
        var roadRules=RoadRules.load();
        var a=WorldRoadPlanner.exit(owner,roadRules,"donghai");
        var b=WorldRoadPlanner.exit(owner,roadRules,"ximo");
        var route=WorldRoadPlanner.plan(owner,roadRules,a,b);
        assertEquals(roadRules.width,route.width());
        var cells=RoadNetworkGeometry.index(route.cells());
        for(var point:route.centerline())for(int dx=-roadRules.width/2;dx<=roadRules.width/2;dx++)for(int dz=-roadRules.width/2;dz<=roadRules.width/2;dz++) {
            var c=cells.get(RoadPlan.Cell.key(point.x()+dx,point.z()+dz));
            assertNotNull(c);assertTrue(c.band()<2);
            for(var d:new int[][]{{1,0},{0,1}}) {
                var n=cells.get(RoadPlan.Cell.key(c.x()+d[0],c.z()+d[1]));
                if(n!=null&&n.band()<2)assertTrue(Math.abs(c.feet2()-n.feet2())<=1);
            }
        }
        for(var exit:List.of(a,b))for(var floor:exit.existing())assertEquals(floor,cells.get(floor.key()));
    }
    @Test void anUnbuiltWaypointDoesNotFreezeItsNaturalHeightAndBlockAFeasibleFullRoad() {
        var start=new RoadPortGeometry.Frame(-3,0,-2,0,1,0,1,20);
        var end=new RoadPortGeometry.Frame(67,0,66,0,-1,0,1,20);
        var a=new WorldRoadPlanner.Exit("a",start,160,
                java.util.stream.IntStream.rangeClosed(-2,2).mapToObj(z->new RoadPlan.Cell(-3,z,160,true)).toList());
        var b=new WorldRoadPlanner.Exit("b",end,160,
                java.util.stream.IntStream.rangeClosed(-2,2).mapToObj(z->new RoadPlan.Cell(67,z,160,true)).toList());
        var waypoint=new RoadAStar.Point(28,0);
        var sampled=new RoadPlan.Cell(waypoint.x(),waypoint.z(),192,false);
        var anchors=WorldRoadPlanner.hardGradeAnchors(a,b,List.of(sampled));
        var geometry=assertDoesNotThrow(()->RoadGeometry.build(List.of(new RoadAStar.Point(0,0),waypoint,new RoadAStar.Point(64,0)),
                5,0,160,160,(x,z)->x>=24&&x<=32?96:80,(x,z)->x>=-2&&x<=66&&Math.abs(z)<=2,8,1000,anchors));
        assertEquals(345,geometry.cells().size());
        assertTrue(anchors.stream().allMatch(RoadPlan.Cell::existing));
        var cells=RoadNetworkGeometry.index(geometry.cells());
        assertTrue(cells.get(RoadPlan.Cell.key(28,0)).feet2()<sampled.feet2());
        for(var c:geometry.cells()) {
            int ground=(c.x()>=24&&c.x()<=32?96:80)*2;
            assertTrue(Math.abs(c.feet2()-ground)<=16);
            for(int[] d:new int[][]{{1,0},{0,1}}) {
                var n=cells.get(RoadPlan.Cell.key(c.x()+d[0],c.z()+d[1]));
                if(n!=null)assertTrue(Math.abs(n.feet2()-c.feet2())<=1);
            }
        }
    }
    @Test void discoveredObstaclesKeepTheirWholeBuffer() {
        var obstacles=List.of(new WorldRoadPlanner.Box(-10,20,-10,20));
        assertFalse(WorldRoadPlanner.avoids(obstacles,24,14,44));
        assertTrue(WorldRoadPlanner.avoids(obstacles,24,15,44));
        assertFalse(WorldRoadPlanner.avoids(List.of(new WorldRoadPlanner.Box(-10,-10,200,200)),24,200,100));
    }
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
        assertFalse(WorldRoadPlanner.columnAllowed(world,rules,"nanjiang","ximo",x+rules.gateApproachHalfWidth+1,edge));
    }
    @Test void thirdCityAndClosedBorderRemainForbidden() {
        var spawn=world.spawn();assertFalse(WorldRoadPlanner.columnAllowed(world,rules,"nanjiang","ximo",spawn.x(),spawn.z()));
        var third=world.capitalOf("zhongzhou").orElseThrow();
        assertFalse(WorldRoadPlanner.columnAllowed(world,rules,"nanjiang","ximo",third.x(),third.z()));
        var closed=world.borders().stream().filter(b->!b.passable()).findFirst().orElseThrow();
        var a=new WorldRoadPlanner.Exit(closed.a(),new RoadPortGeometry.Frame(0,0,0,1,0,1,1,20),160,List.of());
        var b=new WorldRoadPlanner.Exit(closed.b(),new RoadPortGeometry.Frame(100,0,100,1,0,1,1,20),160,List.of());
        assertThrows(IllegalArgumentException.class,()->WorldRoadPlanner.plan(world,rules,a,b));
    }
    @Test void realCapitalSpawnDoesNotCloseItsDeclaredExteriorPublicExit()throws Exception {
        WorldPlan owner;try(var in=getClass().getResourceAsStream(WorldPlanRules.RESOURCE)){owner=new WorldPlan(-6259944962606258298L,new WorldPlanRules(new String(in.readAllBytes(),StandardCharsets.UTF_8)));}
        // Current bundles and safe sect exit reservations identify this exact fixture.
        assertEquals("0e4cc0706678b15ca0e64bc6b5b721b2b041924d8365d99a841233261678140a",owner.fingerprint());
        var exit=WorldRoadPlanner.exit(owner,rules,"ximo");var edge=exit.frame().edge();var spawn=owner.spawn();
        assertTrue(Math.abs(edge.x()-spawn.x())<=owner.rules().spawnPadRadius&&Math.abs(edge.z()-spawn.z())<=owner.rules().spawnPadRadius);
        assertTrue(WorldRoadPlanner.columnAllowed(owner,rules,"ximo","nanjiang",edge.x(),edge.z()));
        assertFalse(WorldRoadPlanner.columnAllowed(owner,rules,"ximo","nanjiang",spawn.x(),spawn.z()));
        assertFalse(WorldRoadPlanner.columnAllowed(owner,rules,"ximo","nanjiang",edge.x(),edge.z()-1));
        assertFalse(WorldRoadPlanner.columnAllowed(owner,rules,"ximo","nanjiang",edge.x()+rules.gateApproachHalfWidth+1,edge.z()));
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
            exits.add(new WorldRoadPlanner.Exit(region,RoadPortGeometry.frame(p,city.x(),city.z(),city.margin(),rules.width,rules.gridStep),feet,anchors));
        }
        var road=WorldRoadPlanner.plan(world,rules,exits.get(0),exits.get(1));
        assertEquals(Map.of("nanjiang",3,"ximo",5),road.gateWidths());
        assertTrue(road.cells().stream().filter(c->!c.existing()).allMatch(c->WorldRoadPlanner.columnAllowed(world,rules,"nanjiang","ximo",c.x(),c.z())));
    }
}
