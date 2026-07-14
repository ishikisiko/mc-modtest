package com.example.myvillage.combat.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class CombatHitResolverTest {
    @Test
    void solidContactMustActuallyPrecedeTheTarget() {
        Vec3 origin = new Vec3(0.0, 1.6, 0.0);
        Vec3 target = new Vec3(0.0, 1.0, 3.0);

        assertTrue(CombatHitResolver.blockPrecedesTarget(
                origin, new Vec3(0.0, 1.3, 1.5), target));
        assertFalse(CombatHitResolver.blockPrecedesTarget(
                origin, new Vec3(0.0, 1.0, 3.0), target));
    }
}
