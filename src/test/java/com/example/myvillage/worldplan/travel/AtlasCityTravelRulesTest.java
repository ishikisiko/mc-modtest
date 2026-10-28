package com.example.myvillage.worldplan.travel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasCityTravelRulesTest {
    private JsonObject json() throws Exception {
        try (var in=getClass().getResourceAsStream(AtlasCityTravelRules.CLASSPATH)) {
            assertNotNull(in);
            return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void unsafeBudgetsAndFractionalNumbersFailClosed() throws Exception {
        for (String key : new String[]{"search_radius","max_chunks_per_request","max_concurrent_requests","candidate_checks_per_tick"}) {
            var source=json();source.addProperty(key,100000);
            assertThrows(IllegalArgumentException.class,() -> AtlasCityTravelRules.parse(source),key);
        }
        var source=json();source.addProperty("search_radius",1.5);
        assertThrows(ArithmeticException.class,() -> AtlasCityTravelRules.parse(source));
    }
    @Test void hazardIdentifiersCannotBecomeArbitraryResourcePaths() throws Exception {
        var source=json();source.getAsJsonArray("hazard_blocks").add("not a namespaced block");
        assertThrows(IllegalArgumentException.class,() -> AtlasCityTravelRules.parse(source));
    }
}
