package com.example.myvillage.entity.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.PersonView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 命簿所有境界都使用保留组合，身份稳定且不回退旧外观。 */
final class CultivatorLooksTest {
    private static final List<String> REALMS =
            List.of("qi_refining", "foundation_establishment", "golden_core", "nascent_soul");
    // CultivatorEntity.LOOKS without initialising the entity class (that needs the game's registries).
    private static final List<String> LOOKS = CultivatorVariants.looks();

    @Test
    void aWomanUsesAnApprovedCombination() {
        assertEquals(CultivatorVariants.forIdentity(1, -100), CultivatorLooks.forPerson(person("f", "qi_refining"), REALMS));
    }

    @Test
    void promotionKeepsAWomansApprovedCombinationInsteadOfTheLegacyAdept() {
        for (String realm : REALMS.subList(1, REALMS.size())) {
            assertEquals(CultivatorVariants.forIdentity(1,-100), CultivatorLooks.forPerson(person("f", realm), REALMS), realm);
        }
    }

    @Test
    void anUnknownOrEmptyRealmCountsAsTheLowest() {
        assertEquals(CultivatorVariants.forIdentity(1, -100), CultivatorLooks.forPerson(person("f", "mortal"), REALMS));
        assertEquals(CultivatorVariants.forIdentity(1, -100), CultivatorLooks.forPerson(person("f", ""), REALMS));
    }

    @Test
    void menAndMissingGenderUseStableGeneratedMaleCombinations() {
        for (String realm : REALMS) {
            assertEquals(CultivatorVariants.forMaleIdentity(1,-100), CultivatorLooks.forPerson(person("m", realm), REALMS), realm);
        }
        assertEquals(CultivatorVariants.forMaleIdentity(1,-100), CultivatorLooks.forPerson(person("", "golden_core"), REALMS));
    }

    @Test
    void everyAnswerIsALookTheCultivatorLists() {
        assertEquals(CultivatorVariants.legacyLooks(), List.of(CultivatorLooks.DEFAULT, CultivatorLooks.FEMALE_NOVICE, CultivatorLooks.FEMALE_ADEPT));
        assertEquals(NpcEntity.LOOK_DEFAULT, CultivatorLooks.DEFAULT);
        for (String gender : List.of("m", "f", "")) {
            for (String realm : REALMS) {
                String look=CultivatorLooks.forPerson(person(gender, realm), REALMS);
                assertTrue(LOOKS.contains(look));
                assertTrue(!CultivatorVariants.legacyLooks().contains(look));
            }
        }
    }

    private static PersonView person(String gender, String realm) {
        return new PersonView(1, "林一", "", gender, true, -100, -1, "", -1, List.of(2000, 2000, 2000, 2000, 2000),
                "mixed", realm, 0, 0.0, 1, "青云宗", "outer", -1, "r0", "at_sect", "", "", 0, List.of(50, 50, 50, 50, 50), List.of());
    }
}
