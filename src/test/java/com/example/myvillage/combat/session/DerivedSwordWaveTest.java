package com.example.myvillage.combat.session;

import com.example.myvillage.combat.definition.CombatTestData;
import com.example.myvillage.combat.network.CombatSkillStatePayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DerivedSwordWaveTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("myvillage", path); }
    @Test void passiveFlightHasNoPaymentOrRefundAndKeepsItsOwnHitLedger() {
        var styles = CombatTestData.styles();
        var skill = styles.skill(id("sword_obsession_wave")).orElseThrow();
        var flight = new CombatSkillSession(skill, skill.id(), 0, 100, 42);
        assertTrue(flight.release(100, Vec3.ZERO, new Vec3(0, 0, 1)));
        assertEquals(0, flight.paidPower());
        assertEquals(0, flight.claimHitRefund());
        var melee = new CombatSession(CombatTestData.basicSword());
        melee.acceptIntent(100, CombatTestData.QINGFENG_SWORD, ResourceLocation.withDefaultNamespace("overworld"), 0);
        var derived = new CombatSession(CombatTestData.basicSword());
        derived.startExternal(skill.releaseMove(styles, 0), 100, CombatTestData.QINGFENG_SWORD,
                ResourceLocation.withDefaultNamespace("overworld"), 0);
        assertTrue(melee.markAttempted(10));
        assertTrue(derived.markAttempted(10));
        assertFalse(derived.markAttempted(10));
        derived.interrupt(CombatStopReason.COMPLETED);
        assertTrue(melee.hasActiveAction());
        assertFalse(melee.isExternal());
        assertTrue(melee.wasAttempted(10));
        int ticks = 0;
        while (!flight.waveStopped() && ticks++ < 100) flight.advance(flight.nextPosition(), false);
        assertTrue(flight.waveStopped());
        assertEquals(skill.range(), flight.position().z, 1e-8);
    }
    @Test void onlyLastNormalMoveIsFinisher() {
        var session = new CombatSession(CombatTestData.basicSword());
        long tick = 100;
        for (int index = 0; index < session.style().moves().size(); index++) {
            session.acceptIntent(tick, CombatTestData.QINGFENG_SWORD, ResourceLocation.withDefaultNamespace("overworld"), 0);
            assertEquals(index == session.style().moves().size() - 1, session.isFinisher());
            tick += session.currentMove().totalTicks();
            session.tick(tick++);
        }
        assertFalse(session.isFinisher());
    }
    @Test void passivePhasesCarryIndependentPositiveInstanceIdentity() {
        for (var phase : new CombatSkillStatePayload.Phase[]{CombatSkillStatePayload.Phase.DERIVED_RELEASED,
                CombatSkillStatePayload.Phase.DERIVED_WAVE, CombatSkillStatePayload.Phase.DERIVED_STOPPED}) {
            var payload = new CombatSkillStatePayload(1, id("sword_obsession_wave"), 42, 100,
                    phase, Vec3.ZERO, new Vec3(0, 0, 1), 0);
            assertEquals(42, payload.revision());
            assertThrows(IllegalArgumentException.class, () -> new CombatSkillStatePayload(1,
                    id("sword_obsession_wave"), 0, 100, phase, Vec3.ZERO, new Vec3(0, 0, 1), 0));
        }
    }
}
