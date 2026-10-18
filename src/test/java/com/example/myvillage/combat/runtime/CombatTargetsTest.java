package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.definition.CombatTestData;
import com.example.myvillage.combat.session.CombatSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class CombatTargetsTest {
    // Distances were sorted by the resolver: the first actual part touched wins for its owner.
    private record Contact(String part, int owner) {}

    @Test
    void multiplePartsConsumeOneSlotBeforeApplyingTheTargetBudget() {
        var wing = new Contact("wing", 10);
        var body = new Contact("body", 10);
        var head = new Contact("head", 10);
        var mob = new Contact("mob", 11);
        var fartherMob = new Contact("farther mob", 12);
        assertEquals(List.of(wing, mob), CombatTargets.firstDistinct(
                List.of(wing, body, head, mob, fartherMob), Contact::owner, 2));
        assertEquals(List.of(wing), CombatTargets.firstDistinct(
                List.of(wing, head, mob), Contact::owner, 1));
    }

    @Test
    void anOwnerAlreadyAttemptedCannotBeHitAgainByAnotherPartOnLaterTicks() {
        var session = new CombatSession(CombatTestData.basicSword());
        var head = new Contact("head", 100);
        var tail = new Contact("tail", 100);
        assertTrue(session.markAttempted(head.owner()));
        assertFalse(session.markAttempted(tail.owner()));
        assertTrue(session.wasAttempted(tail.owner()));
        assertEquals(1, session.attemptedTargetCount());
        assertEquals(1, session.remainingTargetCapacity(2));
        assertTrue(session.markAttempted(101));
        assertEquals(0, session.remainingTargetCapacity(2));
    }

    @Test
    void independentBossesAndOrdinaryTargetsKeepTheirContactOrderAndNoCapacityIsEmpty() {
        var one = new Contact("first boss head", 2);
        var two = new Contact("second boss head", 3);
        var mob = new Contact("ordinary mob", 4);
        var contacts = List.of(one, two, mob, new Contact("first boss tail", 2));
        assertEquals(List.of(one, two, mob), CombatTargets.firstDistinct(contacts, Contact::owner, 10));
        assertTrue(CombatTargets.firstDistinct(contacts, Contact::owner, 0).isEmpty());
    }
}
