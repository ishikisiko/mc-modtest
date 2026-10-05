package com.example.myvillage.entity.beast;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.runtime.CombatReactionService;
import com.example.myvillage.combat.runtime.StaggerResistant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A hostile beast that fights with data-driven attack moves ({@link BeastDefinition}).
 *
 * <p>Server: {@link BeastAttackGoal} chases the target and asks {@link #tryStartMove} to begin a
 * move; the move itself runs here, one move tick per AI step, so a hit-stop freeze (which cancels
 * the whole entity tick) pauses it. A move stops navigation, turns toward the target until
 * {@code turn_lock_tick}, then holds the locked yaw and aim point, applies its lunge, tests its hit
 * box on the active ticks (each victim once) and ends at {@code total_ticks}, starting its cooldown.
 * During {@code immune_ticks} the beast {@linkplain #resistsStagger() resists stagger} and carries a
 * transient knockback-resistance modifier; a combat stun outside that window cancels the move.
 *
 * <p>Client: the current move (index + 1, 0 = none) and a staggered flag are synced entity data;
 * vanilla {@link AnimationState}s, keyed by clip name in {@link #clipAnimationStates()}, follow them.
 * Move state is transient and never saved.
 */
public abstract class BeastEntity extends Monster implements StaggerResistant {
    public static final String IDLE_CLIP = "idle";
    public static final String WALK_CLIP = "walk";
    public static final String RUN_CLIP = "run";
    public static final ResourceLocation POISE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "beast_poise");
    /** How long the stagger state shows once a stun begins (the clip is about half a second). */
    static final int STAGGER_SHOW_TICKS = 10;
    /** Fastest the beast turns toward its target before the aim locks. */
    static final float TURN_DEGREES_PER_TICK = 30.0F;
    static final int NO_MOVE = -1;
    private static final Logger LOGGER = LoggerFactory.getLogger(BeastEntity.class);
    /** {@code /myvillage beast debug on}: log move starts, ends, staggers and hits taken (server). */
    private static volatile boolean debugLog;

    private static final EntityDataAccessor<Integer> DATA_MOVE =
            SynchedEntityData.defineId(BeastEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_STAGGERED =
            SynchedEntityData.defineId(BeastEntity.class, EntityDataSerializers.BOOLEAN);
    /** The move tick the server last ran (-1 when none), so client clips can follow it. */
    private static final EntityDataAccessor<Integer> DATA_MOVE_TICK =
            SynchedEntityData.defineId(BeastEntity.class, EntityDataSerializers.INT);
    /** A client move clip that has drifted this many ticks from the server's move tick is re-anchored. */
    static final int CLIP_RESYNC_TICKS = 2;
    /**
     * Client ticks by which a running move clip trails the newest server move tick, so that the clip
     * shows the tick whose position is being drawn. The packet for server tick {@code s} is handled
     * before the next client tick; that tick (one {@code animationTicks++}) takes the body to
     * {@code s} from {@code s - 1} (see {@link #lerpTo}), and frames in between draw
     * {@code s - 1 + partialTick}.
     */
    static final int MOVE_CLIP_DELAY_TICKS = 2;

    // Assigned lazily: Mob's constructor calls registerGoals before this class's constructor body.
    private BeastDefinition cachedDefinition;

    private final AnimationState idleAnimationState = new AnimationState();
    private final AnimationState staggerAnimationState = new AnimationState();
    private final AnimationState[] moveAnimationStates;
    private final Map<String, AnimationState> clipAnimationStates;
    // Client clip clock: counts this entity's own client ticks, so a tick skipped for a hit-stop
    // freeze (EntityTickEvent cancelled, tickCount still advancing) pauses every clip with it.
    private int animationTicks;
    private int animationTickedAt = Integer.MIN_VALUE;
    private int moveClipStartedAt;

    private final long[] readyAt;
    private final Set<Integer> victims = new HashSet<>();
    private long beastTicks;
    private long lastMoveEnd;
    private int moveIndex = NO_MOVE;
    private int moveTick = -1;
    private boolean aimLocked;
    private float lockedYaw;
    private Vec3 aimPoint = Vec3.ZERO;
    private int forcedMove = NO_MOVE;
    private int staggerShowTicks;
    private boolean wasStunned;

    protected BeastEntity(EntityType<? extends BeastEntity> entityType, Level level) {
        super(entityType, level);
        BeastDefinition definition = definition();
        int moves = definition.moves().size();
        this.moveAnimationStates = new AnimationState[moves];
        Map<String, AnimationState> clips = new LinkedHashMap<>();
        clips.put(IDLE_CLIP, idleAnimationState);
        for (int index = 0; index < moves; index++) {
            moveAnimationStates[index] = new AnimationState();
            clips.put(definition.move(index).animation(), moveAnimationStates[index]);
        }
        clips.put(definition.staggerAnimation(), staggerAnimationState);
        this.clipAnimationStates = Collections.unmodifiableMap(clips);
        this.readyAt = new long[moves];
        this.lastMoveEnd = -definition.chase().moveGapTicks();
    }

    /** The entity id whose bundled data this beast runs. */
    protected abstract ResourceLocation beastId();

    public final BeastDefinition definition() {
        BeastDefinition current = cachedDefinition;
        if (current == null) {
            current = BeastDefinitions.bundled().require(beastId());
            cachedDefinition = current;
        }
        return current;
    }

    /** Registered attributes from a beast's data file. */
    public static AttributeSupplier.Builder createAttributes(BeastDefinition definition) {
        BeastDefinition.Attributes values = definition.attributes();
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, values.maxHealth())
                .add(Attributes.ATTACK_DAMAGE, values.attackDamage())
                .add(Attributes.MOVEMENT_SPEED, values.movementSpeed())
                .add(Attributes.FOLLOW_RANGE, values.followRange())
                .add(Attributes.ARMOR, values.armor())
                .add(Attributes.KNOCKBACK_RESISTANCE, values.knockbackResistance())
                .add(Attributes.STEP_HEIGHT, values.stepHeight());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new BeastAttackGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MOVE, 0);
        builder.define(DATA_STAGGERED, false);
        builder.define(DATA_MOVE_TICK, -1);
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new BeastBodyRotationControl(this);
    }

    /** Summoned and egg-spawned beasts stay; there is no natural spawning to replace them. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ---- Client presentation -------------------------------------------------------------

    /**
     * Every clip this beast drives through an {@link AnimationState}, keyed by clip name: {@code idle}
     * (always running on the client), one entry per move clip, and the stagger clip. A client model
     * plays each state that {@link AnimationState#isStarted() is started} with the clip of that
     * name; {@code walk}/{@code run} are not here, they follow {@link #locomotionClip()}.
     */
    public Map<String, AnimationState> clipAnimationStates() {
        return clipAnimationStates;
    }

    public Optional<AnimationState> clipAnimationState(String clip) {
        return Optional.ofNullable(clipAnimationStates.get(clip));
    }

    /** The locomotion clip for {@code animateWalk}: {@code run} while aggressive (chasing), else {@code walk}. */
    public String locomotionClip() {
        return isAggressive() ? RUN_CLIP : WALK_CLIP;
    }

    /** Synced move value: 0 = none, otherwise the move index + 1. */
    public int syncedMoveValue() {
        return entityData.get(DATA_MOVE);
    }

    /** The move the beast is showing (synced, so valid on both sides). */
    public Optional<BeastMoveDefinition> shownMove() {
        int value = syncedMoveValue();
        List<BeastMoveDefinition> moves = definition().moves();
        return value > 0 && value <= moves.size() ? Optional.of(moves.get(value - 1)) : Optional.empty();
    }

    public boolean isMoveShown() {
        return syncedMoveValue() != 0;
    }

    public boolean isStaggerShown() {
        return entityData.get(DATA_STAGGERED);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // LivingEntity's constructor sets synced health before this class's fields exist.
        if (moveAnimationStates == null || !level().isClientSide()) {
            return;
        }
        if (DATA_MOVE.equals(key)) {
            int value = syncedMoveValue();
            for (int index = 0; index < moveAnimationStates.length; index++) {
                if (index == value - 1) {
                    // This packet carries move tick 0; see MOVE_CLIP_DELAY_TICKS.
                    moveClipStartedAt = animationTicks + MOVE_CLIP_DELAY_TICKS;
                    moveAnimationStates[index].start(moveClipStartedAt);
                } else {
                    moveAnimationStates[index].stop();
                }
            }
        } else if (DATA_MOVE_TICK.equals(key)) {
            anchorMoveClip(false);
        } else if (DATA_STAGGERED.equals(key)) {
            if (isStaggerShown()) {
                staggerAnimationState.start(animationTicks);
            } else {
                staggerAnimationState.stop();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            animationTicks++;
            animationTickedAt = tickCount;
            idleAnimationState.startIfStopped(animationTicks);
        }
    }

    /**
     * Client, once per rendered frame: the time, in ticks, at which {@link #clipAnimationStates()}
     * are sampled (pass it where vanilla passes {@code ageInTicks}). It follows the entity's own
     * client ticks: while a hit-stop skips them it holds where the last tick was heading. While
     * {@code /tick freeze} holds the entity it shows the server's current move tick exactly
     * (re-anchoring the move clip on every frame), so a stepped still is the pose of that tick.
     */
    public float clipTime(float partialTick) {
        if (level().tickRateManager().isEntityFrozen(this)) {
            // A frozen entity is drawn at its current position (partial tick 1). A position packet
            // that arrived during the last stepped tick may still be pending: finish it, so the
            // body stands where the server has it on the tick the clip shows.
            if (isMoveShown() && lerpSteps > 0) {
                lerpPositionAndRotationStep(1, lerpX, lerpY, lerpZ, lerpYRot, lerpXRot);
                lerpSteps = 0;
            }
            anchorMoveClip(true);
            return animationTicks;
        }
        if (animationTickedAt != tickCount) {
            return animationTicks + 1.0F;
        }
        return animationTicks + Math.max(0.0F, Math.min(1.0F, partialTick));
    }

    /**
     * Client: lines the running move clip up with the server's move tick. {@code exact} (a frozen
     * still) shows that tick itself, re-anchored every frame. Otherwise the clip trails the newest
     * tick by {@link #MOVE_CLIP_DELAY_TICKS}, like the drawn position, and is re-anchored only once
     * it has drifted {@link #CLIP_RESYNC_TICKS} from that (a server hit-stop the client did not
     * see, a late packet).
     */
    private void anchorMoveClip(boolean exact) {
        int value = syncedMoveValue();
        int serverTick = entityData.get(DATA_MOVE_TICK);
        if (value <= 0 || value > moveAnimationStates.length || serverTick < 0) {
            return;
        }
        int wanted = exact ? serverTick : serverTick - MOVE_CLIP_DELAY_TICKS;
        int elapsed = animationTicks - moveClipStartedAt;
        if (exact || Math.abs(elapsed - wanted) >= CLIP_RESYNC_TICKS) {
            moveClipStartedAt = animationTicks - wanted;
            moveAnimationStates[value - 1].start(moveClipStartedAt);
        }
    }

    /**
     * Client: while a move is shown the body follows the server's path tick by tick. Vanilla
     * spreads each position packet over three client ticks (each tick closes a third of the gap);
     * a move sends one every tick, so a lunge was drawn about two ticks of travel behind the
     * server, its arc flattened and its landing pose hanging in the air. One step reaches each
     * packet's position on the next client tick, and a frozen beast (a {@code /tick freeze}
     * still) takes it at once. Outside moves (walking, chasing) vanilla smoothing stays. One step
     * shows any unevenness in packet arrival; moves are short and the beast holds still through
     * most of them, so that is accepted for the lunge and landing to read right.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (!level().isClientSide() || !isMoveShown()) {
            super.lerpTo(x, y, z, yRot, xRot, steps);
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, 1);
        if (level().tickRateManager().isEntityFrozen(this)) {
            lerpPositionAndRotationStep(1, x, y, z, yRot, xRot);
            lerpSteps = 0;
        }
    }

    // ---- Server move runtime --------------------------------------------------------------

    @Override
    public void aiStep() {
        if (!level().isClientSide()) {
            beastTicks++;
        }
        super.aiStep();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = liveTarget();
        setAggressive(target != null);

        boolean stunned = CombatReactionService.isStaggered(this);
        if (stunned) {
            if (!wasStunned) {
                staggerShowTicks = STAGGER_SHOW_TICKS;
            }
            if (moveIndex != NO_MOVE) {
                debug("staggered: cancelled {} at move tick {}", definition().move(moveIndex).id(), moveTick);
                endMove(definition().chase().cancelledCooldownTicks());
            } else if (!wasStunned) {
                debug("staggered: no move running");
            }
        }
        wasStunned = stunned;
        if (staggerShowTicks > 0) {
            staggerShowTicks--;
        }
        entityData.set(DATA_STAGGERED, stunned || staggerShowTicks > 0);

        if (forcedMove != NO_MOVE && !stunned) {
            int index = forcedMove;
            forcedMove = NO_MOVE;
            startMove(index);
        }
        if (moveIndex != NO_MOVE) {
            tickMove(target);
        }
        updatePoise();
    }

    @Nullable
    private LivingEntity liveTarget() {
        LivingEntity target = getTarget();
        return target != null && target.isAlive() ? target : null;
    }

    public boolean isMoveRunning() {
        return moveIndex != NO_MOVE || forcedMove != NO_MOVE;
    }

    /**
     * Starts a move against {@code target} when one is ready: on the ground, not stunned, with
     * line of sight, and per {@link BeastMoveSelector}. Called by the attack goal. A move may start
     * on the first tick after a stun ends, while the flinch clip is still showing (the move cuts
     * it short): waiting out the clip as well lets a player who keeps swinging re-stun the beast
     * before it can ever answer.
     */
    public boolean tryStartMove(LivingEntity target) {
        if (isMoveRunning() || !onGround() || CombatReactionService.isStaggered(this)
                || !getSensing().hasLineOfSight(target)) {
            return false;
        }
        BeastDefinition definition = definition();
        double distance = BeastGeometry.horizontalDistance(getX(), getZ(), target.getX(), target.getZ());
        int index = BeastMoveSelector.select(
                definition.moves(), distance, beastTicks, readyAt, lastMoveEnd,
                definition.chase().moveGapTicks(), random::nextInt);
        if (index == BeastMoveSelector.NONE) {
            return false;
        }
        startMove(index);
        return true;
    }

    /**
     * Debug: starts {@code moveId} on the next AI step, ignoring range, cooldown and gap; a running
     * move is dropped without a cooldown. Returns false when this beast has no such move.
     */
    public boolean forceMove(ResourceLocation moveId) {
        OptionalInt index = definition().moveIndex(moveId);
        if (index.isEmpty()) {
            return false;
        }
        if (moveIndex != NO_MOVE) {
            clearMove();
        }
        forcedMove = index.getAsInt();
        return true;
    }

    private void startMove(int index) {
        staggerShowTicks = 0;
        entityData.set(DATA_STAGGERED, false);
        moveIndex = index;
        moveTick = -1;
        aimLocked = false;
        aimPoint = position();
        victims.clear();
        getNavigation().stop();
        // Forced so that a restarted move is re-sent and its clip restarts on the client.
        entityData.set(DATA_MOVE, index + 1, true);
        debug("start {}", definition().move(index).id());
        playWindupSound(definition().move(index));
    }

    private void tickMove(@Nullable LivingEntity target) {
        BeastMoveDefinition move = definition().move(moveIndex);
        moveTick++;
        entityData.set(DATA_MOVE_TICK, moveTick);
        // Hold still: no path, and a move target at our own feet makes MoveControl zero the input
        // without turning the body.
        getNavigation().stop();
        getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0);
        if (move.turnsAt(moveTick) && target != null) {
            turnToward(target.getX(), target.getZ());
        }
        if (move.locksAt(moveTick)) {
            lockAim(move, target);
        }
        if (aimLocked) {
            faceYaw(lockedYaw);
        }
        if (move.lungesAt(moveTick)) {
            lunge(move);
        }
        if (move.activeAt(moveTick)) {
            strike(move, target);
        }
        if (moveTick + 1 >= move.totalTicks()) {
            endMove(move.cooldownTicks());
        }
    }

    private void turnToward(double x, double z) {
        float wanted = BeastGeometry.yawToward(x - getX(), z - getZ());
        faceYaw(Mth.approachDegrees(getYRot(), wanted, TURN_DEGREES_PER_TICK));
    }

    private void faceYaw(float yaw) {
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    private void lockAim(BeastMoveDefinition move, @Nullable LivingEntity target) {
        lockedYaw = getYRot();
        if (target != null) {
            aimPoint = target.position();
        } else {
            Vec3 forward = BeastGeometry.forward(lockedYaw);
            aimPoint = position().add(forward.scale(move.useRange().middle()));
        }
        aimLocked = true;
    }

    private void lunge(BeastMoveDefinition move) {
        BeastMoveDefinition.Lunge lunge = move.lunge();
        Vec3 forward = BeastGeometry.forward(lockedYaw);
        double ahead = BeastGeometry.forwardDistance(getX(), getZ(), lockedYaw, aimPoint.x, aimPoint.z);
        // Come to rest with the aim point in the middle of the hit box's reach.
        double speed = lunge.forwardSpeed(ahead - move.hit().standoff());
        double vertical = lunge.up() > 0.0 ? lunge.up() : getDeltaMovement().y;
        setDeltaMovement(forward.x * speed, vertical, forward.z * speed);
        hasImpulse = true;
        hurtMarked = true;
    }

    private void strike(BeastMoveDefinition move, @Nullable LivingEntity target) {
        if (victims.size() >= move.maximumTargets()) {
            return;
        }
        BeastMoveDefinition.HitBox hit = move.hit();
        double x = getX();
        double y = getY();
        double z = getZ();
        AABB bounds = BeastGeometry.hitBounds(hit, x, y, z, lockedYaw);
        List<LivingEntity> found = level().getEntitiesOfClass(LivingEntity.class, bounds, candidate ->
                candidate != this
                        && !victims.contains(candidate.getId())
                        && isVictim(candidate, target)
                        && BeastGeometry.hitOverlaps(hit, x, y, z, lockedYaw, candidate.getBoundingBox()));
        found.sort(Comparator.comparingDouble(this::distanceToSqr));
        for (LivingEntity victim : found) {
            if (victims.size() >= move.maximumTargets()) {
                break;
            }
            victims.add(victim.getId());
            hitVictim(move, victim);
        }
    }

    /** The current target, any attackable player, and any mob that is fighting this beast. */
    private boolean isVictim(LivingEntity candidate, @Nullable LivingEntity target) {
        if (!candidate.isAlive() || !candidate.isAttackable() || isAlliedTo(candidate) || !canAttack(candidate)) {
            return false;
        }
        return candidate == target
                || candidate instanceof Player
                || candidate instanceof Mob mob && mob.getTarget() == this;
    }

    /** A normal mob attack (shields, armour and enchantments apply) with the move's own knockback. */
    private void hitVictim(BeastMoveDefinition move, LivingEntity victim) {
        DamageSource source = damageSources().mobAttack(this);
        float damage = (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * move.damageMultiplier());
        ServerLevel serverLevel = (ServerLevel) level();
        damage = EnchantmentHelper.modifyDamage(serverLevel, getWeaponItem(), victim, source, damage);
        Vec3 before = victim.getDeltaMovement();
        float health = victim.getHealth();
        boolean hurt = victim.hurt(source, damage);
        debug("hit {} with {} at move tick {}: damage {} accepted={} hp {} -> {}", EntityType.getKey(victim.getType()),
                move.id(), moveTick, damage, hurt, health, victim.getHealth());
        if (!hurt) {
            return;
        }
        knockBack(victim, move.knockback(), before);
        EnchantmentHelper.doPostAttackEffects(serverLevel, victim, source);
        setLastHurtMob(victim);
    }

    /**
     * Replaces vanilla's hurt knockback (0.4 away from the attacker plus a hop) with the move's push
     * along the locked yaw, scaled by the victim's knockback resistance.
     */
    private void knockBack(LivingEntity victim, BeastMoveDefinition.Knockback knockback, Vec3 before) {
        double scale = 1.0 - Mth.clamp(victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        if (scale <= 0.0) {
            return;
        }
        Vec3 forward = BeastGeometry.forward(lockedYaw);
        double horizontal = knockback.strength() * scale;
        double lift = knockback.lift() * scale;
        victim.setDeltaMovement(
                forward.x * horizontal,
                lift > 0.0 ? Math.max(before.y, lift) : before.y,
                forward.z * horizontal);
        victim.hasImpulse = true;
        victim.hurtMarked = true;
    }

    private void endMove(int cooldownTicks) {
        debug("end {} at move tick {}, cooldown {}", definition().move(moveIndex).id(), moveTick, cooldownTicks);
        readyAt[moveIndex] = beastTicks + cooldownTicks;
        lastMoveEnd = beastTicks;
        clearMove();
    }

    private void clearMove() {
        moveIndex = NO_MOVE;
        moveTick = -1;
        aimLocked = false;
        victims.clear();
        entityData.set(DATA_MOVE, 0);
        entityData.set(DATA_MOVE_TICK, -1);
        updatePoise();
    }

    /** Server: true while a move runs and its last executed tick is inside {@code immune_ticks}. */
    @Override
    public boolean resistsStagger() {
        return !level().isClientSide()
                && moveIndex != NO_MOVE
                && definition().move(moveIndex).immuneAt(moveTick);
    }

    /** Vanilla knockback must not push the beast out of a committed move either. */
    private void updatePoise() {
        AttributeInstance resistance = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (resistance == null) {
            return;
        }
        boolean poised = resistsStagger();
        if (poised && !resistance.hasModifier(POISE_MODIFIER_ID)) {
            resistance.addTransientModifier(new AttributeModifier(
                    POISE_MODIFIER_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
        } else if (!poised && resistance.hasModifier(POISE_MODIFIER_ID)) {
            resistance.removeModifier(POISE_MODIFIER_ID);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!debugLog || level().isClientSide()) {
            return super.hurt(source, amount);
        }
        float before = getHealth();
        boolean resists = resistsStagger();
        String move = moveIndex == NO_MOVE ? "none" : definition().move(moveIndex).id().toString();
        // Read before the hurt: a killing blow clears the move.
        int tick = moveTick;
        boolean hurt = super.hurt(source, amount);
        debug("hurt by {} ({}) amount={} accepted={} hp {} -> {} move={} tick={} resists={}",
                source.getEntity() == null ? "-" : EntityType.getKey(source.getEntity().getType()),
                source.getMsgId(), amount, hurt, before, getHealth(), move, tick, resists);
        return hurt;
    }

    public static void setDebugLog(boolean enabled) {
        debugLog = enabled;
    }

    public static boolean debugLog() {
        return debugLog;
    }

    private void debug(String format, Object... arguments) {
        if (debugLog && !level().isClientSide()) {
            LOGGER.info("BEAST_DEBUG #{} t={} " + format, prepend(getId(), beastTicks, arguments));
        }
    }

    private static Object[] prepend(Object first, Object second, Object[] rest) {
        Object[] all = new Object[rest.length + 2];
        all[0] = first;
        all[1] = second;
        System.arraycopy(rest, 0, all, 2, rest.length);
        return all;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide()) {
            forcedMove = NO_MOVE;
            if (moveIndex != NO_MOVE) {
                clearMove();
            }
        }
    }

    /** Telegraph sound at the start of a move's wind-up. */
    protected void playWindupSound(BeastMoveDefinition move) {
    }

    /** One line of server state for {@code /myvillage beast status}. */
    public String debugStatus() {
        BeastDefinition definition = definition();
        StringBuilder line = new StringBuilder();
        line.append(EntityType.getKey(getType()))
                .append(" #").append(getId())
                .append(String.format(Locale.ROOT, " pos=(%.3f, %.3f, %.3f) vel=(%.3f, %.3f, %.3f) yaw=%.1f",
                        getX(), getY(), getZ(),
                        getDeltaMovement().x, getDeltaMovement().y, getDeltaMovement().z, getYRot()))
                .append(" ground=").append(onGround())
                .append(" hp=").append(String.format(Locale.ROOT, "%.1f", getHealth()))
                .append(" t=").append(beastTicks)
                .append(" move=").append(moveIndex == NO_MOVE ? "none" : definition.move(moveIndex).id().toString())
                .append(" tick=").append(moveTick)
                .append(" phase=").append(moveIndex == NO_MOVE ? "-" : definition.move(moveIndex).phase(moveTick).name())
                .append(" synced=").append(syncedMoveValue())
                .append(" staggered=").append(isStaggerShown())
                .append(" resists=").append(resistsStagger())
                .append(" aggressive=").append(isAggressive())
                .append(" victims=").append(victims.size());
        if (aimLocked) {
            line.append(String.format(Locale.ROOT, " aim=(%.2f, %.2f) lockedYaw=%.1f", aimPoint.x, aimPoint.z, lockedYaw));
        }
        LivingEntity target = getTarget();
        line.append(" target=").append(target == null ? "none" : EntityType.getKey(target.getType()) + "#" + target.getId());
        if (target != null) {
            line.append(String.format(Locale.ROOT, " dist=%.2f",
                    BeastGeometry.horizontalDistance(getX(), getZ(), target.getX(), target.getZ())));
        }
        line.append(" cooldowns=").append(Arrays.toString(Arrays.stream(readyAt).map(ready -> Math.max(0L, ready - beastTicks)).toArray()));
        return line.toString();
    }
}
