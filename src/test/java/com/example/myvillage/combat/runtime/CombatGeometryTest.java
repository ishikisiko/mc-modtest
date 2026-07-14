package com.example.myvillage.combat.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.example.myvillage.combat.definition.HitboxSample;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CombatGeometryTest {
    @Test
    void transformsLocalForwardByServerYaw() {
        HitboxSample local = new HitboxSample(
                3, 0.0, 1.0, 0.5, 0.0, 1.0, 3.0, 0.1, 0.2);

        CombatGeometry.WorldSample yawZero = CombatGeometry.transform(local, Vec3.ZERO, 0.0F);
        CombatGeometry.WorldSample yawNinety = CombatGeometry.transform(local, Vec3.ZERO, 90.0F);

        assertEquals(3.0, yawZero.end().z, 1.0E-9);
        assertEquals(-3.0, yawNinety.end().x, 1.0E-9);
        assertEquals(0.0, yawNinety.end().z, 1.0E-9);
    }

    @Test
    void narrowCapsuleRejectsBroadPhaseSideTarget() {
        CombatGeometry.WorldSample thrust = new CombatGeometry.WorldSample(
                new Vec3(0.0, 1.0, 0.5),
                new Vec3(0.0, 1.0, 3.0),
                0.1,
                0.2);
        AABB center = new AABB(-0.2, 0.7, 1.7, 0.2, 1.3, 2.1);
        AABB side = new AABB(0.8, 0.7, 1.7, 1.2, 1.3, 2.1);

        assertTrue(CombatGeometry.firstContact(thrust, center, 0.2, 0.1).isPresent());
        assertTrue(CombatGeometry.firstContact(thrust, side, 0.2, 0.1).isEmpty());
    }

    @Test
    void broadBoundsCoverBothSegmentEndpointsAndTolerance() {
        CombatGeometry.WorldSample sample = new CombatGeometry.WorldSample(
                new Vec3(-1.0, 0.5, 0.0),
                new Vec3(2.0, 1.5, 3.0),
                0.2,
                0.3);
        AABB bounds = CombatGeometry.broadBounds(java.util.List.of(sample), 0.2, 0.1);

        assertTrue(bounds.minX <= -1.4);
        assertTrue(bounds.maxX >= 2.4);
        assertTrue(bounds.minY <= 0.1);
        assertTrue(bounds.maxY >= 1.9);
    }

    @Test
    void everyMoveHasStableInsideAndOutsideNarrowPhaseBoundaries() {
        for (AttackMoveDefinition move : BasicSwordStyle.DEFINITION.moves()) {
            HitboxSample local = move.hitbox().samplesAt(move.activeStartTick()).getFirst();
            CombatGeometry.WorldSample sample = CombatGeometry.transform(local, Vec3.ZERO, 0.0F);
            Vec3 direction = sample.end().subtract(sample.start());
            Vec3 perpendicular = new Vec3(-direction.z, 0.0, direction.x).normalize();
            Vec3 midpoint = sample.start().lerp(sample.end(), 0.5);
            double inflatedHalfExtent = sample.horizontalRadius()
                    + move.hitbox().horizontalTolerance()
                    + 0.005;
            double boundary = inflatedHalfExtent
                    * (Math.abs(perpendicular.x) + Math.abs(perpendicular.z));
            AABB inside = pointBox(midpoint.add(perpendicular.scale(boundary - 0.02)));
            AABB outside = pointBox(midpoint.add(perpendicular.scale(boundary + 0.03)));

            assertTrue(CombatGeometry.firstContact(
                    sample,
                    inside,
                    move.hitbox().horizontalTolerance(),
                    move.hitbox().verticalTolerance()).isPresent(), move.id().toString());
            assertTrue(CombatGeometry.firstContact(
                    sample,
                    outside,
                    move.hitbox().horizontalTolerance(),
                    move.hitbox().verticalTolerance()).isEmpty(), move.id().toString());
        }
    }

    @Test
    void actualStepSweepHitsOnlyBetweenObservedEndpoints() {
        CombatGeometry.WorldSample sweep = new CombatGeometry.WorldSample(
                new Vec3(0.0, 0.9, 0.0), new Vec3(0.0, 0.9, 0.6), 0.24, 0.72);

        assertTrue(CombatGeometry.firstContact(
                sweep, pointBox(new Vec3(0.0, 0.9, 0.4)), 0.2, 0.12).isPresent());
        assertTrue(CombatGeometry.firstContact(
                sweep, pointBox(new Vec3(0.0, 0.9, 1.3)), 0.2, 0.12).isEmpty());
    }

    private static AABB pointBox(Vec3 center) {
        return new AABB(
                center.x - 0.005, center.y - 0.005, center.z - 0.005,
                center.x + 0.005, center.y + 0.005, center.z + 0.005);
    }
}
