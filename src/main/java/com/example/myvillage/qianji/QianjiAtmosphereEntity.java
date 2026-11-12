package com.example.myvillage.qianji;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 仅同步演示相位偏移；自然循环使用世界时钟，离开和重进不会创建计时任务。 */
public final class QianjiAtmosphereEntity extends BlockEntity {
    private long cycleOffset;
    public QianjiAtmosphereEntity(BlockPos p,BlockState s){super(QianjiContent.ENTITY.get(),p,s);}
    public long cycleOffset(){return cycleOffset;}
    public void setCycleOffset(long value){if(level==null||level.isClientSide)return;cycleOffset=Math.floorMod(value,QianjiAtmosphereRules.get().cycleTicks());setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    @Override protected void saveAdditional(CompoundTag n,HolderLookup.Provider p){super.saveAdditional(n,p);n.putLong("CycleOffset",cycleOffset);}
    @Override protected void loadAdditional(CompoundTag n,HolderLookup.Provider p){super.loadAdditional(n,p);cycleOffset=n.getLong("CycleOffset");}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider p){return saveWithoutMetadata(p);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
