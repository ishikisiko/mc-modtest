package com.example.myvillage.cultivation.root;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiritualRootAwakeningServiceTest {
    private static final UUID PLAYER = UUID.fromString("00000000-1111-2222-3333-444444444444");

    @Test
    void defaultProfileAwakensWithOneAtomicReplacement() {
        CultivationProfile current = new CultivationProfile(
                CultivationProfile.CURRENT_SCHEMA_VERSION,
                ModCultivationRegistries.MORTAL_REALM_ID,
                ModCultivationRegistries.MORTAL_UNAWAKENED_STAGE_ID,
                77,
                42,
                19,
                23,
                1234,
                56,
                Optional.empty(),
                Map.of(id("other_technique"), new TechniqueProgress(12)));
        AtomicInteger commits = new AtomicInteger();
        AtomicReference<CultivationProfile> committed = new AtomicReference<>();

        SpiritualRootAwakeningService.Outcome outcome = awaken(
                current,
                replacement -> {
                    commits.incrementAndGet();
                    committed.set(replacement);
                    return true;
                });

        assertEquals(SpiritualRootAwakeningService.Status.SUCCESS, outcome.status());
        assertEquals(1, commits.get());
        assertEquals(outcome.profile(), committed.get());
        assertNotSame(current, outcome.profile());
        assertTrue(outcome.profile().spiritualRoot().isPresent());
        assertEquals(ModCultivationRegistries.MORTAL_REALM_ID, outcome.profile().realmId());
        assertEquals(ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID, outcome.profile().stageId());
        assertEquals(current.schemaVersion(), outcome.profile().schemaVersion());
        assertEquals(current.cultivationProgress(), outcome.profile().cultivationProgress());
        assertEquals(current.stability(), outcome.profile().stability());
        assertEquals(current.currentSpiritualPower(), outcome.profile().currentSpiritualPower());
        assertEquals(current.spiritualAffinity(), outcome.profile().spiritualAffinity());
        assertEquals(current.lifespanConsumedTicks(), outcome.profile().lifespanConsumedTicks());
        assertEquals(current.meditationQiReserve(), outcome.profile().meditationQiReserve());
        assertEquals(current.learnedTechniques(), outcome.profile().learnedTechniques());
        assertTrue(candidates().stream().map(SpiritualRootGenerator.ElementCandidate::id).toList()
                .containsAll(outcome.profile().spiritualRoot().orElseThrow().affinitiesBasisPoints().keySet()));
    }

    @Test
    void repeatedAwakeningDoesNotRerollOrCommit() {
        SpiritualRootAwakeningService.Outcome first = awaken(
                CultivationProfile.defaultProfile(), replacement -> true);
        AtomicInteger commits = new AtomicInteger();

        SpiritualRootAwakeningService.Outcome second = SpiritualRootAwakeningService.awaken(
                first.profile(),
                123456789L,
                PLAYER,
                candidates(),
                replacement -> {
                    commits.incrementAndGet();
                    return true;
                });

        assertEquals(SpiritualRootAwakeningService.Status.ALREADY_AWAKENED, second.status());
        assertEquals(first.profile(), second.profile());
        assertEquals(0, commits.get());
    }

    @Test
    void clearedQiSensedMortalCanReawaken() {
        CultivationProfile cleared = CultivationProfile.defaultProfile()
                .withRealmAndStage(
                        ModCultivationRegistries.MORTAL_REALM_ID,
                        ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID);

        assertEquals(
                SpiritualRootAwakeningService.Status.SUCCESS,
                awaken(cleared, replacement -> true).status());
    }

    @Test
    void rootlessNonMortalAndEmptyCandidatesFailWithoutCommit() {
        CultivationProfile nonMortal = CultivationProfile.defaultProfile().withRealmAndStage(
                ModCultivationRegistries.QI_REFINING_REALM_ID,
                ModCultivationRegistries.QI_REFINING_1_STAGE_ID);
        AtomicInteger commits = new AtomicInteger();

        SpiritualRootAwakeningService.Outcome invalid = SpiritualRootAwakeningService.awaken(
                nonMortal, 1, PLAYER, candidates(), replacement -> {
                    commits.incrementAndGet();
                    return true;
                });
        SpiritualRootAwakeningService.Outcome empty = SpiritualRootAwakeningService.awaken(
                CultivationProfile.defaultProfile(), 1, PLAYER, List.of(), replacement -> {
                    commits.incrementAndGet();
                    return true;
                });

        assertEquals(SpiritualRootAwakeningService.Status.INVALID_PROFILE_STATE, invalid.status());
        assertEquals(nonMortal, invalid.profile());
        assertEquals(SpiritualRootAwakeningService.Status.NO_ELIGIBLE_ELEMENTS, empty.status());
        assertEquals(CultivationProfile.defaultProfile(), empty.profile());
        assertEquals(0, commits.get());
    }

    @Test
    void rejectedCommitKeepsOldProfile() {
        CultivationProfile current = CultivationProfile.defaultProfile();
        AtomicInteger commits = new AtomicInteger();

        SpiritualRootAwakeningService.Outcome outcome = awaken(current, replacement -> {
            commits.incrementAndGet();
            return false;
        });

        assertEquals(SpiritualRootAwakeningService.Status.UPDATE_REJECTED, outcome.status());
        assertEquals(current, outcome.profile());
        assertEquals(1, commits.get());
    }

    @Test
    void resetReplayIsIdenticalUnderUnchangedInputs() {
        SpiritualRoot first = awaken(CultivationProfile.defaultProfile(), replacement -> true)
                .profile().spiritualRoot().orElseThrow();
        SpiritualRoot replay = awaken(CultivationProfile.defaultProfile(), replacement -> true)
                .profile().spiritualRoot().orElseThrow();

        assertEquals(first, replay);
    }

    private static SpiritualRootAwakeningService.Outcome awaken(
            CultivationProfile profile,
            SpiritualRootAwakeningService.ProfileCommitter committer) {
        return SpiritualRootAwakeningService.awaken(
                profile, 123456789L, PLAYER, candidates(), committer);
    }

    private static List<SpiritualRootGenerator.ElementCandidate> candidates() {
        return List.of(
                new SpiritualRootGenerator.ElementCandidate(id("metal"), 1),
                new SpiritualRootGenerator.ElementCandidate(id("wood"), 1),
                new SpiritualRootGenerator.ElementCandidate(id("water"), 1),
                new SpiritualRootGenerator.ElementCandidate(id("fire"), 1),
                new SpiritualRootGenerator.ElementCandidate(id("earth"), 1));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }
}
