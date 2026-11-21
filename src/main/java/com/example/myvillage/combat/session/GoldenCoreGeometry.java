package com.example.myvillage.combat.session;

import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.runtime.CombatCaster;
import com.example.myvillage.combat.runtime.CombatHitResolver;
import com.example.myvillage.combat.runtime.CombatStepService;
import com.example.myvillage.combat.runtime.CombatTargets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Hit geometry and its read-only outlines use the same points and extents. */
final class GoldenCoreGeometry {
    static final double GROUND_HEIGHT = 0.55;
    private static final double WALK_SAMPLE = 0.5;
    private static final double HIT_BELOW = 1.15;
    private static final double HIT_ABOVE = 2.5;
    private static final int POINTS_PER_TICK = 192;
    private static long particleTick = Long.MIN_VALUE;
    private static int remainingPoints;

    private GoldenCoreGeometry() {}

    record Strip(Vec3 start, Vec3 end, double width) {
        Vec3 forward() { return horizontal(end.subtract(start)); }
        Vec3 side() { return lateral(forward()); }
        double length() { return horizontalDistance(start, end); }
        AABB bounds() { return new AABB(start, end).inflate(width / 2 + 0.1, HIT_ABOVE, width / 2 + 0.1); }

        boolean intersects(AABB box) {
            if (length() < 0.01 || box.maxY < Math.min(start.y, end.y) - HIT_BELOW
                    || box.minY > Math.max(start.y, end.y) + HIT_ABOVE) return false;
            Vec3 center = box.getCenter().subtract(start);
            Vec3 forward = forward();
            Vec3 side = side();
            double near = extent(box, forward);
            double along = center.dot(forward);
            return along + near >= 0 && along - near <= length()
                    && Math.abs(center.dot(side)) <= width / 2 + extent(box, side);
        }

        private static double extent(AABB box, Vec3 axis) {
            return (box.getXsize() * Math.abs(axis.x) + box.getZsize() * Math.abs(axis.z)) / 2;
        }

        Vec3 lineOrigin(Entity target) {
            double f = Math.clamp(target.getBoundingBox().getCenter().subtract(start).dot(forward()) / length(), 0, 1);
            return start.lerp(end, f).add(0, 0.8, 0);
        }
    }

    static Vec3 horizontal(Vec3 vector) { return new Vec3(vector.x, 0, vector.z).normalize(); }
    static Vec3 lateral(Vec3 vector) { return new Vec3(vector.z, 0, -vector.x); }
    static double horizontalDistance(Vec3 a, Vec3 b) { return Math.sqrt(a.subtract(b).horizontalDistanceSqr()); }

    static Entity acquire(LivingEntity caster, CombatSession session, double range, double degrees,
                          boolean directFirst, Predicate<Entity> eligible) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        double threshold = Math.cos(Math.toRadians(degrees / 2));
        Comparator<Entity> byAim = Comparator.<Entity>comparingInt(e -> directFirst && rayIntersects(e, eye, end) ? 0 : 1)
                .thenComparingDouble(e -> directFirst && rayIntersects(e, eye, end)
                        ? e.getBoundingBox().distanceToSqr(eye) : -e.getBoundingBox().getCenter().subtract(eye).normalize().dot(look))
                .thenComparingDouble(e -> e.getBoundingBox().distanceToSqr(eye))
                .thenComparingInt(Entity::getId);
        return level(caster).getEntities(caster, caster.getBoundingBox().inflate(range), e ->
                CombatHitResolver.legalTarget(caster, e, session) && eligible.test(e)).stream()
                .filter(e -> inReach(caster, e, range))
                .filter(e -> rayIntersects(e, eye, end) || e.getBoundingBox().getCenter().subtract(eye).normalize().dot(look) >= threshold)
                .sorted(byAim).filter(e -> visible(caster, e)).findFirst().orElse(null);
    }

    private static boolean rayIntersects(Entity e, Vec3 start, Vec3 end) {
        return e.getBoundingBox().contains(start) || e.getBoundingBox().clip(start, end).isPresent();
    }

    static Vec3 groundAim(LivingEntity caster, CombatSession session, double range, double arc) {
        Vec3 from = caster.getEyePosition();
        Vec3 to = from.add(caster.getLookAngle().scale(range));
        var hit = clip(caster, from, to);
        var target = acquire(caster, session, range, arc, true, e -> true);
        if (target != null && rayIntersects(target, from, to)) {
            var underTarget = ground(caster, target.getBoundingBox().getCenter().with(Direction.Axis.Y, target.getBoundingBox().minY));
            if (underTarget != null) return underTarget;
        }
        if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP)
            return hit.getLocation();
        return target == null ? null : ground(caster, target.position());
    }

    static Vec3 ground(LivingEntity caster, Vec3 around) {
        var hit = clip(caster, around.add(0, 0.8, 0), around.add(0, -1.2, 0));
        return hit == null || hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP
                ? null : hit.getLocation();
    }

    static List<Vec3> groundPath(LivingEntity caster, Vec3 origin, Vec3 direction, double length) {
        var floor = ground(caster, origin);
        if (floor == null) return List.of();
        var result = new ArrayList<Vec3>();
        result.add(floor);
        int steps = (int) Math.ceil(length / WALK_SAMPLE);
        for (int i = 1; i <= steps; i++) {
            Vec3 next = origin.add(direction.scale(length * i / steps));
            Vec3 previous = result.getLast();
            Vec3 surface = ground(caster, new Vec3(next.x, previous.y, next.z));
            if (surface == null || Math.abs(surface.y - previous.y) > 1
                    || !clear(caster, previous.add(0, 0.7, 0), surface.add(0, 0.7, 0))) break;
            result.add(surface);
        }
        return result;
    }

    static boolean followsGround(LivingEntity caster, Vec3 anchor, Entity target) {
        var to = target.getBoundingBox().getCenter();
        Vec3 direction = horizontal(to.subtract(anchor));
        double distance = horizontalDistance(anchor, to);
        var path = groundPath(caster, anchor, direction, distance);
        return isGrounded(target) && !path.isEmpty() && horizontalDistance(path.getLast(), to) <= 0.25
                && Math.abs(path.getLast().y - target.getBoundingBox().minY) <= GROUND_HEIGHT;
    }

    static boolean isGrounded(Entity target) {
        return target == CombatTargets.owner(target) ? target.onGround() : true;
    }

    static List<Entity> inArea(LivingEntity caster, CombatSession session, Vec3 center, double radius) {
        var bounds = new AABB(center, center).inflate(radius + 0.1, HIT_ABOVE, radius + 0.1);
        return candidates(caster, session, bounds, center,
                e -> withinRadius(center, e.getBoundingBox(), radius) && clear(caster, center.add(0, 0.8, 0), point(e)));
    }

    static boolean withinRadius(Vec3 center, AABB box, double radius) {
        double dx = center.x - Math.clamp(center.x, box.minX, box.maxX);
        double dz = center.z - Math.clamp(center.z, box.minZ, box.maxZ);
        return dx * dx + dz * dz <= radius * radius
                && box.maxY >= center.y - HIT_BELOW && box.minY <= center.y + HIT_ABOVE;
    }

    static List<Entity> inStrip(LivingEntity caster, CombatSession session, Strip strip) {
        return candidates(caster, session, strip.bounds(), strip.start(), e -> strip.intersects(e.getBoundingBox())
                && clear(caster, strip.lineOrigin(e), point(e)));
    }

    static List<Entity> candidates(LivingEntity caster, CombatSession session, AABB bounds, Vec3 orderOrigin,
                                   Predicate<Entity> filter) {
        return level(caster).getEntities(caster, bounds, e -> CombatHitResolver.legalTarget(caster, e, session)).stream()
                .filter(e -> !CombatTargets.owner(e).isAlliedTo(caster) && filter.test(e) && visible(caster, e))
                .sorted(Comparator.<Entity>comparingDouble(e -> e.getBoundingBox().distanceToSqr(orderOrigin))
                        .thenComparingInt(Entity::getId)).toList();
    }

    static boolean inReach(LivingEntity caster, Entity target, double range) {
        return target.getBoundingBox().distanceToSqr(caster.position()) <= range * range;
    }

    static boolean visible(LivingEntity caster, Entity target) {
        return clear(caster, caster.getEyePosition(), point(target));
    }

    static Vec3 point(Entity target) { return target.getBoundingBox().getCenter(); }

    static boolean clear(LivingEntity caster, Vec3 from, Vec3 to) {
        var hit = clip(caster, from, to);
        return hit != null && (hit.getType() != HitResult.Type.BLOCK
                || hit.getLocation().distanceToSqr(to) < 0.0001);
    }

    static boolean available(LivingEntity caster, Vec3 anchor, double range) {
        return caster.position().distanceToSqr(anchor) <= range * range && level(caster).hasChunkAt(BlockPos.containing(anchor))
                && clear(caster, caster.getEyePosition(), anchor.add(0, 0.35, 0));
    }

    private static BlockHitResult clip(LivingEntity caster, Vec3 from, Vec3 to) {
        var world = level(caster);
        if (!world.hasChunksAt(BlockPos.containing(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z)),
                BlockPos.containing(Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)))) return null;
        return world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
    }

    static double step(LivingEntity entity, Vec3 direction, double requested) {
        if (requested <= 0 || direction.horizontalDistanceSqr() < 0.5 || !entity.onGround()) return 0;
        double safe = CombatStepService.safeDistance(entity, direction, requested, 0.65);
        if (safe <= 0) return 0;
        Vec3 start = entity.position();
        entity.move(MoverType.SELF, direction.scale(safe));
        if (entity instanceof ServerPlayer player && player.position().distanceToSqr(start) > 0.001)
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        return horizontalDistance(start, entity.position());
    }

    static void markStrip(LivingEntity caster, ArtDefinition art, Strip strip, boolean warning) {
        Vec3 offset = strip.side().scale(strip.width() / 2);
        markLine(caster, art, strip.start().add(offset), strip.end().add(offset), warning, 14);
        markLine(caster, art, strip.start().subtract(offset), strip.end().subtract(offset), warning, 14);
    }

    static void markLine(LivingEntity caster, ArtDefinition art, Vec3 start, Vec3 end, boolean warning, int limit) {
        int steps = Math.clamp((int) Math.ceil(start.distanceTo(end) / 0.8), 1, limit);
        var dust = dust(art, warning);
        for (int i = 0; i <= steps; i++) particle(caster, dust, start.lerp(end, i / (double) steps).add(0, 0.17, 0));
    }

    static void markRing(LivingEntity caster, ArtDefinition art, Vec3 center, double radius, boolean warning) {
        int count = Math.clamp((int) Math.ceil(radius * 4), 10, 40);
        var dust = dust(art, warning);
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count;
            particle(caster, dust, center.add(Math.cos(angle) * radius, 0.17, Math.sin(angle) * radius));
        }
    }

    static void markTarget(LivingEntity caster, ArtDefinition art, Entity target, boolean warning) {
        Vec3 center = target.position();
        markRing(caster, art, center.add(0, 0.12, 0), Math.clamp(target.getBbWidth() / 2, 0.45, 2), warning);
    }

    private static DustParticleOptions dust(ArtDefinition art, boolean warning) {
        return new DustParticleOptions(new Vector3f(art.red(), art.green(), art.blue()), warning ? 0.8F : 1.4F);
    }

    private static void particle(LivingEntity caster, DustParticleOptions dust, Vec3 pos) {
        var world = level(caster);
        long now = world.getServer().overworld().getGameTime();
        if (now != particleTick) { particleTick = now; remainingPoints = POINTS_PER_TICK; }
        if (remainingPoints <= 0) return;
        remainingPoints--;
        for (ServerPlayer player : world.players()) {
            if (player.distanceToSqr(pos) <= 64 * 64)
                world.sendParticles(player, dust, true, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }
    }

    static ServerLevel level(LivingEntity caster) { return CombatCaster.level(caster); }
}
