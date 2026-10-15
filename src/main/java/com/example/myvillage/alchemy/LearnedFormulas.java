package com.example.myvillage.alchemy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.*;

/** Immutable player knowledge; recipe identifiers survive death and later recipe additions. */
public record LearnedFormulas(Set<String> recipes) {
    public static final LearnedFormulas EMPTY = new LearnedFormulas(Set.of());
    public static final Codec<LearnedFormulas> CODEC = Codec.STRING.listOf().comapFlatMap(ids -> {
        try { return DataResult.success(new LearnedFormulas(new HashSet<>(ids))); }
        catch (IllegalArgumentException e) { return DataResult.error(e::getMessage); }
    }, value -> value.recipes().stream().sorted().toList());
    public LearnedFormulas {
        recipes = Set.copyOf(recipes);
        if (recipes.size() > 64 || recipes.stream().anyMatch(id -> !id.matches("[a-z0-9_]{1,64}")))
            throw new IllegalArgumentException("Invalid learned formula identifiers");
    }
    public boolean knows(String recipe) { return recipes.contains(recipe); }
    public LearnedFormulas learn(String recipe) {
        if (knows(recipe)) return this;
        Set<String> next = new HashSet<>(recipes); next.add(recipe); return new LearnedFormulas(next);
    }
}
