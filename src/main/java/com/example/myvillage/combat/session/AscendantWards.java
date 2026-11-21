package com.example.myvillage.combat.session;

import com.example.myvillage.combat.art.AscendantArt.Mechanic;
import com.example.myvillage.combat.foundation.ControlRules;
import com.example.myvillage.combat.foundation.ShieldRuntime;
import com.example.myvillage.combat.runtime.CombatActors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import static com.example.myvillage.combat.session.AscendantArts.*;
import static com.example.myvillage.combat.session.GoldenCoreGeometry.*;

public final class AscendantWards {
    private static final ResourceLocation SPEED = ResourceLocation.parse("yuanxian:ascendant_flow_speed");
    private static final Map<UUID, List<Ward>> WARDS = new HashMap<>();
    private static final Map<UUID, Speed> SPEEDS = new HashMap<>();
    private static final int MAX_SPEED_ACTORS = 512;
    private record Protection(double amount, DoubleConsumer debit) {}
    private record Speed(LivingEntity target, Level level, long until, double fraction) {}

    private static final class Ward {
        final Cast cast;
        final LivingEntity target;
        final long until;
        int charges;
        double left;

        Ward(Cast cast, LivingEntity target, int charges) {
            this.cast = cast;
            this.target = target;
            this.until = cast.now() + cast.time.duration();
            this.charges = charges;
            left = Math.min(target.getMaxHealth(), cast.actor.getMaxHealth()) * cast.effect.budget();
        }

        boolean valid() {
            return charges > 0 && left > EPSILON && cast.now() < until && cast.valid() && friend(cast.actor, target);
        }
    }

    private AscendantWards() {}

    static void place(Cast cast) {
        List<LivingEntity> recipients;
        boolean plates = cast.spec.mechanic() == Mechanic.GOLD_PLATES;
        if (plates) recipients = friends(cast.actor, cast.actor.position(), cast.shape.radius()).stream()
                .limit(cast.limits.targets()).toList();
        else {
            LivingEntity target = cast.aim.ally() == null ? cast.actor : cast.aim.ally();
            if (!friend(cast.actor, target) || !inReach(cast.actor, target, cast.shape.range()) || !visible(cast.actor, target)) return;
            recipients = List.of(target);
        }
        if (recipients.isEmpty() || !cast.valid()) return;
        cast.detach();
        for (LivingEntity target : recipients) {
            List<Ward> list = WARDS.computeIfAbsent(target.getUUID(), key -> new ArrayList<>());
            list.removeIf(w -> !w.valid() || w.cast.actor == cast.actor && w.cast.art.id().equals(cast.art.id()));
            if (list.size() >= ControlRules.get().maximumSources()) continue;
            list.add(new Ward(cast, target, plates ? 1 : cast.limits.count()));
            markTarget(cast.actor, cast.art, target, true);
        }
    }

    public static void projectileImpact(ProjectileImpactEvent event) {
        Projectile shot = event.getProjectile();
        if (WARDS.isEmpty() || shot.level().isClientSide() || !ordinaryProjectile(shot)
                || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity victim)
                || !(shot.getOwner() instanceof LivingEntity attacker) || !CombatActors.mayHarm(attacker, victim)) return;
        var wards = WARDS.get(victim.getUUID());
        if (wards == null) return;
        for (Ward ward : wards) {
            if (ward.target != victim || !ward.valid() || ward.cast.spec.mechanic() != Mechanic.GOLD_PLATES) continue;
            shot.discard();
            event.setCanceled(true);
            spend(ward, ward.left);
            return;
        }
    }

    private static void spend(Ward ward, double amount) {
        ward.charges--;
        ward.left -= amount;
        markTarget(ward.cast.actor, ward.cast.art, ward.target, false);
        award(ward.cast);
    }

    public static void absorb(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (victim.level().isClientSide() || !(event.getNewDamage() > 0) || ShieldRuntime.bypassesShields(source)
                || !(source.getEntity() instanceof LivingEntity attacker) || !CombatActors.mayHarm(attacker, victim)) return;
        float incoming = event.getNewDamage();
        Protection strongest = null;
        var wards = WARDS.get(victim.getUUID());
        if (wards != null) {
            for (Ward ward : wards) {
                if (!ward.valid() || ward.target != victim) continue;
                boolean plate = ward.cast.spec.mechanic() == Mechanic.GOLD_PLATES;
                boolean intercept = plate && source.getDirectEntity() instanceof Projectile shot && ordinaryProjectile(shot);
                if (plate && !intercept && !hostileMelee(victim, source)) continue;
                double amount = intercept ? incoming : Math.min(ward.left,
                        incoming * (plate ? 1 : ward.cast.effect.control()));
                if (strongest == null || amount > strongest.amount()) {
                    strongest = new Protection(amount, used -> spend(ward, used));
                }
            }
        }
        for (var field : AscendantFields.protections(victim, Mechanic.MOUNTAIN_SHELL)) {
            var cast = field.cast;
            if (field.shield <= EPSILON || !covered(field, victim) || withinRadius(field.center, attacker.getBoundingBox(), cast.shape.radius()))
                continue;
            double amount = Math.min(field.shield, incoming * cast.effect.control());
            if (strongest == null || amount > strongest.amount()) {
                strongest = new Protection(amount, used -> {
                    field.shield -= used;
                    markTarget(cast.actor, cast.art, victim, false);
                    award(cast);
                });
            }
        }
        if (strongest != null && strongest.amount() > 0) {
            event.setNewDamage(Math.max(0, incoming - (float) strongest.amount()));
            strongest.debit().accept(strongest.amount());
        }
    }

    static void stance(LivingIncomingDamageEvent event) {
        var victim = event.getEntity();
        if (victim.level().isClientSide() || !(event.getAmount() > 0) || ShieldRuntime.bypassesShields(event.getSource())
                || event.getSource().is(DamageTypeTags.BYPASSES_SHIELD)
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || !CombatActors.mayHarm(attacker, victim)) return;
        Cast cast = stance(victim);
        if (cast == null) return;
        var toward = attacker.position().subtract(victim.position());
        if (event.getSource().getDirectEntity() instanceof Projectile shot && shot.getDeltaMovement().horizontalDistanceSqr() > EPSILON)
            toward = shot.getDeltaMovement().scale(-1);
        if (horizontal(toward).dot(cast.aim.forward()) < Math.cos(Math.toRadians(cast.shape.arc() / 2))) return;
        event.setAmount((float)(event.getAmount() * (1 - cast.effect.control())));
    }

    public static void tookDamage(LivingEntity victim, DamageSource source, float amount) {
        if (victim.level().isClientSide() || !(amount > 0)) return;
        Cast cast = stance(victim);
        if (cast != null) cast.damageTaken += amount;
        AscendantFields.tookHit(victim, source, amount);
    }

    private static Cast stance(LivingEntity victim) {
        Cast cast = CombatArtActions.ascendant(victim);
        if (cast == null || cast.spec.mechanic() != Mechanic.DRAGON_STANCE || !cast.valid()) return null;
        int age = cast.session.actionTick(level(victim).getGameTime());
        return age >= cast.time.windup() && age < cast.time.windup() + cast.time.duration() ? cast : null;
    }

    static boolean preventInterrupt(LivingEntity victim, DamageSource source) {
        if (source == null || ShieldRuntime.bypassesShields(source)
                || !(source.getEntity() instanceof LivingEntity enemy) || !CombatActors.mayHarm(enemy, victim)) return false;
        for (var field : AscendantFields.protections(victim, Mechanic.STEADY_PLATFORM)) {
            if (covered(field, victim) && field.helped.add(victim.getId())) {
                award(field.cast);
                markTarget(field.cast.actor, field.cast.art, victim, false);
                return true;
            }
        }
        return false;
    }

    public static double knockbackFactor(LivingEntity victim) {
        double scale = 1;
        for (var field : AscendantFields.protections(victim, Mechanic.STEADY_PLATFORM))
            if (covered(field, victim)) {
                scale = Math.min(scale, 1 - field.cast.effect.control());
                award(field.cast);
            }
        return scale;
    }

    private static boolean covered(AscendantFields.Field field, LivingEntity victim) {
        var c = field.cast;
        if (!friend(c.actor, victim) || !withinRadius(field.center, victim.getBoundingBox(), c.shape.radius())
                || !clear(c.actor, field.center.add(0, .8, 0), point(victim))) return false;
        for (var ally : friends(c.actor, field.center, c.shape.radius())) {
            if (field.points.size() >= c.limits.targets()) break;
            if (field.contacts.putIfAbsent(ally.getId(), 1) == null) field.points.add(ally.position());
        }
        return field.contacts.containsKey(victim.getId());
    }

    static boolean ordinaryProjectile(Projectile shot) {
        return shot instanceof Arrow || shot instanceof SpectralArrow || shot instanceof SmallFireball;
    }

    static boolean hostileMelee(LivingEntity victim, DamageSource source) {
        return source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
                && (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))
                && CombatActors.mayHarm(attacker, victim);
    }

    static void speed(LivingEntity target, double fraction, int ticks) {
        var attribute = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null || target.level().isClientSide()) return;
        long now = level(target).getServer().overworld().getGameTime();
        var old = SPEEDS.get(target.getUUID());
        if (old == null && SPEEDS.size() >= MAX_SPEED_ACTORS) return;
        if (old != null && old.until() > now && old.fraction() > fraction) return;
        attribute.addOrUpdateTransientModifier(new AttributeModifier(SPEED, fraction, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        SPEEDS.put(target.getUUID(), new Speed(target, target.level(), now + ticks, fraction));
    }

    static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        WARDS.values().removeIf(list -> {
            list.removeIf(ward -> !ward.valid());
            return list.isEmpty();
        });
        for (var entry : List.copyOf(SPEEDS.entrySet())) {
            var speed = entry.getValue();
            if (now >= speed.until() || !speed.target().isAlive() || speed.target().isRemoved()
                    || speed.target().level() != speed.level()) clearSpeed(entry.getKey());
        }
    }

    private static void clearSpeed(UUID target) {
        var speed = SPEEDS.remove(target);
        if (speed != null) {
            var attribute = speed.target().getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute != null) attribute.removeModifier(SPEED);
        }
    }

    static void forget(UUID actor) {
        WARDS.remove(actor);
        WARDS.values().removeIf(list -> {
            list.removeIf(ward -> ward.cast.actor.getUUID().equals(actor));
            return list.isEmpty();
        });
        clearSpeed(actor);
    }

    static void clearAll() {
        WARDS.clear();
        for (UUID uuid : List.copyOf(SPEEDS.keySet())) clearSpeed(uuid);
    }
}
