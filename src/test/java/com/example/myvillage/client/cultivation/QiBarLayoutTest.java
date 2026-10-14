package com.example.myvillage.client.cultivation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QiBarLayoutTest {
    @Test void supportedGuiSizesKeepFrameAwayFromReticleHotbarAndRightEffectIcons() {
        for (int[] size : new int[][]{{480,270},{427,240},{320,240}}) {
            var bar = QiBarLayout.at(size[0], size[1]);
            assertTrue(bar.x() >= 0);
            assertTrue(bar.x() + bar.width() <= size[0]);
            assertEquals(8, bar.frameX());
            assertEquals(8, bar.frameY());
            assertTrue(bar.frameX() + bar.frameWidth() < size[0] / 2 - 8);
            assertTrue(bar.frameY() + bar.frameHeight() < size[1] / 2 - 8);
            assertTrue(bar.frameX() + bar.frameWidth() < size[0] - 52);
            assertTrue(bar.chargeY() + 9 < bar.frameY() + bar.frameHeight());
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

    @Test void chargingStripIsSeparateAndBoundsAllInputs() {
        var bar = QiBarLayout.at(320, 240);
        assertEquals(0, bar.chargeFill(-1));
        assertEquals(0, bar.chargeFill(Double.NaN));
        assertEquals(0, bar.chargeFill(Double.POSITIVE_INFINITY));
        assertEquals(20, bar.chargeFill(.5));
        assertEquals(bar.chargeWidth(), bar.chargeFill(2));
        assertTrue(bar.chargeX() + bar.chargeWidth() <= bar.x() + bar.width());
    }
}
