package com.example.myvillage.forging;

import com.example.myvillage.client.forging.jei.ForgingJeiRecipe;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForgingJeiRecipeTest {
    @Test void everyWorkshopOutputIsIndexedOnceWithoutInfusionCosts() {
        var forging = ForgingRules.get();
        var workshop = WorkshopRules.get();
        var recipes = ForgingJeiRecipe.create(forging, workshop);
        assertEquals(workshop.crafts().size(), recipes.size());
        var outputs = new HashSet<String>();
        for (var recipe : recipes) {
            assertTrue(outputs.add(recipe.output()));
            var original = forging.recipe(recipe.id()).orElseThrow();
            assertEquals(workshop.blankCosts(original), recipe.materials());
            assertFalse(recipe.materials().containsKey(workshop.spirit_ingredient()));
            assertEquals(workshop.craft(recipe.id()).requires_pattern() ? original.pattern() : "", recipe.pattern());
        }
    }

    @Test void xuantieArmorShowsBlankMaterialsAndPatternAsACondition() {
        var recipes = ForgingJeiRecipe.create(ForgingRules.get(), WorkshopRules.get());
        var armor = recipes.stream().filter(r -> r.id().equals("xuantie_chestplate")).findFirst().orElseThrow();
        assertEquals("myvillage:xuantie_chestplate", armor.output());
        assertEquals("myvillage:pattern_xuantie_chestplate", armor.pattern());
        assertEquals(Map.of("#myvillage:forging_materials/xuantie", 8, "minecraft:leather", 2), armor.materials());
        assertEquals(4, recipes.stream().filter(r -> WorkshopRules.get().craft(r.id()).armor()).count());
    }

    @Test void displayFollowsChangedAuthorCostsWithoutACopiedRecipeTable() throws Exception {
        String source;
        try (var input = ForgingRules.class.getResourceAsStream(ForgingRules.RESOURCE)) {
            source = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        source = source.replace("\"minecraft:diamond\": 2", "\"minecraft:diamond\": 6");
        var recipes = ForgingJeiRecipe.create(ForgingRules.parse(new StringReader(source)), WorkshopRules.get());
        assertEquals(6, recipes.getFirst().materials().get("minecraft:diamond"));
    }
}
