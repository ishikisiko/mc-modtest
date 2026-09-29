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

    @Test
    void drawnBladeHugsTheSwordInsteadOfTheHitboxReach() {
        for (int moveIndex = 0; moveIndex < BasicSwordStyle.DEFINITION.moves().size(); moveIndex++) {
            for (HitboxSample sample : BasicSwordStyle.DEFINITION.move(moveIndex).hitbox().samples()) {
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
}
