package com.example.myvillage.combat.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import org.junit.jupiter.api.Test;

class CombatSessionManagerCommitmentTest {
    @Test
    void commitmentIsHeavyThroughTheStrikeLighterInRecoveryAndGoneAtTheChainTick() {
        AttackMoveDefinition thrust = BasicSwordStyle.DEFINITION.move(0);
        assertEquals(-0.75, CombatSessionManager.commitmentAt(thrust, 0));
        assertEquals(-0.75, CombatSessionManager.commitmentAt(thrust, thrust.activeEndTick()));
        assertEquals(-0.4, CombatSessionManager.commitmentAt(thrust, thrust.activeEndTick() + 1));
        assertEquals(-0.4, CombatSessionManager.commitmentAt(thrust, thrust.chainTick() - 1));
        assertEquals(0.0, CombatSessionManager.commitmentAt(thrust, thrust.chainTick()));
    }

    @Test
    void finisherStaysCommittedUntilItsEnd() {
        AttackMoveDefinition lunge = BasicSwordStyle.DEFINITION.move(4);
        assertEquals(-0.4, CombatSessionManager.commitmentAt(lunge, lunge.totalTicks() - 1));
        assertEquals("myvillage:combat_commit", CombatSessionManager.COMMIT_MODIFIER_ID.toString());
    }
}
