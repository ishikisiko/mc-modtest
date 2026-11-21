package com.example.myvillage.combat.session;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static com.example.myvillage.combat.session.GoldenCoreGeometry.*;
import static org.junit.jupiter.api.Assertions.*;

class EarlyRealmGeometryTest {
    @Test void fanCoversTargetsToEitherSideWithoutHittingBehind() {
        var fan = new Fan(Vec3.ZERO, new Vec3(0, 0, 1), 5.25, 120);
        assertTrue(fan.intersects(box(3, 3)));
        assertTrue(fan.intersects(box(-3, 3)));
        assertFalse(fan.intersects(box(0, -2)));
        assertFalse(fan.intersects(box(0, 6)));
    }

    @Test void waveReflectsOnlyItsWallNormalComponent() {
        var incoming = new Vec3(.6, 0, .8);
        assertEquals(new Vec3(-.6, 0, .8), reflectedDirection(incoming, new Vec3(-1, 0, 0)));
    }

    @Test void screenInterceptionRequiresCrossingItsWidthAndHeight() {
        var normal = new Vec3(0, 0, 1);
        assertEquals(.5, screenCrossing(Vec3.ZERO, normal, 6, 3.75, new Vec3(2.9, 3.5, -4), new Vec3(2.9, 3.5, 4)));
        assertEquals(.5, screenCrossing(Vec3.ZERO, normal, 6, 3.75, new Vec3(2.9, 3.5, 4), new Vec3(2.9, 3.5, -4)));
        assertFalse(Double.isFinite(screenCrossing(Vec3.ZERO, normal, 6, 3.75, new Vec3(3.2, 2, -4), new Vec3(3.2, 2, 4))));
        assertFalse(Double.isFinite(screenCrossing(Vec3.ZERO, normal, 6, 3.75, new Vec3(2, 4, -4), new Vec3(2, 4, 4))));
        assertFalse(Double.isFinite(screenCrossing(Vec3.ZERO, normal, 6, 3.75, new Vec3(0, 2, 2), new Vec3(0, 2, 4))));
    }

    private static AABB box(double x, double z) {
        return new AABB(x - .3, 0, z - .3, x + .3, 1.8, z + .3);
    }
}
