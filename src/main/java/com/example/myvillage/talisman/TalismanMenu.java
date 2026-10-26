package com.example.myvillage.talisman;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class TalismanMenu extends AbstractContainerMenu {
    public static final int WIDTH = 300, HEIGHT = 238;
    private final Container container;
    private final ContainerData data;
    public TalismanMenu(int id, Inventory inv) { this(id, inv, new SimpleContainer(3), new SimpleContainerData(7)); }
    public TalismanMenu(int id, Inventory inv, Container c, ContainerData d) {
        super(TalismanContent.MENU.get(), id); checkContainerSize(c, 3); checkContainerDataCount(d, 7);
        if (!inv.player.level().isClientSide() && !(c instanceof TalismanDeskBlockEntity)) throw new IllegalArgumentException("制符案菜单需要真实方块");
        container = c; data = d;
        int[] xs = {116, 152, 262};
        for (int i = 0; i < 3; i++) { final int index = i;
            addSlot(new Slot(c, i, xs[i], 34) {
                public boolean mayPlace(ItemStack stack) { return !active() && accepts(index, stack); }
                public boolean mayPickup(Player p) { return index == 2 || !active(); }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inv, 9 + row * 9 + col, 70 + col * 18, 156 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 70 + col * 18, 214));
        addDataSlots(d);
    }
    private boolean accepts(int i, ItemStack s) { return i == 0 && s.is(TalismanContent.PAPER.get()) || i == 1 && s.is(TalismanContent.INK.get()); }
    public int selected() { return data.get(0); }
    public int progress() { return data.get(1); }
    public int required() { return data.get(2); }
    public int status() { return data.get(3); }
    public int quality() { return data.get(4); }
    public boolean active() { return data.get(5) != 0; }
    public int aptitude() { return data.get(6); }
    public boolean isFor(TalismanDeskBlockEntity d) { return container == d; }
    @Override public boolean stillValid(Player p) { return container.stillValid(p); }
    @Override public boolean clickMenuButton(Player p, int button) {
        return p instanceof ServerPlayer s && container instanceof TalismanDeskBlockEntity d && d.button(s, this, button);
    }
    @Override public void removed(Player p) { super.removed(p); if (container instanceof TalismanDeskBlockEntity d) d.release(p); }
    @Override public void clicked(int slot, int button, ClickType type, Player p) {
        if (active() && slot >= 0 && slot < 2) return;
        super.clicked(slot, button, type, p);
    }
    @Override public ItemStack quickMoveStack(Player p, int i) {
        if (i < 0 || i >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(i); if (!slot.hasItem() || !slot.mayPickup(p)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (i < 3) { if (!moveItemStackTo(stack, 3, 39, true)) return ItemStack.EMPTY; }
        else if (accepts(0, stack) || accepts(1, stack)) {
            if (active()) return ItemStack.EMPTY;
            int target = accepts(0, stack) ? 0 : 1;
            if (!moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        } else if (i < 30) { if (!moveItemStackTo(stack, 30, 39, false)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(stack, 3, 30, false)) return ItemStack.EMPTY;
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(p, stack); return copy;
    }
}
