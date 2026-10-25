package com.example.myvillage.entity.npc;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CultivatorVariantsTest {
    @Test
    void catalogueCoversRetainedPartsAndPreservesLegacyIds() {
        assertEquals(75, CultivatorVariants.looks().size());
        assertEquals(72, CultivatorVariants.novices().size());
        assertTrue(CultivatorVariants.looks().containsAll(Set.of("default", "f_novice", "f_adept")));
        Set<String> combinations = new HashSet<>();
        for (var v : CultivatorVariants.novices()) {
            assertTrue(Set.of("01", "02", "03").contains(v.face()));
            assertTrue(Set.of("A", "B", "C", "D").contains(v.hair()));
            assertTrue(Set.of("01", "02", "03", "05", "pants", "robe").contains(v.outfit()));
            assertTrue(combinations.add(v.face() + ":" + v.hair() + ":" + v.outfit()));
        }
    }

    @Test
    void identitySelectionMatchesGeneratorAndIsRepeatable() throws Exception {
        var vectors = JsonParser.parseString(Files.readString(Path.of("src/test/resources/npc_variant_identity.json"))).getAsJsonArray();
        for (var item : vectors) {
            var v = item.getAsJsonObject();
            for (int i = 0; i < 3; i++) {
                assertEquals(v.get("look").getAsString(), CultivatorVariants.forIdentity(
                        v.get("person_id").getAsInt(), v.get("birth_day").getAsLong()));
            }
        }
        Set<String> seen = new HashSet<>();
        for (int id = 0; id < 3000; id++) seen.add(CultivatorVariants.forIdentity(id, -100));
        assertEquals(72, seen.size());
    }

    @Test
    void missingLegacyAndRepeatedOrEmptyVariantsAreRejected() throws Exception {
        String text = Files.readString(Path.of("src/main/resources" + CultivatorVariants.RESOURCE));
        var data = JsonParser.parseString(text).getAsJsonObject();
        data.getAsJsonArray("legacy_looks").remove(1);
        assertThrows(IllegalArgumentException.class, () -> CultivatorVariants.parse(data));
        var repeated = JsonParser.parseString(text).getAsJsonObject();
        repeated.getAsJsonArray("variants").add(repeated.getAsJsonArray("variants").get(0));
        assertThrows(IllegalArgumentException.class, () -> CultivatorVariants.parse(repeated));
        var empty = JsonParser.parseString(text).getAsJsonObject();
        empty.add("variants", new com.google.gson.JsonArray());
        assertThrows(IllegalArgumentException.class, () -> CultivatorVariants.parse(empty));
    }
}
