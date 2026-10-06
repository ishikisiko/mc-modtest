package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.DodgeDirection;
import com.example.myvillage.combat.definition.CombatTestData;
import com.example.myvillage.combat.session.CombatStopReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class CombatDodgeFxTest {
    @AfterEach
    void reset() {
        CombatDodgeFx.clear();
        ClientCombatState.clear();
    }

    @Test
    void remainingTicksCountsDownToZeroAndNeverBelow() {
        assertEquals(8, CombatDodgeFx.remainingTicks(100L, 8, 100L));
        assertEquals(1, CombatDodgeFx.remainingTicks(100L, 8, 107L));
        assertEquals(0, CombatDodgeFx.remainingTicks(100L, 8, 108L));
        assertEquals(0, CombatDodgeFx.remainingTicks(100L, 8, 500L));
        // A start mapped ahead of now (never produced by the clock) still reports the whole dodge and more.
        assertEquals(10, CombatDodgeFx.remainingTicks(102L, 8, 100L));
    }

    @Test
    void afterimagesBurstOnLaunchThenThinOut() {
        int duration = 7;
        assertEquals(4, CombatDodgeFx.afterimageCount(1, duration, true));
        assertEquals(2, CombatDodgeFx.afterimageCount(1, duration, false));
        assertEquals(2, CombatDodgeFx.afterimageCount(3, duration, false));
        assertEquals(1, CombatDodgeFx.afterimageCount(4, duration, false));
        assertEquals(1, CombatDodgeFx.afterimageCount(6, duration, false));
        assertEquals(0, CombatDodgeFx.afterimageCount(7, duration, false));
        assertEquals(0, CombatDodgeFx.afterimageCount(7, duration, true));
        assertEquals(0, CombatDodgeFx.afterimageCount(-1, duration, true));
    }

    @Test
    void afterimageCadenceNeverIncreasesAfterLaunch() {
        for (int duration = 1; duration <= 12; duration++) {
            int previous = Integer.MAX_VALUE;
            int total = 0;
            for (int age = 1; age < duration; age++) {
                int count = CombatDodgeFx.afterimageCount(age, duration, false);
                assertTrue(count <= previous);
                assertTrue(count >= 1);
                previous = count;
                total += count;
            }
            assertTrue(total <= 2 * duration);
        }
    }

    @Test
    void afterimagesTrailBehindTheTravelDirection() {
        // Yaw 0 travels +Z, so the trail lies toward -Z.
        double[] behind = CombatDodgeFx.behindOffset(0.0F, 0.5);
        assertEquals(0.0, behind[0], 1.0E-9);
        assertEquals(-0.5, behind[1], 1.0E-9);
        // Yaw 90 travels -X, so the trail lies toward +X.
        behind = CombatDodgeFx.behindOffset(90.0F, 0.5);
        assertEquals(0.5, behind[0], 1.0E-9);
        assertEquals(0.0, behind[1], 1.0E-9);
    }

    @Test
    void trailOpposesEveryDodgeDirection() {
        float view = 37.0F;
        for (DodgeDirection direction : DodgeDirection.values()) {
            float yaw = direction.worldYaw(view);
            double radians = Math.toRadians(yaw);
            double travelX = -Math.sin(radians);
            double travelZ = Math.cos(radians);
            double[] behind = CombatDodgeFx.behindOffset(yaw, 1.0);
            assertEquals(-1.0, behind[0] * travelX + behind[1] * travelZ, 1.0E-6, direction.name());
        }
    }

    @Test
    void onlySidewaysDodgesLeanAndLeftAndRightLeanOpposite() {
        float view = -120.0F;
        assertEquals(0.0F, CombatDodgeFx.leanFor(DodgeDirection.FORWARD.worldYaw(view), view), 1.0E-4F);
        assertEquals(0.0F, CombatDodgeFx.leanFor(DodgeDirection.BACK.worldYaw(view), view), 1.0E-4F);
        assertEquals(0.0F, CombatDodgeFx.leanFor(DodgeDirection.NONE.worldYaw(view), view), 1.0E-4F);
        float left = CombatDodgeFx.leanFor(DodgeDirection.LEFT.worldYaw(view), view);
        float right = CombatDodgeFx.leanFor(DodgeDirection.RIGHT.worldYaw(view), view);
        assertEquals(CombatDodgeFx.LOCAL_LEAN_DEGREES, left, 1.0E-4F);
        assertEquals(-CombatDodgeFx.LOCAL_LEAN_DEGREES, right, 1.0E-4F);
        float diagonal = CombatDodgeFx.leanFor(DodgeDirection.FORWARD_LEFT.worldYaw(view), view);
        assertTrue(diagonal > 0.0F && diagonal < left);
        // Data cues allow at most 5 degrees of lean; the dodge stays well inside.
        assertTrue(Math.abs(left) <= 5.0F);
    }

    @Test
    void intentsAreThrottledToOneEveryFourTicks() {
        assertTrue(CombatDodgeFx.intentDue(Long.MIN_VALUE, 0L));
        assertFalse(CombatDodgeFx.intentDue(10L, 10L));
        assertFalse(CombatDodgeFx.intentDue(10L, 13L));
        assertTrue(CombatDodgeFx.intentDue(10L, 14L));
        assertTrue(CombatDodgeFx.intentDue(10L, 40L));
    }

    @Test
    void dodgesAreTrackedPerEntityAndForgotten() {
        CombatDodgeFx.start(7, 100L, 6, 45.0F);
        CombatDodgeFx.start(9, 101L, 8, -10.0F);
        assertTrue(CombatDodgeFx.active(7));
        assertEquals(101L, CombatDodgeFx.dodgeFor(9).startTick());
        // A newer dodge of the same entity replaces the old one.
        CombatDodgeFx.start(7, 130L, 7, 0.0F);
        assertEquals(130L, CombatDodgeFx.dodgeFor(7).startTick());
        CombatDodgeFx.forget(7);
        assertFalse(CombatDodgeFx.active(7));
        assertTrue(CombatDodgeFx.active(9));
        CombatDodgeFx.start(5, 100L, 0, 0.0F);
        assertNull(CombatDodgeFx.dodgeFor(5));
        CombatDodgeFx.clear();
        assertFalse(CombatDodgeFx.active(9));
    }

    @Test
    void localDodgeWindowSuppressesPredictionOnlyInsideIt() {
        assertFalse(ClientCombatState.localDodgeProtects(0L));
        ClientCombatState.markLocalDodge(105L);
        assertTrue(ClientCombatState.localDodgeProtects(100L));
        assertTrue(ClientCombatState.localDodgeProtects(104L));
        assertFalse(ClientCombatState.localDodgeProtects(105L));
        ClientCombatState.clear();
        assertFalse(ClientCombatState.localDodgeProtects(100L));
    }

    @Test
    void aDodgeStopKeepsTheServerSession() {
        assertFalse(ClientCombatEvents.resetsServerSession(CombatStopReason.DODGED));
        assertTrue(ClientCombatEvents.resetsServerSession(CombatStopReason.DEATH));
        assertTrue(ClientCombatEvents.resetsServerSession(CombatStopReason.LOGOUT));
    }

    @Test
    void aDodgedStopClearsTheLocalActionAndRestartsTheCombo() {
        // receiveAttackStop for DODGED (not COMPLETED, not a session reset) ends in clearActionAnimation.
        var spear = CombatTestData.basicSpear();
        ClientCombatState.beginPrediction(100);
        ClientCombatState.trackLocalAction(spear, 1, 100, 7);
        ClientCombatState.confirmPrediction(2);
        ClientCombatState.markReadyAnimation(spear.readyIdleAnimation());
        assertTrue(ClientCombatState.bufferClick(106, spear));
        assertTrue(ClientCombatState.acceptActionRevision(12, 7));

        ClientCombatState.clearActionAnimation();

        assertFalse(ClientCombatState.localActionActive());
        assertFalse(ClientCombatState.predictionPending());
        assertFalse(ClientCombatState.localClickBuffered());
        assertFalse(ClientCombatState.readyAnimation(), "the client tick claims the ready idle again");
        assertEquals(-1, ClientCombatState.localMoveIndex());
        assertEquals(0, ClientCombatState.predictedNextMoveIndex());
        // The session (and its revisions) carries on: a stale revision is still refused.
        assertFalse(ClientCombatState.acceptActionRevision(12, 6));
        assertTrue(ClientCombatState.acceptActionRevision(12, 8));
    }
}
