package com.example.myvillage.cultivation.meditation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryBatchRemovalTest {
    @Test
    void insufficientBatchDoesNotMutateAnySlot() {
        FakeSlots slots = new FakeSlots(1, 0, 1, 0);
        int[] original = slots.copy();

        assertFalse(InventoryBatchRemoval.has(slots, 3));
        assertTrue(InventoryBatchRemoval.remove(slots, 3).isEmpty());
        assertArrayEquals(original, slots.copy());
        assertEquals(0, slots.changeCount());
    }

    @Test
    void completeBatchSpansSlotsAndRollbackRestoresExactSnapshots() {
        FakeSlots slots = new FakeSlots(1, 0, 2, 4);
        int[] original = slots.copy();

        InventoryBatchRemoval.Batch<Integer> batch = InventoryBatchRemoval
                .remove(slots, 3)
                .orElseThrow();

        assertEquals(3, batch.count());
        assertEquals(2, batch.snapshots().size());
        assertArrayEquals(new int[]{0, 0, 0, 4}, slots.copy());

        InventoryBatchRemoval.restore(slots, batch);

        assertArrayEquals(original, slots.copy());
        assertEquals(2, slots.changeCount());
    }

    @Test
    void completeBatchRemovesOnlyRequestedCountUntilCommitted() {
        FakeSlots slots = new FakeSlots(0, 5, 0);

        InventoryBatchRemoval.Batch<Integer> batch = InventoryBatchRemoval
                .remove(slots, 3)
                .orElseThrow();

        assertArrayEquals(new int[]{0, 2, 0}, slots.copy());
        assertEquals(3, batch.count());
        assertEquals(1, batch.snapshots().size());
        assertEquals(1, slots.changeCount());
    }

    @Test
    void partialRemovalRestoresEveryTouchedSlot() {
        FlakySlots slots = new FlakySlots(2, 1);

        assertTrue(InventoryBatchRemoval.remove(slots, 3).isEmpty());

        assertArrayEquals(new int[]{2, 0}, slots.copy());
        assertEquals(1, slots.changeCount());
    }

    @Test
    void rejectedAndPreInstallFailuresRestoreTheCompleteBatch() {
        for (boolean throwBeforeInstall : new boolean[]{false, true}) {
            FakeSlots slots = new FakeSlots(1, 2);
            AtomicInteger installCalls = new AtomicInteger();
            InventoryBatchRemoval.TransactionResult<InventoryBatchRemoval.Batch<Integer>> result =
                    InventoryBatchRemoval.transact(
                            () -> InventoryBatchRemoval.remove(slots, 3),
                            batch -> InventoryBatchRemoval.restore(slots, batch),
                            () -> {
                                installCalls.incrementAndGet();
                                if (throwBeforeInstall) {
                                    throw new IllegalStateException("pre-install failure");
                                }
                                return false;
                            },
                            () -> false);

            assertEquals(
                    throwBeforeInstall
                            ? InventoryBatchRemoval.TransactionState.INSTALL_FAILED_BEFORE_COMMIT
                            : InventoryBatchRemoval.TransactionState.INSTALL_REJECTED,
                    result.state());
            assertFalse(result.committed());
            assertArrayEquals(new int[]{1, 2}, slots.copy());
            assertEquals(1, installCalls.get());
        }
    }

    @Test
    void postInstallFailureDoesNotRefundOrRetryTheBatch() {
        FakeSlots slots = new FakeSlots(3);
        AtomicBoolean installed = new AtomicBoolean();
        AtomicInteger installCalls = new AtomicInteger();

        InventoryBatchRemoval.TransactionResult<InventoryBatchRemoval.Batch<Integer>> result =
                InventoryBatchRemoval.transact(
                        () -> InventoryBatchRemoval.remove(slots, 3),
                        batch -> InventoryBatchRemoval.restore(slots, batch),
                        () -> {
                            installCalls.incrementAndGet();
                            installed.set(true);
                            throw new IllegalStateException("post-install snapshot failure");
                        },
                        installed::get);

        assertEquals(
                InventoryBatchRemoval.TransactionState.INSTALL_FAILED_AFTER_COMMIT,
                result.state());
        assertArrayEquals(new int[]{0}, slots.copy());
        assertEquals(1, installCalls.get());
        assertEquals("post-install snapshot failure", result.failure().getMessage());
    }

    @Test
    void successfulInstallCommitsOnceEvenWhenSnapshotDeliveryIsCaught() {
        FakeSlots slots = new FakeSlots(3);
        AtomicInteger installCalls = new AtomicInteger();
        AtomicInteger snapshotCalls = new AtomicInteger();

        InventoryBatchRemoval.TransactionResult<InventoryBatchRemoval.Batch<Integer>> result =
                InventoryBatchRemoval.transact(
                        () -> InventoryBatchRemoval.remove(slots, 3),
                        batch -> InventoryBatchRemoval.restore(slots, batch),
                        () -> {
                            installCalls.incrementAndGet();
                            try {
                                snapshotCalls.incrementAndGet();
                                throw new IllegalStateException("delivery failed");
                            } catch (IllegalStateException ignored) {
                                return true;
                            }
                        },
                        () -> true);

        assertTrue(result.committed());
        assertArrayEquals(new int[]{0}, slots.copy());
        assertEquals(1, installCalls.get());
        assertEquals(1, snapshotCalls.get());
    }

    @Test
    void externalSlotsDoNotFundAnOrdinaryInventoryTransaction() {
        FakeSlots ordinaryInventory = new FakeSlots(1);
        FakeSlots externalContainer = new FakeSlots(64);

        InventoryBatchRemoval.TransactionResult<InventoryBatchRemoval.Batch<Integer>> result =
                InventoryBatchRemoval.transact(
                        () -> InventoryBatchRemoval.remove(ordinaryInventory, 2),
                        batch -> InventoryBatchRemoval.restore(ordinaryInventory, batch),
                        () -> true,
                        () -> false);

        assertEquals(InventoryBatchRemoval.TransactionState.REMOVAL_FAILED, result.state());
        assertArrayEquals(new int[]{1}, ordinaryInventory.copy());
        assertArrayEquals(new int[]{64}, externalContainer.copy());
    }

    @Test
    void settlementGuardRejectsReentryUntilTheOwnerFinishes() {
        UUID playerId = UUID.randomUUID();
        assertTrue(MeditationManager.beginSettlement(playerId));
        assertFalse(MeditationManager.beginSettlement(playerId));

        MeditationManager.endSettlement(playerId);

        assertTrue(MeditationManager.beginSettlement(playerId));
        MeditationManager.endSettlement(playerId);
    }

    private static final class FakeSlots implements InventoryBatchRemoval.SlotAccess<Integer> {
        private final int[] counts;
        private int changeCount;

        private FakeSlots(int... counts) {
            this.counts = Arrays.copyOf(counts, counts.length);
        }

        @Override
        public int size() {
            return counts.length;
        }

        @Override
        public int matchingCount(int slot) {
            return counts[slot];
        }

        @Override
        public Integer snapshot(int slot) {
            return counts[slot];
        }

        @Override
        public void remove(int slot, int count) {
            counts[slot] -= count;
        }

        @Override
        public void restore(int slot, Integer snapshot) {
            counts[slot] = snapshot;
        }

        @Override
        public void changed() {
            changeCount++;
        }

        private int[] copy() {
            return Arrays.copyOf(counts, counts.length);
        }

        private int changeCount() {
            return changeCount;
        }
    }

    private static final class FlakySlots implements InventoryBatchRemoval.SlotAccess<Integer> {
        private final int[] counts;
        private int matchingReads;
        private int changeCount;

        private FlakySlots(int... counts) {
            this.counts = Arrays.copyOf(counts, counts.length);
        }

        @Override
        public int size() {
            return counts.length;
        }

        @Override
        public int matchingCount(int slot) {
            matchingReads++;
            if (matchingReads > counts.length && slot == counts.length - 1) {
                counts[slot] = 0;
            }
            return counts[slot];
        }

        @Override
        public Integer snapshot(int slot) {
            return counts[slot];
        }

        @Override
        public void remove(int slot, int count) {
            counts[slot] -= count;
        }

        @Override
        public void restore(int slot, Integer snapshot) {
            counts[slot] = snapshot;
        }

        @Override
        public void changed() {
            changeCount++;
        }

        private int[] copy() {
            return Arrays.copyOf(counts, counts.length);
        }

        private int changeCount() {
            return changeCount;
        }
    }
}
