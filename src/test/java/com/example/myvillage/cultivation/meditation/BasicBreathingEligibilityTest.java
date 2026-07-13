package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueRequirements;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicBreathingEligibilityTest {
    @Test
    void currentDefinitionRequirementsAreRecheckedForPractice() {
        Map<ResourceLocation, RealmDefinition> realms = realms();
        TechniqueDefinition breathing = basicBreathing();
        CultivationProfile sensed = CultivationProfile.defaultProfile().withRealmAndStage(
                ModCultivationRegistries.MORTAL_REALM_ID,
                ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID);
        CultivationProfile qiOne = sensed.withRealmAndStage(
                ModCultivationRegistries.QI_REFINING_REALM_ID,
                ModCultivationRegistries.QI_REFINING_1_STAGE_ID);

        assertFalse(BasicBreathingEligibility.requirementsSatisfied(
                CultivationProfile.defaultProfile(), breathing, realms::get, ignored -> true));
        assertTrue(BasicBreathingEligibility.requirementsSatisfied(
                sensed, breathing, realms::get, ignored -> true));
        assertTrue(BasicBreathingEligibility.requirementsSatisfied(
                qiOne, breathing, realms::get, ignored -> true));
        assertFalse(BasicBreathingEligibility.requirementsSatisfied(
                sensed, breathing, ignored -> null, ignored -> true));
    }

    private static TechniqueDefinition basicBreathing() {
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

    private static Map<ResourceLocation, RealmDefinition> realms() {
        RealmDefinition mortal = new RealmDefinition(
                "realm.mortal",
                0,
                80,
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
        RealmDefinition qi = new RealmDefinition(
                "realm.qi_refining",
                1,
                120,
                List.of(new RealmStageDefinition(
                        ModCultivationRegistries.QI_REFINING_1_STAGE_ID,
                        "stage.qi_1",
                        0)),
                Optional.empty());
        return Map.of(
                ModCultivationRegistries.MORTAL_REALM_ID, mortal,
                ModCultivationRegistries.QI_REFINING_REALM_ID, qi);
    }
}
