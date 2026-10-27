package com.example.myvillage.block.entity;

import com.example.myvillage.block.ForgingAnvilBlock;
import com.example.myvillage.block.ModBlockEntities;
import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.forging.*;
import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.item.ModItems;
import com.example.myvillage.spiritstone.SpiritStoneSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent shared workpiece, temporary menu access and server-timed handheld hammer contacts. */
public final class ForgingAnvilBlockEntity extends BlockEntity {
    private final WorkshopStorage storage = new WorkshopStorage();
    private final WorkshopStrikeCycle swing = new WorkshopStrikeCycle();
    private WorkshopMenu operator;
    private ServerPlayer viewer, smith;
    private ItemStack scheduled = ItemStack.EMPTY, hammer = ItemStack.EMPTY;
    private WorkshopDisplay display = WorkshopDisplay.EMPTY;
    private int hits;
    private boolean loading;

    public ForgingAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FORGING_ANVIL.get(), pos, state);
        storage.items.addListener(c -> { if (!loading) { setChanged(); refreshDisplay(); } });
    }
    public WorkshopDisplay display() { return display; }
    public WorkshopStorage storage() { return storage; }
    public SimpleContainer contents() { return storage.items; }
    public ItemStack input() { return contents().getItem(0); }
    public ItemStack output() { return contents().getItem(1); }
    public int heat() { return level == null ? 0 : storage.heat(level.getGameTime()); }
    public void setHeat(int ticks) {
        if (level == null || level.isClientSide()) return;
        storage.heat(ticks, level.getGameTime()); setChanged(); refreshDisplay();
    }
    public int hits() { return hits; }
    public boolean swinging() { return swing.active(); }
    public int strikeReason() { return WorkshopStriking.reason(input(), output(), heat(), storage.direction()); }

    private boolean playerValid(ServerPlayer player) {
        return level != null && !isRemoved() && player.level() == level && player.isAlive() && !player.isSpectator()
                && player.canInteractWithBlock(worldPosition, 0);
    }
    public boolean available() {
        if (operator != null && (viewer == null || viewer.containerMenu != operator || !playerValid(viewer))) {
            operator = null; viewer = null; refreshDisplay();
        }
        return operator == null && !swing.active();
    }
    public void bind(WorkshopMenu menu, ServerPlayer owner) {
        if (!available() || !playerValid(owner)) throw new IllegalStateException("锻器台已被占用或不可达");
        operator = menu; viewer = owner;
    }
    public boolean owns(WorkshopMenu menu) { return operator == menu && !isRemoved(); }
    public void publish(WorkshopMenu menu) {
        if (!owns(menu) || level == null) return;
        var before = new int[]{storage.recipe(), storage.trait(), storage.direction(), storage.treatment()};
        storage.selection(menu.recipeIndex(), menu.traitIndex(), menu.direction(), menu.treatment());
        if (before[0] != storage.recipe() || before[1] != storage.trait() || before[2] != storage.direction() || before[3] != storage.treatment()) setChanged();
        update(WorkshopDisplay.from(menu, level.getGameTime()));
    }
    public void release(WorkshopMenu menu) {
        if (operator != menu) return;
        operator = null; viewer = null; refreshDisplay();
    }
    private boolean refuse(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true); return false;
    }
    public boolean place(ServerPlayer player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !playerValid(player) || player.containerMenu != player.inventoryMenu) return false;
        if (!available()) return refuse(player, "workshop.myvillage.occupied");
        if (!input().isEmpty() || !output().isEmpty()) return refuse(player, "workshop.myvillage.manual.take_first");
        var held = player.getItemInHand(hand);
        // Inspect one item, then transfer it. Creative follows inventory movement, not block placement copying.
        var single = held.copyWithCount(1);
        if (!WorkshopItems.valid(single) && WorkshopEquipment.craft(single).isEmpty()) return false;
        setHeat(0);
        var craft = WorkshopItems.valid(single) ? WorkshopRules.get().craft(single.get(ModDataComponents.WORKPIECE.get()).recipe())
                : WorkshopEquipment.craft(single).orElseThrow();
        int index = WorkshopRules.get().crafts().indexOf(craft);
        int direction = craft.armor() ? 2 : WorkshopStriking.direction(single, storage.direction()) ? storage.direction() : 1;
        storage.selection(index, storage.trait(), direction, craft.armor() ? 2 : storage.treatment());
        contents().setItem(0, held.split(1));
        player.getInventory().setChanged();
        player.displayClientMessage(Component.translatable("workshop.myvillage.manual.placed"), true);
        return true;
    }
    public boolean take(ServerPlayer player) {
        if (!playerValid(player) || player.containerMenu != player.inventoryMenu || !player.getMainHandItem().isEmpty()) return false;
        if (!available()) return refuse(player, "workshop.myvillage.occupied");
        int slot = !output().isEmpty() ? 1 : 0;
        if (contents().getItem(slot).isEmpty()) return refuse(player, "workshop.myvillage.manual.empty");
        var result = contents().removeItemNoUpdate(slot);
        setHeat(0); contents().setChanged();
        player.setItemInHand(InteractionHand.MAIN_HAND, result);
        player.getInventory().setChanged();
        return true;
    }
    public boolean strike(ServerPlayer player) {
        if (!playerValid(player) || player.containerMenu != player.inventoryMenu || !player.getMainHandItem().is(ModItems.FORGING_HAMMER.get())) return false;
        if (swing.active()) return false;
        if (!available()) return refuse(player, "workshop.myvillage.occupied");
        int reason = strikeReason();
        if (reason != 0) return refuse(player, "workshop.myvillage.status." + reason);
        var costs = WorkshopStriking.costs(input());
        if (!costs.isEmpty() && ForgingCosts.plan(SpiritStoneSources.open(player).copies(), costs).isEmpty())
            return refuse(player, "workshop.myvillage.status.4");
        if (!swing.begin(level.getGameTime())) return false;
        smith = player; scheduled = input().copy(); hammer = player.getMainHandItem();
        refreshDisplay();
        return true;
    }
    private boolean contact() {
        int reason = strikeReason();
        if (reason != 0) return refuse(smith, "workshop.myvillage.status." + reason);
        var result = WorkshopStriking.result(input(), storage.direction());
        var costs = WorkshopStriking.costs(input());
        if (!costs.isEmpty()) {
            var source = SpiritStoneSources.open(smith); var plan = ForgingCosts.plan(source.copies(), costs);
            if (plan.isEmpty() || !source.commit(plan.get())) return refuse(smith, "workshop.myvillage.status.4");
        }
        contents().setItem(0, result); scheduled = result.copy(); hits++;
        hammer.hurtAndBreak(WorkshopRules.get().hammer_damage_per_strike(), smith, EquipmentSlot.MAINHAND);
        smith.getInventory().setChanged();
        var rules = WorkshopRules.get(); var feedback = ForgingDingVisuals.get().feedback();
        level.playSound(null, worldPosition, WorkshopSounds.get("strike"), SoundSource.BLOCKS, rules.sound_volume(),
                1 + (level.random.nextFloat() * 2 - 1) * rules.sound_pitch_jitter());
        if (level instanceof ServerLevel server) server.sendParticles(ParticleTypes.CRIT,
                worldPosition.getX() + .5, worldPosition.getY() + 1.05, worldPosition.getZ() + .5,
                feedback.sparkCount(), feedback.sparkSpread(), feedback.sparkSpread(), feedback.sparkSpread(), feedback.sparkSpeed());
        var piece = result.get(ModDataComponents.WORKPIECE.get());
        smith.displayClientMessage(piece != null ? Component.translatable("workshop.myvillage.formed", piece.blows(), rules.forming_strikes())
                : Component.translatable("workshop.myvillage.status.13"), true);
        return true;
    }
    private void cancelSwing() { swing.cancel(); smith = null; scheduled = ItemStack.EMPTY; hammer = ItemStack.EMPTY; }
    public static void serverTick(Level level, BlockPos pos, BlockState state, ForgingAnvilBlockEntity anvil) { anvil.tick(); }
    private void tick() {
        available();
        if (swing.active()) {
            boolean valid = smith != null && playerValid(smith) && smith.containerMenu == smith.inventoryMenu
                    && smith.getMainHandItem() == hammer && hammer.is(ModItems.FORGING_HAMMER.get())
                    && ItemStack.matches(scheduled, input());
            var step = swing.tick(level.getGameTime(), valid);
            if (step == WorkshopStrikeCycle.Step.CONTACT && !contact()) cancelSwing();
            if (step == WorkshopStrikeCycle.Step.CANCELLED || step == WorkshopStrikeCycle.Step.FINISHED) cancelSwing();
        }
        refreshDisplay();
    }
    private void refreshDisplay() {
        if (level == null || level.isClientSide() || loading) return;
        if (operator != null) { update(WorkshopDisplay.from(operator, level.getGameTime())); return; }
        var stack = input().isEmpty() ? output() : input();
        if (stack.isEmpty()) { update(WorkshopDisplay.EMPTY); return; }
        var piece = WorkshopItems.valid(stack) ? stack.get(ModDataComponents.WORKPIECE.get()) : null;
        var craft = piece != null ? WorkshopRules.get().craft(piece.recipe()) : WorkshopEquipment.craft(stack).orElse(null);
        update(craft == null ? WorkshopDisplay.EMPTY : new WorkshopDisplay(craft.kind(), piece == null ? 5 : piece.stage(), heat() > 0, swing.started()));
    }
    private void update(WorkshopDisplay next) {
        if (level == null || level.isClientSide() || isRemoved() || !level.getBlockState(worldPosition).is(ModBlocks.FORGING_ANVIL.get())) return;
        var state = getBlockState();
        if (display.equals(next) && state.hasProperty(ForgingAnvilBlock.LIT) && state.getValue(ForgingAnvilBlock.LIT) == next.hot()) return;
        display = next;
        if (state.hasProperty(ForgingAnvilBlock.LIT) && state.getValue(ForgingAnvilBlock.LIT) != next.hot())
            level.setBlock(worldPosition, state.setValue(ForgingAnvilBlock.LIT, next.hot()), Block.UPDATE_CLIENTS);
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    public void dropContents() {
        cancelSwing(); operator = null; viewer = null;
        loading = true;
        try {
            if (level != null && !level.isClientSide()) Containers.dropContents(level, worldPosition, contents());
            contents().clearContent(); setHeat(0); setChanged(); display = WorkshopDisplay.EMPTY;
        } finally { loading = false; }
    }
    @Override public void setRemoved() { cancelSwing(); operator = null; viewer = null; super.setRemoved(); }
    @Override public void onLoad() { super.onLoad(); refreshDisplay(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); storage.save(tag, registries);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); loading = true;
        try { storage.load(tag, registries); } finally { loading = false; }
        cancelSwing(); operator = null; viewer = null;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putString("Kind", display.kind()); tag.putInt("Stage", display.stage());
        tag.putBoolean("Hot", display.hot()); tag.putLong("StrikeStart", display.strikeStart());
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) { readDisplay(tag); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) { readDisplay(packet.getTag()); }
    private void readDisplay(CompoundTag tag) {
        display = new WorkshopDisplay(tag.getString("Kind"), Math.clamp(tag.getInt("Stage"), 0, 5),
                tag.getBoolean("Hot"), tag.contains("StrikeStart") ? tag.getLong("StrikeStart") : -1);
    }
}
