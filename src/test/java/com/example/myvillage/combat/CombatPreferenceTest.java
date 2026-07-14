package com.example.myvillage.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class CombatPreferenceTest {
    @Test
    void defaultsToVanillaAndTogglesImmutably() {
        CombatPreference original = CombatPreference.defaultPreference();
        CombatPreference toggled = original.toggled();

        assertEquals(CombatMode.VANILLA, original.combatMode());
        assertEquals(CombatMode.CULTIVATION, toggled.combatMode());
        assertEquals(CombatMode.VANILLA, toggled.toggled().combatMode());
    }

    @Test
    void codecPersistsOnlyTheNamedMode() {
        CombatPreference preference = new CombatPreference(CombatMode.CULTIVATION);
        JsonObject encoded = CombatPreference.CODEC.encodeStart(JsonOps.INSTANCE, preference)
                .getOrThrow()
                .getAsJsonObject();

        assertEquals(1, encoded.size());
        assertEquals("cultivation", encoded.get("combat_mode").getAsString());
        assertEquals(
                preference,
                CombatPreference.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertTrue(CombatMode.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive("invalid"))
                .error().isPresent());
        assertTrue(CombatMode.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive("VANILLA"))
                .error().isPresent());
    }
}
