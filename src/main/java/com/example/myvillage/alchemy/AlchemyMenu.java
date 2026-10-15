package com.example.myvillage.alchemy;

import com.example.myvillage.block.entity.AlchemyFurnaceBlockEntity;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

/** All moves use vanilla container transactions; an output slot can never accept input. */
public final class AlchemyMenu extends AbstractContainerMenu {
    public static final int WIDTH = 304, HEIGHT = 222;
    public static final int[][] FURNACE_SLOTS = {{16, 108}, {116, 38}, {140, 38}, {184, 38}, {260, 38}};
    private static final int PLAYER_START = 5, PLAYER_END = 41, HOTBAR_START = 32;
    private final Container container;
    private final ContainerData data;
    private final java.util.List<DataSlot> learned = new java.util.ArrayList<>();
    public AlchemyMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(5), new SimpleContainerData(7));
    }
    public AlchemyMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(AlchemyMenus.FURNACE.get(), id);
        checkContainerSize(container, 5); checkContainerDataCount(data, 7);
        if (!inventory.player.level().isClientSide() && !(container instanceof AlchemyFurnaceBlockEntity))
            throw new IllegalArgumentException("Server alchemy menu requires a real furnace");
        this.container = container; this.data = data;
        for (int slot = 0; slot < 5; slot++) {
            final int index = slot;
            addSlot(new Slot(container, slot, FURNACE_SLOTS[slot][0], FURNACE_SLOTS[slot][1]) {
                @Override public boolean mayPlace(ItemStack stack) { return !inputLocked(index) && accepts(index, stack); }
                @Override public boolean mayPickup(Player player) { return !inputLocked(index); }
                @Override public boolean isActive() { return index != 0 || !container.getItem(0).isEmpty(); }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 70 + col * 18, 140 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 70 + col * 18, 198));
        addDataSlots(data);
        for (var recipe : AlchemyRules.get().recipes()) {
            DataSlot known = inventory.player instanceof ServerPlayer server ? new DataSlot() {
                public int get() { return AlchemyKnowledge.knows(server, recipe.id()) ? 1 : 0; }
                public void set(int value) { }
            } : DataSlot.standalone();
            learned.add(known); addDataSlot(known);
        }
    }
    private static boolean accepts(int index, ItemStack stack) {
        String id = AlchemyFurnaceBlockEntity.id(stack);
        return switch (index) {
            case 0 -> false;
            case 1, 2 -> AlchemyRules.get().isHerb(id);
            case 3 -> AlchemyRules.FUEL.equals(id);
            default -> false;
        };
    }
    public int progress() { return data.get(0); }
    public int duration() { return data.get(1); }
    public int status() { return data.get(2); }
    public int aptitude() { return data.get(3); }
    public int qualityCap() { return data.get(4); }
    public boolean active() { return data.get(5) != 0; }
    private boolean inputLocked(int index) { return index >= 0 && index < 4 && active(); }
    public boolean isFor(AlchemyFurnaceBlockEntity furnace) { return container == furnace; }
    public ItemStack formula() { return container.getItem(0); }
    public int selectedRecipe() { return data.get(6) - 1; }
    public boolean knows(int index) { return index >= 0 && index < learned.size() && learned.get(index).get() == 1; }
    @Override public boolean stillValid(Player player) { return container.stillValid(player); }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer server) || player.containerMenu != this
                || !(container instanceof AlchemyFurnaceBlockEntity furnace)) return false;
        return button == 0 ? furnace.start(server, this) : furnace.select(server, this, button - 1);
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (inputLocked(slot)) return;
        super.clicked(slot, button, type, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        // Vanilla's merge-into-existing-stack path does not always consult Slot.mayPlace.
        if (active() && index >= PLAYER_START && (accepts(0, stack) || accepts(1, stack) || accepts(3, stack)))
            return ItemStack.EMPTY;
        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
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
