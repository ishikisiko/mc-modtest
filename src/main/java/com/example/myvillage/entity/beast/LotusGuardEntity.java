package com.example.myvillage.entity.beast;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.stats.runtime.StatsRuntime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The arena keeper uses the ordinary beast clock, hit budget and damage pipeline. */
public final class LotusGuardEntity extends BeastEntity {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "lotus_guard");

    private final ServerBossEvent healthBar = new ServerBossEvent(Component.translatable("entity.yuanxian.lotus_guard"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
    private final Set<ServerPlayer> observers = new HashSet<>();
    private final AnimationState hurtPose = new AnimationState();
    private final AnimationState deathPose = new AnimationState();
    private final Map<String, AnimationState> animationStates;
    private BlockPos home;
    private int previousHurtTime;

    public LotusGuardEntity(EntityType<? extends LotusGuardEntity> type, Level level) {
        super(type, level);
        var states = new LinkedHashMap<>(super.clipAnimationStates());
        states.put("hurt", hurtPose);
        states.put("death", deathPose);
        animationStates = Map.copyOf(states);
        var identity = rules().encounter();
        if (!level.isClientSide()) {
            StatsRuntime.applyVanillaRealm(this, identity.realm().toString(), identity.stage());
            setHealth(getMaxHealth());
        }
        setPersistenceRequired();
        xpReward = identity.experience();
    }

    public static LotusGuardRules rules() { return LotusGuardRules.get(); }

    @Override protected ResourceLocation beastId() { return ID; }

    /** Only server-owned encounters call this before admission to the level. */
    public void bindArena(BlockPos position) {
        if (level().isClientSide() || home != null) throw new IllegalStateException("伏莲兽战台已经绑定");
        home = position.immutable();
    }

    public static boolean isFighter(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new BeastAttackGoal(this));
        goalSelector.addGoal(3, new ReturnHomeGoal());
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::canAttack));
    }

    @Override public boolean canAttack(LivingEntity target) {
        var arena = rules().arena();
        return target instanceof Player player && isFighter(player)
            && level().getDifficulty() != Difficulty.PEACEFUL
            && inBounds(target, arena.targetX(), arena.targetZ())
            && inBounds(this, arena.homeX() + 1, arena.homeZ() + 1)
            && super.canAttack(target);
    }

    private boolean inBounds(Entity entity, double dx, double dz) {
        return home == null || Math.abs(entity.getX() - home.getX() - .5) <= dx
            && Math.abs(entity.getZ() - home.getZ() - .5) <= dz
            && Math.abs(entity.getY() - home.getY()) <= rules().arena().elevation();
    }

    @Override protected boolean chase(LivingEntity target) {
        if (home == null) return super.chase(target);
        var range = rules().arena();
        double x0 = home.getX() + .5, z0 = home.getZ() + .5;
        double x = Mth.clamp(target.getX(), x0 - range.homeX(), x0 + range.homeX());
        double z = Mth.clamp(target.getZ(), z0 - range.homeZ(), z0 + range.homeZ());
        return getNavigation().moveTo(x, home.getY(), z, definition().chase().speedModifier());
    }

    @Override protected float aimYaw(BeastMoveDefinition move, LivingEntity target) {
        return LotusGuardCombat.facing(super.aimYaw(move, target), LotusGuardCombat.side(move.id()));
    }

    @Override protected float impactYaw(BeastMoveDefinition move) {
        return LotusGuardCombat.impact(lockedMoveYaw(), LotusGuardCombat.side(move.id()));
    }

    @Override protected boolean moveEligible(BeastMoveDefinition move, LivingEntity target) {
        return canAttack(target) && (LotusGuardCombat.side(move.id()) == 0
                || target.getBoundingBox().minY <= getY() + move.hit().high());
    }

    @Override protected boolean isVictim(LivingEntity candidate, @Nullable LivingEntity target) {
        return candidate instanceof Player && super.isVictim(candidate, target)
            && getSensing().hasLineOfSight(candidate);
    }

    private int openFlank() {
        var current = shownMove().orElse(null);
        return current != null && LotusGuardCombat.exposed(current, runningMoveTick(), rules().armor())
            ? LotusGuardCombat.side(current.id()) : 0;
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide() && source.getEntity() instanceof Player player) {
            int side = openFlank();
            if (side != 0 && LotusGuardCombat.isFlankHit(getX(), getZ(), lockedMoveYaw(), side,
                    player.getX(), player.getZ(), rules().armor())) {
                amount *= rules().armor().weakMultiplier();
            }
        }
        return super.hurt(source, amount);
    }

    @Override protected void playWindupSound(BeastMoveDefinition move) {
        if (LotusGuardCombat.side(move.id()) != 0) playSound(SoundEvents.DEEPSLATE_PLACE, 1.7F, .64F);
    }

    @Override protected void onMoveTick(BeastMoveDefinition move, int tick) {
        if (tick == move.activeTicks().first() && LotusGuardCombat.side(move.id()) != 0) {
            playSound(SoundEvents.DEEPSLATE_BREAK, 2.5F, .54F);
        }
    }

    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.DEEPSLATE_HIT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.DEEPSLATE_BREAK; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.DEEPSLATE_STEP, 1.25F, .70F); }

    @Override public AABB getBoundingBoxForCulling() { return super.getBoundingBoxForCulling().inflate(2); }

    @Override public Map<String, AnimationState> clipAnimationStates() { return animationStates; }

    public boolean isPresentationShown() { return isDeadOrDying() || hurtPose.isStarted(); }

    @Override public void tick() {
        if (!level().isClientSide() && home == null) home = blockPosition();
        super.tick();
        if (level().isClientSide()) {
            int clock = (int) clipTime(0);
            if (isDeadOrDying()) deathPose.startIfStopped(clock - deathTime);
            if (hurtTime > 0 && !isMoveShown() && !isStaggerShown() && !isDeadOrDying()) {
                if (!hurtPose.isStarted() || hurtTime > previousHurtTime) hurtPose.start(clock);
            } else {
                hurtPose.stop();
            }
            previousHurtTime = hurtTime;
        } else if (!isAlive()) {
            healthBar.removeAllPlayers();
        } else {
            updateHealthBar();
        }
    }

    private void updateHealthBar() {
        healthBar.setProgress(getHealth() / getMaxHealth());
        healthBar.setColor(openFlank() == 0 ? BossEvent.BossBarColor.WHITE : BossEvent.BossBarColor.GREEN);
        if (tickCount % rules().arena().checkTicks() != 0) return;
        double range = rules().arena().observerRadius();
        for (ServerPlayer player : observers) {
            if (player.isAlive() && player.distanceToSqr(this) <= range * range) healthBar.addPlayer(player);
            else healthBar.removePlayer(player);
        }
    }

    @Override public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        observers.add(player);
        if (player.distanceToSqr(this) <= Mth.square(rules().arena().observerRadius())) healthBar.addPlayer(player);
    }

    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        observers.remove(player);
        healthBar.removePlayer(player);
    }

    @Override public void remove(RemovalReason reason) {
        healthBar.removeAllPlayers();
        super.remove(reason);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.putLong("LotusHome", home.asLong());
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("LotusHome")) home = BlockPos.of(tag.getLong("LotusHome"));
        cancelCurrentMove(definition().chase().cancelledCooldownTicks());
    }

    @Override public String debugStatus() {
        return super.debugStatus() + " lotus_flank=" + openFlank();
    }

    private final class ReturnHomeGoal extends Goal {
        private int repath;

        private ReturnHomeGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override public boolean canUse() {
            return home != null && !isMoveRunning() && (getTarget() == null || !canAttack(getTarget()))
                && distanceToSqr(Vec3.atBottomCenterOf(home)) > 9;
        }

        @Override public void start() { repath = 0; }

        @Override public void tick() {
            if (repath-- > 0) return;
            repath = adjustedTickDelay(rules().arena().repathTicks());
            getNavigation().moveTo(home.getX() + .5, home.getY(), home.getZ() + .5,
                definition().chase().speedModifier());
        }

        @Override public void stop() { getNavigation().stop(); }
    }
}
