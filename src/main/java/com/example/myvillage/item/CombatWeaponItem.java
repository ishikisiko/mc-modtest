package com.example.myvillage.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/**
 * A sword-type item with a combat weapon entry ({@code data/<ns>/combat/weapon/}). A landed combat
 * hit costs durability, so the server resends the held stack with new data after every hit. The
 * default NeoForge rule replays the equip animation for any new stack object, which drops the
 * first-person weapon out of view right after the hit-stop. A change of data alone must not replay
 * it; switching slots or switching to another item still does (empty hands are handled before this
 * is asked).
 */
public class CombatWeaponItem extends SwordItem {
    public CombatWeaponItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return reequips(ItemStack.isSameItem(oldStack, newStack), slotChanged);
    }

    /**
     * Re-equip on a slot change or a different item, never on a change of data alone
     * ({@code ItemStack.isSameItem} compares the item only, not its components).
     */
    static boolean reequips(boolean sameItem, boolean slotChanged) {
        return slotChanged || !sameItem;
    }
}
