package com.example.myvillage.entity.beast;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.combat.runtime.CombatReactionService;
import com.example.myvillage.worldplan.wonder.FoldedRidge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class CliffRayEntity extends FoldedRidgeBeast {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "cliff_ray");
    private static final EntityDataAccessor<Boolean> PERCHED = SynchedEntityData.defineId(CliffRayEntity.class, EntityDataSerializers.BOOLEAN);
    private static final CliffRayRules RULES = CliffRayRules.load();
    // The approved perch pose's hook plane is 20.559 model pixels behind the pivot.
    private static final double HOOK_OFFSET = 20.559 / 16;
    private static final double FEET_OVER_WALL_BASE = .32;
    private @Nullable Perch perch;
    private @Nullable Perch approach;
    private int searchDelay;
    private int approachLeft;

    private record Perch(BlockPos wall, Direction outward) {
        Vec3 position() {
            return Vec3.atBottomCenterOf(wall).add(Vec3.atLowerCornerOf(outward.getNormal()).scale(.5 + HOOK_OFFSET))
                    .add(0, FEET_OVER_WALL_BASE, 0);
        }
        float yaw() { return outward.toYRot(); }
    }

    public CliffRayEntity(EntityType<? extends BeastEntity> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    @Override protected ResourceLocation beastId() { return ID; }

    public static void validateMoves(BeastDefinition definition) {
        if (definition.moves().size() != 1 || RULES.releaseFrame() >= definition.move(0).turnLockTick())
            throw new IllegalArgumentException("岩鳐须在掠击方向锁定前松开岩壁");
    }

    public static boolean checkSpawn(EntityType<CliffRayEntity> type, ServerLevelAccessor level,
                                     MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!allowsSpawn(type, level, reason, pos, random)) return false;
        return !natural(reason) || ridge(level).map(site -> findPerch(level, site, type, null, Vec3.atBottomCenterOf(pos))).isPresent();
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData group) {
        if (natural(reason)) {
            ridge(level).ifPresent(site -> {
                Perch home = findPerch(level, site, getType(), this, position());
                if (home != null) latch(home);
            });
        }
        return super.finalizeSpawn(level, difficulty, reason, group);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PERCHED, false);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public String idleClip() {
        return entityData.get(PERCHED) || isMoveShown() || isStaggerShown() ? IDLE_CLIP : "flight";
    }

    @Override
    protected boolean canLaunchMove() { return perch != null || onGround(); }

    @Override
    protected boolean moveEligible(BeastMoveDefinition move, LivingEntity target) {
        double dy = target.getY()-getY();
        if (dy < -RULES.diveDrop() || dy > RULES.diveRise()) return false;
        if (perch == null) return true;
        return Math.abs(Mth.wrapDegrees(super.aimYaw(move, target)-perch.yaw())) <= RULES.attackArc();
    }

    @Override
    protected float aimYaw(BeastMoveDefinition move, LivingEntity target) {
        return perch == null ? super.aimYaw(move, target) : perch.yaw();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server) || isDeadOrDying()) return;
        var site = ridge(server).orElse(null);
        if (CombatReactionService.isStaggered(this) || isInWater()) {
            if (perch != null || approach != null) release();
            return;
        }
        if (perch != null) {
            if (site == null || !validPerch(server, site, getType(), this, perch)) {
                release();
            } else {
                latch(perch);
            }
        }
        if (isMoveRunning()) return;

        LivingEntity target = getTarget();
        if (target != null && (site == null || inInterlayer(site, target.blockPosition())) && tryStartMove(target)) {
            approach = null;
            return;
        }
        if (perch != null) return;

        if (approach != null && --approachLeft > 0 && steerToPerch(server, site)) return;
        approach = null;
        setNoGravity(false);
        if (site != null && --searchDelay <= 0 && inInterlayer(site, blockPosition())) {
            searchDelay = RULES.scanInterval();
            approach = findPerch(server, site, getType(), this, position());
            approachLeft = RULES.approachTicks();
        }
    }

    @Override
    protected void onMoveTick(BeastMoveDefinition move, int tick) {
        if (tick < RULES.releaseFrame()) return;
        if (perch != null) release();
        setNoGravity(true);
        Vec3 forward = BeastGeometry.forward(getYRot());
        double speed = getAttributeValue(Attributes.MOVEMENT_SPEED);
        double lift = tick < move.turnLockTick() ? .05 : Mth.clamp((lockedAimPoint().y-getY())*.24, -.38, .19);
        if (tick > move.activeTicks().last()) {
            speed *= .55;
            lift = -.15;
        }
        Vec3 motion = forward.scale(speed).add(0, lift, 0);
        if (level().getBlockCollisions(this, getBoundingBox().move(motion)).iterator().hasNext()) {
            motion = new Vec3(0, Math.min(0, lift), 0);
        }
        setDeltaMovement(motion);
        resetFallDistance();
        hasImpulse = true;
    }

    @Override
    protected void onMoveFinished(BeastMoveDefinition move) {
        release();
        searchDelay = 0;
    }

    private boolean steerToPerch(ServerLevel server, @Nullable FoldedRidge site) {
        if (site == null || !validPerch(server, site, getType(), this, approach)) return false;
        Vec3 displacement = approach.position().subtract(position());
        double distance = displacement.length();
        if (distance < .24) {
            latch(approach);
            return true;
        }
        Vec3 motion = displacement.scale(Math.min(.27, distance)/distance);
        if (server.getBlockCollisions(this, getBoundingBox().move(motion)).iterator().hasNext()) return false;
        setNoGravity(true);
        setDeltaMovement(motion);
        float yaw = BeastGeometry.yawToward(motion.x, motion.z);
        setYRot(yaw);
        setYBodyRot(yaw);
        hasImpulse = true;
        resetFallDistance();
        return true;
    }

    private void latch(Perch point) {
        perch = point;
        approach = null;
        entityData.set(PERCHED, true);
        setNoGravity(true);
        Vec3 pos = point.position();
        if (distanceToSqr(pos) > 1e-7) setPos(pos.x, pos.y, pos.z);
        setDeltaMovement(Vec3.ZERO);
        setYRot(point.yaw());
        setYBodyRot(point.yaw());
        setYHeadRot(point.yaw());
        resetFallDistance();
    }

    private void release() {
        perch = null;
        approach = null;
        entityData.set(PERCHED, false);
        setNoGravity(false);
    }

    private static @Nullable Perch findPerch(ServerLevelAccessor level, FoldedRidge site, EntityType<?> type,
                                              @Nullable CliffRayEntity ray, Vec3 from) {
        BlockPos origin = BlockPos.containing(from);
        Perch best = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (BlockPos wall : BlockPos.betweenClosed(origin.offset(-RULES.horizontal(), -RULES.vertical(), -RULES.horizontal()),
                origin.offset(RULES.horizontal(), RULES.vertical(), RULES.horizontal()))) {
            if (!level.hasChunkAt(wall) || !level.getBlockState(wall).is(ModBlocks.FOLDED_RIDGE_STONE.get())) continue;
            for (Direction face : Direction.Plane.HORIZONTAL) {
                var point = new Perch(wall.immutable(), face);
                double distance = from.distanceToSqr(point.position());
                if (distance >= nearest || !validPerch(level, site, type, ray, point)) continue;
                best = point;
                nearest = distance;
            }
        }
        return best;
    }

    private static boolean validPerch(ServerLevelAccessor level, FoldedRidge site, EntityType<?> type,
                                      @Nullable CliffRayEntity ray, Perch point) {
        Vec3 position = point.position();
        BlockPos air = BlockPos.containing(position);
        if (!inInterlayer(site, air) || !level.hasChunkAt(air) || !level.hasChunkAt(point.wall)) return false;
        var wall = level.getBlockState(point.wall);
        if (!wall.is(ModBlocks.FOLDED_RIDGE_STONE.get()) || !wall.isFaceSturdy(level, point.wall, point.outward)) return false;
        if (!level.getBlockState(point.wall.relative(point.outward)).getCollisionShape(level, point.wall.relative(point.outward)).isEmpty()) return false;
        var box = type.getSpawnAABB(position.x, position.y, position.z);
        return !level.containsAnyLiquid(box) && level.noCollision(ray, box);
    }

    @Override
    protected void playWindupSound(BeastMoveDefinition move) {
        playSound(SoundEvents.STONE_BREAK, 1.1F, .76F);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    @Override
    public void die(DamageSource source) {
        release();
        super.die(source);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (perch != null) {
            tag.putLong("RidgePerch", perch.wall.asLong());
            tag.putInt("RidgePerchFace", perch.outward.get2DDataValue());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        release();
        if (tag.contains("RidgePerch")) {
            perch = new Perch(BlockPos.of(tag.getLong("RidgePerch")), Direction.from2DDataValue(tag.getInt("RidgePerchFace")));
        }
    }
}
