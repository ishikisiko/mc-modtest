package com.example.myvillage.cultivation.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Decode tests for the technique-catalogue shapes (brief sections 2.1 to 2.3 and 3.1 to 3.4). */
class TechniqueCatalogueDefinitionTest {
    @Test
    void fullTechniqueShapeDecodesWithSchoolLineageAndCoreEffects() {
        TechniqueDefinition technique = decode(TechniqueDefinition.CODEC, """
                {
                  "translation_key": "cultivation.technique.myvillage.gengjin_yinqi_fa",
                  "category": "core",
                  "grade": 2,
                  "elements": ["myvillage:metal"],
                  "school": "myvillage:sword",
                  "requirements": { "minimum_realm": "myvillage:qi_refining", "minimum_stage": "myvillage:qi_refining_3" },
                  "lineage": { "previous": "myvillage:gengjin_tuna_shu" },
                  "effects": { "core": { "meditation_route": "xiaozhoutian" } }
                }
                """);

        assertEquals(TechniqueCategory.CORE, technique.category());
        assertEquals(2, technique.grade());
        assertEquals(List.of(id("metal")), technique.elements());
        assertEquals(Optional.of(id("sword")), technique.school());
        assertEquals(Optional.of(id("gengjin_tuna_shu")), technique.previous());
        assertEquals(Optional.of("xiaozhoutian"), technique.meditationRoute());
        assertEquals(technique, roundTrip(TechniqueDefinition.CODEC, technique));
    }

    @Test
    void legacyTechniqueFileStaysValidAndNullOptionalsReadAsAbsent() throws Exception {
        TechniqueDefinition shipped = decode(TechniqueDefinition.CODEC, Files.readString(Path.of(
                "src/main/resources/data/myvillage/myvillage/technique/basic_breathing.json")));
        assertEquals(0, shipped.grade());
        assertTrue(shipped.isCore());

        TechniqueDefinition legacy = decode(TechniqueDefinition.CODEC, """
                {
                  "translation_key": "cultivation.technique.myvillage.legacy",
                  "category": "core",
                  "grade": 0,
                  "elements": [],
                  "requirements": {}
                }
                """);
        assertEquals(Optional.empty(), legacy.school());
        assertEquals(Optional.empty(), legacy.lineage());
        assertEquals(Optional.empty(), legacy.effects());

        TechniqueDefinition nulls = decode(TechniqueDefinition.CODEC, """
                {
                  "translation_key": "cultivation.technique.myvillage.nulls",
                  "category": "core",
                  "grade": 1,
                  "elements": [],
                  "school": null,
                  "requirements": {},
                  "lineage": null,
                  "effects": null
                }
                """);
        assertEquals(Optional.empty(), nulls.school());
        assertEquals(Optional.empty(), nulls.lineage());
        assertEquals(Optional.empty(), nulls.effects());
    }

    @Test
    void secondPhaseEffectShapesDecodeOnTheirOwnCategory() {
        TechniqueDefinition active = decode(TechniqueDefinition.CODEC, technique("active",
                "\"effects\": { \"active\": { \"skill\": \"myvillage:gengjin_jianjue\", \"qi_cost\": 12, \"slots\": 1 } }"));
        assertEquals(new TechniqueEffects.Active(id("gengjin_jianjue"), 12, 1),
                active.effects().orElseThrow().active().orElseThrow());

        TechniqueDefinition movement = decode(TechniqueDefinition.CODEC, technique("movement",
                "\"effects\": { \"movement\": { \"dash_distance\": 4.0, \"invulnerable_ticks\": 6, "
                        + "\"qi_cost\": 8, \"cooldown_ticks\": 20 } }"));
        assertEquals(new TechniqueEffects.Movement(4.0D, 6, 8, 20),
                movement.effects().orElseThrow().movement().orElseThrow());

        TechniqueDefinition body = decode(TechniqueDefinition.CODEC, technique("body",
                "\"effects\": { \"body\": { \"attributes\": { \"minecraft:generic.max_health\": 4.0, "
                        + "\"minecraft:generic.armor\": 2.0 }, \"stagger_resistance\": 1 } }"));
        TechniqueEffects.Body bodyEffects = body.effects().orElseThrow().body().orElseThrow();
        assertEquals(Map.of(
                        ResourceLocation.withDefaultNamespace("generic.max_health"), 4.0D,
                        ResourceLocation.withDefaultNamespace("generic.armor"), 2.0D),
                bodyEffects.attributes());
        assertEquals(1, bodyEffects.staggerResistance());

        for (TechniqueDefinition definition : List.of(active, movement, body)) {
            assertEquals(definition, roundTrip(TechniqueDefinition.CODEC, definition));
        }
    }

    @Test
    void invalidTechniqueShapesAreRejected() {
        assertError(TechniqueDefinition.CODEC, technique("core", "\"grade\": 5"));
        assertError(TechniqueDefinition.CODEC, technique("core", "\"grade\": -1"));
        assertError(TechniqueDefinition.CODEC, technique("core",
                "\"effects\": { \"active\": { \"skill\": \"myvillage:x\", \"qi_cost\": 1, \"slots\": 1 } }"));
        assertError(TechniqueDefinition.CODEC, technique("core",
                "\"effects\": { \"core\": { \"meditation_route\": \"\" } }"));
        assertError(TechniqueDefinition.CODEC, technique("core", "\"effects\": { \"core\": {} }"));
        assertError(TechniqueDefinition.CODEC, technique("core", "\"lineage\": {}"));
        assertError(TechniqueDefinition.CODEC, technique("active",
                "\"effects\": { \"active\": { \"skill\": \"myvillage:x\", \"qi_cost\": -1, \"slots\": 1 } }"));
        assertError(TechniqueDefinition.CODEC, technique("movement",
                "\"effects\": { \"movement\": { \"dash_distance\": 0, \"invulnerable_ticks\": 6, "
                        + "\"qi_cost\": 8, \"cooldown_ticks\": 20 } }"));
        assertError(TechniqueDefinition.CODEC, technique("body", "\"effects\": { \"body\": {} }"));
    }

    @Test
    void weaponAndSpecialSchoolsDecode() {
        SchoolDefinition sword = decode(SchoolDefinition.CODEC, """
                {
                  "translation_key": "cultivation.school.myvillage.sword",
                  "kind": "weapon",
                  "weapon_family": "sword",
                  "runtime": null,
                  "element_lean": []
                }
                """);
        assertEquals(SchoolKind.WEAPON, sword.kind());
        assertEquals(Optional.of("sword"), sword.weaponFamily());
        assertEquals(Optional.empty(), sword.runtime());
        assertEquals(List.of(), sword.elementLean());
        assertEquals(sword, roundTrip(SchoolDefinition.CODEC, sword));

        SchoolDefinition flyingSword = decode(SchoolDefinition.CODEC, """
                {
                  "translation_key": "cultivation.school.myvillage.flying_sword",
                  "kind": "special",
                  "weapon_family": null,
                  "runtime": "flying_sword",
                  "element_lean": ["myvillage:metal"]
                }
                """);
        assertEquals(SchoolKind.SPECIAL, flyingSword.kind());
        assertEquals(Optional.of(SchoolRuntime.FLYING_SWORD), flyingSword.runtime());
        assertEquals(List.of(id("metal")), flyingSword.elementLean());
        assertEquals(flyingSword, roundTrip(SchoolDefinition.CODEC, flyingSword));
    }

    @Test
    void schoolKindFieldRulesAreEnforced() {
        assertError(SchoolDefinition.CODEC, """
                { "translation_key": "s", "kind": "weapon", "element_lean": [] }
                """);
        assertError(SchoolDefinition.CODEC, """
                { "translation_key": "s", "kind": "weapon", "weapon_family": "sword", "runtime": "spell" }
                """);
        assertError(SchoolDefinition.CODEC, """
                { "translation_key": "s", "kind": "special", "element_lean": [] }
                """);
        assertError(SchoolDefinition.CODEC, """
                { "translation_key": "s", "kind": "special", "weapon_family": "sword", "runtime": "spell" }
                """);
        assertError(SchoolDefinition.CODEC, """
                { "translation_key": "s", "kind": "special", "runtime": "lightning" }
                """);
        assertError(SchoolDefinition.CODEC, """
                { "translation_key": "s", "kind": "magic", "runtime": "spell" }
                """);
    }

    @Test
    void heritageDecodesInOrderAndRejectsShortOrDuplicateChains() {
        HeritageDefinition heritage = decode(HeritageDefinition.CODEC, """
                {
                  "translation_key": "cultivation.heritage.myvillage.taibai_jianmai",
                  "school": "myvillage:sword",
                  "techniques": ["myvillage:gengjin_yinqi_fa", "myvillage:gengjin_jianjue", "myvillage:taibai_jianjing"],
                  "exclusive": true
                }
                """);
        assertEquals(Optional.of(id("sword")), heritage.school());
        assertEquals(List.of(id("gengjin_yinqi_fa"), id("gengjin_jianjue"), id("taibai_jianjing")),
                heritage.techniques());
        assertTrue(heritage.exclusive());
        assertEquals(1, heritage.indexOf(id("gengjin_jianjue")));
        assertEquals(heritage, roundTrip(HeritageDefinition.CODEC, heritage));

        HeritageDefinition schoolless = decode(HeritageDefinition.CODEC, """
                {
                  "translation_key": "cultivation.heritage.myvillage.qingdi_mumai",
                  "school": null,
                  "techniques": ["myvillage:a", "myvillage:b"],
                  "exclusive": false
                }
                """);
        assertEquals(Optional.empty(), schoolless.school());
        assertFalse(schoolless.exclusive());

        assertError(HeritageDefinition.CODEC, """
                { "translation_key": "h", "techniques": ["myvillage:a"], "exclusive": false }
                """);
        assertError(HeritageDefinition.CODEC, """
                { "translation_key": "h", "techniques": ["myvillage:a", "myvillage:a"], "exclusive": false }
                """);
        assertError(HeritageDefinition.CODEC, """
                { "translation_key": "", "techniques": ["myvillage:a", "myvillage:b"] }
                """);
    }

    /** A minimal technique plus one extra field; a repeated key (such as "grade") overrides the default. */
    private static String technique(String category, String extraField) {
        return """
                {
                  "translation_key": "cultivation.technique.myvillage.sample",
                  "category": "%s",
                  "grade": 1,
                  "elements": [],
                  "requirements": {},
                  %s
                }
                """.formatted(category, extraField);
    }

    private static <T> T decode(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static <T> void assertError(Codec<T> codec, String json) {
        DataResult<T> result = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        assertTrue(result.error().isPresent(), () -> "expected a decode error for " + json);
    }

    private static <T> T roundTrip(Codec<T> codec, T value) {
        return codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow())
                .getOrThrow();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }
}
