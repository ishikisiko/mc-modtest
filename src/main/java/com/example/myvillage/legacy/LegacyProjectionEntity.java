package com.example.myvillage.legacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A view of server-owned spell state; never an inventory or a persisted second weapon. */
public final class LegacyProjectionEntity extends Entity {
    public static final int SWORD=1,SHADOW=2,FIRE=3,RULER=4,EMBER=5;
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(LegacyProjectionEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER=SynchedEntityData.defineId(LegacyProjectionEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> STACK=SynchedEntityData.defineId(LegacyProjectionEntity.class,EntityDataSerializers.ITEM_STACK);
    private int remaining;
    public LegacyProjectionEntity(EntityType<? extends LegacyProjectionEntity> type,Level level) {super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {builder.define(KIND,0);builder.define(OWNER,-1);builder.define(STACK,ItemStack.EMPTY);}
    public void initialize(int kind,Entity owner,ItemStack stack,int ticks) {entityData.set(KIND,kind);entityData.set(OWNER,owner==null?-1:owner.getId());entityData.set(STACK,stack.copyWithCount(1));remaining=ticks;}
    public int kind() {return entityData.get(KIND);}
    public int ownerId() {return entityData.get(OWNER);}
    public ItemStack visualStack() {return entityData.get(STACK);}
    @Override public void tick() {super.tick();if(!level().isClientSide && --remaining<=0)discard();}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {remaining=0;}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean isPickable() {return false;}
    @Override public boolean canBeCollidedWith() {return false;}
}
