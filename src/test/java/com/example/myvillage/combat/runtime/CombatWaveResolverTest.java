package com.example.myvillage.combat.runtime;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatWaveResolverTest {
    @Test void wideEdgeTouchesWallEvenWhenCenterRayMisses() {
        Vec3 start = new Vec3(0, 1.2, 0), end = new Vec3(0, 1.2, 8);
        AABB edgeWall = new AABB(.8, 0, 3, 1.8, 3, 4);
        assertTrue(CombatWaveResolver.contact(start, end, Vec3.ZERO, edgeWall).isEmpty());
        double fraction = CombatWaveResolver.contact(start, end, new Vec3(1.1, .25, 0), edgeWall).orElseThrow();
        assertEquals(3.0 / 8, fraction, 1.0E-8);
    }
    @Test void continuousSweepCannotTunnelThroughThinWall() {
        AABB wall = new AABB(-3, 0, 1.01, 3, 3, 1.02);
        double hit = CombatWaveResolver.contact(new Vec3(0, 1, 0), new Vec3(0, 1, 2),
                new Vec3(1, .2, 0), wall).orElseThrow();
        assertEquals(.505, hit, 1.0E-8);
    }
    @Test void nearTargetPrecedesWallAndFarTargetDoesNot() {
        Vec3 start = new Vec3(0, 1, 0), end = new Vec3(0, 1, 8), size = new Vec3(1, .2, 0);
        double wall = CombatWaveResolver.contact(start, end, size, new AABB(-2, 0, 3, 2, 3, 4)).orElseThrow();
        double near = CombatWaveResolver.contact(start, end, size, new AABB(-.3, 0, 2, .3, 2, 2.6)).orElseThrow();
        double far = CombatWaveResolver.contact(start, end, size, new AABB(-.3, 0, 5, .3, 2, 5.6)).orElseThrow();
        assertTrue(near < wall); assertFalse(far < wall);
    }
    @Test void pitchRotatesHeightAndExactVerticalAimStaysFinite() {
        Vec3 pitched = CombatWaveResolver.extents(new Vec3(0, .6, .8), 0, 1.1, .25);
        assertEquals(1.1, pitched.x, 1.0E-8); assertEquals(.2, pitched.y, 1.0E-8);
        assertEquals(.15, pitched.z, 1.0E-8);
        assertEquals(new Vec3(1.1, 0, .25), CombatWaveResolver.extents(new Vec3(0, 1, 0), 0, 1.1, .25));
    }
    @Test void originInsideExpandedWallStopsImmediately() {
        assertEquals(0, CombatWaveResolver.contact(Vec3.ZERO, new Vec3(0, 0, 8), new Vec3(1, .25, 0),
                new AABB(.5, -.2, -.1, 1.5, .2, .1)).orElseThrow());
    }
}
