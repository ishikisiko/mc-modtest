package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.StepDefinition;
import com.example.myvillage.combat.session.CombatSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.DoublePredicate;
import java.util.function.Predicate;

/**
 * Server-decided action footwork. The server chooses whether and how far the attacker steps
 * (collision, support and target-magnetism checks) and then hands the owning client a motion
 * impulse, exactly like vanilla knockback: {@code setDeltaMovement} plus {@code hurtMarked}, which
 * {@code ServerEntity.sendChanges} turns into a {@code ClientboundSetEntityMotionPacket} for the
 * player and its trackers. The client's own physics carries it out, so there is no teleport snap
 * and no silent revert by the next client position packet. Hits never depend on the client's
 * echo: the resolver uses the server-planned origin recorded in the returned sweep.
 */
public final class CombatStepService {
    private static final double SAMPLE_INCREMENT = 0.1;
    /**
     * Ground drag compensation. A grounded entity keeps {@code 0.6 * 0.91 = 0.546} of its
     * horizontal speed each tick (block friction 0.6 times the 0.91 air factor), so an initial
     * speed {@code v} covers {@code v * (1 + 0.546 + 0.546^2 + ...) = v / 0.454} blocks.
     * Hence {@code v = distance * 0.454}; about 85% of the distance is covered in three ticks.
     */
    static final double GROUND_DRAG_COMPENSATION = 1.0 - 0.6 * 0.91;
    /** Steps up to this length are light footwork, which stays put when a target is in range. */
    static final double LIGHT_STEP_LIMIT = 0.5;
    /** Magnetism stops the attacker this far short of the target's hitbox edge. */
    static final double MAGNETISM_STANDOFF = 0.6;
    /** Magnetism considers targets within this half-angle of the action facing. */
    static final double MAGNETISM_HALF_ANGLE_DEGREES = 30.0;

    private CombatStepService() {
    }

    public static Optional<CombatHitResolver.StepSweep> tryStep(
            LivingEntity player,
            AttackMoveDefinition move,
            StepDefinition step,
            float facingYaw,
            CombatSession session) {
        if (!player.onGround()) {
            return Optional.empty();
        }
        Vec3 start = player.position();
        double yaw = Math.toRadians(facingYaw);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        double planned = magnetizedDistance(
                step.maximumDistance(),
                nearestTargetEdge(player, forward, move.range() + step.maximumDistance(), session),
                move.range(),
                step.maximumDistance() <= LIGHT_STEP_LIMIT);
        if (!(planned > 0.0)) {
            return Optional.empty();
        }
        double safeDistance = safeDistance(player, forward, planned, step.supportDepth());
        if (safeDistance <= 0.0) {
            return Optional.empty();
        }

        double speed = impulseForDistance(safeDistance);
        player.setDeltaMovement(forward.x * speed, 0.0, forward.z * speed);
        player.hurtMarked = true;
        // NPC physics runs on the server. Hit origins follow the actual body, never its intended endpoint.
        if (!(player instanceof ServerPlayer)) return Optional.empty();
        return Optional.of(new CombatHitResolver.StepSweep(
                start, start.add(forward.scale(safeDistance)), step.actionTick()));
    }

    /**
     * Longest distance up to {@code maximumDistance} (in {@value #SAMPLE_INCREMENT}-block samples) that
     * the player can slide along the horizontal unit vector {@code forward} without hitting a collider
     * and still with ground within {@code supportDepth} below the destination; 0 when none is safe.
     * Shared by action steps and the 身法 dodge.
     */
    public static double safeDistance(LivingEntity player, Vec3 forward, double maximumDistance, double supportDepth) {
        return chooseSafeDistance(
                maximumDistance,
                SAMPLE_INCREMENT,
                distance -> isSafeDestination(player, forward.scale(distance), supportDepth));
    }

    /**
     * Air movement has no destination-floor requirement. Test the entire swept body, not a center
     * ray or only the endpoint: a thin wall, overhang or stair must never be skipped. The enclosing
     * swept box deliberately errs towards refusing tight diagonal gaps.
     */
    static double safeAirDistance(LivingEntity player, Vec3 forward, double maximumDistance) {
        return chooseAirDistance(player.getBoundingBox(), forward, maximumDistance, swept ->
                ((ServerLevel) player.level()).hasChunksAt(BlockPos.containing(swept.minX, swept.minY, swept.minZ),
                        BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))
                        && ((ServerLevel) player.level()).getWorldBorder().isWithinBounds(swept)
                        && ((ServerLevel) player.level()).noCollision(player, swept));
    }

    static double chooseAirDistance(AABB body, Vec3 forward, double maximumDistance, Predicate<AABB> clearSweep) {
        return chooseSafeDistance(maximumDistance, SAMPLE_INCREMENT,
                distance -> clearSweep.test(body.expandTowards(forward.scale(distance)).deflate(1.0E-5)));
    }

    /** Initial ground speed (blocks per tick) that slides a grounded entity {@code distance}. */
    static double impulseForDistance(double distance) {
        return distance * GROUND_DRAG_COMPENSATION;
    }

    /**
     * Step length after target magnetism. With no target ahead the full step is used. A light
     * step drops to zero when the target is already in range, and every step stops
     * {@link #MAGNETISM_STANDOFF} short of the target's hitbox edge.
     */
    static double magnetizedDistance(
            double maximumDistance,
            OptionalDouble nearestTargetEdge,
            double range,
            boolean lightStep) {
        if (nearestTargetEdge.isEmpty()) {
            return maximumDistance;
        }
        double edge = nearestTargetEdge.getAsDouble();
        if (lightStep && edge <= range) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(maximumDistance, edge - MAGNETISM_STANDOFF));
    }

    static double chooseSafeDistance(
            double maximumDistance,
            double increment,
            DoublePredicate safe) {
        if (!(maximumDistance > 0.0) || !(increment > 0.0)) {
            throw new IllegalArgumentException("Distance and increment must be positive");
        }
        int samples = (int) Math.ceil(maximumDistance / increment - 1.0E-9);
        for (int index = samples; index >= 1; index--) {
            double candidate = Math.min(maximumDistance, index * increment);
            if (safe.test(candidate)) {
                return candidate;
            }
        }
        return 0.0;
    }

    /** Horizontal distance from the attacker's feet to the nearest legal target ahead, if any. */
    private static OptionalDouble nearestTargetEdge(
            LivingEntity player,
            Vec3 forward,
            double reach,
            CombatSession session) {
        Vec3 origin = player.position();
        double minimumCos = Math.cos(Math.toRadians(MAGNETISM_HALF_ANGLE_DEGREES));
        double nearest = Double.POSITIVE_INFINITY;
        for (Entity candidate : ((ServerLevel) player.level()).getEntities(
                player,
                player.getBoundingBox().inflate(reach, 1.0, reach),
                entity -> CombatHitResolver.legalTarget(player, entity, session))) {
            AABB box = candidate.getBoundingBox();
            Vec3 center = box.getCenter();
            double toX = center.x - origin.x;
            double toZ = center.z - origin.z;
            double length = Math.sqrt(toX * toX + toZ * toZ);
            if (length > 1.0E-6 && (toX * forward.x + toZ * forward.z) / length < minimumCos) {
                continue;
            }
            double edge = horizontalEdgeDistance(origin, box);
            if (edge <= reach && edge < nearest) {
                nearest = edge;
            }
        }
        return nearest == Double.POSITIVE_INFINITY ? OptionalDouble.empty() : OptionalDouble.of(nearest);
    }

    static double horizontalEdgeDistance(Vec3 origin, AABB box) {
        double dx = Math.max(Math.max(box.minX - origin.x, 0.0), origin.x - box.maxX);
        double dz = Math.max(Math.max(box.minZ - origin.z, 0.0), origin.z - box.maxZ);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean isSafeDestination(
            LivingEntity player,
            Vec3 displacement,
            double supportDepth) {
        AABB destination = player.getBoundingBox().move(displacement);
        AABB swept = player.getBoundingBox().expandTowards(displacement).deflate(1.0E-5);
        if (!player.level().hasChunksAt(BlockPos.containing(swept.minX, swept.minY, swept.minZ),
                BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))
                || !player.level().getWorldBorder().isWithinBounds(swept)
                || !player.level().noCollision(player, swept)
                || !player.level().noCollision(player, destination)) {
            return false;
        }
        if (player.level().noCollision(player, destination.move(0.0, -supportDepth, 0.0))) {
            return false;
        }
        Vec3 startCenter = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
        BlockHitResult clip = player.level().clip(new ClipContext(
                startCenter,
                startCenter.add(displacement),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player));
        return clip.getType() == HitResult.Type.MISS
                || clip.getLocation().distanceToSqr(startCenter) + 0.0025
                >= displacement.lengthSqr();
    }
}
