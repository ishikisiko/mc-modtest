package com.example.myvillage.cultivation.study;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.meditation.StudyProgress;
import com.example.myvillage.sim.data.Rules;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.example.myvillage.cultivation.study.StudyFixtures.BREATHING;
import static com.example.myvillage.cultivation.study.StudyFixtures.HUANG;
import static com.example.myvillage.cultivation.study.StudyFixtures.REALMS;
import static com.example.myvillage.cultivation.study.StudyFixtures.RULES;
import static com.example.myvillage.cultivation.study.StudyFixtures.TECHNIQUES;
import static com.example.myvillage.cultivation.study.StudyFixtures.XUAN;
import static com.example.myvillage.cultivation.study.StudyFixtures.XUAN_DEFINITION;
import static com.example.myvillage.cultivation.study.StudyFixtures.chainCultivator;
import static com.example.myvillage.cultivation.study.StudyFixtures.cultivator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudyStepTest {
    @Test
    void aBatchWritesThePointsOntoTheManualWithoutTouchingTheProfile() {
        FakeSlot slot = new FakeSlot(XUAN, 100);
        Committer committer = new Committer(true);

        StudyStep.Result result = settle(XUAN, slot, chainCultivator(0), committer, Optional.of(RULES));

        assertEquals(StudyStep.Outcome.CONTINUE, result.outcome());
        assertEquals(111, slot.comprehension);
        assertEquals(new StudyProgress(XUAN, 111, 12_000, 6_000, 50), result.progress().orElseThrow());
        assertTrue(committer.commits.isEmpty());
    }

    @Test
    void withoutWorldSimDataThereIsNoElementBonus() {
        FakeSlot slot = new FakeSlot(XUAN, 100);
        settle(XUAN, slot, chainCultivator(0), new Committer(true), Optional.empty());
        assertEquals(110, slot.comprehension);
    }

    @Test
    void workedExampleGateWithFortyStabilityStopsOnTheGate() {
        FakeSlot slot = new FakeSlot(XUAN, 5_995);
        Committer committer = new Committer(true);

        StudyStep.Result result = settle(XUAN, slot, chainCultivator(40), committer, Optional.of(RULES));

        assertEquals(StudyStep.Outcome.GATE_BLOCKED, result.outcome());
        assertEquals(6_000, slot.comprehension);
        assertEquals(10, result.shortfall());
        assertEquals(50, result.gateCost());
        assertEquals(new StudyProgress(XUAN, 6_000, 12_000, 6_000, 50), result.progress().orElseThrow());
        assertTrue(committer.commits.isEmpty());
    }

    @Test
    void workedExampleGateWithSixtyStabilityPaysFiftyThroughTheCommitter() {
        FakeSlot slot = new FakeSlot(XUAN, 5_995);
        Committer committer = new Committer(true);
        CultivationProfile before = chainCultivator(60);

        StudyStep.Result result = settle(XUAN, slot, before, committer, Optional.of(RULES));

        assertEquals(StudyStep.Outcome.CONTINUE, result.outcome());
        assertEquals(6_006, slot.comprehension);
        assertEquals(50, result.gatesPaid());
        assertEquals(List.of(before.withStability(10)), committer.commits);
        assertEquals(StudyProgress.NO_GATE, result.progress().orElseThrow().nextGatePoints());
    }

    @Test
    void completionLearnsThroughTheCommitterAndConsumesTheManual() {
        FakeSlot slot = new FakeSlot(XUAN, 11_995);
        Committer committer = new Committer(true);
        CultivationProfile before = chainCultivator(0);

        StudyStep.Result result = settle(XUAN, slot, before, committer, Optional.of(RULES));

        assertEquals(StudyStep.Outcome.COMPLETE, result.outcome());
        assertEquals(1, slot.consumed);
        assertEquals(11_995, slot.comprehension);
        assertEquals(1, committer.commits.size());
        CultivationProfile after = committer.commits.get(0);
        assertTrue(after.learnedTechniques().containsKey(XUAN));
        assertEquals(before.activeCoreTechnique(), after.activeCoreTechnique());
        assertEquals(new StudyProgress(XUAN, 12_000, 12_000, StudyProgress.NO_GATE, 50),
                result.progress().orElseThrow());
    }

    @Test
    void theFirstCoreTechniqueLearnedByStudyStartsRunning() {
        CultivationProfile nothingRunning = cultivator(0).forgetTechnique(BREATHING)
                .learnTechnique(BREATHING);
        assertTrue(nothingRunning.activeCoreTechnique().isEmpty());
        FakeSlot slot = new FakeSlot(HUANG, 3_990);
        Committer committer = new Committer(true);

        StudyStep.Result result = settle(HUANG, slot, nothingRunning, committer, Optional.of(RULES));

        assertEquals(StudyStep.Outcome.COMPLETE, result.outcome());
        assertEquals(Optional.of(HUANG), committer.commits.get(0).activeCoreTechnique());
        assertEquals(1, slot.consumed);
    }

    @Test
    void aSwappedOrEmptiedSlotLosesTheManualWithoutWriting() {
        FakeSlot swapped = new FakeSlot(HUANG, 500);
        Committer committer = new Committer(true);
        StudyStep.Result result = settle(XUAN, swapped, chainCultivator(100), committer, Optional.of(RULES));
        assertEquals(StudyStep.Outcome.MANUAL_LOST, result.outcome());
        assertTrue(result.progress().isEmpty());
        assertEquals(500, swapped.comprehension);
        assertEquals(0, swapped.writes);

        FakeSlot empty = new FakeSlot(null, 0);
        assertEquals(StudyStep.Outcome.MANUAL_LOST,
                settle(XUAN, empty, chainCultivator(100), committer, Optional.of(RULES)).outcome());
        assertTrue(committer.commits.isEmpty());
    }

    @Test
    void lostRequirementsStopTheStudy() {
        FakeSlot slot = new FakeSlot(XUAN, 100);
        CultivationProfile forgotPrevious = chainCultivator(100).forgetTechnique(HUANG);
        assertEquals(StudyStep.Outcome.REQUIREMENTS,
                settle(XUAN, slot, forgotPrevious, new Committer(true), Optional.of(RULES)).outcome());
        CultivationProfile alreadyLearned = chainCultivator(100).learnTechnique(XUAN, TechniqueCategory.ACTIVE);
        assertEquals(StudyStep.Outcome.REQUIREMENTS,
                settle(XUAN, slot, alreadyLearned, new Committer(true), Optional.of(RULES)).outcome());
        assertEquals(100, slot.comprehension);
    }

    @Test
    void aRejectedCommitLeavesTheManualAsItWas() {
        FakeSlot gate = new FakeSlot(XUAN, 5_995);
        assertEquals(StudyStep.Outcome.COMMIT_FAILED,
                settle(XUAN, gate, chainCultivator(60), new Committer(false), Optional.of(RULES)).outcome());
        assertEquals(5_995, gate.comprehension);

        FakeSlot finishing = new FakeSlot(XUAN, 11_995);
        Committer throwing = new Committer(true) {
            @Override
            public boolean commit(CultivationProfile replacement) {
                throw new IllegalStateException("boom");
            }
        };
        assertEquals(StudyStep.Outcome.COMMIT_FAILED,
                settle(XUAN, finishing, chainCultivator(0), throwing, Optional.of(RULES)).outcome());
        assertEquals(0, finishing.consumed);
        assertEquals(11_995, finishing.comprehension);
    }

    @Test
    void progressStaysOnTheManualAcrossAnInterruptionAndResumesFromIt() {
        FakeSlot slot = new FakeSlot(XUAN, 0);
        CultivationProfile profile = chainCultivator(0);
        for (int batch = 0; batch < 7; batch++) {
            settle(XUAN, slot, profile, new Committer(true), Optional.of(RULES));
        }
        // The session ends here (moved, damaged, stop key...): nothing touches the slot.
        assertEquals(77, slot.comprehension);
        StudyProgress resumed = StudyStep.progress(XUAN, slot.comprehension, XUAN_DEFINITION.study());
        assertEquals(new StudyProgress(XUAN, 77, 12_000, 6_000, 50), resumed);
        settle(XUAN, slot, profile, new Committer(true), Optional.of(RULES));
        assertEquals(88, slot.comprehension);
        assertFalse(slot.consumed > 0);
    }

    private static StudyStep.Result settle(
            ResourceLocation technique, FakeSlot slot, CultivationProfile profile, Committer committer,
            Optional<Rules> rules) {
        return StudyStep.settle(technique, slot, profile, TECHNIQUES::get, REALMS::get, id -> true, rules, committer);
    }

    private static class Committer implements StudyStep.ProfileCommitter {
        final List<CultivationProfile> commits = new ArrayList<>();
        private final boolean accept;

        Committer(boolean accept) {
            this.accept = accept;
        }

        @Override
        public boolean commit(CultivationProfile replacement) {
            if (accept) {
                commits.add(replacement);
            }
            return accept;
        }
    }

    private static final class FakeSlot implements ManualSlot {
        private final ResourceLocation technique;
        int comprehension;
        int writes;
        int consumed;

        FakeSlot(ResourceLocation technique, int comprehension) {
            this.technique = technique;
            this.comprehension = comprehension;
        }

        @Override
        public Optional<ResourceLocation> technique() {
            return consumed > 0 ? Optional.empty() : Optional.ofNullable(technique);
        }

        @Override
        public int comprehension() {
            return comprehension;
        }

        @Override
        public void writeComprehension(int points) {
            comprehension = points;
            writes++;
        }

        @Override
        public void consumeOne() {
            consumed++;
        }
    }
}
