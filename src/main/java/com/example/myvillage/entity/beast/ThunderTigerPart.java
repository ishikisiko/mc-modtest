package com.example.myvillage.entity.beast;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.entity.PartEntity;

/** Real hurt geometry: the parent owns health, and only the head can receive horn damage. */
public final class ThunderTigerPart extends PartEntity<ThunderTigerEntity> {
    private final boolean head;
    private final EntityDimensions dimensions;
    public ThunderTigerPart(ThunderTigerEntity parent, boolean head, float width, float height) {
        super(parent);
        this.head = head;
        this.dimensions = EntityDimensions.scalable(width, height);
        refreshDimensions();
    }
    public boolean isHead() { return head; }
    @Override public EntityDimensions getDimensions(Pose pose) { return dimensions; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return getParent().isAlive(); }
    @Override public boolean isAttackable() { return getParent().isAttackable(); }
    @Override public boolean is(Entity other) { return this == other || getParent() == other; }
    @Override public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().hurtPart(this, source, amount);
    }
}
