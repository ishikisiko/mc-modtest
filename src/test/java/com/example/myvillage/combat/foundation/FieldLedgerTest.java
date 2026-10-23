package com.example.myvillage.combat.foundation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FieldLedgerTest {
    private static final UUID SOURCE = new UUID(1, 1), OTHER_SOURCE = new UUID(2, 2);
    private static final UUID TARGET = new UUID(3, 3), OTHER_TARGET = new UUID(4, 4);
    private static FieldLedger.Key key(UUID source, String kind) {
        return new FieldLedger.Key(source, kind, "ROOT", "myvillage:测试维度");
    }
    private static void root(FieldLedger ledger, long id, UUID source, String kind, long now) {
        ledger.add(id, key(source, kind), now, 60, 5, 2, true);
    }

    @Test void overlappingRootsShareAttemptsEvenWhenControlRejectedTheFirstAttempt() {
        var ledger = new FieldLedger();
        root(ledger, 1, SOURCE, "缠根", 0);
        root(ledger, 2, SOURCE, "缠根", 0);
        assertTrue(ledger.claim(1, TARGET, 0));
        assertFalse(ledger.claim(1, TARGET, 5));
        assertFalse(ledger.claim(2, TARGET, 5));
        assertEquals(1, ledger.trackedTargets(1));
        assertEquals(1, ledger.trackedTargets(2));
        ledger.remove(1);
        assertFalse(ledger.claim(2, TARGET, 10));
    }

    @Test void aDifferentSourceKindOrDimensionHasItsOwnLedger() {
        var ledger = new FieldLedger();
        root(ledger, 1, SOURCE, "缠根", 0);
        root(ledger, 2, OTHER_SOURCE, "缠根", 0);
        root(ledger, 3, SOURCE, "其他根缠", 0);
        ledger.add(4, new FieldLedger.Key(SOURCE, "缠根", "ROOT", "其他维度"), 0, 60, 5, 2, true);
        for (long id = 1; id <= 4; id++) assertTrue(ledger.claim(id, TARGET, 0));
    }

    @Test void recurringOverlapsShareTheSlowerCadenceWithoutCatchupBursts() {
        var ledger = new FieldLedger();
        var key = new FieldLedger.Key(SOURCE, "治疗", "HEAL", "主世界");
        ledger.add(1, key, 0, 100, 10, 2, false);
        ledger.add(2, key, 1, 100, 5, 2, false);
        assertTrue(ledger.claim(1, TARGET, 0));
        assertFalse(ledger.claim(2, TARGET, 1));
        assertFalse(ledger.claim(2, TARGET, 5));
        assertTrue(ledger.claim(2, TARGET, 10));
        assertFalse(ledger.claim(1, TARGET, 10));
        assertFalse(ledger.claim(2, TARGET, 10));
        assertTrue(ledger.claim(1, TARGET, 20));
        assertTrue(ledger.pulse(1, 0));
        assertFalse(ledger.pulse(1, 9));
        assertTrue(ledger.pulse(1, 70));
        assertFalse(ledger.pulse(1, 70));
        assertFalse(ledger.pulse(1, 79));
        assertTrue(ledger.pulse(1, 80));
    }

    @Test void lifetimeRecipientCapBoundsMemoryButAllowsExistingHealingRecipients() {
        var ledger = new FieldLedger();
        ledger.add(1, key(SOURCE, "治疗"), 0, 100, 5, 1, false);
        assertTrue(ledger.claim(1, TARGET, 0));
        assertFalse(ledger.claim(1, OTHER_TARGET, 5));
        assertTrue(ledger.claim(1, TARGET, 5));
        assertEquals(1, ledger.trackedTargets(1));
    }

    @Test void instanceCapsArePerSourceAndServerAndExpiredEntriesDoNotReserveCapacity() {
        var ledger = new FieldLedger();
        assertTrue(ledger.canAdd(SOURCE, 0, 2, 1));
        root(ledger, 1, SOURCE, "缠根", 0);
        assertFalse(ledger.canAdd(SOURCE, 0, 2, 1));
        assertTrue(ledger.canAdd(OTHER_SOURCE, 0, 2, 1));
        root(ledger, 2, OTHER_SOURCE, "缠根", 0);
        assertFalse(ledger.canAdd(new UUID(9, 9), 0, 2, 1));
        assertTrue(ledger.canAdd(SOURCE, 60, 2, 1));
        assertFalse(ledger.pulse(1, 60));
        assertFalse(ledger.claim(1, TARGET, 60));
    }

    @Test void ownerLifecycleDeletesOnlyOwnedFieldsAndPreservesRecipientReceipts() {
        var ledger = new FieldLedger();
        root(ledger, 1, SOURCE, "缠根", 0);
        root(ledger, 2, OTHER_SOURCE, "缠根", 0);
        assertTrue(ledger.claim(2, SOURCE, 0));
        ledger.forget(SOURCE);
        assertEquals(1, ledger.size());
        assertEquals(1, ledger.trackedTargets(2));
        assertFalse(ledger.claim(1, TARGET, 5));
        assertFalse(ledger.claim(2, SOURCE, 5));
        assertTrue(ledger.claim(2, TARGET, 5));
        ledger.clear();
        assertEquals(0, ledger.size());
        assertFalse(ledger.pulse(2, 10));
    }

    @Test void targetDeathOrDepartureDoesNotReleaseLifetimeCapacityOrAllowAnotherOneShot() {
        var ledger = new FieldLedger();
        root(ledger, 1, SOURCE, "缠根", 0);
        assertTrue(ledger.claim(1, TARGET, 0));
        assertTrue(ledger.claim(1, OTHER_TARGET, 0));
        // 死亡、卸载、离线、跨维度均经同一 forget 入口；UUID 回执留在存活领域。
        ledger.forget(TARGET);
        ledger.forget(OTHER_TARGET);
        assertEquals(2, ledger.trackedTargets(1));
        assertFalse(ledger.claim(1, TARGET, 5));
        assertFalse(ledger.claim(1, new UUID(5, 5), 5));
        ledger.remove(1);
        root(ledger, 2, SOURCE, "缠根", 60);
        assertTrue(ledger.claim(2, TARGET, 60));
        assertTrue(ledger.claim(2, new UUID(5, 5), 60));
    }

    @Test void expiryStartsANewIndependentRootWindowAndInvalidLifetimesAreRejected() {
        var ledger = new FieldLedger();
        root(ledger, 1, SOURCE, "缠根", 0);
        assertTrue(ledger.claim(1, TARGET, 0));
        root(ledger, 2, SOURCE, "缠根", 60);
        assertTrue(ledger.claim(2, TARGET, 60));
        assertThrows(IllegalArgumentException.class, () -> ledger.add(3, key(SOURCE, "缠根"), 0, 0, 5, 1, true));
        assertThrows(IllegalArgumentException.class, () -> ledger.add(3, key(SOURCE, "缠根"), Long.MAX_VALUE, 1, 5, 1, true));
        assertThrows(IllegalArgumentException.class, () -> ledger.add(3, key(SOURCE, "缠根"), 0, 10, 0, 1, true));
        assertThrows(IllegalArgumentException.class, () -> ledger.add(2, key(SOURCE, "缠根"), 0, 10, 5, 1, true));
    }
}
