package com.example.myvillage.portrait;

/**
 * The 16x16 portrait for list rows. Each pixel is the average of the opaque pixels of its 4x4 block,
 * so the background never darkens the edge of the hair the way a plain box filter over transparent
 * black would; a block with no opaque pixel stays transparent.
 */
public final class PortraitThumbnail {
    public static final int SIZE = 16;
    private static final int BLOCK = PortraitMaps.SIZE / SIZE;

    private PortraitThumbnail() {
    }

    /** @param argb64 row-major 64x64 ARGB as {@link PortraitComposer#compose} returns it */
    public static int[] shrink(int[] argb64) {
        if (argb64.length != PortraitMaps.CELLS) {
            throw new IllegalArgumentException("portrait of " + argb64.length + " pixels");
        }
        int[] out = new int[SIZE * SIZE];
        for (int ty = 0; ty < SIZE; ty++) {
            for (int tx = 0; tx < SIZE; tx++) {
                int n = 0;
                int r = 0;
                int g = 0;
                int b = 0;
                for (int dy = 0; dy < BLOCK; dy++) {
                    for (int dx = 0; dx < BLOCK; dx++) {
                        int c = argb64[(ty * BLOCK + dy) * PortraitMaps.SIZE + tx * BLOCK + dx];
                        if ((c >>> 24) == 0) {
                            continue;
                        }
                        n++;
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                    }
                }
                if (n > 0) {
                    out[ty * SIZE + tx] = 0xFF000000 | (r + n / 2) / n << 16 | (g + n / 2) / n << 8 | (b + n / 2) / n;
                }
            }
        }
        return out;
    }
}
