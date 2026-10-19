package com.example.myvillage.entity.beast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import com.example.myvillage.stats.runtime.StatsRuntime;
import javax.annotation.Nullable;
import com.example.myvillage.combat.runtime.CombatReactionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 雷角山君: a persistent lair boss. All intent, hit geometry and state changes run on the server. */
public final class ThunderTigerEntity extends BeastEntity {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("myvillage", "thunder_tiger");
    private static final Logger LOG = LoggerFactory.getLogger(ThunderTigerEntity.class);
    private static final EntityDataAccessor<Boolean> HORN_BROKEN = SynchedEntityData.defineId(ThunderTigerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> HORN_EXPOSURE = SynchedEntityData.defineId(ThunderTigerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BOSS_PHASE = SynchedEntityData.defineId(ThunderTigerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> HORN_BREAK_START = SynchedEntityData.defineId(ThunderTigerEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> PHASE_CHANGE_START = SynchedEntityData.defineId(ThunderTigerEntity.class, EntityDataSerializers.LONG);
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.myvillage.thunder_tiger"), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
    private final ThunderTigerPart headPart;
    private final ThunderTigerPart bodyPart;
    private final ThunderTigerPart[] parts;
    private final Map<String, AnimationState> presentationStates = new LinkedHashMap<>();
    private Map<String, AnimationState> allAnimationStates;
    private BlockPos lairCenter;
    private List<BlockPos> lairPillars = List.of();
    private final Set<ServerPlayer> trackedObservers = new HashSet<>();
    private ResourceLocation encounterRealm;
    private int encounterStage;
    private long loadGraceUntil;
    private int landingAftershockTick = -1;
    private float hornDamage;
    private int unseenTicks;
    private Vec3 previousMovePosition;
    private Vec3 thunderMark;
    private boolean exposureWasShown;
    private int previousHurtTime;

    public ThunderTigerEntity(EntityType<? extends ThunderTigerEntity> type, Level level) {
        super(type, level);
        var h = rules().horn();
        headPart = new ThunderTigerPart(this, true, (float) h.headWidth(), (float) h.headHeight());
        bodyPart = new ThunderTigerPart(this, false, (float) h.bodyWidth(), (float) h.bodyHeight());
        parts = new ThunderTigerPart[]{headPart, bodyPart};
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
        for (String clip : List.of("horn_exposed", "horn_break", "phase_change", "death", "hurt")) presentationStates.put(clip, new AnimationState());
        Map<String, AnimationState> combined = new LinkedHashMap<>(super.clipAnimationStates());
        combined.putAll(presentationStates);
        allAnimationStates = Collections.unmodifiableMap(combined);
        encounterRealm = rules().encounterRealm(); encounterStage = rules().encounterStage();
        if (!level.isClientSide()) {
            StatsRuntime.applyVanillaRealm(this, encounterRealm.toString(), encounterStage);
            // Fresh birth only. Entity NBT subsequently restores saved Health; the read path never heals.
            setHealth(getMaxHealth());
        }
        xpReward = 0; // Rewards intentionally deferred.
        setPersistenceRequired();
        updateParts();
    }
    public static ThunderTigerRules rules() { return ThunderTigerRules.bundled(); }
    @Override protected ResourceLocation beastId() { return ID; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HORN_BROKEN, false); builder.define(HORN_EXPOSURE, 0); builder.define(BOSS_PHASE, 1);
        builder.define(HORN_BREAK_START, -1L); builder.define(PHASE_CHANGE_START, -1L);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new BossGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class,
                rules().bounds().targetRescanTicks(), true, false, this::validTarget));
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isMultipartEntity() { return true; }
    @Override public ThunderTigerPart[] getParts() { return parts; }
    @Override public void setId(int id) {
        super.setId(id);
        if (parts != null) for (int index = 0; index < parts.length; index++) parts[index].setId(id + index + 1);
    }
    public boolean isHornBroken() { return entityData.get(HORN_BROKEN); }
    public boolean isHornExposed() { return entityData.get(HORN_EXPOSURE) > 0; }
    public boolean isEnraged() { return getBossPhase() == 2; }
    public int getBossPhase() { return entityData.get(BOSS_PHASE); }
    public int getHornExposureTicks() { return entityData.get(HORN_EXPOSURE); }
    /** Includes real multipart occupied volume before the entity's first world tick. */
    public AABB getSpawnOccupiedBounds() {
        updateParts();
        return getBoundingBox().minmax(headPart.getBoundingBox()).minmax(bodyPart.getBoundingBox());
    }
    public boolean isLairBound() { return lairCenter != null; }
    public BlockPos getLairCenter() { return lairCenter == null ? blockPosition() : lairCenter; }
    public int getHornBreakTick() { return elapsed(HORN_BREAK_START, rules().presentation().hornBreakTicks()); }
    public int getPhaseChangeTick() { return elapsed(PHASE_CHANGE_START, rules().presentation().phaseChangeTicks()); }
    private int elapsed(EntityDataAccessor<Long> key, int duration) {
        long start = entityData.get(key);
        long age = level().getGameTime() - start;
        return start >= 0 && age >= 0 && age < duration ? (int) age : -1;
    }
    /** Client hurt pose is visual only; it must not pause server AI or extend any damage window. */
    public boolean isHurtShown() {
        AnimationState state = presentationStates.get("hurt");
        return state != null && state.isStarted() && hurtTime > 0 && !isMoveShown() && !isStaggerShown() && !isPresentationShown();
    }
    public boolean isPresentationShown() { return isDeadOrDying() || isHornExposed() || getHornBreakTick() >= 0 || getPhaseChangeTick() >= 0; }
    /** A lair passes authored pillar FOOT CENTERS, each covering the configured column footprint. */
    public void bindLair(BlockPos home, List<BlockPos> pillars) {
        if (level().isClientSide() || lairCenter != null) return;
        if (pillars.size() > rules().horn().maximumPillars()) throw new IllegalArgumentException("Too many authored lair pillars");
        for (BlockPos pillar : pillars) if (!pillar.closerThan(home, rules().bounds().homeRadius())) throw new IllegalArgumentException("Pillar outside lair bounds");
        lairCenter = home.immutable();
        lairPillars = pillars.stream().map(BlockPos::immutable).distinct().toList();
    }
    @Override public Map<String, AnimationState> clipAnimationStates() {
        return allAnimationStates == null ? super.clipAnimationStates() : allAnimationStates;
    }
    @Override public Optional<AnimationState> clipAnimationState(String clip) { return Optional.ofNullable(clipAnimationStates().get(clip)); }
    @Override public void tick() {
        super.tick();
        updateParts();
        if (level().isClientSide()) tickPresentation();
        else if (!isAlive()) bossEvent.removeAllPlayers();
    }
    private void tickPresentation() {
        int clock = (int) clipTime(0);
        presentation(presentationStates.get("horn_break"), getHornBreakTick(), clock);
        presentation(presentationStates.get("phase_change"), getPhaseChangeTick(), clock);
        if (isHornExposed()) {
            if (!exposureWasShown) presentationStates.get("horn_exposed").start(clock - (rules().horn().exposureTicks() - getHornExposureTicks()));
        } else presentationStates.get("horn_exposed").stop();
        exposureWasShown = isHornExposed();
        if (isDeadOrDying()) presentationStates.get("death").startIfStopped(clock - deathTime);
        else presentationStates.get("death").stop();
        AnimationState hurt = presentationStates.get("hurt");
        if (hurtTime > 0 && !isMoveShown() && !isStaggerShown() && !isPresentationShown()) {
            if (!hurt.isStarted() || hurtTime > previousHurtTime) hurt.start(clock - Math.max(0, hurtDuration - hurtTime));
        } else hurt.stop();
        previousHurtTime = hurtTime;
    }
    private static void presentation(AnimationState state, int age, int clock) {
        if (age < 0) state.stop(); else if (!state.isStarted()) state.start(clock - age);
    }
    private void updateParts() {
        if (parts == null) return;
        var h = rules().horn();
        Vec3 forward = BeastGeometry.forward(getYRot());
        double headY = isHornExposed() ? h.exposedHeadCenterY() : h.headCenterY();
        headPart.setPos(getX() + forward.x * h.headForward(), getY() + headY - h.headHeight() / 2, getZ() + forward.z * h.headForward());
        bodyPart.setPos(position());
        double middle = (h.bodyFront() + h.bodyBack()) / 2;
        double length = (h.bodyFront() - h.bodyBack()) / 2;
        double halfX = Math.abs(forward.x) * length + Math.abs(forward.z) * h.bodyWidth() / 2;
        double halfZ = Math.abs(forward.z) * length + Math.abs(forward.x) * h.bodyWidth() / 2;
        double x = getX() + forward.x * middle, z = getZ() + forward.z * middle;
        bodyPart.setBoundingBox(new AABB(x - halfX, getY(), z - halfZ, x + halfX, getY() + h.bodyHeight(), z + halfZ));
    }
    @Override protected void customServerAiStep() {
        updateBossBar();
        updatePhase();
        LivingEntity target = getTarget();
        if (lairCenter != null && position().distanceToSqr(Vec3.atBottomCenterOf(lairCenter)) > rules().bounds().homeRadius() * rules().bounds().homeRadius()) {
            setTarget(null); target = null;
            cancelCurrentMove(definition().chase().cancelledCooldownTicks());
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        if (target != null) {
            unseenTicks = getSensing().hasLineOfSight(target) ? 0 : unseenTicks + 1;
            if (!validTarget(target) || unseenTicks > rules().bounds().unseenTimeoutTicks()) { setTarget(null); unseenTicks = 0; }
        }
        if (isPresentationShown()) {
            if (isHornExposed()) entityData.set(HORN_EXPOSURE, getHornExposureTicks() - 1);
            cancelCurrentMove(definition().chase().cancelledCooldownTicks());
            getNavigation().stop();
            getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
            setDeltaMovement(0, getDeltaMovement().y, 0);
            setAggressive(false);
            return;
        }
        super.customServerAiStep();
    }
    private boolean validTarget(LivingEntity target) {
        var b = rules().bounds();
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level()
                && target.isAttackable() && !isAlliedTo(target) && canAttack(target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable))
                && distanceToSqr(target) <= b.targetRadius() * b.targetRadius()
                && Math.abs(target.getY() - getY()) <= b.verticalRange()
                && (lairCenter == null || target.position().distanceToSqr(Vec3.atBottomCenterOf(lairCenter)) <= b.homeRadius() * b.homeRadius());
    }
    @Override public boolean tryStartMove(LivingEntity target) {
        return level().getGameTime() >= loadGraceUntil && !isPresentationShown() && validTarget(target) && super.tryStartMove(target);
    }
    @Override protected boolean moveEligible(BeastMoveDefinition move, LivingEntity target) {
        return !isHornBroken() || !isThunderMove(move);
    }
    @Override protected int moveGapTicks() { return isEnraged() ? rules().phaseTwo().moveGapTicks() : super.moveGapTicks(); }
    private static boolean isThunderMove(BeastMoveDefinition move) { return move.animation().equals("thunder_stomp") || move.animation().equals("roar"); }
    @Override public boolean forceMove(ResourceLocation move) {
        return !isDeadOrDying() && definition().move(move).filter(m -> !isHornBroken() || !isThunderMove(m)).isPresent() && super.forceMove(move);
    }
    @Override protected void playWindupSound(BeastMoveDefinition move) {
        previousMovePosition = position(); thunderMark = null; landingAftershockTick = -1;
        SoundEvent sound = isThunderMove(move) ? ThunderTigerSounds.CHARGE.get() : ThunderTigerSounds.ATTACK.get();
        playSound(sound, (float) rules().feedback().telegraphVolume(), getVoicePitch());
        event("telegraph", "");
    }
    @Override protected void onMoveTick(BeastMoveDefinition move, int tick) {
        if (tick == move.turnLockTick() && isThunderMove(move)) {
            thunderMark = lockedAimPoint();
            if (move.animation().equals("thunder_stomp")) thunderMark = groundAt(thunderMark);
            else if (getTarget() != null) thunderMark = thunderMark.add(0, getTarget().getBbHeight() / 2, 0);
        }
        if (tick < move.activeTicks().first() && tick % rules().thunder().telegraphIntervalTicks() == 0) {
            if (move.animation().equals("thunder_stomp")) ring(position(), rules().thunder().stompRingRadius(), false);
            if (thunderMark != null) ring(thunderMark, markRadius(move), false);
            else if (move.animation().equals("pounce")) {
                Vec3 forward = BeastGeometry.forward(getYRot());
                ring(position().add(forward.scale(move.hit().far())), move.hit().halfWidth(), false);
            }
        }
        if (tick == move.activeTicks().first()) {
            event("active", "");
            playSound(isThunderMove(move) ? ThunderTigerSounds.THUNDER.get() : ThunderTigerSounds.ATTACK.get(), (float) rules().feedback().strikeVolume(), getVoicePitch());
        }
        if (move.animation().equals("pounce") && tick > move.lunge().tick() && tick <= move.activeTicks().last() && !isHornBroken()) {
            if (collidePillar(previousMovePosition == null ? position() : previousMovePosition, position())) return;
        }
        previousMovePosition = position();
        if (isEnraged() && !isHornBroken() && move.animation().equals("pounce") && tick > move.lunge().tick()) {
            if (landingAftershockTick < 0 && onGround()) {
                thunderMark = groundAt(position());
                landingAftershockTick = tick + rules().phaseTwo().aftershockDelayTicks();
                event("landing_telegraph", " aftershock_tick=" + landingAftershockTick);
            }
            if (landingAftershockTick >= 0 && tick < landingAftershockTick && tick % rules().thunder().telegraphIntervalTicks() == 0)
                ring(thunderMark, rules().phaseTwo().aftershockRadius(), false);
            if (tick == landingAftershockTick) {
                strikeMarked(move, getTarget(), rules().phaseTwo().aftershockRadius());
                ring(thunderMark, rules().phaseTwo().aftershockRadius(), true);
                playSound(ThunderTigerSounds.THUNDER.get(), (float) rules().feedback().strikeVolume(), getVoicePitch());
                event("aftershock", "");
            }
        }
        if (isEnraged() && !isHornBroken() && isThunderMove(move) && tick == move.activeTicks().first() + rules().phaseTwo().aftershockDelayTicks()) {
            ring(thunderMark == null ? position() : thunderMark, rules().phaseTwo().aftershockRadius(), true);
            strikeMarked(move, getTarget(), rules().phaseTwo().aftershockRadius());
            event("aftershock", "");
        }
        if (isEnraged() && !isHornBroken() && isThunderMove(move) && thunderMark != null && tick > move.activeTicks().last()
                && tick < move.activeTicks().first() + rules().phaseTwo().aftershockDelayTicks()
                && tick % rules().thunder().telegraphIntervalTicks() == 0) ring(thunderMark, rules().phaseTwo().aftershockRadius(), false);
    }
    @Override protected void onMoveFinished(BeastMoveDefinition move) { thunderMark = null; landingAftershockTick = -1; }
    private Vec3 groundAt(Vec3 aim) {
        double extent = rules().bounds().verticalRange();
        var hit = level().clip(new ClipContext(aim.add(0, extent, 0), aim.add(0, -extent, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : aim;
    }
    private double markRadius(BeastMoveDefinition move) { return move.animation().equals("roar") ? rules().thunder().roarMarkRadius() : rules().thunder().markRadius(); }
    @Override protected void strike(BeastMoveDefinition move, @Nullable LivingEntity target) {
        if (!isThunderMove(move)) { super.strike(move, target); return; }
        if (runningMoveTick() != move.activeTicks().first()) return;
        strikeMarked(move, target, markRadius(move));
        ring(thunderMark == null ? position() : thunderMark, markRadius(move), true);
        if (move.animation().equals("thunder_stomp")) {
            hitArea(move, target, position(), rules().thunder().stompRingRadius(), rules().thunder().markHeight());
            ring(position(), rules().thunder().stompRingRadius(), true);
        }
    }
    private void strikeMarked(BeastMoveDefinition move, @Nullable LivingEntity target, double radius) {
        if (thunderMark != null) hitArea(move, target, thunderMark, radius, rules().thunder().markHeight());
    }
    private void hitArea(BeastMoveDefinition move, @Nullable LivingEntity target, Vec3 center, double radius, double height) {
        AABB box = new AABB(center.x - radius, center.y - height / 2, center.z - radius, center.x + radius, center.y + height / 2, center.z + radius);
        var candidates = level().getEntitiesOfClass(LivingEntity.class, box, candidate -> isVictim(candidate, target)
                && candidate.getBoundingBox().getCenter().subtract(center).horizontalDistanceSqr() <= radius * radius);
        candidates.sort(Comparator.comparingDouble(this::distanceToSqr));
        for (LivingEntity victim : candidates) if (registerMoveVictim(move, victim)) hitVictim(move, victim);
    }
    @Override protected boolean isVictim(LivingEntity candidate, @Nullable LivingEntity target) {
        return validTarget(candidate) && super.isVictim(candidate, target) && getSensing().hasLineOfSight(candidate);
    }
    @Override protected void hitVictim(BeastMoveDefinition move, LivingEntity victim) {
        BeastMoveDefinition effective = move;
        if (isEnraged()) effective = new BeastMoveDefinition(move.id(), move.animation(), move.totalTicks(), move.windupTicks(), move.turnLockTick(), move.activeTicks(), move.immuneTicks(), move.damageMultiplier() * rules().phaseTwo().damageMultiplier(), move.maximumTargets(), move.useRange(), move.cooldownTicks(), move.weight(), move.lunge(), move.hit(), move.knockback());
        float before = victim.getHealth();
        super.hitVictim(effective, victim);
        event("hit", " victim=" + victim.getUUID() + " accepted_damage=" + Math.max(0, before - victim.getHealth()));
    }
    private boolean collidePillar(Vec3 from, Vec3 to) {
        var h = rules().horn();
        // The physical head travels ahead of the entity; sweep it so fast leaps cannot skip a pillar.
        updateParts();
        AABB sweep = headPart.getBoundingBox().move(from.subtract(to)).minmax(headPart.getBoundingBox());
        for (BlockPos center : lairPillars) {
            AABB column = new AABB(center.getX() - h.pillarHalfWidth(), center.getY(), center.getZ() - h.pillarHalfWidth(), center.getX() + h.pillarHalfWidth() + 1, center.getY() + h.pillarHeight(), center.getZ() + h.pillarHalfWidth() + 1);
            if (!sweep.intersects(column)) continue;
            for (int y = 0; y < h.pillarHeight(); y++) for (int x = -h.pillarHalfWidth(); x <= h.pillarHalfWidth(); x++) for (int z = -h.pillarHalfWidth(); z <= h.pillarHalfWidth(); z++) {
                BlockPos block = center.offset(x, y, z);
                if (!level().hasChunkAt(block)) continue;
                BlockState state = level().getBlockState(block);
                if (h.allowedBlocks().contains(BuiltInRegistries.BLOCK.getKey(state.getBlock())) && state.isCollisionShapeFullBlock(level(), block) && sweep.intersects(new AABB(block))
                        && ThunderTigerGeometry.sweptBoxIntersects(headPart.getBoundingBox(), to.subtract(from), new AABB(block))) {
                    event("horn_collision", " pillar=" + center.getX() + "," + center.getY() + "," + center.getZ());
                    cancelCurrentMove(definition().chase().cancelledCooldownTicks());
                    hornDamage = 0;
                    entityData.set(HORN_EXPOSURE, h.exposureTicks());
                    setDeltaMovement(Vec3.ZERO);
                    updateParts();
                    playSound(ThunderTigerSounds.HORN_BREAK.get(), (float) rules().feedback().strikeVolume(), getVoicePitch());
                    return true;
                }
            }
        }
        return false;
    }
    private void ring(Vec3 center, double radius, boolean impact) {
        if (!(level() instanceof ServerLevel server)) return;
        int points = rules().thunder().particlePoints();
        for (int index = 0; index < points; index++) {
            double angle = index * Math.PI * 2 / points;
            server.sendParticles(impact ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD,
                    center.x + Math.cos(angle) * radius, center.y + 0.12, center.z + Math.sin(angle) * radius, 1, 0, impact ? 0.3 : 0.02, 0, 0);
        }
    }
    @Override public boolean resistsStagger() { return !isHornExposed() && (isPresentationShown() || super.resistsStagger()); }
    public boolean hurtPart(ThunderTigerPart part, DamageSource source, float amount) {
        boolean exposed = part.isHead() && isHornExposed() && !isHornBroken() && source.getEntity() instanceof Player;
        float before = getHealth();
        boolean accepted = hurt(source, amount);
        float dealt = Math.max(0, before - getHealth());
        if (accepted && exposed && dealt > 0 && isAlive()) {
            hornDamage += dealt;
            if (hornDamage >= rules().horn().damageToBreak()) breakHorn();
        }
        return accepted;
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        boolean accepted = super.hurt(source, amount);
        if (accepted && !level().isClientSide() && isAlive()) updatePhase();
        return accepted;
    }
    private void breakHorn() {
        entityData.set(HORN_BROKEN, true); entityData.set(HORN_EXPOSURE, 0);
        entityData.set(HORN_BREAK_START, level().getGameTime());
        entityData.set(PHASE_CHANGE_START, -1L); // Horn-break pose wins if both transitions share a hit.
        cancelCurrentMove(definition().chase().cancelledCooldownTicks());
        playSound(ThunderTigerSounds.HORN_BREAK.get(), (float) rules().feedback().strikeVolume(), getVoicePitch());
        ring(headPart.getBoundingBox().getCenter(), rules().horn().headWidth(), true);
        event("horn_break", " horn_damage=" + hornDamage);
    }
    private void updatePhase() {
        int next = rules().phaseAfter(getBossPhase(), getHealth(), getMaxHealth());
        if (next == getBossPhase()) return;
        entityData.set(BOSS_PHASE, next);
        entityData.set(PHASE_CHANGE_START, isHornExposed() ? -1L : level().getGameTime());
        cancelCurrentMove(definition().chase().cancelledCooldownTicks());
        playSound(ThunderTigerSounds.ROAR.get(), (float) rules().feedback().telegraphVolume(), getVoicePitch());
        event("phase_change", "");
    }
    private void updateBossBar() {
        bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0, 1));
        bossEvent.setColor(isEnraged() ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.BLUE);
        bossEvent.setName(com.example.myvillage.boss.BossBarPresentation.name(getDisplayName(),
                Component.translatable("world_sim.stage." + encounterRealm.getPath() + "." + (encounterStage + 1)),
                isEnraged(), isHornBroken()));
        bossEvent.setVisible(isAlive());
        for (ServerPlayer player : List.copyOf(bossEvent.getPlayers())) if (!eligibleObserver(player)) bossEvent.removePlayer(player);
        for (ServerPlayer player : trackedObservers) if (eligibleObserver(player)) bossEvent.addPlayer(player);
    }
    private boolean eligibleObserver(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && player.level() == level() && distanceToSqr(player) <= rules().bounds().homeRadius() * rules().bounds().homeRadius();
    }
    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); trackedObservers.add(player); if (eligibleObserver(player)) bossEvent.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); trackedObservers.remove(player); bossEvent.removePlayer(player); }
    @Override public void remove(Entity.RemovalReason reason) { if (bossEvent != null) bossEvent.removeAllPlayers(); super.remove(reason); }
    @Override public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide()) {
            entityData.set(HORN_EXPOSURE, 0); entityData.set(HORN_BREAK_START, -1L); entityData.set(PHASE_CHANGE_START, -1L);
        }
        bossEvent.removeAllPlayers(); event("death", "");
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag boss = new CompoundTag();
        boss.putString("encounter_realm", encounterRealm.toString()); boss.putInt("encounter_stage", encounterStage);
        boss.putInt("phase", getBossPhase()); boss.putBoolean("horn_broken", isHornBroken());
        if (lairCenter != null) boss.putLong("home", lairCenter.asLong());
        boss.putLongArray("pillars", lairPillars.stream().mapToLong(BlockPos::asLong).toArray());
        tag.put("thunder_tiger", boss);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        CompoundTag boss = tag.getCompound("thunder_tiger");
        if (boss.contains("encounter_realm", 8)) {
            ResourceLocation savedRealm = ResourceLocation.tryParse(boss.getString("encounter_realm"));
            if (savedRealm != null) {
                com.example.myvillage.stats.StatsRules.bundled().realmKey(savedRealm.toString(), boss.getInt("encounter_stage"));
                encounterRealm = savedRealm; encounterStage = boss.getInt("encounter_stage");
            }
        }
        if (!level().isClientSide()) StatsRuntime.applyVanillaRealm(this, encounterRealm.toString(), encounterStage);
        loadGraceUntil = level().getGameTime() + rules().bounds().loadGraceTicks();
        cancelCurrentMove(rules().bounds().loadGraceTicks());
        entityData.set(BOSS_PHASE, rules().phaseAfter(Mth.clamp(boss.getInt("phase"), 1, 2), getHealth(), getMaxHealth()));
        entityData.set(HORN_BROKEN, boss.getBoolean("horn_broken"));
        entityData.set(HORN_EXPOSURE, 0); entityData.set(HORN_BREAK_START, -1L); entityData.set(PHASE_CHANGE_START, -1L);
        hornDamage = 0; thunderMark = null; previousMovePosition = null;
        lairCenter = boss.contains("home", 4) ? BlockPos.of(boss.getLong("home")) : null;
        long[] stored = boss.getLongArray("pillars");
        List<BlockPos> restored = new ArrayList<>();
        for (int index = 0; index < Math.min(stored.length, rules().horn().maximumPillars()); index++) {
            BlockPos pos = BlockPos.of(stored[index]);
            if (lairCenter != null && pos.closerThan(lairCenter, rules().bounds().homeRadius())) restored.add(pos);
        }
        lairPillars = List.copyOf(restored);
        updateParts();
    }
    @Override protected SoundEvent getAmbientSound() { return ThunderTigerSounds.AMBIENT.get(); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return ThunderTigerSounds.HURT.get(); }
    @Override protected SoundEvent getDeathSound() { return ThunderTigerSounds.DEATH.get(); }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(ThunderTigerSounds.STEP.get(), getSoundVolume(), getVoicePitch()); }
    @Override public float getVoicePitch() { return (float) rules().feedback().ambientPitch(); }
    @Override protected float getSoundVolume() { return (float) rules().feedback().ambientVolume(); }
    private void event(String event, String detail) {
        if (level().isClientSide()) return;
        String move = shownMove().map(m -> m.id().toString()).orElse("none");
        LOG.info("THUNDER_TIGER event={} uuid={} move={} tick={} phase={} hp={} target={}{}", event, getUUID(), move, runningMoveTick(), getBossPhase(), getHealth(), getTarget() == null ? "none" : getTarget().getUUID(), detail);
    }
    @Override public String debugStatus() {
        Vec3 head = headPart.getBoundingBox().getCenter();
        BlockPos home = getLairCenter();
        return super.debugStatus() + String.format(Locale.ROOT, " uuid=%s max=%.1f boss_phase=%d horn_broken=%s horn_exposure=%d horn_damage=%.2f home=%d,%d,%d horn_pos=%.3f,%.3f,%.3f phase_tick=%d horn_break_tick=%d", getUUID(), getMaxHealth(), getBossPhase(), isHornBroken(), getHornExposureTicks(), hornDamage, home.getX(), home.getY(), home.getZ(), head.x, head.y, head.z, getPhaseChangeTick(), getHornBreakTick());
    }
    private final class BossGoal extends Goal {
        private int repath;
        BossGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return getTarget() != null || isMoveRunning() || isPresentationShown() || lairCenter != null && position().distanceToSqr(Vec3.atBottomCenterOf(lairCenter)) > rules().horn().bodyWidth() * rules().horn().bodyWidth(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void stop() { getNavigation().stop(); }
        @Override public void tick() {
            if (isMoveRunning() || isPresentationShown()) { getNavigation().stop(); repath = 0; return; }
            LivingEntity target = getTarget();
            if (target != null && validTarget(target)) {
                getLookControl().setLookAt(target, 30, 30);
                if (tryStartMove(target)) return;
                if (--repath <= 0) { repath = rules().bounds().repathTicks(); getNavigation().moveTo(target, definition().chase().speedModifier()); }
            } else {
                setTarget(null);
                if (lairCenter != null && --repath <= 0) {
                    repath = rules().bounds().repathTicks();
                    getNavigation().moveTo(lairCenter.getX() + 0.5, lairCenter.getY(), lairCenter.getZ() + 0.5, rules().bounds().returnSpeedModifier());
                }
            }
        }
    }
}
