package com.example.myvillage.alchemy;

import com.google.gson.JsonArray;
import com.mojang.serialization.JsonOps;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LearnedFormulasTest {
    @Test void learningIsImmutableIdempotentAndPersistsThroughCodec() {
        var initial = LearnedFormulas.EMPTY;
        var learned = initial.learn("huiling").learn("yangyuan");
        assertFalse(initial.knows("huiling")); assertTrue(learned.knows("huiling"));
        assertSame(learned, learned.learn("huiling"));
        var saved = LearnedFormulas.CODEC.encodeStart(JsonOps.INSTANCE, learned).getOrThrow();
        assertEquals(learned, LearnedFormulas.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
        assertThrows(UnsupportedOperationException.class, () -> learned.recipes().add("ningshen"));
    }
    @Test void invalidOrUnboundedIdsCannotEnterSavedKnowledge() {
        JsonArray invalid = new JsonArray(); invalid.add("../unknown");
        assertTrue(LearnedFormulas.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent());
        assertThrows(IllegalArgumentException.class, () -> new LearnedFormulas(Set.of("")));
        assertThrows(IllegalArgumentException.class, () -> new LearnedFormulas(java.util.stream.IntStream.range(0, 65)
                .mapToObj(i -> "recipe_" + i).collect(java.util.stream.Collectors.toSet())));
        assertEquals(LearnedFormulas.EMPTY, LearnedFormulas.CODEC.parse(JsonOps.INSTANCE, new JsonArray()).getOrThrow());
    }
}
