package com.example.myvillage.cultivation.observation;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.StatsRules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ObservationRulesTest {
    private final StatsRules stats = StatsRules.bundled();
    private final ObservationRules rules = ObservationRules.bundled();
    private RealmKey qi(int layer) { return stats.realmKey("qi_refining", layer - 1); }

    @Test void cultivationAutomaticallyGrantsObservationButMortalsCannotRead() {
        var mortal = stats.realmKey("mortal", 0);
        assertFalse(rules.unlocked(mortal));
        assertTrue(rules.unlocked(qi(1)));
        assertTrue(rules.unlocked(stats.realmKey("golden_core", 0)));
        assertThrows(IllegalArgumentException.class, () -> rules.read(mortal, qi(1)));
        assertFalse(rules.unlocked((com.example.myvillage.cultivation.CultivationProfile) null));
    }

    @Test void exactPrecisionUsesAdjacentMinorStagesIncludingMajorRealmBoundary() {
        for (int layer : new int[]{4, 5, 6}) {
            var reading = rules.read(qi(5), qi(layer));
            assertTrue(reading.exact());
            assertEquals(qi(layer), reading.minimum());
            assertEquals(reading.minimum(), reading.maximum());
        }
        var foundation = stats.realmKey("foundation_establishment", 0);
        assertTrue(rules.read(qi(9), foundation).exact());
        assertTrue(rules.read(foundation, qi(9)).exact());
        assertFalse(rules.read(qi(8), foundation).exact());
        assertFalse(rules.read(qi(5), qi(3)).exact());
        assertFalse(rules.read(qi(5), qi(7)).exact());
    }

    @Test void distantReadingsUseFixedIntervalsRatherThanRevealTheMiddleLayer() {
        var expected = new ObservationRules.Reading(false, qi(4), qi(6));
        for (int layer = 4; layer <= 6; layer++) assertEquals(expected, rules.read(qi(1), qi(layer)));
        var high = stats.realmKey("golden_core", 2);
        assertEquals(new ObservationRules.Reading(false, qi(7), qi(9)), rules.read(high, qi(9)));
        var mortal = stats.realmKey("mortal", 0);
        assertEquals(mortal, rules.read(high, mortal).minimum());
        for (var realm : stats.realms()) for (int stage = 0; stage < realm.stages(); stage++) {
            var target = stats.realmKey(realm.id(), stage);
            var reading = rules.read(qi(1), target);
            assertEquals(target.realmId(), reading.minimum().realmId());
            assertTrue(reading.minimum().stage() <= stage);
            assertTrue(reading.maximum().stage() >= stage);
            assertTrue(reading.maximum().stage() < realm.stages());
        }
    }

    private void rejects(Consumer<JsonObject> mutate) throws Exception {
        try (var input = getClass().getResourceAsStream("/" + ObservationRules.PATH)) {
            assertNotNull(input);
            var root = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            mutate.accept(root);
            assertThrows(IllegalArgumentException.class, () -> ObservationRules.parse(root.toString(), stats));
        }
    }
    @Test void invalidDataCannotEnableUnlimitedOrUncultivatedObservation() throws Exception {
        rejects(root -> root.addProperty("range", 65));
        rejects(root -> root.addProperty("range", "16"));
        rejects(root -> root.addProperty("cooldown_ticks", 0));
        rejects(root -> root.addProperty("request_interval_ticks", 21));
        rejects(root -> root.addProperty("exact_stage_distance", 1.5));
        rejects(root -> root.addProperty("coarse_stage_count", 1));
        rejects(root -> root.addProperty("unknown", true));
        rejects(root -> root.getAsJsonObject("unlock").addProperty("realm", "mortal"));
        rejects(root -> root.getAsJsonObject("unlock").addProperty("stage", 99));
    }
}
