package com.example.myvillage.storagebag;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.item.TechniqueManualItem;
import com.example.myvillage.spiritstone.SpiritStoneDenominations;
import com.example.myvillage.spiritstone.SpiritStoneLedger;
import com.example.myvillage.spiritstone.SpiritStoneStorage;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;
import java.util.function.IntUnaryOperator;

final class StorageBagContents {
    static final TagKey<Item> MATERIALS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "storage_bag_materials"));
    private static final String PAUSED = MyVillageMod.MOD_ID + ":storage_bag_pickup_paused";

    private StorageBagContents() {}

    static boolean material(ItemStack stack) {
        return stack.is(MATERIALS) && MyVillageMod.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())
                && !SpiritStoneDenominations.get().recognizes(stack) && !(stack.getItem() instanceof TechniqueManualItem);
    }

    static boolean accepts(StorageBagRules.Kind kind, ItemStack stack) {
        if (stack.isEmpty() || !stack.canFitInsideContainerItems() || stack.getItem() instanceof StorageBagItem
                || stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS)
                || stack.has(SpiritStoneLedger.TYPE) || SpiritStoneStorage.isBag(stack)) {
            return false;
        }
        return switch (kind) {
            case MATERIAL -> material(stack);
            case GENERAL -> true;
            case MANUAL -> stack.getItem() instanceof TechniqueManualItem;
        };
    }

    static boolean autoPickup(ItemStack bag) {
        return !bag.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(PAUSED);
    }

    static void setAutoPickup(ItemStack bag, boolean enabled) {
        CustomData.update(DataComponents.CUSTOM_DATA, bag, tag -> {
            if (enabled) tag.remove(PAUSED);
            else tag.putBoolean(PAUSED, true);
        });
    }

    static NonNullList<ItemStack> read(ItemStack bag, int capacity) {
        var slots = NonNullList.withSize(capacity, ItemStack.EMPTY);
        bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(slots);
        return slots;
    }

    static void write(ItemStack bag, List<ItemStack> slots) {
        var contents = ItemContainerContents.fromItems(slots);
        if (contents == ItemContainerContents.EMPTY) bag.remove(DataComponents.CONTAINER);
        else bag.set(DataComponents.CONTAINER, contents);
    }

    static int occupied(ItemStack bag) {
        return (int) bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyStream().count();
    }

    /** Mutates only a detached slot plan. The caller keeps the uninserted source count. */
    static int insert(List<ItemStack> slots, ItemStack source, int count, IntUnaryOperator slotLimit) {
        int remaining = count;
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            var stack = slots.get(i);
            if (!ItemStack.isSameItemSameComponents(source, stack)) continue;
            int moved = Math.min(remaining, Math.max(0, slotLimit.applyAsInt(i) - stack.getCount()));
            stack.grow(moved);
            remaining -= moved;
        }
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            if (!slots.get(i).isEmpty()) continue;
            int moved = Math.min(remaining, slotLimit.applyAsInt(i));
            if (moved > 0) slots.set(i, source.copyWithCount(moved));
            remaining -= moved;
        }
        return remaining;
    }
}
