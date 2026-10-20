package com.example.myvillage.forging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopDisplayTest {
    @Test void hammerContactsAtTheAuthoritativeContactTickAndReturnsWithinBounds() {
        var rules=WorkshopRules.get();
        assertEquals(90,WorkshopDisplay.hammerAngle(rules.contact_tick(),rules.contact_tick(),rules.strike_ticks()));
        for(float t=0;t<rules.strike_ticks();t+=.1f) {
            float angle=WorkshopDisplay.hammerAngle(t,rules.contact_tick(),rules.strike_ticks());
            assertTrue(angle>=12&&angle<=90,"angle="+angle+" tick="+t);
        }
        assertEquals(90,WorkshopDisplay.hammerAngle(rules.strike_ticks(),rules.contact_tick(),rules.strike_ticks()));
    }
}
