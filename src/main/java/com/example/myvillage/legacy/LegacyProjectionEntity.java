package com.example.myvillage.legacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A view of server-owned spell state; never an inventory or a persisted second weapon. */
public final class LegacyProjectionEntity extends Entity {
    public static final int SWORD=1,SHADOW=2,FIRE=3,RULER=4,EMBER=5;
    public static final int FIRE_BURST=6,QUAKE=7,FIRE_JET=8,WATER_RAY=9,SHADOW_PATH=10,MIRROR=11,MIRROR_HIT=12,EMBER_COLLECT=13,EARTH_IMPACT=14;
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(LegacyProjectionEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER=SynchedEntityData.defineId(LegacyProjectionEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> STACK=SynchedEntityData.defineId(LegacyProjectionEntity.class,EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> RADIUS=SynchedEntityData.defineId(LegacyProjectionEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DX=SynchedEntityData.defineId(LegacyProjectionEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY=SynchedEntityData.defineId(LegacyProjectionEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ=SynchedEntityData.defineId(LegacyProjectionEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(LegacyProjectionEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TOTAL=SynchedEntityData.defineId(LegacyProjectionEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(LegacyProjectionEntity.class,EntityDataSerializers.LONG);
    private int remaining;
    public LegacyProjectionEntity(EntityType<? extends LegacyProjectionEntity> type,Level level) {super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND,0);builder.define(OWNER,-1);builder.define(STACK,ItemStack.EMPTY);
        builder.define(RADIUS,0F);builder.define(DX,0F);builder.define(DY,0F);builder.define(DZ,0F);
        builder.define(PHASE,0);builder.define(TOTAL,1);builder.define(START,0L);
    }
    public void initialize(int kind,Entity owner,ItemStack stack,int ticks) {
        if(level().isClientSide||ticks<1||ticks>600)throw new IllegalArgumentException("server projection duration");
        entityData.set(KIND,kind);entityData.set(OWNER,owner==null?-1:owner.getId());entityData.set(STACK,stack.copyWithCount(1));
        remaining=ticks;entityData.set(TOTAL,ticks);entityData.set(START,level().getGameTime());
    }
    public void visual(Vec3 vector,double radius) {
        if(level().isClientSide||!Double.isFinite(vector.lengthSqr())||vector.length()>LegacyRules.get().visual("effect_range")||!Double.isFinite(radius)||radius<0||radius>16)throw new IllegalArgumentException("server projection shape");
        entityData.set(DX,(float)vector.x);entityData.set(DY,(float)vector.y);entityData.set(DZ,(float)vector.z);entityData.set(RADIUS,(float)radius);
    }
    public Vec3 vector() {return new Vec3(entityData.get(DX),entityData.get(DY),entityData.get(DZ));}
    public float radius() {return entityData.get(RADIUS);}
    public void phase(int phase) {if(!level().isClientSide)entityData.set(PHASE,phase);}
    public int phase() {return entityData.get(PHASE);}
    public int duration() {return entityData.get(TOTAL);}
    public float age(float partial) {return (float)Math.clamp(level().getGameTime()-entityData.get(START)+partial,0,duration());}
    public int kind() {return entityData.get(KIND);}
    public int ownerId() {return entityData.get(OWNER);}
    public ItemStack visualStack() {return entityData.get(STACK);}
    @Override public void tick() {super.tick();if(!level().isClientSide && --remaining<=0)discard();}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {remaining=0;}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean isPickable() {return false;}
    @Override public boolean canBeCollidedWith() {return false;}
}
