package com.example.myvillage.production;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

public final class ProductionMenu extends AbstractContainerMenu {
    public static final int WIDTH = 316, HEIGHT = 244;
    public static final int TOGGLE = 0, CHANGE_MODE = 1, TARGET_DOWN = 2, TARGET_UP = 3;
    public static final int RESERVE_DOWN = 4, RESERVE_UP = 5, MEDIUM_DOWN = 6, MEDIUM_UP = 7;
    static final int KIND = 0, ENABLED = 1, MODE = 2, PROGRESS = 3, STATUS = 4, RESERVE = 5, RESERVE_FLOOR = 6;
    static final int TARGET = 7, MEDIUM_TARGET = 8, NEXT_GRADE = 9, PART = 10, X = 11, Y = 12, Z = 13;
    static final int CAPACITY = 14, FACING = 15, DATA_COUNT = 16;
    private static final int PLAYER_START = ProductionBlockEntity.SLOT_COUNT, HOTBAR = PLAYER_START + 27, PLAYER_END = HOTBAR + 9;

    public enum Status {
        STOPPED, RUNNING, FULL, TOOL, DEPLETED, UNLOADED, ANCHOR, ORIGINAL_ARRAY, PART, SUPPORT, SHARED, BORDER;

        static Status forCheck(ProductionPattern.Result result) { return valueOf(result.name()); }
        public boolean blocked() { return ordinal() >= UNLOADED.ordinal(); }
        public Component label() { return Component.translatable("menu.yuanxian.production.status." + name().toLowerCase(java.util.Locale.ROOT)); }
    }

    public record BlueprintCell(BlockPos offset, Component name, Direction facing) { }

    private final Container inventory;
    private final ContainerData data;

    public ProductionMenu(int id, Inventory player) {
        this(id, player, new SimpleContainer(ProductionBlockEntity.SLOT_COUNT), new SimpleContainerData(DATA_COUNT));
    }

    ProductionMenu(int id, Inventory player, Container inventory, ContainerData data) {
        super(ProductionContent.MENU.get(), id);
        checkContainerSize(inventory, ProductionBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, DATA_COUNT);
        this.inventory = inventory;
        this.data = data;
        for (int i = 0; i < PLAYER_START; i++) {
            int slot = i;
            addSlot(new Slot(inventory, slot, i == 0 ? 129 : 201 + (i - 1) * 23, 30) {
                @Override public boolean isActive() { return slot == 0 ? kind().mining() : slot <= kind().gradeCount(); }
                @Override public boolean mayPickup(Player player) { return isActive(); }
                @Override public boolean mayPlace(ItemStack stack) { return slot == 0 && kind().mining() && ProductionBlockEntity.validPick(stack); }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new Slot(player, 9 + row * 9 + col, 78 + col * 18, 162 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(player, col, 78 + col * 18, 220));
        addDataSlots(data);
    }

    public ProductionKind kind() { return ProductionKind.values()[Math.clamp(data.get(KIND), 0, ProductionKind.values().length - 1)]; }
    public boolean enabled() { return data.get(ENABLED) != 0; }
    public int mode() { return data.get(MODE); }
    public int progress() { return data.get(PROGRESS); }
    public Status status() { return Status.values()[Math.clamp(data.get(STATUS), 0, Status.values().length - 1)]; }
    public int oreType() { return data.get(NEXT_GRADE); }
    public int reserve() { return data.get(RESERVE); }
    public int capacity() { return data.get(CAPACITY); }
    public int reserveFloor() { return data.get(RESERVE_FLOOR); }
    public int target() { return data.get(TARGET); }
    public int mediumTarget() { return data.get(MEDIUM_TARGET); }
    public BlockPos issue() { return new BlockPos(data.get(X), data.get(Y), data.get(Z)); }
    public Direction facing() { return Direction.from2DDataValue(data.get(FACING)); }
    public ResourceLocation stoneId(int grade) { return ProductionRules.outputId(grade); }

    public Component expectedPart() {
        int slot = data.get(PART) - 1;
        return slot < 0 || slot >= ProductionPart.values().length ? Component.empty()
                : ProductionContent.block(ProductionPart.values()[slot]).getName();
    }

    public List<BlueprintCell> blueprint() {
        return ProductionPattern.layout(kind(), facing()).cells().stream().map(cell -> new BlueprintCell(cell.offset(),
                ProductionContent.block(cell.part()).getName(), cell.part().directional() ? cell.facing() : null)).toList();
    }

    @Override public boolean stillValid(Player player) { return inventory.stillValid(player); }

    @Override public boolean clickMenuButton(Player player, int button) {
        return player instanceof ServerPlayer server && server.containerMenu == this && stillValid(player)
                && inventory instanceof ProductionBlockEntity controller && controller.action(button);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= PLAYER_END) return ItemStack.EMPTY;
        var slot = getSlot(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var original = stack.copy();
        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (getSlot(0).mayPlace(stack) && getSlot(0).getItem().isEmpty()) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (!(index < HOTBAR ? moveItemStackTo(stack, HOTBAR, PLAYER_END, false)
                : moveItemStackTo(stack, PLAYER_START, HOTBAR, false))) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
