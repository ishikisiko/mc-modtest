package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WoodSpiritDefinitionTest {
    @Test void formerPlaceholderNowDecodesWithBoundedRecovery() throws Exception {
        try (var stream = getClass().getResourceAsStream("/data/myvillage/myvillage/innate_fate/wood_spirit_body.json")) {
            assertNotNull(stream);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            var definition = FateDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            assertTrue(definition.effect("lifesteal_fraction") > 0);
            assertTrue(definition.effect("lifesteal_cap_fraction") > 0);
            assertEquals(definition, FateDefinition.CODEC.parse(JsonOps.INSTANCE,
                    FateDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition).getOrThrow()).getOrThrow());
            assertEquals("spirit_body", definition.group());
            assertTrue(definition.element().isEmpty(), "Existing selected placeholder eligibility remains compatible");
        }
    }

    @Test void rejectsLifestealWithoutAnActualHealingCap() {
        assertThrows(IllegalArgumentException.class, () -> new FateDefinition("name", "description", 4, 5,
                "spirit_body", true, Map.of("lifesteal_fraction", .08), Optional.empty(), 0, 5));
    }
}
