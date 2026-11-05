package com.example.myvillage.compat.waystones;

import com.example.myvillage.spiritstone.SpiritStoneSources;
import com.example.myvillage.spiritstone.SpiritStoneStorage;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.blay09.mods.waystones.requirement.InventoryItemResolver;
import net.minecraft.world.item.ItemStack;

/** Exact-item payment. No denomination exchange, stack expansion, or writes during a quote. */
final class SpiritStonePayment {
    private static final int MAX_NESTING_DEPTH = 32;
    private SpiritStonePayment() { }

    static BigInteger balance(SpiritStoneSources.Access access, ItemStack item) {
        return access.copies().stream()
                .map(stack -> count(stack, item, 0))
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    static Optional<Receipt> debit(SpiritStoneSources.Access access, ItemStack item, int count) {
        if (count <= 0) throw new IllegalArgumentException("Payment must be positive");
        var before = access.copies();
        var after = copies(before);
        if (after.stream().map(stack -> count(stack, item, 0)).reduce(BigInteger.ZERO, BigInteger::add)
                .compareTo(BigInteger.valueOf(count)) < 0) return Optional.empty();
        int remaining = count;
        // Preserve the native preference for loose inventory items before nested storage.
        for (var stack : after) if (!SpiritStoneStorage.isBag(stack) && ItemStack.isSameItemSameComponents(item, stack)) {
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        for (var stack : after) remaining = remove(stack, item, remaining, 0);
        if (remaining != 0 || !access.commit(after)) return Optional.empty();
        return Optional.of(new Receipt(item.copyWithCount(1), count, before, after));
    }

    private static BigInteger count(ItemStack stack, ItemStack item, int depth) {
        if (stack.isEmpty()) return BigInteger.ZERO;
        if (SpiritStoneStorage.isBag(stack)) return SpiritStoneStorage.count(stack,
                candidate -> ItemStack.isSameItemSameComponents(item, candidate));
        BigInteger result = ItemStack.isSameItemSameComponents(item, stack) ? BigInteger.valueOf(stack.getCount()) : BigInteger.ZERO;
        if (depth < MAX_NESTING_DEPTH) for (var handler : InventoryItemResolver.NESTED_COMPONENT_HANDLERS) {
            for (var child : handler.nonEmptyItems().apply(stack)) result = result.add(count(child, item, depth + 1));
        }
        return result;
    }

    private static int remove(ItemStack stack, ItemStack item, int remaining, int depth) {
        if (remaining == 0 || stack.isEmpty()) return remaining;
        if (SpiritStoneStorage.isBag(stack)) {
            int take = SpiritStoneStorage.count(stack, candidate -> ItemStack.isSameItemSameComponents(item, candidate))
                    .min(BigInteger.valueOf(remaining)).intValueExact();
            if (take > 0 && !SpiritStoneStorage.remove(List.of(stack),
                    candidate -> ItemStack.isSameItemSameComponents(item, candidate), take))
                throw new IllegalStateException("Copied spirit stone balance changed during planning");
            return remaining - take;
        }
        if (ItemStack.isSameItemSameComponents(item, stack)) {
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        if (depth < MAX_NESTING_DEPTH && !stack.isEmpty()) for (var handler : InventoryItemResolver.NESTED_COMPONENT_HANDLERS) {
            var children = copies(handler.mutableItems().apply(stack));
            int before = remaining;
            for (var child : children) remaining = remove(child, item, remaining, depth + 1);
            if (remaining != before) handler.writeBack().accept(stack, children);
            if (remaining == 0) break;
        }
        return remaining;
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
