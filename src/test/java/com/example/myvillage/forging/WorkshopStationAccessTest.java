package com.example.myvillage.forging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopStationAccessTest {
    @Test void dingCannotMakeOrReworkBilletsEvenWithStalePackets() {
        for(int action=0;action<=6;action++)assertFalse(WorkshopStationAccess.allows(true,action,10));
        for(int action:new int[]{8,20,29,51,52,53,60,61,62})assertFalse(WorkshopStationAccess.allows(true,action,10));
        for(int action:new int[]{7,9,40,48})assertTrue(WorkshopStationAccess.allows(true,action,10));
    }
    @Test void anvilCannotInfuseOrSelectAnAffinity() {
        for(int action:new int[]{7,9,40,41,48})assertFalse(WorkshopStationAccess.allows(false,action,10));
        for(int action:new int[]{0,1,2,3,4,5,6,8,20,29,51,52,53,60,61,62})assertTrue(WorkshopStationAccess.allows(false,action,10));
    }
    @Test void unknownIdsAndSelectionBoundariesFailClosed() {
        for(boolean ding:new boolean[]{false,true})for(int action:new int[]{-1,10,19,30,39,49,50,54,59,63,Integer.MAX_VALUE})
            assertFalse(WorkshopStationAccess.allows(ding,action,10),"ding="+ding+" action="+action);
    }
}
