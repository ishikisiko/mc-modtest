package com.example.myvillage.alchemy;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Plans on copies: failed returns cannot consume, duplicate or discard either inventory. */
public final class AlchemyAutofill {
    private AlchemyAutofill() { }
    public record Plan(List<ItemStack> inventory, List<ItemStack> inputs) { }
    private static String id(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
    private static boolean matches(ItemStack stack, String wanted) {
        return wanted.equals(AlchemyRules.FUEL)
                ? com.example.myvillage.item.SpiritStoneTags.isLowGrade(stack) : id(stack).equals(wanted);
    }
    public static Optional<Plan> plan(List<ItemStack> inventory, List<ItemStack> inputs,
                                      List<String> wanted, List<Integer> counts) {
        if (inputs.size() != 3 || wanted.size() != 3 || counts.size() != 3
                || counts.stream().anyMatch(n -> n < 1 || n > 64)) throw new IllegalArgumentException("Invalid refill plan");
        var bag = new ArrayList<>(inventory.stream().map(ItemStack::copy).toList());
        var slots = new ArrayList<>(inputs.stream().map(ItemStack::copy).toList());
        for (int i = 0; i < 3; i++) {
            ItemStack old = slots.get(i);
            if (!old.isEmpty() && !matches(old, wanted.get(i))) {
                if (!returnToBag(bag, old)) return Optional.empty();
                slots.set(i, ItemStack.EMPTY);
            }
        }
        for (int i = 0; i < 3; i++) {
            ItemStack destination = slots.get(i);
            int need = Math.max(0, counts.get(i) - destination.getCount());
            for (ItemStack stack : bag) {
                if (need == 0) break;
                if (!matches(stack, wanted.get(i)) || (!destination.isEmpty()
                        && !ItemStack.isSameItemSameComponents(destination, stack))) continue;
                int capacity = destination.isEmpty() ? stack.getMaxStackSize() : destination.getMaxStackSize() - destination.getCount();
                int moved = Math.min(need, Math.min(stack.getCount(), Math.max(0, capacity)));
                if (destination.isEmpty()) { destination = stack.copyWithCount(moved); slots.set(i, destination); }
                else destination.grow(moved);
                stack.shrink(moved); need -= moved;
            }
        }
        return Optional.of(new Plan(List.copyOf(bag), List.copyOf(slots)));
    }
    private static boolean returnToBag(List<ItemStack> bag, ItemStack source) {
        for (ItemStack stack : bag) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, source)) {
                int moved = Math.min(source.getCount(), Math.max(0, stack.getMaxStackSize() - stack.getCount()));
                stack.grow(moved); source.shrink(moved);
                if (source.isEmpty()) return true;
            }
        }
        for (int i = 0; i < bag.size(); i++) if (bag.get(i).isEmpty()) {
            int moved = Math.min(source.getCount(), source.getMaxStackSize());
            bag.set(i, source.copyWithCount(moved)); source.shrink(moved);
            if (source.isEmpty()) return true;
        }
        return source.isEmpty();
    }
}
