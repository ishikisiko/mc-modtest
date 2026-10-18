package com.example.myvillage.cultivation.meditation;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import com.example.myvillage.spiritstone.SpiritStoneSources;
import com.example.myvillage.spiritstone.SpiritStoneStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class InventoryBatchRemoval {
    private InventoryBatchRemoval() {
    }

    static boolean has(Player player, TagKey<Item> item, int requiredCount) {
        return has(new MinecraftSlots(SpiritStoneSources.slots(player, false), player.getInventory()::setChanged, item), requiredCount);
    }
    static Optional<Removal> remove(Player player, TagKey<Item> item, int requiredCount) {
        MinecraftSlots slots = new MinecraftSlots(SpiritStoneSources.slots(player, false), player.getInventory()::setChanged, item);
        return remove(slots, requiredCount).map(batch -> new Removal(slots, batch));
    }
    static void restore(Player player, Removal removal) {
        if (removal != null) restore(removal.slots(), removal.batch());
    }
    static boolean has(Inventory inventory, TagKey<Item> item, int requiredCount) {
        return has(new MinecraftSlots(inventory, item), requiredCount);
    }

    static Optional<Removal> remove(Inventory inventory, TagKey<Item> item, int requiredCount) {
        MinecraftSlots slots = new MinecraftSlots(inventory, item);
        return remove(slots, requiredCount).map(batch -> new Removal(slots, batch));
    }

    static void restore(Inventory inventory, Removal removal) {
        if (removal == null) {
            return;
        }
        restore(removal.slots(), removal.batch());
    }

    static <S> boolean has(SlotAccess<S> slots, int requiredCount) {
        Objects.requireNonNull(slots, "slots");
        if (requiredCount <= 0) {
            return false;
        }
        long found = 0;
        for (int slot = 0; slot < slots.size(); slot++) {
            found += slots.matchingCount(slot);
            if (found >= requiredCount) {
                return true;
            }
        }
        return false;
    }

    static <S> Optional<Batch<S>> remove(SlotAccess<S> slots, int requiredCount) {
        Objects.requireNonNull(slots, "slots");
        if (!has(slots, requiredCount)) {
            return Optional.empty();
        }

        int remaining = requiredCount;
        List<SlotSnapshot<S>> snapshots = new ArrayList<>();
        for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
            int available = slots.matchingCount(slot);
            if (available <= 0) {
                continue;
            }
            snapshots.add(new SlotSnapshot<>(slot, slots.snapshot(slot)));
            int removed = Math.min(remaining, available);
            slots.remove(slot, removed);
            remaining -= removed;
        }
        Batch<S> batch = new Batch<>(List.copyOf(snapshots), requiredCount - remaining);
        if (remaining != 0) {
            restore(slots, batch);
            return Optional.empty();
        }
        slots.changed();
        return Optional.of(batch);
    }

    static <S> void restore(SlotAccess<S> slots, Batch<S> batch) {
        Objects.requireNonNull(slots, "slots");
        if (batch == null || batch.count() == 0) {
            return;
        }
        for (SlotSnapshot<S> snapshot : batch.snapshots()) {
            slots.restore(snapshot.slot(), snapshot.original());
        }
        slots.changed();
    }

    static <R> TransactionResult<R> transact(
            Supplier<Optional<R>> removal,
            Consumer<R> restore,
            BooleanSupplier install,
            BooleanSupplier installedAfterFailure) {
        Objects.requireNonNull(removal, "removal");
        Objects.requireNonNull(restore, "restore");
        Objects.requireNonNull(install, "install");
        Objects.requireNonNull(installedAfterFailure, "installedAfterFailure");

        R removed = removal.get().orElse(null);
        if (removed == null) {
            return new TransactionResult<>(TransactionState.REMOVAL_FAILED, null, null);
        }
        try {
            if (install.getAsBoolean()) {
                return new TransactionResult<>(TransactionState.COMMITTED, removed, null);
            }
            restore.accept(removed);
            return new TransactionResult<>(TransactionState.INSTALL_REJECTED, removed, null);
        } catch (RuntimeException exception) {
            if (installedAfterFailure.getAsBoolean()) {
                return new TransactionResult<>(
                        TransactionState.INSTALL_FAILED_AFTER_COMMIT, removed, exception);
            }
            restore.accept(removed);
            return new TransactionResult<>(
                    TransactionState.INSTALL_FAILED_BEFORE_COMMIT, removed, exception);
        }
    }

    interface SlotAccess<S> {
        int size();

        int matchingCount(int slot);

        S snapshot(int slot);

        void remove(int slot, int count);

        void restore(int slot, S snapshot);

        void changed();
    }

    record SlotSnapshot<S>(int slot, S original) {
        SlotSnapshot {
            if (slot < 0) {
                throw new IllegalArgumentException("Inventory slot must be non-negative");
            }
            Objects.requireNonNull(original, "original");
        }
    }

    record Batch<S>(List<SlotSnapshot<S>> snapshots, int count) {
        Batch {
            snapshots = List.copyOf(Objects.requireNonNull(snapshots, "snapshots"));
            if (count < 0) {
                throw new IllegalArgumentException("Removed count must be non-negative");
            }
        }
    }

    record Removal(MinecraftSlots slots, Batch<ItemStackSnapshot> batch) {
        Removal {
            Objects.requireNonNull(slots, "slots");
            Objects.requireNonNull(batch, "batch");
        }

        int count() {
            return batch.count();
        }
    }

    enum TransactionState {
        COMMITTED,
        REMOVAL_FAILED,
        INSTALL_REJECTED,
        INSTALL_FAILED_BEFORE_COMMIT,
        INSTALL_FAILED_AFTER_COMMIT
    }

    record TransactionResult<R>(TransactionState state, R removal, RuntimeException failure) {
        TransactionResult {
            Objects.requireNonNull(state, "state");
            if (state == TransactionState.REMOVAL_FAILED && removal != null) {
                throw new IllegalArgumentException("A failed removal cannot expose a removed batch");
            }
            if (state != TransactionState.REMOVAL_FAILED && removal == null) {
                throw new IllegalArgumentException("A completed removal must expose its batch");
            }
            boolean failedWithException = state == TransactionState.INSTALL_FAILED_BEFORE_COMMIT
                    || state == TransactionState.INSTALL_FAILED_AFTER_COMMIT;
            if (failedWithException != (failure != null)) {
                throw new IllegalArgumentException("Transaction failure state and exception must agree");
            }
        }

        boolean committed() {
            return state == TransactionState.COMMITTED;
        }
    }

    private record ItemStackSnapshot(Item item, ItemStack stack) {
        private ItemStackSnapshot {
            Objects.requireNonNull(item, "item");
            stack = Objects.requireNonNull(stack, "stack").copy();
        }
    }

    private static final class MinecraftSlots implements SlotAccess<ItemStackSnapshot> {
        private final List<SpiritStoneSources.Slot> slots;
        private final Runnable changed;
        private final TagKey<Item> item;
        private MinecraftSlots(Inventory inventory, TagKey<Item> item) {
            this(SpiritStoneSources.inventorySlots(inventory), inventory::setChanged, item);
        }
        private MinecraftSlots(List<SpiritStoneSources.Slot> slots, Runnable changed, TagKey<Item> item) {
            this.slots = List.copyOf(slots); this.changed = changed; this.item = item;
        }
        @Override public int size() { return slots.size(); }
        @Override public int matchingCount(int slot) {
            return SpiritStoneStorage.count(slots.get(slot).read().get(), stack -> stack.is(item));
        }
        @Override public ItemStackSnapshot snapshot(int slot) {
            var stack = slots.get(slot).read().get(); return new ItemStackSnapshot(stack.getItem(), stack);
        }
        @Override public void remove(int slot, int count) {
            var copy = slots.get(slot).read().get().copy();
            if (!SpiritStoneStorage.remove(List.of(copy), stack -> stack.is(item), count))
                throw new IllegalStateException("Spirit stone source changed during payment");
            slots.get(slot).write().accept(copy.isEmpty() ? ItemStack.EMPTY : copy);
        }
        @Override public void restore(int slot, ItemStackSnapshot snapshot) { slots.get(slot).write().accept(snapshot.stack().copy()); }
        @Override public void changed() { changed.run(); }
    }
}
