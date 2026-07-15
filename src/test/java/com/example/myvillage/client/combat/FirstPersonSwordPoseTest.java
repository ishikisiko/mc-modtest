package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.BasicSwordStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class FirstPersonSwordPoseTest {
    private static final float[] WINDUP_PROGRESS = {0.12F, 0.13F, 0.14F, 0.15F, 0.16F};
    private static final float[] STRIKE_PROGRESS = {0.56F, 0.57F, 0.58F, 0.59F, 0.60F};
    private static final float BASE_X = 0.56F;
    private static final float BASE_Y = -0.52F;
    private static final float BASE_Z = -0.72F;
    private static final float MIN_VIEW_PLANE_SWEEP = 1.00F;
    private static final float MAX_VIEW_PLANE_SWEEP = 1.85F;
    private static final float MAX_VIEW_PLANE_STEP = 0.08F;

    @Test
    void everyCombatMoveHasOnePoseCurveThatReturnsToIdle() {
        assertEquals(BasicSwordStyle.DEFINITION.moves().size(), FirstPersonSwordPose.MOVE_COUNT);
        FirstPersonSwordPose.Pose neutral = FirstPersonSwordPose.sample(0, 0.0F);
        for (int moveIndex = 0; moveIndex < FirstPersonSwordPose.MOVE_COUNT; moveIndex++) {
            assertEquals(neutral, FirstPersonSwordPose.sample(moveIndex, 0.0F));
            assertEquals(neutral, FirstPersonSwordPose.sample(moveIndex, 1.0F));
        }
    }

    @Test
    void fiveStrikeSilhouettesAreDistinctAtTheirRetimedPeaks() {
        Set<FirstPersonSwordPose.Pose> silhouettes = new HashSet<>();
        for (int moveIndex = 0; moveIndex < FirstPersonSwordPose.MOVE_COUNT; moveIndex++) {
            silhouettes.add(FirstPersonSwordPose.sample(moveIndex, STRIKE_PROGRESS[moveIndex]));
        }
        assertEquals(FirstPersonSwordPose.MOVE_COUNT, silhouettes.size());
    }

    @Test
    void everyMoveCrossesCenterWithAViewportSizedViewPlaneSweep() {
        for (int moveIndex = 0; moveIndex < FirstPersonSwordPose.MOVE_COUNT; moveIndex++) {
            List<FirstPersonSwordPose.Pose> samples = samples(moveIndex);
            float diameter = viewPlaneDiameter(samples);
            assertTrue(
                    diameter >= MIN_VIEW_PLANE_SWEEP,
                    "move " + moveIndex + " view-plane sweep was only " + diameter);
            assertTrue(
                    diameter <= MAX_VIEW_PLANE_SWEEP,
                    "move " + moveIndex + " view-plane sweep was excessive: " + diameter);

            boolean entersCenterBand = samples.stream()
                    .skip(1)
                    .limit(samples.size() - 2L)
                    .anyMatch(pose -> Math.abs(viewX(pose)) <= 0.25F);
            assertTrue(entersCenterBand, "move " + moveIndex + " never entered the center band");

            if (moveIndex != 2) {
                boolean reachesCenterLeft = samples.stream()
                        .skip(1)
                        .limit(samples.size() - 2L)
                        .anyMatch(pose -> viewX(pose) <= -0.10F);
                assertTrue(reachesCenterLeft, "move " + moveIndex + " never crossed center-left");
            }
        }
    }

    @Test
    void viewportCurvesStayInsideSafeBoundsAndRemainContinuous() {
        for (int moveIndex = 0; moveIndex < FirstPersonSwordPose.MOVE_COUNT; moveIndex++) {
            FirstPersonSwordPose.Pose previous = null;
            for (int sample = 0; sample <= 100; sample++) {
                FirstPersonSwordPose.Pose pose = FirstPersonSwordPose.sample(
                        moveIndex, sample / 100.0F);
                assertTrue(viewX(pose) >= -0.50F && viewX(pose) <= 1.00F);
                assertTrue(viewY(pose) >= -1.00F && viewY(pose) <= 0.35F);
                assertTrue(viewZ(pose) >= -0.92F && viewZ(pose) <= -0.44F);
                assertTrue(Math.abs(pose.pitch()) <= 94.0F);
                assertTrue(Math.abs(pose.yaw()) <= 78.0F);
                assertTrue(Math.abs(pose.roll()) <= 52.0F);
                assertTrue(pose.scale() >= 0.92F && pose.scale() <= 1.16F);
                if (previous != null) {
                    assertTrue(
                            viewPlaneDistance(previous, pose) <= MAX_VIEW_PLANE_STEP,
                            "move " + moveIndex + " jumped at sample " + sample);
                }
                previous = pose;
            }
        }
    }

    @Test
    void thrustHitsAdvanceIntoTheCenterAndLungeReadsStronger() {
        FirstPersonSwordPose.Pose thrustWindup = FirstPersonSwordPose.sample(
                0, WINDUP_PROGRESS[0]);
        FirstPersonSwordPose.Pose thrustHit = FirstPersonSwordPose.sample(
                0, STRIKE_PROGRESS[0]);
        FirstPersonSwordPose.Pose lungeWindup = FirstPersonSwordPose.sample(
                4, WINDUP_PROGRESS[4]);
        FirstPersonSwordPose.Pose lungeHit = FirstPersonSwordPose.sample(
                4, STRIKE_PROGRESS[4]);

        assertTrue(Math.abs(viewX(thrustHit)) <= 0.25F);
        assertTrue(Math.abs(viewX(lungeHit)) <= 0.30F);
        assertTrue(thrustHit.z() > thrustWindup.z());
        assertTrue(thrustHit.scale() > thrustWindup.scale());
        assertTrue(lungeHit.z() > lungeWindup.z());
        assertTrue(lungeHit.scale() > lungeWindup.scale());
        assertTrue(lungeHit.z() > thrustHit.z());
        assertTrue(lungeHit.scale() > thrustHit.scale());
    }

    @Test
    void cutsKeepTheirDirectionalViewPlaneSignatures() {
        FirstPersonSwordPose.Pose horizontalWindup = FirstPersonSwordPose.sample(
                1, WINDUP_PROGRESS[1]);
        FirstPersonSwordPose.Pose horizontalHit = FirstPersonSwordPose.sample(
                1, STRIKE_PROGRESS[1]);
        assertTrue(horizontalWindup.x() - horizontalHit.x() >= 1.40F);

        FirstPersonSwordPose.Pose risingWindup = FirstPersonSwordPose.sample(
                2, WINDUP_PROGRESS[2]);
        FirstPersonSwordPose.Pose risingHit = FirstPersonSwordPose.sample(
                2, STRIKE_PROGRESS[2]);
        assertTrue(risingHit.y() - risingWindup.y() >= 0.95F);

        FirstPersonSwordPose.Pose diagonalWindup = FirstPersonSwordPose.sample(
                3, WINDUP_PROGRESS[3]);
        FirstPersonSwordPose.Pose diagonalHit = FirstPersonSwordPose.sample(
                3, STRIKE_PROGRESS[3]);
        assertTrue(diagonalWindup.x() > 0.0F && diagonalWindup.y() > 0.0F);
        assertTrue(diagonalHit.x() < 0.0F && diagonalHit.y() < 0.0F);
        assertTrue(diagonalWindup.x() - diagonalHit.x() >= 1.35F);
        assertTrue(diagonalWindup.y() - diagonalHit.y() >= 0.65F);
    }

    @Test
    void retimingUsesMoreOfTheSameNormalizedDurationForVisibleMotion() {
        FirstPersonSwordPose.Pose neutral = FirstPersonSwordPose.sample(0, 0.0F);
        for (int moveIndex = 0; moveIndex < FirstPersonSwordPose.MOVE_COUNT; moveIndex++) {
            assertTrue(poseDistance(neutral, FirstPersonSwordPose.sample(moveIndex, 0.10F)) > 0.05F);
            assertTrue(poseDistance(neutral, FirstPersonSwordPose.sample(moveIndex, 0.90F)) > 0.05F);
            assertEquals(neutral, FirstPersonSwordPose.sample(moveIndex, 1.0F));
        }
    }

    @Test
    void samplingClampsProgressAndRejectsUnknownMoves() {
        assertEquals(
                FirstPersonSwordPose.sample(0, 0.0F),
                FirstPersonSwordPose.sample(0, -1.0F));
        assertEquals(
                FirstPersonSwordPose.sample(0, 1.0F),
                FirstPersonSwordPose.sample(0, 2.0F));
        assertThrows(IllegalArgumentException.class, () -> FirstPersonSwordPose.sample(-1, 0.5F));
        assertThrows(
                IllegalArgumentException.class,
                () -> FirstPersonSwordPose.sample(FirstPersonSwordPose.MOVE_COUNT, 0.5F));
    }

    private static List<FirstPersonSwordPose.Pose> samples(int moveIndex) {
        List<FirstPersonSwordPose.Pose> samples = new ArrayList<>();
        for (int sample = 0; sample <= 100; sample++) {
            samples.add(FirstPersonSwordPose.sample(moveIndex, sample / 100.0F));
        }
        return samples;
    }

    private static float viewPlaneDiameter(List<FirstPersonSwordPose.Pose> poses) {
        float diameter = 0.0F;
        for (FirstPersonSwordPose.Pose first : poses) {
            for (FirstPersonSwordPose.Pose second : poses) {
                diameter = Math.max(diameter, viewPlaneDistance(first, second));
            }
        }
        return diameter;
    }

    private static float viewPlaneDistance(
            FirstPersonSwordPose.Pose first,
            FirstPersonSwordPose.Pose second) {
        return (float) Math.hypot(first.x() - second.x(), first.y() - second.y());
    }

    private static float viewX(FirstPersonSwordPose.Pose pose) {
        return BASE_X + pose.x();
    }

    private static float viewY(FirstPersonSwordPose.Pose pose) {
        return BASE_Y + pose.y();
    }

    private static float viewZ(FirstPersonSwordPose.Pose pose) {
        return BASE_Z + pose.z();
    }

    private static float poseDistance(
            FirstPersonSwordPose.Pose first,
            FirstPersonSwordPose.Pose second) {
        float translation = Math.abs(first.x() - second.x())
                + Math.abs(first.y() - second.y())
                + Math.abs(first.z() - second.z());
        float rotation = (Math.abs(first.pitch() - second.pitch())
                + Math.abs(first.yaw() - second.yaw())
                + Math.abs(first.roll() - second.roll())) / 90.0F;
        return translation + rotation + Math.abs(first.scale() - second.scale());
    }
}
