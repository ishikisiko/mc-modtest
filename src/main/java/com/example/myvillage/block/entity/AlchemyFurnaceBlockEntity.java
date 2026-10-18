package com.example.myvillage.block.entity;

import com.example.myvillage.item.SpiritStoneTags;

import com.example.myvillage.alchemy.*;
import com.example.myvillage.block.AlchemyFurnaceBlock;
import com.example.myvillage.block.ModBlockEntities;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import com.example.myvillage.cultivation.crafting.CraftingRules;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Five saved slots, no item-handler capability. The server alone starts and commits one batch. */
public final class AlchemyFurnaceBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SLOT_COUNT = 5;
    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final NonNullList<ItemStack> identities = NonNullList.withSize(4, ItemStack.EMPTY);
    private final BrewingProgress progress = new BrewingProgress();
    private AlchemyBatch batch;
    private String selectedRecipe = "";
    private int duration;
    private int status;
    public final ContainerData data = new ContainerData() {
        public int get(int index) { return switch (index) {
            case 0 -> progress.ticks(); case 1 -> duration; case 2 -> status;
            case 3 -> batch == null ? 0 : batch.score(); case 4 -> batch == null ? 0 : batch.cap();
            case 5 -> batch == null ? 0 : 1; case 6 -> recipeIndex(); default -> 0;
        }; }
        public void set(int index, int value) { }
        public int getCount() { return 7; }
    };
    public AlchemyFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALCHEMY_FURNACE.get(), pos, state);
    }
    public static String id(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
    public boolean active() { return batch != null; }
    private int recipeIndex() {
        var recipes = AlchemyRules.get().recipes();
        for (int i = 0; i < recipes.size(); i++) if (recipes.get(i).id().equals(selectedRecipe)) return i + 1;
        return 0;
    }
    private AlchemyRules.Recipe matching() {
        return AlchemyRules.get().recipe(selectedRecipe)
                .filter(r -> r.matches(r.formula(), id(items.get(1)), id(items.get(2)))).orElse(null);
    }
    private boolean validActor(ServerPlayer player, AlchemyMenu menu) {
        return level != null && !level.isClientSide() && player.level() == level
                && player.isAlive() && !player.isSpectator() && player.containerMenu == menu
                && menu.isFor(this) && stillValid(player);
    }
    /** Selection is an intent: check this player's knowledge and plan all transfers before committing. */
    public boolean select(ServerPlayer player, AlchemyMenu menu, int index) {
        var recipes = AlchemyRules.get().recipes();
        if (!validActor(player, menu) || active() || index < 0 || index >= recipes.size()) return false;
        var recipe = recipes.get(index);
        if (!AlchemyKnowledge.knows(player, recipe.id())) return false;
        var plan = AlchemyAutofill.plan(player.getInventory().items,
                java.util.List.of(items.get(1), items.get(2), items.get(3)),
                java.util.List.of(recipe.ingredients().get(0), recipe.ingredients().get(1), AlchemyRules.FUEL),
                java.util.List.of(1, 1, recipe.fuelCount()));
        if (plan.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.myvillage.alchemy.inventory_full"), true);
            return false;
        }
        var transfer = plan.get();
        for (int i = 0; i < transfer.inventory().size(); i++) player.getInventory().items.set(i, transfer.inventory().get(i));
        for (int i = 0; i < 3; i++) items.set(i + 1, transfer.inputs().get(i));
        selectedRecipe = recipe.id(); captureInputChanges(); super.setChanged();
        player.getInventory().setChanged(); updateIdleStatus(); menu.broadcastChanges(); return true;
    }
    private void updateIdleStatus() {
        var recipe = matching(); duration = AlchemyRules.get().recipe(selectedRecipe).map(AlchemyRules.Recipe::durationTicks).orElse(0);
        status = recipe == null ? (selectedRecipe.isEmpty() ? 0 : 1)
                : !hasFuel(recipe) ? 2 : !items.get(4).isEmpty() ? 3 : 5;
    }
    private boolean hasFuel(AlchemyRules.Recipe recipe) {
        return recipe != null && SpiritStoneTags.isLowGrade(items.get(3)) && items.get(3).getCount() >= recipe.fuelCount();
    }
    private boolean captureInputChanges() {
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            if (!ItemStack.matches(identities.get(i), items.get(i))) changed = true;
            identities.set(i, items.get(i).copy());
        }
        return changed;
    }
    private void clearBatch() { batch = null; progress.reset(); }
    /** Called only by the current server menu; client requests contain only the button number. */
    public boolean start(ServerPlayer player, AlchemyMenu menu) {
        AlchemyRules.Recipe recipe = matching();
        CraftingAptitudes aptitude = CultivationService.craftingAptitudes(player);
        boolean validActor = validActor(player, menu);
        if (validActor && !active() && !AlchemyKnowledge.knows(player, selectedRecipe)) {
            player.displayClientMessage(Component.translatable("message.myvillage.alchemy.formula_unknown"), true); return false;
        }
        AlchemyBatch.Rejection rejection = AlchemyBatch.rejection(validActor, active(),
                aptitude.initialized() && CultivationService.getProfile(player).spiritualRoot().isPresent(),
                recipe != null, hasFuel(recipe), items.get(4).isEmpty());
        if (rejection != null) {
            player.displayClientMessage(Component.translatable(rejection.messageKey()), true); return false;
        }
        int cap = aptitude.qualityCap(CraftingAptitudes.Profession.ALCHEMY, CraftingRules.get());
        AlchemyRules rules = AlchemyRules.get();
        int quality = rules.rollQuality(cap, level.random.nextInt(rules.qualityWeightTotal(cap)));
        batch = new AlchemyBatch(player.getUUID(), recipe.id(), aptitude.alchemy(), cap, quality);
        progress.restore(recipe.id(), 0, recipe.durationTicks());
        duration = recipe.durationTicks(); status = 4;
        captureInputChanges(); super.setChanged();
        menu.broadcastChanges(); return true;
    }
    public static void serverTick(Level level, BlockPos pos, BlockState state, AlchemyFurnaceBlockEntity furnace) {
        furnace.tick(level, pos, state);
    }
    private void tick(Level level, BlockPos pos, BlockState state) {
        int previousTicks = progress.ticks();
        boolean wasActive = active();
        boolean changedInputs = captureInputChanges();
        // Defensive invalidation if another system mutated a returned stack outside the container API.
        if (changedInputs && active()) clearBatch();
        AlchemyRules.Recipe recipe = matching();
        if (active() && (recipe == null || !recipe.id().equals(batch.recipe()) || !hasFuel(recipe))) clearBatch();
        duration = recipe == null ? 0 : recipe.durationTicks();
        boolean complete = false;
        if (!active()) {
            updateIdleStatus();
        } else {
            ItemStack result = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(recipe.output())), recipe.outputCount());
            PillQuality.apply(result, batch.quality());
            ItemStack out = items.get(4);
            boolean space = BrewingProgress.outputFits(out.isEmpty(), ItemStack.isSameItemSameComponents(out, result),
                    out.getCount(), result.getCount(), Math.min(getMaxStackSize(), result.getMaxStackSize()));
            status = space ? 4 : 3;
            complete = progress.advance(recipe.id(), duration, false, space);
            if (complete) {
                // Commit once on this server tick, then require a new attributed start. Knowledge is retained.
                items.get(1).shrink(1); items.get(2).shrink(1); items.get(3).shrink(recipe.fuelCount());
                if (out.isEmpty()) items.set(4, result); else out.grow(result.getCount());
                clearBatch(); captureInputChanges(); status = 3;
            }
        }
        boolean lit = active() && status == 4;
        if (state.getValue(AlchemyFurnaceBlock.LIT) != lit)
            level.setBlock(pos, state.setValue(AlchemyFurnaceBlock.LIT, lit), 3);
        if (complete || changedInputs || previousTicks != progress.ticks() || wasActive != active()) super.setChanged();
    }
    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        if (active() && slot < 4) return ItemStack.EMPTY;
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) inputChanged(); return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (active() && slot < 4) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.takeItem(items, slot); inputChanged(); return result;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (active() && slot < 4) return;
        items.set(slot, stack); stack.limitSize(Math.min(getMaxStackSize(), stack.getMaxStackSize())); inputChanged();
    }
    private void inputChanged() {
        if (captureInputChanges()) clearBatch(); super.setChanged();
    }
    @Override public void setChanged() {
        // Slot.merge may mutate existing stacks before notifying the container.
        if (captureInputChanges()) clearBatch();
        super.setChanged();
    }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (active() && slot < 4) return false;
        return switch (slot) {
            case 0 -> false; // Legacy return-only slot; no physical credential is accepted.
            case 1, 2 -> AlchemyRules.get().isHerb(id(stack));
            case 3 -> SpiritStoneTags.isLowGrade(stack);
            default -> false;
        };
    }
    /** Block removal drops the existing inventories, then clears them and abandons any unfinished batch. */
    @Override public void clearContent() { items.clear(); clearBatch(); captureInputChanges(); super.setChanged(); }
    @Override public Component getDisplayName() { return Component.translatable("container.myvillage.alchemy_furnace"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AlchemyMenu(id, inventory, this, data);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Progress", progress.ticks()); tag.putString("Recipe", progress.recipe());
        tag.putString("SelectedRecipe", selectedRecipe);
        if (batch != null) tag.put("Batch", batch.save()); else tag.remove("Batch");
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items, registries);
        clearBatch(); captureInputChanges();
        selectedRecipe = tag.getString("SelectedRecipe");
        if (selectedRecipe.isEmpty()) selectedRecipe = AlchemyRules.get().recipeForFormula(id(items.get(0)))
                .map(AlchemyRules.Recipe::id).orElse("");
        AlchemyRules.Recipe recipe = matching();
        AlchemyBatch restored = tag.contains("Batch", Tag.TAG_COMPOUND) ? AlchemyBatch.read(tag.getCompound("Batch")).orElse(null) : null;
        if (restored != null && recipe != null && hasFuel(recipe) && recipe.id().equals(restored.recipe())
                && tag.contains("Recipe", Tag.TAG_STRING) && recipe.id().equals(tag.getString("Recipe"))
                && tag.contains("Progress", Tag.TAG_INT) && tag.getInt("Progress") >= 0 && tag.getInt("Progress") < recipe.durationTicks()) {
            batch = restored; progress.restore(recipe.id(), tag.getInt("Progress"), recipe.durationTicks());
        }
        duration = recipe == null ? 0 : recipe.durationTicks();
        if (active()) status = 4; else updateIdleStatus();
    }
}
