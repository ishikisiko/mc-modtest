package com.example.myvillage.cultivation.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.cultivation.data.TechniqueDefinition.TechniqueStudy;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The optional {@code study} block of a technique (docs/technique-manual-brief.md section 4). */
class TechniqueStudyDefinitionTest {
    private static final Map<Integer, TechniqueStudy> STUDY_BY_GRADE = Map.of(
            1, new TechniqueStudy(4000, 0, 0),
            2, new TechniqueStudy(12000, 1, 50),
            3, new TechniqueStudy(36000, 2, 100),
            4, new TechniqueStudy(96000, 3, 150));

    @Test
    void absentStudyReadsAsTheDefaultAndIsNotWrittenBack() {
        TechniqueDefinition technique = decode(technique(""));
        assertEquals(TechniqueStudy.DEFAULT, technique.study());
        assertEquals(new TechniqueStudy(4000, 0, 0), technique.study());

        JsonElement encoded = TechniqueDefinition.CODEC.encodeStart(JsonOps.INSTANCE, technique).getOrThrow();
        assertFalse(encoded.getAsJsonObject().has("study"));
    }

    @Test
    void studyBlockDecodesAndRoundTrips() {
        TechniqueDefinition technique = decode(technique(
                ", \"study\": {\"points\": 12000, \"gates\": 1, \"gate_stability_cost\": 50}"));
        assertEquals(new TechniqueStudy(12000, 1, 50), technique.study());

        JsonElement encoded = TechniqueDefinition.CODEC.encodeStart(JsonOps.INSTANCE, technique).getOrThrow();
        assertEquals(12000, encoded.getAsJsonObject().getAsJsonObject("study").get("points").getAsInt());
        assertEquals(technique, TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void gatesAndCostDefaultToZeroWhenOnlyPointsAreGiven() {
        TechniqueDefinition technique = decode(technique(", \"study\": {\"points\": 500}"));
        assertEquals(new TechniqueStudy(500, 0, 0), technique.study());
    }

    @Test
    void invalidStudyBlocksAreDecodeErrors() {
        for (String study : List.of(
                "{\"points\": 0}",
                "{\"points\": -5, \"gates\": 0, \"gate_stability_cost\": 0}",
                "{\"points\": 100, \"gates\": -1}",
                "{\"points\": 100, \"gates\": 1, \"gate_stability_cost\": -1}",
                "{\"gates\": 1}")) {
            DataResult<TechniqueDefinition> result = TechniqueDefinition.CODEC.parse(
                    JsonOps.INSTANCE, JsonParser.parseString(technique(", \"study\": " + study)));
            assertTrue(result.error().isPresent(), () -> "expected a decode error for " + study);
        }
    }

    @Test
    void constructorRejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> new TechniqueStudy(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TechniqueStudy(1, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TechniqueStudy(1, 0, -1));
    }

    @Test
    void everyShippedManualTechniqueCarriesTheStudyBlockOfItsGrade() throws Exception {
        Path directory = Path.of("src/main/resources/data/myvillage/myvillage/technique");
        int checked = 0;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                JsonElement json = JsonParser.parseString(Files.readString(file));
                TechniqueDefinition technique = TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                if (technique.grade() == 0) {
                    assertFalse(json.getAsJsonObject().has("study"), () -> file + " is grade 0 and has a study block");
                    continue;
                }
                assertTrue(json.getAsJsonObject().has("study"), () -> file + " has no study block");
                assertEquals(STUDY_BY_GRADE.get(technique.grade()), technique.study(), file::toString);
                checked++;
            }
        }
        assertTrue(checked > 0);
    }

    private static TechniqueDefinition decode(String json) {
        return TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static String technique(String extra) {
        return """
                {
                  "translation_key": "cultivation.technique.myvillage.sample",
                  "category": "active",
                  "grade": 2,
                  "elements": [],
                  "requirements": {}%s
                }
                """.formatted(extra);
    }
}
