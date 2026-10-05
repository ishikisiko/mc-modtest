package com.example.myvillage.entity.beast;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Yaw and hit-volume geometry in Minecraft's convention: yaw 0 faces south (+Z), yaw 90 faces
 * west (-X), so forward is {@code (-sin yaw, 0, cos yaw)}. Pure functions over plain values.
 */
public final class BeastGeometry {
    private BeastGeometry() {
    }

    /** Unit horizontal forward vector for a yaw in degrees. */
    public static Vec3 forward(float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    /** Yaw in degrees (wrapped to -180..180) that faces along {@code (dx, dz)}, as MoveControl computes it. */
    public static float yawToward(double dx, double dz) {
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        yaw %= 360.0F;
        if (yaw >= 180.0F) {
            yaw -= 360.0F;
        }
        if (yaw < -180.0F) {
            yaw += 360.0F;
        }
        return yaw;
    }

    public static double horizontalDistance(double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Signed distance of a point ahead of an origin along a yaw (horizontal only). */
    public static double forwardDistance(double originX, double originZ, float yawDegrees, double pointX, double pointZ) {
        Vec3 forward = forward(yawDegrees);
        return (pointX - originX) * forward.x + (pointZ - originZ) * forward.z;
    }

    /** World-axis bounds of the hit volume, for the broad-phase entity query. */
    public static AABB hitBounds(BeastMoveDefinition.HitBox hit, double feetX, double feetY, double feetZ, float yawDegrees) {
        Vec3 forward = forward(yawDegrees);
        double centreX = feetX + forward.x * hit.standoff();
        double centreZ = feetZ + forward.z * hit.standoff();
        double halfLength = (hit.far() - hit.near()) * 0.5;
        // The right vector is forward rotated a quarter turn: (-forward.z, forward.x).
        double extentX = halfLength * Math.abs(forward.x) + hit.halfWidth() * Math.abs(forward.z);
        double extentZ = halfLength * Math.abs(forward.z) + hit.halfWidth() * Math.abs(forward.x);
        return new AABB(
                centreX - extentX, feetY + hit.low(), centreZ - extentZ,
                centreX + extentX, feetY + hit.high(), centreZ + extentZ);
    }

    /**
     * True when the hit volume (a box turned about Y to the yaw) overlaps {@code target}. Exact:
     * separating-axis test on the two world axes and the box's two local axes, plus the height
     * interval. Touching counts as overlap.
     */
    public static boolean hitOverlaps(
            BeastMoveDefinition.HitBox hit, double feetX, double feetY, double feetZ, float yawDegrees, AABB target) {
        if (feetY + hit.high() < target.minY || feetY + hit.low() > target.maxY) {
            return false;
        }
        Vec3 forward = forward(yawDegrees);
        double rightX = -forward.z;
        double rightZ = forward.x;
        double halfLength = (hit.far() - hit.near()) * 0.5;
        double halfWidth = hit.halfWidth();
        double dx = (target.minX + target.maxX) * 0.5 - (feetX + forward.x * hit.standoff());
        double dz = (target.minZ + target.maxZ) * 0.5 - (feetZ + forward.z * hit.standoff());
        double targetHalfX = (target.maxX - target.minX) * 0.5;
        double targetHalfZ = (target.maxZ - target.minZ) * 0.5;
        if (Math.abs(dx) > targetHalfX + halfLength * Math.abs(forward.x) + halfWidth * Math.abs(rightX)) {
            return false;
        }
        if (Math.abs(dz) > targetHalfZ + halfLength * Math.abs(forward.z) + halfWidth * Math.abs(rightZ)) {
            return false;
        }
        if (Math.abs(dx * forward.x + dz * forward.z)
                > halfLength + targetHalfX * Math.abs(forward.x) + targetHalfZ * Math.abs(forward.z)) {
            return false;
        }
        return Math.abs(dx * rightX + dz * rightZ)
                <= halfWidth + targetHalfX * Math.abs(rightX) + targetHalfZ * Math.abs(rightZ);
    }
}
