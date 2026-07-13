package com.example.myvillage.cultivation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CultivationProfileTest {
    @Test
    void defaultProfileHasExactVersionThreeValues() {
        CultivationProfile profile = CultivationProfile.defaultProfile();

        assertEquals(3, profile.schemaVersion());
        assertEquals(id("myvillage", "mortal"), profile.realmId());
        assertEquals(id("myvillage", "mortal_unawakened"), profile.stageId());
        assertEquals(0L, profile.cultivationProgress());
        assertEquals(0, profile.stability());
        assertEquals(0L, profile.currentSpiritualPower());
        assertEquals(10, profile.spiritualAffinity());
        assertEquals(0L, profile.lifespanConsumedTicks());
        assertEquals(0L, profile.meditationQiReserve());
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
                3,
                id("addon", "lost_realm"),
                id("addon", "lost_stage"),
                9_876_543_210L,
                650,
                1_234_567_890L,
                27,
                432_100L,
                999L,
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
    void profileAcceptsStageScaledStabilityAndRejectsNegativeValues() {
        CultivationProfile profile = CultivationProfile.defaultProfile();

        assertThrows(IllegalArgumentException.class, () -> profile.withStability(-1));
        assertEquals(0, profile.withStability(0).stability());
        assertEquals(650, profile.withStability(650).stability());
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
        assertThrows(
                IllegalArgumentException.class,
                () -> profile.withSpiritualAffinity(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> profile.withLifespanConsumedTicks(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> profile.withMeditationQiReserve(-1));
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
                .withCurrentSpiritualPower(56)
                .withSpiritualAffinity(67)
                .withLifespanConsumedTicks(78)
                .withMeditationQiReserve(90);

        assertNotSame(oldProfile, newProfile);
        assertEquals(0L, oldProfile.cultivationProgress());
        assertEquals(0, oldProfile.stability());
        assertEquals(0L, oldProfile.currentSpiritualPower());
        assertEquals(10, oldProfile.spiritualAffinity());
        assertEquals(0L, oldProfile.lifespanConsumedTicks());
        assertEquals(0L, oldProfile.meditationQiReserve());
        assertEquals(12L, newProfile.cultivationProgress());
        assertEquals(34, newProfile.stability());
        assertEquals(56L, newProfile.currentSpiritualPower());
        assertEquals(67, newProfile.spiritualAffinity());
        assertEquals(78L, newProfile.lifespanConsumedTicks());
        assertEquals(90L, newProfile.meditationQiReserve());
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
                3,
                CultivationProfile.DEFAULT_REALM_ID,
                CultivationProfile.DEFAULT_STAGE_ID,
                0,
                0,
                0,
                13,
                11,
                12,
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
                                  "cultivation_progress": 9876543210,
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
        assertEquals(9_876_543_210L, decoded.cultivationProgress());
        assertEquals(3, decoded.schemaVersion());
        assertEquals(10, decoded.spiritualAffinity());
        assertEquals(0L, decoded.lifespanConsumedTicks());
        assertEquals(0L, decoded.meditationQiReserve());
        assertTrue(decoded.spiritualRoot().orElseThrow().affinitiesBasisPoints()
                .containsKey(id("removed_pack", "lost_element")));
        assertEquals(
                new TechniqueProgress(8),
                decoded.learnedTechniques().get(id("removed_pack", "lost_technique")));

        JsonObject reencoded = CultivationProfile.CODEC
                .encodeStart(JsonOps.INSTANCE, decoded)
                .getOrThrow()
                .getAsJsonObject();
        assertEquals(3, reencoded.get("schema_version").getAsInt());
        assertEquals(10, reencoded.get("spiritual_affinity").getAsInt());
        assertEquals(0, reencoded.get("lifespan_consumed_ticks").getAsLong());
        assertEquals(0, reencoded.get("meditation_qi_reserve").getAsLong());
    }

    @Test
    void versionTwoMigrationPreservesAllLegacyValuesAndEncodesOnlyVersionThree() {
        CultivationProfile decoded = CultivationProfile.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString("""
                                {
                                  "schema_version": 2,
                                  "realm_id": "removed_pack:lost_realm",
                                  "stage_id": "removed_pack:lost_stage",
                                  "cultivation_progress": 1234,
                                  "stability": 56,
                                  "current_spiritual_power": 78,
                                  "lifespan_consumed_ticks": 9012,
                                  "meditation_qi_reserve": 345,
                                  "spiritual_root": {
                                    "affinities_basis_points": {
                                      "removed_pack:lost_element": 10000
                                    }
                                  },
                                  "learned_techniques": {
                                    "removed_pack:lost_technique": {
                                      "mastery_points": 67
                                    }
                                  }
                                }
                                """))
                .getOrThrow();

        assertEquals(3, decoded.schemaVersion());
        assertEquals(10, decoded.spiritualAffinity());
        assertEquals(1_234, decoded.cultivationProgress());
        assertEquals(56, decoded.stability());
        assertEquals(78, decoded.currentSpiritualPower());
        assertEquals(9_012, decoded.lifespanConsumedTicks());
        assertEquals(345, decoded.meditationQiReserve());
        assertEquals(new TechniqueProgress(67),
                decoded.learnedTechniques().get(id("removed_pack", "lost_technique")));

        JsonObject reencoded = CultivationProfile.CODEC
                .encodeStart(JsonOps.INSTANCE, decoded)
                .getOrThrow()
                .getAsJsonObject();
        assertEquals(3, reencoded.get("schema_version").getAsInt());
        assertEquals(10, reencoded.get("spiritual_affinity").getAsInt());
        assertEquals(9_012, reencoded.get("lifespan_consumed_ticks").getAsLong());
        assertEquals(345, reencoded.get("meditation_qi_reserve").getAsLong());
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
                  "spiritual_affinity": %d,
                  "lifespan_consumed_ticks": %d,
                  "meditation_qi_reserve": %d,
                  "learned_techniques": {}
                }
                """;

        assertDecodeError(profileJson.formatted(4, 0, 0, 0, 10, 0, 0));
        assertDecodeError(profileJson.formatted(3, -1, 0, 0, 10, 0, 0));
        assertDecodeError(profileJson.formatted(3, 0, -1, 0, 10, 0, 0));
        assertDecodeError(profileJson.formatted(3, 0, 0, -1, 10, 0, 0));
        assertDecodeError(profileJson.formatted(3, 0, 0, 0, -1, 0, 0));
        assertDecodeError(profileJson.formatted(3, 0, 0, 0, 10, -1, 0));
        assertDecodeError(profileJson.formatted(3, 0, 0, 0, 10, 0, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CultivationProfile(
                        1,
                        CultivationProfile.DEFAULT_REALM_ID,
                        CultivationProfile.DEFAULT_STAGE_ID,
                        0,
                        0,
                        0,
                        10,
                        0,
                        0,
                        Optional.empty(),
                        Map.of()));
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
