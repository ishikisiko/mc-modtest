package com.example.myvillage.combat.session;

import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.art.EarlyRealmArt;
import com.example.myvillage.combat.elemental.ElementalEffects;
import com.example.myvillage.combat.foundation.HealingRuntime;
import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatCaster;
import com.example.myvillage.combat.runtime.CombatDisciplines;
import com.example.myvillage.cultivation.data.TechniqueTraits;
import com.example.myvillage.stats.DamageType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.myvillage.combat.session.GoldenCoreGeometry.*;

public final class EarlyRealmEffects {
    private static final Logger LOG = LoggerFactory.getLogger(EarlyRealmEffects.class);
    private static final Map<Key, Field> FIELDS = new HashMap<>();
    private static final Map<UUID, GuardReceipt> GUARDS = new HashMap<>();

    private record Key(UUID caster, ResourceLocation art) {}
    private record GuardReceipt(DamageSource source, long tick, long revision) {}

    private static final class Field {
        final EarlyRealmActions.Cast cast;
        final Vec3 center;
        final long placed;
        long due;
        int charges;
        double multiplier;
        LivingEntity ally;

        Field(EarlyRealmActions.Cast cast, Vec3 center, int life) {
            this.cast = cast;
            this.center = center;
            placed = cast.now();
            due = placed + life;
            charges = cast.spec.mechanic() == EarlyRealmArt.Mechanic.STONE_SCREEN ? cast.spec.targets() : 1;
        }

        EarlyRealmArt.Mechanic mechanic() { return cast.spec.mechanic(); }

        boolean valid() {
            if (!cast.valid()) return false;
            return switch (mechanic()) {
                case ARROW_RING -> true;
                case BACK_GUARD -> cast.caster.position().distanceToSqr(center) <= 1;
                case WAITING_FLAME -> available(cast.caster, cast.aim.anchor(), cast.spec.range());
                default -> available(cast.caster, center, cast.spec.range());
            };
        }
    }

    private EarlyRealmEffects() {}

    static void place(EarlyRealmActions.Cast cast, Vec3 center, int life) {
        if (!cast.valid()) return;
        var field = new Field(cast, center, life);
        if (field.mechanic() == EarlyRealmArt.Mechanic.BACK_GUARD) {
            field.ally = friends(cast.caster, center, cast.spec.range()).stream()
                    .filter(friend -> behind(field, friend))
                    .min(Comparator.comparingDouble((LivingEntity e) -> e.distanceToSqr(cast.caster))
                            .thenComparingInt(LivingEntity::getId)).orElse(null);
        } else if (field.mechanic() != EarlyRealmArt.Mechanic.ARROW_RING) {
            cast.retain();
        }
        FIELDS.put(key(cast.caster, cast.art), field);
        mark(field);
        report(field, "placed");
    }

    static boolean recastReady(LivingEntity caster, ArtDefinition art) {
        var field = FIELDS.get(key(caster, art));
        if (field == null || field.mechanic() != EarlyRealmArt.Mechanic.MATURE_FLAME || field.multiplier > 0)
            return false;
        long now = field.cast.now();
        return now >= field.placed + field.cast.spec.delay() && now < field.due && field.valid();
    }

    static boolean recast(LivingEntity caster, ArtDefinition art) {
        if (!recastReady(caster, art)) return false;
        ignite(FIELDS.get(key(caster, art)));
        return true;
    }

    private static void ignite(Field field) {
        var spec = field.cast.spec;
        long now = field.cast.now();
        field.multiplier = now - field.placed >= spec.statusTicks() ? spec.secondary() : spec.damage();
        field.due = now + spec.warning();
        markRing(field.cast.caster, field.cast.art, field.center, spec.radius(), true);
        report(field, "ignited");
    }

    static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        GUARDS.values().removeIf(receipt -> receipt.tick() < now);
        for (var entry : new ArrayList<>(FIELDS.entrySet())) {
            Field field = entry.getValue();
            if (!field.valid() || field.charges <= 0) {
                FIELDS.remove(entry.getKey(), field);
                continue;
            }
            if (now >= field.due) {
                if (field.mechanic() == EarlyRealmArt.Mechanic.MATURE_FLAME && field.multiplier == 0) {
                    ignite(field);
                    continue;
                }
                FIELDS.remove(entry.getKey(), field);
                release(field);
                continue;
            }
            if (field.mechanic() == EarlyRealmArt.Mechanic.HEALING_BRANCH && heal(field)) {
                FIELDS.remove(entry.getKey(), field);
                continue;
            }
            if (now % EarlyRealmActions.WARNING_INTERVAL == 0) mark(field);
        }
    }

    private static void release(Field field) {
        switch (field.mechanic()) {
            case LINGERING_SLASH -> EarlyRealmActions.fanStrike(field.cast, field.center, field.cast.spec.secondary(), false);
            case WAITING_FLAME -> EarlyRealmActions.flameLine(field.cast, field.center);
            case MATURE_FLAME -> EarlyRealmActions.flameBurst(field.cast, field.center, field.multiplier);
            default -> { }
        }
    }

    private static void mark(Field field) {
        var cast = field.cast;
        var a = cast.spec;
        switch (field.mechanic()) {
            case LINGERING_SLASH -> markFan(cast.caster, cast.art, EarlyRealmActions.fan(cast, field.center, a.range()), true);
            case WAITING_FLAME -> markStrip(cast.caster, cast.art,
                    new Strip(cast.aim.anchor(), field.center, a.width()), true);
            case ARROW_RING -> markFan(cast.caster, cast.art,
                    EarlyRealmActions.fan(cast, cast.caster.position(), a.radius()), true);
            case BACK_GUARD -> {
                markFan(cast.caster, cast.art, EarlyRealmActions.fan(cast, field.center, a.range()), true);
                if (field.ally != null && behind(field, field.ally)) markTarget(cast.caster, cast.art, field.ally, true);
            }
            case STONE_SCREEN ->
                    markScreen(cast.caster, cast.art, field.center, cast.aim.forward(), a.width(), a.height(), true);
            default -> markRing(cast.caster, cast.art, field.center,
                    field.mechanic() == EarlyRealmArt.Mechanic.MATURE_FLAME && field.multiplier == 0 ? .75 : a.radius(), true);
        }
    }

    private static boolean heal(Field field) {
        var cast = field.cast;
        var ally = friends(cast.caster, field.center, cast.spec.radius()).stream()
                .filter(e -> e.getHealth() < e.getMaxHealth())
                .min(Comparator.comparingDouble((LivingEntity e) -> e.getHealth() / e.getMaxHealth())
                        .thenComparingInt(LivingEntity::getId)).orElse(null);
        if (ally == null) return false;
        int charges = ElementalEffects.charges(cast.caster, DamageType.WOOD);
        double bonus = 1 + charges * TechniqueTraits.number("branch_heal_bonus_per_charge");
        float amount = (float) (Math.min(ally.getMaxHealth(), cast.caster.getMaxHealth()) * cast.spec.potency() * bonus);
        if (!(HealingRuntime.heal(ally, amount, cast.art.id()) > 0)) return false;
        if (charges > 0) ElementalEffects.consume(cast.caster, DamageType.WOOD);
        markTarget(cast.caster, cast.art, ally, false);
        EarlyRealmActions.award(cast);
        report(field, "healed");
        return true;
    }

    private static List<LivingEntity> friends(LivingEntity caster, Vec3 center, double radius) {
        var bounds = new AABB(center, center).inflate(radius, 2.5, radius);
        return level(caster).getEntitiesOfClass(LivingEntity.class, bounds,
                ally -> isFriend(caster, ally) && withinRadius(center, ally.getBoundingBox(), radius)
                        && clear(caster, center.add(0, .8, 0), point(ally)));
    }

    private static boolean isFriend(LivingEntity caster, LivingEntity ally) {
        return ally.level() == caster.level() && ally.isAlive() && !ally.isRemoved() && !ally.isSpectator()
                && CombatActors.allied(caster, ally);
    }

    private static boolean behind(Field field, LivingEntity ally) {
        var cast = field.cast;
        return ally != cast.caster && isFriend(cast.caster, ally)
                && point(ally).subtract(field.center).dot(cast.aim.forward()) <= -.15
                && withinRadius(field.center, ally.getBoundingBox(), cast.spec.range())
                && clear(cast.caster, field.center.add(0, .8, 0), point(ally));
    }

    static void guard(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (FIELDS.isEmpty() || victim.level().isClientSide() || !(event.getAmount() > 0)
                || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(source.getEntity() instanceof LivingEntity attacker)) return;
        var field = FIELDS.values().stream()
                .filter(f -> f.mechanic() == EarlyRealmArt.Mechanic.BACK_GUARD && f.charges > 0
                        && (f.cast.caster == victim || f.ally == victim && behind(f, victim))
                        && f.cast.now() < f.due && f.valid() && CombatActors.mayHarm(attacker, victim)
                        && frontallyHit(f, source, attacker))
                .min(Comparator.comparingDouble((Field f) -> f.cast.caster.distanceToSqr(victim))
                        .thenComparingInt(f -> f.cast.caster.getId())).orElse(null);
        if (field == null) return;
        event.setAmount((float) (event.getAmount() * (1 - field.cast.spec.potency())));
        field.charges = 0;
        if (victim == field.cast.caster)
            GUARDS.put(victim.getUUID(), new GuardReceipt(source, field.cast.now(), field.cast.session.revision()));
        markTarget(field.cast.caster, field.cast.art, victim, false);
        EarlyRealmActions.award(field.cast);
        report(field, "guarded");
    }

    private static boolean frontallyHit(Field field, DamageSource source, LivingEntity attacker) {
        Vec3 delta = attacker.position().subtract(field.center);
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getDeltaMovement().horizontalDistanceSqr() > .0001)
            delta = projectile.getDeltaMovement().scale(-1);
        return horizontal(delta).dot(field.cast.aim.forward()) >= Math.cos(Math.toRadians(field.cast.spec.arc() / 2));
    }

    static boolean confirmGuard(LivingEntity victim, DamageSource source) {
        var receipt = GUARDS.get(victim.getUUID());
        if (receipt == null || receipt.source() != source || receipt.tick() != level(victim).getServer().overworld().getGameTime())
            return false;
        GUARDS.remove(victim.getUUID());
        CombatDisciplines.guarded(victim, receipt.revision());
        if (victim instanceof ServerPlayer user) com.example.myvillage.wilderness.ExpeditionCombat.guarded(user);
        return true;
    }

    public static void projectile(EntityTickEvent.Pre event) {
        if (FIELDS.isEmpty() || !(event.getEntity() instanceof Projectile shot) || shot.level().isClientSide()
                || !(shot instanceof Arrow || shot instanceof SpectralArrow || shot instanceof SmallFireball
                    || shot instanceof com.example.myvillage.entity.undead.UndeadArrowEntity undead && undead.directShot())
                || !(shot.getOwner() instanceof LivingEntity attacker)) return;
        Vec3 from = shot.position();
        Vec3 velocity = shot.getDeltaMovement();
        if (velocity.lengthSqr() < 1e-5) return;
        Vec3 to = from.add(velocity);
        Field chosen = null;
        double fraction = Double.POSITIVE_INFINITY;
        for (Field field : FIELDS.values()) {
            if ((field.mechanic() != EarlyRealmArt.Mechanic.STONE_SCREEN
                    && field.mechanic() != EarlyRealmArt.Mechanic.ARROW_RING) || field.charges <= 0
                    || field.cast.world != shot.level() || field.cast.now() >= field.due) continue;
            double contact = field.mechanic() == EarlyRealmArt.Mechanic.ARROW_RING
                    ? ringContact(field, from, velocity) : screenCrossing(field.center, field.cast.aim.forward(),
                            field.cast.spec.width(), field.cast.spec.height(), from, to);
            if (!Double.isFinite(contact) || contact > fraction || !field.valid()
                    || !CombatActors.mayHarm(attacker, field.cast.caster)) continue;
            Vec3 point = from.lerp(to, contact);
            if (!clear(field.cast.caster, from, point)
                    || (field.mechanic() == EarlyRealmArt.Mechanic.ARROW_RING
                        && !clear(field.cast.caster, field.cast.caster.getEyePosition(), point))) continue;
            if (chosen != null && contact == fraction && field.cast.caster.getId() >= chosen.cast.caster.getId()) continue;
            chosen = field;
            fraction = contact;
        }
        if (chosen == null) return;
        chosen.charges--;
        shot.discard();
        event.setCanceled(true);
        var cast = chosen.cast;
        Vec3 contact = from.lerp(to, fraction);
        markRing(cast.caster, cast.art, contact, .5, false);
        EarlyRealmActions.award(cast);
        report(chosen, "intercepted");
        if (chosen.mechanic() == EarlyRealmArt.Mechanic.ARROW_RING)
            EarlyRealmActions.reflectArrow(cast, horizontal(velocity.scale(-1)));
    }

    private static double ringContact(Field field, Vec3 from, Vec3 motion) {
        var cast = field.cast;
        Vec3 torso = cast.caster.position().add(0, 1, 0);
        Vec3 offset = from.subtract(torso);
        double approaching = offset.dot(motion);
        if (approaching >= 0) return Double.POSITIVE_INFINITY;
        double outer = offset.lengthSqr() - cast.spec.radius() * cast.spec.radius();
        double discriminant = approaching * approaching - motion.lengthSqr() * outer;
        if (discriminant < 0) return Double.POSITIVE_INFINITY;
        double fraction = outer <= 0 ? 0 : (-approaching - Math.sqrt(discriminant)) / motion.lengthSqr();
        Vec3 contact = offset.add(motion.scale(fraction));
        return fraction <= 1 && horizontal(contact).dot(cast.aim.forward()) >= Math.cos(Math.toRadians(cast.spec.arc() / 2))
                ? fraction : Double.POSITIVE_INFINITY;
    }

    static void forget(UUID caster) {
        FIELDS.keySet().removeIf(key -> key.caster().equals(caster));
        GUARDS.remove(caster);
    }

    static void clearAll() { FIELDS.clear(); GUARDS.clear(); }

    private static Key key(LivingEntity caster, ArtDefinition art) { return new Key(caster.getUUID(), art.id()); }

    private static void report(Field field, String event) {
        LOG.info("EARLY_REALM actor={} art={} event={} charges={}",
                CombatCaster.name(field.cast.caster), field.cast.art.id(), event, field.charges);
    }
}
