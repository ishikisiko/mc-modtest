package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.session.CombatSession;
import com.example.myvillage.stats.runtime.StatsRuntime;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

public final class CombatDamageService {
    /** The target whose {@code hurt} call is one of our hits, so its vanilla knockback is dropped. */
    private static final ThreadLocal<Entity> RESOLVING_TARGET = new ThreadLocal<>();
    /** External sword-qi actions share this service but are not melee attacks. */
    private static final ThreadLocal<Entity> RESOLVING_SKILL_TARGET = new ThreadLocal<>();

    private static final ThreadLocal<Entity> DERIVED_TARGET = new ThreadLocal<>();
    private static final ThreadLocal<PrimaryAttribution> PRIMARY = new ThreadLocal<>();
    private static final class PrimaryAttribution {
        private final Entity target;
        private double fraction = 1;
        private PrimaryAttribution(Entity target) { this.target = target; }
    }

    private CombatDamageService() {
    }

    public static DamageResult apply(
            LivingEntity attacker,
            Entity target,
            AttackMoveDefinition move,
            CombatSession session) {
        return apply(attacker, target, move, session, session.facingYaw(), false);
    }

    public static DamageResult applyDerived(LivingEntity attacker, Entity target,
            AttackMoveDefinition move, CombatSession session) {
        return apply(attacker, target, move, session, session.facingYaw(), true);
    }

    /** Area arts radiate reactions while retaining the single authoritative damage pipeline. */
    public static DamageResult apply(LivingEntity attacker, Entity target, AttackMoveDefinition move,
            CombatSession session, float reactionYaw) {
        return apply(attacker, target, move, session, reactionYaw, false);
    }

    private static DamageResult apply(LivingEntity attacker, Entity target,
            AttackMoveDefinition move, CombatSession session, float reactionYaw, boolean derived) {
        return apply(attacker,target,move,session,reactionYaw,derived,1);
    }

    /** Dodge tail scales the complete outgoing hit, before vanilla mitigation runs exactly once. */
    public static DamageResult applyMitigated(LivingEntity attacker, Entity target,
            AttackMoveDefinition move, CombatSession session, double fraction) {
        if (!Double.isFinite(fraction) || fraction<=0 || fraction>1) throw new IllegalArgumentException("Damage fraction");
        return apply(attacker,target,move,session,session.facingYaw(),false,fraction);
    }

    private static DamageResult apply(LivingEntity attacker, Entity target,
            AttackMoveDefinition move, CombatSession session, float reactionYaw, boolean derived, double fraction) {
        if (!Float.isFinite(reactionYaw)) throw new IllegalArgumentException("Non-finite reaction yaw");
        Entity owner = CombatTargets.owner(target);
        if (!CombatTargets.physicalTarget(target)
                || (!derived && attacker instanceof ServerPlayer player && !CommonHooks.onPlayerAttackTarget(player, target))
                || !CombatActors.mayHarm(attacker, owner)
                || !target.isAttackable()
                || target.skipAttackInteraction(attacker)) {
            return DamageResult.rejected();
        }

        boolean spearTip = !derived && attacker instanceof ServerPlayer player
                && CombatDisciplines.spearTip(player, target);
        if (spearTip) move = CombatDisciplines.scale(move, DisciplineRules.get().spearDamage(), DisciplineRules.get().spearStun());
        ServerLevel level = (ServerLevel) attacker.level();
        ItemStack weapon = attacker.getMainHandItem();
        DamageSource source = CombatActors.source(attacker);
        float attributeDamage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double streak = !derived && attacker instanceof ServerPlayer player
                ? com.example.myvillage.cultivation.fate.SwordObsessionRuntime.multiplier(player, session) : 1;
        float damage = StatsRuntime.outgoing(attacker, attributeDamage, move.damageMultiplier() * streak);
        if (!derived) {
            damage += weapon.getItem().getAttackDamageBonus(target, attributeDamage, source);
            damage = EnchantmentHelper.modifyDamage(level, weapon, target, source, damage);
        }
        damage *= (float) fraction;
        if (!(damage > 0.0F) || (target.isInvulnerableTo(source) || owner.isInvulnerableTo(source))) {
            return DamageResult.rejected();
        }

        float healthBefore = owner instanceof LivingEntity living ? living.getHealth() : 0.0F;
        boolean dragonDyingBefore = dragonDying(owner);
        // Invulnerability frames: our scheduled combo hits always land in full (target freeze
        // pauses the i-frame countdown, and chained hits can be 7 ticks apart), so the timer is
        // cleared for this call only. Afterwards the target keeps the larger of its previous
        // timer and the fresh one our hit set (vanilla sets 20), so every other damage source
        // still meets vanilla i-frames, including right after our hit. A rejected hurt restores
        // the previous timer unchanged.
        int previousInvulnerableTime = owner.invulnerableTime;
        owner.invulnerableTime = 0;
        Entity previousTarget = RESOLVING_TARGET.get();
        Entity previousSkillTarget = RESOLVING_SKILL_TARGET.get();
        Entity previousDerived = DERIVED_TARGET.get();
        PrimaryAttribution previousAttribution = PRIMARY.get();
        PrimaryAttribution attribution = new PrimaryAttribution(owner);
        PRIMARY.set(attribution);
        if (derived) DERIVED_TARGET.set(owner); else DERIVED_TARGET.remove();
        RESOLVING_TARGET.set(owner);
        if (session.isExternal()) RESOLVING_SKILL_TARGET.set(owner);
        else RESOLVING_SKILL_TARGET.remove();
        boolean hurt;
        try {
            hurt = StatsRuntime.hurt(attacker, target, source, damage);
        } finally {
            if (previousAttribution == null) PRIMARY.remove(); else PRIMARY.set(previousAttribution);
            if (previousDerived == null) DERIVED_TARGET.remove(); else DERIVED_TARGET.set(previousDerived);
            if (previousTarget == null) RESOLVING_TARGET.remove();
            else RESOLVING_TARGET.set(previousTarget);
            if (previousSkillTarget == null) RESOLVING_SKILL_TARGET.remove();
            else RESOLVING_SKILL_TARGET.set(previousSkillTarget);
            owner.invulnerableTime = Math.max(previousInvulnerableTime, owner.invulnerableTime);
        }
        // EnderDragon.hurt(part, ...) can return true for a rejected internal living hurt (e.g.
        // invulnerable phase). Require owner HP loss or this hit's real transition into the
        // vanilla death flight: a dragon already at 1 HP is restored to 1 HP when that starts.
        // This accepts the transition once without inventing health loss for damage/fate receipts.
        boolean enteredDragonDeath = !dragonDyingBefore && dragonDying(owner);
        if (!hurt || (owner != target && owner instanceof LivingEntity living
                && living.getHealth() >= healthBefore && !enteredDragonDeath)) {
            return DamageResult.rejected();
        }

        // Freeze the primary receipt before enchantment post-effects can cause nested damage.
        double primaryActualDamage = owner instanceof LivingEntity living
                ? Math.min(healthBefore, Math.max(0, healthBefore - Math.max(0, living.getHealth()))) : 0;

        // Vanilla sources (ATTACK_KNOCKBACK attribute, Knockback enchantments) keep contributing
        // in vanilla units: knockback strength k moves a target at k * 0.5 blocks per tick.
        float knockback = (float) attacker.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        knockback = EnchantmentHelper.modifyKnockback(level, weapon, target, source, knockback);
        double resistance = target instanceof LivingEntity living
                ? living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
                : 0.0;
        Vec3 impulse = CombatReactionMath.impulse(
                move.reaction(), reactionYaw, Math.max(0.0F, knockback) * 0.5, resistance);
        // A multipart box is not an independently movable body. Keep the part's true contact
        // receipt, but never move/freeze its parent to imitate an ordinary mob reaction.
        if (target instanceof LivingEntity living) {
            CombatReactionService.onHit(living, move, feedbackFreezeTicks(move), impulse);
        } else if (owner == target && target.isPushable() && impulse.horizontalDistanceSqr() > 0.0) {
            target.push(impulse.x, Math.max(0.1, impulse.y), impulse.z);
        }
        if (!derived) EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, weapon);

        if (!derived && owner instanceof LivingEntity living && session.markDurabilityCharged()) {
            if (attacker instanceof ServerPlayer player) {
                if (weapon.hurtEnemy(living, player)) weapon.postHurtEnemy(living, player);
            } else {
                weapon.hurtAndBreak(1, attacker, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            }
        }

        float actualDamage = owner instanceof LivingEntity living
                ? Math.max(0.0F, healthBefore - living.getHealth())
                : damage;
        if (!derived && session.markBookkeepingApplied()) {
            attacker.setLastHurtMob(owner);
            if (attacker instanceof ServerPlayer player) {
                player.awardStat(Stats.DAMAGE_DEALT, Math.round(actualDamage * 10.0F));
                player.causeFoodExhaustion(0.1F);
            }
        }
        if (!derived && attacker instanceof ServerPlayer player) {
            com.example.myvillage.cultivation.fate.SwordObsessionRuntime.accepted(player, session);
            CombatDisciplines.meleeAccepted(player, session, primaryActualDamage);
            if (spearTip && primaryActualDamage > 0) CombatDisciplines.tipAccepted(player, session.revision());
            if (owner instanceof LivingEntity living) {
                double primaryLoss = primaryActualDamage * attribution.fraction;
                com.example.myvillage.cultivation.fate.FireSpiritRuntime.directHit(
                        player, living, primaryLoss, attributeDamage);
            }
        }
        return new DamageResult(true, damage, actualDamage);
    }

    private static boolean dragonDying(Entity owner) {
        return owner instanceof EnderDragon dragon
                && dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING;
    }

    /** A bonus-damage listener annotates only the exact synchronous primary hit. */
    public static void primaryFraction(Entity target, double fraction) {
        var attribution = PRIMARY.get();
        if (attribution != null && attribution.target == target && Double.isFinite(fraction))
            attribution.fraction = Math.clamp(fraction, 0, 1);
    }

    /** Attributed secondary damage shares all mitigation, but cannot cause offensive fate procs. */
    public static boolean hurtDerived(LivingEntity attacker, LivingEntity target, DamageSource source, float damage) {
        if (!attacker.isAlive() || attacker.isRemoved() || target.level() != attacker.level()
                || !target.isAlive() || target.isRemoved() || target == attacker
                || attacker.isAlliedTo(target) || target.isAlliedTo(attacker)
                || !CombatActors.mayHarm(attacker, target)
                || !Float.isFinite(damage) || damage <= 0 || target.isInvulnerableTo(source)) return false;
        Entity previousDerived = DERIVED_TARGET.get();
        Entity previousSkill = RESOLVING_SKILL_TARGET.get();
        int previousInvulnerableTime = target.invulnerableTime;
        DERIVED_TARGET.set(target);
        RESOLVING_SKILL_TARGET.set(target);
        target.invulnerableTime = 0;
        try {
            return StatsRuntime.hurt(attacker, target, source, damage);
        } finally {
            if (previousDerived == null) DERIVED_TARGET.remove(); else DERIVED_TARGET.set(previousDerived);
            if (previousSkill == null) RESOLVING_SKILL_TARGET.remove(); else RESOLVING_SKILL_TARGET.set(previousSkill);
            target.invulnerableTime = Math.max(previousInvulnerableTime, target.invulnerableTime);
        }
    }

    /**
     * True while {@code entity} is the target of one of our {@code hurt} calls. The
     * LivingKnockBackEvent listener cancels vanilla's hurt knockback (0.4 toward the attacker
     * plus a forced hop) for exactly that entity; our reaction impulse replaces it.
     */
    public static boolean isDerivedHit(Entity entity) { return DERIVED_TARGET.get() == entity; }

    public static boolean isResolvingHit(Entity entity) {
        Entity target = RESOLVING_TARGET.get();
        return target != null && target == entity;
    }

    /** Only the exact target of the current external-skill hurt call is excluded from melee fates. */
    public static boolean isResolvingSkillHit(Entity entity) {
        return RESOLVING_SKILL_TARGET.get() == entity;
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
        return CombatReactionMath.freezeTicks(move.feedback().hitStopTicks());
    }

    public record DamageResult(boolean successful, float requestedDamage, float actualDamage) {
        private static DamageResult rejected() {
            return new DamageResult(false, 0.0F, 0.0F);
        }
    }
}
