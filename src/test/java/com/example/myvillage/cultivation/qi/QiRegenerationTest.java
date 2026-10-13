package com.example.myvillage.cultivation.qi;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QiRegenerationTest {
    private static final QiRules RULES = new QiRules(20, 1, 3, 60);

    @Test
    void spendDelayAndFollowingIntervalsAreExact() {
        QiRegeneration timer = new QiRegeneration(RULES);
        assertEquals(0, ticks(timer, 59, 10000, false, false));
        assertEquals(1, timer.tick(RULES, 10000, false, false));
        assertEquals(0, ticks(timer, 19, 10000, false, false));
        assertEquals(1, timer.tick(RULES, 10000, false, false));
        timer.spent(RULES);
        assertEquals(0, ticks(timer, 59, 10000, false, false));
        assertEquals(1, timer.tick(RULES, 10000, false, false));
    }

    @Test
    void fractionalCoreGainAccumulatesWithoutRoundingItAway() {
        QiRegeneration timer = new QiRegeneration(RULES);
        // Four payments of 1.25 => 5, including the initial spend delay.
        assertEquals(5, ticks(timer, 120, 12500, false, false));
    }

    @Test
    void meditationMultipliesRecoveryAndLeavingItUsesTheCurrentState() {
        QiRegeneration timer = new QiRegeneration(RULES);
        assertEquals(3, ticks(timer, 60, 10000, true, false));
        assertEquals(1, ticks(timer, 20, 10000, false, false));
    }

    @Test
    void capAndNewSpendDiscardBankedFractions() {
        QiRegeneration timer = new QiRegeneration(RULES);
        assertEquals(1, ticks(timer, 60, 15000, false, false));
        assertEquals(0, ticks(timer, 20, 15000, false, true));
        assertEquals(1, ticks(timer, 20, 15000, false, false));
        timer.spent(RULES);
        assertEquals(1, ticks(timer, 60, 15000, false, false));
    }

    @Test
    void freshLifecycleNeverSkipsSpendDelay() {
        QiRegeneration old = new QiRegeneration(RULES);
        ticks(old, 40, 10000, false, false);
        QiRegeneration relogged = new QiRegeneration(RULES);
        assertEquals(60, relogged.ticksRemaining());
        assertEquals(0, ticks(relogged, 59, 10000, false, false));
    }

    @Test
    void extremeRuleMultipliersSaturateRatherThanWrap() {
        QiRules extreme = new QiRules(1, Long.MAX_VALUE, Integer.MAX_VALUE, 0);
        assertEquals(Long.MAX_VALUE, new QiRegeneration(extreme).tick(extreme, Integer.MAX_VALUE, true, false));
    }

    @Test
    void malformedOrMissingTuningIsRejected() {
        assertTrue(QiRules.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{} ")).isError());
        assertTrue(QiRules.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"regen_interval_ticks":0,"regen_amount":1,"meditation_multiplier":3,"spend_delay_ticks":60}
                """)).isError());
        assertThrows(IllegalArgumentException.class, () -> new QiRules(20, 1, 3, -1));
    }

    private static long ticks(QiRegeneration timer, int count, int core, boolean meditation, boolean full) {
        long total = 0;
        for (int tick = 0; tick < count; tick++) total += timer.tick(RULES, core, meditation, full);
        return total;
    }
}
