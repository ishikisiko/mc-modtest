package com.example.myvillage.entity.beast;

import com.example.myvillage.wilderness.BeastBirth;
import com.example.myvillage.worldplan.FieldBiomeRules.Kind;
import com.example.myvillage.worldplan.wonder.FieldArenaEncounter;
import com.example.myvillage.worldplan.wonder.FieldEncounterSites.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Optional;

public final class FieldEncounterBoss extends BeastEntity {
    public static final String DRAGON = "tide_dragon";
    public static final String APE = "stele_ape";
    private static final EntityDataAccessor<Boolean> EXPOSED =
            SynchedEntityData.defineId(FieldEncounterBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STELE_SET =
            SynchedEntityData.defineId(FieldEncounterBoss.class, EntityDataSerializers.BOOLEAN);

    private final FieldBossPart[] parts;
    private final ServerBossEvent healthBar;
    private final Set<ServerPlayer> observers = new HashSet<>();
    private final AnimationState hurt = new AnimationState(), death = new AnimationState();
    private final Map<String, AnimationState> clips;
    private BlockPos fieldHome;
    private int lastHurt;

    public FieldEncounterBoss(EntityType<? extends FieldEncounterBoss> type, Level level) {
        super(type, level);
        var rule = FieldEncounterRules.bundled().beast(beastId().getPath());
        xpReward = rule.body().experience();
        setPersistenceRequired();
        parts = isDragon() ? new FieldBossPart[]{
                new FieldBossPart(this, 3.3F, 2.5F, 6.7, 0, 1.7, false),
                new FieldBossPart(this, 1.6F, 1.8F, 6.1, -1.75, 2.2, true),
                new FieldBossPart(this, 1.6F, 1.8F, 6.1, 1.75, 2.2, true),
                new FieldBossPart(this, 3.8F, 2.8F, -3.2, .7, .4, false),
                new FieldBossPart(this, 2.6F, 2.0F, -7.4, .7, .27, false),
                new FieldBossPart(this, 2.0F, 1.6F, -10.6, -.3, .22, false),
                new FieldBossPart(this, 1.65F, 1.4F, -13.4, -1.4, .18, false)
        } : new FieldBossPart[0];
        if (isDragon()) setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
        healthBar = new ServerBossEvent(getDisplayName(), baseColor(), BossEvent.BossBarOverlay.PROGRESS);
        var presentation = new LinkedHashMap<>(super.clipAnimationStates());
        presentation.put("hurt", hurt);
        presentation.put("death", death);
        clips = Collections.unmodifiableMap(presentation);
    }

    @Override protected ResourceLocation beastId() { return EntityType.getKey(getType()); }
    private boolean isDragon() { return beastId().getPath().equals(DRAGON); }
    public Kind kind() { return isDragon() ? Kind.TIDE : Kind.WIND; }
    private BossEvent.BossBarColor baseColor() { return isDragon() ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.YELLOW; }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(EXPOSED, false);
        builder.define(STELE_SET, false);
    }
    @Override public Map<String, AnimationState> clipAnimationStates() { return clips == null ? super.clipAnimationStates() : clips; }
    @Override public Optional<AnimationState> clipAnimationState(String clip) { return Optional.ofNullable(clipAnimationStates().get(clip)); }
    public boolean steleSet() { return entityData.get(STELE_SET); }
    public boolean exposed() { return entityData.get(EXPOSED); }
    public void setFieldPhase(boolean exposed, boolean steleSet) {
        entityData.set(EXPOSED, exposed);
        entityData.set(STELE_SET, steleSet);
    }
    @Override public boolean isMultipartEntity() { return parts != null && parts.length != 0; }
    @Override public FieldBossPart[] getParts() { return parts; }
    @Override public void setId(int id) {
        super.setId(id);
        if (parts != null) for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
    }
    @Override public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(isDragon() ? 15 : 4, isDragon() ? 2 : 7, isDragon() ? 15 : 4);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new BeastAttackGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, ServerPlayer.class, 6, false, false, this::fieldTarget));
    }

    public void bind(Site site) {
        fieldHome = site.home();
        var rule = FieldEncounterRules.bundled().beast(beastId().getPath());
        var realm = com.example.myvillage.stats.StatsRules.bundled().realmKey(rule.realm(), rule.stage());
        assignWildernessBirth(new BeastBirth(realm, site.domain().region(),
                fieldHome.getX(), fieldHome.getY(), fieldHome.getZ(), false));
    }

    public boolean isBoundTo(BlockPos home) { return home.equals(fieldHome); }
    public boolean rewardsAllowed() {
        return level() instanceof ServerLevel server && FieldArenaEncounter.isRewardOwner(server, kind(), getUUID(), fieldHome);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (fieldHome != null) tag.putLong("FieldHome", fieldHome.asLong());
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        fieldHome = tag.contains("FieldHome") ? BlockPos.of(tag.getLong("FieldHome")) : null;
    }

    private Site site() {
        return fieldHome != null && level() instanceof ServerLevel server ? FieldArenaEncounter.site(server, kind(), fieldHome) : null;
    }

    private boolean fieldTarget(LivingEntity target) {
        if (!super.canAttack(target)) return false;
        var site = site();
        if (site == null) return fieldHome == null;
        return site.contains(target.getBlockX(), target.getBlockZ())
                && Math.abs(target.getY() - getY()) <= 18;
    }

    @Override public boolean canAttack(LivingEntity target) { return fieldTarget(target); }

    @Override public void tick() {
        super.tick();
        if (parts != null) {
            var forward = BeastGeometry.forward(yBodyRot);
            for (var part : parts) part.place(position(), forward);
        }
        if (level().isClientSide()) {
            int clock = (int) clipTime(0);
            if (isDeadOrDying()) death.startIfStopped(clock - deathTime);
            if (isAlive() && hurtTime > 0 && !isMoveShown() && !isStaggerShown()) {
                if (!hurt.isStarted() || hurtTime > lastHurt) hurt.start(clock - Math.max(0, hurtDuration - hurtTime));
            } else hurt.stop();
            lastHurt = hurtTime;
        } else if (!isAlive()) healthBar.removeAllPlayers();
    }

    @Override protected void customServerAiStep() {
        var site = site();
        if (getTarget() != null && !fieldTarget(getTarget())) setTarget(null);
        if (site != null && !site.contains(getBlockX(), getBlockZ())) {
            setTarget(null);
            cancelCurrentMove(definition().chase().cancelledCooldownTicks());
        }
        if (site != null && getTarget() == null && !isMoveRunning() && tickCount % 20 == 0
                && distanceToSqr(Vec3.atBottomCenterOf(site.home())) > 10 * 10) {
            var home = Vec3.atBottomCenterOf(site.home());
            getNavigation().moveTo(home.x, home.y, home.z, .88);
        }
        super.customServerAiStep();
        if (tickCount % 10 == 0) {
            for (var observer : observers) updateObserver(observer,site);
        }
        healthBar.setProgress(getHealth() / getMaxHealth());
        healthBar.setColor(exposed() ? BossEvent.BossBarColor.GREEN : baseColor());
        healthBar.setName(exposed() ? Component.translatable(isDragon()
                ? "entity.yuanxian.tide_dragon.reflux" : "entity.yuanxian.stele_ape.recovery", getDisplayName()) : getDisplayName());
    }

    @Override protected boolean moveEligible(BeastMoveDefinition move, LivingEntity target) {
        var site = site();
        return switch (move.animation()) {
            case "surge" -> site != null && FieldArenaEncounter.idle((ServerLevel) level(), kind());
            case "reflux" -> false;
            case "stele" -> site != null && FieldArenaEncounter.idle((ServerLevel) level(), kind())
                    && distanceToSqr(Vec3.atBottomCenterOf(site.bridgeA())) <= launchDistanceSquared()
                    && target.distanceToSqr(Vec3.atBottomCenterOf(site.bridgeB())) <= launchDistanceSquared();
            default -> !exposed();
        };
    }

    private static int launchDistanceSquared() {
        int range = FieldEncounterRules.bundled().encounter().steleLaunchRange();
        return range * range;
    }

    @Override protected float aimYaw(BeastMoveDefinition move, LivingEntity target) {
        var site = site();
        if (site != null && move.animation().equals("stele")) {
            Vec3 direction = Vec3.atBottomCenterOf(site.bridgeB()).subtract(position());
            return BeastGeometry.yawToward(direction.x, direction.z);
        }
        return super.aimYaw(move, target);
    }

    @Override protected void strike(BeastMoveDefinition move, LivingEntity target) {
        var site = site();
        if (site != null && level() instanceof ServerLevel server) {
            if (move.animation().equals("surge")) {
                FieldArenaEncounter.raiseTide(server, site, this);
                return;
            }
            if (move.animation().equals("stele") && FieldArenaEncounter.setBridge(server, site, this)) {
                var bridge = AABB.encapsulatingFullBlocks(site.bridgeA(), site.bridgeB()).inflate(2.5, 3, 2.5);
                for (var victim : server.getEntitiesOfClass(LivingEntity.class, bridge, entity -> isVictim(entity, target))) {
                    if (registerMoveVictim(move, victim)) hitVictim(move, victim);
                }
                return;
            }
        }
        if (!move.animation().equals("reflux")) super.strike(move, target);
    }

    public void surgeHit(ServerPlayer player) {
        var surge = definition().moves().stream().filter(m -> m.animation().equals("surge")).findFirst().orElseThrow();
        if (isVictim(player, getTarget())) hitVictim(surge, player);
    }

    public void startReflux() {
        cancelCurrentMove(definition().chase().cancelledCooldownTicks());
        forceMove(beastId().withPath(DRAGON + "_reflux"));
    }

    @Override protected void playWindupSound(BeastMoveDefinition move) {
        var sound = switch (move.animation()) {
            case "surge" -> SoundEvents.BUCKET_EMPTY;
            case "stele" -> SoundEvents.RAVAGER_ROAR;
            default -> isDragon() ? SoundEvents.GUARDIAN_AMBIENT : SoundEvents.RAVAGER_ATTACK;
        };
        playSound(sound, 1.5F, .76F);
    }

    @Override public boolean hurt(DamageSource source, float amount) { return hurtAt(source, amount, false); }

    boolean hurtAt(DamageSource source, float amount, boolean gill) {
        if (exposed() && (isDragon() ? gill : behind(source)))
            amount *= FieldEncounterRules.bundled().encounter().weakPointMultiplier();
        return super.hurt(source, amount);
    }

    private boolean behind(DamageSource source) {
        var from = source.getSourcePosition();
        if (from == null) return false;
        Vec3 delta = from.subtract(position()).multiply(1, 0, 1).normalize();
        return BeastGeometry.forward(yBodyRot).dot(delta) < -.42;
    }

    @Override public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        observers.add(player);
        updateObserver(player,site());
    }

    private void updateObserver(ServerPlayer player, Site site) {
        int range = site == null ? 48 : site.domain().observerRadius();
        if (isAlive() && distanceToSqr(player) <= range * range) healthBar.addPlayer(player);
        else healthBar.removePlayer(player);
    }

    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        observers.remove(player);
        healthBar.removePlayer(player);
    }

    @Override public void remove(Entity.RemovalReason reason) {
        if (healthBar != null) healthBar.removeAllPlayers();
        super.remove(reason);
    }
}
