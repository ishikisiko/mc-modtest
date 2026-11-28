package com.example.myvillage.entity.beast;

import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.FluidTags;

public final class FieldAmbientBeast extends BeastEntity {
    public static final String CRAB = "tide_mud_crab";
    public static final String SCORPION = "hook_jaw_scorpion";

    public FieldAmbientBeast(EntityType<? extends FieldAmbientBeast> type, Level level) {
        super(type, level);
        xpReward = FieldEncounterRules.bundled().beast(beastId().getPath()).body().experience();
    }

    @Override protected ResourceLocation beastId() { return EntityType.getKey(getType()); }

    @Override protected void registerGoals() {
        if (!beastId().getPath().equals(CRAB)) {
            super.registerGoals();
            return;
        }
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new BeastAttackGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override public boolean removeWhenFarAway(double distance) { return true; }

    @Override public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        // The placement predicate owns light and ground checks for these two beasts.
        return 0;
    }

    @Override protected void playWindupSound(BeastMoveDefinition move) {
        playSound(move.animation().equals("sting") ? SoundEvents.SPIDER_AMBIENT : SoundEvents.ARMADILLO_HURT, .72F, 1.05F);
    }

    public static boolean checkSpawn(EntityType<FieldAmbientBeast> type, ServerLevelAccessor level,
                                     MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) return true;
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getFluidState(pos).isEmpty()) return false;
        String id = EntityType.getKey(type).getPath();
        var generator = WorldPlanRuntime.generator(level.getLevel()).orElse(null);
        if (generator == null || generator.rules().fieldBiomes == null) return false;
        var rules = generator.rules().fieldBiomes;
        var domain = id.equals(CRAB) ? rules.tide.domain() : rules.wind.domain();
        if (!level.getBiome(pos).is(ResourceLocation.parse(domain.biome()))) return false;
        BlockState floor = level.getBlockState(pos.below());
        var rule = FieldEncounterRules.bundled().beast(id);
        if (id.equals(CRAB)) {
            return (floor.is(Blocks.MUD) || floor.is(Blocks.GRAVEL) || floor.is(Blocks.SAND))
                    && nearWater(level, pos, rule.nearWater(), rule.nearWaterDepth());
        }
        return (floor.is(Blocks.RED_SAND) || floor.is(Blocks.RED_SANDSTONE) || floor.is(Blocks.TERRACOTTA)
                || floor.is(Blocks.ORANGE_TERRACOTTA)) && level.getMaxLocalRawBrightness(pos) <= rule.maximumLight();
    }

    private static boolean nearWater(ServerLevelAccessor level, BlockPos pos, int radius, int depth) {
        var cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz > radius * radius) continue;
            for (int dy = -depth; dy <= 0; dy++) {
                cursor.setWithOffset(pos, dx, dy, dz);
                if (level.hasChunk(cursor.getX() >> 4, cursor.getZ() >> 4) && level.getFluidState(cursor).is(FluidTags.WATER)) return true;
            }
        }
        return false;
    }
}
