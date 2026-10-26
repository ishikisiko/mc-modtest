package com.example.myvillage.talisman;

import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import com.example.myvillage.cultivation.crafting.CraftingRules;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 无自动化的三槽单人制符案；物品/进度由方块唯一持有，掉落整个封存案。 */
public final class TalismanDeskBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    private NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private TalismanBatch batch;
    private int selected, status;
    private UUID viewer;
    private final DrawingSession session = new DrawingSession();
    private boolean corrupt, needsRelease;
    private CompoundTag corruptBatch;
    public final ContainerData data = new ContainerData() {
        public int get(int i) { return switch (i) {
            case 0 -> selected; case 1 -> batch == null ? 0 : batch.pulses();
            case 2 -> batch == null ? TalismanRules.get().draw("pulses") : batch.required();
            case 3 -> status; case 4 -> batch == null ? 0 : batch.quality();
            case 5 -> active() ? 1 : 0; case 6 -> batch == null ? 0 : batch.aptitude(); default -> 0;
        }; }
        public void set(int i, int value) { }
        public int getCount() { return 7; }
    };
    public TalismanDeskBlockEntity(BlockPos p, BlockState s) { super(TalismanContent.DESK_ENTITY.get(), p, s); }
    public boolean active() { return batch != null || corrupt; }
    private ServerPlayer liveViewer() {
        if (level == null || level.getServer() == null || viewer == null) return null;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(viewer);
        return valid(p) && p.containerMenu instanceof TalismanMenu m && m.isFor(this) ? p : null;
    }
    private boolean valid(ServerPlayer p) {
        return p != null && p.isAlive() && !p.isSpectator() && !isRemoved() && p.level() == level && stillValid(p);
    }
    public boolean occupied() { return liveViewer() != null; }
    public void release(Player p) {
        if (p.getUUID().equals(viewer)) { viewer = null; session.stop(); status = 0; }
    }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
        if (!(p instanceof ServerPlayer server) || !valid(server) || occupied()) return null;
        viewer = p.getUUID(); session.stop(); needsRelease = false;
        return new TalismanMenu(id, inventory, this, data);
    }
    public boolean button(ServerPlayer p, TalismanMenu menu, int button) {
        if (!valid(p) || p.containerMenu != menu || !menu.isFor(this) || !p.getUUID().equals(viewer)) return false;
        if (button == 11) { needsRelease = false; session.stop(); status = 0; return true; }
        if (button >= 0 && button <= 2) {
            if (active()) return false;
            selected = button; setChanged(); return true;
        }
        if (button != 10 || corrupt || needsRelease) return false;
        if (batch != null && !batch.owner().equals(p.getUUID())) {
            status = 6; return false;
        }
        // Heartbeat is only a lease refresh; at most one paid pulse per configured server interval.
        session.heartbeat(level.getGameTime(), TalismanRules.get().draw("lease_ticks")); return true;
    }
    private boolean outputFits(ItemStack out) {
        ItemStack existing = items.get(2);
        return existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, out)
                && existing.getCount() + out.getCount() <= Math.min(getMaxStackSize(), out.getMaxStackSize());
    }
    private boolean materials() {
        var r = TalismanRules.get();
        return items.get(0).is(TalismanContent.PAPER.get()) && items.get(0).getCount() >= r.draw("paper_count")
                && items.get(1).is(TalismanContent.INK.get()) && items.get(1).getCount() >= r.draw("ink_count");
    }
    public static void tick(Level world, BlockPos pos, BlockState state, TalismanDeskBlockEntity desk) { desk.tick(); }
    private void tick() {
        ServerPlayer p = liveViewer();
        if (p == null) { session.stop(); viewer = null; status = corrupt ? 7 : 0; return; }
        if (corrupt) { status = 7; return; }
        long now = level.getGameTime();
        if (!session.holding(now)) { status = needsRelease ? 8 : 0; return; }
        if (batch != null && !batch.owner().equals(p.getUUID())) { status = 6; return; }
        CultivationService.initializeCraftingAptitudes(p);
        var aptitude = CultivationService.craftingAptitudes(p);
        if (!aptitude.talismanInitialized() || CultivationService.getProfile(p).spiritualRoot().isEmpty()) { status = 5; return; }
        int quality = batch == null ? aptitude.qualityCap(CraftingAptitudes.Profession.TALISMAN, CraftingRules.get()) : batch.quality();
        ItemStack result = TalismanContent.result(batch == null ? selected : batch.recipe(), quality);
        if (!outputFits(result)) { status = 3; return; }
        if (batch == null && !materials()) { status = 2; return; }
        if (!session.due(now)) { status = 1; return; }
        var r = TalismanRules.get();
        // Plan the entire pulse before the service-owned qi commit. No inventory callbacks are interleaved.
        TalismanBatch next = batch == null ? new TalismanBatch(p.getUUID(), selected, aptitude.talisman(), quality, quality,
                0, r.draw("pulses"), r.draw("power_per_pulse")) : batch;
        boolean accepted = session.pulse(now, r.draw("pulse_ticks"), batch != null || materials(), outputFits(result),
                () -> CultivationService.spendSpiritualPower(p, next.powerPerPulse()), () -> {
            if (batch == null) { items.get(0).shrink(r.draw("paper_count")); items.get(1).shrink(r.draw("ink_count")); }
            batch = next.advance(); status = 1;
            if (batch.pulses() == batch.required()) {
                if (items.get(2).isEmpty()) items.set(2, result); else items.get(2).grow(result.getCount());
                batch = null; needsRelease = true; session.stop(); status = 8;
            }
        });
        if (!accepted) { status = 4; return; }
        setChanged();
    }
    public void diagnostic(com.google.gson.JsonObject j) {
        j.addProperty("desk", true); j.addProperty("recipe", selected);
        j.addProperty("recipeId", new String[]{"lightning", "snare", "ward"}[selected]);
        j.addProperty("stage", corrupt ? "corrupt" : batch == null ? "idle" : "drawing");
        j.addProperty("progress", data.get(1)); j.addProperty("required", data.get(2));
        j.addProperty("batch", active()); j.addProperty("frozenQuality", data.get(4)); j.addProperty("frozenAptitude", data.get(6));
        j.addProperty("status", status); j.addProperty("holding", liveViewer() != null && session.holding(level.getGameTime()));
        j.addProperty("owner", batch == null ? null : batch.owner().toString());
        j.addProperty("viewer", viewer == null ? null : viewer.toString());
        com.google.gson.JsonArray slots = new com.google.gson.JsonArray();
        for (int i = 0; i < 3; i++) {
            ItemStack stack = items.get(i); var slot = new com.google.gson.JsonObject();
            slot.addProperty("slot", i); slot.addProperty("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            slot.addProperty("count", stack.getCount());
            if (!stack.isEmpty() && stack.getItem() instanceof TalismanItem) slot.addProperty("quality", TalismanQuality.get(stack));
            slots.add(slot);
        }
        j.add("inventory", slots);
    }
    @Override public int getContainerSize() { return 3; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int i) { return items.get(i); }
    @Override public ItemStack removeItem(int i, int count) {
        if (active() && i < 2) return ItemStack.EMPTY;
        ItemStack out = ContainerHelper.removeItem(items, i, count); if (!out.isEmpty()) setChanged(); return out;
    }
    @Override public ItemStack removeItemNoUpdate(int i) { return active() && i < 2 ? ItemStack.EMPTY : ContainerHelper.takeItem(items, i); }
    @Override public void setItem(int i, ItemStack stack) {
        if (active() && i < 2) return;
        items.set(i, stack); stack.limitSize(Math.min(getMaxStackSize(), stack.getMaxStackSize())); setChanged();
    }
    @Override public boolean canPlaceItem(int i, ItemStack s) {
        return !active() && (i == 0 && s.is(TalismanContent.PAPER.get()) || i == 1 && s.is(TalismanContent.INK.get()));
    }
    @Override public boolean stillValid(Player p) { return Container.stillValidBlockEntity(this, p); }
    @Override public void clearContent() { items.clear(); batch = null; corrupt = false; corruptBatch = null; session.reset(); setChanged(); }
    @Override public Component getDisplayName() { return Component.literal("朱纹制符案"); }
    @Override public int[] getSlotsForFace(Direction d) { return new int[0]; }
    @Override public boolean canPlaceItemThroughFace(int i, ItemStack s, Direction d) { return false; }
    @Override public boolean canTakeItemThroughFace(int i, ItemStack s, Direction d) { return false; }
    @Override protected void saveAdditional(CompoundTag t, HolderLookup.Provider r) {
        super.saveAdditional(t, r); ContainerHelper.saveAllItems(t, items, r); t.putInt("Selected", selected);
        if (batch != null) t.put("Batch", batch.save()); else if (corruptBatch != null) t.put("Batch", corruptBatch.copy());
    }
    @Override protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) {
        super.loadAdditional(t, r); items = NonNullList.withSize(3, ItemStack.EMPTY); ContainerHelper.loadAllItems(t, items, r);
        selected = Math.max(0, Math.min(2, t.getInt("Selected"))); batch = null; corrupt = false; corruptBatch = null;
        if (t.contains("Batch", Tag.TAG_COMPOUND)) {
            try { batch = TalismanBatch.load(t.getCompound("Batch")); if (batch.pulses() == batch.required()) throw new IllegalArgumentException("完成符稿不应被保存"); selected = batch.recipe(); }
            catch (RuntimeException e) { corrupt = true; corruptBatch = t.getCompound("Batch").copy(); }
        }
        viewer = null; session.reset(); status = corrupt ? 7 : 0;
    }
}
