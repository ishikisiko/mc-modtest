package com.example.myvillage.entity;

import com.example.myvillage.cultivation.nascent.NascentRules;
import com.example.myvillage.cultivation.nascent.NascentTrial;
import com.example.myvillage.stats.runtime.ModStatsAttributes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/** Only spawned by the active player-owned duel. The trial saves its health and clock. */
public final class NascentDemon extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(
            NascentDemon.class, EntityDataSerializers.OPTIONAL_UUID);

    private Vec3 strike;
    private int warning;
    private int recovery;
    private int untilHeavy = 60;
    private int untilMelee = 20;

    public NascentDemon(EntityType<? extends NascentDemon> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        var rules = NascentRules.get();
        return createMobAttributes().add(Attributes.MAX_HEALTH, rules.value("demon_health"))
                .add(Attributes.ATTACK_DAMAGE, rules.value("demon_melee"))
                .add(Attributes.MOVEMENT_SPEED, .31)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(ModStatsAttributes.BODY_DEFENSE, rules.value("demon_defense"));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
    }

    public void bind(ServerPlayer player, float health) {
        entityData.set(OWNER, Optional.of(player.getUUID()));
        setHealth(health);
    }

    public Optional<UUID> owner() { return entityData.get(OWNER); }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(source.getEntity() instanceof ServerPlayer player) || owner().filter(player.getUUID()::equals).isEmpty())
            return false;
        return super.hurt(source, amount);
    }

    public void observeTechnique(ServerPlayer player) {
        if (warning == 0 && recovery == 0 && distanceToSqr(player) <= 100) warnAt(player.position());
    }

    private void warnAt(Vec3 point) {
        strike = point;
        warning = NascentRules.get().value("demon_warning_ticks");
        getNavigation().stop();
        playSound(SoundEvents.BLAZE_SHOOT, .8F, .7F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || isDeadOrDying()) return;
        ServerPlayer player = owner().map(server.getServer().getPlayerList()::getPlayer).orElse(null);
        if (player == null || player.level() != server || !NascentTrial.owns(player, this)) {
            discard();
            return;
        }
        getLookControl().setLookAt(player, 30, 30);
        if (warning > 0) {
            tickHeavy(server, player);
            return;
        }
        if (recovery > 0) {
            recovery--;
            return;
        }
        if (tickCount % 5 == 0) getNavigation().moveTo(player, 1.0);
        if (--untilHeavy <= 0 && distanceToSqr(player) <= 100 && hasLineOfSight(player)) {
            warnAt(player.position());
        } else if (--untilMelee <= 0 && distanceToSqr(player) <= 6.25 && hasLineOfSight(player)) {
            swing(InteractionHand.MAIN_HAND);
            doHurtTarget(player);
            untilMelee = 20;
        }
    }

    private void tickHeavy(ServerLevel level, ServerPlayer player) {
        if (warning % 5 == 0) {
            for (int i = 0; i < 16; i++) {
                double angle = 2 * Math.PI * i / 16;
                level.sendParticles(ParticleTypes.FLAME, strike.x + 2 * Math.cos(angle), strike.y + .15,
                        strike.z + 2 * Math.sin(angle), 1, 0, 0, 0, 0);
            }
        }
        if (--warning == 0) {
            swing(InteractionHand.MAIN_HAND);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, strike.x, strike.y + 1, strike.z, 1, 0, 0, 0, 0);
            if (player.distanceToSqr(strike) <= 4.0 && hasLineOfSight(player)) {
                player.hurt(damageSources().mobAttack(this), NascentRules.get().value("demon_heavy"));
            }
            recovery = NascentRules.get().value("demon_recovery_ticks");
            untilHeavy = 60;
            strike = null;
        }
    }
}
