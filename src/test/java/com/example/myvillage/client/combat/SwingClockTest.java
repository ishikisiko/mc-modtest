package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SwingClockTest {
    @Test
    void withoutHitStopVisualTimeIsRealTime() {
        SwingClock clock = new SwingClock(13.0F);
        for (float tick = 0.0F; tick <= 13.0F; tick += 0.5F) {
            assertEquals(tick, clock.visualTick(tick), 1.0E-5F);
        }
        assertEquals(0.0F, clock.visualTick(-2.0F));
        assertEquals(13.0F, clock.visualTick(20.0F));
    }

    @Test
    void hitStopSlowsThenCatchesUpToTheServerTotal() {
        SwingClock clock = new SwingClock(13.0F);
        assertTrue(clock.beginHitStop(5.0F));
        assertEquals(5.0F, clock.visualTick(5.0F), 1.0E-5F);
        float midStop = clock.visualTick(5.0F + SwingClock.HIT_STOP_TICKS * 0.5F);
        assertTrue(midStop - 5.0F < 0.2F, "hit-stop should nearly freeze the swing");
        assertTrue(clock.inHitStop(6.0F));
        assertFalse(clock.inHitStop(5.0F + SwingClock.HIT_STOP_TICKS));
        assertEquals(13.0F, clock.visualTick(13.0F), 1.0E-4F);

        float previous = 0.0F;
        for (float tick = 0.0F; tick <= 13.0F; tick += 0.1F) {
            float visual = clock.visualTick(tick);
            assertTrue(visual >= previous - 1.0E-5F, "visual time must not run backwards");
            assertTrue(visual <= tick + 1.0E-4F, "visual time never runs ahead of real time");
            previous = visual;
        }
    }

    @Test
    void onlyOneHitStopFitsAndNeverNearTheEnd() {
        SwingClock clock = new SwingClock(11.0F);
        assertFalse(clock.beginHitStop(8.0F));
        assertTrue(clock.beginHitStop(4.0F));
        assertFalse(clock.beginHitStop(5.0F));
        assertThrows(IllegalArgumentException.class, () -> new SwingClock(0.0F));
    }
}
