package com.example.myvillage.client.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QiUseLatchTest {
    @Test void menuCancelsOnceWithoutReleasingOrRepeatingTheHeldPress() {
        var latch = new QiUseLatch();
        assertTrue(latch.press());
        assertTrue(latch.cancel());
        assertFalse(latch.cancel());
        assertFalse(latch.press());
        assertFalse(latch.release(true));
        assertFalse(latch.release(false));
        assertTrue(latch.press());
        assertTrue(latch.release(false));
    }

    @Test void repeatUseEventsCannotRestartAnAutoReleasedCharge() {
        var latch = new QiUseLatch();
        assertTrue(latch.press());
        for (int i = 0; i < 100; i++) {
            assertFalse(latch.press());
            assertFalse(latch.release(true));
        }
        assertTrue(latch.release(false));
        assertFalse(latch.release(false));
        assertTrue(latch.press());
    }

    @Test void WorldChangeForgetsTheHeldKeyWithoutSendingAStaleRelease() {
        var latch = new QiUseLatch();
        assertTrue(latch.press());
        latch.clear();
        assertFalse(latch.release(false));
        assertTrue(latch.press());
    }
}
