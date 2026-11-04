package com.example.myvillage.client.forging.jei;

import com.example.myvillage.forging.ForgingRules;
import com.example.myvillage.forging.WorkshopRules;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A material-to-implement overview; the server workshop remains the production authority. */
public record ForgingJeiRecipe(String id, String output, String pattern, Map<String, Integer> materials) {
    public ForgingJeiRecipe {
        materials = Collections.unmodifiableMap(new TreeMap<>(materials));
    }

    public static List<ForgingJeiRecipe> create(ForgingRules forging, WorkshopRules workshop) {
        return workshop.crafts().stream().map(craft -> {
            var recipe = forging.recipe(craft.recipe()).orElseThrow();
            return new ForgingJeiRecipe(recipe.id(), recipe.output(),
                    craft.requires_pattern() ? recipe.pattern() : "", workshop.blankCosts(recipe));
        }).toList();
    }
}
