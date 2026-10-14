package com.example.myvillage.stats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class StatsRulesTest {
    private String source() throws IOException {
        try (var input = StatsRulesTest.class.getResourceAsStream(StatsRules.RESOURCE)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void rejects(Consumer<JsonObject> mutation, String field) throws IOException {
        JsonObject json = JsonParser.parseString(source()).getAsJsonObject();
        mutation.accept(json);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> StatsRules.fromJson(json.toString()));
        assertTrue(error.getMessage().contains(field), error.getMessage());
    }

    @Test void bundledRulesAreImmutableCachedAndHashExactInput() throws Exception {
        StatsRules rules = StatsRules.bundled();
        assertSame(rules, StatsRules.bundled());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source().getBytes(StandardCharsets.UTF_8))), rules.ruleHash());
        assertEquals(rules.ruleHash(), StatsRules.fromJson(new StringReader(source())).ruleHash());
        assertThrows(UnsupportedOperationException.class, () -> rules.realms().clear());
        assertThrows(UnsupportedOperationException.class, () -> rules.playerAliases().clear());
    }

    @Test void unknownMissingDuplicateAndMistypedFieldsAreRejected() throws IOException {
        rejects(json -> json.addProperty("surprise", 1), "surprise");
        rejects(json -> json.remove("limits"), "limits");
        rejects(json -> json.getAsJsonObject("growth").addProperty("minor_slope", "0.05"), "minor_slope");
        rejects(json -> json.getAsJsonArray("realms").get(0).getAsJsonObject().addProperty("mortal", "true"), "mortal");
        rejects(json -> json.addProperty("schema", 1.5), "schema");
        String raw = source();
        assertThrows(IllegalArgumentException.class, () -> StatsRules.fromJson(raw.replace("\"schema\": 1", "\"schema\": 1, \"schema\": 1")));
        assertThrows(IllegalArgumentException.class, () -> StatsRules.fromJson(raw.replace("\"minor_slope\": 0.05", "\"minor_slope\": NaN")));
        assertThrows(IllegalArgumentException.class, () -> StatsRules.fromJson(raw + "{}"));
    }

    @Test void nonfiniteGrowthAndReversedProgressionCannotLoad() throws IOException {
        rejects(json -> json.getAsJsonObject("growth").addProperty("entry_multiplier", 1), "entry_multiplier");
        rejects(json -> json.getAsJsonObject("growth").addProperty("minor_slope", -1), "minor_slope");
        rejects(json -> json.getAsJsonObject("growth").addProperty("minor_slope", 0), "minor_slope");
        rejects(json -> json.getAsJsonObject("growth").addProperty("major_ratio", 1), "major_ratio");
        rejects(json -> json.getAsJsonObject("growth").addProperty("major_ratio", 1.4), "boundary");
        rejects(json -> json.getAsJsonObject("growth").addProperty("major_ratio", 3), "max_health");
        String raw = source();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> StatsRules.fromJson(raw.replace("\"entry_multiplier\": 1.5", "\"entry_multiplier\": 1e999")));
        assertTrue(error.getMessage().contains("entry_multiplier"));
    }

    @Test void duplicateRealmAliasAndInvalidStageFail() throws IOException {
        rejects(json -> json.getAsJsonArray("realms").get(2).getAsJsonObject().addProperty("id", "myvillage:qi_refining"), "duplicate");
        rejects(json -> json.getAsJsonArray("realms").get(2).getAsJsonObject().getAsJsonArray("aliases").add("qi_refining"), "duplicate");
        rejects(json -> json.getAsJsonArray("realms").get(2).getAsJsonObject().addProperty("order", 3), "order");
        rejects(json -> json.getAsJsonArray("realms").get(1).getAsJsonObject().addProperty("stages", 0), "stages");
        rejects(json -> json.getAsJsonArray("player_stage_aliases").get(0).getAsJsonObject().addProperty("stage", 2), "stage");
        rejects(json -> json.getAsJsonArray("player_stage_aliases").add(json.getAsJsonArray("player_stage_aliases").get(0).deepCopy()), "duplicate");
        rejects(json -> json.getAsJsonArray("player_stage_aliases").get(0).getAsJsonObject().addProperty("realm", "missing"), "realm.id");
    }

    @Test void resistanceAndNativeLimitsCannotExceedTheSupportedRange() throws IOException {
        rejects(json -> json.getAsJsonObject("limits").addProperty("max_health", 1025), "max_health");
        rejects(json -> json.getAsJsonObject("limits").addProperty("max_attack", 2049), "max_attack");
        rejects(json -> json.getAsJsonObject("limits").addProperty("max_base_health", 80), "max_health");
        rejects(json -> json.getAsJsonObject("limits").addProperty("max_resistance", 1), "max_resistance");
        rejects(json -> json.getAsJsonObject("limits").addProperty("max_penetration", -1), "max_penetration");
        rejects(json -> json.getAsJsonObject("limits").addProperty("max_total_multiplier", 0.5), "multipliers");
        rejects(json -> json.getAsJsonArray("damage_types").add("magic"), "damage_types");
        rejects(json -> json.getAsJsonArray("damage_types").add("fire"), "duplicate");
    }
}
