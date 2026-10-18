package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.HitboxDefinition;
import com.example.myvillage.combat.session.CombatSession;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class CombatHitResolver {
    /** Only a lunge-length step also sweeps the attacker's body path as a hit capsule. */
    static final double LUNGE_BODY_SWEEP_DISTANCE = 0.6;

    private CombatHitResolver() {
    }

    /** AI reach decision uses the same samples and vertical tolerances as an actual strike. */
    public static boolean canReach(LivingEntity attacker, Entity target, AttackMoveDefinition move, float yaw) {
        if (target.isMultipartEntity()) {
            for (var part : target.getParts()) {
                if (canReach(attacker, part, move, yaw)) return true;
            }
            return false;
        }
        for (var sample : move.hitbox().samples()) {
            var world = CombatGeometry.transform(sample, attacker.position(), yaw);
            if (CombatGeometry.firstContact(world, target.getBoundingBox(), move.hitbox().horizontalTolerance(),
                    move.hitbox().verticalTolerance()).isPresent()) return !blockedByWall(attacker, target);
        }
        return false;
    }

    public static Resolution resolve(
            LivingEntity attacker,
            CombatSession session,
            AttackMoveDefinition move,
            int actionTick,
            Optional<StepSweep> stepSweep) {
        HitboxDefinition hitbox = move.hitbox();
        // After a step the hitbox follows the server-planned origin, never the client's echo.
        Vec3 origin = stepSweep
                .map(sweep -> sweep.originAt(actionTick))
                .orElseGet(attacker::position);
        List<CombatGeometry.WorldSample> worldSamples = new ArrayList<>(
                hitbox.samplesAt(actionTick).stream()
                        .map(sample -> CombatGeometry.transform(
                                sample, origin, session.facingYaw()))
                        .toList());
        if (move.step().isPresent() && stepSweep.isPresent()
                && stepSweep.orElseThrow().distance() >= LUNGE_BODY_SWEEP_DISTANCE) {
            StepSweep sweep = stepSweep.orElseThrow();
            worldSamples.add(new CombatGeometry.WorldSample(
                    sweep.start().add(0.0, 0.9, 0.0),
                    sweep.end().add(0.0, 0.9, 0.0),
                    0.24,
                    0.72));
        }
        if (worldSamples.isEmpty()) {
            return new Resolution(List.of(), List.of());
        }

        int remainingTargets = session.remainingTargetCapacity(move.maximumTargets());
        if (remainingTargets == 0) {
            return new Resolution(List.of(), List.copyOf(worldSamples));
        }

        AABB broadBounds = CombatGeometry.broadBounds(
                worldSamples, hitbox.horizontalTolerance(), hitbox.verticalTolerance());
        ServerLevel level = ((ServerLevel) attacker.level());
        List<TargetContact> contacts = new ArrayList<>();
        for (Entity candidate : level.getEntities(attacker, broadBounds, entity ->
                legalTarget(attacker, entity, session))) {
            TargetContact contact = firstContact(
                    attacker, candidate, worldSamples, hitbox.horizontalTolerance(), hitbox.verticalTolerance());
            if (contact != null && !blockedByWall(attacker, candidate)) {
                contacts.add(contact);
            }
        }
        contacts.sort(Comparator
                .comparingDouble(TargetContact::distanceSquared)
                .thenComparingInt(contact -> contact.target().getId()));
        contacts = CombatTargets.firstDistinct(contacts, contact -> CombatTargets.hitId(contact.target()), remainingTargets);
        return new Resolution(List.copyOf(contacts), List.copyOf(worldSamples));
    }

    public static boolean legalTarget(
            LivingEntity attacker,
            Entity target,
            CombatSession session) {
        Entity owner = CombatTargets.owner(target);
        if (!CombatTargets.physicalTarget(target)
                || owner == attacker
                || !owner.isAlive()
                || owner.isRemoved()
                || target == attacker
                || target.level() != attacker.level()
                || target.isRemoved()
                || !target.isAlive()
                || target.isSpectator()
                || !target.isAttackable()
                || target.skipAttackInteraction(attacker)
                || session.wasAttempted(CombatTargets.hitId(target))
                || attacker.isAlliedTo(target)) {
            return false;
        }
        if (!CombatActors.mayHarm(attacker, owner)) {
            return false;
        }
        return !target.isInvulnerableTo(CombatActors.source(attacker))
                && !owner.isInvulnerableTo(CombatActors.source(attacker));
    }

    private static TargetContact firstContact(
            LivingEntity attacker,
            Entity target,
            List<CombatGeometry.WorldSample> samples,
            double horizontalTolerance,
            double verticalTolerance) {
        double closestDistance = Double.POSITIVE_INFINITY;
        Vec3 closestPoint = null;
        for (CombatGeometry.WorldSample sample : samples) {
            OptionalDouble contact = CombatGeometry.firstContact(
                    sample, target.getBoundingBox(), horizontalTolerance, verticalTolerance);
            if (contact.isEmpty()) {
                continue;
            }
            Vec3 point = sample.start().lerp(sample.end(), contact.getAsDouble());
            double distance = point.distanceToSqr(attacker.position());
            if (distance < closestDistance) {
                closestDistance = distance;
                closestPoint = point;
            }
        }
        return closestPoint == null ? null : new TargetContact(target, closestDistance, closestPoint);
    }

    private static boolean blockedByWall(LivingEntity attacker, Entity target) {
        Vec3 origin = attacker.getEyePosition();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        BlockHitResult hit = attacker.level().clip(new ClipContext(
                origin,
                targetCenter,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                attacker));
        return hit.getType() == HitResult.Type.BLOCK
                && blockPrecedesTarget(origin, hit.getLocation(), targetCenter);
    }

    static boolean blockPrecedesTarget(Vec3 origin, Vec3 blockHit, Vec3 targetCenter) {
        return blockHit.distanceToSqr(origin) + 0.01 < targetCenter.distanceToSqr(origin);
    }

    /**
     * A server-planned step: the attacker's position when the step was decided, the planned
     * destination, and the action tick of the step impulse.
     */
    public record StepSweep(Vec3 start, Vec3 end, int stepTick) {
        /** Planned feet origin at an action tick: the start until the step tick, the end after. */
        public Vec3 originAt(int actionTick) {
            double progress = Math.max(0.0, Math.min(1.0, actionTick - stepTick));
            return start.lerp(end, progress);
        }

        public double distance() {
            return start.distanceTo(end);
        }
    }

    public record TargetContact(Entity target, double distanceSquared, Vec3 contactPoint) {
    }

    public record Resolution(
            List<TargetContact> contacts,
            List<CombatGeometry.WorldSample> samples) {
    }
}
