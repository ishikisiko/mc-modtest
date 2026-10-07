package com.example.myvillage.portrait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Every role of every vector resolves to the colour roles.py gave it. */
final class PortraitPaletteTest {
    @Test
    void everyPaletteVectorMatches() throws IOException {
        JsonObject vectors;
        try (InputStream in = PortraitPaletteTest.class.getResourceAsStream("/portrait_palette_vectors.json")) {
            assertNotNull(in, "portrait_palette_vectors.json");
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                vectors = JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
        List<String> failures = new ArrayList<>();
        int checked = 0;
        int cases = 0;
        for (JsonElement element : vectors.getAsJsonArray("cases")) {
            JsonObject vector = element.getAsJsonObject();
            PortraitSpec spec = PortraitTestData.spec(vector.getAsJsonObject("spec"));
            int[] colours = PortraitPalette.colours(spec);
            for (Map.Entry<String, JsonElement> role : vector.getAsJsonObject("palette").entrySet()) {
                int index = PortraitPalette.ROLES.indexOf(role.getKey());
                JsonArray rgb = role.getValue().getAsJsonArray();
                int expected = 0xFF000000 | rgb.get(0).getAsInt() << 16 | rgb.get(1).getAsInt() << 8 | rgb.get(2).getAsInt();
                if (index < 0 || colours[index] != expected) {
                    failures.add(String.format("case %d role %s expected %08X got %s", cases, role.getKey(), expected,
                            index < 0 ? "no such role" : String.format("%08X", colours[index])));
                }
                checked++;
            }
            cases++;
        }
        assertTrue(cases > 0 && checked > 0);
        assertEquals(List.of(), failures, cases + " cases, " + checked + " roles");
    }
}
