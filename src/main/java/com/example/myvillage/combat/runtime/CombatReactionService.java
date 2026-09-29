package com.example.myvillage.combat.runtime;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.definition.AttackMoveDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Server-authoritative target reaction to a successful Qingfeng hit.
 *
 * <ol>
 *     <li>Freeze: a struck non-player {@link Mob} skips its whole entity tick for
 *     {@code round(hitStopTicks)} ticks (EntityTickEvent.Pre is cancelled after
 *     {@code setOldPosAndRot}, so it holds still cleanly). Its horizontal motion is zeroed on the
 *     hit tick and the knockback impulse is held until the freeze ends: impact, then launch.</li>
 *     <li>Hitstun: for {@code reaction.hitstunTicks} after the freeze the mob's navigation is
 *     stopped, its MOVE/LOOK/JUMP goal flags are disabled and its input is zeroed every tick, and
 *     its melee damage is cancelled.</li>
 *     <li>Anti-lock: each further stun within 40 ticks of the previous one is scaled by 0.7,
 *     targets with at least 100 max health take 30%, and the Ender Dragon, Wither and Warden are
 *     never frozen or stunned (they still receive knockback, scaled by their resistance).</li>
 *     <li>Players are never frozen and keep full control; they get the knockback at once and a
 *     transient 60% MOVEMENT_SPEED slow for the stun.</li>
 * </ol>
 * State is cleared on death, unload, dimension change and server stop.
 */
public final class CombatReactionService {
    public static final ResourceLocation STUN_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "combat_stun");
    static final double PLAYER_STUN_SLOW = -0.6;

    private static final Map<Integer, Reaction> REACTIONS = new HashMap<>();

    private CombatReactionService() {
    }

    /** Registers a hit's reaction. Called by CombatDamageService after a successful hurt. */
    static void onHit(LivingEntity target, AttackMoveDefinition move, int freezeTicks, Vec3 impulse) {
        if (target.level().isClientSide()) {
            return;
        }
        long now = target.level().getGameTime();
        boolean excluded = excluded(target);
        Reaction reaction = REACTIONS.get(target.getId());
        if (reaction == null || reaction.target != target) {
            reaction = new Reaction(target);
            REACTIONS.put(target.getId(), reaction);
        }

        boolean freezable = target instanceof Mob && !excluded && freezeTicks > 0 && !target.isDeadOrDying();
        if (freezable) {
            reaction.freezeUntil = Math.max(reaction.freezeUntil, now + freezeTicks);
            reaction.pendingImpulse = impulse;
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(0.0, Math.min(0.0, motion.y), 0.0);
        } else {
            CombatDamageService.applyImpulse(target, impulse);
        }

        int baseStun = move.reaction().hitstunTicks();
        if (!excluded && baseStun > 0 && !target.isDeadOrDying()) {
            boolean stacked = reaction.lastStunTick != Long.MIN_VALUE
                    && now - reaction.lastStunTick <= CombatReactionMath.STUN_STACK_WINDOW_TICKS;
            reaction.stunStacks = stacked ? reaction.stunStacks + 1 : 0;
            reaction.lastStunTick = now;
            int stun = CombatReactionMath.stunTicks(baseStun, reaction.stunStacks, target.getMaxHealth());
            if (stun > 0) {
                long begin = Math.max(now, reaction.freezeUntil);
                reaction.stunUntil = Math.max(reaction.stunUntil, begin + stun);
                if (target instanceof Player) {
                    setPlayerStun(target, true);
                }
            }
        }
    }

    /** Server tick: releases held impulses when a freeze ends and ends expired stuns. */
    public static void tick() {
        if (REACTIONS.isEmpty()) {
            return;
        }
        Iterator<Reaction> iterator = REACTIONS.values().iterator();
        while (iterator.hasNext()) {
            Reaction reaction = iterator.next();
            LivingEntity target = reaction.target;
            if (target.isRemoved() || !target.isAlive()) {
                endStun(reaction);
                iterator.remove();
                continue;
            }
            long now = target.level().getGameTime();
            if (reaction.pendingImpulse != null && now >= reaction.freezeUntil) {
                CombatDamageService.applyImpulse(target, reaction.pendingImpulse);
                reaction.pendingImpulse = null;
            }
            if (reaction.stunUntil != Long.MIN_VALUE && now >= reaction.stunUntil) {
                endStun(reaction);
                reaction.stunUntil = Long.MIN_VALUE;
            }
            boolean idle = reaction.pendingImpulse == null
                    && reaction.stunUntil == Long.MIN_VALUE
                    && now >= reaction.freezeUntil
                    && (reaction.lastStunTick == Long.MIN_VALUE
                    || now - reaction.lastStunTick > CombatReactionMath.STUN_STACK_WINDOW_TICKS);
            if (idle) {
                iterator.remove();
            }
        }
    }

    /** Cancels a frozen mob's tick and holds a stunned mob's AI still. */
    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || REACTIONS.isEmpty()
                || !(entity instanceof Mob mob) || mob.isDeadOrDying()) {
            return;
        }
        Reaction reaction = REACTIONS.get(entity.getId());
        if (reaction == null || reaction.target != entity) {
            return;
        }
        long now = entity.level().getGameTime();
        if (now <= reaction.freezeUntil) {
            event.setCanceled(true);
            return;
        }
        if (now <= reaction.stunUntil) {
            // Mob.tick re-enables control flags every 5 ticks after aiStep, so this runs each tick.
            mob.getNavigation().stop();
            mob.goalSelector.disableControlFlag(Goal.Flag.MOVE);
            mob.goalSelector.disableControlFlag(Goal.Flag.LOOK);
            mob.goalSelector.disableControlFlag(Goal.Flag.JUMP);
            mob.setZza(0.0F);
            mob.setXxa(0.0F);
            mob.setJumping(false);
        }
    }

    /** A frozen or staggered mob's melee swing does no damage. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || REACTIONS.isEmpty()) {
            return;
        }
        DamageSource source = event.getSource();
        Entity direct = source.getDirectEntity();
        if (!(direct instanceof Mob mob) || direct != source.getEntity()
                || !(source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))) {
            return;
        }
        if (isStaggered(mob)) {
            event.setCanceled(true);
        }
    }

    public static boolean isStaggered(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return false;
        }
        Reaction reaction = REACTIONS.get(entity.getId());
        if (reaction == null || reaction.target != entity) {
            return false;
        }
        long now = entity.level().getGameTime();
        return now <= reaction.freezeUntil || now <= reaction.stunUntil;
    }

    /** Drops all reaction state for an entity that died, unloaded or changed dimension. */
    public static void clear(Entity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        Reaction reaction = REACTIONS.get(entity.getId());
        if (reaction != null && reaction.target == entity) {
            endStun(reaction);
            REACTIONS.remove(entity.getId());
        }
    }

    public static void clearAll() {
        for (Reaction reaction : REACTIONS.values()) {
            endStun(reaction);
        }
        REACTIONS.clear();
    }

    private static void endStun(Reaction reaction) {
        LivingEntity target = reaction.target;
        if (target instanceof Player) {
            setPlayerStun(target, false);
        } else if (target instanceof Mob mob && reaction.stunUntil != Long.MIN_VALUE) {
            // Same rule as Mob.updateControlFlags, so control returns on time rather than at the
            // next five-tick refresh.
            boolean notControlledByMob = !(mob.getControllingPassenger() instanceof Mob);
            boolean notInBoat = !(mob.getVehicle() instanceof Boat);
            mob.goalSelector.setControlFlag(Goal.Flag.MOVE, notControlledByMob);
            mob.goalSelector.setControlFlag(Goal.Flag.JUMP, notControlledByMob && notInBoat);
            mob.goalSelector.setControlFlag(Goal.Flag.LOOK, notControlledByMob);
        }
    }

    private static void setPlayerStun(LivingEntity player, boolean stunned) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (stunned) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(
                    STUN_MODIFIER_ID, PLAYER_STUN_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            speed.removeModifier(STUN_MODIFIER_ID);
        }
    }

    private static boolean excluded(LivingEntity target) {
        return target instanceof EnderDragon || target instanceof WitherBoss || target instanceof Warden;
    }

    private static final class Reaction {
        private final LivingEntity target;
        private long freezeUntil = Long.MIN_VALUE;
        private long stunUntil = Long.MIN_VALUE;
        private long lastStunTick = Long.MIN_VALUE;
        private int stunStacks;
        private Vec3 pendingImpulse;

        private Reaction(LivingEntity target) {
            this.target = target;
        }
    }
}
