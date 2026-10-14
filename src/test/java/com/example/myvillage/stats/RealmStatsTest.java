package com.example.myvillage.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RealmStatsTest {
    private final StatsRules rules = StatsRules.bundled();

    @Test void mortalStagesStayAtBaseline() {
        assertEquals(1, rules.growth(rules.playerKey("mortal", "mortal_unawakened")));
        assertEquals(1, rules.growth(rules.playerKey("myvillage:mortal", "myvillage:mortal_qi_sensed")));
    }

    @Test void minorStagesAreLinearAndMajorEntriesExponential() {
        double delta = rules.entryMultiplier() * rules.minorSlope();
        for (int stage = 1; stage < 9; stage++)
            assertEquals(delta, rules.growth(new RealmKey("qi_refining", stage))
                    - rules.growth(new RealmKey("qi_refining", stage - 1)), 1e-12);
        String[] realms = {"qi_refining", "foundation_establishment", "golden_core", "nascent_soul"};
        for (int index = 1; index < realms.length; index++) {
            for (int stage = 0; stage < 3; stage++)
                assertEquals(rules.majorRatio(), rules.growth(new RealmKey(realms[index], stage))
                        / rules.growth(new RealmKey(realms[index - 1], stage)), 1e-12);
        }
    }

    @Test void everyShippedBoundaryIncreasesAndNativeCeilingsAreRespected() {
        double last = 0;
        for (StatsRules.RealmDefinition realm : rules.realms()) {
            double first = rules.growth(new RealmKey(realm.id(), 0));
            assertTrue(realm.mortal() || first > last);
            for (int stage = 0; stage < realm.stages(); stage++) {
                RealmStats.Derived stats = RealmStats.derived(rules, new RealmKey(realm.id(), stage), 70, 16);
                assertTrue(stats.health() <= 1024);
                assertTrue(stats.attack() <= 2048);
            }
            last = rules.growth(new RealmKey(realm.id(), realm.stages() - 1));
        }
        RealmStats.Derived highest = RealmStats.derived(rules, new RealmKey("nascent_soul", 2), 70, 4);
        assertEquals(13.2, highest.factor(), 1e-12);
        assertEquals(924, highest.health(), 1e-12);
        assertEquals(52.8, highest.attack(), 1e-12);
    }

    @Test void explicitAliasesDoNotParsePlayerSuffixesOrIgnoreRealmMismatch() {
        assertEquals(new RealmKey("foundation_establishment", 0), rules.playerKey("foundation_establishment", "foundation_early"));
        for (int stage = 1; stage <= 9; stage++)
            assertEquals(new RealmKey("qi_refining", stage - 1), rules.playerKey("qi_refining", "qi_refining_" + stage));
        assertThrows(IllegalArgumentException.class, () -> rules.playerKey("qi_refining", "qi_refining_10"));
        assertThrows(IllegalArgumentException.class, () -> rules.playerKey("mortal", "qi_refining_1"));
        assertThrows(IllegalArgumentException.class, () -> rules.realmKey("qi_refining", 9));
        assertThrows(IllegalArgumentException.class, () -> rules.realmKey("unknown", 0));
        assertThrows(IllegalArgumentException.class, () -> new RealmKey("qi_refining", -1));
        assertThrows(IllegalArgumentException.class, () -> new RealmKey("Qi Ref", 0));
    }

    @Test void unsupportedBaseStatsAreRejectedInsteadOfClipped() {
        RealmKey realm = new RealmKey("mortal", 0);
        assertThrows(IllegalArgumentException.class, () -> RealmStats.derived(rules, realm, 71, 4));
        assertThrows(IllegalArgumentException.class, () -> RealmStats.derived(rules, realm, 70, 17));
        assertThrows(IllegalArgumentException.class, () -> RealmStats.derived(rules, realm, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> RealmStats.derived(rules, realm, Double.NaN, 4));
    }
}
