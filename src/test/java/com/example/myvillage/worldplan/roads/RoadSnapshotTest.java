package com.example.myvillage.worldplan.roads;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoadSnapshotTest {
    @Test void permitsOnlyExpectedVegetationDisappearance() {
        var before=new RoadPaver.Snapshot(1,new int[]{10,20,0},new byte[]{1,0,2});
        assertTrue(before.accepts(new RoadPaver.Snapshot(2,new int[]{0,20,0},new byte[]{2,0,2})));
        assertFalse(before.accepts(new RoadPaver.Snapshot(2,new int[]{10,0,0},new byte[]{1,2,2})),"实心地物消失不能作为植被更新接受");
        assertFalse(before.accepts(new RoadPaver.Snapshot(2,new int[]{10,20,30},new byte[]{1,0,0})),"新增方块不能被快照接受");
        assertFalse(before.accepts(new RoadPaver.Snapshot(2,new int[]{30,20,0},new byte[]{0,0,2})),"植被不能被替换成其他方块");
    }
    @Test void snapshotRetainsItsOwnImmutableInputAndExactShape() {
        int[] states={10};byte[] flags={0};var snapshot=new RoadPaver.Snapshot(1,states,flags);states[0]=0;flags[0]=1;
        assertFalse(snapshot.accepts(new RoadPaver.Snapshot(2,new int[]{0},new byte[]{2})));
        assertFalse(snapshot.accepts(new RoadPaver.Snapshot(1,new int[]{10,0},new byte[]{0,2})));
    }
}
