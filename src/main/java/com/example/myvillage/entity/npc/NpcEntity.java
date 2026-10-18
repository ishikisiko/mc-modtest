package com.example.myvillage.entity.npc;

import com.example.myvillage.portrait.NpcColours;
import com.example.myvillage.sim.runtime.life.NpcLifeGoal;
import com.example.myvillage.sim.runtime.life.NpcLifeInteraction;
import com.example.myvillage.sim.runtime.player.SectDialogue;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Humanoid body shared by summoned cultivators and ledger projections. Server AI submits
 * basic sword intents to CombatSessionManager; the shared executor owns timing and damage.
 * A managed ledger body is temporary: identity, equipment and losses belong to WorldSim.
 * Copies of avatar NBT are refused by WorldSimAvatars, and stale leases cannot submit results.
 *
 * <p>Look, colours, dialogue role and an atomic combat snapshot are tracked to clients.
 * The snapshot seeds the current phase for late observers. Appearance assets and grip bones
 * remain owned by tools/npcgen; no client types are loaded by this entity.
 *
 * <p>Residents suspend their life executor during combat. On disengagement they must route
 * back to a workplace or home. Discard/unload checkpoints a body; only a completed die call
 * commits a death, with the original damage attribution.
 */
public abstract class NpcEntity extends PathfinderMob {
    public static final String IDLE_CLIP = "idle";
    public static final String WALK_CLIP = "walk";
    /** The ledger person id of an ordinary (summoned) NPC. */
    public static final int NO_LEDGER_PERSON = -1;
    /**
     * Written only for an avatar, which is never stored in a chunk: if a copy escapes some other way
     * (a structure template, an entity copied by data), it loads as an avatar its manager does not
     * know, and the manager refuses it on joining the level.
     */
    static final String LEDGER_PERSON_TAG = "WorldSimPerson";
    static final float LOOK_DISTANCE = 8.0F;
    /** The look every NPC type has; its files are the type's plain model, clips and texture. */
    public static final String LOOK_DEFAULT = "default";
    /** Saved only when the look is not {@link #LOOK_DEFAULT}. */
    public static final String LOOK_TAG = "Look";
    /** The packed {@link NpcColours}; saved only when set. */
    public static final String COLOURS_TAG = "Colours";
    /** No dialogue role (every summoned NPC, and avatars without a role). */
    public static final String ROLE_NONE = "none";
    /** The sect's steward (守山执事), who receives players at the gate. */
    public static final String ROLE_STEWARD = "steward";
    /** An elder or the sect master. */
    public static final String ROLE_ELDER = "elder";

    private static final EntityDataAccessor<Integer> DATA_LEDGER_PERSON =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_LOOK =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_LEDGER_ROLE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_COLOURS =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<CompoundTag> DATA_COMBAT =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.COMPOUND_TAG);
    static final String COMBAT_INITIALIZED_TAG = "NpcCombatInitialized";
    boolean combatInitialized;
    long combatEpoch = -1;
    long nextCombatDecision, lastThreatTick, nextRecoveryTick, combatLastDamageTick;
    String combatLastAttacker = "";
    net.minecraft.world.phys.Vec3 combatAnchor = net.minecraft.world.phys.Vec3.ZERO;

    private final AnimationState idleAnimationState = new AnimationState();
    private Goal strollGoal;
    /** Transient executor only. Identity, activity, position and progress live in the world ledger. */
    private NpcLifeGoal lifeGoal;
    private Goal lifeDoorGoal;

    protected NpcEntity(EntityType<? extends NpcEntity> entityType, Level level) {
        super(entityType, level);
    }

    /** Speed modifier of the stroll goal; the walk clip's rate does not depend on it. */
    protected abstract double strollSpeedModifier();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEDGER_PERSON, NO_LEDGER_PERSON);
        builder.define(DATA_COMBAT, new CompoundTag());
        builder.define(DATA_LOOK, LOOK_DEFAULT);
        builder.define(DATA_LEDGER_ROLE, ROLE_NONE);
        builder.define(DATA_COLOURS, NpcColours.NONE);
    }

    /** The looks this type can wear, {@link #LOOK_DEFAULT} first. Subclasses with more looks list them. */
    protected List<String> looks() {
        return List.of(LOOK_DEFAULT);
    }

    /** The current look name (synced). */
    public String look() {
        return entityData.get(DATA_LOOK);
    }

    /** Sets the look; null or empty means {@link #LOOK_DEFAULT}. */
    public void setLook(String look) {
        entityData.set(DATA_LOOK, look == null || look.isEmpty() ? LOOK_DEFAULT : look);
    }

    /** The hair and eye colours (synced), or null when the NPC keeps its texture's baked colours. */
    @Nullable
    public NpcColours colours() {
        return NpcColours.unpack(entityData.get(DATA_COLOURS));
    }

    /** The packed form of {@link #colours()}: {@link NpcColours#NONE} when unset. */
    public int packedColours() {
        return entityData.get(DATA_COLOURS);
    }

    /** Sets the hair and eye colours; null clears them. */
    public void setColours(@Nullable NpcColours colours) {
        entityData.set(DATA_COLOURS, colours == null ? NpcColours.NONE : colours.pack());
    }

    /** The avatar's dialogue role (synced, never saved): none, steward or elder. */
    public String ledgerRole() {
        return entityData.get(DATA_LEDGER_ROLE);
    }

    /** Sets the dialogue role; null or empty means {@link #ROLE_NONE}. */
    public void setLedgerRole(String role) {
        entityData.set(DATA_LEDGER_ROLE, role == null || role.isEmpty() ? ROLE_NONE : role);
    }

    @Override
    protected void registerGoals() {
        strollGoal = new WaterAvoidingRandomStrollGoal(this, strollSpeedModifier());
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new NpcCombatGoal(this));
        goalSelector.addGoal(5, strollGoal);
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, LOOK_DISTANCE));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /**
     * Makes this NPC the world simulation's avatar of ledger person {@code personId} (server side,
     * before it is added to the level). Irreversible: an avatar is discarded, never turned back.
     */
    public void becomeLedgerAvatar(int personId) {
        if (personId < 0) {
            throw new IllegalArgumentException("a ledger person id is not negative, got " + personId);
        }
        entityData.set(DATA_LEDGER_PERSON, personId);
        if (strollGoal != null) {
            goalSelector.removeGoal(strollGoal);
        }
        getNavigation().stop();
    }

    /** Installs the manager's current life executor; copied entity NBT never installs this goal. */
    public void enableLedgerLife(long epoch) {
        if (level().isClientSide() || !isLedgerAvatar()) {
            throw new IllegalStateException("life behaviour requires a server ledger avatar");
        }
        // Open decorative shutters are classified as TRAPDOOR by vanilla even when their
        // vertical collision blocks the crossing. Town routes use doors and authored entries.
        setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.TRAPDOOR, -1.0F);
        if (lifeGoal != null) {
            goalSelector.removeGoal(lifeGoal);
        }
        if (lifeDoorGoal != null) {
            goalSelector.removeGoal(lifeDoorGoal);
        }
        // Resident routes need collision-safe stairs, landings and interior corners.
        if (!(navigation instanceof com.example.myvillage.sim.runtime.life.NpcLifeNavigation)) {
            navigation.stop();
            navigation = new com.example.myvillage.sim.runtime.life.NpcLifeNavigation(this, level());
        }
        if (getNavigation() instanceof GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
            lifeDoorGoal = new OpenDoorGoal(this, true);
            goalSelector.addGoal(1, lifeDoorGoal);
        }
        lifeGoal = new NpcLifeGoal(this, epoch);
        goalSelector.addGoal(2, lifeGoal);
    }

    /** Rechecks an active job before ledger work can be paid, including a newly projected body. */
    public void validateLedgerLifeWorkplace() {
        if (lifeGoal != null) lifeGoal.validateWorkplaceBeforeSettlement();
    }

    public com.google.gson.JsonObject lifeNavigationSnapshot() {
        return lifeGoal == null ? new com.google.gson.JsonObject() : lifeGoal.navigationSnapshot();
    }

    /** The ledger person this NPC projects, or {@link #NO_LEDGER_PERSON}. */
    public int ledgerPersonId() {
        return entityData.get(DATA_LEDGER_PERSON);
    }

    public boolean isLedgerAvatar() {
        return ledgerPersonId() >= 0;
    }

    @Override
    public boolean shouldBeSaved() {
        return !isLedgerAvatar() && super.shouldBeSaved();
    }

    @Override
    public boolean isAttackable() {
        return (!isLedgerAvatar() || level().isClientSide() || NpcCombatRuntime.managed(this)) && super.isAttackable();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isLedgerAvatar() && !NpcCombatRuntime.managed(this)) return false;
        if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker
                && attacker != this && !com.example.myvillage.combat.runtime.CombatActors.mayHarm(attacker, this)) return false;
        boolean accepted = super.hurt(source, amount);
        if (accepted && !level().isClientSide()) NpcCombatRuntime.attacked(this, source);
        return accepted;
    }

    @Override public void setHealth(float value) {
        super.setHealth(value);
        if (combatEpoch > 0 && !level().isClientSide()) NpcCombatRuntime.checkpoint(this);
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (dead && !level().isClientSide()) NpcCombatRuntime.died(this, source);
    }

    @Override public void remove(RemovalReason reason) {
        if (!level().isClientSide()) NpcCombatRuntime.leaving(this);
        super.remove(reason);
    }

    public CompoundTag combatState() { return entityData.get(DATA_COMBAT); }
    public boolean combatEngaged() { return combatState().getBoolean("engaged"); }
    public void setCombatEngaged(boolean engaged) {
        if (combatEngaged() == engaged) return;
        CompoundTag state = combatState().copy(); state.putBoolean("engaged", engaged); entityData.set(DATA_COMBAT, state);
    }
    public void clearCombatAction() {
        if (!combatState().contains("move")) return;
        CompoundTag state = new CompoundTag(); state.putBoolean("engaged", combatEngaged());
        entityData.set(DATA_COMBAT, state);
    }
    public void syncCombat(com.example.myvillage.combat.session.CombatSession session, long tick, boolean frozen) {
        if (session == null || !session.hasActiveAction()) { clearCombatAction(); return; }
        CompoundTag old = combatState();
        if (old.getLong("revision") == session.revision() && old.getLong("start") == session.actionStartTick()
                && old.getBoolean("frozen") == frozen && old.contains("move")) return;
        CompoundTag state = new CompoundTag();
        state.putBoolean("engaged", combatEngaged()); state.putString("move", session.currentMove().id().toString());
        state.putLong("revision", session.revision()); state.putLong("start", session.actionStartTick());
        state.putLong("stamp", tick); state.putInt("elapsed", session.actionTick(tick));
        state.putFloat("yaw", session.facingYaw()); state.putBoolean("frozen", frozen);
        entityData.set(DATA_COMBAT, state);
    }

    @Override
    public boolean fireImmune() {
        return super.fireImmune();
    }

    @Override
    public boolean isPushable() {
        return super.isPushable();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (combatEngaged()) {
            if (!level().isClientSide()) player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("entity.myvillage.cultivator.in_combat"), true);
            return InteractionResult.CONSUME;
        }
        if (!isLedgerAvatar()) {
            return super.mobInteract(player, hand);
        }
        if (player instanceof ServerPlayer merchantPlayer
                && com.example.myvillage.sim.runtime.commerce.NpcCommerce.open(merchantPlayer, this))
            return InteractionResult.CONSUME;
        boolean lifeInteraction = player instanceof ServerPlayer serverPlayer
                && NpcLifeInteraction.interact(serverPlayer, this);
        if (ROLE_NONE.equals(ledgerRole())) {
            return lifeInteraction ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            SectDialogue.open(serverPlayer, this); // checks everything itself; a refusal is a chat line or nothing
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(COMBAT_INITIALIZED_TAG, combatInitialized);
        // Read-only /data diagnostics. Never read back as authority when an entity is copied.
        CompoundTag diagnostic = combatState().copy();
        diagnostic.putLong("epoch", combatEpoch);
        diagnostic.putBoolean("reaction_frozen", com.example.myvillage.combat.runtime.CombatReactionService.isFrozen(this));
        diagnostic.putBoolean("reaction_stunned", com.example.myvillage.combat.runtime.CombatReactionService.isStunned(this));
        diagnostic.putLong("recovery_until", com.example.myvillage.combat.session.CombatSessionManager.recoveryUntil(this));
        if (getTarget() != null) diagnostic.putUUID("target", getTarget().getUUID());
        tag.put("NpcCombatView", diagnostic);
        if (isLedgerAvatar()) {
            tag.putInt(LEDGER_PERSON_TAG, ledgerPersonId());
        }
        if (!LOOK_DEFAULT.equals(look())) {
            tag.putString(LOOK_TAG, look());
        }
        if (colours() != null) {
            tag.putInt(COLOURS_TAG, packedColours());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        combatInitialized = tag.getBoolean(COMBAT_INITIALIZED_TAG);
        if (tag.contains(LEDGER_PERSON_TAG, Tag.TAG_INT) && tag.getInt(LEDGER_PERSON_TAG) >= 0) {
            becomeLedgerAvatar(tag.getInt(LEDGER_PERSON_TAG));
        }
        if (tag.contains(LOOK_TAG, Tag.TAG_STRING) && looks().contains(tag.getString(LOOK_TAG))) {
            setLook(tag.getString(LOOK_TAG));
        }
        if (tag.contains(COLOURS_TAG, Tag.TAG_INT)) {
            setColours(NpcColours.unpack(tag.getInt(COLOURS_TAG))); // an invalid value clears them
        }
    }

    @Override
    @SuppressWarnings("deprecation") // NeoForge deprecates calling it, not overriding it
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        if (!level.isClientSide()) NpcCombatRuntime.equipInitial(this);
        if (spawnType == MobSpawnType.SPAWN_EGG && LOOK_DEFAULT.equals(look())) {
            List<String> looks = looks();
            setLook(looks.get(level.getRandom().nextInt(looks.size())));
        }
        return data;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            idleAnimationState.startIfStopped(tickCount);
        }
    }

    public AnimationState idleAnimationState() {
        return idleAnimationState;
    }
}
