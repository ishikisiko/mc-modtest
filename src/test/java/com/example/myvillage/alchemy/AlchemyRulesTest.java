package com.example.myvillage.alchemy;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlchemyRulesTest {
    private String source() throws IOException {
        try (var stream = getClass().getResourceAsStream(AlchemyRules.RESOURCE)) {
            assertNotNull(stream); return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private void rejects(Consumer<JsonObject> mutation) throws IOException {
        JsonObject json = JsonParser.parseString(source()).getAsJsonObject(); mutation.accept(json);
        assertThrows(IllegalArgumentException.class, () -> AlchemyRules.parse(new StringReader(json.toString())));
    }
    @Test void bundledRecipesAreImmutableAndUnorderedButRequireFormulaAndBothHerbs() {
        AlchemyRules rules = AlchemyRules.get();
        assertSame(rules, AlchemyRules.get()); assertEquals(8, rules.recipes().size());
        for (var recipe : rules.recipes().stream().filter(r -> r.fixedQuality()==0).toList()) {
            assertTrue(recipe.matches(recipe.formula(), recipe.ingredients().get(0), recipe.ingredients().get(1)));
            assertTrue(recipe.matches(recipe.formula(), recipe.ingredients().get(1), recipe.ingredients().get(0)));
            assertFalse(recipe.matches(recipe.formula(), recipe.ingredients().get(0), recipe.ingredients().get(0)));
            assertFalse(recipe.matches("", recipe.ingredients().get(0), recipe.ingredients().get(1)));
            assertEquals(recipe, rules.recipeForFormula(recipe.formula()).orElseThrow());
            assertThrows(UnsupportedOperationException.class, () -> recipe.ingredients().clear());
        }
        assertThrows(UnsupportedOperationException.class, () -> rules.recipes().clear());
    }
    @Test void rejectsUnknownMissingDuplicateFractionalAndMisTypedData() throws Exception {
        rejects(j -> j.addProperty("extra", 1)); rejects(j -> j.remove("effects"));
        rejects(j -> j.addProperty("use_ticks", true)); rejects(j -> j.addProperty("use_ticks", "32"));
        rejects(j -> j.addProperty("use_ticks", 1.2)); rejects(j -> j.addProperty("shared_cooldown_ticks", 0));
        assertThrows(IllegalArgumentException.class, () -> AlchemyRules.parse(new StringReader(source().replaceFirst("\\{", "{\"version\":1,"))));
        assertThrows(IllegalArgumentException.class, () -> AlchemyRules.parse(new StringReader(source() + "{}")));
    }
    @Test void rejectsRecipeAliasesInvalidSlotsAndBounds() throws IOException {
        rejects(j -> j.getAsJsonArray("recipes").get(0).getAsJsonObject().addProperty("duration_ticks", 32768));
        rejects(j -> j.getAsJsonArray("recipes").get(0).getAsJsonObject().addProperty("output_count", 17));
        rejects(j -> j.getAsJsonArray("recipes").get(0).getAsJsonObject().addProperty("fuel_count", -1));
        rejects(j -> j.getAsJsonArray("recipes").get(0).getAsJsonObject().addProperty("formula", "myvillage:formula_yangyuan"));
        rejects(j -> j.getAsJsonArray("recipes").get(0).getAsJsonObject().getAsJsonArray("ingredients").set(0, new JsonPrimitive("minecraft:dirt")));
        rejects(j -> j.getAsJsonObject("effects").getAsJsonObject("huiling").addProperty("power_fraction", 1.1));
        rejects(j -> j.getAsJsonObject("effects").getAsJsonObject("ningshen").addProperty("stability_multiplier", 1));
        rejects(j -> j.getAsJsonObject("effects").getAsJsonObject("yangyuan").addProperty("interval_ticks", 999999));
    }
    @Test void everyWeightedDrawStaysUnderItsCeilingAndMatchesData() {
        AlchemyRules rules = AlchemyRules.get();
        int[][] expected = {{100, 0, 0, 0}, {70, 30, 0, 0}, {55, 30, 15, 0}, {45, 30, 20, 5}};
        for (int cap = 1; cap <= 4; cap++) {
            int[] actual = new int[4];
            for (int draw = 0; draw < rules.qualityWeightTotal(cap); draw++) {
                int quality = rules.rollQuality(cap, draw);
                assertTrue(quality >= 1 && quality <= cap);
                actual[quality - 1]++;
            }
            assertArrayEquals(expected[cap - 1], actual);
            final int ceiling = cap;
            assertThrows(IllegalArgumentException.class, () -> rules.rollQuality(ceiling, -1));
            assertThrows(IllegalArgumentException.class, () -> rules.rollQuality(ceiling, rules.qualityWeightTotal(ceiling)));
        }
        assertEquals(1.0, rules.qualityMultiplier(1));
        assertEquals(1.25, rules.qualityMultiplier(2));
        assertEquals(1.5, rules.qualityMultiplier(3));
        assertEquals(2.0, rules.qualityMultiplier(4));
        for (int invalid : new int[] {Integer.MIN_VALUE, 0, 5, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> rules.qualityMultiplier(invalid));
            assertThrows(IllegalArgumentException.class, () -> rules.qualityWeightTotal(invalid));
        }
    }

    @Test void rejectsUnsafeQualityTablesAndMultipliers() throws IOException {
        rejects(j -> j.addProperty("version", 1));
        rejects(j -> j.getAsJsonObject("quality").getAsJsonArray("weights_by_cap").remove(0));
        rejects(j -> j.getAsJsonObject("quality").getAsJsonArray("weights_by_cap").get(0).getAsJsonArray().set(1, new JsonPrimitive(1)));
        for (JsonElement invalid : new JsonElement[] {new JsonPrimitive(-1), new JsonPrimitive(1000001),
                new JsonPrimitive(1.5), new JsonPrimitive("100"), new JsonPrimitive(true), JsonNull.INSTANCE}) {
            rejects(j -> j.getAsJsonObject("quality").getAsJsonArray("weights_by_cap").get(0).getAsJsonArray().set(0, invalid));
        }
        rejects(j -> j.getAsJsonObject("quality").getAsJsonArray("weights_by_cap").get(1).getAsJsonArray().set(1, new JsonPrimitive(0)));
        rejects(j -> j.getAsJsonObject("quality").getAsJsonArray("strength_multipliers").set(0, new JsonPrimitive(1.1)));
        rejects(j -> j.getAsJsonObject("quality").getAsJsonArray("strength_multipliers").set(2, new JsonPrimitive(1.25)));
        rejects(j -> j.getAsJsonObject("quality").getAsJsonArray("strength_multipliers").set(3, new JsonPrimitive(17)));
    }

}
