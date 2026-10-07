package com.example.myvillage.entity.npc;

import com.example.myvillage.portrait.NpcColours;
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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

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
 * (it still looks at players and around), and does nothing when a player interacts with it unless
 * it has a dialogue role (below), when the server opens the sect dialogue ({@link SectDialogue}). Its
 * ledger person id is synced to clients. An NPC summoned by a command or a spawn egg has id
 * {@link #NO_LEDGER_PERSON} and behaves exactly as described above.
 *
 * <p><b>Look (外观).</b> One entity type can wear several looks (model, clips and texture sets),
 * named by {@link #looks()} with {@link #LOOK_DEFAULT} first. The look is synced to clients
 * ({@link #look()}), which pick the renderer files by it, and saved as {@value #LOOK_TAG} only
 * when it is not the default ({@code /summon myvillage:cultivator ~ ~ ~ {Look:"f_novice"}}); a
 * saved name the type does not list is ignored. An NPC from a spawn egg draws a random look from
 * {@link #looks()} (the default included); an avatar's look is set by the world simulation.
 *
 * <p><b>Colours (发色瞳色).</b> An avatar's hair and eye colours follow its person's portrait
 * ({@link NpcColours}, synced as one int): the client recolours the look's texture with them. They
 * are set by the world simulation when it spawns or reconciles the avatar, and saved as
 * {@value #COLOURS_TAG} (the packed int) when set, so a summoned NPC can wear them too
 * ({@code /summon myvillage:cultivator ~ ~ ~ {Colours:68}}); an NPC without them keeps the colours
 * baked into its texture.
 *
 * <p><b>Ledger role (命簿角色).</b> An avatar may carry a dialogue role ({@link #ledgerRole()}:
 * {@link #ROLE_NONE}, {@link #ROLE_STEWARD} or {@link #ROLE_ELDER}), synced to clients and set by
 * the world simulation whenever it spawns or reconciles the avatar. It is never saved; a summoned
 * NPC keeps {@link #ROLE_NONE}.
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
        if (!isLedgerAvatar()) {
            return super.mobInteract(player, hand);
        }
        if (ROLE_NONE.equals(ledgerRole())) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            SectDialogue.open(serverPlayer, this); // checks everything itself; a refusal is a chat line or nothing
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
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
