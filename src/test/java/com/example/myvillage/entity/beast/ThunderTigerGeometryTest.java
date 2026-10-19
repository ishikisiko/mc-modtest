package com.example.myvillage.entity.beast;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class ThunderTigerGeometryTest {
    private static AABB headAt(Vec3 center) { return new AABB(center.x - .5, center.y - .5, center.z - .5, center.x + .5, center.y + .5, center.z + .5); }
    @Test void diagonalBroadPhaseCornerIsNotAnActualCollision() {
        Vec3 start = new Vec3(0, 1, 0), end = new Vec3(4, 1, 4);
        AABB obstacle = new AABB(0, 0, 3, 1, 2, 4);
        assertTrue(headAt(start).minmax(headAt(end)).intersects(obstacle), "the old endpoint union admits this false corner");
        assertFalse(ThunderTigerGeometry.sweptBoxIntersects(headAt(end), end.subtract(start), obstacle));
    }
    @Test void diagonalPhysicalHitAndReverseSweepBothCount() {
        Vec3 start = new Vec3(0, 1, 0), end = new Vec3(4, 1, 4);
        AABB obstacle = new AABB(2, 0, 2, 3, 2, 3);
        assertTrue(ThunderTigerGeometry.sweptBoxIntersects(headAt(end), end.subtract(start), obstacle));
        assertTrue(ThunderTigerGeometry.sweptBoxIntersects(headAt(start), start.subtract(end), obstacle));
    }
    @Test void highSpeedCrossingDoesNotTunnelAndVerticalNearMissStaysSafe() {
        Vec3 start = new Vec3(-20, 1, 0), end = new Vec3(20, 1, 0);
        AABB obstacle = new AABB(0, 0, -1, 1, 2, 1);
        assertTrue(ThunderTigerGeometry.sweptBoxIntersects(headAt(end), end.subtract(start), obstacle));
        assertFalse(ThunderTigerGeometry.sweptBoxIntersects(headAt(end.add(0, 3, 0)), end.subtract(start), obstacle));
    }
    @Test void stationaryOverlapAndExactContactArePhysicalButSeparatedBoxesAreNot() {
        AABB obstacle = new AABB(0, 0, 0, 1, 1, 1);
        assertTrue(ThunderTigerGeometry.sweptBoxIntersects(headAt(new Vec3(.5, .5, .5)), Vec3.ZERO, obstacle));
        assertTrue(ThunderTigerGeometry.sweptBoxIntersects(headAt(new Vec3(1.5, .5, .5)), Vec3.ZERO, obstacle));
        assertFalse(ThunderTigerGeometry.sweptBoxIntersects(headAt(new Vec3(1.51, .5, .5)), Vec3.ZERO, obstacle));
    }
}
