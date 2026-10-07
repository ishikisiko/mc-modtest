package com.example.myvillage.portrait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** The 16x16 thumbnail averages the opaque pixels of each 4x4 block. */
final class PortraitThumbnailTest {
    @Test
    void averagesOnlyOpaquePixelsAndKeepsEmptyBlocksTransparent() {
        int[] argb = new int[64 * 64];
        // block (0,0): two opaque pixels, red 10 and red 21 -> rounded average 16 (15.5 rounds up)
        argb[0] = 0xFF0A0000;
        argb[1 + 64] = 0xFF150000;
        // block (1,0): fully opaque grey 100
        for (int y = 0; y < 4; y++) {
            for (int x = 4; x < 8; x++) {
                argb[y * 64 + x] = 0xFF646464;
            }
        }
        int[] thumb = PortraitThumbnail.shrink(argb);
        assertEquals(16 * 16, thumb.length);
        assertEquals(0xFF100000, thumb[0]);
        assertEquals(0xFF646464, thumb[1]);
        assertEquals(0, thumb[2]);
        assertEquals(0, thumb[16]);
    }

    @Test
    void aComposedPortraitShrinksWithinItsSilhouette() {
        PortraitSpec spec = new PortraitSpec(true, PortraitSpec.Face.OVAL, PortraitSpec.Skin.PALE,
                PortraitSpec.HairColour.INK_VIOLET, PortraitSpec.Front.HIME, PortraitSpec.Back.HALF_UP,
                PortraitSpec.EyeShape.ROUND, PortraitSpec.EyeColour.SPIRIT, PortraitSpec.Brow.ARCHED,
                PortraitSpec.Mouth.SMILE, true, true, PortraitSpec.Robe.COURT, 0, PortraitSpec.Headwear.JADE_PIN,
                false, false, false, false);
        int[] full = PortraitComposer.compose(PortraitTestData.maps(), spec);
        int[] thumb = PortraitThumbnail.shrink(full);
        for (int ty = 0; ty < 16; ty++) {
            for (int tx = 0; tx < 16; tx++) {
                boolean any = false;
                for (int dy = 0; dy < 4; dy++) {
                    for (int dx = 0; dx < 4; dx++) {
                        any |= full[(ty * 4 + dy) * 64 + tx * 4 + dx] != 0;
                    }
                }
                assertEquals(any, thumb[ty * 16 + tx] != 0, tx + "," + ty);
            }
        }
    }

    @Test
    void rejectsAnythingButSixtyFourSquare() {
        assertThrows(IllegalArgumentException.class, () -> PortraitThumbnail.shrink(new int[16]));
    }
}
