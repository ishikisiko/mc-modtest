package com.example.myvillage.block.entity;

import com.example.myvillage.block.ForgingAnvilBlock;
import com.example.myvillage.block.ModBlockEntities;
import com.example.myvillage.forging.WorkshopDisplay;
import com.example.myvillage.forging.WorkshopMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 实体工位的短期占用与只读表现。真实物品仍归菜单，关闭时返还玩家；
 * 台面信息从不存入存档，重启不会留下复制品或永久占用。
 */
public final class ForgingAnvilBlockEntity extends BlockEntity {
    private WorkshopMenu operator;
    private ServerPlayer player;
    private WorkshopDisplay display = WorkshopDisplay.EMPTY;

    public ForgingAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FORGING_ANVIL.get(), pos, state);
    }
    public WorkshopDisplay display() { return display; }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide() && getBlockState().getValue(ForgingAnvilBlock.LIT))
            level.setBlock(worldPosition,getBlockState().setValue(ForgingAnvilBlock.LIT,false),Block.UPDATE_CLIENTS);
    }
    public boolean available() {
        if (operator != null && (player == null || player.containerMenu != operator || !player.isAlive())) {
            operator = null;
            player = null;
            update(WorkshopDisplay.EMPTY);
        }
        return operator == null;
    }
    public void bind(WorkshopMenu menu, ServerPlayer owner) {
        if (!available()) throw new IllegalStateException("锻器台已被占用");
        operator = menu;
        player = owner;
    }
    public boolean owns(WorkshopMenu menu) { return operator == menu; }
    public void publish(WorkshopMenu menu) {
        if (operator == menu && level != null) update(WorkshopDisplay.from(menu, level.getGameTime()));
    }
    public void release(WorkshopMenu menu) {
        if (operator != menu) return;
        operator = null;
        player = null;
        update(WorkshopDisplay.EMPTY);
    }
    private void update(WorkshopDisplay next) {
        if (level == null || level.isClientSide() || display.equals(next)) return;
        display = next;
        var state = getBlockState();
        if (state.getValue(ForgingAnvilBlock.LIT) != next.hot())
            level.setBlock(worldPosition, state.setValue(ForgingAnvilBlock.LIT, next.hot()), Block.UPDATE_CLIENTS);
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putString("Kind", display.kind()); tag.putInt("Stage", display.stage());
        tag.putBoolean("Hot", display.hot()); tag.putLong("StrikeStart", display.strikeStart());
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) { readDisplay(tag); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        readDisplay(packet.getTag());
    }
    private void readDisplay(CompoundTag tag) {
        display = new WorkshopDisplay(tag.getString("Kind"), Math.clamp(tag.getInt("Stage"), 0, 5),
                tag.getBoolean("Hot"), tag.contains("StrikeStart") ? tag.getLong("StrikeStart") : -1);
    }
}
