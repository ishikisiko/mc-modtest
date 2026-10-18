package com.example.myvillage.alchemy;

import com.example.myvillage.spiritstone.SpiritStoneStorage;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Plans on copies: failed returns cannot consume, duplicate or discard either inventory. */
public final class AlchemyAutofill {
    private AlchemyAutofill() { }
    public record Plan(List<ItemStack> inventory, List<ItemStack> inputs) { }
    private static boolean matches(ItemStack stack, String wanted) {
        return wanted.equals(AlchemyRules.FUEL) ? com.example.myvillage.item.SpiritStoneTags.isLowGrade(stack)
                : !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(wanted);
    }
    public static Optional<Plan> plan(List<ItemStack> inventory, List<ItemStack> inputs, List<String> wanted, List<Integer> counts) {
        return plan(inventory, inputs, wanted, counts, inventory.size());
    }
    public static Optional<Plan> plan(List<ItemStack> inventory, List<ItemStack> inputs,
                                      List<String> wanted, List<Integer> counts, int storageSlots) {
        if (inputs.size() != 3 || wanted.size() != 3 || counts.size() != 3 || storageSlots < 0 || storageSlots > inventory.size()
                || counts.stream().anyMatch(n -> n < 1 || n > 64)) throw new IllegalArgumentException("Invalid refill plan");
        var bag = new ArrayList<>(inventory.stream().map(ItemStack::copy).toList());
        var slots = new ArrayList<>(inputs.stream().map(ItemStack::copy).toList());
        for (int i = 0; i < 3; i++) {
            ItemStack old = slots.get(i);
            if (!old.isEmpty() && !matches(old, wanted.get(i))) {
                int remaining = SpiritStoneStorage.insertBags(bag, old, old.getCount(), false);
                if (SpiritStoneStorage.insertLoose(bag, old, remaining, storageSlots) != 0) return Optional.empty();
                slots.set(i, ItemStack.EMPTY);
            }
        }
        var expanded = new SpiritStoneStorage.Expanded(bag);
        var sources = expanded.entries(); int[] remaining = sources.stream().mapToInt(ItemStack::getCount).toArray();
        for (int i = 0; i < 3; i++) {
            ItemStack destination = slots.get(i);
            int need = Math.max(0, counts.get(i) - destination.getCount());
            for (int j = 0; j < sources.size() && need > 0; j++) {
                ItemStack source = sources.get(j);
                if (!matches(source, wanted.get(i)) || (!destination.isEmpty() && !ItemStack.isSameItemSameComponents(destination, source))) continue;
                int capacity = destination.isEmpty() ? source.getMaxStackSize() : destination.getMaxStackSize() - destination.getCount();
                int moved = Math.min(need, Math.min(remaining[j], Math.max(0, capacity)));
                if (moved == 0) continue;
                if (destination.isEmpty()) { destination = source.copyWithCount(moved); slots.set(i, destination); }
                else destination.grow(moved);
                remaining[j] -= moved; need -= moved;
            }
        }
        return Optional.of(new Plan(expanded.remaining(remaining), List.copyOf(slots)));
    }
}
