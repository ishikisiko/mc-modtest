package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.CameraCues;
import com.example.myvillage.combat.definition.CombatStyleDefinition;
import com.example.myvillage.combat.definition.CombatTestData;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CombatCameraFxTest {
    @Test
    void traumaDecaysAtOnePointSixPerSecond() {
        assertEquals(0.8F, CombatCameraFx.decayedTrauma(0.8F, 0.0), 1.0E-6F);
        assertEquals(0.0F, CombatCameraFx.decayedTrauma(0.8F, 10.0), 1.0E-6F);
        assertEquals(0.8F - 0.08F * 5.0F, CombatCameraFx.decayedTrauma(0.8F, 5.0), 1.0E-6F);
        // A light hit settles in 3-4 ticks, the finisher in about 10.
        assertEquals(0.0F, CombatCameraFx.decayedTrauma(0.25F, 4.0), 1.0E-6F);
        assertTrue(CombatCameraFx.decayedTrauma(0.8F, 9.0) > 0.0F);
    }

    @Test
    void shakeIsTraumaSquared() {
        assertEquals(0.0625F, CombatCameraFx.shake(0.25F), 1.0E-6F);
        assertEquals(1.0F, CombatCameraFx.shake(2.0F), 1.0E-6F);
        assertEquals(0.0F, CombatCameraFx.shake(-1.0F), 1.0E-6F);
    }

    @Test
    void noiseIsSmoothAndBounded() {
        float previous = CombatCameraFx.noise(0, 0.0F);
        for (float time = 0.0F; time < 50.0F; time += 0.01F) {
            float value = CombatCameraFx.noise(0, time);
            assertTrue(value >= -1.0F && value <= 1.0F);
            assertTrue(Math.abs(value - previous) < 0.05F);
            previous = value;
        }
        assertTrue(CombatCameraFx.noise(0, 3.5F) != CombatCameraFx.noise(1, 3.5F));
    }

    @Test
    void kickEnvelopeRisesThenSettles() {
        assertEquals(0.0F, CombatCameraFx.envelope(-0.5F, 2.0F, 5.0F), 1.0E-6F);
        assertEquals(1.0F, CombatCameraFx.envelope(2.0F, 2.0F, 5.0F), 1.0E-6F);
        assertTrue(CombatCameraFx.envelope(1.0F, 2.0F, 5.0F) > 0.5F);
        assertTrue(CombatCameraFx.envelope(4.0F, 2.0F, 5.0F) < 1.0F);
        assertEquals(0.0F, CombatCameraFx.envelope(7.0F, 2.0F, 5.0F), 1.0E-6F);
    }

    @Test
    void qingfengCameraCuesArePinned() {
        CombatStyleDefinition style = CombatTestData.basicSword();
        float[] pitch = {0.0F, 0.0F, 0.6F, -0.8F, -1.2F};
        float[] roll = {0.0F, 0.5F, 0.0F, 0.4F, 0.0F};
        float[] fov = {0.0F, 0.0F, 0.0F, 0.0F, -3.0F};
        float[] lean = {0.0F, 0.3F, -0.3F, 0.3F, 0.0F};
        float[] surge = {0.0F, 0.0F, 0.0F, 0.0F, 2.0F};
        float[] trauma = {0.25F, 0.30F, 0.30F, 0.50F, 0.80F};
        assertEquals(5, style.moves().size());
        for (int index = 0; index < 5; index++) {
            AttackMoveDefinition move = style.move(index);
            assertEquals(new CameraCues(pitch[index], roll[index], fov[index], lean[index], surge[index]), move.camera());
            assertEquals(new CombatCameraFx.HitCue(trauma[index], pitch[index], roll[index], fov[index]),
                    CombatCameraFx.hitCue(Optional.of(move), true));
            assertEquals(new CombatCameraFx.HitCue(trauma[index] * 0.5F, 0.0F, 0.0F, 0.0F),
                    CombatCameraFx.hitCue(Optional.of(move), false));
        }
        assertEquals(0.25F, CombatCameraFx.UNKNOWN_MOVE_TRAUMA);
        assertEquals(new CombatCameraFx.HitCue(0.25F, 0.0F, 0.0F, 0.0F), CombatCameraFx.hitCue(Optional.empty(), true));
        assertEquals(new CombatCameraFx.HitCue(0.25F, 0.0F, 0.0F, 0.0F), CombatCameraFx.hitCue(Optional.empty(), false));
    }

    @Test
    void hitCuesAreReadFromTheMove() {
        CombatStyleDefinition style = CombatTestData.basicSword();
        for (AttackMoveDefinition move : style.moves()) {
            CombatCameraFx.HitCue first = CombatCameraFx.hitCue(Optional.of(move), true);
            assertEquals(move.feedback().cameraTrauma(), first.trauma(), 0.0F);
            assertEquals(move.camera().hitPitchKick(), first.pitchKick(), 0.0F);
            assertEquals(move.camera().hitRollKick(), first.rollKick(), 0.0F);
            assertEquals(move.camera().hitFovPunch(), first.fovPunch(), 0.0F);
            CombatCameraFx.HitCue later = CombatCameraFx.hitCue(Optional.of(move), false);
            assertEquals(move.feedback().cameraTrauma() * 0.5F, later.trauma(), 0.0F);
            assertEquals(new CombatCameraFx.HitCue(later.trauma(), 0.0F, 0.0F, 0.0F), later);
        }
        // The lunge: -1.2 pitch and a -3 FOV punch; an unknown move only shakes.
        CombatCameraFx.HitCue lunge = CombatCameraFx.hitCue(Optional.of(style.move(4)), true);
        assertEquals(new CombatCameraFx.HitCue(0.80F, -1.2F, 0.0F, -3.0F), lunge);
        assertEquals(new CombatCameraFx.HitCue(CombatCameraFx.UNKNOWN_MOVE_TRAUMA, 0.0F, 0.0F, 0.0F),
                CombatCameraFx.hitCue(Optional.empty(), true));

        // A move with its own cues gets exactly those.
        AttackMoveDefinition thrust = style.move(0);
        AttackMoveDefinition retuned = new AttackMoveDefinition(
                thrust.id(), thrust.displayKey(), thrust.kind(), thrust.totalTicks(), thrust.activeStartTick(),
                thrust.activeEndTick(), thrust.damageMultiplier(), thrust.maximumTargets(), thrust.range(),
                thrust.bufferStartTick(), thrust.chainTick(), thrust.reaction(), thrust.animation(),
                thrust.hitbox(), thrust.step(), thrust.feedback(), new CameraCues(0.7F, -0.2F, -1.0F, 0.4F, 1.5F));
        assertEquals(new CombatCameraFx.HitCue(0.25F, 0.7F, -0.2F, -1.0F),
                CombatCameraFx.hitCue(Optional.of(retuned), true));
    }

    @Test
    void accessibilityScalesStillApply() {
        assertEquals(1.0F, CombatCameraFx.angleScale(1.0F, false), 0.0F);
        assertEquals(0.5F, CombatCameraFx.angleScale(1.0F, true), 0.0F);
        assertEquals(0.25F, CombatCameraFx.angleScale(0.5F, true), 0.0F);
        assertEquals(0.0F, CombatCameraFx.angleScale(0.0F, false), 0.0F);
        assertEquals(-1.5F, CombatCameraFx.scaledFov(-3.0F, 0.5F), 0.0F);
        assertEquals(0.0F, CombatCameraFx.scaledFov(2.0F, 0.0F), 0.0F);
    }

    @Test
    void combatSlowDoesNotZoomTheFov() {
        // Walking speed 0.1 slowed to 25% by the swing commitment: vanilla term 0.625, restored to 1.0.
        assertEquals(1.6F, CombatCameraFx.fovCorrection(0.025, 0.25, 0.1F), 1.0E-5F);
        assertEquals(1.0F, CombatCameraFx.fovCorrection(0.1, 1.0, 0.1F), 1.0E-6F);
        assertEquals(1.0F, CombatCameraFx.fovCorrection(0.1, 0.0, 0.1F), 1.0E-6F);
    }
}
