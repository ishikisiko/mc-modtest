package com.example.myvillage.worldplan.roads;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoadGeometryTest {
    @Test void fullWidthIsPresentThroughDiagonalAndTurn() {
        var geometry=RoadGeometry.build(List.of(new RoadAStar.Point(-30,-30),new RoadAStar.Point(0,0),new RoadAStar.Point(0,30)),5,6,160,160,(x,z)->80,(x,z)->true,8,10000);
        Set<Long> cells=new HashSet<>();geometry.cells().forEach(c->cells.add(c.key()));
        for(var p:geometry.centerline())for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)
            assertTrue(cells.contains(RoadPlan.Cell.key(p.x()+dx,p.z()+dz)),"lost width column");
    }
    @Test void rampsAreContinuousAcrossAllCardinalEdges() {
        var geometry=RoadGeometry.build(List.of(new RoadAStar.Point(0,0),new RoadAStar.Point(60,0)),5,6,140,180,(x,z)->70+Math.max(0,Math.min(60,x))/3,(x,z)->true,8,10000);
        Map<Long,RoadPlan.Cell> cells=new HashMap<>();geometry.cells().forEach(c->cells.put(c.key(),c));
        for(var c:geometry.cells())for(int[] off:new int[][]{{1,0},{0,1}}){var n=cells.get(RoadPlan.Cell.key(c.x()+off[0],c.z()+off[1]));if(n!=null)assertTrue(Math.abs(c.feet2()-n.feet2())<=1);}
    }
    @Test void protectedFullWidthColumnFailsEvenWhenCenterlineIsClear() {
        assertThrows(IllegalStateException.class,()->RoadGeometry.build(List.of(new RoadAStar.Point(0,0),new RoadAStar.Point(20,0)),5,0,160,160,(x,z)->80,(x,z)->!(x==10&&z==2),8,10000));
    }
    @Test void excessiveEarthworkFailsRatherThanFlatteningTerrain() {
        assertThrows(IllegalStateException.class,()->RoadGeometry.build(List.of(new RoadAStar.Point(0,0),new RoadAStar.Point(80,0)),5,6,160,160,(x,z)->x==40?100:80,(x,z)->true,2,10000));
    }
    @Test void columnBudgetCannotTruncateTheRoad() {
        assertThrows(IllegalStateException.class,()->RoadGeometry.build(List.of(new RoadAStar.Point(0,0),new RoadAStar.Point(80,0)),5,0,160,160,(x,z)->80,(x,z)->true,8,10));
    }
    @Test void steppedGateJoinsEveryLaneWithoutChangingExistingStairs() {
        var anchors=List.of(new RoadPlan.Cell(-2,-1,164,true),new RoadPlan.Cell(-1,-1,162,true),
            new RoadPlan.Cell(0,-1,160,true),new RoadPlan.Cell(1,-1,162,true),new RoadPlan.Cell(2,-1,164,true));
        var g=RoadGeometry.build(List.of(new RoadAStar.Point(0,2),new RoadAStar.Point(0,42)),5,6,160,160,
            (x,z)->80,(x,z)->z>=0,8,10000,anchors);
        Map<Long,RoadPlan.Cell> cells=new HashMap<>();g.cells().forEach(c->cells.put(c.key(),c));
        for(var a:anchors)assertTrue(Math.abs(cells.get(RoadPlan.Cell.key(a.x(),0)).feet2()-a.feet2())<=1);
        for(var c:g.cells())for(int[] off:new int[][]{{1,0},{0,1}}){var n=cells.get(RoadPlan.Cell.key(c.x()+off[0],c.z()+off[1]));if(n!=null)assertTrue(Math.abs(c.feet2()-n.feet2())<=1);}
        assertEquals(164,anchors.getFirst().feet2());
    }
    @Test void switchbackUsesConnectedRoadDistanceRatherThanShortcutAcrossEmptySpace(){
        var g=RoadGeometry.build(List.of(new RoadAStar.Point(0,0),new RoadAStar.Point(0,40),new RoadAStar.Point(12,40),new RoadAStar.Point(12,0)),3,0,160,200,(x,z)->x<6?80:100,(x,z)->true,24,10000);
        var by=RoadNetworkGeometry.index(g.cells());assertEquals(160,by.get(RoadPlan.Cell.key(0,0)).feet2());assertEquals(200,by.get(RoadPlan.Cell.key(12,0)).feet2());
    }
    @Test void incompatibleGateHeightIntervalsFail() {
        var anchors=List.of(new RoadPlan.Cell(0,-1,160,true),new RoadPlan.Cell(1,-1,170,true));
        assertThrows(IllegalStateException.class,()->RoadGeometry.build(List.of(new RoadAStar.Point(0,2),new RoadAStar.Point(0,42)),5,6,160,160,
            (x,z)->80,(x,z)->z>=0,8,10000,anchors));
    }
}
