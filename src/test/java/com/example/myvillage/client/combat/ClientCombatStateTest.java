package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.CombatMode;
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
}
