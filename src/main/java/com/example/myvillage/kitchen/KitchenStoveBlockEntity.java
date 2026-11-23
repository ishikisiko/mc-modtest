package com.example.myvillage.kitchen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

public final class KitchenStoveBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int INPUTS = 5, BOWLS = 5, FUEL = 6, PRODUCT = 7, BUCKETS = 8, BOTTLES = 9, SIZE = 10;
    public static final int DATA_SIZE = 5;
    public static final int SELECTED = 0, PROGRESS = 1, MANUAL = 2, CHARCOAL = 3, STATUS = 4;
    public static final int IDLE = 0, COOKING = 1, MISSING_INPUT = 2, MISSING_BOWL = 3, MISSING_FUEL = 4, OUTPUT_BLOCKED = 5;
    private static final int[] TOP = {0, 1, 2, 3, 4}, LEFT = {FUEL}, RIGHT = {BOWLS}, BOTTOM = {PRODUCT, BUCKETS, BOTTLES}, NONE = {};

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int selected, progress, charcoal;
    private int status;
    private boolean manual;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case SELECTED -> selected;
                case PROGRESS -> progress;
                case MANUAL -> manual ? 1 : 0;
                case CHARCOAL -> charcoal;
                case STATUS -> status;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) { }
        @Override public int getCount() { return DATA_SIZE; }
    };

    public KitchenStoveBlockEntity(BlockPos pos, BlockState state) {
        super(KitchenContent.STOVE_ENTITY.get(), pos, state);
    }

    @Override protected Component getDefaultName() { return Component.translatable("block.yuanxian.kitchen_stove"); }
    @Override public int getContainerSize() { return SIZE; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> contents) { items = contents; }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new KitchenMenu(id, inventory, this, data); }

    public KitchenRules.Recipe recipe() { return KitchenRules.get().stove.get(selected); }

    boolean control(ServerPlayer player, int button) {
        if (!stillValid(player) || player.isSpectator()) return false;
        var recipes = KitchenRules.get().stove;
        if (button >= 0 && button < recipes.size()) {
            if (selected != button) {
                selected = button;
                progress = 0;
                manual = false;
                setChanged();
            }
            return true;
        }
        if (button == KitchenMenu.TOGGLE_COOK) {
            manual = !manual;
            setChanged();
            return true;
        }
        return false;
    }

    public static KitchenRules.Ingredient material(KitchenRules.Recipe recipe, int slot) {
        int index = 0;
        for (var ingredient : recipe.ingredients()) {
            if (ingredient.bowl()) continue;
            if (index++ == slot) return ingredient;
        }
        return null;
    }

    public static boolean accepts(KitchenRules.Recipe recipe, int slot, ItemStack stack) {
        if (slot == FUEL) return stack.is(Items.CHARCOAL);
        if (slot == BOWLS) return recipe.servedInBowl() && stack.is(Items.BOWL);
        if (slot < 0 || slot >= INPUTS) return false;
        var ingredient = material(recipe, slot);
        return ingredient != null && ingredient.matches(stack);
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return accepts(recipe(), slot, stack); }

    private boolean fits(int slot, ItemStack stack) {
        ItemStack old = getItem(slot);
        return stack.isEmpty() || old.isEmpty() || ItemStack.isSameItemSameComponents(stack, old)
                && old.getCount() + stack.getCount() <= old.getMaxStackSize();
    }

    private void store(int slot, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack old = getItem(slot);
        if (old.isEmpty()) items.set(slot, stack);
        else old.grow(stack.getCount());
    }

    private static ItemStack remainder(KitchenRules.Recipe recipe, ItemStack source, ItemStack container) {
        for (var ingredient : recipe.ingredients()) {
            if (ingredient.matches(source)) return container.copyWithCount(ingredient.count());
        }
        return ItemStack.EMPTY;
    }

    private int ready(KitchenRules.Recipe recipe, ItemStack result, ItemStack buckets, ItemStack bottles) {
        for (int slot = 0; slot < INPUTS; slot++) {
            var material = material(recipe, slot);
            if (material != null && (!material.matches(getItem(slot)) || getItem(slot).getCount() < material.count()))
                return MISSING_INPUT;
        }
        if (recipe.servedInBowl() && !getItem(BOWLS).is(Items.BOWL)) return MISSING_BOWL;
        if (charcoal == 0 && !getItem(FUEL).is(Items.CHARCOAL)) return MISSING_FUEL;
        if (!fits(PRODUCT, result) || !fits(BUCKETS, buckets) || !fits(BOTTLES, bottles)) return OUTPUT_BLOCKED;
        return COOKING;
    }

    public void tick() {
        if (level == null) return;
        boolean running = manual || level.hasNeighborSignal(worldPosition);
        if (!running) {
            status = IDLE;
            setLit(false);
            return;
        }
        var recipe = recipe();
        ItemStack result = recipe.result();
        ItemStack buckets = remainder(recipe, Items.WATER_BUCKET.getDefaultInstance(), Items.BUCKET.getDefaultInstance());
        ItemStack bottles = remainder(recipe, Items.HONEY_BOTTLE.getDefaultInstance(), Items.GLASS_BOTTLE.getDefaultInstance());
        status = ready(recipe, result, buckets, bottles);
        setLit(status == COOKING);
        if (status != COOKING) return;
        if (++progress < recipe.ticks()) {
            setChanged();
            return;
        }
        // 检查和出菜在同一个服务器 tick。缺料、停机或取走材料均不会预扣这批投入。
        for (int slot = 0; slot < INPUTS; slot++) {
            var material = material(recipe, slot);
            if (material != null) getItem(slot).shrink(material.count());
        }
        if (recipe.servedInBowl()) getItem(BOWLS).shrink(1);
        if (charcoal == 0) {
            getItem(FUEL).shrink(1);
            charcoal = KitchenRules.get().charcoalBatches;
        }
        charcoal--;
        store(PRODUCT, result);
        store(BUCKETS, buckets);
        store(BOTTLES, bottles);
        progress = 0;
        setChanged();
    }

    private void setLit(boolean lit) {
        var state = getBlockState();
        if (level != null && state.getValue(KitchenStationBlock.LIT) != lit)
            level.setBlock(worldPosition, state.setValue(KitchenStationBlock.LIT, lit), Block.UPDATE_CLIENTS);
    }

    @Override public int[] getSlotsForFace(Direction side) {
        if (side == null || isRemoved()) return NONE;
        if (side == Direction.UP) return TOP;
        if (side == Direction.DOWN) return BOTTOM;
        Direction front = getBlockState().getValue(KitchenStationBlock.FACING);
        if (side == front.getClockWise()) return LEFT;
        if (side == front.getCounterClockWise()) return RIGHT;
        return NONE;
    }

    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (!accepts(recipe(), slot, stack)) return false;
        for (int allowed : getSlotsForFace(side)) if (allowed == slot) return true;
        return false;
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return !isRemoved() && side == Direction.DOWN && slot >= PRODUCT && slot < SIZE;
    }
    public IItemHandler sidedHandler(Direction side) {
        return getSlotsForFace(side).length == 0 ? null : new SidedInvWrapper(this, side);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("recipe", selected);
        tag.putInt("progress", progress);
        tag.putInt("charcoal", charcoal);
        tag.putBoolean("manual", manual);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        selected = Math.clamp(tag.getInt("recipe"), 0, KitchenRules.get().stove.size() - 1);
        progress = Math.clamp(tag.getInt("progress"), 0, recipe().ticks() - 1);
        charcoal = Math.clamp(tag.getInt("charcoal"), 0, KitchenRules.get().charcoalBatches - 1);
        manual = tag.getBoolean("manual");
    }
}
