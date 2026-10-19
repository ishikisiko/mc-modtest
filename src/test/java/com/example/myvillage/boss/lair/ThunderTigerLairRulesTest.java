package com.example.myvillage.boss.lair;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThunderTigerLairRulesTest {
    private JsonObject json() throws Exception {
        try (var in = getClass().getResourceAsStream("/data/myvillage/boss/thunder_tiger_lair.json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void bundledLairHasOneSupportedHomeAndThreeBoundPillars() {
        var rules = ThunderTigerLairRules.get();
        assertEquals(3, rules.pillars().size());
        assertTrue(rules.anchor().y() < rules.spawn().y());
        assertTrue(rules.tickInterval() > 0);
    }
    @Test void invalidLimitsPositionsAndPillarContractAreRejected() throws Exception {
        var radius = json(); radius.addProperty("trigger_radius", Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> ThunderTigerLairRules.parse(new StringReader(radius.toString())));
        var outside = json(); outside.getAsJsonArray("spawn").set(0, new JsonPrimitive(1000));
        assertThrows(IllegalArgumentException.class, () -> ThunderTigerLairRules.parse(new StringReader(outside.toString())));
        var pillar = json(); pillar.addProperty("pillar_height", 1);
        assertThrows(IllegalArgumentException.class, () -> ThunderTigerLairRules.parse(new StringReader(pillar.toString())));
        var duplicate = json(); duplicate.getAsJsonArray("pillars").add(duplicate.getAsJsonArray("pillars").get(0));
        assertThrows(IllegalArgumentException.class, () -> ThunderTigerLairRules.parse(new StringReader(duplicate.toString())));
        var fractional = json(); fractional.addProperty("tick_interval", 1.5);
        assertThrows(ArithmeticException.class, () -> ThunderTigerLairRules.parse(new StringReader(fractional.toString())));
    }
}
