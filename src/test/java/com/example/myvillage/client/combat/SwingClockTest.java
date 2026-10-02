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
    void perMoveStopFreezesThenCreepsThenCatchesUp() {
        SwingClock clock = new SwingClock(17.0F);
        assertTrue(clock.beginHitStop(7.0F, 3.0F));
        float freezeEnd = 7.0F + 3.0F * SwingClock.FREEZE_FRACTION;
        // True freeze for the first 60% of the stop.
        assertEquals(7.0F, clock.visualTick(7.5F), 1.0E-5F);
        assertEquals(7.0F, clock.visualTick(freezeEnd - 0.01F), 1.0E-5F);
        assertEquals(0.0F, clock.rate(8.0F), 1.0E-6F);
        // Then a slow creep.
        assertEquals(SwingClock.CREEP_RATE, clock.rate(freezeEnd + 0.1F), 1.0E-6F);
        assertEquals(7.0F + 0.5F * SwingClock.CREEP_RATE, clock.visualTick(freezeEnd + 0.5F), 1.0E-5F);
        assertTrue(clock.inHitStop(9.9F));
        assertFalse(clock.inHitStop(10.0F));
        // Then the catch-up still lands exactly on the server total.
        assertTrue(clock.rate(12.0F) > 1.0F);
        assertEquals(17.0F, clock.visualTick(17.0F), 1.0E-4F);
        assertEquals(1.0F, clock.rate(17.0F), 1.0E-6F);
        assertEquals(1.0F, clock.rate(6.0F), 1.0E-6F);
        assertEquals(0.0F, clock.hitStopProgress(7.0F), 1.0E-6F);
        assertEquals(0.5F, clock.hitStopProgress(8.5F), 1.0E-6F);
    }

    @Test
    void longerStopsFreezeLongerAndTheVisualClockNeverRunsBackwards() {
        for (float stop : new float[] {1.5F, 2.0F, 3.0F, 4.0F}) {
            SwingClock clock = new SwingClock(20.0F);
            assertTrue(clock.beginHitStop(8.0F, stop), "stop " + stop);
            assertEquals(8.0F, clock.visualTick(8.0F + stop * 0.59F), 1.0E-5F);
            float previous = 0.0F;
            for (float tick = 0.0F; tick <= 20.0F; tick += 0.05F) {
                float visual = clock.visualTick(tick);
                assertTrue(visual >= previous - 1.0E-5F, "stop " + stop + " runs backwards at " + tick);
                assertTrue(visual <= tick + 1.0E-4F, "stop " + stop + " runs ahead at " + tick);
                previous = visual;
            }
            assertEquals(20.0F, clock.visualTick(20.0F), 1.0E-4F);
        }
    }

    @Test
    void contactAlignedStartMayLieAheadOfNow() {
        SwingClock clock = new SwingClock(13.0F);
        // The server hit arrives at real tick 4.2; the drawn blade reaches its contact at 4.9.
        float start = Math.max(4.2F, clock.realTickForVisual(4.9F));
        assertEquals(4.9F, start, 1.0E-5F);
        assertTrue(clock.beginHitStop(start, 2.0F));
        assertEquals(4.5F, clock.visualTick(4.5F), 1.0E-5F);
        assertFalse(clock.inHitStop(4.5F));
        assertTrue(clock.inHitStop(5.0F));
        // The inverse maps visual time back onto real time around the stop.
        for (float visual = 0.0F; visual <= 13.0F; visual += 0.25F) {
            assertEquals(visual, clock.visualTick(clock.realTickForVisual(visual)), 1.0E-3F);
        }
    }

    @Test
    void aLateConfirmationFreezesThePoseOnScreenNotTheTickStart() {
        // The live spear sweep: contact 6.0, stop 2.0. A frame drew real tick 6.55; the hit
        // confirmation is handled at the start of the next frame, whose tick reading is still 6.0.
        SwingClock clock = new SwingClock(15.0F);
        float shown = clock.visualTick(clock.advance(6.55F));
        assertEquals(6.55F, clock.advance(6.0F), 1.0E-6F, "an older reading never moves the present back");
        assertEquals(6.55F, clock.confirmHit(6.0F, 2.0F), 1.0E-6F);
        // The next frames hold exactly the pose already drawn, for the whole frozen share.
        assertEquals(shown, clock.visualTick(clock.advance(6.6F)), 1.0E-6F);
        assertEquals(shown, clock.visualTick(clock.advance(6.55F + 2.0F * SwingClock.FREEZE_FRACTION - 0.01F)), 1.0E-6F);
        assertTrue(clock.inHitStop(8.5F));
        assertFalse(clock.inHitStop(8.55F));
        // Then the creep and the catch-up still end on the server total.
        assertEquals(6.55F + 0.8F * SwingClock.CREEP_RATE, clock.visualTick(8.55F), 1.0E-4F);
        assertEquals(15.0F, clock.visualTick(15.0F), 1.0E-4F);
    }

    @Test
    void anEarlyConfirmationStillWaitsForTheContactTick() {
        SwingClock clock = new SwingClock(13.0F);
        clock.advance(4.2F);
        assertEquals(4.9F, clock.confirmHit(4.9F, 2.0F), 1.0E-6F);
        assertEquals(4.5F, clock.visualTick(clock.advance(4.5F)), 1.0E-6F);
        assertEquals(4.9F, clock.visualTick(clock.advance(5.5F)), 1.0E-6F);
    }

    @Test
    void aConfirmationBeforeAnyReadingStartsAtContact() {
        SwingClock clock = new SwingClock(13.0F);
        assertEquals(Float.NEGATIVE_INFINITY, clock.present());
        assertEquals(4.9F, clock.confirmHit(4.9F, 2.0F), 1.0E-6F);
        SwingClock noRig = new SwingClock(13.0F);
        assertEquals(0.0F, noRig.confirmHit(Float.NEGATIVE_INFINITY, 2.0F), 1.0E-6F);
    }

    @Test
    void aStartCorrectedLaterHoldsThePoseInsteadOfRewinding() {
        // The server's start lands three ticks after the predicted one: the real tick drops from
        // 7 to 4. The present stays at 7 until the corrected timeline passes it.
        SwingClock clock = new SwingClock(17.0F);
        assertEquals(7.0F, clock.advance(7.0F), 1.0E-6F);
        assertEquals(7.0F, clock.advance(4.0F), 1.0E-6F);
        assertEquals(7.0F, clock.advance(6.9F), 1.0E-6F);
        assertEquals(7.5F, clock.advance(7.5F), 1.0E-6F);
    }

    @Test
    void lateOrRepeatedConfirmationsAreIgnored() {
        SwingClock clock = new SwingClock(11.0F);
        clock.advance(8.0F);
        assertTrue(Float.isNaN(clock.confirmHit(3.8F, 1.5F)), "too late to catch up before the server total");
        SwingClock early = new SwingClock(11.0F);
        assertEquals(3.8F, early.confirmHit(3.8F, 1.5F), 1.0E-6F);
        assertTrue(Float.isNaN(early.confirmHit(3.8F, 1.5F)), "one hit-stop per move");
    }

    @Test
    void emptyOrLateStopsAreIgnored() {
        SwingClock clock = new SwingClock(11.0F);
        assertFalse(clock.beginHitStop(3.0F, 0.0F));
        assertFalse(clock.beginHitStop(8.0F, 1.5F));
        assertFalse(clock.hasHitStop());
        assertTrue(clock.beginHitStop(3.8F, 1.5F));
        assertTrue(clock.hasHitStop());
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
