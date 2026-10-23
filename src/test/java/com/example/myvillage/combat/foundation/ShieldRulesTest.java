package com.example.myvillage.combat.foundation;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ShieldRulesTest {
    private static JsonObject valid() {
        return JsonParser.parseString("""
                {"schema":1,"max_sources":2,"max_per_source":10,"max_total":15,
                 "max_duration_ticks":100,"max_tracked_entities":8}
                """).getAsJsonObject();
    }

    @Test void authoredConfigurationLoadsAndAllTuningComesFromTheDocument() {
        assertEquals(new ShieldRules(2, 10, 15, 100, 8), ShieldRules.parse(valid()));
        var bundled = ShieldRules.get();
        assertTrue(bundled.maxSources() > 0);
        assertTrue(bundled.maxPerSource() <= bundled.maxTotal());
        assertTrue(bundled.maxDurationTicks() > 0);
        assertTrue(bundled.maxTrackedEntities() > 0);
    }

    @Test void fractionalMissingAndStringValuesAreRejected() {
        for (String key : new String[] {"schema", "max_sources", "max_per_source", "max_total",
                "max_duration_ticks", "max_tracked_entities"}) {
            var missing = valid();
            missing.remove(key);
            assertThrows(IllegalArgumentException.class, () -> ShieldRules.parse(missing));
            var text = valid();
            text.addProperty(key, "10");
            assertThrows(IllegalArgumentException.class, () -> ShieldRules.parse(text));
        }
        for (String key : new String[] {"schema", "max_sources", "max_duration_ticks", "max_tracked_entities"}) {
            var fractional = valid();
            fractional.addProperty(key, 1.5);
            assertThrows(IllegalArgumentException.class, () -> ShieldRules.parse(fractional));
        }
    }

    @Test void invalidAndUnboundedLimitsCannotCreateBenefits() {
        for (String key : new String[] {"max_sources", "max_per_source", "max_total",
                "max_duration_ticks", "max_tracked_entities"}) {
            for (double value : new double[] {-1, 0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE}) {
                var invalid = valid();
                invalid.addProperty(key, value);
                assertThrows(IllegalArgumentException.class, () -> ShieldRules.parse(invalid));
            }
        }
        var wrongVersion = valid();
        wrongVersion.addProperty("schema", 2);
        assertThrows(IllegalArgumentException.class, () -> ShieldRules.parse(wrongVersion));
        assertThrows(IllegalArgumentException.class, () -> new ShieldRules(2, 16, 15, 100, 8));
        assertThrows(IllegalArgumentException.class, () -> new ShieldRules(129, 10, 15, 100, 8));
        assertThrows(IllegalArgumentException.class, () -> new ShieldRules(2, 10, 15, 1_728_001, 8));
        assertThrows(IllegalArgumentException.class, () -> new ShieldRules(2, 10, 15, 100, 65_537));
    }
}
