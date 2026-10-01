package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.CombatTestData;
import com.example.myvillage.combat.definition.HitboxSample;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CombatWorldTrailsTest {
    @Test
    void arcTrailKeepsItsRadiusBetweenSamples() {
        List<HitboxSample> samples = CombatTestData.basicSword().move(1).hitbox().samples();
        double radius = Math.hypot(samples.getFirst().endX(), samples.getFirst().endZ());
        for (float tick = samples.getFirst().actionTick(); tick <= samples.getLast().actionTick(); tick += 0.25F) {
            HitboxSample blade = CombatWorldTrails.blade(samples, tick);
            assertEquals(radius, Math.hypot(blade.endX(), blade.endZ()), 1.0E-6);
        }
    }

    @Test
    void trailClampsToTheAuthoredSamples() {
        List<HitboxSample> samples = CombatTestData.basicSword().move(3).hitbox().samples();
        assertSame(samples.getFirst(), CombatWorldTrails.blade(samples, 0.0F));
        assertSame(samples.getLast(), CombatWorldTrails.blade(samples, 30.0F));
        assertSame(samples.get(1), CombatWorldTrails.blade(samples, samples.get(1).actionTick()));
    }

    @Test
    void drawnBladeHugsTheSwordInsteadOfTheHitboxReach() {
        for (int moveIndex = 0; moveIndex < CombatTestData.basicSword().moves().size(); moveIndex++) {
            for (HitboxSample sample : CombatTestData.basicSword().move(moveIndex).hitbox().samples()) {
                HitboxSample drawn = CombatWorldTrails.drawnBlade(sample, 1.0);
                double tip = Math.sqrt(drawn.endX() * drawn.endX()
                        + Math.pow(drawn.endY() - CombatWorldTrails.PIVOT_HEIGHT, 2)
                        + drawn.endZ() * drawn.endZ());
                double base = Math.sqrt(drawn.startX() * drawn.startX()
                        + Math.pow(drawn.startY() - CombatWorldTrails.PIVOT_HEIGHT, 2)
                        + drawn.startZ() * drawn.startZ());
                assertEquals(CombatWorldTrails.MAXIMUM_TIP_RADIUS, tip, 1.0E-6);
                assertEquals(CombatWorldTrails.DRAWN_BLADE_LENGTH, tip - base, 1.0E-6);
                // Same direction as the server sample, so the trail still shows where the hit lands.
                double sampleLength = Math.sqrt(sample.endX() * sample.endX()
                        + Math.pow(sample.endY() - CombatWorldTrails.PIVOT_HEIGHT, 2)
                        + sample.endZ() * sample.endZ());
                assertEquals(sample.endX() / sampleLength, drawn.endX() / tip, 1.0E-6);
                assertEquals(sample.endZ() / sampleLength, drawn.endZ() / tip, 1.0E-6);
            }
        }
    }

    @Test
    void worldBladeFollowsTheMoveSamplesAndTheServerFacing() {
        List<HitboxSample> samples = CombatTestData.basicSword().move(1).hitbox().samples();
        float tick = samples.getFirst().actionTick();
        net.minecraft.world.phys.Vec3 origin = new net.minecraft.world.phys.Vec3(10.0, 64.0, -5.0);
        HitboxSample drawn = CombatWorldTrails.drawnBlade(samples.getFirst(), 1.0, 1.0);
        for (float yaw : new float[] {0.0F, 90.0F, -137.5F}) {
            com.example.myvillage.combat.runtime.CombatGeometry.WorldSample expected =
                    com.example.myvillage.combat.runtime.CombatGeometry.transform(drawn, origin, yaw);
            com.example.myvillage.combat.runtime.CombatGeometry.WorldSample world =
                    CombatWorldTrails.worldBlade(samples, tick, 1.0, 1.0, origin, yaw);
            assertEquals(expected.start().x, world.start().x, 1.0E-9);
            assertEquals(expected.start().z, world.start().z, 1.0E-9);
            assertEquals(expected.end().x, world.end().x, 1.0E-9);
            assertEquals(expected.end().y, world.end().y, 1.0E-9);
            assertEquals(expected.end().z, world.end().z, 1.0E-9);
        }
        // Turning the server facing by 90 degrees turns the drawn blade with it.
        var north = CombatWorldTrails.worldBlade(samples, tick, 1.0, 1.0, origin, 0.0F);
        var east = CombatWorldTrails.worldBlade(samples, tick, 1.0, 1.0, origin, 90.0F);
        double northAngle = Math.atan2(north.end().x - origin.x, north.end().z - origin.z);
        double eastAngle = Math.atan2(east.end().x - origin.x, east.end().z - origin.z);
        assertEquals(Math.PI / 2.0, Math.abs(Math.IEEEremainder(northAngle - eastAngle, 2.0 * Math.PI)), 1.0E-9);
    }

    @Test
    void drawnBladeMatchesTheSwordModelInThirdPerson() throws IOException {
        SwordGeometry sword = FirstPersonSwingTest.geometry();
        // The 3D model's third-person display scale is 0.8: the ribbon spans the drawn blade.
        double length = CombatWorldTrails.drawnBladeLength(Optional.of(sword), 0.8F);
        assertEquals(sword.bladeLengthPixels() / 16.0 * 0.8, length, 1.0E-6);
        assertTrue(length > 0.6 && length < 1.0, "blade " + length);
        assertEquals(CombatWorldTrails.DRAWN_BLADE_LENGTH,
                CombatWorldTrails.drawnBladeLength(Optional.empty(), 0.8F), 1.0E-9);
        assertEquals(CombatWorldTrails.DRAWN_BLADE_LENGTH,
                CombatWorldTrails.drawnBladeLength(Optional.of(sword), 0.0F), 1.0E-9);
        assertEquals(CombatWorldTrails.MAXIMUM_BLADE_LENGTH,
                CombatWorldTrails.drawnBladeLength(Optional.of(sword), 4.0F), 1.0E-9);
        HitboxSample sample = CombatTestData.basicSword().move(1).hitbox().samples().getFirst();
        HitboxSample drawn = CombatWorldTrails.drawnBlade(sample, 1.0, length);
        double tip = Math.sqrt(drawn.endX() * drawn.endX()
                + Math.pow(drawn.endY() - CombatWorldTrails.PIVOT_HEIGHT, 2) + drawn.endZ() * drawn.endZ());
        double base = Math.sqrt(drawn.startX() * drawn.startX()
                + Math.pow(drawn.startY() - CombatWorldTrails.PIVOT_HEIGHT, 2) + drawn.startZ() * drawn.startZ());
        assertEquals(length, tip - base, 1.0E-6);
    }
}
