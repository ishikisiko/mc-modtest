package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.myvillage.combat.definition.CombatTestData;
import com.example.myvillage.combat.definition.CombatStyleDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Server ticks enter the local clock once, when the START arrives; a later reset of the client's
 * game clock moves neither the swing nor the chain prediction.
 */
final class ClientCombatClockTest {
    @AfterEach
    void clearState() {
        ClientCombatState.clear();
        ClientCombatClock.setForTest(0L);
    }

    @Test
    void aServerTickMapsToTheLocalTickItElapsedFrom() {
        ClientCombatClock.setForTest(500L);
        assertEquals(498L, ClientCombatClock.fromServer(1000L, 1002L), "two ticks ago by the game clock");
        assertEquals(500L, ClientCombatClock.fromServer(1003L, 1002L), "never in the future");
        ClientCombatClock.advance();
        assertEquals(501.25, ClientCombatClock.now(0.25F), 1.0E-9);
    }

    /**
     * The sweep's START maps the server's start once. The chain tick then comes ten local ticks
     * later whatever the game clock does meanwhile: a reset of +2 or -1 used to make the client
     * predict the chained move two ticks early or one late.
     */
    @Test
    void aGameClockResetDoesNotMoveTheChainPrediction() {
        CombatStyleDefinition spear = CombatTestData.basicSpear();
        int chainTick = spear.move(1).chainTick();
        for (int reset : new int[] {2, 1, 0, -1}) {
            ClientCombatState.clear();
            ClientCombatClock.setForTest(100L);
            long gameTime = 3000L;
            ClientCombatState.beginPrediction(ClientCombatClock.ticks());
            ClientCombatState.trackLocalAction(spear, 1, ClientCombatClock.ticks(), -1L);
            // The START: the server started at game tick 3000, read when the client's clock says 3001.
            ClientCombatClock.advance();
            gameTime++;
            ClientCombatState.trackLocalAction(spear, 1, ClientCombatClock.fromServer(3000L, gameTime), 7L);
            ClientCombatState.confirmPrediction(2);
            ClientCombatState.bufferClick(ClientCombatClock.ticks() + 5, spear);
            int predictedAt = -1;
            for (int tick = 2; tick <= chainTick + 3 && predictedAt < 0; tick++) {
                ClientCombatClock.advance();
                gameTime += tick == 4 ? 1 + reset : 1; // the time packet lands at local tick 104
                if (ClientCombatState.chainDue(ClientCombatClock.ticks(), spear) >= 0) {
                    predictedAt = (int) (ClientCombatClock.ticks() - 100L);
                }
            }
            assertEquals(chainTick, predictedAt, "reset " + reset);
        }
    }
}
