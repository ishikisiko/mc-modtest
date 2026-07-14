package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.definition.HitboxSample;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.OptionalDouble;

public final class CombatGeometry {
    private static final double EPSILON = 1.0E-9;

    private CombatGeometry() {
    }

    public static WorldSample transform(HitboxSample sample, Vec3 feetOrigin, float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        Vec3 right = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 start = localToWorld(
                feetOrigin, right, forward, sample.startX(), sample.startY(), sample.startZ());
        Vec3 end = localToWorld(
                feetOrigin, right, forward, sample.endX(), sample.endY(), sample.endZ());
        return new WorldSample(
                start, end, sample.horizontalRadius(), sample.verticalRadius());
    }

    public static AABB broadBounds(
            List<WorldSample> samples,
            double horizontalTolerance,
            double verticalTolerance) {
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("Cannot build broad bounds without samples");
        }
        AABB bounds = sampleBounds(samples.getFirst(), horizontalTolerance, verticalTolerance);
        for (int index = 1; index < samples.size(); index++) {
            bounds = bounds.minmax(sampleBounds(
                    samples.get(index), horizontalTolerance, verticalTolerance));
        }
        return bounds;
    }

    public static OptionalDouble firstContact(
            WorldSample sample,
            AABB target,
            double horizontalTolerance,
            double verticalTolerance) {
        double horizontalInflation = sample.horizontalRadius() + horizontalTolerance;
        double verticalInflation = sample.verticalRadius() + verticalTolerance;
        AABB expanded = target.inflate(horizontalInflation, verticalInflation, horizontalInflation);
        return segmentAabbContact(sample.start(), sample.end(), expanded);
    }

    static OptionalDouble segmentAabbContact(Vec3 start, Vec3 end, AABB box) {
        double[] interval = {0.0, 1.0};
        if (!clipAxis(start.x, end.x - start.x, box.minX, box.maxX, interval)
                || !clipAxis(start.y, end.y - start.y, box.minY, box.maxY, interval)
                || !clipAxis(start.z, end.z - start.z, box.minZ, box.maxZ, interval)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(interval[0]);
    }

    private static boolean clipAxis(
            double start,
            double delta,
            double minimum,
            double maximum,
            double[] interval) {
        if (Math.abs(delta) < EPSILON) {
            return start >= minimum && start <= maximum;
        }
        double first = (minimum - start) / delta;
        double second = (maximum - start) / delta;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        interval[0] = Math.max(interval[0], first);
        interval[1] = Math.min(interval[1], second);
        return interval[0] <= interval[1];
    }

    private static AABB sampleBounds(
            WorldSample sample,
            double horizontalTolerance,
            double verticalTolerance) {
        double horizontal = sample.horizontalRadius() + horizontalTolerance;
        double vertical = sample.verticalRadius() + verticalTolerance;
        return new AABB(sample.start(), sample.end())
                .inflate(horizontal, vertical, horizontal);
    }

    private static Vec3 localToWorld(
            Vec3 origin,
            Vec3 right,
            Vec3 forward,
            double x,
            double y,
            double z) {
        return origin.add(right.scale(x)).add(0.0, y, 0.0).add(forward.scale(z));
    }

    public record WorldSample(
            Vec3 start,
            Vec3 end,
            double horizontalRadius,
            double verticalRadius) {
    }
}
