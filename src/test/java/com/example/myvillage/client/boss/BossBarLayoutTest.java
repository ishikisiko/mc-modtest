package com.example.myvillage.client.boss;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossBarLayoutTest {
    @Test void centeredBarReservesQiCornerAndFollowingNativeRowsAtGuiScales() {
        for (int width : new int[]{320, 427, 480, 640, 960}) {
            for (boolean qi : new boolean[]{false, true}) {
                var first = BossBarLayout.at(width, 12, qi, 138, 46);
                assertTrue(first.x() >= 10);
                assertTrue(first.x() + first.width() + 10 <= width);
                assertTrue(Math.abs(2 * first.x() + first.width() - width) <= 1);
                if (qi && first.x() - 10 < 142) assertTrue(first.top() >= 50);
                int nextY = 12 + first.increment();
                assertTrue(nextY - 9 >= first.top() + BossBarLayout.HEIGHT + 6);
                var second = BossBarLayout.at(width, nextY, qi, 138, 46);
                assertTrue(second.top() >= first.top() + BossBarLayout.HEIGHT + 6);
            }
        }
    }
    @Test void emptyAndFullHealthAreExactAndInvalidSamplesAreBounded() {
        var b = BossBarLayout.at(480, 12, false, 138, 46);
        assertEquals(0, b.fill(0)); assertEquals(b.width(), b.fill(1));
        assertEquals(0, b.fill(Float.NaN)); assertEquals(0, b.fill(-1));
        assertEquals(b.width(), b.fill(2)); assertEquals(0, b.fill(Float.POSITIVE_INFINITY));
    }
}
