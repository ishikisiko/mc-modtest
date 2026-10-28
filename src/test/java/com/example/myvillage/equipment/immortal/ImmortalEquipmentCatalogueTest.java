package com.example.myvillage.equipment.immortal;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class ImmortalEquipmentCatalogueTest {
    private static JsonObject source() {
        var stream = ImmortalEquipmentCatalogueTest.class.getResourceAsStream("/data/myvillage/immortal_equipment/catalogue.json");
        assertNotNull(stream, "缺少正式目录");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }

    private static JsonObject first(JsonObject source) {
        return source.getAsJsonArray("sets").get(0).getAsJsonObject();
    }

    private static void rejects(Consumer<JsonObject> mutation) {
        var data = source();mutation.accept(data);
        assertThrows(IllegalArgumentException.class, () -> ImmortalEquipmentCatalogue.parse(new StringReader(data.toString())));
    }

    @Test void allSeventyFiveIdsAndFourSlotsRemainUnique() {
        var parsed = ImmortalEquipmentCatalogue.parse(new StringReader(source().toString()));
        assertEquals(15, parsed.sets().size());assertEquals(60, parsed.armor().size());assertEquals(15, parsed.ingots().size());
        var ids = new HashSet<String>();
        for (var armor : parsed.armor()) assertTrue(ids.add(armor.id().toString()));
        for (var ingot : parsed.ingots()) assertTrue(ids.add(ingot.id().toString()));
        assertEquals(75, ids.size());
        for (int level = 1; level <= 5; level++) {
            final int expected = level;
            assertEquals(3, parsed.sets().stream().filter(s -> s.level() == expected).count());
        }
        for (var set : parsed.sets()) {
            assertEquals(4, set.armor().size());
            assertEquals(4, set.armor().stream().map(ImmortalEquipmentCatalogue.ArmorDefinition::type).distinct().count());
            for (var armor : set.armor()) {
                assertEquals(set.id(), armor.setId());assertEquals(set.level(), armor.level());
                assertEquals(set.model(), armor.model());assertEquals(set.texture(), armor.texture());
                assertSame(parsed.tiers().get(set.level()), armor.tier());
            }
        }
    }

    @Test void tierValuesAreReadFromInputRatherThanDuplicatedConstants() {
        var data = source();
        var tier = data.getAsJsonObject("tiers").getAsJsonObject("1");
        tier.addProperty("durability_multiplier", 29);tier.addProperty("toughness", 1.25);
        var parsed = ImmortalEquipmentCatalogue.parse(new StringReader(data.toString()));
        assertEquals(29, parsed.tiers().get(1).durabilityMultiplier());
        assertEquals(1.25f, parsed.tiers().get(1).toughness());
        assertTrue(parsed.armor().stream().filter(a -> a.level() == 1).allMatch(a -> a.tier().durabilityMultiplier() == 29));
    }

    @Test void missingSetDuplicateSetOrIncompleteSlotsFailClosed() {
        rejects(d -> d.getAsJsonArray("sets").remove(0));
        rejects(d -> d.getAsJsonArray("sets").set(1, first(d).deepCopy()));
        rejects(d -> first(d).getAsJsonArray("armor").remove(0));
        rejects(d -> first(d).getAsJsonArray("armor").get(0).getAsJsonObject().addProperty("slot", "hand"));
    }

    @Test void foreignIdsAndMismatchedResourcesFailClosed() {
        rejects(d -> first(d).getAsJsonObject("ingot").addProperty("id", "minecraft:iron_ingot"));
        rejects(d -> first(d).getAsJsonArray("armor").get(0).getAsJsonObject().addProperty("id", "myvillage:xuantie_helmet"));
        rejects(d -> first(d).addProperty("model", "myvillage:immortal_equipment/other.json"));
        rejects(d -> first(d).addProperty("texture", "myvillage:textures/equipment/xuantie_armor.png"));
    }

    @Test void invalidTierAndNonfiniteNumbersFailClosed() {
        rejects(d -> first(d).addProperty("level", 0));
        rejects(d -> d.getAsJsonObject("tiers").remove("5"));
        rejects(d -> d.getAsJsonObject("tiers").getAsJsonObject("1").addProperty("durability_multiplier", 0));
        rejects(d -> d.getAsJsonObject("tiers").getAsJsonObject("1").addProperty("durability_multiplier", 1.5));
        rejects(d -> d.getAsJsonObject("tiers").getAsJsonObject("1").addProperty("toughness", 1e100));
        rejects(d -> d.getAsJsonObject("tiers").getAsJsonObject("1").addProperty("knockback_resistance", 1.1));
    }

    @Test void parsedCollectionsAreImmutable() {
        var parsed = ImmortalEquipmentCatalogue.parse(new StringReader(source().toString()));
        assertThrows(UnsupportedOperationException.class, () -> parsed.armor().clear());
        assertThrows(UnsupportedOperationException.class, () -> parsed.ingots().clear());
        assertThrows(UnsupportedOperationException.class, () -> parsed.sets().getFirst().armor().clear());
        assertThrows(UnsupportedOperationException.class, () -> parsed.tiers().clear());
        assertThrows(UnsupportedOperationException.class, () -> parsed.tiers().get(1).defense().clear());
    }
}
