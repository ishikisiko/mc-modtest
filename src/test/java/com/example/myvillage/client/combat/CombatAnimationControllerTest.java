package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.client.combat.CombatAnimationController.Lifecycle;
import com.example.myvillage.client.combat.CombatAnimationController.Lifecycle.Phase;
import com.example.myvillage.client.combat.CombatAnimationController.RemoteHitStop;
import org.junit.jupiter.api.Test;

final class CombatAnimationControllerTest {
    @Test
    void predictedMoveIsKeptWhenTheAuthoritativeStartIsWithinOneTick() {
        assertFalse(CombatAnimationController.resyncNeeded(2.4F, 2.0F));
        assertFalse(CombatAnimationController.resyncNeeded(1.0F, 2.0F));
        assertTrue(CombatAnimationController.resyncNeeded(0.5F, 2.0F));
        assertTrue(CombatAnimationController.resyncNeeded(4.2F, 3.0F));
    }

    @Test
    void chainedStartsCrossFadeAndFreshStartsDoNot() {
        assertEquals(0, CombatAnimationController.startFadeTicks(Phase.NONE, false, false));
        assertEquals(CombatAnimationController.CHAIN_FADE_TICKS,
                CombatAnimationController.startFadeTicks(Phase.MOVE, true, false));
        // The server sends STOP COMPLETED and the chained START in the same tick.
        assertEquals(CombatAnimationController.CHAIN_FADE_TICKS,
                CombatAnimationController.startFadeTicks(Phase.STOPPING, true, false));
        assertEquals(CombatAnimationController.START_FADE_TICKS,
                CombatAnimationController.startFadeTicks(Phase.IDLE, true, false));
        assertEquals(CombatAnimationController.START_FADE_TICKS,
                CombatAnimationController.startFadeTicks(Phase.MOVE, true, true));
        assertEquals(2, CombatAnimationController.CHAIN_FADE_TICKS);
        assertTrue(CombatAnimationController.SETTLE_FADE_TICKS >= 3
                && CombatAnimationController.SETTLE_FADE_TICKS <= 4);
    }

    @Test
    void stopIsGracefulUntilSomethingTakesOver() {
        Lifecycle lifecycle = new Lifecycle();
        assertFalse(lifecycle.requestStop(100L), "nothing playing stops at once");

        lifecycle.startMove();
        assertTrue(lifecycle.reportsActive());
        assertTrue(lifecycle.requestStop(100L));
        assertEquals(Phase.STOPPING, lifecycle.phase());
        assertFalse(lifecycle.reportsActive(), "the client may claim the ready idle while stopping");
        assertFalse(lifecycle.expired(100L + Lifecycle.STOP_GRACE_TICKS));
        assertTrue(lifecycle.expired(101L + Lifecycle.STOP_GRACE_TICKS));

        // A second stop does not extend the grace period.
        assertTrue(lifecycle.requestStop(101L));
        assertTrue(lifecycle.expired(101L + Lifecycle.STOP_GRACE_TICKS));

        // A chained START takes over.
        lifecycle.startMove();
        assertEquals(Phase.MOVE, lifecycle.phase());
        assertFalse(lifecycle.expired(Long.MAX_VALUE));
    }

    @Test
    void finishedMoveHoldsTheReadyIdleUntilItsStopArrives() {
        Lifecycle lifecycle = new Lifecycle();
        assertFalse(lifecycle.onTriggeredFinished(10L), "an idle layer keeps PAL stopped");

        lifecycle.startMove();
        assertTrue(lifecycle.onTriggeredFinished(20L));
        assertEquals(Phase.HELD_IDLE, lifecycle.phase());
        assertTrue(lifecycle.reportsActive(), "the move is still active until the server stops it");
        assertTrue(lifecycle.holdsIdle());
        assertTrue(lifecycle.onTriggeredFinished(21L), "repeated handler calls keep holding");
        assertFalse(lifecycle.expired(20L + Lifecycle.AWAIT_STOP_TICKS));

        assertTrue(lifecycle.requestStop(22L));
        assertEquals(Phase.STOPPING, lifecycle.phase());
        assertTrue(lifecycle.holdsIdle(), "the held idle can be claimed without a restart");

        lifecycle.idle();
        assertEquals(Phase.IDLE, lifecycle.phase());
        assertTrue(lifecycle.reportsActive());
        assertFalse(lifecycle.expired(Long.MAX_VALUE));
    }

    @Test
    void lostStopEventuallySnapsOff() {
        Lifecycle lifecycle = new Lifecycle();
        lifecycle.startMove();
        lifecycle.onTriggeredFinished(50L);
        assertTrue(lifecycle.expired(51L + Lifecycle.AWAIT_STOP_TICKS));
        lifecycle.reset();
        assertEquals(Phase.NONE, lifecycle.phase());
        assertFalse(lifecycle.reportsActive());
        assertFalse(lifecycle.expired(Long.MAX_VALUE));
    }

    @Test
    void modeEntryHandsOverToTheReadyIdle() {
        Lifecycle lifecycle = new Lifecycle();
        lifecycle.startEnter();
        assertTrue(lifecycle.reportsActive());
        assertTrue(lifecycle.onTriggeredFinished(5L));
        assertEquals(Phase.STOPPING, lifecycle.phase());
        assertFalse(lifecycle.reportsActive(), "ClientCombatEvents claims the ready idle next tick");
        assertTrue(lifecycle.expired(6L + Lifecycle.STOP_GRACE_TICKS));
    }

    @Test
    void remoteHitStopFreezesThenRepaysTheLostTime() {
        RemoteHitStop hitStop = new RemoteHitStop();
        assertEquals(1.0F, hitStop.advance(0L));

        hitStop.set(0.0F, 10L);
        float played = 0.0F;
        played += hitStop.advance(10L);
        played += hitStop.advance(11L);
        assertEquals(0.0F, played, 1.0E-6F, "the body freezes while the rate is fresh");
        assertEquals(2.0F, hitStop.debt(), 1.0E-6F);

        for (long tick = 12L; tick < 30L; tick++) {
            float speed = hitStop.advance(tick);
            assertTrue(speed <= 1.0F + RemoteHitStop.CATCH_UP_BONUS + 1.0E-6F);
            played += speed;
        }
        assertEquals(20.0F, played, 1.0E-4F, "after catching up the body is back on the server clock");
        assertEquals(0.0F, hitStop.debt(), 1.0E-6F);
    }

    @Test
    void remoteHitStopHonoursExplicitRatesAndClears() {
        RemoteHitStop hitStop = new RemoteHitStop();
        hitStop.set(0.15F, 0L);
        assertEquals(0.15F, hitStop.advance(0L), 1.0E-6F);
        hitStop.set(1.0F, 1L);
        assertEquals(1.0F + RemoteHitStop.CATCH_UP_BONUS, hitStop.advance(1L), 1.0E-6F);
        hitStop.set(1.5F, 2L);
        assertEquals(1.5F, hitStop.advance(2L), 1.0E-6F);
        assertEquals(0.0F, hitStop.debt(), 1.0E-6F, "an explicit catch-up rate repays the debt");

        hitStop.set(-3.0F, 5L);
        assertEquals(0.0F, hitStop.advance(5L), 1.0E-6F);
        hitStop.set(99.0F, 6L);
        assertEquals(CombatAnimationController.MAX_PLAYBACK_RATE, hitStop.advance(6L), 1.0E-6F);
        hitStop.clear();
        assertEquals(1.0F, hitStop.advance(7L), 1.0E-6F);
        assertEquals(1.0F, CombatAnimationController.clampRate(Float.NaN));
    }
}
