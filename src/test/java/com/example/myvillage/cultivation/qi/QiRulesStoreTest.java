package com.example.myvillage.cultivation.qi;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QiRulesStoreTest {
    @Test
    void malformedReloadKeepsThePreviousCompleteConfiguration() {
        QiRulesStore store = new QiRulesStore();
        QiRules original = new QiRules(20, 1, 3, 60);
        store.load(() -> original);
        assertThrows(RuntimeException.class, () -> store.load(() -> QiRules.CODEC.parse(
                JsonOps.INSTANCE, JsonParser.parseString("""
                {"regen_interval_ticks":5,"regen_amount":-1,"meditation_multiplier":3,"spend_delay_ticks":60}
                """)).getOrThrow()));
        assertSame(original, store.get());
        QiRules replacement = new QiRules(10, 2, 4, 80);
        store.load(() -> replacement);
        assertSame(replacement, store.get());
    }

    @Test
    void missingStartupRulesAndStoppedServerHaveNoSilentFallback() {
        QiRulesStore store = new QiRulesStore();
        assertThrows(IllegalStateException.class, store::get);
        assertThrows(IllegalStateException.class, () -> store.load(() -> {
            throw new IllegalStateException("Missing resource");
        }));
        assertThrows(IllegalStateException.class, store::get);
        store.load(() -> new QiRules(20, 1, 3, 60));
        store.clear();
        assertThrows(IllegalStateException.class, store::get);
    }
}
