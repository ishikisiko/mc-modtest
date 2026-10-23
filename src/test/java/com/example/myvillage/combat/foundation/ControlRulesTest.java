package com.example.myvillage.combat.foundation;

import com.example.myvillage.combat.runtime.CombatReactionMath;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControlRulesTest {
    @Test
    void bundledDurationsRetainExistingEliteAndRepeatSemantics() {
        ControlRules rules = ControlRules.get();
        for (int prior = 0; prior <= 12; prior++) {
            assertEquals(CombatReactionMath.stunTicks(60, prior, 20), rules.rootTicks(60, prior, 20));
            assertEquals(CombatReactionMath.stunTicks(60, prior, 100), rules.rootTicks(60, prior, 100));
        }
        assertEquals(60, rules.rootTicks(60, 0, 20));
        assertEquals(42, rules.rootTicks(60, 1, 20));
        assertEquals(18, rules.rootTicks(60, 0, 100));
        assertEquals(0, rules.rootTicks(60, 40, 20), "连续控制最终衰减为完全拒绝");
    }

    @Test
    void durationIsBoundedAndInvalidRequestsCannotControl() {
        ControlRules rules = ControlRules.get();
        assertEquals(rules.maximumTicks(), rules.rootTicks(Integer.MAX_VALUE, 0, 20));
        assertEquals(0, rules.rootTicks(0, 0, 20));
        assertEquals(0, rules.rootTicks(-1, 0, 20));
        assertEquals(0, rules.rootTicks(60, 0, Double.NaN));
        assertEquals(0, rules.rootTicks(60, 0, Double.POSITIVE_INFINITY));
    }

    @Test
    void malformedConfigCannotSilentlyEnablePermanentOrGrowingControls() {
        assertThrows(IllegalArgumentException.class, () -> ControlRules.parse(JsonParser.parseString("{\"schema\":1}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> new ControlRules(1200, 512, 32, 40,
                1, 100, .3, .95, .0001, .546, .91, .0001));
        assertThrows(IllegalArgumentException.class, () -> new ControlRules(1200, 512, 32, 40,
                .7, 100, .3, .95, .0001, .546, Double.NaN, .0001));
        assertThrows(IllegalArgumentException.class, () -> new ControlRules(1200, 512, 32, 40,
                .7, 100, .3, 1, .0001, .546, .91, .0001));
    }
}
