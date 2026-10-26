package com.example.myvillage.cultivation.crafting;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.myvillage.cultivation.crafting.CraftingAptitudes.Profession.*;

class CraftingAptitudesTest {
    @Test void codecPreservesInitializationAndBothIndependentValues() {
        for (var state : new CraftingAptitudes[] {CraftingAptitudes.EMPTY, new CraftingAptitudes(1, true, 0, 100),
                new CraftingAptitudes(1, true, 67, 96)}) {
            var json = CraftingAptitudes.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
            assertEquals(state, CraftingAptitudes.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
    }
    @Test void legacyCodecOmitsOnlyAddedProfessionAndDoesNotInventItsScore() {
        var legacy = CraftingAptitudes.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"version\":1,\"initialized\":true,\"alchemy\":12,\"forging\":93}")).getOrThrow();
        assertEquals(12, legacy.alchemy()); assertEquals(93, legacy.forging());
        assertFalse(legacy.talismanInitialized()); assertEquals(-1, legacy.talisman());
        assertEquals(0, legacy.qualityCap(TALISMAN, CraftingRules.get()));
        var complete = legacy.withScore(TALISMAN, 90);
        assertTrue(complete.talismanInitialized()); assertEquals(4, complete.qualityCap(TALISMAN, CraftingRules.get()));
        var json = CraftingAptitudes.CODEC.encodeStart(JsonOps.INSTANCE, complete).getOrThrow();
        assertEquals(complete, CraftingAptitudes.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(complete.talisman(), complete.withScore(ALCHEMY, 33).talisman());
        assertEquals(complete.talisman(), complete.withScore(FORGING, 44).talisman());
        assertThrows(IllegalArgumentException.class, () -> new CraftingAptitudes(1, false, 0, 0, 0));
    }
    @Test void invalidSerializedStateIsRejectedWithoutInstallingPartialValues() {
        for (String json : new String[] {
                "{\"version\":2,\"initialized\":true,\"alchemy\":1,\"forging\":2}",
                "{\"version\":1,\"initialized\":true,\"alchemy\":-1,\"forging\":2}",
                "{\"version\":1,\"initialized\":true,\"alchemy\":1,\"forging\":101}",
                "{\"version\":1,\"initialized\":false,\"alchemy\":1,\"forging\":0}",
                "{\"version\":1,\"alchemy\":1,\"forging\":2}"}) {
            assertTrue(CraftingAptitudes.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent(), json);
        }
    }
    @Test void settingOneProfessionPreservesTheOtherAndPendingHasNoEligibleQuality() {
        var rules = CraftingRules.get();
        var current = new CraftingAptitudes(1, true, 41, 71);
        assertEquals(new CraftingAptitudes(1, true, 90, 71), current.withScore(ALCHEMY, 90));
        assertEquals(new CraftingAptitudes(1, true, 41, 0), current.withScore(FORGING, 0));
        assertEquals(0, CraftingAptitudes.EMPTY.qualityCap(ALCHEMY, rules));
        assertFalse(CraftingAptitudes.EMPTY.allowsQuality(ALCHEMY, 1, rules));
        assertEquals(1, new CraftingAptitudes(1, true, 0, 0).qualityCap(ALCHEMY, rules));
        assertTrue(new CraftingAptitudes(1, true, 0, 0).allowsQuality(ALCHEMY, 1, rules));
        assertFalse(current.allowsQuality(ALCHEMY, 3, rules));
        assertTrue(current.allowsQuality(FORGING, 3, rules));
        assertThrows(IllegalStateException.class, () -> CraftingAptitudes.EMPTY.withScore(ALCHEMY, 10));
        assertThrows(IllegalArgumentException.class, () -> current.withScore(ALCHEMY, 101));
    }
}
