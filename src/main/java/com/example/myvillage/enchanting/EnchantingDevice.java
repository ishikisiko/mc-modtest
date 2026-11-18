package com.example.myvillage.enchanting;

import static com.example.myvillage.enchanting.EnchantingRules.RULES;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class EnchantingDevice extends BlockEntity {
    private ItemStack stone = ItemStack.EMPTY;
    private CompoundTag ledger = new CompoundTag();
    private boolean formed;
    private int pose;
    private long phaseEnd;

    EnchantingDevice(BlockPos pos, BlockState state) {
        super(EnchantingContent.DEVICE.get(), pos, state);
    }

    public Direction front() { return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING); }
    public boolean library() { return getBlockState().is(EnchantingContent.PORT.get()); }
    public boolean formed() { return formed; }
    public int pose() { return pose; }
    int stoneStrength() { return EnchantingContent.stoneStrength(stone); }

    void exchangeStone(ServerPlayer player) {
        var hand = player.getMainHandItem();
        if (!hand.isEmpty() && EnchantingContent.stoneStrength(hand) == 0) return;
        var old = stone;
        stone = hand.isEmpty() ? ItemStack.EMPTY : hand.copyWithCount(1);
        if (!hand.isEmpty()) hand.shrink(1);
        if (!old.isEmpty()) player.getInventory().placeItemBackInInventory(old);
        setChanged();
    }

    ItemStack removeStone() {
        var old = stone;
        stone = ItemStack.EMPTY;
        return old;
    }

    CompoundTag ledgerCopy() { return ledger.copy(); }
    void replaceLedger(CompoundTag next) { ledger = next; setChanged(); }
    static int points(CompoundTag ledger, String key) { return ledger.getCompound(key).getInt("Points"); }
    static int recorded(CompoundTag ledger, String key) { return ledger.getCompound(key).getInt("Level"); }

    static boolean adjust(CompoundTag ledger, String key, int delta, int seenLevel) {
        int points = points(ledger, key) + delta;
        if (points < 0 || points > RULES.point_capacity()) return false;
        var row = new CompoundTag();
        row.putInt("Points", points);
        row.putInt("Level", Math.max(recorded(ledger, key), seenLevel));
        ledger.put(key, row);
        return true;
    }

    boolean verifyStructure() {
        if (level == null) return false;
        var origin = library() ? EnchantingStructure.libraryCenter(worldPosition, front()) : worldPosition;
        return EnchantingStructure.matches(level, origin, front(), library());
    }

    void tick(ServerLevel server) {
        long time = server.getGameTime();
        if (Math.floorMod(time + worldPosition.asLong(), 20) == 0) {
            boolean ready = verifyStructure();
            if (ready != formed) {
                formed = ready;
                if (!formed) pose = 0;
                sync();
            }
        }
        if (pose != 0 && time >= phaseEnd) {
            pose = 0;
            sync();
        }
    }

    void animate(int target) {
        if (!(level instanceof ServerLevel server)) return;
        formed = verifyStructure();
        if (!formed) return;
        pose = target;
        phaseEnd = server.getGameTime() + (target == 1 ? 100 : 34);
        sync();
        server.sendParticles(target == 1 ? ParticleTypes.ENCHANT : ParticleTypes.END_ROD,
                worldPosition.getX() + .5, worldPosition.getY() + 1.45, worldPosition.getZ() + .5, 14, .36, .38, .36, .04);
    }

    private void sync() {
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!stone.isEmpty()) tag.put("Stone", stone.save(registries));
        if (library()) tag.put("Ledger", ledger.copy());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stone = ItemStack.parseOptional(registries, tag.getCompound("Stone"));
        ledger = tag.getCompound("Ledger").copy();
        formed = tag.getBoolean("Formed");
        pose = tag.getInt("Pose");
    }

    @Override protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (library() && !ledger.isEmpty()) {
            var tag = new CompoundTag();
            tag.put("LibraryLedger", ledger.copy());
            components.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override protected void applyImplicitComponents(DataComponentInput components) {
        super.applyImplicitComponents(components);
        if (library()) ledger = components.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getCompound("LibraryLedger");
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putBoolean("Formed", formed);
        tag.putInt("Pose", pose);
        return tag;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
