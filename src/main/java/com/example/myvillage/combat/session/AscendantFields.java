package com.example.myvillage.combat.session;

import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.art.AscendantArt.Mechanic;
import com.example.myvillage.combat.elemental.ElementalEffects;
import com.example.myvillage.combat.foundation.ControlService;
import com.example.myvillage.combat.foundation.ShieldRuntime;
import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatCaster;
import com.example.myvillage.combat.runtime.CombatTargets;
import com.example.myvillage.stats.DamageType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.myvillage.combat.session.AscendantArts.*;
import static com.example.myvillage.combat.session.GoldenCoreGeometry.*;

public final class AscendantFields {
    private static final Logger LOG = LoggerFactory.getLogger(AscendantFields.class);
    private static final Map<Key, Field> ACTIVE = new LinkedHashMap<>();
    private record Key(UUID actor, ResourceLocation art) {}
    private record Passage(Strip strip, double multiplier) {}

    static final class Field {
        final Cast cast;
        final Set<Integer> touched = new HashSet<>();
        final Set<Integer> helped = new HashSet<>();
        final Map<Integer, Integer> contacts = new HashMap<>();
        final Map<Integer, Long> dwell = new HashMap<>();
        final ArrayList<Vec3> points = new ArrayList<>();
        final long born;
        Vec3 center;
        Vec3 direction;
        Vec3 other;
        Vec3 crestEnd;
        List<Passage> passages = List.of();
        long expires;
        long next;
        int phase;
        int charges;
        int armedPosts;
        Integer excluded;
        boolean continued;
        boolean released;
        boolean sneak;
        double shield;

        Field(Cast cast, Vec3 where, int duration) {
            this.cast = cast;
            center = where;
            direction = cast.aim.forward();
            born = cast.now();
            expires = born + duration;
        }

        Mechanic kind() { return cast.spec.mechanic(); }

        boolean valid() {
            if (!cast.valid() || !cast.loaded(center)) return false;
            if (cast.aim.anchor() == null || kind() == Mechanic.FALLING_PALM || kind() == Mechanic.FREEZING_CREST
                    || kind() == Mechanic.FLOW_LANE || kind() == Mechanic.FOLDING_EARTH) return true;
            return cast.anchored(center);
        }
    }

    private AscendantFields() {}

    private static Field install(Cast cast, Vec3 at, int life) {
        cast.detach();
        var field = new Field(cast, at, life);
        ACTIVE.put(new Key(cast.actor.getUUID(), cast.art.id()), field);
        LOG.info("ASCENDANT_FIELD actor={} art={} event=placed", CombatCaster.name(cast.actor), cast.art.id());
        return field;
    }

    static void delayedLines(Cast cast, List<Strip> lines, double multiplier, int delay, Integer excluded) {
        if (!cast.valid() || lines.isEmpty()) return;
        var field = install(cast, cast.actor.position(), delay);
        field.passages = lines.stream().map(line -> new Passage(line, multiplier)).toList();
        field.excluded = excluded;
        field.released = true;
        markField(field);
    }

    static void place(Cast cast) {
        if (!cast.valid()) return;
        Vec3 center = cast.aim.anchor() == null ? cast.actor.position() : cast.aim.anchor();
        if (cast.aim.anchor() != null && !cast.anchored(center)) return;
        int life = cast.time.duration();
        switch (cast.spec.mechanic()) {
            case SPEAR_ARRAY -> life = cast.time.interval() * cast.limits.count() + 1;
            case LINKED_ROOTS -> life = cast.time.window();
            case MEETING_TIDES, FOLDING_EARTH -> life = cast.time.delay();
            case FREEZING_CREST -> life = (int) Math.ceil(cast.shape.length() / cast.shape.reach()) * cast.time.interval() + 1;
            default -> { }
        }
        var field = install(cast, center, life);
        switch (field.kind()) {
            case SWORD_GATE -> field.passages = List.of(new Passage(crossLine(cast, center, field.direction, cast.shape.length()),
                    cast.effect.damage()));
            case SPEAR_POSTS -> {
                Vec3 side = lateral(field.direction);
                for (int index = 0; index < cast.limits.count(); index++) {
                    Vec3 start = center.add(side.scale((index - (cast.limits.count() - 1) / 2.0) * cast.shape.gap()));
                    Vec3 floor = ground(cast.actor, start);
                    field.points.add(floor == null || !clear(cast.actor, center.add(0,.8,0), floor.add(0,.8,0)) ? start : floor);
                    if (floor != null && clear(cast.actor, center.add(0,.8,0), floor.add(0,.8,0)))
                        field.armedPosts |= 1 << index;
                }
            }
            case SPEAR_ARRAY -> field.next = field.born + cast.time.interval();
            case RESCUE_VINE -> {
                ServerPlayer ally = (ServerPlayer) cast.aim.ally();
                field.sneak = ally.isShiftKeyDown();
                ally.sendSystemMessage(Component.literal(CombatCaster.name(cast.actor)
                        + "以牵藤救人相援。2 秒内按潜行可接受。"));
            }
            case MEETING_TIDES -> startTides(field);
            case FLOW_LANE, FOLDING_EARTH -> {
                Strip line = groundRay(cast, center, field.direction, cast.shape.length(), cast.shape.width());
                field.passages = List.of(new Passage(line, cast.effect.damage()));
                if (field.kind() == Mechanic.FOLDING_EARTH) firstEarth(field, line);
            }
            case RETURNING_SEA -> {
                for (Entity target : area(cast, center, cast.shape.radius()))
                    if (hit(cast, target, cast.effect.damage()))
                        displace(cast, target, center.subtract(target.position()),
                                Math.min(cast.effect.push(), horizontalDistance(center, target.position())));
            }
            case FREEZING_CREST -> {
                field.crestEnd = groundRay(cast, center, field.direction, cast.shape.length(), cast.shape.width()).end();
                field.other = center;
                field.next = field.born + cast.time.interval();
            }
            case PAIRED_FURNACES -> field.other = cast.actor.position();
            case MOVING_WALL -> field.charges = cast.limits.count();
            case MOUNTAIN_SHELL -> field.shield = cast.actor.getMaxHealth() * cast.effect.budget();
            default -> { }
        }
        markField(field);
    }

    static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (var entry : List.copyOf(ACTIVE.entrySet())) {
            var field = entry.getValue();
            if (!field.valid()) { ACTIVE.remove(entry.getKey(), field); continue; }
            if (now >= field.expires) {
                ACTIVE.remove(entry.getKey(), field);
                release(field);
                continue;
            }
            boolean finished = switch (field.kind()) {
                case SWORD_GATE -> { crossEnemies(field, field.passages.getFirst().strip(), true); yield false; }
                case SPEAR_POSTS -> posts(field);
                case SPEAR_ARRAY -> array(field, now);
                case FALLING_PALM -> landing(field);
                case LINKED_ROOTS -> { if (field.continued) linkedRoots(field); yield false; }
                case RESCUE_VINE -> rescue(field);
                case ANCIENT_SHADE -> { shade(field, now); yield false; }
                case FLOW_LANE -> { flow(field); yield false; }
                case FREEZING_CREST -> { crest(field, now); yield false; }
                case FIRE_ENCLOSURE -> { fireEnclosure(field, now); yield false; }
                default -> false;
            };
            if (finished) ACTIVE.remove(entry.getKey(), field);
            else if (now % WARN_INTERVAL == 0) markField(field);
        }
    }

    private static void release(Field field) {
        Cast cast = field.cast;
        if (field.released && !field.passages.isEmpty()) {
            var hits = new LinkedHashMap<Integer, Hit>();
            for (Passage passage : field.passages) {
                var old = passage.strip();
                var line = ray(cast, old.start(), old.forward(), old.length(), old.width());
                mark(cast, line, false);
                collect(cast, hits, alongRay(cast.actor, cast.session, line).stream()
                        .filter(e -> field.excluded == null || CombatTargets.hitId(e) != field.excluded)
                        .toList(), e -> passage.multiplier());
            }
            for (Hit h : hits.values()) {
                double amount = h.multiplier();
                if (field.kind() == Mechanic.SWORD_POINTS && amount > cast.effect.damage() + EPSILON)
                    amount += cast.effect.bonus();
                hit(cast, h.target(), amount, false);
            }
            return;
        }
        switch (field.kind()) {
            case EVADE_ECHO -> {
                if (!field.released) return;
                var hits = new LinkedHashMap<Integer, Hit>();
                for (Vec3 where : List.of(field.center, cast.actor.position())) {
                    Fan fan = new Fan(where, field.direction, cast.shape.range(), cast.shape.arc());
                    markFan(cast.actor, cast.art, fan, false);
                    collect(cast, hits, inFan(cast.actor, cast.session, fan), e -> cast.effect.damage());
                }
                commit(cast, hits);
            }
            case MEETING_TIDES -> impactRing(field, cast.shape.radius(), cast.effect.secondary());
            case RETURNING_SEA -> exitSea(field);
            case FREEZING_CREST -> {
                if (!field.continued) {
                    freeze(field);
                    ACTIVE.put(new Key(cast.actor.getUUID(), cast.art.id()), field);
                }
            }
            case FIRE_ESCAPE -> { if (field.released) impactRing(field, cast.shape.radius(), cast.effect.damage()); }
            case FOLDING_EARTH -> {
                Strip first = field.passages.getFirst().strip();
                var center = groundRay(cast, first.start(), first.forward(), first.length(), cast.shape.inner());
                mark(cast, center, false);
                for (Entity target : strip(cast, center, false))
                    if (followsGround(cast.actor, center.lineOrigin(target).add(0,-.8,0), target))
                        hit(cast, target, cast.effect.secondary());
            }
            default -> { }
        }
    }

    private static void impactRing(Field field, double radius, double multiplier) {
        var cast = field.cast;
        markRing(cast.actor, cast.art, field.center, radius, false);
        for (Entity target : area(cast, field.center, radius)) hit(cast, target, multiplier, false);
    }

    private static void markField(Field field) {
        var cast = field.cast;
        if (field.released && !field.passages.isEmpty()) {
            field.passages.forEach(p -> mark(cast, p.strip(), true));
            return;
        }
        switch (field.kind()) {
            case EVADE_ECHO -> {
                if (field.released) {
                    for (Vec3 at : List.of(field.center, cast.actor.position()))
                        markFan(cast.actor, cast.art, new Fan(at, field.direction, cast.shape.range(), cast.shape.arc()), true);
                } else markRing(cast.actor, cast.art, field.center, .85, true);
            }
            case SWORD_POINTS -> field.points.forEach(at -> markRing(cast.actor, cast.art, at, .55, true));
            case SWORD_GATE, FLOW_LANE -> field.passages.forEach(p -> mark(cast, p.strip(), true));
            case SPEAR_POSTS -> {
                for (int index = 0; index < field.points.size(); index++)
                    if ((field.armedPosts & 1 << index) != 0)
                        mark(cast, postLine(field, index), true);
            }
            case SPEAR_ARRAY -> { if (field.phase < cast.limits.count()) mark(cast, arrayLine(field), true); }
            case FOLDING_EARTH -> {
                var s = field.passages.getFirst().strip();
                mark(cast, new Strip(s.start(), s.end(), cast.shape.inner()), true);
            }
            case MOVING_WALL -> markScreen(cast.actor, cast.art, field.center, field.direction,
                    cast.shape.width(), cast.shape.height(), true);
            case RESCUE_VINE -> {
                var ally = cast.aim.ally();
                if (friend(cast.actor, ally) && visible(cast.actor, ally))
                    markLine(cast.actor, cast.art, cast.actor.getEyePosition(), point(ally), true, 12);
            }
            case PAIRED_FURNACES -> {
                markRing(cast.actor, cast.art, field.other, .9, true);
                markRing(cast.actor, cast.art, field.center, .9, true);
            }
            case LINKED_ROOTS -> {
                markRing(cast.actor, cast.art, field.center, .65, true);
                if (field.continued) {
                    markRing(cast.actor, cast.art, field.other, .65, true);
                    mark(cast, field.passages.getFirst().strip(), true);
                }
            }
            case RETURNING_SEA -> {
                markRing(cast.actor, cast.art, field.center, cast.shape.radius(), true);
                mark(cast, seaLine(field), true);
            }
            case FREEZING_CREST -> mark(cast, iceBand(field), true);
            case FIRE_ENCLOSURE -> {
                markFireRing(field, field.direction, true);
                if (field.continued && !field.released)
                    markFireRing(field, rotate(field.direction, cast.shape.arc()), true);
            }
            case FIRE_ESCAPE -> {
                if (field.released) markRing(cast.actor, cast.art, field.center, cast.shape.radius(), true);
            }
            default -> markRing(cast.actor, cast.art, field.center, Math.max(.65, cast.shape.radius()), true);
        }
    }

    static boolean recastReady(LivingEntity actor, ArtDefinition art) {
        var field = ACTIVE.get(new Key(actor.getUUID(), art.id()));
        if (field == null || field.continued || field.released || field.cast.now() >= field.expires || !field.valid()) return false;
        return switch (field.kind()) {
            case SWORD_POINTS -> !field.points.isEmpty();
            case GOLD_MIRROR, LINKED_ROOTS, RETURNING_SEA, FREEZING_CREST, FIRE_ENCLOSURE, MOVING_WALL -> true;
            default -> false;
        };
    }

    static boolean recast(LivingEntity actor, CombatSession session, ArtDefinition art) {
        if (!recastReady(actor, art)) return false;
        var f = ACTIVE.get(new Key(actor.getUUID(), art.id()));
        var cast = f.cast;
        Vec3 forward = horizontal(Vec3.directionFromRotation(0, actor.getYRot()));
        long now = cast.now();
        switch (f.kind()) {
            case SWORD_POINTS, GOLD_MIRROR -> {
                Vec3 aiming = placement(actor, session, cast.shape.range(), forward);
                if (aiming == null) return false;
                List<Vec3> sources = f.kind() == Mechanic.SWORD_POINTS ? List.copyOf(f.points) : List.of(f.center);
                var lines = new ArrayList<Passage>();
                for (Vec3 from : sources) {
                    double reach = f.kind() == Mechanic.SWORD_POINTS ? cast.shape.reach() : cast.shape.length();
                    if (!cast.loaded(from) || horizontalDistance(from, aiming) < EPSILON) continue;
                    Strip line = ray(cast, from, direction(from, aiming, forward),
                            Math.min(reach, horizontalDistance(from, aiming)), cast.shape.width());
                    if (line.length() > EPSILON) lines.add(new Passage(line, cast.effect.damage()));
                }
                if (lines.isEmpty()) return false;
                f.passages = List.copyOf(lines);
                f.released = true;
                f.expires = now + cast.time.delay();
            }
            case LINKED_ROOTS -> {
                Vec3 second = placement(actor, session, cast.shape.range(), forward);
                if (second == null || horizontalDistance(f.center, second) < 1 || horizontalDistance(f.center, second) > cast.shape.length()
                        || !cast.anchored(second) || !clear(actor, f.center.add(0,.8,0), second.add(0,.8,0))) return false;
                f.other = second;
                f.passages = List.of(new Passage(new Strip(f.center, second, cast.shape.width()), cast.effect.damage()));
                f.expires = now + cast.time.duration();
            }
            case RETURNING_SEA -> f.direction = forward;
            case FREEZING_CREST -> freeze(f);
            case FIRE_ENCLOSURE -> f.next = now + cast.time.delay();
            case MOVING_WALL -> { if (!shiftWall(f, forward)) return false; }
            default -> { return false; }
        }
        f.continued = true;
        markField(f);
        LOG.info("ASCENDANT_FIELD actor={} art={} event=continued", CombatCaster.name(actor), art.id());
        return true;
    }

    private static boolean shiftWall(Field f, Vec3 to) {
        var cast = f.cast;
        Vec3 old = f.center;
        Strip swept = groundRay(cast, old, to, cast.shape.step(), cast.shape.width());
        if (swept.length() < .25 || !cast.anchored(swept.end())) return false;
        f.center = swept.end();
        mark(cast, swept, false);
        for (Entity target : strip(cast, swept, true))
            if (hit(cast, target, cast.effect.damage())) displace(cast, target, to, cast.effect.push());
        return true;
    }

    private static Strip crossLine(Cast cast, Vec3 at, Vec3 forward, double length) {
        Vec3 side = lateral(forward);
        Vec3 left = unobstructedEnd(cast.actor, at, at.add(side.scale(-length / 2)));
        Vec3 right = unobstructedEnd(cast.actor, at, at.add(side.scale(length / 2)));
        return new Strip(left, right, cast.shape.width());
    }

    private static boolean entered(Strip line, Entity entity) {
        AABB now = entity.getBoundingBox();
        Vec3 before = new Vec3(entity.xOld, entity.yOld, entity.zOld);
        Vec3 movement = entity.position().subtract(before);
        if (movement.lengthSqr() > 36 || movement.lengthSqr() < EPSILON) return false;
        var previous = now.move(movement.scale(-1));
        if (line.intersects(previous)) return false;
        int samples = Math.clamp((int) Math.ceil(movement.length() / .35), 1, 18);
        for (int step = 1; step <= samples; step++)
            if (line.intersects(previous.move(movement.scale((double)step / samples)))) return true;
        return false;
    }

    private static void crossEnemies(Field f, Strip line, boolean slowing) {
        var c = f.cast;
        if (line.length() < EPSILON || f.touched.size() >= c.limits.targets()) return;
        var enemies = candidates(c.actor, c.session, line.bounds().inflate(5), line.start(),
                e -> !f.touched.contains(CombatTargets.hitId(e)) && entered(line, e) && clear(c.actor, line.lineOrigin(e), point(e)));
        for (Entity enemy : distinct(enemies, c.limits.targets() - f.touched.size())) {
            if (!f.touched.add(CombatTargets.hitId(enemy))) continue;
            if (hit(c, enemy, c.effect.damage()) && slowing) slow(c, enemy, c.time.statusTicks(), c.effect.control());
        }
    }

    private static Strip postLine(Field f, int index) {
        return ray(f.cast, f.points.get(index), f.direction, f.cast.shape.length(), f.cast.shape.width());
    }

    private static boolean posts(Field f) {
        var cast = f.cast;
        for (int i = 0; i < f.points.size(); i++) {
            int mask = 1 << i;
            if ((f.armedPosts & mask) == 0) continue;
            var line = postLine(f, i);
            if (line.length() < EPSILON) continue;
            var target = candidates(cast.actor, cast.session, line.bounds().inflate(3), line.start(),
                    e -> !f.touched.contains(CombatTargets.hitId(e)) && entered(line, e)
                            && clear(cast.actor, line.lineOrigin(e), point(e))).stream().findFirst().orElse(null);
            if (target != null) {
                f.armedPosts &= ~mask;
                f.touched.add(CombatTargets.hitId(target));
                mark(cast, line, false);
                hit(cast, target, cast.effect.damage());
            }
        }
        return f.armedPosts == 0;
    }

    private static Vec3 rotate(Vec3 forward, double degrees) {
        double radians = Math.toRadians(degrees);
        return horizontal(forward.scale(Math.cos(radians)).add(lateral(forward).scale(Math.sin(radians))));
    }

    private static Strip arrayLine(Field f) {
        var cast = f.cast;
        Vec3 arm = rotate(f.direction, 90 * f.phase);
        Vec3 edge = unobstructedEnd(cast.actor, f.center, f.center.add(arm.scale(cast.shape.length())));
        return new Strip(edge, f.center, cast.shape.width());
    }

    private static boolean array(Field f, long now) {
        if (now < f.next) return false;
        var cast = f.cast;
        var line = arrayLine(f);
        mark(cast, line, false);
        for (Entity target : strip(cast, line, true)) {
            int id = CombatTargets.hitId(target);
            if (f.contacts.getOrDefault(id, 0) >= cast.limits.perTarget()
                    || !f.contacts.containsKey(id) && f.contacts.size() >= cast.limits.targets()) continue;
            if (hit(cast, target, cast.effect.damage())) f.contacts.merge(id, 1, Integer::sum);
        }
        f.phase++;
        f.next = now + cast.time.interval();
        return f.phase >= cast.limits.count();
    }

    private static boolean landing(Field f) {
        var cast = f.cast;
        if (!cast.actor.onGround()) return false;
        if (Math.abs(cast.actor.getY() - f.center.y) <= .7
                && horizontalDistance(cast.actor.position(), f.center) <= cast.shape.inner()
                && ground(cast.actor, f.center) != null) {
            markRing(cast.actor, cast.art, f.center, cast.shape.radius(), false);
            for (var target : area(cast, f.center, cast.shape.radius()))
                if (followsGround(cast.actor, f.center, target)) hit(cast, target,
                        withinRadius(f.center, target.getBoundingBox(), cast.shape.inner()) ? cast.effect.damage() : cast.effect.secondary());
        }
        return true;
    }

    private static void linkedRoots(Field f) {
        var c = f.cast;
        var line = f.passages.getFirst().strip();
        if (!c.anchored(f.other) || !clear(c.actor, line.start().add(0,.8,0), line.end().add(0,.8,0))) return;
        crossEnemies(f, line, false);
        if (f.helped.size() >= c.limits.count()) return;
        for (LivingEntity ally : c.level.getEntitiesOfClass(LivingEntity.class, line.bounds().inflate(5),
                e -> friend(c.actor, e) && entered(line, e) && clear(c.actor, line.lineOrigin(e), point(e)))) {
            if (f.helped.size() >= c.limits.count()) break;
            if (f.helped.contains(ally.getId())) continue;
            double amount = Math.min(c.actor.getMaxHealth(), ally.getMaxHealth()) * c.effect.budget();
            if (heal(c, ally, amount) > 0) f.helped.add(ally.getId());
        }
    }

    private static boolean rescue(Field f) {
        var cast = f.cast;
        ServerPlayer ally = (ServerPlayer) cast.aim.ally();
        if (!friend(cast.actor, ally) || !visible(cast.actor, ally) || !inReach(cast.actor, ally, cast.shape.range())) return true;
        boolean pressed = ally.isShiftKeyDown() && !f.sneak;
        f.sneak = ally.isShiftKeyDown();
        if (!pressed) return false;
        if (!ally.onGround() || ally.isPassenger() || ally.isVehicle() || ControlService.isRooted(ally)) return false;
        Vec3 at = ally.position();
        double length = Math.min(cast.shape.step(), Math.max(0, horizontalDistance(at, cast.actor.position()) - 1));
        step(ally, horizontal(cast.actor.position().subtract(at)), length);
        float amount = (float) (Math.min(cast.actor.getMaxHealth(), ally.getMaxHealth()) * cast.effect.budget());
        if (ShieldRuntime.grant(ally, cast.art.id(), amount, cast.time.statusTicks())) award(cast);
        return true;
    }

    private static void shade(Field f, long now) {
        var c = f.cast;
        if (f.touched.size() >= c.limits.targets()) return;
        var pool = inArea(c.actor, c.session, f.center, c.shape.radius());
        var seen = new HashSet<Integer>();
        for (Entity target : distinct(pool, 24)) {
            int id = CombatTargets.hitId(target);
            if (f.touched.contains(id)) continue;
            seen.add(id);
            if (now - f.dwell.computeIfAbsent(id, key -> now) < c.time.delay()) continue;
            if (f.touched.size() >= c.limits.targets()) break;
            f.touched.add(id);
            double amount = ElementalEffects.harvestDamage(c.actor, target, DamageType.WOOD) > 0
                    ? c.effect.secondary() : c.effect.damage();
            if (hit(c, target, amount) && CombatTargets.owner(target).isAlive())
                ElementalEffects.seed(c.actor, target, DamageType.WOOD, c.base * RealmArtCombat.pvpDamage(c.actor, target));
        }
        f.dwell.keySet().retainAll(seen);
    }

    private static void startTides(Field f) {
        var cast = f.cast;
        var seen = new HashSet<Integer>();
        for (double sign : new double[]{-1, 1}) {
            Vec3 side = lateral(f.direction).scale(sign);
            var bound = groundRay(cast, f.center, side, cast.shape.length(), cast.shape.width());
            Strip inward = new Strip(bound.end(), bound.start(), bound.width());
            mark(cast, inward, false);
            for (Entity target : strip(cast, inward, true)) {
                if (seen.size() >= cast.limits.targets()) break;
                if (seen.add(CombatTargets.hitId(target)) && hit(cast, target, cast.effect.damage()))
                    displace(cast, target, f.center.subtract(target.position()),
                            Math.min(cast.effect.push(), horizontalDistance(f.center, target.position())));
            }
        }
    }

    private static void flow(Field f) {
        var c = f.cast;
        var line = f.passages.getFirst().strip();
        if (line.length() < EPSILON) return;
        for (LivingEntity ally : c.level.getEntitiesOfClass(LivingEntity.class, line.bounds(),
                e -> friend(c.actor, e) && line.intersects(e.getBoundingBox()) && alongMotion(e, line.forward()) > .02
                        && clear(c.actor, line.lineOrigin(e), point(e))).stream().limit(c.limits.targets()).toList()) {
            AscendantWards.speed(ally, c.effect.bonus(), WARN_INTERVAL + 1);
            award(c);
        }
        for (var enemy : strip(c, line, false))
            if (alongMotion(enemy, line.forward()) < -.02) {
                slow(c, enemy, WARN_INTERVAL + 1, c.effect.control());
                award(c);
            }
    }

    private static double alongMotion(Entity entity, Vec3 direction) {
        return (entity.getX() - entity.xOld) * direction.x + (entity.getZ() - entity.zOld) * direction.z;
    }

    private static Strip seaLine(Field f) {
        return ray(f.cast, f.center, f.direction, f.cast.shape.length(), f.cast.shape.width());
    }

    private static void exitSea(Field f) {
        var c = f.cast;
        Strip line = seaLine(f);
        mark(c, line, false);
        markRing(c.actor, c.art, f.center, c.shape.inner(), false);
        for (Entity enemy : area(c, f.center, c.shape.radius())) {
            if (withinRadius(f.center, enemy.getBoundingBox(), c.shape.inner())) hit(c, enemy, c.effect.secondary());
            else if (line.intersects(enemy.getBoundingBox())) hit(c, enemy, c.effect.bonus());
        }
    }

    private static void crest(Field f, long now) {
        var c = f.cast;
        if (!f.continued && now >= f.next) {
            double distance = Math.min(horizontalDistance(f.center, f.crestEnd),
                    Math.max(0, now - f.born) * c.shape.reach() / c.time.interval());
            Vec3 desired = f.center.add(f.direction.scale(distance));
            Vec3 floor = ground(c.actor, desired);
            if (floor == null || !clear(c.actor, f.other.add(0,.8,0), floor.add(0,.8,0))) {
                freeze(f);
            } else {
                f.other = floor;
                if (horizontalDistance(floor, f.crestEnd) < .2) freeze(f);
            }
            f.next = now + c.time.interval();
        }
        if (f.continued && f.touched.size() < c.limits.targets()) {
            for (Entity target : strip(c, iceBand(f), true)) {
                if (f.touched.size() >= c.limits.targets()) break;
                if (isGrounded(target) && f.touched.add(CombatTargets.hitId(target)) && hit(c, target, c.effect.damage()))
                    slow(c, target, c.time.statusTicks(), c.effect.control());
            }
        }
    }

    private static void freeze(Field f) {
        f.continued = true;
        f.expires = f.cast.now() + f.cast.time.duration();
        mark(f.cast, iceBand(f), false);
    }

    private static Strip iceBand(Field f) {
        var across = crossLine(f.cast, f.other, f.direction, f.cast.shape.width());
        return new Strip(across.start(), across.end(), f.cast.shape.height());
    }

    static void swordHit(LivingEntity attacker, Entity target) {
        Field f = find(attacker, Mechanic.SWORD_POINTS);
        if (f == null || f.released || f.points.size() >= f.cast.limits.count()) return;
        Vec3 at = target.position();
        if (f.points.stream().anyMatch(p -> p.distanceToSqr(at) < .25) || !f.cast.loaded(at)) return;
        f.points.add(at);
        markRing(attacker, f.cast.art, at, .55, false);
    }

    public static void dodged(LivingEntity actor, DamageSource source, float amount) {
        if (!(amount > 0) || !(source.getEntity() instanceof LivingEntity enemy) || !CombatActors.mayHarm(enemy, actor)) return;
        Field f = find(actor, Mechanic.EVADE_ECHO);
        if (f == null || f.released) return;
        f.released = true;
        f.expires = f.cast.now() + f.cast.time.delay();
        markField(f);
    }

    static void fireHit(LivingEntity actor, Entity target) {
        Field f = find(actor, Mechanic.PAIRED_FURNACES);
        if (f == null || f.released) return;
        var cast = f.cast;
        List<Passage> beams = List.of(f.center, f.other).stream().filter(cast::loaded)
                .map(at -> ray(cast, at, direction(at, target.position(), f.direction),
                        Math.min(cast.shape.length(), horizontalDistance(at, target.position())), cast.shape.width()))
                .filter(line -> line.length() > EPSILON)
                .map(line -> new Passage(line, cast.effect.damage())).toList();
        if (beams.isEmpty()) return;
        f.released = true;
        f.expires = cast.now() + cast.time.delay();
        f.passages = beams;
        markField(f);
    }

    static void tookHit(LivingEntity victim, DamageSource source, float amount) {
        if (!(amount > 0) || !AscendantWards.hostileMelee(victim, source)) return;
        var f = find(victim, Mechanic.FIRE_ESCAPE);
        if (f == null || f.released) return;
        f.released = true;
        f.center = victim.position();
        f.expires = f.cast.now() + f.cast.time.delay();
        AscendantWards.speed(victim, f.cast.effect.control(), f.cast.time.statusTicks());
        markField(f);
    }

    private static boolean fireArc(Field f, Vec3 direction, Vec3 position) {
        Vec3 delta = position.subtract(f.center);
        double range = Math.hypot(delta.x, delta.z);
        double width = f.cast.shape.width() / 2;
        double radius = f.cast.shape.radius();
        double gap = f.cast.shape.gap() / 2;
        return Math.abs(range - radius) <= width + EPSILON && Math.abs(delta.dot(lateral(direction))) >= gap;
    }

    private static void fireEnclosure(Field f, long now) {
        var c = f.cast;
        if (f.continued && !f.released && now >= f.next) {
            f.direction = rotate(f.direction, c.shape.arc());
            f.released = true;
        }
        if (f.touched.size() >= c.limits.targets()) return;
        var pool = inArea(c.actor, c.session, f.center, c.shape.radius() + c.shape.width() / 2).stream()
                .filter(e -> fireArc(f, f.direction, point(e)) && !f.touched.contains(CombatTargets.hitId(e))).toList();
        for (Entity target : distinct(pool, c.limits.targets() - f.touched.size())) {
            if (f.touched.add(CombatTargets.hitId(target)) && hit(c, target, c.effect.damage()) && CombatTargets.owner(target).isAlive())
                ElementalEffects.seed(c.actor, target, DamageType.FIRE, c.base * RealmArtCombat.pvpDamage(c.actor, target));
        }
    }

    private static void markFireRing(Field f, Vec3 direction, boolean warning) {
        var c = f.cast;
        for (double radius : new double[]{c.shape.radius() - c.shape.width()/2, c.shape.radius() + c.shape.width()/2}) {
            for (int index = 0; index < 32; index++) {
                Vec3 a = f.center.add(rotate(direction, 360.0 * index / 32).scale(radius));
                Vec3 b = f.center.add(rotate(direction, 360.0 * (index + 1) / 32).scale(radius));
                double lateral = Math.abs(a.lerp(b, .5).subtract(f.center).dot(lateral(direction)));
                if (lateral >= c.shape.gap() / 2) markLine(c.actor, c.art, a, b, warning, 2);
            }
        }
    }

    private static void firstEarth(Field f, Strip line) {
        var c = f.cast;
        if (line.length() <= EPSILON) return;
        Vec3 side = line.side();
        double offset = (c.shape.width() + c.shape.inner()) / 4;
        double width = (c.shape.width() - c.shape.inner()) / 2;
        for (double direction : new double[]{1, -1}) {
            Vec3 delta = side.scale(direction * offset);
            mark(c, new Strip(line.start().add(delta), line.end().add(delta), width), false);
        }
        var outer = inStrip(c.actor, c.session, line).stream()
                .filter(e -> Math.abs(point(e).subtract(line.start()).dot(side)) >= c.shape.inner() / 2
                        && followsGround(c.actor, line.lineOrigin(e).add(0, -.8, 0), e)).toList();
        for (var e : distinct(outer, c.limits.targets())) {
            Vec3 toward = line.lineOrigin(e).subtract(e.position());
            if (hit(c, e, c.effect.damage())) displace(c, e, toward, Math.min(c.effect.push(), toward.horizontalDistance()));
        }
    }

    static List<Field> protections(LivingEntity victim, Mechanic mechanic) {
        return ACTIVE.values().stream().filter(f -> f.kind() == mechanic && f.cast.level == victim.level()
                && f.cast.now() < f.expires && f.valid()).toList();
    }

    public static void projectile(EntityTickEvent.Pre event) {
        if (ACTIVE.isEmpty() || event.isCanceled() || !(event.getEntity() instanceof Projectile shot)
                || !AscendantWards.ordinaryProjectile(shot) || !(shot.getOwner() instanceof LivingEntity enemy)) return;
        Vec3 from = shot.position(), to = from.add(shot.getDeltaMovement());
        Field chosen = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (Field f : ACTIVE.values()) {
            if (f.kind() != Mechanic.MOVING_WALL || f.charges <= 0 || shot.level() != f.cast.level
                    || f.cast.now() >= f.expires || !f.valid() || !CombatActors.mayHarm(enemy, f.cast.actor)) continue;
            double fraction = screenCrossing(f.center, f.direction, f.cast.shape.width(), f.cast.shape.height(), from, to);
            if (fraction < nearest && clear(f.cast.actor, from, from.lerp(to, fraction))) {
                nearest = fraction;
                chosen = f;
            }
        }
        if (chosen == null) return;
        shot.discard();
        event.setCanceled(true);
        chosen.charges--;
        markRing(chosen.cast.actor, chosen.cast.art, from.lerp(to, nearest), .5, false);
        award(chosen.cast);
        LOG.info("ASCENDANT_FIELD actor={} art={} event=intercepted left={}", CombatCaster.name(chosen.cast.actor),
                chosen.cast.art.id(), chosen.charges);
    }

    private static Field find(LivingEntity actor, Mechanic kind) {
        if (ACTIVE.isEmpty()) return null;
        return ACTIVE.values().stream().filter(f -> f.cast.actor == actor && f.kind() == kind
                && f.cast.now() < f.expires && f.valid()).findFirst().orElse(null);
    }

    static void forget(UUID actor) { ACTIVE.keySet().removeIf(key -> key.actor().equals(actor)); }
    static void clearAll() { ACTIVE.clear(); }
}
