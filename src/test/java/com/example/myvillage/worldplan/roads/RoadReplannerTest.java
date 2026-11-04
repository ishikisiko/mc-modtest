package com.example.myvillage.worldplan.roads;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.myvillage.worldplan.roads.RoadAStar.Point;

class RoadReplannerTest {
    private static final Point START=new Point(0,0),END=new Point(64,0),SPIKE=new Point(20,0);
    private static final RoadAStar.Cost COST=new RoadAStar.Cost(10,14.142,18,30,.05,1.2);
    private static RoadAStar.Field terrain(){return new RoadAStar.Field(){
        public int height(int x,int z){return x==SPIKE.x()&&z==SPIKE.z()?100:80;}
        public int waterDepth(int x,int z){return 0;}
        public int corridorWidth(){return 5;}
        public boolean allowed(int x,int z){return x>=0&&x<=64&&Math.abs(z)<=8;}
    };}
    private static RoadGeometry.Geometry realize(List<Point> path,RoadAStar.Field field){
        return RoadGeometry.build(path,5,0,160,160,field::height,
                (x,z)->x>=-2&&x<=66&&Math.abs(z)<=10,2,10000);
    }
    @Test void aLateFullWidthHeightConflictReroutesAndKeepsEveryLane(){
        var field=terrain();var calls=new AtomicInteger();var budget=new RoadReplanner.Budget(5000,4);
        var geometry=RoadReplanner.findVia("fixture",List.of(START,END),field,8,budget,COST,path->{calls.incrementAndGet();return realize(path,field);});
        assertEquals(2,calls.get());assertEquals(1,budget.replans());
        assertTrue(geometry.centerline().stream().anyMatch(p->p.z()!=0));
        var cells=RoadNetworkGeometry.index(geometry.cells());assertFalse(cells.containsKey(RoadPlan.Cell.key(SPIKE.x(),SPIKE.z())));
        for(var p:geometry.centerline())for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)
            assertNotNull(cells.get(RoadPlan.Cell.key(p.x()+dx,p.z()+dz)));
        for(var c:geometry.cells()){
            assertTrue(Math.abs(c.feet2()-field.height(c.x(),c.z())*2)<=4);
            for(int[] d:new int[][]{{1,0},{0,1}}){var n=cells.get(RoadPlan.Cell.key(c.x()+d[0],c.z()+d[1]));if(n!=null)assertTrue(Math.abs(c.feet2()-n.feet2())<=1);}
        }
    }
    @Test void aRequiredCrossingIsNeverDroppedToMakeAPlanPass(){
        var paths=new ArrayList<List<Point>>();var field=terrain();var budget=new RoadReplanner.Budget(5000,2);
        assertThrows(IllegalStateException.class,()->RoadReplanner.findVia("required",List.of(START,SPIKE,END),field,8,budget,COST,path->{paths.add(path);return realize(path,field);}));
        assertFalse(paths.isEmpty());assertTrue(paths.size()<=3);assertTrue(budget.replans()<=2);
        for(var path:paths){assertEquals(START,path.getFirst());assertEquals(END,path.getLast());assertTrue(path.contains(SPIKE));}
    }
    @Test void retriesCannotResetTheExpansionBudget(){
        var field=terrain();var calls=new AtomicInteger();var budget=new RoadReplanner.Budget(8,4);
        assertThrows(IllegalStateException.class,()->RoadReplanner.findVia("budget",List.of(START,END),field,8,budget,COST,path->{calls.incrementAndGet();return realize(path,field);}));
        assertEquals(1,calls.get());assertEquals(0,budget.remainingExpansions());
    }
    @Test void protectionAndOtherNonGradeFailuresAreNotRetriedOrHidden(){
        var calls=new AtomicInteger();var failure=new IllegalStateException("完整路幅触及保护区");
        assertSame(failure,assertThrows(IllegalStateException.class,()->RoadReplanner.findVia("protected",List.of(START,END),terrain(),8,
                new RoadReplanner.Budget(5000,4),COST,path->{calls.incrementAndGet();throw failure;})));
        assertEquals(1,calls.get());
    }
    @Test void gradeFailureRetainsTheActualConstraintThatMustBeAvoided(){
        var failure=assertThrows(RoadNetworkGeometry.GradeConflict.class,()->realize(List.of(START,END),terrain()));
        assertEquals(SPIKE.x(),failure.lower().x());assertEquals(SPIKE.z(),failure.lower().z());assertEquals(196,failure.lower().feet2());
        assertTrue(failure.getMessage().contains("下限来源=20,0@196"));
    }
}
