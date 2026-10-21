package com.example.myvillage.spiritstone;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Exact ordinary-item exchange. Plan on copies, then commit the whole inventory once. */
public final class SpiritStoneExchange {
    public record Plan(String result, List<ItemStack> items, BigInteger consumed, BigInteger produced) {
        public boolean ready() { return result.equals("ready"); }
    }
    private SpiritStoneExchange() { }
    public static Plan plan(List<ItemStack> inventory, SpiritStoneDenominations.Tier from,
                            SpiritStoneDenominations.Tier to, int batches) {
        if (batches < 1 || from.id().equals(to.id())) return failure("invalid");
        var input = new ItemStack(BuiltInRegistries.ITEM.get(from.item()));
        var output = new ItemStack(BuiltInRegistries.ITEM.get(to.item()));
        if (input.isEmpty() || output.isEmpty() || !input.is(from.tag()) || !output.is(to.tag())) return failure("invalid");
        var larger = from.units().max(to.units());
        if (larger.remainder(from.units().min(to.units())).signum() != 0) return failure("invalid");
        var amount = BigInteger.valueOf(batches);
        var consumed = larger.divide(from.units()).multiply(amount);
        var produced = larger.divide(to.units()).multiply(amount);
        var copies = inventory.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (!SpiritStoneStorage.remove(copies, SpiritStoneStorage.ordinary(input), consumed)) return failure("short");
        // Active exchange may deposit in paused bags too, without changing their automatic switch.
        var remaining = produced;
        for (var bag : copies) {
            remaining = SpiritStoneStorage.insert(bag, output, remaining, false);
            if (remaining.signum() == 0) break;
        }
        if (remaining.signum() > 0) {
            // Bound loose emission before any int conversion; huge amounts never expand into stacks.
            long capacity = (long)Math.min(36, copies.size()) * output.getMaxStackSize();
            if (remaining.compareTo(BigInteger.valueOf(capacity)) > 0
                    || SpiritStoneStorage.insertLoose(copies, output, remaining.intValueExact(), 36) != 0) return failure("full");
        }
        return new Plan("ready", List.copyOf(copies), consumed, produced);
    }
    private static Plan failure(String result) { return new Plan(result, List.of(), BigInteger.ZERO, BigInteger.ZERO); }
}
