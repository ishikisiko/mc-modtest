package com.example.myvillage.story.entrance;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static com.example.myvillage.story.entrance.EntranceColumnPlan.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class EntranceColumnPlanTest {
    private Map<Integer,EntranceColumnPlan.Kind> forest() {
        var m=new HashMap<Integer,EntranceColumnPlan.Kind>();for(int y=-8;y<=0;y++)m.put(y,FOUNDATION);
        for(int y=1;y<=12;y++)m.put(y,DECORATION);m.remove(6);m.remove(7);return m;
    }
    private EntranceColumnPlan plan(Map<Integer,EntranceColumnPlan.Kind> m,int surface,int ground) {
        return EntranceColumnPlan.plan(surface,ground,4,-64,320,40,40,3,y->m.getOrDefault(y,AIR));
    }
    private void assertSolidToGround(EntranceColumnPlan p,Map<Integer,EntranceColumnPlan.Kind> m) {
        var after=new HashMap<>(m);for(int y=p.foundationTop()+1;y<=p.ground();y++)after.put(y,FOUNDATION);
        for(int y=p.foundationTop()-2;y<=p.ground();y++)assertEquals(FOUNDATION,after.get(y),"no air, leaves or log remains below floor at "+y);
    }
    @Test void canopyBelowTargetNeverBecomesTheFillBase() {
        var m=forest();var p=plan(m,12,20);assertEquals(0,p.foundationTop());assertSolidToGround(p,m);
    }
    @Test void canopyAboveTargetIsOnlyTheClearingCeiling() {
        var m=forest();var p=plan(m,12,6);assertEquals(0,p.foundationTop());assertEquals(12,p.clearTop());assertSolidToGround(p,m);
    }
    @Test void waterOrLavaInTheClearingOrFoundationColumnIsRejected() {
        var wet=forest();wet.put(4,FLUID);assertThrows(IllegalArgumentException.class,()->plan(wet,12,6));
        var underwater=forest();underwater.put(-1,FLUID);assertThrows(IllegalArgumentException.class,()->plan(underwater,12,20));
    }
    @Test void shallowDryCaveIsFilledThroughToItsRealContiguousFloor() {
        var m=new HashMap<Integer,EntranceColumnPlan.Kind>();m.put(5,FOUNDATION);for(int y=-6;y<=0;y++)m.put(y,FOUNDATION);
        var p=plan(m,5,5);assertEquals(0,p.foundationTop());assertSolidToGround(p,m);
    }
    @Test void deepCaveOrNoFoundationIsNotAcceptedByIncreasingTheFillBudget() {
        var m=new HashMap<Integer,EntranceColumnPlan.Kind>();for(int y=-64;y<=-50;y++)m.put(y,FOUNDATION);
        assertThrows(IllegalArgumentException.class,()->plan(m,0,0));assertThrows(IllegalArgumentException.class,()->plan(Map.of(),0,0));
    }
    @Test void disconnectedThinNaturalShelfDoesNotCountAsContiguousGround() {
        var m=new HashMap<Integer,EntranceColumnPlan.Kind>();m.put(0,FOUNDATION);m.put(-1,FOUNDATION);m.put(-3,FOUNDATION);m.put(-4,FOUNDATION);
        assertThrows(IllegalArgumentException.class,()->plan(m,0,0));
    }
    @Test void protectedBlocksCannotHideUnderACanopyOrInAGroundGap() {
        var m=forest();m.put(7,PROTECTED);assertThrows(IllegalArgumentException.class,()->plan(m,12,6));
        var base=forest();base.put(-1,PROTECTED);assertThrows(IllegalArgumentException.class,()->plan(base,12,20));
    }
    @Test void everyFoundationSearchQueryAndEveryFillCellIsBudgeted() {
        AtomicInteger q=new AtomicInteger();var m=forest();var p=EntranceColumnPlan.plan(12,20,4,-64,320,40,40,3,y->{q.incrementAndGet();return m.getOrDefault(y,AIR);});
        assertEquals(q.get(),p.queries());assertTrue(p.queries()<=4+40+3);
        assertEquals(21,p.writeBudget()); // fill 1..20 plus final surface; no pointless writes above WORLD_SURFACE
    }
}
