package com.example.myvillage.worldplan.roads;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;
import static com.example.myvillage.worldplan.roads.RoadAStar.Point;

class RoadJunctionPlannerTest {
    private final RoadRules rules=RoadRules.load();
    private RoadPlan road(String from,String to,Point start,Point end,boolean slope) {
        var line=RoadGeometry.densify(List.of(start,end));Map<Long,Cell> body=new LinkedHashMap<>();
        for(var p:line)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
            int x=p.x()+dx,z=p.z()+dz;body.putIfAbsent(Cell.key(x,z),new Cell(x,z,161+(slope?Math.floorDiv(x,8):0),false));
        }
        var styled=RoadOfficialStyle.apply(List.copyOf(body.values()),rules,63,(x,z)->80,(x,z)->true);
        return new RoadPlan(from,to,"a".repeat(64),rules.fingerprint,5,Map.of(),List.of(),line,styled);
    }
    private List<RoadPlan> tee(boolean slope) {
        return List.of(road("a","b",new Point(-30,0),new Point(30,0),slope),road("b","c",new Point(0,0),new Point(0,30),slope));
    }
    @Test void teeHasOneFlatChamferedPlatformAndContinuousApproachSlopes() {
        var original=tee(true);var result=RoadJunctionPlanner.prepare(original,List.of(),rules,(x,z)->80);
        assertEquals(1,result.hubs().size());var hub=result.hubs().getFirst();assertEquals(5,hub.radius());assertEquals(3,hub.branches());
        Map<Long,Cell> body=new HashMap<>();for(var road:result.routes())for(var c:road.cells())if(c.band()<2)body.put(c.key(),c);
        for(var c:RoadJunctionPlanner.pad(hub,rules.junctions.cornerCut()))assertEquals(hub.feet2(),body.get(c.key()).feet2());
        assertFalse(RoadJunctionPlanner.pad(hub,2).stream().anyMatch(c->c.x()==5&&c.z()==5));
        for(var c:body.values())for(var d:new int[][]{{1,0},{0,1}}){var n=body.get(Cell.key(c.x()+d[0],c.z()+d[1]));if(n!=null)assertTrue(Math.abs(c.feet2()-n.feet2())<=1);}
        assertEquals(160,original.getFirst().cells().stream().filter(c->c.x()==-1&&c.z()==0).findFirst().orElseThrow().feet2());
    }
    @Test void aCrossingGetsTheLargerPlatform() {
        var roads=List.of(road("a","b",new Point(-30,0),new Point(30,0),false),road("c","d",new Point(0,-30),new Point(0,30),false));
        var result=RoadJunctionPlanner.prepare(roads,List.of(),rules,(x,z)->80);
        assertEquals(1,result.hubs().size());assertEquals(4,result.hubs().getFirst().branches());assertEquals(6,result.hubs().getFirst().radius());
    }
    @Test void aSingleBendDoesNotBecomeAnIntersection() {
        var roads=List.of(road("a","b",new Point(-30,0),new Point(0,0),false),road("b","c",new Point(0,0),new Point(0,30),false));
        var result=RoadJunctionPlanner.prepare(roads,List.of(),rules,(x,z)->80);assertEquals(List.of(),result.hubs());assertEquals(roads,result.routes());
    }
    @Test void oldCarriagewaysAndReadOnlyGatesAreKeptAtTheirOriginalHeight() {
        var old=new Cell(0,0,161,false);var result=RoadJunctionPlanner.prepare(tee(false),List.of(old),rules,(x,z)->80);
        for(var road:result.routes())for(var c:road.cells())if(c.key()==old.key())assertEquals(161,c.feet2());
        var gate=new Cell(0,0,161,true);
        assertThrows(IllegalStateException.class,()->RoadJunctionPlanner.prepare(tee(false),List.of(gate),rules,(x,z)->80));
    }
    @Test void protectedColumnsCannotBeFilledJustToCompleteThePlaza() {
        var roads=new ArrayList<RoadPlan>();
        for(var road:tee(false))roads.add(new RoadPlan(road.from(),road.to(),road.worldHash(),road.rulesHash(),road.width(),road.gateWidths(),road.obstacles(),road.centerline(),road.cells().stream().filter(c->c.x()!=0||c.z()!=-3).toList()));
        assertThrows(IllegalStateException.class,()->RoadJunctionPlanner.prepare(roads,List.of(),rules,(x,z)->80));
    }
    @Test void oldSlopedRoadsAreNeverFlattenedByAJoiningNewRoad() {
        var roads=tee(true);var old=roads.getFirst().cells().stream().filter(c->c.band()<2).toList();
        assertThrows(IllegalStateException.class,()->RoadJunctionPlanner.prepare(roads,old,rules,(x,z)->80));
        assertEquals(160,old.stream().filter(c->c.x()==-1&&c.z()==0).findFirst().orElseThrow().feet2());
    }
}
