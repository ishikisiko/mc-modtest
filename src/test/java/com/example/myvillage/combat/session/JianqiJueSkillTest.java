package com.example.myvillage.combat.session;

import com.example.myvillage.combat.definition.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JianqiJueSkillTest {
    private final CombatStyles styles = CombatTestData.styles();
    private final CombatSkillDefinition skill = styles.skill(id("jianqi_jue")).orElseThrow();
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("myvillage", path); }
    private CombatSkillSession charge() { return new CombatSkillSession(skill, skill.id(), 8, 100, 1); }

    @Test void horizontalReleaseKeepsItsExistingHitReaction() {
        var move = skill.releaseMove(styles, 0);
        assertEquals(id("basic_sword_02_horizontal_cut"), move.animation().animationId());
        assertEquals(styles.move(id("basic_sword_04_diagonal_cut")).orElseThrow().move().reaction(), move.reaction());
        assertEquals(0, move.feedback().cutRollDegrees());
    }

    @Test void preparationIsPlayedDuringChargeAndOnlyTheRemainingSlashRunsAfterRelease() {
        for (int elapsed = 0; elapsed <= skill.chargeTicks(); elapsed++) {
            int level = skill.chargeLevel(elapsed);
            int prepared = Math.min(elapsed, skill.chargePoseTicks());
            assertEquals(prepared, skill.releaseAnimationOffset(level));
            assertEquals(skill.releaseTicks(), prepared + skill.releaseDelay(level));
            assertEquals(prepared, skill.chargePoseTick(elapsed), 1e-8);
            var state = charge(); state.release(100 + elapsed, Vec3.ZERO, new Vec3(0, 0, 1));
            assertFalse(state.waveActive(100 + elapsed + skill.releaseDelay(level) - 1));
            assertTrue(state.waveActive(100 + elapsed + skill.releaseDelay(level)));
        }
        assertEquals(skill.chargePoseTicks(), skill.chargePoseTick(10000));
        assertEquals(0, skill.chargePoseTick(-1));
        assertEquals(0, skill.chargePoseTick(Double.NaN));
    }

    @Test void serverClockBoundsChargeAndRoundsCostOnce() {
        CombatSkillSession charge = charge();
        assertEquals(8, charge.costAt(100));
        assertEquals(10, charge.costAt(110));
        assertEquals(12, charge.costAt(120));
        assertEquals(12, charge.costAt(10000));
        assertFalse(charge.full(119)); assertTrue(charge.full(120));
        assertTrue(charge.release(110, Vec3.ZERO, new Vec3(0, .6, .8)));
        assertFalse(charge.release(120, new Vec3(10, 0, 0), new Vec3(1, 0, 0)));
        assertEquals(110, charge.releaseTick());
        assertEquals(10, charge.costAt(10000));
        assertEquals(new Vec3(0, .6, .8), charge.direction());
        assertEquals(Vec3.ZERO, charge.origin());
    }

    @Test void releaseRunsOneRealCombatSessionWithoutMeleeBufferOrStepAndResetsCombo() {
        CombatSession session = new CombatSession(CombatTestData.basicSword());
        AttackMoveDefinition move = skill.releaseMove(styles, 255);
        CombatSession.StartEvent start = session.startExternal(move, 120, CombatTestData.QINGFENG_SWORD,
                ResourceLocation.withDefaultNamespace("overworld"), 42);
        assertTrue(session.isExternal()); assertEquals(1, start.revision());
        assertEquals(skill.releaseDelay(255), move.activeStartTick());
        assertTrue(move.step().isEmpty());
        assertEquals(1.35, move.damageMultiplier(), 1.0E-8);
        assertEquals(CombatSession.IntentDecision.REJECTED_TIMING, session.acceptIntent(
                120 + move.bufferStartTick(), CombatTestData.QINGFENG_SWORD,
                ResourceLocation.withDefaultNamespace("overworld"), 0).decision());
        assertTrue(session.markAttempted(42)); assertFalse(session.markAttempted(42));
        assertEquals(2, session.remainingTargetCapacity(3));
        assertTrue(session.markDurabilityCharged()); assertFalse(session.markDurabilityCharged());
        assertTrue(session.tick(120 + move.totalTicks() - 1).stop().isEmpty());
        assertEquals(CombatStopReason.COMPLETED, session.tick(120 + move.totalTicks()).stop().orElseThrow().reason());
        assertFalse(session.hasActiveAction()); assertFalse(session.isExternal()); assertEquals(0, session.nextMoveIndex());
    }

    @Test void cancelledChargeRevisionNeverReappearsAndExternalInterruptClearsAction() {
        CombatSession session = new CombatSession(CombatTestData.basicSword());
        long chargeRevision = session.nextRevision();
        session.discardPendingAction();
        assertTrue(session.nextRevision() > chargeRevision);
        session.startExternal(skill.releaseMove(styles, 0), 100, CombatTestData.QINGFENG_SWORD,
                ResourceLocation.withDefaultNamespace("overworld"), 0);
        assertEquals(CombatStopReason.WEAPON_CHANGED,
                session.interrupt(CombatStopReason.WEAPON_CHANGED).orElseThrow().reason());
        assertFalse(session.hasActiveAction()); assertFalse(session.isExternal());
        assertEquals(0, session.nextMoveIndex());
    }

    @Test void pitchedWaveWaitsForWindupThenAdvancesToRangeOnce() {
        CombatSkillSession charge = charge();
        charge.release(120, Vec3.ZERO, new Vec3(0, .6, .8));
        assertFalse(charge.waveActive(120 + skill.releaseDelay(charge.chargeLevel()) - 1));
        for (int i = 0; i < skill.travelTicks(); i++) {
            assertTrue(charge.waveActive(120 + skill.releaseDelay(charge.chargeLevel()) + i));
            charge.advance(charge.nextPosition(), false);
        }
        assertEquals(skill.range(), charge.position().length(), 1.0E-8);
        assertTrue(charge.position().y > 0);
        assertTrue(charge.waveStopped()); assertFalse(charge.waveActive(1000));
    }

    @Test void wallContactStopsRemainingTravelPermanently() {
        CombatSkillSession charge = charge();
        charge.release(100, Vec3.ZERO, new Vec3(0, 0, 1));
        charge.advance(new Vec3(0, 0, .5), true);
        assertFalse(charge.waveActive(200));
        assertEquals(.5, charge.position().z);
    }
}
