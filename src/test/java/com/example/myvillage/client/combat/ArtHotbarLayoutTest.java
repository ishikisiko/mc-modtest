package com.example.myvillage.client.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArtHotbarLayoutTest {
    @Test void fitsNarrowAndWideScreensWithoutCoveringOffhandOrHotbar() {
        for(int width:new int[]{320,321,427,640,854,1280}) {
            assertTrue(ArtHotbarLayout.fits(width,240));
            var left=ArtHotbarLayout.slot(width,240,0);var right=ArtHotbarLayout.slot(width,240,1);
            assertTrue(left.x()>=2);assertTrue(right.x()+right.size()<=width-2);
            assertTrue(left.x()+left.size()<width/2-120);
            assertTrue(right.x()>width/2+120);
            assertEquals(left.y(),right.y());assertTrue(left.y()>=240-30);
            var wave=ArtHotbarLayout.slot(width,240,2);var dodge=ArtHotbarLayout.slot(width,240,3);
            assertEquals(left.x(),wave.x());assertEquals(right.x(),dodge.x());
            assertTrue(wave.y()+wave.size()<left.y());assertEquals(wave.y(),dodge.y());
            assertTrue(wave.y()>=2);
        }
        assertFalse(ArtHotbarLayout.fits(280,240));
    }
    @Test void cooldownMaskStaysBoundedAndDecreases() {
        assertEquals(22,ArtHotbarLayout.coverHeight(200,200,22));
        assertEquals(11,ArtHotbarLayout.coverHeight(100,200,22));
        assertEquals(0,ArtHotbarLayout.coverHeight(0,200,22));
        assertEquals(0,ArtHotbarLayout.coverHeight(-20,200,22));
        assertEquals(22,ArtHotbarLayout.coverHeight(300,200,22));
    }
    @Test void lastSecondKeepsReadableTenthsWithoutReadyZero() {
        assertEquals("",ArtHotbarLayout.cooldownText(0));
        assertEquals("10",ArtHotbarLayout.cooldownText(200));
        assertEquals("2",ArtHotbarLayout.cooldownText(21));
        assertEquals("0.5",ArtHotbarLayout.cooldownText(10));
    }
}
