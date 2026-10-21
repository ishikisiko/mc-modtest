package com.example.myvillage.gathering;

import com.example.myvillage.item.SpiritStoneTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class GatheringMenu extends AbstractContainerMenu {
    public static final int WIDTH = 300, HEIGHT = 238;
    private final Container container;
    private final ContainerData data;
    public GatheringMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(1), new SimpleContainerData(GatheringBlockEntity.DATA_COUNT));
    }
    public GatheringMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(GatheringContent.MENU.get(), id);
        checkContainerSize(container, 1); checkContainerDataCount(data, GatheringBlockEntity.DATA_COUNT);
        if (!inventory.player.level().isClientSide() && !(container instanceof GatheringBlockEntity))
            throw new IllegalArgumentException("聚灵阵需要服务器阵心");
        this.container = container; this.data = data;
        addSlot(new Slot(container, 0, 140, 45) {
            @Override public boolean mayPlace(ItemStack stack) { return SpiritStoneTags.isLowGrade(stack); }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 69 + col * 18, 156 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 69 + col * 18, 214));
        addDataSlots(data);
    }
    public int value(int i) { return data.get(i); }
    public boolean isFor(GatheringBlockEntity root) { return container == root; }
    @Override public boolean stillValid(Player player) { return container.stillValid(player); }
    @Override public boolean clickMenuButton(Player player, int button) {
        return player instanceof ServerPlayer server && container instanceof GatheringBlockEntity core
                && core.control(server, this, button);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), before = stack.copy();
        if (index == 0) { if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY; }
        else if (SpiritStoneTags.isLowGrade(stack)) { if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY; }
        else if (index < 28) { if (!moveItemStackTo(stack, 28, 37, false)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(stack, 1, 28, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == before.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack); return before;
    }
}
