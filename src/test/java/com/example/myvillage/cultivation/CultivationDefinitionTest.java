package com.example.myvillage.cultivation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.data.SpiritualElementDefinition;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueRequirements;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CultivationDefinitionTest {
    @Test
    void realmStageCodecRoundTripsStableIdAndOrder() {
        RealmStageDefinition stage = new RealmStageDefinition(
                id("qi_refining_1"),
                "cultivation.stage.myvillage.qi_refining_1",
                0);

        assertEquals(stage, roundTrip(RealmStageDefinition.CODEC, stage));
    }

    @Test
    void realmDefinitionCodecRoundTripsAndChecksMembership() {
        RealmStageDefinition first = new RealmStageDefinition(
                id("mortal_unawakened"),
                "cultivation.stage.myvillage.mortal_unawakened",
                0);
        RealmStageDefinition second = new RealmStageDefinition(
                id("mortal_qi_sensed"),
                "cultivation.stage.myvillage.mortal_qi_sensed",
                1);
        RealmDefinition realm = new RealmDefinition(
                "cultivation.realm.myvillage.mortal",
                0,
                List.of(first, second),
                Optional.of(id("qi_refining")));

        RealmDefinition decoded = roundTrip(RealmDefinition.CODEC, realm);

        assertEquals(realm, decoded);
        assertTrue(decoded.hasStage(id("mortal_unawakened")));
        assertTrue(decoded.hasStage(id("mortal_qi_sensed")));
        assertFalse(decoded.hasStage(id("qi_refining_1")));
    }

    @Test
    void realmDefinitionRejectsDuplicateOrUnorderedStages() {
        RealmStageDefinition first = new RealmStageDefinition(id("one"), "stage.one", 1);
        RealmStageDefinition duplicateOrder = new RealmStageDefinition(id("two"), "stage.two", 1);
        RealmStageDefinition earlier = new RealmStageDefinition(id("three"), "stage.three", 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new RealmDefinition(
                        "realm.test",
                        0,
                        List.of(first, duplicateOrder),
                        Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RealmDefinition(
                        "realm.test",
                        0,
                        List.of(first, earlier),
                        Optional.empty()));
    }

    @Test
    void spiritualElementDefinitionCodecRoundTripsOptionalColor() {
        SpiritualElementDefinition element = new SpiritualElementDefinition(
                "cultivation.element.myvillage.fire",
                3,
                Optional.of(0xEE4411));

        assertEquals(element, roundTrip(SpiritualElementDefinition.CODEC, element));
    }

    @Test
    void techniqueRequirementsCodecRoundTripsAndDefensivelyCopies() {
        Map<ResourceLocation, Integer> affinities = new LinkedHashMap<>();
        affinities.put(id("fire"), 4_000);
        TechniqueRequirements requirements = new TechniqueRequirements(
                Optional.of(id("qi_refining")),
                Optional.of(id("qi_refining_1")),
                affinities);

        affinities.clear();

        assertEquals(4_000, requirements.minimumElementAffinity().get(id("fire")));
        assertEquals(requirements, roundTrip(TechniqueRequirements.CODEC, requirements));
        assertThrows(
                UnsupportedOperationException.class,
                () -> requirements.minimumElementAffinity().put(id("water"), 1));
    }

    @Test
    void techniqueCategoryUsesStableStringsRatherThanOrdinals() {
        for (TechniqueCategory category : TechniqueCategory.values()) {
            JsonElement encoded = TechniqueCategory.CODEC
                    .encodeStart(JsonOps.INSTANCE, category)
                    .getOrThrow();

            assertEquals(new JsonPrimitive(category.serializedName()), encoded);
            assertEquals(
                    category,
                    TechniqueCategory.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        }
        assertTrue(TechniqueCategory.CODEC
                .parse(JsonOps.INSTANCE, new JsonPrimitive("not_a_category"))
                .isError());
    }

    @Test
    void techniqueDefinitionCodecRoundTripsNestedRequirementsAndCategory() {
        TechniqueRequirements requirements = new TechniqueRequirements(
                Optional.of(id("qi_refining")),
                Optional.of(id("qi_refining_1")),
                Map.of(id("fire"), 2_500));
        TechniqueDefinition technique = new TechniqueDefinition(
                "cultivation.technique.myvillage.flame_step",
                TechniqueCategory.MOVEMENT,
                2,
                List.of(id("fire")),
                requirements);

        JsonElement encoded = TechniqueDefinition.CODEC
                .encodeStart(JsonOps.INSTANCE, technique)
                .getOrThrow();

        assertEquals("movement", encoded.getAsJsonObject().get("category").getAsString());
        assertEquals(technique, TechniqueDefinition.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow());
    }

    @Test
    void invalidDefinitionInvariantsAreRejected() {
        RealmStageDefinition validStage = new RealmStageDefinition(id("valid"), "stage.valid", 0);
        ResourceLocation fire = id("fire");
        TechniqueRequirements noRequirements = TechniqueRequirements.none();

        assertThrows(
                IllegalArgumentException.class,
                () -> new RealmStageDefinition(id("bad_order"), "stage.bad", -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RealmDefinition(
                        "realm.bad",
                        -1,
                        List.of(validStage),
                        Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpiritualElementDefinition("element.bad", -1, Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpiritualElementDefinition("element.bad", 0, Optional.of(0x1000000)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TechniqueDefinition(
                        "technique.bad",
                        TechniqueCategory.CORE,
                        -1,
                        List.of(fire),
                        noRequirements));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TechniqueDefinition(
                        "technique.bad",
                        TechniqueCategory.CORE,
                        0,
                        List.of(fire, fire),
                        noRequirements));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TechniqueRequirements(
                        Optional.empty(),
                        Optional.of(id("qi_refining_1")),
                        Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TechniqueRequirements(
                        Optional.empty(),
                        Optional.empty(),
                        Map.of(fire, 10_001)));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }

    private static <T> T roundTrip(Codec<T> codec, T value) {
        return codec.parse(
                        JsonOps.INSTANCE,
                        codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow())
                .getOrThrow();
    }
}
