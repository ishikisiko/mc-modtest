package com.example.myvillage.entity.beast;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 妖狼: the first beast. Moves and attributes come from {@code data/myvillage/beast/demon_wolf.json}. */
public final class DemonWolfEntity extends BeastEntity {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "demon_wolf");
    static final int EXPERIENCE_REWARD = 10;
    /** Lower and heavier than a vanilla wolf. */
    static final float VOICE_PITCH = 0.7F;

    public DemonWolfEntity(EntityType<? extends DemonWolfEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = EXPERIENCE_REWARD;
    }

    @Override
    protected ResourceLocation beastId() {
        return ID;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WOLF_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOLF_STEP, 0.25F, VOICE_PITCH);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * VOICE_PITCH;
    }

    @Override
    protected float getSoundVolume() {
        return 1.2F;
    }

    @Override
    protected void playWindupSound(BeastMoveDefinition move) {
        playSound(SoundEvents.WOLF_GROWL, 1.6F, getVoicePitch() * 0.85F);
    }
}
