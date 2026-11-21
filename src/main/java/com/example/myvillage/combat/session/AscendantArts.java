package com.example.myvillage.combat.session;

import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.art.AscendantArt;
import com.example.myvillage.combat.art.GoldenCoreArt;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.elemental.ElementalEffects;
import com.example.myvillage.combat.foundation.ControlService;
import com.example.myvillage.combat.foundation.HealingRuntime;
import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatCaster;
import com.example.myvillage.combat.runtime.CombatHitResolver;
import com.example.myvillage.combat.runtime.CombatTargets;
import com.example.myvillage.combat.runtime.SwordArmorBreak;
import com.example.myvillage.cultivation.growth.TechniqueGrowthRuntime;
import com.example.myvillage.stats.DamageType;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.myvillage.combat.session.GoldenCoreGeometry.*;

final class AscendantArts {
    private static final Logger LOG = LoggerFactory.getLogger(AscendantArts.class);
    static final int WARN_INTERVAL = 4;
    static final double EPSILON = .0001;

    private AscendantArts() {}

    record Aim(Vec3 position, Vec3 forward, Vec3 anchor, Entity target, ServerPlayer ally) {}
    record Hit(Entity target, double multiplier) {}

    static final class Cast {
        final LivingEntity actor;
        final ArtDefinition art;
        final AscendantArt spec;
        final AscendantArt.Geometry shape;
        final AscendantArt.Timing time;
        final AscendantArt.Effect effect;
        final AscendantArt.Limits limits;
        final ServerLevel level;
        final ItemStack held;
        final Aim aim;
        final int slot;
        final boolean paid;
        final double base;
        CombatSession session;
        long revision;
        boolean mastery;
        double damageTaken;

        Cast(LivingEntity actor, ArtDefinition art, CombatSession session, Aim aim, boolean paid) {
            this.actor = actor;
            this.art = art;
            this.session = session;
            this.aim = aim;
            this.paid = paid;
            spec = art.ascendant().orElseThrow();
            shape = spec.geometry();
            time = spec.timing();
            effect = spec.effect();
            limits = spec.limits();
            level = level(actor);
            held = actor.getMainHandItem();
            slot = CombatCaster.slot(actor);
            base = spec.element().map(element -> RealmArtCombat.base(actor, element, paid)).orElse(0.0);
        }

        long now() { return level.getServer().overworld().getGameTime(); }

        boolean valid() {
            return level == actor.level() && CombatCaster.active(actor) && CombatCaster.inventoryFree(actor)
                    && actor.getMainHandItem() == held && CombatCaster.slot(actor) == slot
                    && session.hasActiveAction() && session.revision() == revision && CombatArtActions.authorized(actor, art);
        }

        void detach() {
            var style = CombatStyles.bundled().move(art.phases().getFirst().moveId()).orElseThrow().style();
            float yaw = session.facingYaw();
            var snapshot = new CombatSession(style);
            snapshot.startExternal(art.move(0, 1), level.getGameTime(), BuiltInRegistries.ITEM.getKey(held.getItem()),
                    level.dimension().location(), yaw, paid);
            session = snapshot;
            revision = snapshot.revision();
        }

        boolean loaded(Vec3 pos) { return level.hasChunkAt(BlockPos.containing(pos)); }

        boolean anchored(Vec3 point) {
            return point != null && available(actor, point, shape.range() + .12);
        }

        boolean legal(Entity target) { return CombatHitResolver.legalTarget(actor, target, session); }
    }

    static Aim prepare(LivingEntity actor, CombatSession session, ArtDefinition art) {
        var spec = art.ascendant().orElseThrow();
        double range = spec.geometry().range();
        double arc = GoldenCoreArt.rules().aimArc();
        Vec3 from = actor.position();
        Vec3 forward = horizontal(Vec3.directionFromRotation(0, actor.getYRot()));
        Entity target = null;
        ServerPlayer ally = null;
        Vec3 anchor = null;
        switch (spec.mechanic()) {
            case TWIN_BLADE, ARMOR_STAR -> {
                target = acquire(actor, session, range, arc, true, e -> true);
                if (target == null) return null;
                if (spec.mechanic() == AscendantArt.Mechanic.TWIN_BLADE) {
                    anchor = ground(actor, new Vec3(point(target).x, target.getBoundingBox().minY, point(target).z));
                    if (anchor == null || !available(actor, anchor, range)) return null;
                }
                forward = direction(from, point(target), forward);
            }
            case GUARDING_ECHO, RESCUE_VINE -> {
                boolean rescue = spec.mechanic() == AscendantArt.Mechanic.RESCUE_VINE;
                if (rescue && !(actor instanceof ServerPlayer)) return null;
                ally = aimedFriend(actor, range);
                if (rescue && ally == null) return null;
            }
            case FALLING_PALM -> {
                if (actor.onGround() || actor.getDeltaMovement().y >= -EPSILON || actor.isFallFlying()
                        || actor instanceof ServerPlayer p && p.getAbilities().flying) return null;
                anchor = groundAim(actor, session, range, arc);
                if (anchor == null || from.y - anchor.y < 1 || from.y - anchor.y > spec.geometry().height()
                        || !available(actor, anchor, range)
                        || !level(actor).noCollision(actor, actor.getBoundingBox().move(anchor.subtract(from)).deflate(EPSILON)))
                    return null;
            }
            case SWORD_GATE, OPPOSING_SPEARS, SPEAR_POSTS, SPEAR_ARRAY, GOLD_MIRROR, ARMOR_STAMP,
                    LINKED_ROOTS, WOOD_HARVEST, ANCIENT_SHADE, MEETING_TIDES, RETURNING_SEA,
                    PAIRED_FURNACES, FIRE_ENCLOSURE, FIRE_HARVEST, MOVING_WALL, STEADY_PLATFORM, MOUNTAIN_SHELL -> {
                anchor = groundAim(actor, session, range, arc);
                if (anchor == null || !available(actor, anchor, range)) return null;
            }
            case FLOW_LANE, FREEZING_CREST, FOLDING_EARTH -> {
                anchor = ground(actor, from);
                if (anchor == null) return null;
            }
            case CLEAVING_CHARGE, PIERCING_FIST -> {
                target = acquire(actor, session, range, arc, true, e -> true);
                if (target != null) forward = direction(from, point(target), forward);
            }
            case EVADE_ECHO, SWORD_POINTS, DRAGON_STANCE, GOLD_PLATES, FIRE_ESCAPE -> { }
        }
        return new Aim(from, forward, anchor, target, ally);
    }

    static void resolve(Cast cast, int frame) {
        if (frame < cast.time.windup()) {
            if (frame % WARN_INTERVAL == 0) warn(cast);
            return;
        }
        if (frame == cast.time.windup()) strike(cast);
        if (cast.spec.mechanic() == AscendantArt.Mechanic.DRAGON_STANCE
                && frame == cast.time.windup() + cast.time.duration()) {
            double steps = Math.floor(cast.damageTaken / (cast.actor.getMaxHealth() * cast.effect.budget()));
            hitFan(cast, cast.actor.position(), cast.aim.forward(),
                    Math.min(cast.effect.secondary(), cast.effect.damage() + steps * cast.effect.bonus()));
        }
    }

    private static void warn(Cast cast) {
        Vec3 at = cast.actor.position();
        switch (cast.spec.mechanic()) {
            case FALLING_PALM, ARMOR_STAMP, WOOD_HARVEST, ANCIENT_SHADE, RETURNING_SEA,
                    FIRE_HARVEST, STEADY_PLATFORM, MOUNTAIN_SHELL ->
                    markRing(cast.actor, cast.art, cast.aim.anchor(), cast.shape.radius(), true);
            case DRAGON_STANCE, EVADE_ECHO ->
                    markFan(cast.actor, cast.art, new Fan(at, cast.aim.forward(), cast.shape.range(), cast.shape.arc()), true);
            case ARMOR_STAR -> markTarget(cast.actor, cast.art, cast.aim.target(), true);
            case MOVING_WALL ->
                    markScreen(cast.actor, cast.art, cast.aim.anchor(), cast.aim.forward(), cast.shape.width(), cast.shape.height(), true);
            case PIERCING_FIST, CLEAVING_CHARGE, FOLDING_EARTH, FLOW_LANE, FREEZING_CREST ->
                    mark(cast, ray(cast, at, cast.aim.forward(), cast.shape.range(), cast.shape.width()), true);
            default -> {
                Vec3 where = cast.aim.anchor() == null ? at : cast.aim.anchor();
                markRing(cast.actor, cast.art, where, .8, true);
            }
        }
    }

    private static void strike(Cast cast) {
        Vec3 at = cast.actor.position();
        Vec3 forward = cast.aim.forward();
        switch (cast.spec.mechanic()) {
            case TWIN_BLADE -> {
                hitStrip(cast, ray(cast, at, forward, cast.shape.length(), cast.shape.width()), cast.effect.damage(), false);
                if (cast.valid() && cast.anchored(cast.aim.anchor())) {
                    Vec3 other = cast.aim.anchor().add(forward.scale(cast.shape.reach()));
                    if (cast.loaded(other) && clear(cast.actor, cast.aim.anchor().add(0, .8, 0), other.add(0, .8, 0))) {
                        var line = ray(cast, other, forward.scale(-1), cast.shape.length(), cast.shape.width());
                        AscendantFields.delayedLines(cast, List.of(line), cast.effect.secondary(), cast.time.delay(), null);
                    }
                }
            }
            case OPPOSING_SPEARS -> opposingSpears(cast, at);
            case CLEAVING_CHARGE -> charge(cast, at);
            case PIERCING_FIST -> piercingFist(cast, at);
            case ARMOR_STAR -> armorStar(cast);
            case ARMOR_STAMP -> armorStamp(cast);
            case WOOD_HARVEST -> woodHarvest(cast);
            case FIRE_HARVEST -> fireHarvest(cast);
            case GUARDING_ECHO, GOLD_PLATES -> AscendantWards.place(cast);
            case DRAGON_STANCE -> markFan(cast.actor, cast.art, new Fan(at, forward, cast.shape.range(), cast.shape.arc()), true);
            default -> AscendantFields.place(cast);
        }
    }

    private static void opposingSpears(Cast cast, Vec3 origin) {
        Vec3 other = cast.aim.anchor();
        if (!cast.anchored(other) || horizontalDistance(origin, other) < 1) return;
        Vec3 forward = direction(origin, other, cast.aim.forward());
        var hits = new LinkedHashMap<Integer, Hit>();
        for (Strip line : List.of(ray(cast, origin, forward, cast.shape.length(), cast.shape.width()),
                ray(cast, other, forward.scale(-1), cast.shape.length(), cast.shape.width()))) {
            mark(cast, line, false);
            collect(cast, hits, strip(cast, line, true),
                    target -> spearTip(line.start(), target) ? cast.effect.secondary() : cast.effect.damage());
        }
        commit(cast, hits);
    }

    private static void charge(Cast cast, Vec3 start) {
        var direction = cast.aim.forward();
        if (!ControlService.isRooted(cast.actor)) step(cast.actor, direction, cast.shape.step());
        var moved = cast.actor.position();
        var whole = ray(cast, start, direction, horizontalDistance(start, moved) + cast.shape.length(), cast.shape.width());
        mark(cast, whole, false);
        var targets = strip(cast, whole, false);
        boolean heavy = false;
        for (Entity target : targets) {
            Vec3 offset = point(target).subtract(moved);
            if (!heavy && offset.dot(direction) >= 0 && spearTip(moved, target)) {
                hit(cast, target, cast.effect.damage());
                heavy = true;
            } else hit(cast, target, cast.effect.secondary());
        }
    }

    private static void piercingFist(Cast cast, Vec3 origin) {
        var main = ray(cast, origin, cast.aim.forward(), cast.shape.range(), cast.shape.width());
        mark(cast, main, false);
        var hits = strip(cast, main, false);
        if (hits.isEmpty()) return;
        Entity first = hits.getFirst();
        if (hit(cast, first, cast.effect.damage())) {
            Vec3 at = first.position();
            var line = ray(cast, at, cast.aim.forward(), cast.shape.length(), cast.shape.width());
            AscendantFields.delayedLines(cast, List.of(line), cast.effect.secondary(), cast.time.delay(), CombatTargets.hitId(first));
        }
    }

    private static void armorStar(Cast cast) {
        var target = cast.aim.target();
        if (!cast.legal(target) || !inReach(cast.actor, target, cast.shape.range()) || !visible(cast.actor, target)) return;
        markLine(cast.actor, cast.art, cast.actor.getEyePosition(), point(target), false, 22);
        boolean marked = CombatTargets.owner(target) instanceof LivingEntity living && SwordArmorBreak.active(living);
        hit(cast, target, marked ? cast.effect.secondary() : cast.effect.damage());
    }

    private static void armorStamp(Cast cast) {
        Vec3 at = cast.aim.anchor();
        if (!cast.anchored(at)) return;
        int stacks = ElementalEffects.consume(cast.actor, DamageType.METAL);
        markRing(cast.actor, cast.art, at, cast.shape.radius(), false);
        markRing(cast.actor, cast.art, at, cast.shape.inner(), false);
        boolean inner = true;
        for (Entity target : area(cast, at, cast.shape.radius())) {
            if (inner && withinRadius(at, target.getBoundingBox(), cast.shape.inner())) {
                inner = false;
                if (hit(cast, target, cast.effect.damage() + stacks * cast.effect.bonus())
                        && CombatTargets.owner(target) instanceof LivingEntity living && living.isAlive())
                    SwordArmorBreak.apply(living, cast.effect.control(), cast.time.statusTicks());
            } else hit(cast, target, cast.effect.secondary());
        }
    }

    private static void woodHarvest(Cast cast) {
        Vec3 at = cast.aim.anchor();
        if (!cast.anchored(at) || !(cast.base > 0)) return;
        markRing(cast.actor, cast.art, at, cast.shape.radius(), false);
        double healing = 0;
        var candidates = distinct(inArea(cast.actor, cast.session, at, cast.shape.radius()).stream()
                .filter(target -> ElementalEffects.harvestDamage(cast.actor, target, DamageType.WOOD) > 0).toList(), cast.limits.targets());
        for (Entity target : candidates) {
            double remains = ElementalEffects.harvestDamage(cast.actor, target, DamageType.WOOD);
            double multiplier = cast.effect.damage() * remains / (cast.base * RealmArtCombat.pvpDamage(cast.actor, target));
            if (multiplier > 0 && hit(cast, target, multiplier)) {
                ElementalEffects.harvest(cast.actor, target, DamageType.WOOD);
                healing += remains * cast.effect.secondary();
            }
        }
        healing = Math.min(healing, cast.actor.getMaxHealth() * cast.effect.budget());
        for (var friend : friends(cast.actor, at, cast.shape.radius()).stream()
                .filter(e -> e.getHealth() < e.getMaxHealth()).sorted(byHealth()).limit(cast.limits.count()).toList()) {
            double limit = Math.min(cast.actor.getMaxHealth(), friend.getMaxHealth()) * cast.effect.control();
            if (healing <= 0) break;
            healing -= heal(cast, friend, Math.min(healing, limit));
        }
    }

    private static void fireHarvest(Cast cast) {
        Vec3 at = cast.aim.anchor();
        if (!cast.anchored(at)) return;
        markRing(cast.actor, cast.art, at, cast.shape.radius(), false);
        var aim = cast.actor.getLookAngle();
        var eye = cast.actor.getEyePosition();
        var candidates = area(cast, at, cast.shape.radius()).stream().sorted(Comparator.comparingDouble(
                (Entity e) -> -point(e).subtract(eye).normalize().dot(aim)).thenComparingInt(Entity::getId)).toList();
        boolean first = true;
        for (Entity target : candidates) {
            double remains = ElementalEffects.harvestDamage(cast.actor, target, DamageType.FIRE);
            double extra = cast.base <= 0 ? 0 : Math.min(remains / cast.base, first ? cast.effect.bonus() : cast.effect.control());
            if (hit(cast, target, (first ? cast.effect.damage() : cast.effect.secondary()) + extra) && remains > 0)
                ElementalEffects.harvest(cast.actor, target, DamageType.FIRE);
            first = false;
        }
    }

    static void hitFan(Cast cast, Vec3 center, Vec3 forward, double multiplier) {
        var fan = new Fan(center, forward, cast.shape.range(), cast.shape.arc());
        markFan(cast.actor, cast.art, fan, false);
        for (var foe : distinct(inFan(cast.actor, cast.session, fan), cast.limits.targets())) hit(cast, foe, multiplier);
    }

    static void hitStrip(Cast cast, Strip line, double amount, boolean projected) {
        mark(cast, line, false);
        for (var foe : strip(cast, line, projected)) hit(cast, foe, amount);
    }

    static boolean hit(Cast cast, Entity target, double multiplier) {
        return hit(cast, target, multiplier, true);
    }

    static boolean hit(Cast cast, Entity target, double multiplier, boolean primary) {
        if (!cast.valid() || !cast.legal(target) || multiplier <= 0 || !Double.isFinite(multiplier)) return false;
        float damage = RealmArtCombat.hit(cast.actor, target, cast.art, cast.session, multiplier, cast.base);
        if (!(damage > 0) || !cast.valid()) return false;
        award(cast);
        if (primary && cast.spec.element().orElse(null) == DamageType.FIRE)
            GoldenCoreActions.onDirectFire(cast.actor, target);
        LOG.info("ASCENDANT_ART actor={} art={} target={} multiplier={} damage={}",
                CombatCaster.name(cast.actor), cast.art.id(), target.getId(), multiplier, damage);
        return true;
    }

    static void award(Cast cast) {
        if (cast.mastery) return;
        cast.mastery = true;
        TechniqueGrowthRuntime.hit(cast.actor, cast.art.id());
    }

    static void collect(Cast cast, Map<Integer, Hit> hits, List<Entity> targets, ToDoubleFunction<Entity> multiplier) {
        for (Entity target : targets) {
            int id = CombatTargets.hitId(target);
            if (!hits.containsKey(id) && hits.size() >= cast.limits.targets()) continue;
            hits.merge(id, new Hit(target, multiplier.applyAsDouble(target)), (a, b) -> new Hit(a.target(), a.multiplier() + b.multiplier()));
        }
    }

    static void commit(Cast cast, Map<Integer, Hit> hits) {
        for (Hit hit : hits.values()) hit(cast, hit.target(), hit.multiplier());
    }

    static void mark(Cast cast, Strip line, boolean warning) {
        if (line.length() > EPSILON) markStrip(cast.actor, cast.art, line, warning);
    }

    static Strip ray(Cast cast, Vec3 origin, Vec3 forward, double length, double width) {
        return new Strip(origin, unobstructedEnd(cast.actor, origin, origin.add(forward.scale(length))), width);
    }

    static Strip groundRay(Cast cast, Vec3 from, Vec3 forward, double length, double width) {
        var path = groundPath(cast.actor, from, forward, length);
        return new Strip(from, path.isEmpty() ? from : path.getLast(), width);
    }

    static List<Entity> strip(Cast cast, Strip line, boolean projected) {
        if (line.length() < EPSILON) return List.of();
        return distinct(projected ? alongRay(cast.actor, cast.session, line) : inStrip(cast.actor, cast.session, line),
                cast.limits.targets());
    }

    static List<Entity> distinct(List<Entity> targets, int limit) {
        return CombatTargets.firstDistinct(targets, CombatTargets::hitId, limit);
    }

    static List<Entity> area(Cast cast, Vec3 at, double radius) {
        return distinct(inArea(cast.actor, cast.session, at, radius), cast.limits.targets());
    }

    static Vec3 direction(Vec3 from, Vec3 to, Vec3 fallback) {
        Vec3 dir = horizontal(to.subtract(from));
        return dir.horizontalDistanceSqr() < .5 ? fallback : dir;
    }

    static boolean spearTip(Vec3 origin, Entity target) {
        double range = Math.sqrt(target.getBoundingBox().distanceToSqr(origin));
        return range >= GoldenCoreArt.rules().spearTipMin() && range <= GoldenCoreArt.rules().spearTipMax() + EPSILON;
    }

    static void displace(Cast cast, Entity entity, Vec3 toward, double max) {
        if (entity instanceof LivingEntity target && RealmArtCombat.canDisplace(cast.actor, target))
            step(target, horizontal(toward), max * RealmArtCombat.displacementFactor(cast.actor, target));
    }

    static void slow(Cast cast, Entity entity, int ticks, double fraction) {
        if (CombatTargets.owner(entity) instanceof LivingEntity living)
            ControlService.slow(cast.actor, living, cast.art.id(), ticks, fraction);
    }

    static boolean friend(LivingEntity source, LivingEntity target) {
        return source.level() == target.level() && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && CombatActors.allied(source, target);
    }

    static List<LivingEntity> friends(LivingEntity source, Vec3 at, double radius) {
        var bounds = new AABB(at, at).inflate(radius + .1, 2.5, radius + .1);
        return level(source).getEntitiesOfClass(LivingEntity.class, bounds, e -> friend(source, e)
                && withinRadius(at, e.getBoundingBox(), radius) && clear(source, at.add(0, .8, 0), point(e)))
                .stream().sorted(Comparator.comparingDouble((LivingEntity e) -> e.distanceToSqr(at))
                        .thenComparingInt(LivingEntity::getId)).toList();
    }

    static Comparator<LivingEntity> byHealth() {
        return Comparator.comparingDouble((LivingEntity e) -> e.getHealth() / e.getMaxHealth()).thenComparingInt(LivingEntity::getId);
    }

    static float heal(Cast cast, LivingEntity friend, double amount) {
        float healed = HealingRuntime.healCapped(friend, (float) amount, cast.art.id());
        if (healed > 0) {
            markTarget(cast.actor, cast.art, friend, false);
            award(cast);
        }
        return healed;
    }

    private static ServerPlayer aimedFriend(LivingEntity source, double range) {
        var eye = source.getEyePosition();
        var look = source.getLookAngle();
        var end = eye.add(look.scale(range));
        double minimum = Math.cos(Math.toRadians(GoldenCoreArt.rules().aimArc() / 2));
        return friends(source, source.position(), range).stream().filter(e -> e != source && e instanceof ServerPlayer)
                .map(ServerPlayer.class::cast)
                .filter(e -> inReach(source, e, range) && visible(source, e))
                .filter(e -> e.getBoundingBox().clip(eye, end).isPresent() || point(e).subtract(eye).normalize().dot(look) >= minimum)
                .min(Comparator.<ServerPlayer>comparingInt(e -> e.getBoundingBox().clip(eye, end).isPresent() ? 0 : 1)
                        .thenComparingDouble(e -> -point(e).subtract(eye).normalize().dot(look))
                        .thenComparingDouble(e -> e.distanceToSqr(source)).thenComparingInt(Entity::getId)).orElse(null);
    }
}
