package com.example.myvillage.alchemy;

import com.example.myvillage.block.entity.AlchemyFurnaceBlockEntity;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** All moves use vanilla container transactions; an output slot can never accept input. */
public final class AlchemyMenu extends AbstractContainerMenu {
    public static final int WIDTH = 212, HEIGHT = 222;
    public static final int[][] FURNACE_SLOTS = {{16, 38}, {52, 38}, {76, 38}, {112, 38}, {178, 38}};
    private static final int PLAYER_START = 5, PLAYER_END = 41, HOTBAR_START = 32;
    private final Container container;
    private final ContainerData data;
    public AlchemyMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(5), new SimpleContainerData(3));
    }
    public AlchemyMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(AlchemyMenus.FURNACE.get(), id);
        checkContainerSize(container, 5); checkContainerDataCount(data, 3);
        this.container = container; this.data = data;
        for (int slot = 0; slot < 5; slot++) {
            final int index = slot;
            addSlot(new Slot(container, slot, FURNACE_SLOTS[slot][0], FURNACE_SLOTS[slot][1]) {
                @Override public boolean mayPlace(ItemStack stack) { return accepts(index, stack); }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 26 + col * 18, 140 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 26 + col * 18, 198));
        addDataSlots(data);
    }
    private static boolean accepts(int index, ItemStack stack) {
        String id = AlchemyFurnaceBlockEntity.id(stack);
        return switch (index) {
            case 0 -> AlchemyRules.get().recipeForFormula(id).isPresent();
            case 1, 2 -> AlchemyRules.get().isHerb(id);
            case 3 -> AlchemyRules.FUEL.equals(id);
            default -> false;
        };
    }
    public int progress() { return data.get(0); }
    public int duration() { return data.get(1); }
    public int status() { return data.get(2); }
    public ItemStack formula() { return container.getItem(0); }
    @Override public boolean stillValid(Player player) { return container.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (accepts(0, stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (accepts(1, stack)) {
            if (!moveItemStackTo(stack, 1, 3, false)) return ItemStack.EMPTY;
        } else if (accepts(3, stack)) {
            if (!moveItemStackTo(stack, 3, 4, false)) return ItemStack.EMPTY;
        } else if (index < HOTBAR_START) {
            if (!moveItemStackTo(stack, HOTBAR_START, PLAYER_END, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_START, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack); return original;
    }
}
