package com.example.myvillage.block.entity;

import com.example.myvillage.alchemy.*;
import com.example.myvillage.block.AlchemyFurnaceBlock;
import com.example.myvillage.block.ModBlockEntities;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Five saved slots, no item-handler capability. The server alone commits completed batches. */
public final class AlchemyFurnaceBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SLOT_COUNT = 5;
    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final NonNullList<ItemStack> identities = NonNullList.withSize(4, ItemStack.EMPTY);
    private final BrewingProgress progress = new BrewingProgress();
    private int duration;
    private int status;
    public final ContainerData data = new ContainerData() {
        public int get(int index) { return switch (index) { case 0 -> progress.ticks(); case 1 -> duration; case 2 -> status; default -> 0; }; }
        public void set(int index, int value) { }
        public int getCount() { return 3; }
    };
    public AlchemyFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALCHEMY_FURNACE.get(), pos, state);
    }
    public static String id(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
    private AlchemyRules.Recipe matching() {
        return AlchemyRules.get().recipes().stream().filter(r -> r.matches(id(items.get(0)), id(items.get(1)), id(items.get(2))))
                .findFirst().orElse(null);
    }
    private boolean captureInputChanges() {
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            if (!ItemStack.isSameItemSameComponents(identities.get(i), items.get(i))) changed = true;
            identities.set(i, items.get(i).copyWithCount(items.get(i).isEmpty() ? 0 : 1));
        }
        return changed;
    }
    public static void serverTick(Level level, BlockPos pos, BlockState state, AlchemyFurnaceBlockEntity furnace) {
        furnace.tick(level, pos, state);
    }
    private void tick(Level level, BlockPos pos, BlockState state) {
        int previousTicks = progress.ticks();
        String previousRecipe = progress.recipe();
        boolean changedInputs = captureInputChanges();
        AlchemyRules.Recipe recipe = matching();
        duration = recipe == null ? 0 : recipe.durationTicks();
        ItemStack result = recipe == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(recipe.output())), recipe.outputCount());
        boolean fuel = recipe != null && id(items.get(3)).equals(AlchemyRules.FUEL) && items.get(3).getCount() >= recipe.fuelCount();
        ItemStack out = items.get(4);
        boolean space = recipe != null && BrewingProgress.outputFits(out.isEmpty(),
                ItemStack.isSameItemSameComponents(out, result), out.getCount(), result.getCount(),
                Math.min(getMaxStackSize(), result.getMaxStackSize()));
        status = recipe == null ? (AlchemyRules.get().recipeForFormula(id(items.get(0))).isPresent() ? 1 : 0)
                : !fuel ? 2 : !space ? 3 : 4;
        boolean complete = progress.advance(recipe == null ? "" : recipe.id(), duration, changedInputs, fuel && space);
        if (complete) {
            // All validation above and this commit execute on one server tick. Formula is retained.
            items.get(1).shrink(1); items.get(2).shrink(1); items.get(3).shrink(recipe.fuelCount());
            if (out.isEmpty()) items.set(4, result); else out.grow(result.getCount());
            captureInputChanges();
        }
        boolean lit = status == 4 && !complete;
        if (state.getValue(AlchemyFurnaceBlock.LIT) != lit)
            level.setBlock(pos, state.setValue(AlchemyFurnaceBlock.LIT, lit), 3);
        // Only state changes dirty the chunk; an idle furnace costs no repeated saves.
        if (complete || changedInputs || previousTicks != progress.ticks() || !previousRecipe.equals(progress.recipe())) setChanged();
    }
    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) inputChanged(); return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot); inputChanged(); return result;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack); stack.limitSize(Math.min(getMaxStackSize(), stack.getMaxStackSize())); inputChanged();
    }
    private void inputChanged() {
        if (captureInputChanges()) progress.reset(); setChanged();
    }
    @Override public void setChanged() {
        // Slot.merge mutates existing stacks before notifying the container.
        if (captureInputChanges()) progress.reset();
        super.setChanged();
    }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case 0 -> AlchemyRules.get().recipeForFormula(id(stack)).isPresent();
            case 1, 2 -> AlchemyRules.get().isHerb(id(stack));
            case 3 -> AlchemyRules.FUEL.equals(id(stack));
            default -> false;
        };
    }
    @Override public void clearContent() { items.clear(); inputChanged(); }
    @Override public Component getDisplayName() { return Component.translatable("container.myvillage.alchemy_furnace"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AlchemyMenu(id, inventory, this, data);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Progress", progress.ticks()); tag.putString("Recipe", progress.recipe());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items, registries);
        captureInputChanges(); AlchemyRules.Recipe recipe = matching();
        if (recipe != null && recipe.id().equals(tag.getString("Recipe")))
            progress.restore(recipe.id(), tag.getInt("Progress"), recipe.durationTicks());
        else progress.reset();
    }
}
