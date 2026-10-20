package com.example.myvillage.block.entity;

import com.example.myvillage.block.ModBlockEntities;
import com.example.myvillage.block.QuenchingTroughBlock;
import com.example.myvillage.forging.WorkshopMenu;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 占用只活到本次工序结束，不保存或复制真实器坯。 */
public final class QuenchingTroughBlockEntity extends BlockEntity {
    private WorkshopMenu operator;
    private String kind="";
    private long started=-1;
    public QuenchingTroughBlockEntity(BlockPos pos,BlockState state) { super(ModBlockEntities.QUENCHING_TROUGH.get(),pos,state); }
    public String kind() { return kind; }
    public long started() { return started; }
    public boolean filled() { return !isRemoved()&&getBlockState().getValue(QuenchingTroughBlock.FILLED); }
    public boolean available() {
        if(operator!=null&&!operator.quenchingAt(this)) clear();
        return operator==null;
    }
    public boolean owns(WorkshopMenu menu) { return operator==menu&&!isRemoved(); }
    public boolean reserve(WorkshopMenu menu) {
        if(level==null||level.isClientSide()||!available()||!filled()) return false;
        operator=menu;kind=menu.craft().kind();started=level.getGameTime();sync();return true;
    }
    public boolean consume(WorkshopMenu menu) {
        if(!owns(menu)||!filled()||level==null) return false;
        level.setBlock(worldPosition,getBlockState().setValue(QuenchingTroughBlock.FILLED,false),Block.UPDATE_ALL);
        return true;
    }
    public void release(WorkshopMenu menu) { if(operator==menu) clear(); }
    private void clear() { operator=null;kind="";started=-1;sync(); }
    private void sync() {
        if(level!=null&&!level.isClientSide()) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag=new CompoundTag();tag.putString("Kind",kind);tag.putLong("Started",started);return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries) { read(tag); }
    @Override public void onDataPacket(Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries) { read(packet.getTag()); }
    private void read(CompoundTag tag) { kind=tag.getString("Kind");started=tag.contains("Started")?tag.getLong("Started"):-1; }
}
