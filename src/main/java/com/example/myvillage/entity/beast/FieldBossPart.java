package com.example.myvillage.entity.beast;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

final class FieldBossPart extends PartEntity<FieldEncounterBoss> {
    private final EntityDimensions dimensions;
    private final double along, across, height;
    private final boolean gill;

    FieldBossPart(FieldEncounterBoss parent, float width, float height, double along, double across,
                  double baseY, boolean gill) {
        super(parent);
        this.dimensions = EntityDimensions.scalable(width, height);
        this.along = along;
        this.across = across;
        this.height = baseY;
        this.gill = gill;
        refreshDimensions();
    }

    void place(Vec3 origin, Vec3 forward) {
        setPos(origin.add(forward.x * along + forward.z * across, height, forward.z * along - forward.x * across));
    }

    @Override public EntityDimensions getDimensions(Pose pose) { return dimensions; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return getParent().isAlive(); }
    @Override public boolean isAttackable() { return getParent().isAttackable(); }
    @Override public boolean is(Entity other) { return other == this || other == getParent(); }
    @Override public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().hurtAt(source, amount, gill);
    }
}
