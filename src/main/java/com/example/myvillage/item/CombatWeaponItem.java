package com.example.myvillage.item;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A sword-type item with a combat weapon entry ({@code data/<ns>/combat/weapon/}). A landed combat
 * hit costs durability, so the server resends the held stack with a new {@code minecraft:damage}
 * after every hit. NeoForge's default rule replays the equip animation for any new stack object,
 * which dropped the first-person weapon out of view right after each hit-stop.
 *
 * <p>The rule here is the vanilla behaviour minus that replay: the first-person renderer first
 * keeps a stack that {@code ItemStack.matches} the one it holds, and then asks this item; the
 * answer is "re-equip" unless the two stacks are the same item, count and components with
 * {@code minecraft:damage} ignored. {@code slotChanged} is not consulted (it is true for one tick
 * only). Empty hands are answered by NeoForge before this item is asked.
 */
public class CombatWeaponItem extends SwordItem {
    public CombatWeaponItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return reequips(
                ItemStack.isSameItem(oldStack, newStack) && oldStack.getCount() == newStack.getCount(),
                components(oldStack),
                components(newStack),
                DataComponents.DAMAGE);
    }

    private static Map<DataComponentType<?>, Optional<?>> components(ItemStack stack) {
        Map<DataComponentType<?>, Optional<?>> components = new HashMap<>();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : stack.getComponentsPatch().entrySet()) {
            components.put(entry.getKey(), entry.getValue());
        }
        return components;
    }

    /**
     * Re-equip unless the stacks are the same item and count and their component changes (from
     * the item's defaults) agree once {@code ignored} (the durability) is left out.
     */
    static <K> boolean reequips(boolean sameItemAndCount, Map<K, ?> oldComponents, Map<K, ?> newComponents, K ignored) {
        if (!sameItemAndCount) {
            return true;
        }
        Map<K, Object> before = new HashMap<>(oldComponents);
        Map<K, Object> after = new HashMap<>(newComponents);
        before.remove(ignored);
        after.remove(ignored);
        return !before.equals(after);
    }
}
