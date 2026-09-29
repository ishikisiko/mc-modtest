package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.example.myvillage.combat.session.CombatSession;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

public final class CombatDamageService {
    /** The target whose {@code hurt} call is one of our hits, so its vanilla knockback is dropped. */
    private static final ThreadLocal<Entity> RESOLVING_TARGET = new ThreadLocal<>();

    private CombatDamageService() {
    }

    public static DamageResult apply(
            ServerPlayer attacker,
            Entity target,
            AttackMoveDefinition move,
            CombatSession session) {
        if (!CommonHooks.onPlayerAttackTarget(attacker, target)
                || !target.isAttackable()
                || target.skipAttackInteraction(attacker)) {
            return DamageResult.rejected();
        }

        ServerLevel level = attacker.serverLevel();
        ItemStack weapon = attacker.getMainHandItem();
        DamageSource source = attacker.damageSources().playerAttack(attacker);
        float attributeDamage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = (float) (attributeDamage * move.damageMultiplier());
        damage += weapon.getItem().getAttackDamageBonus(target, attributeDamage, source);
        damage = EnchantmentHelper.modifyDamage(level, weapon, target, source, damage);
        if (!(damage > 0.0F) || target.isInvulnerableTo(source)) {
            return DamageResult.rejected();
        }

        float healthBefore = target instanceof LivingEntity living ? living.getHealth() : 0.0F;
        // Invulnerability frames: our scheduled combo hits always land in full (target freeze
        // pauses the i-frame countdown, and chained hits can be 7 ticks apart), so the timer is
        // cleared for this call only. Afterwards the target keeps the larger of its previous
        // timer and the fresh one our hit set (vanilla sets 20), so every other damage source
        // still meets vanilla i-frames, including right after our hit. A rejected hurt restores
        // the previous timer unchanged.
        int previousInvulnerableTime = target.invulnerableTime;
        target.invulnerableTime = 0;
        RESOLVING_TARGET.set(target);
        boolean hurt;
        try {
            hurt = target.hurt(source, damage);
        } finally {
            RESOLVING_TARGET.remove();
            target.invulnerableTime = Math.max(previousInvulnerableTime, target.invulnerableTime);
        }
        if (!hurt) {
            return DamageResult.rejected();
        }

        // Vanilla sources (ATTACK_KNOCKBACK attribute, Knockback enchantments) keep contributing
        // in vanilla units: knockback strength k moves a target at k * 0.5 blocks per tick.
        float knockback = (float) attacker.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        knockback = EnchantmentHelper.modifyKnockback(level, weapon, target, source, knockback);
        double resistance = target instanceof LivingEntity living
                ? living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
                : 0.0;
        Vec3 impulse = CombatReactionMath.impulse(
                move.reaction(), session.facingYaw(), Math.max(0.0F, knockback) * 0.5, resistance);
        if (target instanceof LivingEntity living) {
            CombatReactionService.onHit(living, move, feedbackFreezeTicks(move), impulse);
        } else if (target.isPushable() && impulse.horizontalDistanceSqr() > 0.0) {
            target.push(impulse.x, Math.max(0.1, impulse.y), impulse.z);
        }
        EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, weapon);

        if (target instanceof LivingEntity living && session.markDurabilityCharged()) {
            if (weapon.hurtEnemy(living, attacker)) {
                weapon.postHurtEnemy(living, attacker);
            }
        }

        float actualDamage = target instanceof LivingEntity living
                ? Math.max(0.0F, healthBefore - living.getHealth())
                : damage;
        if (session.markBookkeepingApplied()) {
            attacker.setLastHurtMob(target);
            attacker.awardStat(Stats.DAMAGE_DEALT, Math.round(actualDamage * 10.0F));
            attacker.causeFoodExhaustion(0.1F);
        }
        return new DamageResult(true, damage, actualDamage);
    }

    /**
     * True while {@code entity} is the target of one of our {@code hurt} calls. The
     * LivingKnockBackEvent listener cancels vanilla's hurt knockback (0.4 toward the attacker
     * plus a forced hop) for exactly that entity; our reaction impulse replaces it.
     */
    public static boolean isResolvingHit(Entity entity) {
        Entity target = RESOLVING_TARGET.get();
        return target != null && target == entity;
    }

    /**
     * Applies a reaction impulse: x/z replace the horizontal motion, a positive y lifts the
     * target (0 keeps its own vertical motion). Mobs sync through {@code hurtMarked}; a player
     * target gets the motion packet directly, as vanilla melee does, because its movement is
     * client-driven.
     */
    static void applyImpulse(Entity target, Vec3 impulse) {
        // A killing blow still launches the body, so only removed entities are skipped.
        if (target.isRemoved()) {
            return;
        }
        Vec3 current = target.getDeltaMovement();
        Vec3 motion = new Vec3(
                impulse.x,
                impulse.y > 0.0 ? Math.max(current.y, impulse.y) : current.y,
                impulse.z);
        if (target instanceof ServerPlayer targetPlayer) {
            targetPlayer.setDeltaMovement(motion);
            targetPlayer.hasImpulse = true;
            targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
            targetPlayer.hurtMarked = false;
            targetPlayer.setDeltaMovement(current);
            return;
        }
        target.setDeltaMovement(motion);
        target.hasImpulse = true;
        target.hurtMarked = true;
    }

    private static int feedbackFreezeTicks(AttackMoveDefinition move) {
        int index = BasicSwordStyle.DEFINITION.indexOf(move.id());
        return CombatReactionMath.freezeTicks(BasicSwordStyle.feedback(Math.max(0, index)).hitStopTicks());
    }

    public record DamageResult(boolean successful, float requestedDamage, float actualDamage) {
        private static DamageResult rejected() {
            return new DamageResult(false, 0.0F, 0.0F);
        }
    }
}
