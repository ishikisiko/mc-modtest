package com.example.myvillage.spiritstone;

import com.example.myvillage.item.SpiritStoneTags;
import java.util.*;
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

/** Bag data uses vanilla container components: original item identity and components survive save/network. */
public final class SpiritStoneStorage {
    public static final TagKey<Item> BAGS = TagKey.create(Registries.ITEM, ResourceLocation.parse("myvillage:spirit_stone_bags"));
    private static final String AUTO = "myvillage:spirit_bag_auto_collect";
    private SpiritStoneStorage() { }
    public static boolean isBag(ItemStack stack) { return stack.getCount() == 1 && stack.is(BAGS); }
    public static boolean collectible(ItemStack stack) {
        return SpiritStoneTags.isLowGrade(stack) && !stack.is(BAGS) && !stack.has(DataComponents.CONTAINER);
    }
    public static boolean autoCollect(ItemStack bag) {
        CompoundTag data = bag.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return !data.contains(AUTO) || data.getBoolean(AUTO);
    }
    public static void autoCollect(ItemStack bag, boolean enabled) {
        CustomData.update(DataComponents.CUSTOM_DATA, bag, data -> data.putBoolean(AUTO, enabled));
    }
    public static List<ItemStack> contents(ItemStack bag) {
        return bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).stream()
                .map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
    public static void contents(ItemStack bag, List<ItemStack> contents) {
        bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
    }
    public static int count(ItemStack stack, Predicate<ItemStack> accepts) {
        if (!isBag(stack)) return accepts.test(stack) ? stack.getCount() : 0;
        return contents(stack).stream().filter(SpiritStoneStorage::collectible).filter(accepts).mapToInt(ItemStack::getCount).sum();
    }
    /** Mutates only caller-owned copies; no partial removal on shortage. */
    public static boolean remove(List<ItemStack> copies, Predicate<ItemStack> accepts, int required) {
        if (required < 0 || copies.stream().mapToLong(s -> count(s, accepts)).sum() < required) return false;
        int remaining = required;
        for (ItemStack stack : copies) {
            if (remaining == 0) break;
            if (isBag(stack)) {
                var inside = contents(stack);
                for (ItemStack stone : inside) if (collectible(stone) && accepts.test(stone)) {
                    int take = Math.min(remaining, stone.getCount()); stone.shrink(take); remaining -= take;
                }
                contents(stack, inside);
            } else if (accepts.test(stack)) {
                int take = Math.min(remaining, stack.getCount()); stack.shrink(take); remaining -= take;
            }
        }
        return remaining == 0;
    }
    /** Return uninserted count. Invalid or reloaded-away stones remain retrievable, never silently converted. */
    public static int insert(ItemStack bag, ItemStack source, int count, boolean requireAutomatic) {
        if (count <= 0 || !isBag(bag) || !collectible(source) || requireAutomatic && !autoCollect(bag)) return count;
        var inside = contents(bag);
        int limit = SpiritStoneBagRules.get().slots(), remaining = count;
        for (int i = 0; i < Math.min(inside.size(), limit); i++) {
            ItemStack old = inside.get(i);
            if (!old.isEmpty() && ItemStack.isSameItemSameComponents(old, source)) {
                int moved = Math.min(remaining, Math.max(0, old.getMaxStackSize() - old.getCount()));
                old.grow(moved); remaining -= moved;
            }
        }
        for (int i = 0; i < limit && remaining > 0; i++) {
            if (i < inside.size() && !inside.get(i).isEmpty()) continue;
            int moved = Math.min(remaining, source.getMaxStackSize());
            while (inside.size() <= i) inside.add(ItemStack.EMPTY);
            inside.set(i, source.copyWithCount(moved)); remaining -= moved;
        }
        if (remaining != count) contents(bag, inside);
        return remaining;
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

    /** Flatten spendable stones for the existing non-greedy forging allocator, retaining their locations. */
    public static final class Expanded {
        private record Position(int outer, int inner) { }
        private final List<ItemStack> original;
        private final List<ItemStack> entries = new ArrayList<>();
        private final List<Position> positions = new ArrayList<>();
        public Expanded(List<ItemStack> original) {
            this.original = original;
            for (int outer = 0; outer < original.size(); outer++) {
                ItemStack stack = original.get(outer);
                if (isBag(stack)) {
                    var inside = contents(stack);
                    for (int inner = 0; inner < inside.size(); inner++) if (collectible(inside.get(inner))) {
                        entries.add(inside.get(inner)); positions.add(new Position(outer, inner));
                    }
                } else { entries.add(stack); positions.add(new Position(outer, -1)); }
            }
        }
        public List<ItemStack> entries() { return List.copyOf(entries); }
        public List<ItemStack> remaining(int[] counts) {
            if (counts.length != entries.size()) throw new IllegalArgumentException("Allocation size");
            List<ItemStack> result = original.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            Map<Integer, List<ItemStack>> bagChanges = new HashMap<>();
            for (int i = 0; i < counts.length; i++) {
                if (counts[i] < 0 || counts[i] > entries.get(i).getCount()) throw new IllegalArgumentException("Allocation count");
                var pos = positions.get(i);
                if (pos.inner() < 0) result.get(pos.outer()).setCount(counts[i]);
                else bagChanges.computeIfAbsent(pos.outer(), slot -> contents(result.get(slot))).get(pos.inner()).setCount(counts[i]);
            }
            bagChanges.forEach((slot, inside) -> contents(result.get(slot), inside));
            return List.copyOf(result);
        }
    }
}
