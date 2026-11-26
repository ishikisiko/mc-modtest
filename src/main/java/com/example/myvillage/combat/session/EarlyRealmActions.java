package com.example.myvillage.combat.session;

import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.art.EarlyRealmArt;
import com.example.myvillage.combat.art.GoldenCoreArt;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.elemental.ElementalEffects;
import com.example.myvillage.combat.foundation.ControlService;
import com.example.myvillage.combat.runtime.CombatCaster;
import com.example.myvillage.combat.runtime.CombatDisciplines;
import com.example.myvillage.combat.runtime.CombatHitResolver;
import com.example.myvillage.combat.runtime.CombatTargets;
import com.example.myvillage.combat.runtime.CombatTraitStates;
import com.example.myvillage.cultivation.growth.TechniqueGrowthRuntime;
import com.example.myvillage.stats.DamageType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.myvillage.combat.session.GoldenCoreGeometry.*;

final class EarlyRealmActions {
    private static final Logger LOG = LoggerFactory.getLogger(EarlyRealmActions.class);
    private static final double LINE_HEIGHT = .8;
    private static final double FACE_OFFSET = .05;
    static final int WARNING_INTERVAL = 4;

    private EarlyRealmActions() {}

    record Aim(Vec3 origin, Vec3 forward, Vec3 anchor, Entity target, double side) {}

    static final class Cast {
        final LivingEntity caster;
        final ArtDefinition art;
        final EarlyRealmArt spec;
        final Aim aim;
        final ItemStack held;
        final int slot;
        final ServerLevel world;
        final double base;
        final boolean paid;
        final Map<Integer, Double> before = new HashMap<>();
        CombatSession session;
        long revision;
        boolean mastery;

        Cast(LivingEntity caster, ArtDefinition art, CombatSession session, Aim aim, boolean paid) {
            this.caster = caster;
            this.art = art;
            this.session = session;
            this.aim = aim;
            this.paid = paid;
            spec = art.earlyRealm().orElseThrow();
            held = caster.getMainHandItem();
            slot = CombatCaster.slot(caster);
            world = level(caster);
            base = spec.element().map(element -> RealmArtCombat.base(caster, element, paid)).orElse(0.0);
        }

        boolean valid() {
            return caster.level() == world && CombatCaster.active(caster) && CombatCaster.inventoryFree(caster)
                    && held == caster.getMainHandItem() && slot == CombatCaster.slot(caster)
                    && session.hasActiveAction() && session.revision() == revision
                    && CombatArtActions.authorized(caster, art);
        }

        long now() { return world.getServer().overworld().getGameTime(); }

        void retain() {
            var style = CombatStyles.bundled().move(art.phases().getFirst().moveId()).orElseThrow().style();
            float yaw = session.facingYaw();
            session = new CombatSession(style);
            session.startExternal(art.move(0, 1), world.getGameTime(), BuiltInRegistries.ITEM.getKey(held.getItem()),
                    world.dimension().location(), yaw, paid);
            revision = session.revision();
        }
    }

    static Aim prepare(LivingEntity caster, CombatSession session, ArtDefinition art) {
        EarlyRealmArt spec = art.earlyRealm().orElseThrow();
        Vec3 origin = caster.position();
        Vec3 forward = horizontal(Vec3.directionFromRotation(0, caster.getYRot()));
        Vec3 anchor = null;
        Entity target = null;
        double arc = GoldenCoreArt.rules().aimArc();
        switch (spec.mechanic()) {
            case VINE_LINE, STEPPING_SPEAR -> {
                target = acquire(caster, session, spec.range(), arc, true,
                        e -> spec.mechanic() != EarlyRealmArt.Mechanic.VINE_LINE
                                || ElementalEffects.harvestDamage(caster, e, DamageType.WOOD) > 0);
                if (target == null) return null;
                forward = aimedForward(origin, target, forward);
            }
            case GATHER_TIDE, EARTH_SPIKE, HEALING_BRANCH, MATURE_FLAME, STONE_SCREEN -> {
                anchor = groundAim(caster, session, spec.range(), arc);
                if (anchor == null || !available(caster, anchor, spec.range())) return null;
            }
            case WAITING_FLAME -> {
                anchor = ground(caster, origin);
                if (anchor == null) return null;
            }
            case BRACE_SPEAR, SHOULDER_RAM, SHOULDER_THROW, ARMOR_LINE -> {
                target = acquire(caster, session, spec.range(), arc, true, e -> true);
                if (target != null) forward = aimedForward(origin, target, forward);
            }
            case SIDESTEP_SLASH, LINGERING_SLASH, SWEEPING_SPEAR, ARROW_RING, BACK_GUARD, REFLECTED_WAVE -> { }
        }
        return new Aim(origin, forward, anchor, target, caster.isShiftKeyDown() ? -1 : 1);
    }

    static void resolve(Cast cast, int frame) {
        var a = cast.spec;
        if (frame < a.windup()) {
            if (frame == 0 && a.mechanic() == EarlyRealmArt.Mechanic.BRACE_SPEAR) snapshotApproach(cast);
            if (frame % WARNING_INTERVAL == 0) warn(cast);
        } else if (frame == a.windup()) {
            strike(cast);
        }
    }

    private static void warn(Cast cast) {
        var a = cast.spec;
        var caster = cast.caster;
        Vec3 at = caster.position();
        switch (a.mechanic()) {
            case SIDESTEP_SLASH, LINGERING_SLASH, SWEEPING_SPEAR, ARROW_RING ->
                    markFan(caster, cast.art, fan(cast, at, a.mechanic() == EarlyRealmArt.Mechanic.ARROW_RING ? a.radius() : a.range()), true);
            case REFLECTED_WAVE -> {
                var path = wavePath(cast, at);
                markStrip(caster, cast.art, path.front(), true);
                if (path.reflected() != null) markStrip(caster, cast.art, path.reflected(), true);
            }
            case STONE_SCREEN -> markScreen(caster, cast.art, cast.aim.anchor(), cast.aim.forward(), a.width(), a.height(), true);
            case GATHER_TIDE, EARTH_SPIKE, HEALING_BRANCH, MATURE_FLAME ->
                    markRing(caster, cast.art, cast.aim.anchor(), a.radius(), true);
            case BACK_GUARD -> markFan(caster, cast.art, fan(cast, at, a.range()), true);
            case VINE_LINE -> {
                if (targetReady(cast, cast.aim.target()))
                    markStrip(caster, cast.art, lineTo(cast, at, cast.aim.target()), true);
            }
            default -> markStrip(caster, cast.art, line(cast, at, cast.aim.forward(), a.range()), true);
        }
    }

    private static void strike(Cast cast) {
        var a = cast.spec;
        var caster = cast.caster;
        Vec3 at = caster.position();
        Vec3 forward = cast.aim.forward();
        switch (a.mechanic()) {
            case SIDESTEP_SLASH -> {
                move(caster, horizontal(forward.add(lateral(forward).scale(-cast.aim.side()))), a.step());
                fanStrike(cast, caster.position(), a.damage(), false);
            }
            case BRACE_SPEAR -> brace(cast, at);
            case SHOULDER_RAM -> {
                move(caster, forward, a.step());
                var target = firstLine(cast, caster.position(), forward, a.length());
                if (target != null && hit(cast, target, a.damage())) displace(cast, target, forward, a.push());
            }
            case VINE_LINE -> vine(cast);
            case GATHER_TIDE -> gather(cast);
            case EARTH_SPIKE -> spike(cast);
            case LINGERING_SLASH -> {
                fanStrike(cast, at, a.damage(), false);
                if (cast.valid()) EarlyRealmEffects.place(cast, at, a.delay());
            }
            case STEPPING_SPEAR -> steppingSpear(cast);
            case SWEEPING_SPEAR -> fanStrike(cast, at, a.damage(), true);
            case SHOULDER_THROW -> shoulderThrow(cast);
            case ARMOR_LINE -> armorLine(cast);
            case REFLECTED_WAVE -> reflectedWave(cast);
            case WAITING_FLAME -> {
                var lane = groundLine(cast, cast.aim.anchor());
                if (lane.length() > .05) EarlyRealmEffects.place(cast, lane.end(), a.delay());
            }
            case ARROW_RING, BACK_GUARD -> EarlyRealmEffects.place(cast, at, a.duration());
            case HEALING_BRANCH, MATURE_FLAME, STONE_SCREEN -> {
                if (available(caster, cast.aim.anchor(), a.range()))
                    EarlyRealmEffects.place(cast, cast.aim.anchor(), a.duration());
            }
        }
    }

    private static void snapshotApproach(Cast cast) {
        Vec3 at = cast.aim.origin();
        var lane = line(cast, at, cast.aim.forward(), cast.spec.range() + cast.spec.threshold() + 1);
        for (Entity entity : inStrip(cast.caster, cast.session, lane).stream().limit(24).toList())
            cast.before.put(CombatTargets.hitId(entity), edgeDistance(at, entity));
    }

    private static void brace(Cast cast, Vec3 at) {
        Entity target = firstLine(cast, at, cast.aim.forward(), cast.spec.length());
        if (target == null) return;
        double now = edgeDistance(at, target);
        boolean advanced = cast.before.getOrDefault(CombatTargets.hitId(target), now)
                - edgeDistance(cast.aim.origin(), target) >= cast.spec.threshold();
        boolean tip = spearTip(now);
        if (hit(cast, target, advanced && tip ? cast.spec.secondary() : cast.spec.damage()) && tip) {
            CombatDisciplines.tipAccepted(cast.caster, cast.session.revision());
            if (advanced) CombatTraitStates.grant(cast.caster, cast.caster, CombatTraitStates.Kind.SPEAR_STANCE);
        }
    }

    private static void steppingSpear(Cast cast) {
        Entity target = cast.aim.target();
        if (!targetReady(cast, target)) return;
        Vec3 forward = cast.aim.forward();
        var back = horizontal(forward.scale(-1).add(lateral(forward).scale(.55 * cast.aim.side())));
        double space = Math.max(0, cast.spec.range() - edgeDistance(cast.caster.position(), target));
        move(cast.caster, back, Math.min(cast.spec.step(), space));
        Vec3 at = cast.caster.position();
        var line = line(cast, at, aimedForward(at, target, forward), cast.spec.length());
        markStrip(cast.caster, cast.art, line, false);
        boolean touched = line.intersects(target.getBoundingBox()) && clear(cast.caster, line.lineOrigin(target), point(target));
        boolean tip = spearTip(edgeDistance(at, target));
        if (touched && targetReady(cast, target) && hit(cast, target, cast.spec.damage()) && tip)
            CombatDisciplines.tipAccepted(cast.caster, cast.session.revision());
    }

    private static void shoulderThrow(Cast cast) {
        var caster = cast.caster;
        Vec3 backward = cast.aim.forward().scale(-1);
        Entity entity = firstLine(cast, caster.position(), cast.aim.forward(), cast.spec.length());
        if (!(entity instanceof LivingEntity target)) {
            if (entity != null) hit(cast, entity, cast.spec.damage());
            return;
        }
        if (!hit(cast, target, cast.spec.damage()) || !RealmArtCombat.canDisplace(caster, target)) return;
        if (target instanceof Player) {
            displace(cast, target, cast.aim.forward(), cast.spec.push());
            return;
        }
        Vec3 behind = caster.position().add(backward.scale(cast.spec.push()));
        displace(cast, target, horizontal(behind.subtract(target.position())), horizontalDistance(behind, target.position()));
    }

    private static void vine(Cast cast) {
        Entity target = cast.aim.target();
        if (!targetReady(cast, target) || ElementalEffects.harvestDamage(cast.caster, target, DamageType.WOOD) <= 0) return;
        var line = lineTo(cast, cast.caster.position(), target);
        markStrip(cast.caster, cast.art, line, false);
        for (Entity foe : distinct(inStrip(cast.caster, cast.session, line), cast.spec.targets())) {
            if (hit(cast, foe, cast.spec.damage()) && CombatTargets.owner(foe) instanceof LivingEntity living)
                ControlService.slow(cast.caster, living, cast.art.id(), cast.spec.statusTicks(), cast.spec.potency());
        }
    }

    private static void gather(Cast cast) {
        Vec3 anchor = cast.aim.anchor();
        if (!available(cast.caster, anchor, cast.spec.range())) return;
        markRing(cast.caster, cast.art, anchor, cast.spec.radius(), false);
        var candidates = inArea(cast.caster, cast.session, anchor, cast.spec.radius());
        if (candidates.isEmpty()) return;
        Entity target = candidates.getFirst();
        if (hit(cast, target, cast.spec.damage()))
            displace(cast, target, horizontal(anchor.subtract(target.position())),
                    Math.min(cast.spec.push(), horizontalDistance(anchor, target.position())));
    }

    private static void spike(Cast cast) {
        Vec3 anchor = cast.aim.anchor();
        if (!available(cast.caster, anchor, cast.spec.range())) return;
        markRing(cast.caster, cast.art, anchor, cast.spec.radius(), false);
        var grounded = inArea(cast.caster, cast.session, anchor, cast.spec.radius()).stream()
                .filter(e -> followsGround(cast.caster, anchor, e)).toList();
        for (Entity enemy : distinct(grounded, cast.spec.targets())) {
            if (hit(cast, enemy, cast.spec.damage()) && enemy instanceof LivingEntity living
                    && RealmArtCombat.canDisplace(cast.caster, living)) {
                double lift = cast.spec.push() * RealmArtCombat.displacementFactor(cast.caster, living);
                if (lift > 0) {
                    var motion = living.getDeltaMovement();
                    living.setDeltaMovement(motion.x, Math.max(lift, motion.y), motion.z);
                    living.hurtMarked = true;
                }
            }
        }
    }

    private static void armorLine(Cast cast) {
        Vec3 forward = cast.aim.forward();
        Entity first = firstLine(cast, cast.caster.position(), forward, cast.spec.length());
        if (first == null) return;
        boolean armored = CombatTargets.owner(first) instanceof LivingEntity owner
                && owner.getAttributeValue(Attributes.ARMOR) >= cast.spec.threshold();
        if (!hit(cast, first, cast.spec.damage()) || !armored) return;
        Vec3 point = point(first);
        var next = inArea(cast.caster, cast.session, point, cast.spec.secondaryRange()).stream()
                .filter(e -> CombatTargets.hitId(e) != CombatTargets.hitId(first)
                        && horizontal(point(e).subtract(point)).dot(forward) >= .4
                        && clear(cast.caster, point, point(e))).findFirst().orElse(null);
        if (next != null) {
            markLine(cast.caster, cast.art, point, point(next), false, 16);
            hit(cast, next, cast.spec.secondary());
        }
    }

    record Wave(Strip front, Strip reflected) {}
    private static Wave wavePath(Cast cast, Vec3 at) {
        Vec3 forward = cast.aim.forward();
        Vec3 high = at.add(0, LINE_HEIGHT, 0);
        var contact = clip(cast.caster, high, high.add(forward.scale(cast.spec.length())));
        if (contact == null || contact.getType() != HitResult.Type.BLOCK)
            return new Wave(line(cast, at, forward, cast.spec.length()), null);
        Vec3 corner = contact.getLocation().add(0, -LINE_HEIGHT, 0);
        var front = new Strip(at, corner, cast.spec.width());
        Vec3 normal = Vec3.atLowerCornerOf(contact.getDirection().getNormal());
        Vec3 reflected = reflectedDirection(forward, normal);
        if (normal.horizontalDistanceSqr() < .5 || reflected.horizontalDistanceSqr() < .5) return new Wave(front, null);
        var next = line(cast, corner.add(normal.scale(FACE_OFFSET)), reflected, cast.spec.secondaryRange());
        return new Wave(front, next);
    }

    private static void reflectedWave(Cast cast) {
        var path = wavePath(cast, cast.caster.position());
        var seen = new HashSet<Integer>();
        sweep(cast, path.front(), cast.spec.damage(), seen);
        if (path.reflected() != null) sweep(cast, path.reflected(), cast.spec.secondary(), seen);
    }

    private static void sweep(Cast cast, Strip strip, double multiplier, Set<Integer> seen) {
        markStrip(cast.caster, cast.art, strip, false);
        for (Entity target : alongRay(cast.caster, cast.session, strip)) {
            if (seen.size() >= cast.spec.targets()) break;
            if (seen.add(CombatTargets.hitId(target))) hit(cast, target, multiplier);
        }
    }

    static void fanStrike(Cast cast, Vec3 at, double damage, boolean spear) {
        var fan = fan(cast, at, cast.spec.range());
        markFan(cast.caster, cast.art, fan, false);
        for (Entity enemy : distinct(inFan(cast.caster, cast.session, fan), cast.spec.targets())) {
            boolean tip = spear && spearTip(edgeDistance(at, enemy));
            double multiplier = tip ? cast.spec.secondary() : damage;
            if (hit(cast, enemy, multiplier) && spear) {
                if (tip) CombatDisciplines.tipAccepted(cast.caster, cast.session.revision());
                displace(cast, enemy, lateral(cast.aim.forward()).scale(cast.aim.side()), cast.spec.push());
            }
        }
    }

    static Strip groundLine(Cast cast, Vec3 at) {
        return groundLine(cast, at, cast.spec.length());
    }

    private static Strip groundLine(Cast cast, Vec3 at, double length) {
        var path = groundPath(cast.caster, at, cast.aim.forward(), length);
        Vec3 end = path.isEmpty() ? at : path.getLast();
        return new Strip(at, end, cast.spec.width());
    }

    static void flameLine(Cast cast, Vec3 markedEnd) {
        var line = groundLine(cast, cast.aim.anchor(), horizontalDistance(cast.aim.anchor(), markedEnd));
        markStrip(cast.caster, cast.art, line, false);
        for (Entity enemy : distinct(inStrip(cast.caster, cast.session, line), cast.spec.targets())) {
            if (hit(cast, enemy, cast.spec.damage()) && CombatTargets.owner(enemy).isAlive())
                ElementalEffects.seed(cast.caster, enemy, DamageType.FIRE, cast.base * RealmArtCombat.pvpDamage(cast.caster, enemy));
        }
    }

    static void flameBurst(Cast cast, Vec3 center, double multiplier) {
        markRing(cast.caster, cast.art, center, cast.spec.radius(), false);
        for (Entity enemy : distinct(inArea(cast.caster, cast.session, center, cast.spec.radius()), cast.spec.targets()))
            hit(cast, enemy, multiplier);
    }

    static void reflectArrow(Cast cast, Vec3 direction) {
        var target = firstLine(cast, cast.caster.position(), direction, cast.spec.length());
        if (target != null) hit(cast, target, cast.spec.damage());
    }

    private static Entity firstLine(Cast cast, Vec3 at, Vec3 direction, double distance) {
        var line = line(cast, at, direction, distance);
        markStrip(cast.caster, cast.art, line, false);
        var targets = inStrip(cast.caster, cast.session, line);
        return targets.isEmpty() ? null : targets.getFirst();
    }

    private static Strip lineTo(Cast cast, Vec3 at, Entity target) {
        return line(cast, at, aimedForward(at, target, cast.aim.forward()),
                Math.min(cast.spec.length(), horizontalDistance(at, point(target))));
    }

    private static Strip line(Cast cast, Vec3 at, Vec3 direction, double distance) {
        return new Strip(at, unobstructedEnd(cast.caster, at, at.add(direction.scale(distance))), cast.spec.width());
    }

    static Fan fan(Cast cast, Vec3 at, double radius) {
        return new Fan(at, cast.aim.forward(), radius, cast.spec.arc());
    }

    private static Vec3 aimedForward(Vec3 from, Entity target, Vec3 fallback) {
        Vec3 direction = horizontal(point(target).subtract(from));
        return direction.horizontalDistanceSqr() > .5 ? direction : fallback;
    }

    private static double edgeDistance(Vec3 from, Entity target) {
        return Math.sqrt(target.getBoundingBox().distanceToSqr(from));
    }

    private static boolean spearTip(double distance) {
        var settings = GoldenCoreArt.rules();
        return distance >= settings.spearTipMin() && distance <= settings.spearTipMax() + .0001;
    }

    private static boolean targetReady(Cast cast, Entity target) {
        return target != null && CombatHitResolver.legalTarget(cast.caster, target, cast.session)
                && inReach(cast.caster, target, cast.spec.range()) && visible(cast.caster, target);
    }

    private static List<Entity> distinct(List<Entity> candidates, int count) {
        return CombatTargets.firstDistinct(candidates, CombatTargets::hitId, count);
    }

    private static void move(LivingEntity caster, Vec3 direction, double distance) {
        if (!ControlService.isRooted(caster)) step(caster, direction, distance);
    }

    private static void displace(Cast cast, Entity entity, Vec3 direction, double distance) {
        if (entity instanceof LivingEntity target && RealmArtCombat.canDisplace(cast.caster, target))
            step(target, direction, distance * RealmArtCombat.displacementFactor(cast.caster, target));
    }

    private static boolean hit(Cast cast, Entity target, double multiplier) {
        if (!cast.valid() || !CombatHitResolver.legalTarget(cast.caster, target, cast.session)) return false;
        float damage = RealmArtCombat.hit(cast.caster, target, cast.art, cast.session, multiplier, cast.base);
        if (!(damage > 0) || !cast.valid()) return false;
        switch (cast.spec.mechanic()) {
            case SHOULDER_RAM, SHOULDER_THROW ->
                    CombatTraitStates.grant(cast.caster, target, CombatTraitStates.Kind.FIST_UNBALANCE);
            case EARTH_SPIKE -> CombatTraitStates.grant(cast.caster, target, CombatTraitStates.Kind.EARTH_UNBALANCE);
            default -> { }
        }
        award(cast);
        if (cast.spec.element().orElse(null) == DamageType.FIRE) GoldenCoreActions.onDirectFire(cast.caster, target);
        LOG.info("EARLY_REALM actor={} art={} event=hit target={} multiplier={} damage={}",
                CombatCaster.name(cast.caster), cast.art.id(), target.getId(), multiplier, damage);
        return true;
    }

    static void award(Cast cast) {
        if (cast.mastery) return;
        cast.mastery = true;
        TechniqueGrowthRuntime.hit(cast.caster, cast.art.id());
    }
}
