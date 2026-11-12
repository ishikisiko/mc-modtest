package com.example.myvillage.qianji;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QianjiAtmosphereRulesTest {
    private final QianjiAtmosphereRules rules=QianjiAtmosphereRules.get();

    @Test void quietArrayAndMirageHaveSeparateIntervalsAndSmoothEdges(){
        assertEquals(0,rules.at(0).array());assertEquals(0,rules.at(0).mirage());
        assertEquals(1,rules.at(20).array());assertEquals(0,rules.at(20).mirage());
        assertEquals(0,rules.at(50).array());assertEquals(1,rules.at(50).mirage());
        assertEquals(.5,rules.at(12.25).array(),1e-8);
        assertEquals(0,rules.at(31).array());assertEquals(0,rules.at(63).mirage());
    }
    @Test void worldClockAndPersistedOffsetReconstructTheSamePhase(){
        long ticks=9876543210123L,offset=345;
        var first=rules.phase(ticks,offset,.4f);
        assertEquals(first,rules.phase(ticks+rules.cycleTicks()*10000,offset,.4f));
        assertEquals(first,rules.phase(ticks,offset-rules.cycleTicks()*500,.4f));
        assertEquals(rules.phase(ticks+1,offset,0).seconds(),rules.phase(ticks,offset,1).seconds(),1e-9);
    }
    @Test void negativeAndVeryLargeClocksStayWithinTheConfiguredCycle(){
        for(long tick:new long[]{Long.MIN_VALUE,-1561,-1,0,Long.MAX_VALUE}){
            var phase=rules.phase(tick,Long.MAX_VALUE,.99f);
            assertTrue(phase.seconds()>=0&&phase.seconds()<rules.number("cycle_seconds"));
            assertTrue(phase.array()>=0&&phase.array()<=1);assertTrue(phase.mirage()>=0&&phase.mirage()<=1);
        }
    }
}
