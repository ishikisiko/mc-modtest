package com.example.myvillage.entity.beast;

import com.example.myvillage.entity.beast.ProvincialHostiles.Behavior;
import com.example.myvillage.entity.beast.ProvincialHostiles.Habitat;
import com.example.myvillage.entity.beast.ProvincialHostiles.Rule;
import com.example.myvillage.wilderness.BeastBirth;
import com.example.myvillage.wilderness.WildernessRules;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Regular provincial beasts run the shared server combat timeline and use habitat-specific navigation. */
public final class ProvincialEnemy extends BeastEntity {
    private long contextValidUntil = Long.MIN_VALUE;
    private boolean nativeTerrainInReach;

    public ProvincialEnemy(EntityType<? extends ProvincialEnemy> type, Level level) {
        super(type, level);
        switch (rule().behavior()) {
            case WING -> {
                moveControl = new FlyingMoveControl(this, 35, true);
                setNoGravity(true);
            }
            case SWIM -> moveControl = new SmoothSwimmingMoveControl(this, 65, 15, .9F, .15F, true);
            default -> {}
        }
    }

    @Override protected ResourceLocation beastId() { return EntityType.getKey(getType()); }
    private Rule rule() { return ProvincialHostiles.require(beastId()); }
    @Override public float getWalkTargetValue(BlockPos pos, LevelReader world) { return 0; }

    @Override protected PathNavigation createNavigation(Level level) {
        return switch (rule().behavior()) {
            case WING -> new FlyingPathNavigation(this, level);
            case SWIM -> new WaterBoundPathNavigation(this, level);
            case WADER -> new AmphibiousPathNavigation(this, level);
            default -> super.createNavigation(level);
        };
    }

    @Override protected void registerGoals() {
        var movement = rule().behavior();
        switch (movement) {
            case WING -> goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, .86));
            case SWIM -> goalSelector.addGoal(5, new RandomSwimmingGoal(this, .88, 16));
            case WADER -> goalSelector.addGoal(5, new RandomStrollGoal(this, .82, 32));
            default -> {
                if (rule().habitat() != Habitat.LAVA) goalSelector.addGoal(0, new FloatGoal(this));
                goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, .83));
            }
        }
        goalSelector.addGoal(2, new BeastAttackGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  MobSpawnType reason, SpawnGroupData group) {
        if (natural(reason) && wildernessBirth().isEmpty()) {
            String region = rule().region();
            var realm = WildernessRules.bundled().region(region).orElseThrow().beast()
                    .sample(level.getLevel().getSeed(), region, random.nextLong());
            assignWildernessBirth(new BeastBirth(realm, region, getBlockX(), getBlockY(), getBlockZ(), true));
        }
        return super.finalizeSpawn(level, difficulty, reason, group);
    }

    @Override public boolean checkSpawnObstruction(LevelReader level) {
        return rule().behavior().aquatic()
                ? level.isUnobstructed(this) && level.noCollision(this)
                : super.checkSpawnObstruction(level);
    }

    @Override protected int decreaseAirSupply(int air) {
        return rule().behavior().aquatic() ? air : super.decreaseAirSupply(air);
    }

    @Override public void travel(Vec3 input) {
        if (rule().behavior() == Behavior.SWIM && isEffectiveAi() && isInWater()) {
            moveRelative(.044F, input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(.89));
        } else super.travel(input);
    }

    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return rule().behavior() != Behavior.WING && super.causeFallDamage(distance, multiplier, source);
    }

    @Override public boolean onClimbable() {
        return super.onClimbable() || rule().behavior() == Behavior.CLIMB && horizontalCollision;
    }

    @Override protected boolean chase(LivingEntity target) {
        var behavior = rule().behavior();
        double speed = definition().chase().speedModifier();
        if (behavior == Behavior.WING) {
            return getNavigation().moveTo(target.getX(), target.getY() + .85, target.getZ(), speed);
        }
        if (behavior == Behavior.SWIM) {
            BlockPos targetPos = target.blockPosition();
            for (int depth = 0; depth <= 2; depth++) {
                BlockPos water = targetPos.below(depth);
                if (level().getFluidState(water).is(FluidTags.WATER))
                    return getNavigation().moveTo(target.getX(), water.getY() + .15, target.getZ(), speed);
            }
            return false;
        }
        if (behavior == Behavior.CLIMB && onClimbable() && target.getY() > getY() + .6
                && distanceToSqr(target) < 20) {
            Vec3 movement = getDeltaMovement();
            setDeltaMovement(movement.x, .19, movement.z);
            return true;
        }
        return super.chase(target);
    }

    @Override protected boolean canLaunchMove() {
        var behavior = rule().behavior();
        return onGround() || behavior == Behavior.WING || behavior.aquatic() && isInWater();
    }

    @Override protected boolean moveEligible(BeastMoveDefinition move, LivingEntity target) {
        double dy = target.getY() - getY();
        boolean flying = rule().behavior() == Behavior.WING;
        if (dy > move.hit().high() + .6
                || dy + target.getBbHeight() < move.hit().low() - (flying ? 1.6 : .30)) return false;
        var context = rule().moveContexts().get(move.id());
        return switch (context) {
            case FREE -> true;
            case NEAR_LAVA, NEAR_CRYSTAL -> nearNativeTerrain();
            case AWAY_LAVA, AWAY_CRYSTAL -> !nearNativeTerrain();
        };
    }

    private boolean nearNativeTerrain() {
        long now = level().getGameTime();
        if (now >= contextValidUntil) {
            var level = level();
            var pos = blockPosition();
            nativeTerrainInReach = rule().habitat() == Habitat.LAVA
                    ? nearby(level, pos, 4, -2, 1, state -> state.getFluidState().is(FluidTags.LAVA))
                    : nearby(level, pos, 4, -2, 2, ProvincialEnemy::crystal);
            contextValidUntil = now + 8;
        }
        return nativeTerrainInReach;
    }

    @Override protected void onMoveTick(BeastMoveDefinition move, int tick) {
        if (rule().behavior() != Behavior.WING || tick < move.lunge().tick() || tick > move.activeTicks().last()) return;
        Vec3 motion = getDeltaMovement();
        double descent = Mth.clamp((lockedAimPoint().y + .35 - getY()) * .23, -.36, .17);
        setDeltaMovement(motion.x, descent, motion.z);
        hasImpulse = true;
    }

    @Override protected boolean isVictim(LivingEntity candidate, @Nullable LivingEntity target) {
        return super.isVictim(candidate, target) && getSensing().hasLineOfSight(candidate);
    }

    @Override protected void playWindupSound(BeastMoveDefinition move) {
        var sound = switch (rule().habitat()) {
            case LAVA -> SoundEvents.BLAZE_SHOOT;
            case CRYSTAL -> SoundEvents.AMETHYST_BLOCK_CHIME;
            default -> switch (rule().behavior()) {
                case WING -> SoundEvents.PHANTOM_FLAP;
                case SWIM, WADER -> SoundEvents.GENERIC_SPLASH;
                default -> SoundEvents.PLAYER_ATTACK_SWEEP;
            };
        };
        playSound(sound, .8F, .86F + random.nextFloat() * .22F);
    }

    public static boolean canSpawn(EntityType<ProvincialEnemy> type, ServerLevelAccessor level, MobSpawnType reason,
                                   BlockPos pos, RandomSource random) {
        return level.getDifficulty() != Difficulty.PEACEFUL
                && (!natural(reason) || eligible(ProvincialHostiles.require(EntityType.getKey(type)), level, pos));
    }

    static boolean eligible(Rule rule, ServerLevelAccessor level, BlockPos pos) {
        var server = level.getLevel();
        if (!server.dimension().equals(Level.OVERWORLD) || !level.getBiome(pos).is(rule.biome())) return false;
        var generator = WorldPlanRuntime.generator(server).orElse(null);
        if (generator == null || generator.rules().provincialBiomes == null
                || generator.fieldSites().values().stream().anyMatch(site -> site.contains(pos.getX(), pos.getZ()))) return false;
        long time = Math.floorMod(level.dayTime(), 24000);
        if (rule.activity() == ProvincialHostiles.Activity.DAY && time >= 12500
                || rule.activity() == ProvincialHostiles.Activity.TWILIGHT && time >= 3500 && time < 9300) return false;

        var here = level.getBlockState(pos);
        boolean water = here.getFluidState().is(FluidTags.WATER);
        if (!here.getCollisionShape(level, pos).isEmpty()) return false;
        if (rule.behavior() == Behavior.SWIM) {
            if (!water || !level.getFluidState(pos.above()).is(FluidTags.WATER)) return false;
            return rule.habitat() == Habitat.REEF ? ProvincialAnimal.nearReef(level, pos)
                    : nearby(level, pos, 5, -1, 5, ProvincialEnemy::ice);
        }

        var below = level.getBlockState(pos.below());
        boolean palette = rule.palette().contains(BuiltInRegistries.BLOCK.getKey(below.getBlock()));
        if (water && rule.behavior() != Behavior.WADER || !water && !here.getFluidState().isEmpty()) return false;
        if (!palette && !(rule.habitat() == Habitat.SNOW && below.is(Blocks.SNOW))
                && !(rule.habitat() == Habitat.TREES && (below.is(BlockTags.LOGS) || below.is(BlockTags.LEAVES)))) return false;

        return switch (rule.habitat()) {
            case BANK, FALLS, TIDE, FEN, FORD -> water || nearby(level, pos, 5, -3, 2,
                    state -> state.getFluidState().is(FluidTags.WATER));
            case TREES -> ProvincialAnimal.nearLog(level, pos) || below.is(BlockTags.LOGS) || below.is(BlockTags.LEAVES);
            case LEDGE, REDROCK -> ProvincialAnimal.nearWall(level, pos) || ProvincialAnimal.nearDrop(level, pos);
            case SNOW -> below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.SNOW)
                    || nearby(level, pos, 3, -1, 0, state -> state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW));
            case SALT -> below.is(Blocks.WHITE_TERRACOTTA) || below.is(Blocks.CALCITE)
                    || nearby(level, pos, 3, -1, 0, state -> state.is(Blocks.WHITE_TERRACOTTA));
            case LAVA -> nearby(level, pos, 5, -3, 1, state -> state.getFluidState().is(FluidTags.LAVA));
            case CRYSTAL -> nearby(level, pos, 5, -2, 3, ProvincialEnemy::crystal);
            case REEF, ICE -> false;
        };
    }

    private static boolean ice(BlockState state) {
        return state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE);
    }

    private static boolean crystal(BlockState state) {
        return state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST) || state.is(Blocks.AMETHYST_CLUSTER);
    }

    private static boolean nearby(LevelReader level, BlockPos pos, int radius, int bottom, int top,
                                  Predicate<BlockState> match) {
        var cursor = new BlockPos.MutableBlockPos();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz > radius * radius
                        || !level.hasChunk((pos.getX() + dx) >> 4, (pos.getZ() + dz) >> 4)) continue;
                for (int dy = bottom; dy <= top; dy++) {
                    if (match.test(level.getBlockState(cursor.setWithOffset(pos, dx, dy, dz)))) return true;
                }
            }
        }
        return false;
    }

    private static boolean natural(MobSpawnType reason) {
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
    }
}
