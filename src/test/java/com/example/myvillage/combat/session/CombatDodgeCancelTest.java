package com.example.myvillage.combat.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.CombatTestData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CombatDodgeCancelTest {
    private static final ResourceLocation WEAPON = ResourceLocation.fromNamespaceAndPath("myvillage", "test_weapon");
    private static final ResourceLocation WORLD = ResourceLocation.withDefaultNamespace("overworld");

    @Test
    void noSessionOrNoActionAlwaysAllowsADodge() {
        assertTrue(CombatSessionManager.dodgeCancelAllowed(null, 100L));
        assertTrue(CombatSessionManager.dodgeCancelAllowed(new CombatSession(CombatTestData.basicSword()), 100L));
    }

    @Test
    void onlyTheRecoveryAfterTheLastActiveTickCanBeCancelled() {
        CombatSession session = new CombatSession(CombatTestData.basicSword());
        long start = 1_000L;
        session.acceptIntent(start, WEAPON, WORLD, 0.0F);
        AttackMoveDefinition move = session.currentMove();

        assertFalse(CombatSessionManager.dodgeCancelAllowed(session, start));
        assertFalse(CombatSessionManager.dodgeCancelAllowed(session, start + move.activeStartTick()));
        assertFalse(CombatSessionManager.dodgeCancelAllowed(session, start + move.activeEndTick()));
        assertTrue(CombatSessionManager.dodgeCancelAllowed(session, start + move.activeEndTick() + 1));
        assertTrue(CombatSessionManager.dodgeCancelAllowed(session, start + move.totalTicks() - 1));
    }

    @Test
    void aDodgeInterruptResetsTheCombo() {
        CombatSession session = new CombatSession(CombatTestData.basicSword());
        session.acceptIntent(0L, WEAPON, WORLD, 0.0F);
        AttackMoveDefinition first = session.currentMove();
        session.tick(first.totalTicks());
        assertEquals(1, session.nextMoveIndex());

        session.acceptIntent(first.totalTicks() + 1L, WEAPON, WORLD, 0.0F);
        CombatSession.StopEvent stop = session.interrupt(CombatStopReason.DODGED).orElseThrow();
        assertEquals(CombatStopReason.DODGED, stop.reason());
        assertFalse(session.hasActiveAction());
        assertEquals(0, session.nextMoveIndex());
        assertEquals(CombatStopReason.DODGED, session.lastStopReason());
    }
}
