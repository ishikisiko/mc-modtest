package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class ClientCombatStateTest {
    @AfterEach
    void clearState() {
        ClientCombatState.clear();
    }

    @Test
    void stalePreferenceAndActionRevisionsCannotReplaceNewerState() {
        assertTrue(ClientCombatState.replaceMode(CombatMode.CULTIVATION, 4));
        assertFalse(ClientCombatState.replaceMode(CombatMode.VANILLA, 3));
        assertTrue(ClientCombatState.acceptActionRevision(12, 8));
        assertFalse(ClientCombatState.acceptActionRevision(12, 7));
    }

    @Test
    void lifecycleSnapshotAndSessionRemovalPermitFreshActionRevisions() {
        assertTrue(ClientCombatState.replaceMode(CombatMode.CULTIVATION, 4));
        assertTrue(ClientCombatState.acceptActionRevision(12, 8));

        assertFalse(ClientCombatState.replaceMode(CombatMode.CULTIVATION, 4));
        assertTrue(ClientCombatState.acceptActionRevision(12, 1));

        ClientCombatState.resetActionRevision(12);
        assertTrue(ClientCombatState.acceptActionRevision(12, 0));
    }

    @Test
    void authoritativeActionPreventsPredictionFromReplacingCurrentMove() {
        ClientCombatState.beginPrediction(20);
        assertTrue(ClientCombatState.predictionPending());
        ClientCombatState.confirmPrediction(1);
        assertTrue(ClientCombatState.localActionActive());
        assertFalse(ClientCombatState.predictionPending());

        ClientCombatState.clearActionAnimation();
        assertFalse(ClientCombatState.localActionActive());
    }

    @Test
    void predictionResetsToFirstMoveAfterServerComboTimeout() {
        ClientCombatState.confirmPrediction(2);
        ClientCombatState.completeAction(40);

        assertEquals(2, ClientCombatState.preparePrediction(54, 14));
        assertEquals(0, ClientCombatState.preparePrediction(55, 14));
    }

    @Test
    void bufferedClickPredictsTheChainedMoveAtItsChainTick() {
        AttackMoveDefinition first = BasicSwordStyle.DEFINITION.move(0);
        ClientCombatState.trackLocalAction(0, 100, 5);
        ClientCombatState.confirmPrediction(1);

        // Anticipation clicks are not held, like the server.
        assertFalse(ClientCombatState.bufferClick(100 + first.bufferStartTick() - 1, BasicSwordStyle.DEFINITION));
        assertTrue(ClientCombatState.bufferClick(100 + first.bufferStartTick(), BasicSwordStyle.DEFINITION));
        // One slot only.
        assertFalse(ClientCombatState.bufferClick(100 + first.bufferStartTick() + 1, BasicSwordStyle.DEFINITION));

        assertEquals(-1, ClientCombatState.chainDue(100 + first.chainTick() - 1, BasicSwordStyle.DEFINITION));
        assertEquals(1, ClientCombatState.chainDue(100 + first.chainTick(), BasicSwordStyle.DEFINITION));

        ClientCombatState.beginChainPrediction(100 + first.chainTick(), 1);
        assertTrue(ClientCombatState.predictionPending());
        assertTrue(ClientCombatState.chainPredictionPending());
        assertTrue(ClientCombatState.localActionActive());
        assertEquals(1, ClientCombatState.localMoveIndex());
        assertEquals(-1, ClientCombatState.chainDue(200, BasicSwordStyle.DEFINITION));

        // The server's COMPLETED stop for move 1 is absorbed; its START for move 2 confirms.
        assertFalse(ClientCombatState.absorbChainSourceStop(4, 108));
        assertTrue(ClientCombatState.absorbChainSourceStop(5, 108));
        assertFalse(ClientCombatState.chainPredictionAbandoned(110, 2));
        ClientCombatState.trackLocalAction(1, 107, 6);
        ClientCombatState.confirmPrediction(2);
        assertFalse(ClientCombatState.predictionPending());
        assertEquals(1, ClientCombatState.localMoveIndexFor(6));
        assertEquals(-1, ClientCombatState.localMoveIndexFor(5));
    }

    @Test
    void chainPredictionWithoutServerStartIsAbandoned() {
        AttackMoveDefinition first = BasicSwordStyle.DEFINITION.move(0);
        ClientCombatState.trackLocalAction(0, 100, 5);
        ClientCombatState.confirmPrediction(1);
        assertTrue(ClientCombatState.bufferClick(100 + first.bufferStartTick(), BasicSwordStyle.DEFINITION));
        ClientCombatState.beginChainPrediction(100 + first.chainTick(), 1);
        assertTrue(ClientCombatState.absorbChainSourceStop(5, 111));

        assertTrue(ClientCombatState.chainPredictionAbandoned(114, 2));
        ClientCombatState.rejectPrediction();
        assertFalse(ClientCombatState.predictionPending());
        assertFalse(ClientCombatState.localActionActive());
        assertEquals(-1, ClientCombatState.localMoveIndex());
        // The server finished move 1 without chaining, so its next move is still move 2.
        assertEquals(1, ClientCombatState.preparePrediction(115, 14));
    }

    @Test
    void cuesFireOncePerActionEvenAfterAStartCorrection() {
        ClientCombatState.beginPrediction(100);
        ClientCombatState.trackLocalAction(1, 100, -1L);
        assertFalse(ClientCombatState.reachCue(102, 3));
        ClientCombatState.markCuesThrough(102);
        assertTrue(ClientCombatState.reachCue(103, 3));
        ClientCombatState.markCuesThrough(103);

        // The server START moves the start one tick later; the cue already played.
        ClientCombatState.trackLocalAction(1, 101, 7);
        ClientCombatState.confirmPrediction(2);
        assertFalse(ClientCombatState.reachCue(104, 3));
    }
}
