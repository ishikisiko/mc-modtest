package com.example.myvillage.client.cultivation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwakeningTooltipLayoutTest {
    @Test void completeTooltipKeepsSixPixelMarginsAtEveryPointerEdge() {
        for (int[] size : new int[][]{{480,270},{427,240},{320,240}}) {
            for (int[] pointer : new int[][]{{0,0},{size[0],0},{0,size[1]},
                    {size[0],size[1]},{size[0]/2,size[1]/2}}) {
                for (int[] text : new int[][]{{140,83},{260,173},{260,213},{1200,1500}}) {
                    var box = AwakeningTooltipLayout.place(size[0], size[1], pointer[0], pointer[1], text[0], text[1]);
                    assertTrue(box.x() >= 6 && box.y() >= 6);
                    assertTrue(box.right() <= size[0] - 6 && box.bottom() <= size[1] - 6);
                    assertTrue(text[0] * box.textScale() <= box.width() - 14 + .00001);
                    assertTrue(text[1] * box.textScale() <= box.height() - 14 + .00001);
                    assertTrue(box.textScale() > 0 && box.textScale() <= 1);
                }
            }
        }
    }
    @Test void normalCompleteEffectDescriptionUsesNativeFontSize() {
        for (int width : new int[]{480,427,320}) {
            int wrap = AwakeningTooltipLayout.wrapWidth(width);
            assertEquals(260, wrap);
            assertTrue(AwakeningTooltipLayout.widestWrap(width) + 26 <= width);
            assertEquals(1, AwakeningTooltipLayout.place(width,240,width-4,240-4,wrap,173).textScale());
        }
    }
}
