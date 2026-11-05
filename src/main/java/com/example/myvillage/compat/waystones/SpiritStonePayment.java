package com.example.myvillage.compat.waystones;

import com.example.myvillage.spiritstone.SpiritStoneSources;
import com.example.myvillage.spiritstone.SpiritStoneStorage;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;

/** Exact-item payment. No denomination exchange, stack expansion, or writes during a quote. */
final class SpiritStonePayment {
    private SpiritStonePayment() { }

    static BigInteger balance(SpiritStoneSources.Access access, ItemStack item) {
        return access.copies().stream()
                .map(stack -> SpiritStoneStorage.count(stack, candidate -> ItemStack.isSameItemSameComponents(item, candidate)))
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    static Optional<Receipt> debit(SpiritStoneSources.Access access, ItemStack item, int count) {
        if (count <= 0) throw new IllegalArgumentException("Payment must be positive");
        var before = access.copies();
        var after = copies(before);
        if (!SpiritStoneStorage.remove(after, candidate -> ItemStack.isSameItemSameComponents(item, candidate), count)
                || !access.commit(after)) return Optional.empty();
        return Optional.of(new Receipt(item.copyWithCount(1), count, before, after));
    }

    private static List<ItemStack> copies(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    private static boolean matches(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) if (!ItemStack.matches(first.get(i), second.get(i))) return false;
        return true;
    }

    static final class Receipt {
        private final ItemStack item;
        private final int count;
        private final List<ItemStack> before;
        private final List<ItemStack> after;
        private boolean settled;

        private Receipt(ItemStack item, int count, List<ItemStack> before, List<ItemStack> after) {
            this.item = item;
            this.count = count;
            this.before = copies(before);
            this.after = copies(after);
        }

        /** Complete and explicit rollback share this guard; a repeated notification never refunds twice. */
        void finish(boolean successful, Supplier<SpiritStoneSources.Access> source, Consumer<ItemStack> overflow) {
            if (settled) return;
            if (successful) {
                settled = true;
                return;
            }
            var access = source.get();
            var current = access.copies();
            // The usual failure occurs in the same server operation: restore the exact slots and bag components.
            if (matches(current, after) && access.commit(copies(before))) {
                settled = true;
                return;
            }
            // An event may have moved an item. Refund only our currency; never overwrite those other changes.
            int remaining = SpiritStoneStorage.insertBags(current, item, count, false);
            remaining = SpiritStoneStorage.insertLoose(current, item, remaining, Math.min(36, current.size()));
            if (!access.commit(current)) throw new IllegalStateException("Spirit stone refund inventory changed during commit");
            settled = true;
            while (remaining > 0) {
                int amount = Math.min(item.getMaxStackSize(), remaining);
                overflow.accept(item.copyWithCount(amount));
                remaining -= amount;
            }
        }
    }
}
