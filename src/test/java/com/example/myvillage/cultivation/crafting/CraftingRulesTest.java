package com.example.myvillage.cultivation.crafting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.StringReader;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftingRulesTest {
    private static final String RULES = """
            {"version":1,"initial_roll":{"min":0,"max":100},"quality_thresholds":[
             {"quality":1,"minimum":0},{"quality":2,"minimum":40},
             {"quality":3,"minimum":70},{"quality":4,"minimum":90}]}
            """;
    private static void rejects(Consumer<JsonObject> mutation) {
        JsonObject json = JsonParser.parseString(RULES).getAsJsonObject(); mutation.accept(json);
        assertThrows(IllegalArgumentException.class, () -> CraftingRules.parse(new StringReader(json.toString())));
    }
    @Test void everyScoreMapsToExactSameRecipeQualityCeiling() {
        var rules = CraftingRules.get();
        for (int score = 0; score <= 100; score++) {
            int expected = score < 40 ? 1 : score < 70 ? 2 : score < 90 ? 3 : 4;
            assertEquals(expected, rules.qualityCap(score), "score=" + score);
            for (int quality = 0; quality <= 5; quality++)
                assertEquals(quality >= 1 && quality <= expected, rules.allowsQuality(score, quality));
        }
        assertThrows(IllegalArgumentException.class, () -> rules.qualityCap(-1));
        assertThrows(IllegalArgumentException.class, () -> rules.qualityCap(101));
    }
    @Test void rangeAndThresholdBalanceAreActuallyDataDriven() {
        JsonObject json = JsonParser.parseString(RULES).getAsJsonObject();
        json.getAsJsonObject("initial_roll").addProperty("min", 10);
        json.getAsJsonObject("initial_roll").addProperty("max", 15);
        json.getAsJsonArray("quality_thresholds").get(1).getAsJsonObject().addProperty("minimum", 20);
        var rules = CraftingRules.parse(new StringReader(json.toString()));
        assertEquals(10, rules.initialMin()); assertEquals(15, rules.initialMax());
        assertEquals(2, rules.qualityCap(20)); assertEquals(1, rules.qualityCap(19));
    }
    @Test void strictSchemaRejectsBrokenBoundsOrderTypesAndUnknownFields() {
        rejects(j -> j.addProperty("version", 2));
        rejects(j -> j.addProperty("extra", true));
        rejects(j -> j.remove("initial_roll"));
        rejects(j -> j.getAsJsonObject("initial_roll").addProperty("min", -1));
        rejects(j -> j.getAsJsonObject("initial_roll").addProperty("max", 101));
        rejects(j -> {
            j.getAsJsonObject("initial_roll").addProperty("min", 100);
            j.getAsJsonObject("initial_roll").addProperty("max", 99);
        });
        rejects(j -> j.getAsJsonObject("initial_roll").addProperty("max", 99.5));
        rejects(j -> j.getAsJsonArray("quality_thresholds").remove(3));
        rejects(j -> j.getAsJsonArray("quality_thresholds").get(0).getAsJsonObject().addProperty("minimum", 1));
        rejects(j -> j.getAsJsonArray("quality_thresholds").get(1).getAsJsonObject().addProperty("quality", 1));
        rejects(j -> j.getAsJsonArray("quality_thresholds").get(2).getAsJsonObject().addProperty("minimum", 39));
        rejects(j -> j.getAsJsonArray("quality_thresholds").get(3).getAsJsonObject().addProperty("minimum", "90"));
        rejects(j -> j.getAsJsonArray("quality_thresholds").get(3).getAsJsonObject().addProperty("minimum", 101));
    }
    @Test void duplicateFieldsAndTrailingDataCannotSilentlyOverrideRules() {
        assertThrows(IllegalArgumentException.class,
                () -> CraftingRules.parse(new StringReader(RULES.replace("\"version\":1", "\"version\":1,\"version\":1"))));
        assertThrows(IllegalArgumentException.class, () -> CraftingRules.parse(new StringReader(RULES + " {}")));
    }
}
