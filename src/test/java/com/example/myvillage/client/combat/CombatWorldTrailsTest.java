package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.example.myvillage.combat.definition.HitboxSample;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CombatWorldTrailsTest {
    @Test
    void arcTrailKeepsItsRadiusBetweenSamples() {
        List<HitboxSample> samples = BasicSwordStyle.DEFINITION.move(1).hitbox().samples();
        double radius = Math.hypot(samples.getFirst().endX(), samples.getFirst().endZ());
        for (float tick = samples.getFirst().actionTick(); tick <= samples.getLast().actionTick(); tick += 0.25F) {
            HitboxSample blade = CombatWorldTrails.blade(samples, tick);
            assertEquals(radius, Math.hypot(blade.endX(), blade.endZ()), 1.0E-6);
        }
    }

    @Test
    void trailClampsToTheAuthoredSamples() {
        List<HitboxSample> samples = BasicSwordStyle.DEFINITION.move(3).hitbox().samples();
        assertSame(samples.getFirst(), CombatWorldTrails.blade(samples, 0.0F));
        assertSame(samples.getLast(), CombatWorldTrails.blade(samples, 30.0F));
        assertSame(samples.get(1), CombatWorldTrails.blade(samples, samples.get(1).actionTick()));
    }
}
