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
            for(int i=0;i<8;i++) {
                var box=ArtHotbarLayout.slot(width,240,i);
                assertTrue(box.x()>=2 && box.x()+box.size()<=width-2);
                assertTrue(box.y()>=2 && box.y()+box.size()<=238);
                assertTrue(i%2==0?box.x()+box.size()<width/2-120:box.x()>width/2+120);
                for(int j=0;j<i;j++) {
                    var other=ArtHotbarLayout.slot(width,240,j);
                    assertTrue(box.x()+box.size()<other.x() || other.x()+other.size()<box.x()
                            || box.y()+box.size()<other.y() || other.y()+other.size()<box.y());
                }
            }
        }
        assertFalse(ArtHotbarLayout.fits(280,240));
    }
    @Test void fillsOutwardsBeforeStartingNextRow() {
        // One, two, three and four columns, including the exact width transitions.
        for(int[] sample:new int[][]{{320,1},{353,1},{354,2},{409,2},{410,3},{465,3},{466,4},{1280,4}}) {
            int width=sample[0],columns=sample[1];
            for(int side=0;side<2;side++)for(int ordinal=1;ordinal<4;ordinal++) {
                var box=ArtHotbarLayout.slot(width,240,ordinal*2+side);
                var previous=ArtHotbarLayout.slot(width,240,(ordinal-1)*2+side);
                if(ordinal%columns!=0) {
                    assertEquals(previous.y(),box.y());
                    assertEquals(side==0?-28:28,box.x()-previous.x());
                } else {
                    assertEquals(ArtHotbarLayout.slot(width,240,side).x(),box.x());
                    assertTrue(box.y()+box.size()<previous.y());
                }
            }
        }
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
