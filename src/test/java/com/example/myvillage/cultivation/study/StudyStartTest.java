package com.example.myvillage.cultivation.study;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static com.example.myvillage.cultivation.study.StudyFixtures.FIRE;
import static com.example.myvillage.cultivation.study.StudyFixtures.HUANG;
import static com.example.myvillage.cultivation.study.StudyFixtures.METAL;
import static com.example.myvillage.cultivation.study.StudyFixtures.REALMS;
import static com.example.myvillage.cultivation.study.StudyFixtures.TECHNIQUES;
import static com.example.myvillage.cultivation.study.StudyFixtures.UNKNOWN;
import static com.example.myvillage.cultivation.study.StudyFixtures.XUAN;
import static com.example.myvillage.cultivation.study.StudyFixtures.chainCultivator;
import static com.example.myvillage.cultivation.study.StudyFixtures.cultivator;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StudyStartTest {
    @Test
    void aValidManualOfALearnableTechniqueWithNoSessionMayStart() {
        assertEquals(Optional.empty(), refusal(Optional.of(XUAN), chainCultivator(0), false));
        assertEquals(Optional.empty(), refusal(Optional.of(HUANG), cultivator(0), false));
    }

    @Test
    void anInvalidManualIsRefusedFirst() {
        assertEquals(Optional.of(StudyStart.Refusal.INVALID_MANUAL),
                refusal(Optional.empty(), CultivationProfile.defaultProfile(), true));
        assertEquals(Optional.of(StudyStart.Refusal.INVALID_MANUAL),
                refusal(Optional.of(UNKNOWN), chainCultivator(0), true));
    }

    @Test
    void anAlreadyLearnedTechniqueIsRefusedBeforeTheRoot() {
        CultivationProfile learnedButRootless = chainCultivator(0).withSpiritualRoot(Optional.empty());
        assertEquals(Optional.of(StudyStart.Refusal.ALREADY_LEARNED),
                refusal(Optional.of(HUANG), learnedButRootless, true));
    }

    @Test
    void anUnawakenedCultivatorIsRefused() {
        assertEquals(Optional.of(StudyStart.Refusal.NOT_AWAKENED),
                refusal(Optional.of(HUANG), CultivationProfile.defaultProfile(), true));
    }

    @Test
    void theTechniqueRequirementsAreChecked() {
        CultivationProfile mortal = chainCultivator(0).withRealmAndStage(
                ModCultivationRegistries.MORTAL_REALM_ID, ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID);
        assertEquals(Optional.of(StudyStart.Refusal.REALM_TOO_LOW),
                refusal(Optional.of(XUAN), mortal, true));

        CultivationProfile fire = chainCultivator(0).withSpiritualRoot(
                new SpiritualRoot(Map.of(METAL, 1_499, FIRE, 8_501)));
        assertEquals(Optional.of(StudyStart.Refusal.AFFINITY_TOO_LOW),
                refusal(Optional.of(XUAN), fire, true));

        assertEquals(Optional.of(StudyStart.Refusal.REQUIREMENTS_UNAVAILABLE),
                StudyStart.refusal(Optional.of(XUAN), chainCultivator(0), TECHNIQUES::get, id -> null,
                        id -> true, true));
    }

    @Test
    void thePreviousTechniqueOfTheChainMustBeLearned() {
        assertEquals(Optional.of(StudyStart.Refusal.PREVIOUS_REQUIRED),
                refusal(Optional.of(XUAN), cultivator(0), true));
    }

    @Test
    void aRunningSessionIsRefusedLast() {
        assertEquals(Optional.of(StudyStart.Refusal.BUSY),
                refusal(Optional.of(XUAN), chainCultivator(0), true));
    }

    @Test
    void learnRefusalIsTheSameChainWithoutTheManualAndSession() {
        CultivationProfile learned = chainCultivator(0).learnTechnique(XUAN, TechniqueCategory.ACTIVE);
        assertEquals(Optional.of(StudyStart.Refusal.ALREADY_LEARNED),
                StudyStart.learnRefusal(XUAN, learned, TECHNIQUES::get, REALMS::get, id -> true));
        assertEquals(Optional.empty(),
                StudyStart.learnRefusal(XUAN, chainCultivator(0), TECHNIQUES::get, REALMS::get, id -> true));
    }

    @Test
    void refusalKeysAreTheStudyMessageKeys() {
        assertEquals("message.myvillage.cultivation.study.previous_required",
                StudyMessages.key(StudyStart.Refusal.PREVIOUS_REQUIRED));
        assertEquals("message.myvillage.cultivation.study.invalid_manual",
                StudyMessages.key(StudyStart.Refusal.INVALID_MANUAL));
    }

    private static Optional<StudyStart.Refusal> refusal(
            Optional<ResourceLocation> manual, CultivationProfile profile, boolean busy) {
        Map<ResourceLocation, TechniqueDefinition> techniques = TECHNIQUES;
        return StudyStart.refusal(manual, profile, techniques::get, REALMS::get, id -> true, busy);
    }
}
