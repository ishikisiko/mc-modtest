package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.example.myvillage.combat.definition.CameraCues;
import com.example.myvillage.combat.definition.CombatStyleDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.definition.MoveFeedback;
import com.example.myvillage.combat.definition.MoveKind;
import com.example.myvillage.combat.definition.WeaponDefinition;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * D5: the bundled {@code basic_sword.json}, loaded from the classpath like the game loads it,
 * reproduces the removed Java constants value for value: {@code BasicSwordStyle.DEFINITION} (every
 * hitbox sample included), {@code BasicSwordStyle.FEEDBACK} with the sound choices the runtime made
 * from it, and the {@code CombatCameraFx} per-move arrays.
 */
@SuppressWarnings("removal")
final class LegacyBasicSwordEquivalenceTest {
    @Test
    void loadedStyleEqualsTheLegacyDefinition() {
        CombatStyles styles = CombatStyles.bundled();
        BasicSwordStyle.LegacyStyle legacy = BasicSwordStyle.DEFINITION;
        CombatStyleDefinition loaded = styles.style(legacy.id()).orElseThrow();

        assertEquals(legacy.comboTimeoutTicks(), loaded.comboTimeoutTicks());
        assertEquals(BasicSwordStyle.COMBO_TIMEOUT_TICKS, loaded.comboTimeoutTicks());
        assertEquals(legacy.minimumIntentIntervalTicks(), loaded.minimumIntentIntervalTicks());
        assertEquals(BasicSwordStyle.MINIMUM_INTENT_INTERVAL_TICKS, loaded.minimumIntentIntervalTicks());
        assertEquals(BasicSwordStyle.READY_IDLE_ANIMATION, loaded.readyIdleAnimation());
        // The former CombatAnimationController.SMOKE_ANIMATION, played on entering cultivation mode.
        assertEquals(ResourceLocation.fromNamespaceAndPath("myvillage", "sword_mode_enter"), loaded.modeEnterAnimation());
        assertEquals(
                legacy.supportedItems(),
                styles.weapons().stream()
                        .filter(weapon -> weapon.style().equals(legacy.id()))
                        .map(WeaponDefinition::item)
                        .collect(Collectors.toSet()));
        assertEquals(Set.of(BasicSwordStyle.QINGFENG_SWORD_ID), legacy.supportedItems());

        assertEquals(legacy.moves().size(), loaded.moves().size());
        for (int index = 0; index < legacy.moves().size(); index++) {
            BasicSwordStyle.LegacyMove expected = legacy.move(index);
            AttackMoveDefinition actual = loaded.move(index);
            String move = expected.id().toString();
            assertEquals(expected.id(), actual.id(), move);
            assertEquals(expected.displayKey(), actual.displayKey(), move);
            assertEquals(expected.totalTicks(), actual.totalTicks(), move);
            assertEquals(expected.activeStartTick(), actual.activeStartTick(), move);
            assertEquals(expected.activeEndTick(), actual.activeEndTick(), move);
            assertEquals(expected.damageMultiplier(), actual.damageMultiplier(), move);
            assertEquals(expected.maximumTargets(), actual.maximumTargets(), move);
            assertEquals(expected.range(), actual.range(), move);
            assertEquals(expected.bufferStartTick(), actual.bufferStartTick(), move);
            assertEquals(expected.chainTick(), actual.chainTick(), move);
            assertEquals(expected.reaction(), actual.reaction(), move);
            assertEquals(expected.animation(), actual.animation(), move);
            assertEquals(expected.step(), actual.step(), move);
            // Shape family, both tolerances, and every sample's nine doubles (record equality).
            assertEquals(expected.hitbox().shapeFamily(), actual.hitbox().shapeFamily(), move);
            assertEquals(expected.hitbox().horizontalTolerance(), actual.hitbox().horizontalTolerance(), move);
            assertEquals(expected.hitbox().verticalTolerance(), actual.hitbox().verticalTolerance(), move);
            assertEquals(expected.hitbox().samples().size(), actual.hitbox().samples().size(), move);
            for (int sample = 0; sample < expected.hitbox().samples().size(); sample++) {
                assertEquals(expected.hitbox().samples().get(sample), actual.hitbox().samples().get(sample),
                        move + " sample " + sample);
            }
            assertEquals(expected.hitbox(), actual.hitbox(), move);
        }
    }

    @Test
    void loadedFeedbackEqualsTheLegacyFeedbackAndSoundChoices() {
        CombatStyleDefinition loaded = CombatStyles.bundled().style(BasicSwordStyle.DEFINITION.id()).orElseThrow();
        assertEquals(BasicSwordStyle.FEEDBACK.size(), loaded.moves().size());
        for (int index = 0; index < BasicSwordStyle.FEEDBACK.size(); index++) {
            BasicSwordStyle.LegacyFeedback expected = BasicSwordStyle.feedback(index);
            AttackMoveDefinition move = loaded.move(index);
            MoveFeedback actual = move.feedback();
            String name = move.id().toString();
            boolean thrust = expected.swingSound() == BasicSwordStyle.SwingSound.THRUST;
            // CombatFeedbackService / the animator: THRUST -> SWORD_THRUST, CUT -> SWORD_CUT.
            assertEquals(sound(thrust ? "combat.sword.thrust" : "combat.sword.cut"), actual.swingSound(), name);
            // CombatFeedbackService: heavy -> SWORD_HIT_HEAVY plus the IMPACT_HEAVY layer.
            assertEquals(sound(expected.heavyHit() ? "combat.sword.hit_heavy" : "combat.sword.hit"),
                    actual.hitSound(), name);
            assertEquals(expected.heavyHit() ? Optional.of(sound("combat.sword.impact_heavy")) : Optional.empty(),
                    actual.heavyLayerSound(), name);
            // The trail form used to follow the swing sound: thrust -> streak, cut -> band.
            assertEquals(thrust ? MoveKind.THRUST : MoveKind.CUT, move.kind(), name);
            assertEquals(expected.swingPitch(), actual.swingPitch(), name);
            assertEquals(expected.heavyHit(), actual.heavyHit(), name);
            assertEquals(expected.hitStopTicks(), actual.hitStopTicks(), name);
            assertEquals(expected.cameraTrauma(), actual.cameraTrauma(), name);
            assertEquals(expected.cutRollDegrees(), actual.cutRollDegrees(), name);
        }
    }

    @Test
    void loadedCameraCuesEqualTheLegacyArrays() {
        CombatStyleDefinition loaded = CombatStyles.bundled().style(BasicSwordStyle.DEFINITION.id()).orElseThrow();
        for (int index = 0; index < loaded.moves().size(); index++) {
            AttackMoveDefinition move = loaded.move(index);
            CameraCues camera = move.camera();
            String name = move.id().toString();
            assertEquals(CombatCameraFx.LEGACY_HIT_PITCH_KICK[index], camera.hitPitchKick(), name);
            assertEquals(CombatCameraFx.LEGACY_HIT_ROLL_KICK[index], camera.hitRollKick(), name);
            assertEquals(CombatCameraFx.LEGACY_HIT_FOV_PUNCH[index], camera.hitFovPunch(), name);
            // The lean kick was SWING_LEAN_DEGREES times the per-move sign.
            assertEquals(CombatCameraFx.LEGACY_SWING_LEAN_DEGREES * CombatCameraFx.LEGACY_SWING_LEAN_SIGN[index],
                    camera.swingLeanDegrees(), name);
            // The surge played for a step of at least one block, at LUNGE_FOV_SURGE.
            boolean lunge = BasicSwordStyle.DEFINITION.move(index).step()
                    .filter(step -> step.maximumDistance() >= CombatCameraFx.LEGACY_LUNGE_STEP_DISTANCE)
                    .isPresent();
            assertEquals(lunge ? CombatCameraFx.LEGACY_LUNGE_FOV_SURGE : 0.0F, camera.stepFovSurge(), name);
        }
    }

    private static ResourceLocation sound(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }
}
