package com.example.myvillage.combat.session;

import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.art.GoldenCoreArt;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.elemental.ElementalEffects;
import com.example.myvillage.combat.elemental.ElementalRules;
import com.example.myvillage.combat.foundation.ControlService;
import com.example.myvillage.combat.runtime.CombatCaster;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.combat.runtime.CombatDisciplines;
import com.example.myvillage.combat.runtime.CombatDodgeService;
import com.example.myvillage.combat.runtime.CombatFeedbackService;
import com.example.myvillage.combat.runtime.CombatHitResolver;
import com.example.myvillage.combat.runtime.CombatTargets;
import com.example.myvillage.combat.runtime.StaggerResistant;
import com.example.myvillage.combat.runtime.SwordArmorBreak;
import com.example.myvillage.cultivation.growth.TechniqueGrowthRuntime;
import com.example.myvillage.cultivation.technique.HeartTechniques;
import com.example.myvillage.stats.DamageType;
import com.example.myvillage.stats.runtime.StatsRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.myvillage.combat.session.GoldenCoreGeometry.*;

/** Keyed casts and their finite continuations are advanced by the shared combat manager. */
public final class GoldenCoreActions {
    private static final Logger LOG = LoggerFactory.getLogger(GoldenCoreActions.class);
    private static final double SUPPORT_SAMPLE = 0.5;
    private static final double TRACE_MIN_ANGLE = 18;
    private static final int PULSE_COUNT = 3;
    private static final int TELEGRAPH_INTERVAL = 4;
    private static final Map<UUID, Trace> TRACES = new HashMap<>();
    private static final Map<UUID, Trail> TRAILS = new HashMap<>();
    private static final Map<UUID, Fuse> FUSES = new HashMap<>();

    private GoldenCoreActions() {}

    record Aim(Vec3 origin, Vec3 direction, Entity target, Vec3 anchor, double side) {}

    static final class Cast {
        final LivingEntity caster;
        final ArtDefinition art;
        final GoldenCoreArt spec;
        final Aim aim;
        final ItemStack held;
        final int slot;
        final ServerLevel world;
        final int stacks;
        final double base;
        final Map<Integer, Integer> contacts = new HashMap<>();
        final Map<Integer, Double> beforeRetreat = new HashMap<>();
        CombatSession session;
        Vec3 origin;
        boolean mastery;

        Cast(LivingEntity caster, ArtDefinition art, Aim aim, CombatSession session, boolean paid) {
            this.caster = caster;
            this.art = art;
            spec = art.goldenCore().orElseThrow();
            this.aim = aim;
            this.session = session;
            held = caster.getMainHandItem();
            slot = CombatCaster.slot(caster);
            world = level(caster);
            origin = aim.origin();
            stacks = switch (spec.mechanic()) {
                case PIERCING_SWORD, STORED_PUNCH -> CombatDisciplines.consumeStacks(caster, art.family());
                default -> 0;
            };
            base = spec.element().map(element -> ElementalEffects.base(caster, element)
                    + StatsRuntime.heartAttackAddition(caster) + HeartTechniques.attackExtra(caster, "", element, true, paid)).orElse(0.0);
        }

        boolean valid() {
            return caster.level() == world && CombatCaster.active(caster) && CombatCaster.inventoryFree(caster)
                    && held == caster.getMainHandItem() && slot == CombatCaster.slot(caster) && session.hasActiveAction()
                    && CombatArtActions.authorized(caster, art);
        }

        void detach() {
            var style = CombatStyles.bundled().move(art.phases().getFirst().moveId()).orElseThrow().style();
            var detached = new CombatSession(style);
            detached.startExternal(art.move(0, 1), world.getGameTime(), BuiltInRegistries.ITEM.getKey(held.getItem()),
                    world.dimension().location(), session.facingYaw(), session.paidArt());
            session = detached;
        }
    }

    private static final class Trace {
        final Cast cast;
        final Strip first;
        Strip second;
        long due;
        Trace(Cast cast, Strip first) {
            this.cast = cast;
            this.first = first;
            due = clock(cast.caster) + cast.spec.duration();
        }
    }

    private record TrailPart(Strip strip, long until) {}
    private static final class Trail {
        final Cast cast;
        final List<TrailPart> parts = new ArrayList<>();
        final long recordUntil;
        Vec3 last;
        double left;
        boolean recording = true;
        Trail(Cast cast) {
            this.cast = cast;
            last = cast.aim.target().position();
            left = cast.spec.length();
            recordUntil = clock(cast.caster) + cast.spec.interval();
        }
    }
    private record Fuse(Cast cast, Entity victim, long due) {}

    static Aim prepare(LivingEntity caster, CombatSession session, ArtDefinition art) {
        var spec = art.goldenCore().orElseThrow();
        var direction = horizontal(Vec3.directionFromRotation(0, caster.getYRot()));
        var origin = caster.position();
        Entity target = null;
        Vec3 anchor = null;
        switch (spec.mechanic()) {
            case ARMOR_LOCK, METAL_CHAIN, WOOD_TRANSFER, WOOD_TRAIL, FROST_SHATTER, FIRE_FUSE -> {
                target = acquire(caster, session, spec.range(), GoldenCoreArt.rules().aimArc(), true,
                        e -> spec.mechanic() != GoldenCoreArt.Mechanic.WOOD_TRAIL
                                || ElementalEffects.harvestDamage(caster, e, DamageType.WOOD) > 0);
                if (target == null) return null;
            }
            case PIERCING_SWORD, STORED_PUNCH, TIP_CHARGE, RETREATING_THRUST -> {
                target = acquire(caster, session, spec.range(), GoldenCoreArt.rules().lineAssistArc(), true, e -> true);
                if (target != null) {
                    var adjusted = horizontal(point(target).subtract(origin));
                    if (adjusted.horizontalDistanceSqr() > 0.5) direction = adjusted;
                }
            }
            case CROSSING_TRACES, STONE_STAMP -> {
                anchor = groundAim(caster, session, spec.range(), GoldenCoreArt.rules().aimArc());
                if (anchor == null || !available(caster, anchor, spec.range())) return null;
            }
            case EARTH_ROWS, FIRE_ROWS, GROUND_RINGS -> {
                anchor = ground(caster, origin);
                if (anchor == null) return null;
                origin = anchor;
            }
            case SIDE_TIDE -> { }
        }
        return new Aim(origin, direction, target, anchor, caster.isShiftKeyDown() ? -1 : 1);
    }

    static boolean recastReady(LivingEntity caster, ArtDefinition art) {
        Trace trace = TRACES.get(caster.getUUID());
        return trace != null && trace.cast.art.id().equals(art.id()) && trace.second == null
                && clock(caster) < trace.due && trace.cast.valid();
    }

    static boolean recast(LivingEntity caster, CombatSession session, ArtDefinition art) {
        if (!recastReady(caster, art)) return false;
        Trace trace = TRACES.get(caster.getUUID());
        Aim aim = prepare(caster, session, art);
        if (aim == null) {
            CombatCaster.message(caster, net.minecraft.network.chat.Component.literal("请选择可见的地面布下第二道剑痕"), true);
            return false;
        }
        Strip next = trace(aim, trace.cast.spec);
        if (Math.abs(trace.first.forward().dot(next.forward())) > Math.cos(Math.toRadians(TRACE_MIN_ANGLE))) {
            CombatCaster.message(caster, net.minecraft.network.chat.Component.literal("转动朝向后再布第二道剑痕"), true);
            return false;
        }
        trace.second = next;
        trace.due = clock(caster) + trace.cast.spec.interval();
        markStrip(caster, art, trace.first, true);
        markStrip(caster, art, next, true);
        LOG.info("GOLDEN_CORE actor={} art={} event=recast", CombatCaster.name(caster), art.id());
        return true;
    }

    static void resolve(Cast cast, int frame) {
        if (frame < cast.spec.windup()) {
            if (frame % TELEGRAPH_INTERVAL == 0) warn(cast);
            return;
        }
        int age = frame - cast.spec.windup();
        switch (cast.spec.mechanic()) {
            case RETREATING_THRUST -> {
                if (age == 0) retreat(cast);
                if (age == cast.spec.interval()) thrust(cast);
            }
            case GROUND_RINGS, FIRE_ROWS, EARTH_ROWS -> {
                if (age <= cast.spec.interval() * (PULSE_COUNT - 1) && age % cast.spec.interval() == 0)
                    pulse(cast, age / cast.spec.interval());
            }
            default -> { if (age == 0) strike(cast); }
        }
    }

    private static void warn(Cast cast) {
        var spec = cast.spec;
        switch (spec.mechanic()) {
            case CROSSING_TRACES -> markStrip(cast.caster, cast.art, trace(cast.aim, spec), true);
            case STONE_STAMP -> {
                markRing(cast.caster, cast.art, cast.aim.anchor(), spec.radius(), true);
                markRing(cast.caster, cast.art, cast.aim.anchor(), spec.inner(), true);
            }
            case GROUND_RINGS -> markRing(cast.caster, cast.art, cast.origin, spec.radius(), true);
            case FIRE_ROWS -> {
                var path = lane(cast);
                for (int i = 0; i < PULSE_COUNT; i++) {
                    var point = phasePoint(cast, path, i + 1);
                    if (point != null) markRing(cast.caster, cast.art, point, spec.radius(), true);
                }
            }
            case PIERCING_SWORD, STORED_PUNCH, TIP_CHARGE, RETREATING_THRUST, SIDE_TIDE, EARTH_ROWS ->
                    markStrip(cast.caster, cast.art, line(cast, cast.origin, spec.length()), true);
            default -> {
                var target = liveTarget(cast);
                if (target != null) markTarget(cast.caster, cast.art, target, true);
            }
        }
    }

    private static Strip line(Cast cast, Vec3 origin, double length) {
        return new Strip(origin, origin.add(cast.aim.direction().scale(length)), cast.spec.width());
    }

    private static Strip trace(Aim aim, GoldenCoreArt spec) {
        Vec3 half = aim.direction().scale(spec.length() / 2);
        return new Strip(aim.anchor().subtract(half), aim.anchor().add(half), spec.width());
    }

    private static void strike(Cast cast) {
        var spec = cast.spec;
        switch (spec.mechanic()) {
            case PIERCING_SWORD, STORED_PUNCH -> lineStrike(cast, spec.damage() + cast.stacks * spec.bonus());
            case TIP_CHARGE -> charge(cast);
            case CROSSING_TRACES -> {
                if (available(cast.caster, cast.aim.anchor(), spec.range())) {
                    cast.detach();
                    TRACES.put(cast.caster.getUUID(), new Trace(cast, trace(cast.aim, spec)));
                }
            }
            case SIDE_TIDE -> tide(cast);
            case STONE_STAMP -> stone(cast);
            default -> chosenStrike(cast);
        }
    }

    private static void lineStrike(Cast cast, double multiplier) {
        Strip strip = line(cast, cast.caster.position(), cast.spec.length());
        markStrip(cast.caster, cast.art, strip, false);
        first(inStrip(cast.caster, cast.session, strip)).ifPresent(target -> hit(cast, target, multiplier, 1));
    }

    private static void charge(Cast cast) {
        var caster = cast.caster;
        var spec = cast.spec;
        var target = first(inStrip(caster, cast.session, line(cast, caster.position(), spec.range()))).orElse(null);
        double distance = spec.step();
        if (target != null) {
            double edge = Math.sqrt(target.getBoundingBox().distanceToSqr(caster.position()));
            distance = Math.clamp(edge - GoldenCoreArt.rules().spearTipMax(), 0, distance);
        }
        if (!ControlService.isRooted(caster)) step(caster, cast.aim.direction(), distance);
        Strip strip = line(cast, caster.position(), spec.length());
        markStrip(caster, cast.art, strip, false);
        first(inStrip(caster, cast.session, strip)).ifPresent(victim -> {
            double edge = Math.sqrt(victim.getBoundingBox().distanceToSqr(caster.position()));
            boolean tip = edge >= GoldenCoreArt.rules().spearTipMin() && edge <= GoldenCoreArt.rules().spearTipMax();
            if (hit(cast, victim, tip ? spec.damage() : spec.secondary(), 1) && tip) {
                CombatCaster.message(caster, net.minecraft.network.chat.Component.literal("枪锋命中"), true);
            }
        });
    }

    private static void retreat(Cast cast) {
        cast.origin = cast.caster.position();
        var reach = line(cast, cast.origin, cast.spec.range());
        var nearby = inStrip(cast.caster, cast.session, reach);
        for (var target : nearby.stream().limit(24).toList())
            cast.beforeRetreat.put(CombatTargets.hitId(target), point(target).subtract(cast.origin).dot(cast.aim.direction()));
        if (!ControlService.isRooted(cast.caster)) step(cast.caster, cast.aim.direction().scale(-1), cast.spec.step());
        markStrip(cast.caster, cast.art, line(cast, cast.caster.position(), cast.spec.length()), true);
    }

    private static void thrust(Cast cast) {
        Strip strip = line(cast, cast.caster.position(), cast.spec.length());
        markStrip(cast.caster, cast.art, strip, false);
        first(inStrip(cast.caster, cast.session, strip)).ifPresent(target -> {
            double old = cast.beforeRetreat.getOrDefault(CombatTargets.hitId(target), Double.NEGATIVE_INFINITY);
            double now = point(target).subtract(cast.origin).dot(cast.aim.direction());
            boolean followed = old > 0.35 && now <= 0.35 && old - now >= 0.65;
            hit(cast, target, cast.spec.damage() + (followed ? cast.spec.bonus() : 0), 1);
        });
    }

    private static void tide(Cast cast) {
        Strip strip = line(cast, cast.origin, cast.spec.length());
        markStrip(cast.caster, cast.art, strip, false);
        for (var target : distinct(inStrip(cast.caster, cast.session, strip), cast.spec.targets())) {
            if (hit(cast, target, cast.spec.damage(), 1))
                displace(cast, target, lateral(cast.aim.direction()).scale(cast.aim.side()));
        }
    }

    private static void chosenStrike(Cast cast) {
        Entity target = liveTarget(cast);
        if (target == null) return;
        var spec = cast.spec;
        if (spec.mechanic() == GoldenCoreArt.Mechanic.WOOD_TRAIL) {
            if (ElementalEffects.harvestDamage(cast.caster, target, DamageType.WOOD) > 0) {
                cast.detach();
                TRAILS.put(cast.caster.getUUID(), new Trail(cast));
                markTarget(cast.caster, cast.art, target, true);
            }
            return;
        }
        boolean chill = spec.mechanic() == GoldenCoreArt.Mechanic.FROST_SHATTER
                && CombatTargets.owner(target) instanceof LivingEntity living && ElementalEffects.hasWaterChill(cast.caster, living);
        markLine(cast.caster, cast.art, cast.caster.getEyePosition().add(0, -0.5, 0), point(target), false, 22);
        if (!hit(cast, target, chill ? spec.secondary() : spec.damage(), 1)) return;
        switch (spec.mechanic()) {
            case ARMOR_LOCK -> {
                if (CombatTargets.owner(target) instanceof LivingEntity living && living.isAlive())
                    SwordArmorBreak.apply(living, spec.status(), spec.statusTicks());
            }
            case METAL_CHAIN -> chain(cast, target);
            case WOOD_TRANSFER -> {
                int moved = ElementalEffects.transferWood(cast.caster, target,
                        inArea(cast.caster, cast.session, target.position(), spec.radius()), spec.targets());
                if (moved > 0) markRing(cast.caster, cast.art, target.position(), spec.radius(), false);
            }
            case FROST_SHATTER -> {
                if (chill) ElementalEffects.consumeWaterChill(cast.caster, (LivingEntity) CombatTargets.owner(target));
            }
            case FIRE_FUSE -> {
                if (CombatTargets.owner(target).isAlive()) {
                    cast.detach();
                    FUSES.put(cast.caster.getUUID(), new Fuse(cast, target, clock(cast.caster) + spec.duration()));
                }
            }
            default -> { }
        }
    }

    private static void chain(Cast cast, Entity initial) {
        Vec3 previous = point(initial);
        Vec3 forward = horizontal(previous.subtract(cast.origin));
        for (int jump = 1; jump < cast.spec.targets() && cast.valid(); jump++) {
            Vec3 origin = previous;
            var next = first(inArea(cast.caster, cast.session, origin, cast.spec.secondaryRange()).stream()
                    .filter(e -> !cast.contacts.containsKey(CombatTargets.hitId(e))
                            && horizontal(point(e).subtract(origin)).dot(forward) >= 0.4
                            && clear(cast.caster, origin, point(e))).toList()).orElse(null);
            if (next == null) return;
            previous = point(next);
            markLine(cast.caster, cast.art, origin, previous, false, 22);
            if (!hit(cast, next, jump == 1 ? cast.spec.secondary() : cast.spec.bonus(), 1)) return;
        }
    }

    private static void stone(Cast cast) {
        var spec = cast.spec;
        Vec3 anchor = cast.aim.anchor();
        if (!available(cast.caster, anchor, spec.range()) || ground(cast.caster, anchor) == null) return;
        markRing(cast.caster, cast.art, anchor, spec.radius(), false);
        markRing(cast.caster, cast.art, anchor, spec.inner(), false);
        boolean primary = true;
        for (var target : distinct(inArea(cast.caster, cast.session, anchor, spec.radius()), spec.targets())) {
            boolean center = primary && isGrounded(target) && withinRadius(anchor, target.getBoundingBox(), spec.inner());
            if (center) primary = false;
            hit(cast, target, center ? spec.damage() : spec.secondary(), 1);
        }
    }

    private static void pulse(Cast cast, int index) {
        if (cast.spec.mechanic() == GoldenCoreArt.Mechanic.GROUND_RINGS) {
            groundRing(cast, index);
            return;
        }
        var path = lane(cast);
        var edge = phasePoint(cast, path, index + 1);
        if (edge == null) return;
        if (cast.spec.mechanic() == GoldenCoreArt.Mechanic.FIRE_ROWS) {
            markRing(cast.caster, cast.art, edge, cast.spec.radius(), false);
            for (var target : distinct(inArea(cast.caster, cast.session, edge, cast.spec.radius()), cast.spec.targets())) {
                boolean first = !cast.contacts.containsKey(CombatTargets.hitId(target));
                if (hit(cast, target, cast.spec.damage(), 2) && first && CombatTargets.owner(target).isAlive())
                    ElementalEffects.seed(cast.caster, target, DamageType.FIRE, cast.base * pvp(cast.caster, target));
            }
        } else {
            var start = index == 0 ? cast.origin : phasePoint(cast, path, index);
            if (start == null) return;
            // Adjacent segments overlap slightly. Each enemy may take damage from at most two segments.
            Vec3 before = index == 0 ? start : start.subtract(cast.aim.direction().scale(SUPPORT_SAMPLE));
            var strip = new Strip(before, edge, cast.spec.width());
            markStrip(cast.caster, cast.art, strip, false);
            var nearby = inStrip(cast.caster, cast.session, strip).stream()
                    .filter(target -> followsGround(cast.caster, strip.lineOrigin(target).add(0, -0.8, 0), target)).toList();
            for (var target : distinct(nearby, cast.spec.targets())) hit(cast, target, cast.spec.damage(), 2);
        }
    }

    private static List<Vec3> lane(Cast cast) {
        return groundPath(cast.caster, cast.origin, cast.aim.direction(), cast.spec.length());
    }

    private static Vec3 phasePoint(Cast cast, List<Vec3> path, int boundary) {
        int index = (int) Math.ceil(cast.spec.length() / SUPPORT_SAMPLE * boundary / PULSE_COUNT);
        return index < path.size() ? path.get(index) : null;
    }

    private static void groundRing(Cast cast, int index) {
        double radius = cast.spec.radius() * (index + 1) / PULSE_COUNT;
        double previous = cast.spec.radius() * index / PULSE_COUNT;
        markRing(cast.caster, cast.art, cast.origin, radius, false);
        var nearby = inArea(cast.caster, cast.session, cast.origin, radius).stream()
                .filter(e -> (index == 0 || horizontalDistance(cast.origin, point(e)) >= previous)
                        && !cast.contacts.containsKey(CombatTargets.hitId(e)) && followsGround(cast.caster, cast.origin, e)).toList();
        int limit = cast.spec.targets() - cast.contacts.size();
        if (limit <= 0) return;
        for (var target : distinct(nearby, limit)) {
            if (hit(cast, target, cast.spec.damage(), 1))
                displace(cast, target, horizontal(point(target).subtract(cast.origin)));
        }
    }

    private static Entity liveTarget(Cast cast) {
        Entity target = cast.aim.target();
        return target != null && CombatHitResolver.legalTarget(cast.caster, target, cast.session)
                && inReach(cast.caster, target, cast.spec.range()) && visible(cast.caster, target) ? target : null;
    }

    private static double pvp(LivingEntity caster, Entity victim) {
        return caster instanceof ServerPlayer && CombatTargets.owner(victim) instanceof ServerPlayer
                ? GoldenCoreArt.rules().pvpDamage() : 1;
    }

    private static boolean hit(Cast cast, Entity target, double multiplier, int maximum) {
        int key = CombatTargets.hitId(target);
        int count = cast.contacts.getOrDefault(key, 0);
        if (count >= maximum || !cast.valid() || !CombatHitResolver.legalTarget(cast.caster, target, cast.session)) return false;
        cast.contacts.put(key, count + 1);
        var template = cast.art.move(0, 1);
        var move = CombatDisciplines.scale(template, multiplier / template.damageMultiplier(), 0);
        var element = cast.spec.element().orElse(null);
        var source = element == null ? null : ElementalRules.source(cast.caster, element);
        var result = CombatDamageService.applyGoldenCore(cast.caster, target, move, cast.session,
                pvp(cast.caster, target), source, cast.base);
        if (result.successful()) {
            CombatFeedbackService.hit(cast.caster, move, cast.session.revision(), List.of(new CombatHitResolver.TargetContact(
                    target, target.distanceToSqr(cast.caster), point(target))));
        }
        if (result.actualDamage() <= 0 || !cast.valid()) return false;
        award(cast);
        if (element == DamageType.FIRE && cast.spec.mechanic() != GoldenCoreArt.Mechanic.FIRE_FUSE)
            onDirectFire(cast.caster, target);
        LOG.info("GOLDEN_CORE actor={} art={} event=hit target={} multiplier={} damage={}", CombatCaster.name(cast.caster),
                cast.art.id(), target.getId(), multiplier, result.actualDamage());
        return true;
    }

    private static void award(Cast cast) {
        if (cast.mastery) return;
        cast.mastery = true;
        TechniqueGrowthRuntime.hit(cast.caster, cast.art.id());
    }

    private static void displace(Cast cast, Entity entity, Vec3 direction) {
        if (!(entity instanceof LivingEntity target) || CombatTargets.owner(target) != target || !target.isAlive()
                || CombatDodgeService.isDodging(target) || StaggerResistant.resists(target)
                || !ControlService.canControl(cast.caster, target)) return;
        double resistance = Math.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1);
        double scale = pvp(cast.caster, target) < 1 ? GoldenCoreArt.rules().pvpDisplacement() : 1;
        step(target, direction, cast.spec.push() * (1 - resistance) * scale);
    }

    private static java.util.Optional<Entity> first(List<Entity> contacts) { return contacts.stream().findFirst(); }
    private static List<Entity> distinct(List<Entity> hits, int maximum) {
        return CombatTargets.firstDistinct(hits, CombatTargets::hitId, maximum);
    }
    private static long clock(LivingEntity caster) { return level(caster).getServer().overworld().getGameTime(); }

    public static void onDirectFire(LivingEntity caster, Entity actualTarget) {
        Fuse fuse = FUSES.get(caster.getUUID());
        if (fuse != null && CombatTargets.hitId(fuse.victim()) == CombatTargets.hitId(actualTarget)
                && FUSES.remove(caster.getUUID(), fuse)) detonate(fuse, fuse.cast().spec.bonus());
    }

    private static void detonate(Fuse fuse, double multiplier) {
        var cast = fuse.cast();
        var victim = fuse.victim();
        if (liveTarget(cast) == null || !cast.valid()) return;
        markRing(cast.caster, cast.art, victim.position(), Math.max(0.75, victim.getBbWidth()), false);
        var source = ElementalRules.source(cast.caster, DamageType.FIRE);
        float damage = StatsRuntime.outgoing(cast.caster, cast.base, multiplier * pvp(cast.caster, victim));
        if (CombatDamageService.hurtElementalTick(cast.caster, victim, source, damage)) {
            LOG.info("GOLDEN_CORE actor={} art={} event=fuse target={} multiplier={}", CombatCaster.name(cast.caster),
                    cast.art.id(), victim.getId(), multiplier);
        }
    }

    static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (var entry : List.copyOf(TRACES.entrySet())) {
            Trace trace = entry.getValue();
            if (!trace.cast.valid()) { TRACES.remove(entry.getKey(), trace); continue; }
            if (now >= trace.due && TRACES.remove(entry.getKey(), trace)) release(trace);
            else if (now % TELEGRAPH_INTERVAL == 0) {
                markStrip(trace.cast.caster, trace.cast.art, trace.first, true);
                if (trace.second != null) markStrip(trace.cast.caster, trace.cast.art, trace.second, true);
            }
        }
        for (var entry : List.copyOf(TRAILS.entrySet())) {
            Trail trail = entry.getValue();
            if (!trail.cast.valid() || !tickTrail(trail, now)) TRAILS.remove(entry.getKey(), trail);
        }
        for (var entry : List.copyOf(FUSES.entrySet())) {
            Fuse fuse = entry.getValue();
            if (!fuse.cast.valid() || liveTarget(fuse.cast) == null) { FUSES.remove(entry.getKey(), fuse); continue; }
            if (now >= fuse.due && FUSES.remove(entry.getKey(), fuse)) detonate(fuse, fuse.cast.spec.secondary());
            else if (now % TELEGRAPH_INTERVAL == 0) markTarget(fuse.cast.caster, fuse.cast.art, fuse.victim(), true);
        }
    }

    private static void release(Trace trace) {
        Cast cast = trace.cast;
        Map<Integer, Entity> targets = new LinkedHashMap<>();
        var counts = new HashMap<Integer, Integer>();
        var lines = trace.second == null ? List.of(trace.first) : List.of(trace.first, trace.second);
        for (var line : lines) {
            if (!available(cast.caster, line.start().lerp(line.end(), 0.5), cast.spec.range())) continue;
            markStrip(cast.caster, cast.art, line, false);
            for (var target : distinct(inStrip(cast.caster, cast.session, line), cast.spec.targets())) {
                int key = CombatTargets.hitId(target);
                targets.putIfAbsent(key, target);
                counts.merge(key, 1, Integer::sum);
            }
        }
        for (var target : targets.values().stream().limit(cast.spec.targets()).toList()) {
            int contacts = counts.get(CombatTargets.hitId(target));
            hit(cast, target, cast.spec.damage() * contacts + (contacts > 1 ? cast.spec.bonus() : 0), 1);
        }
    }

    private static boolean tickTrail(Trail trail, long now) {
        Cast cast = trail.cast;
        trail.parts.removeIf(part -> now >= part.until());
        if (trail.recording) {
            Entity target = liveTarget(cast);
            if (target == null || now >= trail.recordUntil || trail.left < 0.1) trail.recording = false;
            else recordTrail(trail, target.position(), now);
        }
        if (now % TELEGRAPH_INTERVAL == 0) {
            for (var part : List.copyOf(trail.parts)) {
                if (!cast.valid()) return false;
                markStrip(cast.caster, cast.art, part.strip(), true);
                int limit = cast.spec.targets() - cast.contacts.size();
                if (limit <= 0) continue;
                int ignored = CombatTargets.hitId(cast.aim.target());
                var candidates = inStrip(cast.caster, cast.session, part.strip()).stream().filter(target -> {
                    int hitId = CombatTargets.hitId(target);
                    return hitId != ignored && !cast.contacts.containsKey(hitId) && isGrounded(target);
                }).toList();
                for (var target : distinct(candidates, limit)) {
                    if (hit(cast, target, cast.spec.damage(), 1) && CombatTargets.owner(target) instanceof LivingEntity living)
                        ControlService.slow(cast.caster, living, cast.art.id(), cast.spec.statusTicks(), cast.spec.status());
                }
            }
        }
        return trail.recording || !trail.parts.isEmpty();
    }

    private static void recordTrail(Trail trail, Vec3 target, long now) {
        Vec3 displacement = target.subtract(trail.last);
        double distance = horizontalDistance(target, trail.last);
        if (distance < 0.65) return;
        if (distance > 3 || Math.abs(displacement.y) > 1.2) {
            trail.recording = false;
            return;
        }
        var path = groundPath(trail.cast.caster, trail.last, horizontal(displacement), Math.min(distance, trail.left));
        if (path.size() < 2) return;
        var strip = new Strip(path.getFirst(), path.getLast(), trail.cast.spec.width());
        trail.parts.add(new TrailPart(strip, now + trail.cast.spec.duration()));
        trail.left -= strip.length();
        trail.last = strip.end();
    }

    static void forget(UUID id) { TRACES.remove(id); TRAILS.remove(id); FUSES.remove(id); }
    static void clear() { TRACES.clear(); TRAILS.clear(); FUSES.clear(); }
}
