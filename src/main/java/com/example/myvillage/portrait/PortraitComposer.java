package com.example.myvillage.portrait;

/**
 * Composes a 64x64 portrait from the part maps. A port of {@code replay()} in
 * {@code tools/portraitgen/replay.py}, the executable spec that equals the Python render pixel for
 * pixel: the steps run in the same order on a grid of roles, the hair finish uses the same four rules
 * on the same masks, and only at the end do the roles become the person's colours. Keeping the
 * drawing in Python and only the replay here means a new hairstyle is a re-export, not Java work.
 */
public final class PortraitComposer {
    private static final int SIZE = PortraitMaps.SIZE;
    private static final byte NONE = 0;
    private static final byte SKIN = role('S');
    private static final byte SKIN_SHADE = role('s');
    private static final byte SKIN_LIGHT = role('L');
    private static final byte BLUSH = role('B');
    private static final byte BLUSH_LIGHT = role('b');
    private static final byte HAIR = role('H');
    private static final byte HAIR_SHADOW = role('h');
    private static final byte HAIR_OUTLINE = role('D');
    private static final byte HAIR_LIGHT = role('I');
    private static final byte HAIR_SHEEN = role('J');
    private static final byte BROW_THROUGH = role('R');

    private PortraitComposer() {
    }

    /** Row-major ARGB, 64x64; 0 where transparent, alpha 255 elsewhere. */
    public static int[] compose(PortraitMaps maps, PortraitSpec spec) {
        return colour(roles(maps, spec), PortraitPalette.colours(spec), spec.dead());
    }

    /** The role grid (role index plus one per cell, 0 = transparent) before the palette. */
    static byte[] roles(PortraitMaps maps, PortraitSpec spec) {
        byte[] grid = new byte[PortraitMaps.CELLS];
        String g = spec.female() ? "f" : "m";
        String old = spec.old() ? "_old" : "";
        String back = PortraitSpec.key(spec.back());
        String front = PortraitSpec.key(spec.front());

        PortraitMaps.Part behind = maps.part("behind", back);
        paint(grid, behind);
        PortraitMaps.Part face = maps.part("face", PortraitSpec.key(spec.face()) + "_" + g + old);
        paint(grid, face);
        // the silhouette is the behind map's mask, not its painted cells: a style may paint outside it
        boolean[] silhouette = behind.mask().clone();
        if (maps.clearsFace(back)) {
            for (int i = 0; i < silhouette.length; i++) {
                silhouette[i] &= !face.mask()[i];
            }
        }
        if (spec.blush()) {
            paint(grid, maps.part("blush", spec.old() ? "old" : "normal"));
        }
        if (spec.nose()) {
            paint(grid, maps.part("nose", g));
        }
        paint(grid, maps.part("mouth", PortraitSpec.key(spec.mouth()) + old));
        paint(grid, maps.part("eyes", PortraitSpec.key(spec.eyeShape()) + "_" + g));
        PortraitMaps.Part locks = maps.part("locks", back);
        paint(grid, locks);
        PortraitMaps.Part bangs = maps.part("bangs", front + "_" + back);
        paint(grid, bangs);
        finish(grid, face.mask(), silhouette, locks.mask(), bangs.mask());

        // brows show through the bangs: over hair the marker takes the see-through role
        byte[] brows = maps.part("brows", PortraitSpec.key(spec.brow()) + "_" + g).roles();
        for (int i = 0; i < brows.length; i++) {
            if (brows[i] != NONE) {
                byte r = grid[i];
                boolean overHair = r == HAIR || r == HAIR_SHADOW || r == HAIR_OUTLINE || r == HAIR_LIGHT || r == HAIR_SHEEN;
                grid[i] = overHair ? BROW_THROUGH : HAIR_SHADOW;
            }
        }
        paint(grid, maps.part("coat", spec.robeKey()));
        paint(grid, maps.part("knot", back));
        if (spec.headwear() != PortraitSpec.Headwear.NONE) {
            paint(grid, maps.part("headwear", PortraitSpec.key(spec.headwear()) + "_" + back));
        }
        if (spec.bandage()) {
            paint(grid, maps.part("marks", "bandage_" + front + "_" + back));
        }
        if (spec.scar()) {
            paint(grid, maps.part("marks", "scar_" + back));
        }
        return grid;
    }

    /**
     * replay.py {@code finish}: the shadow the bangs cast on the forehead, the jaw shade beside the side
     * locks, the outline of all hair, and the line where the front hair meets the face. Each rule reads
     * only masks and writes only one role, so the cell order does not matter.
     */
    private static void finish(byte[] grid, boolean[] face, boolean[] silhouette, boolean[] locks, boolean[] bangs) {
        boolean[] front = new boolean[PortraitMaps.CELLS];
        boolean[] allHair = new boolean[PortraitMaps.CELLS];
        for (int i = 0; i < front.length; i++) {
            front[i] = locks[i] || bangs[i];
            allHair[i] = front[i] || silhouette[i];
        }
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!bangs[y * SIZE + x]) {
                    continue;
                }
                if (!at(front, x, y + 1) && at(face, x, y + 1)) {
                    shadeSkin(grid, x, y + 1);
                    if (at(face, x, y + 2) && !at(front, x, y + 2)) {
                        shadeSkin(grid, x, y + 2);
                    }
                }
            }
        }
        for (int y = 30; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!locks[y * SIZE + x]) {
                    continue;
                }
                int qx = x < 32 ? x + 1 : x - 1;
                if (at(face, qx, y) && !at(front, qx, y) && grid[y * SIZE + qx] == SKIN) {
                    grid[y * SIZE + qx] = SKIN_SHADE;
                }
            }
        }
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (allHair[y * SIZE + x] && (!at(allHair, x + 1, y) || !at(allHair, x - 1, y)
                        || !at(allHair, x, y + 1) || !at(allHair, x, y - 1))) {
                    grid[y * SIZE + x] = HAIR_OUTLINE;
                }
            }
        }
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (front[y * SIZE + x] && (meetsFace(front, face, x, y + 1) || meetsFace(front, face, x + 1, y)
                        || meetsFace(front, face, x - 1, y))) {
                    grid[y * SIZE + x] = HAIR_OUTLINE;
                }
            }
        }
    }

    private static boolean meetsFace(boolean[] front, boolean[] face, int x, int y) {
        return !at(front, x, y) && at(face, x, y);
    }

    /** {@code put_if(x, y, "s", "SLBb")}. */
    private static void shadeSkin(byte[] grid, int x, int y) {
        int i = y * SIZE + x;
        byte r = grid[i];
        if (r == SKIN || r == SKIN_LIGHT || r == BLUSH || r == BLUSH_LIGHT) {
            grid[i] = SKIN_SHADE;
        }
    }

    /** Mask membership; outside the grid is outside every mask. */
    private static boolean at(boolean[] mask, int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE && mask[y * SIZE + x];
    }

    private static void paint(byte[] grid, PortraitMaps.Part part) {
        byte[] roles = part.roles();
        for (int i = 0; i < roles.length; i++) {
            if (roles[i] != NONE) {
                grid[i] = roles[i];
            }
        }
    }

    /** render.py {@code to_image}, including the grey the dead are drawn in. */
    private static int[] colour(byte[] grid, int[] palette, boolean dead) {
        int[] out = new int[grid.length];
        for (int i = 0; i < grid.length; i++) {
            int r = grid[i] & 0xFF;
            if (r == 0) {
                continue;
            }
            int c = palette[r - 1];
            if (dead) {
                int grey = (int) (0.3 * ((c >> 16) & 0xFF) + 0.59 * ((c >> 8) & 0xFF) + 0.11 * (c & 0xFF));
                int red = (int) (grey * 0.72 + 8);
                int green = (int) (grey * 0.72 + 10);
                int blue = (int) (grey * 0.72 + 18);
                c = 0xFF000000 | red << 16 | green << 8 | blue;
            }
            out[i] = c;
        }
        return out;
    }

    private static byte role(char c) {
        int i = PortraitPalette.ROLES.indexOf(c);
        if (i < 0) {
            throw new IllegalStateException("unknown portrait role " + c);
        }
        return (byte) (i + 1);
    }
}
