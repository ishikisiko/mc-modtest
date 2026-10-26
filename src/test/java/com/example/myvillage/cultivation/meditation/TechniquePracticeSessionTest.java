package com.example.myvillage.cultivation.meditation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TechniquePracticeSessionTest {
    private static final ResourceLocation TECHNIQUE = ResourceLocation.parse("myvillage:jianqi_jue");
    @Test void practiceOwnsOneSessionAndOnlyFullActiveBatchesCanSettle() {
        var session = MeditationSession.practice(TECHNIQUE, 0, 64, 0, Level.OVERWORLD);
        assertTrue(session.status(MeditationStopReason.PRACTICE_ACCEPTED).study().isEmpty());
        assertEquals(TECHNIQUE, session.status(MeditationStopReason.PRACTICE_ACCEPTED).practice().orElseThrow());
        assertFalse(session.advancePracticeBatch(200), "preparation cannot earn mastery");
        for (int i = 0; i < 40; i++) session.advancePreparation();
        for (int i = 0; i < 19; i++) assertFalse(session.advancePracticeBatch(200));
        assertTrue(session.advancePracticeBatch(200));
        assertFalse(session.advancePracticeBatch(200), "next batch starts from zero");
        assertEquals(MeditationMode.PRACTICE, session.mode());
        assertTrue(session.moved(.1, 64, 0));
    }
    @Test void interruptedPracticeDoesNotCarryPartialTicksIntoANewSession() {
        var first = MeditationSession.practice(TECHNIQUE, 0, 64, 0, Level.OVERWORLD);
        for (int i = 0; i < 40; i++) first.advancePreparation();
        for (int i = 0; i < 19; i++) first.advancePracticeBatch(200);
        var next = MeditationSession.practice(TECHNIQUE, 0, 64, 0, Level.OVERWORLD);
        for (int i = 0; i < 40; i++) next.advancePreparation();
        assertFalse(next.advancePracticeBatch(200));
    }
    @Test void practiceCannotBeMistakenForNormalMeditationOrASecondStudyTarget() {
        assertThrows(IllegalArgumentException.class, () -> new MeditationSession(MeditationMode.PRACTICE, 0, 64, 0, Level.OVERWORLD));
        assertThrows(IllegalArgumentException.class, () -> new MeditationStatus(MeditationState.MEDITATING_NORMAL, 0,
                MeditationStopReason.NONE, java.util.Optional.empty(), 0, 0,
                java.util.Optional.of(new StudyProgress(TECHNIQUE, 0, 100, -1, 0)), java.util.Optional.of(TECHNIQUE)));
        assertThrows(IllegalArgumentException.class, () -> MeditationStatus.practice(MeditationState.IDLE, 0, MeditationStopReason.NONE, TECHNIQUE));
    }
}
