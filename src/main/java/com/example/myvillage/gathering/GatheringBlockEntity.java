package com.example.myvillage.gathering;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Sole energy owner; no idle ticking cost and no wall-clock/offline accrual. */
public final class GatheringBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int DATA_COUNT = 14;
    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private GatheringEnergy energy = GatheringEnergy.EMPTY;
    private boolean enabled, formed, running, leased;
    private GatheringPattern.Check check = new GatheringPattern.Check(9, BlockPos.ZERO);
    private final Map<UUID, Long> users = new HashMap<>();
    public final ContainerData data = new ContainerData() {
        public int get(int i) {
            return switch (i) {
                case 0 -> enabled ? 1 : 0; case 1 -> formed ? 1 : 0; case 2 -> charges();
                case 3 -> users.size(); case 4 -> check.issue();
                case 5 -> check.offset().getX(); case 6 -> check.offset().getY(); case 7 -> check.offset().getZ();
                case 8 -> effectiveBonus(); case 9 -> accepts(items.getFirst()) ? GatheringRules.get().charges(queuedFuel()) : 0;
                case 10 -> tier().ordinal(); case 11 -> energy.fuel() == null ? 0 : energy.fuel().wire();
                case 12 -> queuedFuel() == null ? 0 : queuedFuel().wire();
                case 13 -> GatheringRules.get().tier(tier()).bonus(queuedFuel());
                default -> 0;
            };
        }
        public void set(int i, int value) { }
        public int getCount() { return DATA_COUNT; }
    };
    public GatheringBlockEntity(BlockPos pos, BlockState state) { super(GatheringContent.ENTITY.get(), pos, state); }
    public boolean formed() { return formed; }
    public boolean enabled() { return enabled; }
    public boolean running() { return running; }
    public int charges() { return energy.charges(); }
    public GatheringTier tier() { return ((GatheringBlock)getBlockState().getBlock()).tier(); }
    public GatheringFuel activeFuel() { return energy.fuel(); }
    public GatheringFuel queuedFuel() { return GatheringFuel.of(items.getFirst()); }
    public boolean accepts(ItemStack stack) { return GatheringRules.get().tier(tier()).bonus(GatheringFuel.of(stack)) > 0; }
    public int effectiveBonus() { return energy.bonus(tier(), queuedFuel(), GatheringRules.get()); }
    public int userCount() { return users.size(); }
    public GatheringPattern.Check freshCheck() {
        check = level == null ? new GatheringPattern.Check(7, BlockPos.ZERO) : GatheringPattern.check(level, worldPosition);
        boolean changed = formed != check.formed();
        formed = check.formed();
        if (!formed) { users.clear(); running = false; }
        if (changed) sync();
        return check;
    }
    public void tick() {
        if (level == null) return;
        long now = level.getGameTime();
        users.entrySet().removeIf(e -> now - e.getValue() >= GatheringRules.get().activeWindowTicks());
        if (now % GatheringRules.get().checkIntervalTicks() == 0) freshCheck();
        boolean next = formed && enabled && !users.isEmpty();
        if (next != running) { running = next; sync(); }
    }
    public boolean hasEnergy() { return charges() > 0 || accepts(items.getFirst()); }
    public boolean contains(ServerPlayer player) {
        int r = GatheringRules.get().tier(tier()).innerRadius();
        return player.level() == level && player.getX() >= worldPosition.getX() - r
                && player.getX() < worldPosition.getX() + r + 1
                && player.getZ() >= worldPosition.getZ() - r && player.getZ() < worldPosition.getZ() + r + 1
                && Math.abs(player.getY() - (worldPosition.getY() + 1)) < .1;
    }
    public boolean eligible(ServerPlayer player) {
        return !leased && enabled && !isRemoved() && contains(player) && hasEnergy() && freshCheck().formed();
    }
    public boolean control(ServerPlayer player, GatheringMenu menu, int button) {
        if (level == null || level.isClientSide() || leased || !player.isAlive() || player.isSpectator()
                || player.level() != level || player.containerMenu != menu || !menu.isFor(this) || !stillValid(player)) return false;
        if (button == 0) {
            if (!freshCheck().formed() || !hasEnergy()) return false;
            enabled = true;
        } else if (button == 1) { enabled = false; users.clear(); running = false; }
        else return false;
        setChanged(); sync(); menu.broadcastChanges(); return true;
    }
    /** A synchronous reservation is paired with the exact CultivationService replacement. */
    public Lease reserve(ServerPlayer player) {
        if (!eligible(player)) return null;
        ItemStack before = items.getFirst().copy();
        var previousEnergy = energy;
        int bonus = effectiveBonus();
        var debit = energy.debit(tier(), queuedFuel(), GatheringRules.get());
        if (debit == null) return null;
        leased = true;
        if (debit.consumedStone()) items.getFirst().shrink(1);
        energy = debit.remaining(); setChanged();
        return new Lease(player, before, previousEnergy, debit.consumedStone(), bonus);
    }
    public final class Lease {
        private final ServerPlayer player;
        private final ItemStack before;
        private final GatheringEnergy previousEnergy;
        private final int bonus;
        private final boolean consumedStone;
        private boolean finished;
        private Lease(ServerPlayer player, ItemStack before, GatheringEnergy previousEnergy, boolean consumedStone, int bonus) {
            this.player = player; this.before = before; this.previousEnergy = previousEnergy; this.consumedStone = consumedStone; this.bonus = bonus;
        }
        public void finish(boolean committed) {
            if (finished) return;
            finished = true;
            leased = false;
            if (!committed) {
                if (level != null && !isRemoved() && level.getBlockEntity(worldPosition) == GatheringBlockEntity.this) {
                    items.set(0, before); energy = previousEnergy;
                } else if (consumedStone && level != null) {
                    Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + 1,
                            worldPosition.getZ() + .5, before.copyWithCount(1));
                }
            } else if (level != null && !isRemoved()) {
                Long last = users.put(player.getUUID(), level.getGameTime());
                boolean first = !running;
                running = true;
                setChanged();
                try {
                    if (last == null) player.displayClientMessage(Component.literal("聚灵阵生效：修为获取 +"
                            + bonus / 100.0 + "%（每批取整）"), true);
                    if (first) sync();
                } catch (RuntimeException exception) {
                    com.mojang.logging.LogUtils.getLogger().warn("聚灵阵收益已提交，表现同步失败", exception);
                }
            }
            setChanged();
        }
    }
    private void sync() {
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    public SimpleContainer dropSnapshot() { return new SimpleContainer(items.getFirst().copy()); }
    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return items.getFirst().isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? items.getFirst() : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int count) {
        if (slot != 0 || leased) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.removeItem(items, slot, count);
        if (!result.isEmpty()) setChanged(); return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, Integer.MAX_VALUE); }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot != 0 || leased) return;
        items.set(0, stack); stack.limitSize(Math.min(getMaxStackSize(), stack.getMaxStackSize())); setChanged();
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 && !leased && accepts(stack); }
    @Override public int[] getSlotsForFace(Direction side) { return new int[0]; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() { items = NonNullList.withSize(1, ItemStack.EMPTY); energy = GatheringEnergy.EMPTY; enabled = false; running = false; users.clear(); setChanged(); }
    @Override public Component getDisplayName() { return Component.literal(tier().label); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        freshCheck(); return new GatheringMenu(id, inventory, this, data);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Version", 2); tag.putInt("Charges", charges()); tag.putBoolean("Enabled", enabled);
        tag.putString("FuelGrade", energy.fuel() == null ? "" : energy.fuel().id);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(1, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items, registries);
        energy = GatheringEnergy.EMPTY; enabled = false; formed = false; running = false; leased = false; users.clear();
        int version = tag.getInt("Version");
        if ((version == 1 || version == 2) && tag.contains("Charges", Tag.TAG_INT)) {
            energy = GatheringEnergy.restore(version, tag.getInt("Charges"), tag.getString("FuelGrade"), tier(), GatheringRules.get());
            enabled = tag.getBoolean("Enabled");
        }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(); tag.putBoolean("Formed", formed); tag.putBoolean("Running", running); return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) { formed = tag.getBoolean("Formed"); running = tag.getBoolean("Running"); }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) { handleUpdateTag(packet.getTag(), registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
