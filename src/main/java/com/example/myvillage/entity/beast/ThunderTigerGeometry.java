package com.example.myvillage.entity.beast;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Pure collision geometry for the boss's translated, axis-aligned physical head. */
public final class ThunderTigerGeometry {
    private ThunderTigerGeometry() {}

    /**
     * Exact swept-box narrow phase: translate the box center against the solid box expanded by
     * its half extents. A union of endpoints alone falsely includes the corners of diagonal paths.
     * Boundary contact counts; the broad-phase caller can conservatively include extra blocks.
     */
    public static boolean sweptBoxIntersects(AABB atEnd, Vec3 displacement, AABB solid) {
        Vec3 end = atEnd.getCenter();
        Vec3 start = end.subtract(displacement);
        AABB expanded = solid.inflate(atEnd.getXsize() / 2, atEnd.getYsize() / 2, atEnd.getZsize() / 2);
        double[] origin = {start.x, start.y, start.z};
        double[] direction = {displacement.x, displacement.y, displacement.z};
        double[] minimum = {expanded.minX, expanded.minY, expanded.minZ};
        double[] maximum = {expanded.maxX, expanded.maxY, expanded.maxZ};
        double enter = 0, leave = 1;
        for (int axis = 0; axis < 3; axis++) {
            if (direction[axis] == 0) {
                if (origin[axis] < minimum[axis] || origin[axis] > maximum[axis]) return false;
                continue;
            }
            double first = (minimum[axis] - origin[axis]) / direction[axis];
            double last = (maximum[axis] - origin[axis]) / direction[axis];
            enter = Math.max(enter, Math.min(first, last));
            leave = Math.min(leave, Math.max(first, last));
            if (enter > leave) return false;
        }
        return true;
    }
}
