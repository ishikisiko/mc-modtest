package com.example.myvillage.combat.session;

import com.example.myvillage.combat.definition.CombatTestData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TechniqueGrowthSkillTest {
    @Test void masteryReceiptIsOnePerPaidCastAndCannotBeRearmedByReleaseOrWaveTicks() {
        var skill = CombatTestData.styles().skill(ResourceLocation.parse("myvillage:jianqi_jue")).orElseThrow();
        var cast = new CombatSkillSession(skill, skill.id(), 8, 100, 1);
        assertFalse(cast.claimMastery(), "charging cannot earn skill experience");
        cast.release(100, Vec3.ZERO, new Vec3(0, 0, 1), 8, 0);
        assertTrue(cast.claimMastery());
        for (int i = 0; i < 20; i++) { cast.advance(cast.nextPosition(), false); assertFalse(cast.claimMastery()); }
        assertFalse(cast.release(100, Vec3.ZERO, new Vec3(0, 0, 1), 8, 0));
        assertFalse(cast.claimMastery());
    }
    @Test void chargeCostAndDamageUseFrozenGrowthWhileLegacyCastsKeepTheirNumbers() {
        var skill = CombatTestData.styles().skill(ResourceLocation.parse("myvillage:jianqi_jue")).orElseThrow();
        var normal = new CombatSkillSession(skill, skill.id(), 8, 100, 1);
        var edge = new CombatSkillSession(skill, skill.id(), 8, 100, 1, 11500, 13500);
        var reserve = new CombatSkillSession(skill, skill.id(), 8, 100, 1, 8000, 12000);
        assertEquals(8, normal.costAt(100)); assertEquals(12, normal.costAt(120));
        assertEquals(10, edge.costAt(100)); assertEquals(14, edge.costAt(120));
        assertEquals(7, reserve.costAt(100)); assertEquals(10, reserve.costAt(120));
        assertEquals(1.35, edge.growthDamageMultiplier(), 1e-9);
        assertEquals(1.2, reserve.growthDamageMultiplier(), 1e-9);
    }
}
