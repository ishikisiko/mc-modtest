package com.example.myvillage.client.cultivation.motion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeditationTimelineTest {
    @Test void lateObserversSampleTheExistingEpochAndDuplicatesDoNotRestart() {
        var t=new MeditationTimeline();
        assertTrue(t.accept(3,true,100,40,false,40));
        assertEquals(1,t.weight(500));
        assertFalse(t.accept(3,true,500,40,false,40));
        assertFalse(t.accept(2,false,500,40,false,40));
        assertEquals(1,t.weight(501));
    }
    @Test void partialEntryStopsFromItsCurrentPoseWithoutSnappingToFullSit() {
        var t=new MeditationTimeline();t.accept(1,true,100,40,false,40);
        assertEquals(.5,t.weight(120),1e-6);
        t.accept(2,false,120,40,true,40);
        assertEquals(.5,t.weight(120),1e-6);
        assertEquals(.25,t.weight(130),1e-6);
        assertEquals(0,t.weight(140));
    }
    @Test void forcedInterruptIsImmediateAndRestartCanBlendOutOfTheExit() {
        var t=new MeditationTimeline();t.accept(1,true,100,40,false,40);
        t.accept(2,false,200,40,true,40);
        float partial=t.weight(210);
        t.accept(3,true,210,40,false,40);
        assertEquals(partial,t.weight(210));
        assertEquals(1,t.weight(250));
        t.accept(4,false,260,40,false,40);
        assertEquals(0,t.weight(260));
        assertEquals(0,t.weight(300));
    }
    @Test void movingDuringGracefulExitRestoresVanillaWithoutWaiting() {
        var t=new MeditationTimeline();t.accept(1,true,0,40,false,40);
        t.accept(2,false,100,40,true,40);t.abort(105);
        assertEquals(0,t.weight(105));assertEquals(0,t.weight(120));
    }
}
