package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.cultivation.data.AdvancementKind;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeditationStatusStudyTest {
    private static final ResourceLocation TECHNIQUE = ResourceLocation.fromNamespaceAndPath("myvillage", "gengjin_jianjue");
    private static final StudyProgress PROGRESS = new StudyProgress(TECHNIQUE, 6_000, 12_000, 6_000, 50);

    @Test
    void studyProgressAppearsOnlyInTheStatesAStudySessionUses() {
        assertDoesNotThrow(() -> MeditationStatus.study(
                MeditationState.PREPARING_NORMAL, 40, MeditationStopReason.STUDY_ACCEPTED, PROGRESS));
        assertTrue(MeditationStatus.study(
                MeditationState.MEDITATING_NORMAL, 0, MeditationStopReason.NONE, PROGRESS).studying());
        for (MeditationState state : new MeditationState[]{
                MeditationState.IDLE, MeditationState.PREPARING_SPIRIT, MeditationState.MEDITATING_SPIRIT}) {
            assertThrows(IllegalArgumentException.class,
                    () -> MeditationStatus.study(state, 0, MeditationStopReason.NONE, PROGRESS));
        }
        assertThrows(IllegalArgumentException.class, () -> new MeditationStatus(
                MeditationState.ADVANCING_ORDINARY, 0, MeditationStopReason.NONE,
                Optional.of(AdvancementKind.ORDINARY), 100, 50, Optional.of(PROGRESS)));
        assertFalse(MeditationStatus.idle(MeditationStopReason.STUDY_COMPLETE).studying());
        assertFalse(new MeditationStatus(MeditationState.MEDITATING_NORMAL, 0, MeditationStopReason.NONE).studying());
    }

    @Test
    void studyProgressValidatesPointsGateAndCost() {
        assertDoesNotThrow(() -> new StudyProgress(TECHNIQUE, 0, 4_000, StudyProgress.NO_GATE, 0));
        assertDoesNotThrow(() -> new StudyProgress(TECHNIQUE, 4_000, 4_000, StudyProgress.NO_GATE, 0));
        assertThrows(IllegalArgumentException.class, () -> new StudyProgress(TECHNIQUE, 0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new StudyProgress(TECHNIQUE, -1, 4_000, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new StudyProgress(TECHNIQUE, 4_001, 4_000, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new StudyProgress(TECHNIQUE, 6_001, 12_000, 6_000, 50));
        assertThrows(IllegalArgumentException.class, () -> new StudyProgress(TECHNIQUE, 0, 12_000, 12_000, 50));
        assertThrows(IllegalArgumentException.class, () -> new StudyProgress(TECHNIQUE, 0, 12_000, -2, 50));
        assertThrows(IllegalArgumentException.class, () -> new StudyProgress(TECHNIQUE, 0, 12_000, 6_000, -1));
        assertThrows(NullPointerException.class, () -> new StudyProgress(null, 0, 12_000, 6_000, 0));
    }
}
