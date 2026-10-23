package com.example.myvillage.combat.foundation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 不启动服务器；覆盖 fixture 参数绕过与过宽选择器的拒绝边界。 */
class FoundationCommandsTest {
    @Test void targetCountIsBounded() {
        assertFalse(FoundationCommands.validCount(0));
        assertFalse(FoundationCommands.validCount(-1));
        assertTrue(FoundationCommands.validCount(1));
        assertTrue(FoundationCommands.validCount(64));
        assertFalse(FoundationCommands.validCount(65));
    }
    @Test void shieldsRejectNonfiniteNonpositiveOrUnboundedInputs() {
        assertTrue(FoundationCommands.validFixture(8, 60, false));
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, 0, 1000001})
            assertFalse(FoundationCommands.validFixture(invalid, 60, false));
        assertFalse(FoundationCommands.validFixture(8, 0, false));
        assertFalse(FoundationCommands.validFixture(8, 72001, false));
    }
    @Test void modifierAllowsBoundedReductionAndRejectsOutOfRange() {
        assertTrue(FoundationCommands.validFixture(-1, 1, true));
        assertTrue(FoundationCommands.validFixture(-.5, 60, true));
        assertTrue(FoundationCommands.validFixture(0, 60, true));
        assertFalse(FoundationCommands.validFixture(-1.01, 60, true));
        assertFalse(FoundationCommands.validFixture(16.01, 60, true));
        assertFalse(FoundationCommands.validFixture(Double.NaN, 60, true));
    }
}
