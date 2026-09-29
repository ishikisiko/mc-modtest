package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.HitboxDefinition;
import com.example.myvillage.combat.session.CombatSession;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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

    public static Resolution resolve(
            ServerPlayer attacker,
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
        ServerLevel level = attacker.serverLevel();
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
        if (contacts.size() > remainingTargets) {
            contacts = new ArrayList<>(contacts.subList(0, remainingTargets));
        }
        return new Resolution(List.copyOf(contacts), List.copyOf(worldSamples));
    }

    static boolean legalTarget(
            ServerPlayer attacker,
            Entity target,
            CombatSession session) {
        if (target == attacker
                || target.level() != attacker.level()
                || target.isRemoved()
                || !target.isAlive()
                || target.isSpectator()
                || !target.isAttackable()
                || target.skipAttackInteraction(attacker)
                || session.wasAttempted(target.getId())
                || attacker.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof Player player && !attacker.canHarmPlayer(player)) {
            return false;
        }
        return !target.isInvulnerableTo(attacker.damageSources().playerAttack(attacker));
    }

    private static TargetContact firstContact(
            ServerPlayer attacker,
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

    private static boolean blockedByWall(ServerPlayer attacker, Entity target) {
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
