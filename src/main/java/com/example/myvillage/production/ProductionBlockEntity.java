package com.example.myvillage.production;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.cultivation.time.CultivationCalendarSavedData;
import com.example.myvillage.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class ProductionBlockEntity extends BlockEntity implements Container, MenuProvider {
    static final int SLOT_COUNT = 4;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final ProductionKind kind;
    private boolean enabled;
    private int mode;
    private int reserveFloor = ProductionRules.get().mining().reserve();
    private int target;
    private int mediumTarget = ProductionRules.get().mediumSupply();
    private long credit;
    private long workSince = -1;
    private long lastTick = Long.MIN_VALUE;
    private int pulseTicks;
    private boolean wasFormed;
    private int reserve;
    private int progress;
    private int nextGrade;
    private ProductionMenu.Status status = ProductionMenu.Status.STOPPED;
    private ProductionPattern.Check lastCheck;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            var offset = lastCheck == null ? BlockPos.ZERO : lastCheck.issue().subtract(lastCheck.anchor());
            return switch (index) {
                case ProductionMenu.KIND -> kind.ordinal();
                case ProductionMenu.ENABLED -> enabled ? 1 : 0;
                case ProductionMenu.MODE -> mode;
                case ProductionMenu.PROGRESS -> progress;
                case ProductionMenu.STATUS -> status.ordinal();
                case ProductionMenu.RESERVE -> reserve;
                case ProductionMenu.RESERVE_FLOOR -> reserveFloor;
                case ProductionMenu.TARGET -> target;
                case ProductionMenu.MEDIUM_TARGET -> mediumTarget;
                case ProductionMenu.NEXT_GRADE -> nextGrade;
                case ProductionMenu.PART -> lastCheck == null || lastCheck.expected() == null ? 0 : lastCheck.expected().ordinal() + 1;
                case ProductionMenu.X -> offset.getX();
                case ProductionMenu.Y -> offset.getY();
                case ProductionMenu.Z -> offset.getZ();
                case ProductionMenu.CAPACITY -> ProductionRules.get().mining().capacity();
                case ProductionMenu.FACING -> facing().get2DDataValue();
                default -> 0;
            };
        }
        @Override public void set(int index, int value) { }
        @Override public int getCount() { return ProductionMenu.DATA_COUNT; }
    };

    ProductionBlockEntity(BlockPos pos, BlockState state) {
        super(ProductionContent.CONTROLLER.get(), pos, state);
        kind = ProductionKind.forController(((ProductionBlock) state.getBlock()).part());
        target = kind.mining() ? ProductionRules.get().mining().target() : ProductionRules.get().lowSupply();
        mode = kind == ProductionKind.HIGH ? 3 : 0;
    }

    ProductionKind kind() { return kind; }
    Direction facing() { return getBlockState().getValue(ProductionBlock.FACING); }
    ProductionPattern.Check formation() { return ProductionPattern.check(level, worldPosition, kind, facing()); }

    void invalidateOutlet() {
        if (level == null) return;
        var layout = ProductionPattern.layout(kind, facing());
        var outlet = worldPosition.subtract(layout.controller().offset()).offset(layout.outlet().offset());
        if (level.hasChunkAt(outlet)) level.invalidateCapabilities(outlet);
    }

    void tick() {
        if (!(level instanceof ServerLevel server)) return;
        long gameTime = server.getGameTime();
        if (lastTick != gameTime - 1) pulseTicks = 0;
        lastTick = gameTime;
        if (++pulseTicks < ProductionRules.get().pulse()) return;
        int elapsed = pulseTicks;
        pulseTicks = 0;
        lastCheck = formation();
        if (lastCheck.formed() != wasFormed) {
            wasFormed = lastCheck.formed();
            invalidateOutlet();
        }
        if (!lastCheck.formed()) {
            status = ProductionMenu.Status.forCheck(lastCheck.result());
            // Incomplete rigs cannot catch up after the structure is repaired.
            if (kind.mining()) stopMiningClock();
            return;
        }
        if (kind.mining()) tickMine(server, lastCheck.anchor());
        else tickCondenser(elapsed);
    }

    private void tickMine(ServerLevel server, BlockPos eye) {
        var rules = ProductionRules.get().mining();
        var ledger = MineReserve.get(server);
        long now = CultivationCalendarSavedData.get(server.getServer().overworld()).elapsedCalendarTicks();
        var ore = ((ProductionBlock) server.getBlockState(eye).getBlock()).part();
        boolean xuantie = ore == ProductionPart.XUANTIE_MINE_EYE;
        nextGrade = xuantie ? 1 : 0;
        var product = new ItemStack(xuantie ? ModItems.RAW_XUANTIE.get() : Items.RAW_IRON);
        reserve = ledger.stock(eye, now).count();
        if (!enabled) { stopMining(ProductionMenu.Status.STOPPED); return; }
        if (!mineTool(xuantie)) { stopMining(ProductionMenu.Status.TOOL); return; }
        if (!hasRoom(1, product, target)) { stopMining(ProductionMenu.Status.FULL); return; }
        if (workSince < 0 || workSince > now) { workSince = now; setChanged(); }

        // A mine's natural anchor and its whole frame share a chunk. Replay completed batches in
        // timestamp order after unloading, with a hard bound from the small internal warehouse.
        for (int remaining = target - items.get(1).getCount(); remaining > 0; remaining--) {
            int interval = mode == 1 ? rules.rush() : rules.steady();
            if (now - workSince < interval) break;
            long finish = workSince + interval;
            if (mode == 1 && ledger.stock(eye, finish).count() <= reserveFloor) {
                mode = 0;
                setChanged();
                continue;
            }
            if (!ledger.take(eye, finish, 0)) { stopMining(ProductionMenu.Status.DEPLETED); break; }
            putOne(1, product);
            wearPick();
            workSince = finish;
            setChanged();
            if (!mineTool(xuantie) || !hasRoom(1, product, target)) break;
        }
        reserve = ledger.stock(eye, now).count();
        if (!mineTool(xuantie)) { stopMining(ProductionMenu.Status.TOOL); return; }
        if (!hasRoom(1, product, target)) { stopMining(ProductionMenu.Status.FULL); return; }
        if (workSince >= 0) {
            progress = (int) Math.min(100, (now - workSince) * 100 / (mode == 1 ? rules.rush() : rules.steady()));
            status = ProductionMenu.Status.RUNNING;
        }
    }

    private boolean mineTool(boolean xuantie) {
        var pick = items.getFirst();
        return validPick(pick) && pick.isCorrectToolForDrops(xuantie
                ? ModBlocks.XUANTIE_ORE.get().defaultBlockState() : Blocks.IRON_ORE.defaultBlockState());
    }

    static boolean validPick(ItemStack pick) { return pick.is(ItemTags.PICKAXES) && pick.isDamageableItem(); }

    private void wearPick() {
        var pick = items.getFirst();
        int damage = pick.getDamageValue() + 1;
        if (damage >= pick.getMaxDamage()) items.set(0, ItemStack.EMPTY);
        else pick.setDamageValue(damage);
    }

    private void stopMining(ProductionMenu.Status reason) {
        status = reason;
        stopMiningClock();
    }

    private void stopMiningClock() {
        progress = 0;
        if (workSince >= 0) { workSince = -1; setChanged(); }
    }

    private void tickCondenser(int elapsed) {
        nextGrade = outputGrade(mode, items.get(1).getCount(), items.get(2).getCount(), target, mediumTarget);
        progress = percent(credit, ProductionRules.cost(nextGrade));
        if (!enabled) { status = ProductionMenu.Status.STOPPED; return; }
        var product = new ItemStack(BuiltInRegistries.ITEM.get(ProductionRules.outputId(nextGrade)));
        if (!hasRoom(nextGrade + 1, product, product.getMaxStackSize())) { status = ProductionMenu.Status.FULL; return; }

        credit = Math.min(maxCredit(), credit + (long) ProductionRules.get().rate(kind) * elapsed);
        // Typically one batch at most. Stored high-grade credit can be used after a lower grade is selected.
        int remaining = kind.gradeCount() * ProductionRules.STACK_TARGET;
        while (remaining-- > 0 && credit >= ProductionRules.cost(nextGrade)
                && hasRoom(nextGrade + 1, product, product.getMaxStackSize())) {
            putOne(nextGrade + 1, product);
            credit -= ProductionRules.cost(nextGrade);
            nextGrade = outputGrade(mode, items.get(1).getCount(), items.get(2).getCount(), target, mediumTarget);
            product = new ItemStack(BuiltInRegistries.ITEM.get(ProductionRules.outputId(nextGrade)));
        }
        progress = percent(credit, ProductionRules.cost(nextGrade));
        status = hasRoom(nextGrade + 1, product, product.getMaxStackSize()) ? ProductionMenu.Status.RUNNING : ProductionMenu.Status.FULL;
        setChanged();
    }

    static int outputGrade(int mode, int lowCount, int midCount, int lowTarget, int midTarget) {
        if (mode != 3) return mode;
        if (lowCount < lowTarget) return 0;
        return midCount < midTarget ? 1 : 2;
    }

    private static int percent(long amount, long total) { return (int) Math.min(100, amount * 100 / total); }

    private long maxCredit() {
        return ProductionRules.cost(kind.gradeCount() - 1) + (long) ProductionRules.get().rate(kind) * ProductionRules.get().pulse();
    }

    private boolean hasRoom(int slot, ItemStack product, int limit) {
        var existing = items.get(slot);
        return !product.isEmpty() && (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, product)
                && existing.getCount() < Math.min(limit, existing.getMaxStackSize()));
    }

    private void putOne(int slot, ItemStack item) {
        var stack = items.get(slot);
        if (stack.isEmpty()) items.set(slot, item.copy());
        else stack.grow(1);
    }

    boolean action(int button) {
        int change = switch (button) {
            case ProductionMenu.TARGET_DOWN, ProductionMenu.RESERVE_DOWN, ProductionMenu.MEDIUM_DOWN -> -1;
            default -> 1;
        };
        switch (button) {
            case ProductionMenu.TOGGLE -> {
                if (!enabled && !formation().formed()) return false;
                enabled = !enabled;
            }
            case ProductionMenu.CHANGE_MODE -> {
                if (!kind.hasModes()) return false;
                mode = (mode + 1) % (kind.lastMode + 1);
            }
            case ProductionMenu.TARGET_DOWN, ProductionMenu.TARGET_UP -> {
                if (!kind.mining() && kind != ProductionKind.HIGH) return false;
                target = Math.clamp(target + change * 8, kind.mining() ? 1 : 0, ProductionRules.STACK_TARGET);
            }
            case ProductionMenu.RESERVE_DOWN, ProductionMenu.RESERVE_UP -> {
                if (!kind.mining()) return false;
                reserveFloor = Math.clamp(reserveFloor + change, 0, ProductionRules.get().mining().capacity() - 1);
            }
            case ProductionMenu.MEDIUM_DOWN, ProductionMenu.MEDIUM_UP -> {
                if (kind != ProductionKind.HIGH) return false;
                mediumTarget = Math.clamp(mediumTarget + change, 0, ProductionRules.STACK_TARGET);
            }
            default -> { return false; }
        }
        if (kind.mining()) stopMiningClock();
        setChanged();
        return true;
    }

    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ProductionMenu(id, inventory, this, data);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        var result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); setChanged(); }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() { items.clear(); setChanged(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putBoolean("Enabled", enabled);
        tag.putInt("Mode", mode);
        tag.putInt("Target", target);
        if (kind.mining()) {
            tag.putInt("ReserveFloor", reserveFloor);
            tag.putLong("WorkSince", workSince);
        } else {
            tag.putInt("MediumTarget", mediumTarget);
            tag.putLong("Credit", credit);
        }
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        enabled = tag.getBoolean("Enabled");
        if (tag.contains("Mode", Tag.TAG_INT)) mode = Math.clamp(tag.getInt("Mode"), 0, kind.lastMode);
        if (tag.contains("Target", Tag.TAG_INT)) target = Math.clamp(tag.getInt("Target"), kind.mining() ? 1 : 0, ProductionRules.STACK_TARGET);
        if (kind.mining()) {
            if (tag.contains("ReserveFloor", Tag.TAG_INT)) reserveFloor = Math.clamp(tag.getInt("ReserveFloor"), 0, ProductionRules.get().mining().capacity() - 1);
            workSince = enabled && tag.contains("WorkSince", Tag.TAG_LONG) ? Math.max(-1, tag.getLong("WorkSince")) : -1;
        } else {
            if (tag.contains("MediumTarget", Tag.TAG_INT)) mediumTarget = Math.clamp(tag.getInt("MediumTarget"), 0, ProductionRules.STACK_TARGET);
            credit = Math.clamp(tag.getLong("Credit"), 0, maxCredit());
        }
    }
}
