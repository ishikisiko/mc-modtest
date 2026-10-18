package com.example.myvillage.spiritstone;

import java.util.*;
import java.math.BigInteger;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;

/** Aggregated exact quantities with lazy, lossless migration of the old vanilla container. */
public final class SpiritStoneStorage {
    public static final TagKey<Item> BAGS = TagKey.create(Registries.ITEM, ResourceLocation.parse("myvillage:spirit_stone_bags"));
    private static final String AUTO = "myvillage:spirit_bag_auto_collect";
    private SpiritStoneStorage() { }
    public static boolean isBag(ItemStack stack) { return stack.getCount() == 1 && stack.is(BAGS); }
    public static boolean collectible(ItemStack stack) {
        return SpiritStoneDenominations.get().recognizes(stack) && !stack.is(BAGS) && !stack.has(DataComponents.CONTAINER) && !stack.has(SpiritStoneLedger.TYPE);
    }
    public static boolean autoCollect(ItemStack bag) {
        CompoundTag data = bag.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return !data.contains(AUTO) || data.getBoolean(AUTO);
    }
    public static void autoCollect(ItemStack bag, boolean enabled) {
        CustomData.update(DataComponents.CUSTOM_DATA, bag, data -> data.putBoolean(AUTO, enabled));
    }
    public static List<SpiritStoneLedger.Entry> entries(ItemStack bag) {
        var ledger = bag.get(SpiritStoneLedger.TYPE);
        if (!bag.has(DataComponents.CONTAINER)) return ledger == null ? List.of() : ledger.entries();
        var values = new ArrayList<SpiritStoneLedger.Entry>();
        if (ledger != null) values.addAll(ledger.entries());
        // Read both representations defensively; writes clear the legacy component atomically.
        bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).stream()
                .filter(s -> !s.isEmpty()).forEach(s -> values.add(new SpiritStoneLedger.Entry(s, BigInteger.valueOf(s.getCount()))));
        return values.isEmpty() ? List.of() : new SpiritStoneLedger(values).entries();
    }
    public static void entries(ItemStack bag, List<SpiritStoneLedger.Entry> contents) {
        bag.set(SpiritStoneLedger.TYPE, new SpiritStoneLedger(contents));
        bag.remove(DataComponents.CONTAINER);
    }
    public static void contents(ItemStack bag, List<ItemStack> contents) {
        entries(bag, contents.stream().filter(s -> !s.isEmpty())
                .map(s -> new SpiritStoneLedger.Entry(s, BigInteger.valueOf(s.getCount()))).toList());
    }
    public static BigInteger count(ItemStack stack, Predicate<ItemStack> accepts) {
        if (!isBag(stack)) return BigInteger.valueOf(accepts.test(stack) ? stack.getCount() : 0);
        return entries(stack).stream().filter(e -> collectible(e.item()) && accepts.test(e.item()))
                .map(SpiritStoneLedger.Entry::quantity).reduce(BigInteger.ZERO, BigInteger::add);
    }
    /** An int consumer only needs a bounded projection, never a lossy persisted balance. */
    public static int boundedCount(ItemStack stack, Predicate<ItemStack> accepts, int limit) {
        return count(stack, accepts).min(BigInteger.valueOf(limit)).intValueExact();
    }
    public static boolean remove(List<ItemStack> copies, Predicate<ItemStack> accepts, int required) {
        return remove(copies, accepts, BigInteger.valueOf(required));
    }
    /** Mutates only caller-owned copies; no partial removal on shortage. */
    public static boolean remove(List<ItemStack> copies, Predicate<ItemStack> accepts, BigInteger required) {
        if (required.signum() < 0 || copies.stream().map(s -> count(s, accepts)).reduce(BigInteger.ZERO, BigInteger::add).compareTo(required) < 0) return false;
        BigInteger remaining = required;
        for (ItemStack stack : copies) {
            if (remaining.signum() == 0) break;
            if (isBag(stack)) {
                var inside = new ArrayList<SpiritStoneLedger.Entry>();
                for (var entry : entries(stack)) {
                    BigInteger take = collectible(entry.item()) && accepts.test(entry.item()) ? remaining.min(entry.quantity()) : BigInteger.ZERO;
                    remaining = remaining.subtract(take);
                    if (entry.quantity().compareTo(take) > 0) inside.add(new SpiritStoneLedger.Entry(entry.item(), entry.quantity().subtract(take)));
                }
                entries(stack, inside);
            } else if (accepts.test(stack)) {
                int take = remaining.min(BigInteger.valueOf(stack.getCount())).intValueExact(); stack.shrink(take); remaining = remaining.subtract(BigInteger.valueOf(take));
            }
        }
        return remaining.signum() == 0;
    }
    public static int insert(ItemStack bag, ItemStack source, int count, boolean requireAutomatic) {
        if (count <= 0) return count;
        return insert(bag, source, BigInteger.valueOf(count), requireAutomatic).intValueExact();
    }
    /** Return uninserted quantity. There is no stack-count or numeric gameplay capacity. */
    public static BigInteger insert(ItemStack bag, ItemStack source, BigInteger count, boolean requireAutomatic) {
        if (count.signum() < 0) throw new IllegalArgumentException("Negative deposit");
        if (count.signum() == 0 || !isBag(bag) || !collectible(source) || requireAutomatic && !autoCollect(bag)) return count;
        var inside = new ArrayList<>(entries(bag)); inside.add(new SpiritStoneLedger.Entry(source, count));
        entries(bag, inside);
        return BigInteger.ZERO;
    }
    public static int insertBags(List<ItemStack> copies, ItemStack source, int count, boolean requireAutomatic) {
        int remaining = count;
        for (ItemStack bag : copies) {
            remaining = insert(bag, source, remaining, requireAutomatic);
            if (remaining == 0) break;
        }
        return remaining;
    }
    public static int insertLoose(List<ItemStack> copies, ItemStack source, int count, int limit) {
        int remaining = count;
        for (int i = 0; i < Math.min(limit, copies.size()); i++) {
            var old = copies.get(i);
            if (!old.isEmpty() && ItemStack.isSameItemSameComponents(old, source)) {
                int moved = Math.min(remaining, Math.max(0, old.getMaxStackSize() - old.getCount()));
                old.grow(moved); remaining -= moved;
            }
        }
        for (int i = 0; i < Math.min(limit, copies.size()) && remaining > 0; i++) if (copies.get(i).isEmpty()) {
            int moved = Math.min(remaining, source.getMaxStackSize());
            copies.set(i, source.copyWithCount(moved)); remaining -= moved;
        }
        return remaining;
    }
    public static boolean collect(List<ItemStack> copies) {
        boolean changed = false;
        for (ItemStack bag : copies) if (isBag(bag) && bag.has(DataComponents.CONTAINER)) {
            entries(bag, entries(bag)); changed = true;
        }
        for (ItemStack source : copies) if (collectible(source)) {
            int remaining = insertBags(copies, source, source.getCount(), true);
            if (remaining != source.getCount()) { source.setCount(remaining); changed = true; }
        }
        return changed;
    }
    /** Only ordinary currency with the exact configured identity is legal tender for commerce. */
    public static Predicate<ItemStack> ordinary(ItemStack type) {
        return stack -> !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, type);
    }

    /** One bounded token per identity for finite-cost allocators; never expand coins into stacks. */
    public static final class Expanded {
        private record Position(int outer, int inner) { }
        private final List<ItemStack> original;
        private final List<ItemStack> entries = new ArrayList<>();
        private final List<Position> positions = new ArrayList<>();
        public Expanded(List<ItemStack> original) { this(original, Integer.MAX_VALUE); }
        public Expanded(List<ItemStack> original, int demand) {
            if (demand < 0) throw new IllegalArgumentException("Negative demand");
            this.original = original;
            for (int outer = 0; outer < original.size(); outer++) {
                ItemStack stack = original.get(outer);
                if (isBag(stack)) {
                    var inside = SpiritStoneStorage.entries(stack);
                    for (int inner = 0; inner < inside.size(); inner++) if (collectible(inside.get(inner).item())) {
                        var entry = inside.get(inner);
                        entries.add(entry.item().copyWithCount(entry.quantity().min(BigInteger.valueOf(demand)).intValueExact()));
                        positions.add(new Position(outer, inner));
                    }
                } else { entries.add(stack.copy()); positions.add(new Position(outer, -1)); }
            }
        }
        public List<ItemStack> entries() { return entries.stream().map(ItemStack::copy).toList(); }
        public List<ItemStack> remaining(int[] counts) {
            if (counts.length != entries.size()) throw new IllegalArgumentException("Allocation size");
            List<ItemStack> result = original.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            Map<Integer, List<SpiritStoneLedger.Entry>> bagChanges = new HashMap<>();
            for (int i = 0; i < counts.length; i++) {
                if (counts[i] < 0 || counts[i] > entries.get(i).getCount()) throw new IllegalArgumentException("Allocation count");
                int spent = entries.get(i).getCount() - counts[i];
                if (spent == 0) continue;
                var pos = positions.get(i);
                if (pos.inner() < 0) result.get(pos.outer()).setCount(counts[i]);
                else {
                    var inside = bagChanges.computeIfAbsent(pos.outer(), slot -> new ArrayList<>(SpiritStoneStorage.entries(result.get(slot))));
                    var before = inside.get(pos.inner()); var after = before.quantity().subtract(BigInteger.valueOf(spent));
                    inside.set(pos.inner(), after.signum() == 0 ? null : new SpiritStoneLedger.Entry(before.item(), after));
                }
            }
            bagChanges.forEach((slot, inside) -> SpiritStoneStorage.entries(result.get(slot), inside.stream().filter(Objects::nonNull).toList()));
            return List.copyOf(result);
        }
    }
}
