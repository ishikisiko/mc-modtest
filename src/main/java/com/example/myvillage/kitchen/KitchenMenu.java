package com.example.myvillage.kitchen;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import static com.example.myvillage.kitchen.KitchenStoveBlockEntity.*;

public final class KitchenMenu extends AbstractContainerMenu {
    public static final int WIDTH = 232, HEIGHT = 226, TOGGLE_COOK = 80;
    private final Container container;
    private final ContainerData data;

    public KitchenMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(SIZE), new SimpleContainerData(DATA_SIZE));
    }

    public KitchenMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(KitchenContent.MENU.get(), id);
        checkContainerSize(container, SIZE);
        checkContainerDataCount(data, DATA_SIZE);
        this.container = container;
        this.data = data;
        for (int slot = 0; slot < INPUTS; slot++) addKitchenSlot(slot, 16 + 18 * slot, 63);
        addKitchenSlot(BOWLS, 18, 97);
        addKitchenSlot(FUEL, 59, 97);
        addKitchenSlot(PRODUCT, 157, 63);
        addKitchenSlot(BUCKETS, 180, 63);
        addKitchenSlot(BOTTLES, 199, 63);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + 9 * (row + 1), 36 + col * 18, 145 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 36 + col * 18, 203));
        addDataSlots(data);
    }

    private void addKitchenSlot(int index, int x, int y) {
        addSlot(new Slot(container, index, x, y) {
            @Override public boolean mayPlace(ItemStack stack) { return accepts(recipe(), index, stack); }
        });
    }

    public int value(int index) { return data.get(index); }
    public KitchenRules.Recipe recipe() {
        var list = KitchenRules.get().stove;
        return list.get(Math.clamp(data.get(SELECTED), 0, list.size() - 1));
    }
    @Override public boolean stillValid(Player player) { return container.stillValid(player); }

    @Override public boolean clickMenuButton(Player player, int button) {
        return player instanceof ServerPlayer server && container instanceof KitchenStoveBlockEntity stove
                && stove.control(server, button);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SIZE) {
            if (!moveItemStackTo(stack, SIZE, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, PRODUCT, false)) {
            int hotbar = slots.size() - 9;
            if (index < hotbar && !moveItemStackTo(stack, hotbar, slots.size(), false)
                    || index >= hotbar && !moveItemStackTo(stack, SIZE, hotbar, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
