package com.example.myvillage.orchard;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.orchard.NingyuanTreePlan.Cell;

class NingyuanTreePlanTest {
    private static final int[][] SIDES={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    @Test void hundredsOfTreesHaveConnectedTrunksSupportedFruitAndNonDecayingCrowns() {
        for(int seed=0;seed<256;seed++) {
            var tree=NingyuanTreePlan.create(seed,OrchardRules.get());
            assertEquals(tree,NingyuanTreePlan.create(seed,OrchardRules.get()));
            assertTrue(tree.size()>60 && tree.size()<300);
            var logs=new HashSet<Cell>();tree.forEach((c,p)->{if(p.kind().equals("log"))logs.add(c);});
            var seen=new HashSet<Cell>();var q=new ArrayDeque<Cell>();q.add(new Cell(0,0,0));
            while(!q.isEmpty()) {var c=q.remove();if(!logs.contains(c)||!seen.add(c))continue;for(var d:SIDES)q.add(c.add(d[0],d[1],d[2]));}
            assertEquals(logs,seen,"Floating log at seed "+seed);
            for(var entry:tree.entrySet()) {
                var c=entry.getKey();var p=entry.getValue();
                assertTrue(Math.abs(c.x())<=4 && Math.abs(c.z())<=4 && c.y()>=0 && c.y()<=8);
                if(p.kind().equals("leaves")) {
                    assertTrue(p.distance()>=1 && p.distance()<=6);
                    assertTrue(Arrays.stream(SIDES).map(d->tree.get(c.add(d[0],d[1],d[2])))
                            .anyMatch(n->n!=null && !n.kind().equals("fruit") && n.distance()==p.distance()-1));
                }
                if(p.kind().equals("fruit")) assertEquals("leaves",tree.get(c.add(0,1,0)).kind());
            }
        }
    }
    @Test void seedsVaryCrownShapeWithoutExpandingPlacementBounds() {
        var plans=new HashSet<Map<Cell,NingyuanTreePlan.Piece>>();
        for(int seed=0;seed<20;seed++)plans.add(NingyuanTreePlan.create(seed,OrchardRules.get()));
        assertTrue(plans.size()>10);
        assertThrows(UnsupportedOperationException.class,()->plans.iterator().next().clear());
    }
}
