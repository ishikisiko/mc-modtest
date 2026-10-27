package com.example.myvillage.forging;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** The anvil's real inventory. Menus borrow these slots; leaving never returns a second copy. */
public final class WorkshopStorage {
    public final SimpleContainer items = new SimpleContainer(2);
    private long heatUntil;
    private int recipe, trait, direction = 1, treatment;

    public int recipe() { return recipe; }
    public int trait() { return trait; }
    public int direction() { return direction; }
    public int treatment() { return treatment; }
    public int heat(long now) { return (int)Math.clamp(heatUntil - now, 0, WorkshopRules.get().heat_ticks()); }
    public void heat(int ticks, long now) { heatUntil = now + Math.clamp(ticks, 0, WorkshopRules.get().heat_ticks()); }
    public void selection(int recipe, int trait, int direction, int treatment) {
        this.recipe = Math.clamp(recipe, 0, WorkshopRules.get().crafts().size() - 1);
        this.trait = Math.clamp(trait, 0, ForgeMark.TRAITS.size() - 1);
        this.direction = Math.clamp(direction, 1, 3);
        this.treatment = Math.clamp(treatment, 0, 2);
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        var stacks = NonNullList.withSize(2, ItemStack.EMPTY);
        for (int i = 0; i < stacks.size(); i++) stacks.set(i, items.getItem(i));
        ContainerHelper.saveAllItems(tag, stacks, registries);
        tag.putLong("HeatUntil", heatUntil);
        tag.putInt("Recipe", recipe); tag.putInt("Trait", trait);
        tag.putInt("Direction", direction); tag.putInt("Treatment", treatment);
    }
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        var stacks = NonNullList.withSize(2, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, stacks, registries);
        for (int i = 0; i < stacks.size(); i++) items.setItem(i, stacks.get(i));
        heatUntil = Math.max(0, tag.getLong("HeatUntil"));
        selection(tag.getInt("Recipe"), tag.getInt("Trait"), tag.contains("Direction") ? tag.getInt("Direction") : 1,
                tag.getInt("Treatment"));
    }
}
