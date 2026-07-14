package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
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
        if (!target.hurt(source, damage)) {
            return DamageResult.rejected();
        }

        float knockback = (float) (attacker.getAttributeValue(Attributes.ATTACK_KNOCKBACK)
                + move.knockback());
        knockback = EnchantmentHelper.modifyKnockback(level, weapon, target, source, knockback);
        Vec3 targetMotionBefore = target.getDeltaMovement();
        applyKnockback(target, knockback, session.facingYaw());
        synchronizePlayerKnockback(target, targetMotionBefore);
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

    private static void applyKnockback(Entity target, float knockback, float facingYaw) {
        if (!(knockback > 0.0F)) {
            return;
        }
        double yaw = Math.toRadians(facingYaw);
        if (target instanceof LivingEntity living) {
            living.knockback(knockback * 0.5, Math.sin(yaw), -Math.cos(yaw));
        } else {
            target.push(-Math.sin(yaw) * knockback * 0.5, 0.1, Math.cos(yaw) * knockback * 0.5);
        }
    }

    private static void synchronizePlayerKnockback(Entity target, Vec3 motionBefore) {
        if (target instanceof ServerPlayer targetPlayer && targetPlayer.hurtMarked) {
            targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
            targetPlayer.hurtMarked = false;
            targetPlayer.setDeltaMovement(motionBefore);
        }
    }

    public record DamageResult(boolean successful, float requestedDamage, float actualDamage) {
        private static DamageResult rejected() {
            return new DamageResult(false, 0.0F, 0.0F);
        }
    }
}
