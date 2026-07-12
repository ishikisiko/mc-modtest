package com.example.myvillage.cultivation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CultivationProfileTest {
    @Test
    void defaultProfileHasExactVersionOneValues() {
        CultivationProfile profile = CultivationProfile.defaultProfile();

        assertEquals(1, profile.schemaVersion());
        assertEquals(id("myvillage", "mortal"), profile.realmId());
        assertEquals(id("myvillage", "mortal_unawakened"), profile.stageId());
        assertEquals(0L, profile.cultivationProgress());
        assertEquals(0, profile.stability());
        assertEquals(0L, profile.currentSpiritualPower());
        assertTrue(profile.spiritualRoot().isEmpty());
        assertFalse(profile.awakened());
        assertTrue(profile.learnedTechniques().isEmpty());
    }

    @Test
    void profileCodecRoundTripsEveryField() {
        SpiritualRoot root = new SpiritualRoot(Map.of(
                id("myvillage", "fire"), 6_250,
                id("addon", "lightning"), 3_750));
        CultivationProfile profile = new CultivationProfile(
                1,
                id("addon", "lost_realm"),
                id("addon", "lost_stage"),
                9_876_543_210L,
                73,
                1_234_567_890L,
                Optional.of(root),
                Map.of(
                        id("myvillage", "basic_breathing"), new TechniqueProgress(41),
                        id("addon", "forgotten_art"), new TechniqueProgress(99)));

        assertEquals(profile, roundTrip(CultivationProfile.CODEC, profile));
    }

    @Test
    void validGenericSpiritualRootIsAccepted() {
        SpiritualRoot root = new SpiritualRoot(Map.of(
                id("addon", "lightning"), 4_000,
                id("addon", "ice"), 3_000,
                id("addon", "void"), 3_000));

        assertEquals(10_000, root.affinitiesBasisPoints().values().stream()
                .mapToInt(Integer::intValue)
                .sum());
    }

    @Test
    void spiritualRootRejectsWrongTotalInConstructorAndCodec() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpiritualRoot(Map.of(id("myvillage", "fire"), 9_999)));
        assertTrue(SpiritualRoot.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString(
                                "{\"affinities_basis_points\":{\"myvillage:fire\":9999}}"))
                .isError());
    }

    @Test
    void spiritualRootRejectsEachOutOfRangeAffinity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpiritualRoot(Map.of(
                        id("myvillage", "fire"), -1,
                        id("myvillage", "water"), 10_001)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpiritualRoot(Map.of(
                        id("myvillage", "fire"), 10_001,
                        id("myvillage", "water"), -1)));
        assertTrue(SpiritualRoot.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString("""
                                {
                                  "affinities_basis_points": {
                                    "myvillage:fire": 10001,
                                    "myvillage:water": -1
                                  }
                                }
                                """))
                .isError());
    }

    @Test
    void profileRejectsStabilityOutsideClosedRange() {
        CultivationProfile profile = CultivationProfile.defaultProfile();

        assertThrows(IllegalArgumentException.class, () -> profile.withStability(-1));
        assertThrows(IllegalArgumentException.class, () -> profile.withStability(101));
        assertEquals(0, profile.withStability(0).stability());
        assertEquals(100, profile.withStability(100).stability());
    }

    @Test
    void profileAndTechniqueRejectNegativeCounters() {
        CultivationProfile profile = CultivationProfile.defaultProfile();

        assertThrows(
                IllegalArgumentException.class,
                () -> profile.withCultivationProgress(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> profile.withCurrentSpiritualPower(-1));
        assertThrows(IllegalArgumentException.class, () -> new TechniqueProgress(-1));
        assertTrue(TechniqueProgress.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString("{\"mastery_points\":-1}"))
                .isError());
    }

    @Test
    void profileUpdatesDoNotMutateOldInstance() {
        CultivationProfile oldProfile = CultivationProfile.defaultProfile();
        CultivationProfile newProfile = oldProfile
                .withCultivationProgress(12)
                .withStability(34)
                .withCurrentSpiritualPower(56);

        assertNotSame(oldProfile, newProfile);
        assertEquals(0L, oldProfile.cultivationProgress());
        assertEquals(0, oldProfile.stability());
        assertEquals(0L, oldProfile.currentSpiritualPower());
        assertEquals(12L, newProfile.cultivationProgress());
        assertEquals(34, newProfile.stability());
        assertEquals(56L, newProfile.currentSpiritualPower());
    }

    @Test
    void profileAndRootDefensivelyCopyInputMaps() {
        ResourceLocation fire = id("myvillage", "fire");
        ResourceLocation technique = id("myvillage", "basic_breathing");
        Map<ResourceLocation, Integer> affinities = new LinkedHashMap<>();
        affinities.put(fire, 10_000);
        SpiritualRoot root = new SpiritualRoot(affinities);
        Map<ResourceLocation, TechniqueProgress> learned = new LinkedHashMap<>();
        learned.put(technique, new TechniqueProgress(7));
        CultivationProfile profile = new CultivationProfile(
                1,
                CultivationProfile.DEFAULT_REALM_ID,
                CultivationProfile.DEFAULT_STAGE_ID,
                0,
                0,
                0,
                Optional.of(root),
                learned);

        affinities.clear();
        learned.clear();

        assertEquals(10_000, root.affinitiesBasisPoints().get(fire));
        assertEquals(new TechniqueProgress(7), profile.learnedTechniques().get(technique));
        assertThrows(
                UnsupportedOperationException.class,
                () -> root.affinitiesBasisPoints().put(fire, 0));
        assertThrows(
                UnsupportedOperationException.class,
                () -> profile.learnedTechniques().put(technique, TechniqueProgress.ZERO));
    }

    @Test
    void learnMasteryAndForgetTransitionsPreserveOldProfiles() {
        ResourceLocation technique = id("myvillage", "basic_breathing");
        CultivationProfile initial = CultivationProfile.defaultProfile();
        CultivationProfile learned = initial.learnTechnique(technique);
        CultivationProfile mastered = learned.withTechniqueMastery(technique, 42);
        CultivationProfile forgotten = mastered.forgetTechnique(technique);

        assertTrue(initial.learnedTechniques().isEmpty());
        assertEquals(TechniqueProgress.ZERO, learned.learnedTechniques().get(technique));
        assertEquals(new TechniqueProgress(42), mastered.learnedTechniques().get(technique));
        assertTrue(forgotten.learnedTechniques().isEmpty());
        assertEquals(initial.realmId(), forgotten.realmId());
        assertEquals(initial.stageId(), forgotten.stageId());
    }

    @Test
    void masteryDoesNotImplicitlyLearnTechnique() {
        ResourceLocation technique = id("myvillage", "basic_breathing");
        CultivationProfile initial = CultivationProfile.defaultProfile();

        assertThrows(
                IllegalArgumentException.class,
                () -> initial.withTechniqueMastery(technique, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> initial.forgetTechnique(technique));
        assertTrue(initial.learnedTechniques().isEmpty());

        CultivationProfile learned = initial.learnTechnique(technique);
        assertThrows(
                IllegalArgumentException.class,
                () -> learned.learnTechnique(technique));
        assertThrows(
                IllegalArgumentException.class,
                () -> learned.withTechniqueMastery(technique, -1));
        assertEquals(TechniqueProgress.ZERO, learned.learnedTechniques().get(technique));
    }

    @Test
    void profileCodecPreservesUnknownResourceLocations() {
        CultivationProfile decoded = CultivationProfile.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString("""
                                {
                                  "schema_version": 1,
                                  "realm_id": "removed_pack:lost_realm",
                                  "stage_id": "removed_pack:lost_stage",
                                  "cultivation_progress": 5,
                                  "stability": 6,
                                  "current_spiritual_power": 7,
                                  "spiritual_root": {
                                    "affinities_basis_points": {
                                      "removed_pack:lost_element": 10000
                                    }
                                  },
                                  "learned_techniques": {
                                    "removed_pack:lost_technique": {
                                      "mastery_points": 8
                                    }
                                  }
                                }
                                """))
                .getOrThrow();

        assertEquals(id("removed_pack", "lost_realm"), decoded.realmId());
        assertEquals(id("removed_pack", "lost_stage"), decoded.stageId());
        assertTrue(decoded.spiritualRoot().orElseThrow().affinitiesBasisPoints()
                .containsKey(id("removed_pack", "lost_element")));
        assertEquals(
                new TechniqueProgress(8),
                decoded.learnedTechniques().get(id("removed_pack", "lost_technique")));
    }

    @Test
    void profileCodecRejectsUnsupportedSchemaAndInvalidNumbers() {
        String profileJson = """
                {
                  "schema_version": %d,
                  "realm_id": "myvillage:mortal",
                  "stage_id": "myvillage:mortal_unawakened",
                  "cultivation_progress": %d,
                  "stability": %d,
                  "current_spiritual_power": %d,
                  "learned_techniques": {}
                }
                """;

        assertDecodeError(profileJson.formatted(2, 0, 0, 0));
        assertDecodeError(profileJson.formatted(1, -1, 0, 0));
        assertDecodeError(profileJson.formatted(1, 0, 101, 0));
        assertDecodeError(profileJson.formatted(1, 0, 0, -1));
    }

    private static void assertDecodeError(String json) {
        assertTrue(CultivationProfile.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .isError());
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static <T> T roundTrip(Codec<T> codec, T value) {
        return codec.parse(
                        JsonOps.INSTANCE,
                        codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow())
                .getOrThrow();
    }
}
