package com.example.myvillage.entity.beast;

import com.example.myvillage.entity.beast.ProvincialFauna.Behavior;
import com.example.myvillage.entity.beast.ProvincialFauna.Rule;
import com.example.myvillage.wilderness.BeastBirth;
import com.example.myvillage.wilderness.WildernessRules;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Natural fauna share the beast data and renderer, with separate movement for land, water and air. */
public final class ProvincialAnimal extends BeastEntity {
    private int glideTicks;

    public ProvincialAnimal(EntityType<? extends ProvincialAnimal> type, Level level) {
        super(type, level);
        if (rule().behavior() == Behavior.SWIM) {
            moveControl = new SmoothSwimmingMoveControl(this, 60, 12, .82F, .12F, true);
        } else if (rule().behavior() == Behavior.FLY) {
            moveControl = new FlyingMoveControl(this, 25, true);
            setNoGravity(true);
        }
    }

    @Override protected ResourceLocation beastId() { return EntityType.getKey(getType()); }
    private Rule rule() { return ProvincialFauna.require(beastId().getPath()); }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return true; }
    @Override public float getWalkTargetValue(BlockPos pos, LevelReader world) { return 0; }

    @Override protected PathNavigation createNavigation(Level level) {
        return switch (rule().behavior()) {
            case SWIM -> new WaterBoundPathNavigation(this, level);
            case AMPHIBIOUS, HOP -> new AmphibiousPathNavigation(this, level);
            case FLY -> new FlyingPathNavigation(this, level);
            default -> super.createNavigation(level);
        };
    }

    @Override protected void registerGoals() {
        var behavior = rule().behavior();
        switch (behavior) {
            case SWIM -> goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1, 14));
            case FLY -> goalSelector.addGoal(4, new WaterAvoidingRandomFlyingGoal(this, .92));
            case AMPHIBIOUS, HOP -> {
                goalSelector.addGoal(2, new PanicGoal(this, 1.23));
                goalSelector.addGoal(5, new RandomStrollGoal(this, .82, 35));
            }
            default -> {
                goalSelector.addGoal(0, new FloatGoal(this));
                goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, .80));
                if (behavior == Behavior.RETALIATE) {
                    goalSelector.addGoal(2, new BeastAttackGoal(this));
                    targetSelector.addGoal(1, new HurtByTargetGoal(this));
                } else if (behavior == Behavior.FLEE || behavior == Behavior.GLIDE || behavior == Behavior.CLIMB) {
                    goalSelector.addGoal(1, new PanicGoal(this, 1.25));
                    goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 5, .95, 1.28));
                }
            }
        }
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                 MobSpawnType reason, SpawnGroupData group) {
        if (natural(reason) && wildernessBirth().isEmpty()) {
            var region = rule().region();
            var realm = WildernessRules.bundled().region(region).orElseThrow().beast()
                    .sample(level.getLevel().getSeed(), region, random.nextLong());
            assignWildernessBirth(new BeastBirth(realm, region, getBlockX(), getBlockY(), getBlockZ(), true));
        }
        return super.finalizeSpawn(level, difficulty, reason, group);
    }

    @Override public boolean checkSpawnObstruction(LevelReader level) {
        return rule().behavior() == Behavior.SWIM ? level.isUnobstructed(this) : super.checkSpawnObstruction(level);
    }

    @Override public void travel(Vec3 input) {
        if (isEffectiveAi() && isInWater() && rule().behavior() == Behavior.SWIM) {
            moveRelative(.035F, input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(.88));
        } else super.travel(input);
    }

    @Override public boolean onClimbable() {
        if (rule().behavior() == Behavior.CLIMB && horizontalCollision) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (level().getBlockState(blockPosition().relative(side)).is(BlockTags.LOGS)) return true;
            }
        }
        return super.onClimbable();
    }

    @Override public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        var behavior = rule().behavior();
        return behavior != Behavior.FLY && behavior != Behavior.GLIDE && super.causeFallDamage(distance, multiplier, source);
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        var behavior = rule().behavior();
        if (behavior == Behavior.GLIDE) {
            Vec3 motion = getDeltaMovement();
            if (!onGround() && motion.y < -.07) setDeltaMovement(motion.x, -.07, motion.z);
            if (glideTicks > 0) glideTicks--;
            else if (onGround() && tickCount % 55 == 0) tryGlide();
        } else if (behavior == Behavior.HOP && onGround() && !getNavigation().isDone() && tickCount % 11 == 0) {
            jumpControl.jump();
        }
    }

    private void tryGlide() {
        BlockPos start = blockPosition();
        int first = random.nextInt(4);
        for (int i = 0; i < 4; i++) {
            Direction side = Direction.from2DDataValue((first + i) % 4);
            var lip = start.relative(side, 2);
            var end = start.relative(side, 9);
            if (!level().hasChunkAt(end) || !level().hasChunkAt(lip)) continue;
            int landing = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, end.getX(), end.getZ());
            if (landing > start.getY() - 3 || landing < start.getY() - 12 || !level().isEmptyBlock(lip.below(2))) continue;
            if (!level().getBiome(end.atY(landing)).is(rule().biome())) continue;
            getNavigation().stop();
            setDeltaMovement(side.getStepX() * .43, .20, side.getStepZ() * .43);
            glideTicks = 33;
            return;
        }
    }

    public static boolean canSpawn(EntityType<ProvincialAnimal> type, ServerLevelAccessor level,
                                   MobSpawnType reason, BlockPos pos, RandomSource random) {
        return !natural(reason) || eligible(ProvincialFauna.require(EntityType.getKey(type).getPath()), level, pos);
    }

    public static boolean eligible(Rule rule, ServerLevelAccessor level, BlockPos pos) {
        var server = level.getLevel();
        if (!server.dimension().equals(Level.OVERWORLD) || !level.getBiome(pos).is(rule.biome())) return false;
        if (WorldPlanRuntime.generator(server).filter(g -> g.rules().provincialBiomes != null).isEmpty()) return false;
        long time = Math.floorMod(level.dayTime(), 24000);
        boolean active = switch (rule.activity()) {
            case DAY -> time < 12500;
            case DUSK -> time >= 9000 && time <= 17800;
            case TWILIGHT -> time < 3600 || time >= 9300;
            case RAIN_NIGHT -> server.isRaining() || time >= 11500;
            case ANY -> true;
        };
        if (!active) return false;
        var here = level.getBlockState(pos);
        var below = level.getBlockState(pos.below());
        boolean onPalette = rule.palette().contains(BuiltInRegistries.BLOCK.getKey(below.getBlock()));
        if (rule.habitat() == ProvincialFauna.Habitat.REEF) {
            return here.getFluidState().is(FluidTags.WATER) && level.getFluidState(pos.above()).is(FluidTags.WATER)
                    && level.getFluidState(pos.below()).is(FluidTags.WATER) && nearReef(level, pos);
        }
        if (!here.getFluidState().isEmpty() || !here.getCollisionShape(level, pos).isEmpty()) return false;
        if (rule.habitat() == ProvincialFauna.Habitat.RIFT) {
            return (onPalette || level.getBlockState(pos.above()).isFaceSturdy(level, pos.above(), Direction.DOWN))
                    && nearWall(level, pos);
        }
        boolean iceBank = rule.habitat() == ProvincialFauna.Habitat.BANK && (below.is(Blocks.PACKED_ICE) || below.is(Blocks.ICE));
        if (!onPalette && !iceBank)
            return false;
        return switch (rule.habitat()) {
            case BANK -> nearLiquid(level, pos, false);
            case CLEARING -> level.getRawBrightness(pos, 0) >= 9;
            case LEDGE -> nearWall(level, pos) || nearDrop(level, pos);
            case ROOTS -> nearLog(level, pos);
            case HOT_EDGE -> nearLiquid(level, pos, true);
            case SALT_CRUST -> below.is(Blocks.WHITE_TERRACOTTA) || nearLiquid(level, pos, false);
            case RIFT, REEF -> false;
        };
    }

    private static boolean nearLiquid(ServerLevelAccessor level, BlockPos pos, boolean lava) {
        var tag = lava ? FluidTags.LAVA : FluidTags.WATER;
        var cursor = new BlockPos.MutableBlockPos();
        for (int z = -5; z <= 5; z++) for (int x = -5; x <= 5; x++) {
            if (x * x + z * z > 27 || !level.hasChunk((pos.getX() + x) >> 4, (pos.getZ() + z) >> 4)) continue;
            for (int dy = 0; dy >= -3; dy--) if (level.getFluidState(cursor.setWithOffset(pos, x, dy, z)).is(tag)) return true;
        }
        return false;
    }

    private static boolean nearReef(ServerLevelAccessor level, BlockPos pos) {
        for (int depth = 2; depth <= 7; depth++) {
            var below = level.getBlockState(pos.below(depth));
            if (below.is(BlockTags.CORAL_BLOCKS)) return true;
            if (below.is(Blocks.PRISMARINE)) return true;
        }
        return false;
    }

    private static boolean nearLog(ServerLevelAccessor level, BlockPos pos) {
        for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) {
            if (!level.hasChunk((pos.getX() + dx) >> 4, (pos.getZ() + dz) >> 4)) continue;
            for (int dy = 0; dy <= 3; dy++) {
                var state = level.getBlockState(pos.offset(dx, dy, dz));
                if (state.is(BlockTags.LOGS) || state.is(Blocks.MANGROVE_ROOTS)) return true;
            }
        }
        return false;
    }

    private static boolean nearWall(ServerLevelAccessor level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            var ledge = pos.relative(side, 2).above();
            if (level.hasChunk(ledge.getX() >> 4, ledge.getZ() >> 4)
                    && level.getBlockState(ledge).isFaceSturdy(level, ledge, side.getOpposite())) return true;
        }
        return false;
    }

    private static boolean nearDrop(ServerLevelAccessor level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            var over = pos.relative(side, 3).below(3);
            if (level.hasChunk(over.getX() >> 4, over.getZ() >> 4) && level.isEmptyBlock(over)) return true;
        }
        return false;
    }

    private static boolean natural(MobSpawnType reason) {
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
    }
}
