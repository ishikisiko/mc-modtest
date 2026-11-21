package com.example.myvillage.cultivation.fate;

import com.example.myvillage.combat.foundation.ControlService;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.combat.runtime.CombatReactionService;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class HeavyHandRuntime {
    public static final ResourceKey<DamageType> DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("yuanxian:heavy_hand_wall"));
    private record Strike(LivingEntity attacker, DamageSource source, long tick, float raw, boolean accepted) { }
    private record Launch(Strike strike, long until, Vec3 away) { }
    private static final Map<LivingEntity, Strike> STRIKES = new WeakHashMap<>();
    private static final Map<LivingEntity, Launch> FLIGHTS = new WeakHashMap<>();
    private static final Map<LivingEntity, Map<UUID, Long>> COOLDOWNS = new WeakHashMap<>();

    private HeavyHandRuntime() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, HeavyHandRuntime::incoming);
        NeoForge.EVENT_BUS.addListener(HeavyHandRuntime::hurt);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, HeavyHandRuntime::knockback);
    }

    private static void incoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Mob target) || target.level().isClientSide()
                || event.getSource().is(DamageTypeTags.NO_KNOCKBACK)) return;
        STRIKES.remove(target);
        if (!FateDefenses.directMelee(event.getSource()) || CombatDamageService.isResolvingSkillHit(target)
                || CombatDamageService.isDerivedHit(target) || event.getAmount() <= 0
                || target.isDamageSourceBlocked(event.getSource())) return;
        LivingEntity actor = (LivingEntity) event.getSource().getEntity();
        long now = target.level().getGameTime();
        if (!FateActors.supported(actor) || FateService.effect(actor, "wall_fraction") <= 0
                || COOLDOWNS.getOrDefault(target, Map.of()).getOrDefault(actor.getUUID(), 0L) > now) return;
        STRIKES.put(target, new Strike(actor, event.getSource(), now, event.getAmount(), false));
    }

    private static void hurt(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        var strike = STRIKES.get(target);
        if (strike != null && strike.tick == target.level().getGameTime() && strike.source == event.getSource()
                && event.getNewDamage() > 0) {
            FLIGHTS.remove(target);
            STRIKES.put(target, new Strike(strike.attacker, strike.source, strike.tick, strike.raw, true));
        }
    }

    private static void knockback(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || !(target instanceof Mob)
                || CombatDamageService.isResolvingHit(target) || ControlService.isPoised(target)) return;
        var strike = STRIKES.get(target);
        if (strike == null || !strike.accepted || strike.tick != target.level().getGameTime()
                || target.getLastHurtByMob() != strike.attacker
                || event.getStrength() * (1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) <= 0) return;
        var direction = new Vec3(-event.getRatioX(), 0, -event.getRatioZ());
        arm(target, strike, direction);
    }

    /** The mod's authored hit-stop holds the strike until the first actual impulse. */
    public static void launched(LivingEntity target, Vec3 impulse, DamageSource source) {
        var strike = STRIKES.get(target);
        if (strike != null && strike.accepted && strike.source == source && !ControlService.isPoised(target))
            arm(target, strike, impulse);
    }

    private static void arm(LivingEntity target, Strike strike, Vec3 impulse) {
        if (impulse.horizontalDistanceSqr() < 1.0e-6) return;
        STRIKES.remove(target);
        var away = new Vec3(impulse.x, 0, impulse.z).normalize();
        FLIGHTS.put(target, new Launch(strike, target.level().getGameTime()
                + (long) FateService.effect(strike.attacker, "wall_ticks"), away));
    }

    public static boolean tracking(Entity entity) {
        if (!(entity instanceof Mob mob) || entity.level().isClientSide()) return false;
        var flight = FLIGHTS.get(mob);
        if (flight == null) return false;
        if (mob.level().getGameTime() >= flight.until || !mob.isAlive()) {
            FLIGHTS.remove(mob);
            return false;
        }
        return true;
    }

    public static void collide(Entity entity, AABB start, Vec3 requested, Vec3 actual) {
        if (!(entity instanceof Mob target) || !target.horizontalCollision) return;
        var flight = FLIGHTS.get(target);
        if (flight == null) return;
        Vec3 clipped = requested.subtract(actual).multiply(1, 0, 1);
        if (clipped.dot(flight.away) <= .0001) return;
        var torso = start.move(actual).deflate(.0001);
        boolean hitBlock = false;
        for (var block : target.level().getBlockCollisions(target, torso.expandTowards(clipped))) {
            if (!block.isEmpty()) { hitBlock = true; break; }
        }
        if (!hitBlock) return;
        FLIGHTS.remove(target);
        var strike = flight.strike;
        LivingEntity attacker = strike.attacker;
        var holder = target.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE);
        if (!CombatDamageService.hurtDerived(attacker, target, new DamageSource(holder, attacker),
                (float) (strike.raw * FateService.effect(attacker, "wall_fraction")))) return;
        CombatReactionService.wallStun(target, (int) FateService.effect(attacker, "wall_stun_ticks"));
        long now = target.level().getGameTime();
        var timers = COOLDOWNS.computeIfAbsent(target, mob -> new HashMap<>());
        timers.values().removeIf(tick -> tick <= now);
        timers.put(attacker.getUUID(), now + (long) FateService.effect(attacker, "wall_cooldown"));
    }

    static void clear() { STRIKES.clear(); FLIGHTS.clear(); COOLDOWNS.clear(); }
}
