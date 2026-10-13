package com.example.myvillage.client.cultivation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QiBarLayoutTest {
    @Test void fitsSupportedGuiSizesAboveVanillaStatusRows() {
        for (int[] size : new int[][]{{480,270},{427,240},{320,240}}) {
            var bar = QiBarLayout.at(size[0], size[1]);
            assertTrue(bar.x() >= 0);
            assertTrue(bar.x() + bar.width() <= size[0]);
            assertTrue(bar.y() - 22 >= 0);
            assertTrue(bar.y() + 9 < size[1] - 49);
            assertEquals(91, bar.width());
        }
    }

    @Test void displayBoundsCorruptOrStaleBalancesWithoutOverflow() {
        var bar = QiBarLayout.at(320, 240);
        assertEquals(0, bar.fill(10, 0));
        assertEquals(0, bar.fill(-1, 30));
        assertEquals(91, bar.fill(Long.MAX_VALUE, 30));
        assertEquals(91, bar.fill(Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(46, bar.fill(15, 30));
    }
}
