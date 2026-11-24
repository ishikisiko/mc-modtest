package com.example.myvillage.entity.beast;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

final class LotusGuardCombatTest {
    private static final ResourceLocation ID = ResourceLocation.parse("yuanxian:lotus_guard");

    @Test
    void theSelectedFlankSharesTheLockedAttackAxis() {
        var definition = BeastDefinitions.bundled().require(ID);
        for (var move : definition.moves()) {
            int side = LotusGuardCombat.side(move.id());
            if (side == 0) continue;
            for (float yaw : new float[]{0, 45, 90, -173}) {
                float body = LotusGuardCombat.facing(yaw, side);
                float impact = LotusGuardCombat.impact(body, side);
                assertEquals(yaw, impact);
                var along = BeastGeometry.forward(impact);
                var player = new AABB(-.3, 0, -.3, .3, 1.8, .3).move(along.scale(5.4));
                assertTrue(BeastGeometry.hitOverlaps(move.hit(), 0, 0, 0, impact, player));
                assertFalse(BeastGeometry.hitOverlaps(move.hit(), 0, 0, 0, impact, player.move(along.scale(-10.8))));
                assertFalse(BeastGeometry.hitOverlaps(move.hit(), 0, 0, 0, impact, player.move(0, 2.6, 0)));
                var armor = LotusGuardRules.get().armor();
                assertTrue(LotusGuardCombat.isFlankHit(0, 0, body, side, along.x * 5.4, along.z * 5.4, armor));
                assertFalse(LotusGuardCombat.isFlankHit(0, 0, body, side, -along.x * 5.4, -along.z * 5.4, armor));
            }
        }
    }

    @Test
    void theWeakSideStaysClosedThroughImpactAndClosesWithTheArmor() {
        var rules = LotusGuardRules.get();
        var def = BeastDefinitions.bundled().require(ID);
        LotusGuardRules.validateMoves(def);
        var slam = def.move(LotusGuardCombat.LEFT).orElseThrow();
        int active = slam.activeTicks().last();
        int closing = slam.totalTicks() - rules.armor().returnTicks();
        assertFalse(LotusGuardCombat.exposed(slam, active, rules.armor()));
        assertTrue(LotusGuardCombat.exposed(slam, active + 1, rules.armor()));
        assertTrue(LotusGuardCombat.exposed(slam, closing - 1, rules.armor()));
        assertFalse(LotusGuardCombat.exposed(slam, closing, rules.armor()));
        assertFalse(LotusGuardCombat.exposed(def.move(0), active + 1, rules.armor()));
    }
}
