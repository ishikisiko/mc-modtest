package com.example.myvillage.cultivation.technique;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueRequirements;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechniqueRequirementEvaluatorTest {
    private static final Map<ResourceLocation, RealmDefinition> REALMS = realms();

    @Test
    void basicBreathingRequirementsComeFromDefinitionAndAcceptAnyRootCount() {
        TechniqueDefinition definition = basicBreathing();
        assertEquals(Optional.of(ModCultivationRegistries.MORTAL_REALM_ID),
                definition.requirements().minimumRealm());
        assertEquals(Optional.of(ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID),
                definition.requirements().minimumStage());
        assertTrue(definition.requirements().minimumElementAffinity().isEmpty());

        for (int count = 1; count <= 5; count++) {
            assertTrue(TechniqueRequirementEvaluator.evaluate(
                    sensedProfile(root(count)), definition, REALMS::get).satisfied());
        }
    }

    @Test
    void lowerStageAndAmbiguousRealmOrderingFailClosed() {
        CultivationProfile unawakenedStage = CultivationProfile.defaultProfile()
                .withSpiritualRoot(root(1));
        assertEquals(
                TechniqueRequirementEvaluator.Status.STAGE_TOO_LOW,
                TechniqueRequirementEvaluator.evaluate(
                        unawakenedStage, basicBreathing(), REALMS::get).status());

        ResourceLocation ambiguousId = id("ambiguous");
        Map<ResourceLocation, RealmDefinition> ambiguous = new LinkedHashMap<>(REALMS);
        ambiguous.put(ambiguousId, new RealmDefinition(
                "realm.ambiguous",
                0,
                List.of(new RealmStageDefinition(id("ambiguous_stage"), "stage.ambiguous", 0)),
                Optional.empty()));
        CultivationProfile ambiguousProfile = new CultivationProfile(
                1, ambiguousId, id("ambiguous_stage"), 0, 0, 0, Optional.of(root(1)), Map.of());
        assertEquals(
                TechniqueRequirementEvaluator.Status.AMBIGUOUS_REALM_ORDER,
                TechniqueRequirementEvaluator.evaluate(
                        ambiguousProfile, basicBreathing(), ambiguous::get).status());
    }

    @Test
    void minimumAffinityUsesDefinitionAndFailsWhenMissingOrLow() {
        TechniqueDefinition fireTechnique = new TechniqueDefinition(
                "technique.fire",
                TechniqueCategory.CORE,
                0,
                List.of(),
                new TechniqueRequirements(
                        Optional.empty(), Optional.empty(), Map.of(id("fire"), 6_000)));
        CultivationProfile low = sensedProfile(new SpiritualRoot(Map.of(id("fire"), 5_999, id("water"), 4_001)));
        CultivationProfile enough = sensedProfile(new SpiritualRoot(Map.of(id("fire"), 6_000, id("water"), 4_000)));

        assertEquals(
                TechniqueRequirementEvaluator.Status.AFFINITY_TOO_LOW,
                TechniqueRequirementEvaluator.evaluate(low, fireTechnique, REALMS::get).status());
        assertTrue(TechniqueRequirementEvaluator.evaluate(enough, fireTechnique, REALMS::get).satisfied());
        assertFalse(TechniqueRequirementEvaluator.evaluate(
                CultivationProfile.defaultProfile(), fireTechnique, REALMS::get).satisfied());
    }

    @Test
    void missingRealmDefinitionFailsClosed() {
        assertEquals(
                TechniqueRequirementEvaluator.Status.DEFINITION_UNAVAILABLE,
                TechniqueRequirementEvaluator.evaluate(
                        sensedProfile(root(1)), basicBreathing(), id -> null).status());
    }

    @Test
    void higherRealmStillRejectsAMissingMinimumStageDefinition() {
        ResourceLocation missingStage = id("missing_stage");
        TechniqueDefinition invalid = new TechniqueDefinition(
                "technique.invalid_stage",
                TechniqueCategory.CORE,
                0,
                List.of(),
                new TechniqueRequirements(
                        Optional.of(ModCultivationRegistries.MORTAL_REALM_ID),
                        Optional.of(missingStage),
                        Map.of()));
        CultivationProfile higherRealm = new CultivationProfile(
                1,
                ModCultivationRegistries.QI_REFINING_REALM_ID,
                ModCultivationRegistries.QI_REFINING_1_STAGE_ID,
                0,
                0,
                0,
                Optional.of(root(1)),
                Map.of());

        assertEquals(
                TechniqueRequirementEvaluator.Status.DEFINITION_UNAVAILABLE,
                TechniqueRequirementEvaluator.evaluate(
                        higherRealm, invalid, REALMS::get).status());
    }

    @Test
    void missingRequiredElementDefinitionFailsClosed() {
        TechniqueDefinition fireTechnique = new TechniqueDefinition(
                "technique.fire",
                TechniqueCategory.CORE,
                0,
                List.of(),
                new TechniqueRequirements(
                        Optional.empty(), Optional.empty(), Map.of(id("fire"), 1)));
        CultivationProfile profile = sensedProfile(
                new SpiritualRoot(Map.of(id("fire"), 10_000)));

        assertEquals(
                TechniqueRequirementEvaluator.Status.DEFINITION_UNAVAILABLE,
                TechniqueRequirementEvaluator.evaluate(
                        profile, fireTechnique, REALMS::get, ignored -> false).status());
    }

    static TechniqueDefinition basicBreathing() {
        return new TechniqueDefinition(
                "cultivation.technique.myvillage.basic_breathing",
                TechniqueCategory.CORE,
                0,
                List.of(),
                new TechniqueRequirements(
                        Optional.of(ModCultivationRegistries.MORTAL_REALM_ID),
                        Optional.of(ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID),
                        Map.of()));
    }

    static CultivationProfile sensedProfile(SpiritualRoot root) {
        return CultivationProfile.defaultProfile()
                .withRealmAndStage(
                        ModCultivationRegistries.MORTAL_REALM_ID,
                        ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID)
                .withSpiritualRoot(root);
    }

    static SpiritualRoot root(int count) {
        LinkedHashMap<ResourceLocation, Integer> values = new LinkedHashMap<>();
        int share = 10_000 / count;
        int residue = 10_000 - share * count;
        for (int index = 0; index < count; index++) {
            values.put(id("element_" + index), share + (index < residue ? 1 : 0));
        }
        return new SpiritualRoot(values);
    }

    private static Map<ResourceLocation, RealmDefinition> realms() {
        RealmDefinition mortal = new RealmDefinition(
                "realm.mortal",
                0,
                List.of(
                        new RealmStageDefinition(
                                ModCultivationRegistries.MORTAL_UNAWAKENED_STAGE_ID,
                                "stage.unawakened",
                                0),
                        new RealmStageDefinition(
                                ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID,
                                "stage.sensed",
                                1)),
                Optional.of(ModCultivationRegistries.QI_REFINING_REALM_ID));
        RealmDefinition qiRefining = new RealmDefinition(
                "realm.qi_refining",
                1,
                List.of(new RealmStageDefinition(
                        ModCultivationRegistries.QI_REFINING_1_STAGE_ID,
                        "stage.qi_1",
                        0)),
                Optional.empty());
        return Map.of(
                ModCultivationRegistries.MORTAL_REALM_ID, mortal,
                ModCultivationRegistries.QI_REFINING_REALM_ID, qiRefining);
    }

    static RealmDefinition realm(ResourceLocation id) {
        return REALMS.get(id);
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }
}
