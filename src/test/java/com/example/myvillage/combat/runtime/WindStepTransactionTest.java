package com.example.myvillage.combat.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WindStepTransactionTest {
    @Test void failedPaymentNeitherCancelsAnAttackNorMoves() {
        List<String> calls = new ArrayList<>();
        var rejected = CombatDodgeService.commitAirDodge(
                () -> { calls.add("pay"); return false; },
                () -> { calls.add("cancel"); return true; },
                () -> calls.add("refund"), () -> calls.add("move"));
        assertEquals(Optional.of(CombatDodgeService.Rejection.NO_POWER), rejected);
        assertEquals(List.of("pay"), calls);
    }

    @Test void unexpectedCancelFailureRefundsEverythingWithoutSubmittingTheMove() {
        AtomicInteger power = new AtomicInteger(10);
        List<String> calls = new ArrayList<>();
        var rejected = CombatDodgeService.commitAirDodge(
                () -> { calls.add("pay"); power.addAndGet(-10); return true; },
                () -> { calls.add("cancel"); return false; },
                () -> { calls.add("refund"); power.addAndGet(10); },
                () -> calls.add("move"));
        assertEquals(Optional.of(CombatDodgeService.Rejection.TIMING), rejected);
        assertEquals(10, power.get());
        assertEquals(List.of("pay", "cancel", "refund"), calls);
    }

    @Test void successfulCommitPaysAndCancelsBeforeMovingExactlyOnce() {
        List<String> calls = new ArrayList<>();
        var accepted = CombatDodgeService.commitAirDodge(
                () -> { calls.add("pay"); return true; },
                () -> { calls.add("cancel"); return true; },
                () -> calls.add("refund"), () -> calls.add("move"));
        assertTrue(accepted.isEmpty());
        assertEquals(List.of("pay", "cancel", "move"), calls);
    }
}
