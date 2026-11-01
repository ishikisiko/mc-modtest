package com.example.myvillage.worldplan.roads;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.BiPredicate;
import static org.junit.jupiter.api.Assertions.*;

class RoadAStarTest {
    private static final RoadAStar.Cost COST=new RoadAStar.Cost(10,14.142,18,30,.05,1.2);
    private static RoadAStar.Field field(BiPredicate<Integer,Integer> mask) {
        return new RoadAStar.Field() {
            public int height(int x,int z){return 80;}
            public int waterDepth(int x,int z){return 0;}
            public boolean allowed(int x,int z){return x>=-16&&x<=16&&z>=-16&&z<=16&&mask.test(x,z);}
        };
    }
    @Test void gridTransitionsDoNotJumpThinWalls() {
        var f=field((x,z)->x!=2||Math.abs(z)>=3);
        var route=RoadAStar.find(new RoadAStar.Point(-8,0),new RoadAStar.Point(8,0),f,4,5000,COST);
        var line=RoadGeometry.densify(route);
        assertTrue(line.stream().allMatch(p->f.allowed(p.x(),p.z())));
        assertTrue(line.stream().anyMatch(p->Math.abs(p.z())>=3));
    }
    @Test void diagonalCannotClipProtectedCorners() {
        var f=field((x,z)->(x==0&&z==0)||(x==1&&z==1));
        assertThrows(IllegalStateException.class,()->RoadAStar.find(new RoadAStar.Point(0,0),new RoadAStar.Point(1,1),f,1,100,COST));
    }
    @Test void exactOffGridEndIsIncludedAtNegativeCoordinates() {
        var route=RoadAStar.find(new RoadAStar.Point(-13,-11),new RoadAStar.Point(6,7),field((x,z)->true),4,5000,COST);
        assertEquals(new RoadAStar.Point(6,7),route.getLast());
        assertEquals(new RoadAStar.Point(-13,-11),route.getFirst());
    }
    @Test void exhaustedSearchDoesNotReturnPartialSuccess() {
        assertThrows(IllegalStateException.class,()->RoadAStar.find(new RoadAStar.Point(-16,-16),new RoadAStar.Point(16,16),field((x,z)->true),1,1,COST));
    }
    @Test void feasibleHeightLabelsPermitLongerApproachWithoutSteepShortcut(){
        var f=new RoadAStar.Field(){
            public int height(int x,int z){return x>=8?88:80;}
            public int waterDepth(int x,int z){return 0;}
            public boolean allowed(int x,int z){return x>=0&&x<=16&&z>=0&&z<=16;}
            public int minimumFeet2(int x,int z){return x==0&&z==0?160:x>=8?176:150;}
            public int maximumFeet2(int x,int z){return minimumFeet2(x,z)==160?160:minimumFeet2(x,z)==176?176:190;}
        };
        var route=RoadAStar.find(new RoadAStar.Point(0,0),new RoadAStar.Point(16,0),f,4,5000,COST);assertEquals(new RoadAStar.Point(16,0),route.getLast());assertTrue(route.stream().anyMatch(p->p.z()>0));
    }
    @Test void interruptionStopsWorker() {
        Thread.currentThread().interrupt();
        try {assertThrows(java.util.concurrent.CancellationException.class,()->RoadAStar.find(new RoadAStar.Point(-16,-16),new RoadAStar.Point(16,16),field((x,z)->true),1,5000,COST));}
        finally {Thread.interrupted();}
    }
}
