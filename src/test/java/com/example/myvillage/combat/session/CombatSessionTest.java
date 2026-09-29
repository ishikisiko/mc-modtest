package com.example.myvillage.combat.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.BasicSwordStyle;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CombatSessionTest {
    private static final ResourceLocation WEAPON = BasicSwordStyle.QINGFENG_SWORD_ID;
    private static final ResourceLocation WORLD = ResourceLocation.withDefaultNamespace("overworld");

    private CombatSession session;

    @BeforeEach
    void setUp() {
        session = new CombatSession(BasicSwordStyle.DEFINITION);
    }

    @Test
    void fiveBufferedInputsAdvanceExactlyOneMoveAtATimeAndReset() {
        long tick = 100;
        CombatSession.StartEvent current = session.acceptIntent(tick, WEAPON, WORLD, 20.0F)
                .start().orElseThrow();
        for (int index = 0; index < 5; index++) {
            assertEquals(index, BasicSwordStyle.DEFINITION.indexOf(current.move().id()));

            int bufferTick = current.move().bufferStartTick();
            assertEquals(
                    CombatSession.IntentDecision.BUFFERED,
                    session.acceptIntent(tick + bufferTick, WEAPON, WORLD, 20.0F).decision());
            CombatSession.TickResult transition = session.tick(
                    tick + current.move().chainTick());
            assertTrue(transition.stop().isPresent());
            assertEquals(CombatStopReason.COMPLETED, transition.stop().orElseThrow().reason());
            if (index < 4) {
                current = transition.start().orElseThrow();
                assertEquals(tick + BasicSwordStyle.DEFINITION.move(index).chainTick(), current.startTick());
                tick = current.startTick();
            } else {
                assertTrue(transition.start().isPresent());
                assertEquals(
                        BasicSwordStyle.DEFINITION.move(0).id(),
                        transition.start().orElseThrow().move().id());
            }
        }
    }

    @Test
    void bufferedIntentStartsTheNextMoveAtTheChainTickNotBefore() {
        CombatSession.StartEvent first = session.acceptIntent(0, WEAPON, WORLD, 0.0F)
                .start().orElseThrow();
        int chainTick = first.move().chainTick();
        assertTrue(chainTick < first.move().totalTicks());
        assertEquals(
                CombatSession.IntentDecision.BUFFERED,
                session.acceptIntent(first.move().activeEndTick(), WEAPON, WORLD, 0.0F).decision());

        CombatSession.TickResult early = session.tick(chainTick - 1L, 45.0F);
        assertTrue(early.stop().isEmpty());
        assertTrue(early.start().isEmpty());

        CombatSession.TickResult chained = session.tick(chainTick, 45.0F);
        assertEquals(first.revision(), chained.stop().orElseThrow().revision());
        CombatSession.StartEvent second = chained.start().orElseThrow();
        assertEquals(BasicSwordStyle.DEFINITION.move(1).id(), second.move().id());
        assertEquals(chainTick, second.startTick());
        assertEquals(45.0F, second.facingYaw());
        assertEquals(0, session.actionTick(chainTick));
    }

    @Test
    void withoutABufferedIntentTheMovePlaysToItsTotal() {
        CombatSession.StartEvent first = session.acceptIntent(0, WEAPON, WORLD, 0.0F)
                .start().orElseThrow();
        assertTrue(session.tick(first.move().chainTick()).stop().isEmpty());
        assertTrue(session.hasActiveAction());

        CombatSession.TickResult done = session.tick(first.move().totalTicks());
        assertTrue(done.stop().isPresent());
        assertTrue(done.start().isEmpty());
        assertEquals(1, session.nextMoveIndex());
    }

    @Test
    void clickDuringTheStrikeIsHeldAndClickAfterTheChainTickStartsAtOnce() {
        CombatSession.StartEvent first = session.acceptIntent(0, WEAPON, WORLD, 0.0F)
                .start().orElseThrow();
        int late = first.move().chainTick() + 1;
        assertTrue(session.tick(late).stop().isEmpty());
        assertEquals(
                CombatSession.IntentDecision.BUFFERED,
                session.acceptIntent(late + 1L, WEAPON, WORLD, 0.0F).decision());
        CombatSession.TickResult chained = session.tick(late + 1L);
        assertEquals(late + 1L, chained.start().orElseThrow().startTick());
    }

    @Test
    void clickOnTheCompletionTickIsHeldInsteadOfDropped() {
        CombatSession.StartEvent first = session.acceptIntent(0, WEAPON, WORLD, 0.0F)
                .start().orElseThrow();
        long total = first.move().totalTicks();
        assertEquals(
                CombatSession.IntentDecision.BUFFERED,
                session.acceptIntent(total, WEAPON, WORLD, 0.0F).decision());
        assertEquals(
                BasicSwordStyle.DEFINITION.move(1).id(),
                session.tick(total).start().orElseThrow().move().id());
    }

    @Test
    void earlyAndSecondBufferedInputsCannotSkipMoves() {
        CombatSession.StartEvent start = session.acceptIntent(0, WEAPON, WORLD, 0.0F)
                .start().orElseThrow();

        assertEquals(
                CombatSession.IntentDecision.REJECTED_TIMING,
                session.acceptIntent(start.move().bufferStartTick() - 1L, WEAPON, WORLD, 0.0F).decision());
        assertEquals(
                CombatSession.IntentDecision.BUFFERED,
                session.acceptIntent(start.move().bufferStartTick(), WEAPON, WORLD, 0.0F).decision());
        assertEquals(
                CombatSession.IntentDecision.REJECTED_BUFFER_FULL,
                session.acceptIntent(start.move().bufferStartTick() + 1L, WEAPON, WORLD, 0.0F).decision());
        CombatSession.TickResult chained = session.tick(start.move().chainTick());
        assertEquals(
                BasicSwordStyle.DEFINITION.move(1).id(),
                chained.start().orElseThrow().move().id());
    }

    @Test
    void earlyClickDuringTheHitIsBufferedForEveryMove() {
        for (int index = 0; index < BasicSwordStyle.DEFINITION.moves().size(); index++) {
            var move = BasicSwordStyle.DEFINITION.move(index);
            assertTrue(move.acceptsBuffer(move.activeStartTick()), move.id().toString());
            assertTrue(move.acceptsBuffer(move.activeEndTick()), move.id().toString());
            assertTrue(!move.acceptsBuffer(move.activeStartTick() - 1), move.id().toString());
        }
    }

    @Test
    void missesStillContinueAndTimeoutResets() {
        CombatSession.StartEvent first = session.acceptIntent(0, WEAPON, WORLD, 0.0F)
                .start().orElseThrow();
        session.tick(first.move().totalTicks());
        assertEquals(1, session.nextMoveIndex());

        long expired = first.move().totalTicks() + BasicSwordStyle.COMBO_TIMEOUT_TICKS + 1L;
        CombatSession.StartEvent reset = session.acceptIntent(expired, WEAPON, WORLD, 0.0F)
                .start().orElseThrow();
        assertEquals(BasicSwordStyle.DEFINITION.move(0).id(), reset.move().id());
    }

    @Test
    void interruptionsClearTransientStateAndRetainOriginalEndForRecovery() {
        CombatSession.StartEvent start = session.acceptIntent(50, WEAPON, WORLD, 30.0F)
                .start().orElseThrow();
        assertTrue(session.markAttempted(42));
        assertFalse(session.markAttempted(42));

        CombatSession.StopEvent stop = session.interrupt(CombatStopReason.WEAPON_CHANGED)
                .orElseThrow();

        assertEquals(50 + start.move().totalTicks(), stop.originalEndTick());
        assertFalse(session.hasActiveAction());
        assertFalse(session.wasAttempted(42));
        assertEquals(0, session.nextMoveIndex());
    }

    @Test
    void durabilityAndBookkeepingCanBeClaimedOnlyOncePerAction() {
        session.acceptIntent(0, WEAPON, WORLD, 0.0F);

        assertTrue(session.markDurabilityCharged());
        assertFalse(session.markDurabilityCharged());
        assertTrue(session.markBookkeepingApplied());
        assertFalse(session.markBookkeepingApplied());
    }

    @Test
    void targetCapacityRemainsBoundedAcrossActiveTicks() {
        session.acceptIntent(10, WEAPON, WORLD, 0.0F);

        assertEquals(3, session.remainingTargetCapacity(3));
        assertTrue(session.markAttempted(30));
        assertTrue(session.markAttempted(10));
        assertEquals(2, session.attemptedTargetCount());
        assertEquals(1, session.remainingTargetCapacity(3));
        assertTrue(session.markAttempted(20));
        assertEquals(0, session.remainingTargetCapacity(3));
        assertFalse(session.markAttempted(20));
        assertEquals(0, session.remainingTargetCapacity(3));
    }
}
