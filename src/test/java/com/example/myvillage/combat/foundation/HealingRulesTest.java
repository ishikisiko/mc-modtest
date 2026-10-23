package com.example.myvillage.combat.foundation;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HealingRulesTest {
    private JsonObject authored() throws Exception {
        try (var in = getClass().getResourceAsStream("/data/myvillage/combat/foundation/healing.json")) {
            assertNotNull(in);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test void packagedAuthoredConfigurationParses() throws Exception {
        assertEquals(HealingRules.get(), HealingRules.parse(authored()));
        assertEquals(20, HealingRules.get().lifestealWindowTicks());
    }

    @Test void invalidLimitsAndFractionalCapacitiesAreRejected() throws Exception {
        for (String key : new String[]{"maximum_tracked_entities", "maximum_damage_receipts", "maximum_sources_per_entity",
                "maximum_duration_ticks", "maximum_healing_per_call", "maximum_healing_multiplier"}) {
            var json = authored();
            json.addProperty(key, 0);
            assertThrows(IllegalArgumentException.class, () -> HealingRules.parse(json));
        }
        var fractional = authored();
        fractional.addProperty("maximum_sources_per_entity", 1.5);
        assertThrows(IllegalArgumentException.class, () -> HealingRules.parse(fractional));
        var reduction = authored();
        reduction.addProperty("maximum_reduction_fraction", 1.1);
        assertThrows(IllegalArgumentException.class, () -> HealingRules.parse(reduction));
    }

    @Test void missingStringAndNonFiniteValuesCannotStartTheRuntime() throws Exception {
        var missing = authored(); missing.remove("maximum_healing_per_call");
        assertThrows(IllegalArgumentException.class, () -> HealingRules.parse(missing));
        var string = authored(); string.addProperty("maximum_healing_per_call", "1");
        assertThrows(IllegalArgumentException.class, () -> HealingRules.parse(string));
        var nan = authored(); nan.addProperty("maximum_healing_per_call", Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> HealingRules.parse(nan));
        var infinity = authored(); infinity.addProperty("maximum_bonus_fraction", Double.POSITIVE_INFINITY);
        assertThrows(IllegalArgumentException.class, () -> HealingRules.parse(infinity));
        var duration = authored(); duration.addProperty("maximum_duration_ticks", Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> HealingRules.parse(duration));
    }

    @Test void oneSecondWindowCannotBeRetunedIntoAnUnboundedLifestealRate() throws Exception {
        var shorter = authored(); shorter.addProperty("lifesteal_window_ticks", 1);
        assertThrows(IllegalArgumentException.class, () -> HealingRules.parse(shorter));
        var schema = authored(); schema.addProperty("schema", 2);
        assertThrows(IllegalArgumentException.class, () -> HealingRules.parse(schema));
    }
}
