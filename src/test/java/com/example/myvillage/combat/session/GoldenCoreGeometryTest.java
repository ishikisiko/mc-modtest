package com.example.myvillage.combat.session;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoldenCoreGeometryTest {
    @Test void expandedStripHitsTheCollisionBoxInsteadOfRequiringCenterAim() {
        var strip = new GoldenCoreGeometry.Strip(Vec3.ZERO, new Vec3(0, 0, 14), 2);
        assertTrue(strip.intersects(new AABB(0.95, 0, 8, 1.55, 1.8, 8.6)));
        assertTrue(strip.intersects(new AABB(-0.25, 0, 13.95, 0.25, 1.8, 14.55)));
        assertFalse(strip.intersects(new AABB(1.01, 0, 8, 1.61, 1.8, 8.6)));
        assertFalse(strip.intersects(new AABB(-0.25, 0, 14.01, 0.25, 1.8, 14.61)));
        assertFalse(strip.intersects(new AABB(-0.25, 3, 8, 0.25, 4.8, 8.6)));
    }

    @Test void stoneStampUsesTheNearestEdgeAndSeparatesInnerFromOuter() {
        var target = new AABB(2.45, 0, -0.3, 3.05, 1.8, 0.3);
        assertTrue(GoldenCoreGeometry.withinRadius(Vec3.ZERO, target, 2.5));
        var outer = new AABB(5.9, 0, -0.3, 6.5, 1.8, 0.3);
        assertFalse(GoldenCoreGeometry.withinRadius(Vec3.ZERO, outer, 2.5));
        assertTrue(GoldenCoreGeometry.withinRadius(Vec3.ZERO, outer, 6.125));
    }
}
