package com.example.myvillage.entity.npc;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CultivatorVariantsTest {
    @Test
    void everyAutomaticSpawnChoiceUsesARetainedCombination() throws Exception {
        var document=JsonParser.parseString(Files.readString(Path.of("src/main/resources"+CultivatorVariants.RESOURCE))).getAsJsonObject();
        int total=0;
        for (var family:document.getAsJsonArray("spawn_families")) total+=family.getAsJsonObject().get("weight").getAsInt();
        Set<String> seen=new HashSet<>();
        for (int roll=0;roll<total;roll++) {
            for (int index=0;index<Math.max(CultivatorVariants.males().size(),CultivatorVariants.novices().size());index++) {
                final int familyRoll=roll,variantRoll=index;
                var calls=new java.util.concurrent.atomic.AtomicInteger();
                String look=CultivatorVariants.randomLook(bound -> calls.getAndIncrement()==0 ? familyRoll : variantRoll%bound);
                assertEquals(2,calls.get());
                assertFalse(CultivatorVariants.legacyLooks().contains(look));
                seen.add(look);
            }
        }
        assertEquals(172,seen.size());
    }

    @Test
    void automaticSpawnConfigurationRejectsLegacyFamiliesAndInvalidWeights() throws Exception {
        String text=Files.readString(Path.of("src/main/resources"+CultivatorVariants.RESOURCE));
        for (String family:List.of("default","f_adept","unknown")) {
            var data=JsonParser.parseString(text).getAsJsonObject();
            data.getAsJsonArray("spawn_families").get(0).getAsJsonObject().addProperty("family",family);
            assertThrows(IllegalArgumentException.class,()->CultivatorVariants.parse(data));
        }
        for (int weight:new int[]{-1,0,101}) {
            var data=JsonParser.parseString(text).getAsJsonObject();
            data.getAsJsonArray("spawn_families").get(0).getAsJsonObject().addProperty("weight",weight);
            assertThrows(IllegalArgumentException.class,()->CultivatorVariants.parse(data));
        }
    }
    @Test
    void catalogueCoversRetainedPartsAndPreservesLegacyIds() {
        assertEquals(175, CultivatorVariants.looks().size());
        assertEquals(72, CultivatorVariants.novices().size());
        assertEquals(100, CultivatorVariants.males().size());
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
    void maleCatalogueIsIndependentAndComplete() throws Exception {
        Set<String> combinations = new HashSet<>();
        for (var v : CultivatorVariants.males()) {
            assertEquals("male", v.family());
            assertTrue(Set.of("01","02","03","04","05").contains(v.face()));
            assertTrue(Set.of("01","02","03","04","05").contains(v.hair()));
            assertTrue(Set.of("A","B","C","legacy").contains(v.outfit()));
            assertTrue(combinations.add(v.face()+":"+v.hair()+":"+v.outfit()));
        }
        assertEquals(100, combinations.size());
        var vectors = JsonParser.parseString(Files.readString(Path.of("src/test/resources/npc_male_identity.json"))).getAsJsonArray();
        for (var item : vectors) {
            var v = item.getAsJsonObject();
            assertEquals(v.get("look").getAsString(), CultivatorVariants.forMaleIdentity(
                    v.get("person_id").getAsInt(),v.get("birth_day").getAsLong()));
        }
        Set<String> seen = new HashSet<>();
        for (int id=0;id<10000;id++) seen.add(CultivatorVariants.forMaleIdentity(id,-100));
        assertEquals(100, seen.size());
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
        var badFamily = JsonParser.parseString(text).getAsJsonObject();
        badFamily.getAsJsonArray("variants").get(0).getAsJsonObject().addProperty("family", "unknown");
        assertThrows(IllegalArgumentException.class, () -> CultivatorVariants.parse(badFamily));
    }
}
