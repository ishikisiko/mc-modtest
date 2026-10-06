package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.cultivation.data.AdvancementDefinition;
import com.example.myvillage.cultivation.data.AdvancementKind;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeditationSessionTest {
    @Test
    void normalAndSpiritPreparationTakeExactlyFortyTicks() {
        for (MeditationMode mode : new MeditationMode[]{MeditationMode.NORMAL, MeditationMode.SPIRIT}) {
            MeditationSession session = new MeditationSession(mode, 1, 2, 3, Level.OVERWORLD);

            assertEquals(MeditationState.preparing(mode), session.state());
            assertEquals(40, session.preparationTicksRemaining());
            for (int tick = 1; tick < MeditationManager.PREPARATION_TICKS; tick++) {
                assertFalse(session.advancePreparation());
                assertEquals(MeditationManager.PREPARATION_TICKS - tick,
                        session.preparationTicksRemaining());
            }
            assertTrue(session.advancePreparation());
            assertEquals(MeditationState.meditating(mode), session.state());
            assertEquals(0, session.preparationTicksRemaining());
            assertFalse(session.advancePreparation());
        }
    }

    @Test
    void movementUsesPerAxisToleranceAndIgnoresRotationByConstruction() {
        MeditationSession session = new MeditationSession(
                MeditationMode.NORMAL, 10, 64, -10, Level.OVERWORLD);

        assertFalse(session.moved(10.01, 63.99, -9.99));
        assertTrue(session.moved(10.010_001, 64, -10));
        assertTrue(session.moved(10, 63.989_999, -10));
        assertTrue(session.moved(10, 64, -9.989_999));
    }

    @Test
    void duplicateFeedbackIsRateLimitedWithoutChangingState() {
        MeditationSession session = new MeditationSession(
                MeditationMode.SPIRIT, 0, 0, 0, Level.OVERWORLD);

        assertTrue(session.allowDuplicateFeedback(100));
        assertFalse(session.allowDuplicateFeedback(119));
        assertTrue(session.allowDuplicateFeedback(120));
        assertEquals(MeditationState.PREPARING_SPIRIT, session.state());
        assertEquals(40, session.preparationTicksRemaining());
    }

    @Test
    void activeMeditationSettlesEveryTenTicksAndLocksItsSourceStage() {
        MeditationSession session = new MeditationSession(
                MeditationMode.NORMAL,
                ModCultivationRegistries.MORTAL_REALM_ID,
                ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID,
                0,
                0,
                0,
                Level.OVERWORLD);
        for (int tick = 0; tick < MeditationManager.PREPARATION_TICKS; tick++) {
            session.advancePreparation();
        }

        for (int tick = 1; tick < BasicBreathingSettlement.SETTLEMENT_INTERVAL_TICKS; tick++) {
            assertFalse(session.advanceMeditationTick());
        }
        assertTrue(session.advanceMeditationTick());
        assertTrue(session.matchesSource(
                ModCultivationRegistries.MORTAL_REALM_ID,
                ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID));
        assertFalse(session.matchesSource(
                ModCultivationRegistries.QI_REFINING_REALM_ID,
                ModCultivationRegistries.QI_REFINING_1_STAGE_ID));
    }

    @Test
    void advancementUsesDeclaredDurationWithoutMeditationPreparation() {
        AdvancementDefinition definition = new AdvancementDefinition(
                ModCultivationRegistries.QI_REFINING_REALM_ID,
                ModCultivationRegistries.QI_REFINING_1_STAGE_ID,
                AdvancementKind.ORDINARY,
                100,
                500,
                250,
                0);
        MeditationSession session = MeditationSession.advancement(
                new AdvancementContext(
                        ModCultivationRegistries.MORTAL_REALM_ID,
                        ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID,
                        1000,
                        definition),
                0,
                0,
                0,
                Level.OVERWORLD);

        assertEquals(MeditationState.ADVANCING_ORDINARY, session.state());
        assertEquals(100, session.advancementTicksRemaining());
        assertEquals(AdvancementKind.ORDINARY,
                session.status(MeditationStopReason.ADVANCEMENT_ACCEPTED)
                        .advancementKind().orElseThrow());
        for (int tick = 1; tick < definition.durationTicks(); tick++) {
            assertFalse(session.advanceAdvancementTick());
            assertEquals(definition.durationTicks() - tick, session.advancementTicksRemaining());
        }
        assertTrue(session.advanceAdvancementTick());
        assertEquals(0, session.advancementTicksRemaining());
    }

    @Test
    void studySitsLikeNormalMeditationAndCarriesItsProgressInTheStatus() {
        ResourceLocation technique = ResourceLocation.fromNamespaceAndPath("myvillage", "gengjin_jianjue");
        StudyProgress initial = new StudyProgress(technique, 120, 12_000, 6_000, 50);
        MeditationSession session = MeditationSession.study(
                new MeditationSession.StudyTarget(3, technique), initial, 0, 64, 0, Level.OVERWORLD);

        assertTrue(session.studying());
        assertEquals(MeditationMode.STUDY, session.mode());
        assertEquals(MeditationState.PREPARING_NORMAL, session.state());
        MeditationStatus accepted = session.status(MeditationStopReason.STUDY_ACCEPTED);
        assertEquals(40, accepted.preparationTicksRemaining());
        assertEquals(initial, accepted.study().orElseThrow());
        assertTrue(accepted.studying());
        assertEquals(3, session.studyTarget().orElseThrow().slot());

        for (int tick = 1; tick < MeditationManager.PREPARATION_TICKS; tick++) {
            assertFalse(session.advancePreparation());
        }
        assertTrue(session.advancePreparation());
        assertEquals(MeditationState.MEDITATING_NORMAL, session.state());
        for (int tick = 1; tick < BasicBreathingSettlement.SETTLEMENT_INTERVAL_TICKS; tick++) {
            assertFalse(session.advanceMeditationTick());
        }
        assertTrue(session.advanceMeditationTick());

        StudyProgress later = new StudyProgress(technique, 131, 12_000, 6_000, 50);
        session.updateStudyProgress(later);
        assertEquals(later, session.status(MeditationStopReason.NONE).study().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> session.updateStudyProgress(
                new StudyProgress(ResourceLocation.fromNamespaceAndPath("myvillage", "other"), 0, 4_000, -1, 0)));
        assertThrows(IllegalStateException.class, session::downgradeToNormal);
    }

    @Test
    void studyStartsOnlyThroughItsOwnFactoryAndOtherSessionsCarryNoStudy() {
        assertThrows(IllegalArgumentException.class,
                () -> new MeditationSession(MeditationMode.STUDY, 0, 0, 0, Level.OVERWORLD));
        assertThrows(IllegalArgumentException.class,
                () -> new MeditationSession.StudyTarget(-1, ResourceLocation.fromNamespaceAndPath("myvillage", "x")));
        MeditationSession normal = new MeditationSession(MeditationMode.NORMAL, 0, 0, 0, Level.OVERWORLD);
        assertFalse(normal.studying());
        assertTrue(normal.status(MeditationStopReason.NONE).study().isEmpty());
        assertThrows(IllegalStateException.class, () -> normal.updateStudyProgress(
                new StudyProgress(ResourceLocation.fromNamespaceAndPath("myvillage", "x"), 0, 4_000, -1, 0)));
    }
}
