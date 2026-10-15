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
        assertSame(rules, AlchemyRules.get()); assertEquals(3, rules.recipes().size());
        for (var recipe : rules.recipes()) {
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
}
