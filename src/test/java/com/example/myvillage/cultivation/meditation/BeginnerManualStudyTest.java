package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.study.ManualSlot;
import com.example.myvillage.cultivation.study.StudyStart;
import com.example.myvillage.cultivation.study.StudyStep;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped beginner manual, driven by the production session's preparation and settlement clocks. */
class BeginnerManualStudyTest {
    private static final ResourceLocation SWORD_QI = ResourceLocation.fromNamespaceAndPath("myvillage", "jianqi_jue");
    private static final ResourceLocation QI_REALM = ModCultivationRegistries.QI_REFINING_REALM_ID;

    @Test
    void defaultAffinityFinishesAtTenSecondsAfterCommittingLearningAndConsumesOnlyOnce() throws Exception {
        StudyRun run = new StudyRun(0, CultivationProfile.DEFAULT_SPIRITUAL_AFFINITY);
        assertEquals(1, run.definition.grade());

        run.advance(39);
        assertTrue(run.session.state().preparing());
        assertEquals(0, run.comprehension());
        run.advance(1);
        assertTrue(run.session.state().meditating());
        assertEquals(0, run.comprehension());
        run.advance(159);
        assertFalse(run.learned());
        assertEquals(150, run.comprehension());
        assertEquals(0, run.consumed);
        assertTrue(run.commitAndConsumeOrder.isEmpty());

        run.advance(1);
        assertEquals(200, run.ticks);
        assertEquals(StudyStep.Outcome.COMPLETE, run.last.outcome());
        assertTrue(run.learned());
        assertEquals(160, run.session.studyProgress().orElseThrow().points());
        assertEquals(List.of("commit", "consume"), run.commitAndConsumeOrder);
        assertEquals(1, run.consumed);

        // Even a repeated settlement cannot spend another manual or replay the learning commit.
        assertEquals(StudyStep.Outcome.MANUAL_LOST, run.settle().outcome());
        assertEquals(List.of("commit", "consume"), run.commitAndConsumeOrder);
        assertEquals(1, run.consumed);
    }

    @Test
    void affinityStillChangesStudyDurationInsteadOfUsingAFixedTenSecondTimer() throws Exception {
        for (int[] example : new int[][]{{20, 120}, {5, 360}}) {
            StudyRun run = new StudyRun(0, example[0]);
            run.advance(example[1] - 1);
            assertFalse(run.learned());
            assertEquals(0, run.consumed);
            run.advance(1);
            assertTrue(run.learned());
            assertEquals(StudyStep.Outcome.COMPLETE, run.last.outcome());
            assertEquals(1, run.consumed);
        }
    }

    @Test
    void zeroAffinityDoesNotAutomaticallyLearnAfterTenSeconds() throws Exception {
        StudyRun run = new StudyRun(0, 0);
        run.advance(400);
        assertEquals(StudyStep.Outcome.CONTINUE, run.last.outcome());
        assertEquals(0, run.comprehension());
        assertFalse(run.learned());
        assertEquals(0, run.consumed);
    }

    @Test
    void oldManualAboveTheNewCostCompletesAtTheFirstNormalSettlement() throws Exception {
        StudyRun run = new StudyRun(3_990, CultivationProfile.DEFAULT_SPIRITUAL_AFFINITY);
        assertEquals(160, run.session.studyProgress().orElseThrow().points());
        run.advance(49);
        assertFalse(run.learned());
        assertEquals(3_990, run.comprehension());
        assertEquals(0, run.consumed);
        run.advance(1);
        assertTrue(run.learned());
        assertEquals(StudyStep.Outcome.COMPLETE, run.last.outcome());
        assertEquals(160, run.last.progress().orElseThrow().points());
        assertEquals(List.of("commit", "consume"), run.commitAndConsumeOrder);
        assertEquals(1, run.consumed);
    }

    @Test
    void rejectedLearningCommitDoesNotConsumeTheNearlyFinishedManual() throws Exception {
        StudyRun run = new StudyRun(0, CultivationProfile.DEFAULT_SPIRITUAL_AFFINITY);
        run.acceptCommits = false;
        run.advance(200);
        assertEquals(StudyStep.Outcome.COMMIT_FAILED, run.last.outcome());
        assertFalse(run.learned());
        assertEquals(150, run.comprehension());
        assertEquals(0, run.consumed);
        assertTrue(run.commitAndConsumeOrder.isEmpty());
    }

    private static final class StudyRun implements ManualSlot {
        final TechniqueDefinition definition;
        final RealmDefinition realm;
        final MeditationSession session;
        final List<String> commitAndConsumeOrder = new ArrayList<>();
        CultivationProfile profile;
        StudyStep.Result last;
        int points;
        int consumed;
        int ticks;
        boolean acceptCommits = true;

        StudyRun(int comprehension, int affinity) throws IOException {
            Path directory = Path.of("src/main/resources/data/myvillage/myvillage");
            definition = TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString(Files.readString(directory.resolve("technique/jianqi_jue.json")))).getOrThrow();
            realm = RealmDefinition.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString(Files.readString(directory.resolve("realm/qi_refining.json")))).getOrThrow();
            profile = CultivationProfile.defaultProfile()
                    .withRealmAndStage(QI_REALM, ModCultivationRegistries.QI_REFINING_1_STAGE_ID)
                    .withSpiritualRoot(Optional.of(new SpiritualRoot(
                            Map.of(ModCultivationRegistries.METAL_ELEMENT_ID, 10_000))))
                    .withSpiritualAffinity(affinity);
            points = comprehension;
            assertTrue(StudyStart.refusal(technique(), profile, this::techniqueDefinition,
                    this::realmDefinition, id -> true, false).isEmpty());
            session = MeditationSession.study(new MeditationSession.StudyTarget(0, SWORD_QI),
                    StudyStep.progress(SWORD_QI, points, definition.study()), 0, 64, 0, Level.OVERWORLD);
        }

        void advance(int count) {
            for (int tick = 0; tick < count; tick++) {
                ticks++;
                // Same mutually exclusive preparation/meditation branches as MeditationManager.tick.
                if (session.state().preparing()) {
                    session.advancePreparation();
                } else if (session.advanceMeditationTick()) {
                    last = settle();
                    last.progress().ifPresent(session::updateStudyProgress);
                }
            }
        }

        StudyStep.Result settle() {
            return StudyStep.settle(SWORD_QI, this, profile, this::techniqueDefinition,
                    this::realmDefinition, id -> true, Optional.empty(), replacement -> {
                        if (!acceptCommits) {
                            return false;
                        }
                        profile = replacement;
                        commitAndConsumeOrder.add("commit");
                        return true;
                    });
        }

        boolean learned() {
            return profile.learnedTechniques().containsKey(SWORD_QI);
        }

        private TechniqueDefinition techniqueDefinition(ResourceLocation id) {
            return id.equals(SWORD_QI) ? definition : null;
        }

        private RealmDefinition realmDefinition(ResourceLocation id) {
            return id.equals(QI_REALM) ? realm : null;
        }

        @Override
        public Optional<ResourceLocation> technique() {
            return consumed == 0 ? Optional.of(SWORD_QI) : Optional.empty();
        }

        @Override
        public int comprehension() {
            return points;
        }

        @Override
        public void writeComprehension(int value) {
            points = value;
        }

        @Override
        public void consumeOne() {
            assertTrue(learned(), "Learning must be committed before consuming the manual");
            consumed++;
            commitAndConsumeOrder.add("consume");
        }
    }
}
