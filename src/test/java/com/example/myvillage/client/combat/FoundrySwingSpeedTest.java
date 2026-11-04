package com.example.myvillage.client.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoundrySwingSpeedTest {
    @Test void fasterAndSlowerActionsUseTheSamePoseTimelineAtTheirScaledTimes(){
        var normal=new LocalSwingTimeline(20,100,1);var fast=new LocalSwingTimeline(20,100,1.25);var slow=new LocalSwingTimeline(20,100,.8);
        assertEquals(normal.visualTick(110),fast.visualTick(108),.000001);assertEquals(normal.visualTick(110),slow.visualTick(112.5),.000001);
        assertFalse(fast.ended(115.9));assertTrue(fast.ended(116));assertTrue(slow.ended(125));
        assertEquals(1.25,fast.rate(108),.000001);assertEquals(.8,slow.rate(108),.000001);
    }
    @Test void invalidPlaybackSpeedsAreRejected(){
        for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,2.01,.49})assertThrows(IllegalArgumentException.class,()->new LocalSwingTimeline(20,0,value));
    }
}
