package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.BasicSwordStyle;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class FirstPersonSwordPoseTest {
    private static final float[] STRIKE_PROGRESS = {0.56F, 0.57F, 0.58F, 0.59F, 0.60F};

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
    void ownerRevisionAmplifiesDisplacementByExactlyTwentyPercent() {
        assertEquals(1.20F, FirstPersonSwordPose.AMPLITUDE_SCALE);

        FirstPersonSwordPose.Pose thrustPeak = FirstPersonSwordPose.sample(0, 0.56F);
        assertEquals(-0.192F, thrustPeak.x(), 0.0001F);
        assertEquals(-0.216F, thrustPeak.z(), 0.0001F);
        assertEquals(-74.4F, thrustPeak.pitch(), 0.0001F);
        assertEquals(0.856F, thrustPeak.scale(), 0.0001F);
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
    void revisedPeaksKeepDirectionalSignaturesInsteadOfOnlyScalingAliases() {
        FirstPersonSwordPose.Pose thrust = FirstPersonSwordPose.sample(0, STRIKE_PROGRESS[0]);
        FirstPersonSwordPose.Pose horizontal = FirstPersonSwordPose.sample(1, STRIKE_PROGRESS[1]);
        FirstPersonSwordPose.Pose rising = FirstPersonSwordPose.sample(2, STRIKE_PROGRESS[2]);
        FirstPersonSwordPose.Pose diagonal = FirstPersonSwordPose.sample(3, STRIKE_PROGRESS[3]);
        FirstPersonSwordPose.Pose lunge = FirstPersonSwordPose.sample(4, STRIKE_PROGRESS[4]);

        assertTrue(horizontal.x() < -0.40F && horizontal.yaw() > 65.0F);
        assertTrue(rising.y() > 0.29F && rising.pitch() < -90.0F);
        assertTrue(diagonal.y() < -0.29F && diagonal.roll() < -50.0F);
        assertTrue(lunge.z() < -0.35F && lunge.z() < thrust.z() - 0.10F);
    }

    @Test
    void everyInterpolatedPoseStaysInsideCameraSafeBounds() {
        for (int moveIndex = 0; moveIndex < FirstPersonSwordPose.MOVE_COUNT; moveIndex++) {
            for (int sample = 0; sample <= 100; sample++) {
                FirstPersonSwordPose.Pose pose = FirstPersonSwordPose.sample(moveIndex, sample / 100.0F);
                assertTrue(Math.abs(pose.x()) <= 0.44F);
                assertTrue(Math.abs(pose.y()) <= 0.31F);
                assertTrue(Math.abs(pose.z()) <= 0.37F);
                assertTrue(Math.abs(pose.pitch()) <= 94.0F);
                assertTrue(Math.abs(pose.yaw()) <= 70.0F);
                assertTrue(Math.abs(pose.roll()) <= 51.0F);
                assertTrue(pose.scale() >= 0.78F && pose.scale() <= 1.0F);
            }
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
