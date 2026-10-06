package com.example.myvillage.entity.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A humanoid NPC drawn from its own model, clip and texture files (see {@code NpcRenderer}).
 *
 * <p>This base only gives the figure a body in the world: it floats, strolls, and looks at nearby
 * players. It has no target, attack, trade or dialogue; whether an NPC is a friend or a foe is
 * decided by what a subclass adds later. It never despawns by distance.
 *
 * <p><b>Ledger avatar.</b> The world simulation may project a person of its ledger (命簿) as an NPC
 * ({@link #becomeLedgerAvatar}). Such an avatar belongs to the simulation, which spawns, renames and
 * withdraws it: it is never saved with its chunk, cannot be attacked or hurt (except by what bypasses
 * invulnerability, such as {@code /kill} or the void), does not burn, is not pushed, does not stroll
 * (it still looks at players and around), and does nothing when a player interacts with it. Its
 * ledger person id is synced to clients. An NPC summoned by a command or a spawn egg has id
 * {@link #NO_LEDGER_PERSON} and behaves exactly as described above.
 *
 * <p>Client: {@link #idleAnimationState()} runs from the first client tick; the walk clip is driven
 * by vanilla limb swing.
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

    private static final EntityDataAccessor<Integer> DATA_LEDGER_PERSON =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);

    private final AnimationState idleAnimationState = new AnimationState();
    private Goal strollGoal;

    protected NpcEntity(EntityType<? extends NpcEntity> entityType, Level level) {
        super(entityType, level);
    }

    /** Speed modifier of the stroll goal; the walk clip's rate does not depend on it. */
    protected abstract double strollSpeedModifier();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEDGER_PERSON, NO_LEDGER_PERSON);
    }

    @Override
    protected void registerGoals() {
        strollGoal = new WaterAvoidingRandomStrollGoal(this, strollSpeedModifier());
        goalSelector.addGoal(0, new FloatGoal(this));
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
        return !isLedgerAvatar() && super.isAttackable();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isLedgerAvatar() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean fireImmune() {
        return isLedgerAvatar() || super.fireImmune();
    }

    @Override
    public boolean isPushable() {
        return !isLedgerAvatar() && super.isPushable();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return isLedgerAvatar() ? InteractionResult.PASS : super.mobInteract(player, hand);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (isLedgerAvatar()) {
            tag.putInt(LEDGER_PERSON_TAG, ledgerPersonId());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(LEDGER_PERSON_TAG, Tag.TAG_INT) && tag.getInt(LEDGER_PERSON_TAG) >= 0) {
            becomeLedgerAvatar(tag.getInt(LEDGER_PERSON_TAG));
        }
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
