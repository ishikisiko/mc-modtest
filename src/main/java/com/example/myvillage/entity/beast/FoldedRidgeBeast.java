package com.example.myvillage.entity.beast;

import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.example.myvillage.worldplan.wonder.FoldedRidge;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.Optional;

public abstract class FoldedRidgeBeast extends BeastEntity {
    FoldedRidgeBeast(EntityType<? extends BeastEntity> type, Level level) {
        super(type, level);
    }

    static Optional<FoldedRidge> ridge(ServerLevelAccessor level) {
        return WorldPlanRuntime.generator(level.getLevel()).flatMap(g -> g.foldedRidge());
    }

    static boolean inInterlayer(FoldedRidge ridge, BlockPos pos) {
        return ridge.interlayer(pos.getX(), pos.getY(), pos.getZ());
    }

    static boolean natural(MobSpawnType reason) {
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
    }

    static boolean allowsSpawn(EntityType<?> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        return level.getDifficulty() != Difficulty.PEACEFUL
                && (!natural(reason) || ridge(level).filter(site -> inInterlayer(site, pos)).isPresent());
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        // PathfinderMob also checks this at spawn time. Monster's value would reintroduce a light gate.
        return 0;
    }

    @Override
    protected boolean isVictim(LivingEntity candidate, LivingEntity target) {
        return super.isVictim(candidate, target) && hasLineOfSight(candidate);
    }
}
