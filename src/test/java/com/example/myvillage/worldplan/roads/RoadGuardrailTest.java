package com.example.myvillage.worldplan.roads;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.ToIntBiFunction;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;

class RoadGuardrailTest {
    private final RoadRules rules=RoadRules.load();
    private List<RoadAStar.Point> line(){var out=new ArrayList<RoadAStar.Point>();for(int z=0;z<=80;z++)out.add(new RoadAStar.Point(0,z));return out;}
    private List<Cell> carriage(int y){var out=new ArrayList<Cell>();for(int z=0;z<=80;z++)for(int x=-2;x<=2;x++)out.add(new Cell(x,z,y*2+1,false));return out;}
    private List<Cell> apply(List<Cell> body,int sea,ToIntBiFunction<Integer,Integer> ground){return RoadGuardrailPlanner.apply(RoadOfficialStyle.apply(body,rules,sea,ground,(x,z)->true),line(),rules.guard,rules.style.curbRise2(),sea,ground);}
    @Test void flatRoadRemainsOpenAndCentralFiveColumnsAreUnchanged() {
        var body=carriage(80);var result=apply(body,63,(x,z)->80);
        assertTrue(result.stream().noneMatch(c->c.rail()>=0));
        for(var c:body){var n=result.stream().filter(k->k.key()==c.key()).findFirst().orElseThrow();assertEquals(c.feet2(),n.feet2());assertEquals(-1,n.rail());assertTrue(n.band()<2);}
    }
    @Test void cliffRailStaysOnDangerousOuterCurbAndLeavesGateEndsOpen() {
        var result=apply(carriage(80),63,(x,z)->x>=5?75:80);
        var rail=result.stream().filter(c->c.rail()>=0).toList();assertFalse(rail.isEmpty());
        assertTrue(rail.stream().allMatch(c->c.band()==2&&c.x()==3&&c.z()>=rules.guard.endGap&&c.z()<=80-rules.guard.endGap));
        assertTrue(result.stream().filter(c->Math.abs(c.x())<=2).allMatch(c->c.rail()==-1));
    }
    @Test void ShallowWaterIsGuardedEvenWhenDropIsBelowCliffThreshold() {
        var result=apply(carriage(63),63,(x,z)->x>=5?62:63);
        assertTrue(result.stream().anyMatch(c->c.rail()>=0));
        assertTrue(result.stream().filter(c->c.rail()>=0).allMatch(c->c.x()==3));
    }
    @Test void shortHazardGetsConnectedApproachesButDoesNotEncloseWholeRoad() {
        var result=apply(carriage(80),63,(x,z)->x>=5&&z>=30&&z<=34?75:80);
        var rails=result.stream().filter(c->c.rail()>=0).toList();
        assertEquals(5+2*rules.guard.runPadding,rails.size());
        assertTrue(rails.stream().allMatch(c->c.x()==3&&c.z()>=30-rules.guard.runPadding&&c.z()<=34+rules.guard.runPadding));
    }
    @Test void fullBlockStepHasVerticalCouplerAndRoundTripUsesBothMasks() {
        var body=carriage(80).stream().map(c->new Cell(c.x(),c.z(),c.feet2()+(c.z()>=40?2:0),false)).toList();
        var result=apply(body,63,(x,z)->x>=5?75:80);
        assertTrue(result.stream().anyMatch(c->c.rail()>15));
        for(var c:result)if(c.rail()>=0)assertEquals(0,(c.rail()>>>4)&~c.rail());
    }
    @Test void curvedHazardRetainsCardinalConnectionsAtStaircaseCorners() {
        var path=new ArrayList<RoadAStar.Point>();var body=new HashMap<Long,Cell>();
        for(int z=0;z<=80;z++){
            int x=z/3;path.add(new RoadAStar.Point(x,z));
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){var c=new Cell(x+dx,z+dz,161,false);body.put(c.key(),c);}
        }
        var styled=RoadOfficialStyle.apply(List.copyOf(body.values()),rules,63,(x,z)->75,(x,z)->true);
        var result=RoadGuardrailPlanner.apply(styled,path,rules.guard,rules.style.curbRise2(),63,(x,z)->75);
        var rails=result.stream().filter(c->c.rail()>=0).toList();assertTrue(rails.size()>40);
        assertTrue(rails.stream().allMatch(c->c.band()==2&&(c.rail()&15)!=0));
        var by=new HashMap<Long,Cell>();result.forEach(c->by.put(c.key(),c));
        for(var c:body.values()){assertEquals(-1,by.get(c.key()).rail());assertEquals(c.feet2(),by.get(c.key()).feet2());}
    }
    @Test void halfSlabGeometryRestsOnSupportAndNeverIntrudesIntoAdjacentColumn() {
        for(int mask=0;mask<256;mask++)for(boolean collision:new boolean[]{false,true}) {
            var full=rules.guard.boxes(mask,false,collision);var half=rules.guard.boxes(mask,true,collision);
            assertEquals(full.size(),half.size());
            for(int i=0;i<full.size();i++) {
                var a=full.get(i);var b=half.get(i);assertEquals(a[1]-8,b[1]);assertEquals(a[4]-8,b[4]);
                assertTrue(b[0]>=0&&b[2]>=0&&b[3]<=16&&b[5]<=16);
            }
            assertEquals(-8,half.stream().mapToDouble(b->b[1]).min().orElseThrow());
        }
    }
    @Test void malformedPlanCannotPutGuardrailsInCarriageOrCity() {
        assertThrows(IllegalArgumentException.class,()->new Cell(0,0,160,false,0,0));
        assertThrows(IllegalArgumentException.class,()->new Cell(0,0,160,true,2,0));
        assertThrows(IllegalArgumentException.class,()->new Cell(0,0,160,false,2,16));
    }
}
