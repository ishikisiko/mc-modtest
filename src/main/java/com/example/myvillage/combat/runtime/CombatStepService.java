package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.definition.StepDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.DoublePredicate;

public final class CombatStepService {
    private static final double SAMPLE_INCREMENT = 0.1;

    private CombatStepService() {
    }

    public static Optional<CombatHitResolver.StepSweep> tryStep(
            ServerPlayer player,
            StepDefinition step,
            float facingYaw) {
        if (!player.onGround()) {
            return Optional.empty();
        }
        Vec3 start = player.position();
        double yaw = Math.toRadians(facingYaw);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        double safeDistance = chooseSafeDistance(
                step.maximumDistance(),
                SAMPLE_INCREMENT,
                distance -> isSafeDestination(player, forward.scale(distance), step.supportDepth()));
        if (safeDistance <= 0.0) {
            return Optional.empty();
        }

        player.move(MoverType.PLAYER, forward.scale(safeDistance));
        Vec3 end = player.position();
        if (end.distanceToSqr(start) < 1.0E-6) {
            return Optional.empty();
        }
        return Optional.of(new CombatHitResolver.StepSweep(start, end));
    }

    static double chooseSafeDistance(
            double maximumDistance,
            double increment,
            DoublePredicate safe) {
        if (!(maximumDistance > 0.0) || !(increment > 0.0)) {
            throw new IllegalArgumentException("Distance and increment must be positive");
        }
        int samples = (int) Math.ceil(maximumDistance / increment);
        for (int index = samples; index >= 1; index--) {
            double candidate = Math.min(maximumDistance, index * increment);
            if (safe.test(candidate)) {
                return candidate;
            }
        }
        return 0.0;
    }

    private static boolean isSafeDestination(
            ServerPlayer player,
            Vec3 displacement,
            double supportDepth) {
        AABB destination = player.getBoundingBox().move(displacement);
        if (!player.level().noCollision(player, destination)) {
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
