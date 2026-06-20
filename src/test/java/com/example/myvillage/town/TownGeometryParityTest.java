package com.example.myvillage.town;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TownGeometryParityTest {
    private static JsonObject fixture() {
        return new Gson().fromJson(new InputStreamReader(
                TownGeometryParityTest.class.getResourceAsStream("/town_geometry_parity.json"),
                StandardCharsets.UTF_8), JsonObject.class);
    }

    @Test
    void probeSeedSnapshotsMatchPython() {
        for (JsonElement element : fixture().getAsJsonArray("snapshots")) {
            JsonObject expected = element.getAsJsonObject();
            long seed = expected.get("seed").getAsLong();
            Map<String, Object> actual = TownGenerator.paritySnapshot(seed);
            assertEquals(expected.get("family").getAsString(), actual.get("family"));
            assertEquals(expected.get("modifier").getAsString(), actual.get("modifier"));
            assertEquals(expected.get("center_x").getAsInt(), actual.get("center_x"));
            assertEquals(intLists(expected.getAsJsonArray("lanes")), actual.get("lanes"));
            assertEquals(expected.get("perimeter_cells").getAsInt(), actual.get("perimeter_cells"));
            assertEquals(expected.get("interior_cells").getAsInt(), actual.get("interior_cells"));
            assertEquals(intList(expected.getAsJsonArray("district_widths")), actual.get("district_widths"));
            assertEquals(intList(expected.getAsJsonArray("district_cells")), actual.get("district_cells"));
            assertEquals(List.of(), actual.get("validation_errors"));
        }
    }

    @Test
    void integerCircleAndOvalSweepMatchesPython() {
        for (JsonElement element : fixture().getAsJsonArray("curves")) {
            JsonObject expected = element.getAsJsonObject();
            List<Integer> actual = TownGenerator.curveCounts(
                    expected.get("seed").getAsLong(), expected.get("family").getAsString());
            assertEquals(expected.get("interior_cells").getAsInt(), actual.get(0));
            assertEquals(expected.get("perimeter_cells").getAsInt(), actual.get(1));
        }
    }

    private static List<Integer> intList(JsonArray values) {
        return values.asList().stream().map(JsonElement::getAsInt).toList();
    }

    private static List<List<Integer>> intLists(JsonArray values) {
        return values.asList().stream().map(e -> intList(e.getAsJsonArray())).toList();
    }
}
