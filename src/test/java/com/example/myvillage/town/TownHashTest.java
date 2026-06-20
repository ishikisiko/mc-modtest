package com.example.myvillage.town;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TownHash} — mirror of {@code test_town_hash.py}.
 */
class TownHashTest {

    private static final long MASK64 = 0xFFFFFFFFFFFFFFFFL;

    @Test
    void hash64IsDeterministicForSamePair() {
        long[] seeds = {0L, 1L, 42L, 20260618L, 1L << 62, MASK64};
        String[] tags = {"cx", "lane_s", "family"};
        for (long seed : seeds) {
            for (String tag : tags) {
                long h1 = TownHash.hash64(seed, tag);
                long h2 = TownHash.hash64(seed, tag);
                assertEquals(h1, h2, "non-deterministic for (" + seed + "," + tag + ")");
            }
        }
    }

    @Test
    void hash64DiffersAcrossTags() {
        long seed = 20260618L;
        long a = TownHash.hash64(seed, "a");
        long b = TownHash.hash64(seed, "b");
        assertNotEquals(a, b, "different tags collided");
    }

    @Test
    void range64RespectsInclusiveBounds() {
        for (int seed = 0; seed < 200; seed++) {
            int[][] ranges = {{-4, 4}, {-2, 2}, {-3, 3}, {0, 0}, {5, 5}, {0, 5}};
            for (int[] r : ranges) {
                int v = TownHash.range64(seed, "tag", r[0], r[1]);
                assertTrue(v >= r[0] && v <= r[1],
                        v + " not in [" + r[0] + "," + r[1] + "] for seed=" + seed);
            }
        }
    }

    @Test
    void range64RejectsBadBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> TownHash.range64(0L, "tag", 5, 4));
    }

    @Test
    void pickIsDeterministicAndInOptions() {
        String[] opts = {"square", "circle", "oval", "dshape", "octagon", "trapezoid"};
        for (int seed = 0; seed < 200; seed++) {
            String a = TownHash.pick(seed, "family", opts);
            String b = TownHash.pick(seed, "family", opts);
            assertEquals(a, b, "pick non-deterministic");
            boolean inOpts = java.util.List.of(opts).contains(a);
            assertTrue(inOpts, "pick returned " + a);
        }
    }

    @Test
    void pickRejectsEmptyOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> TownHash.pick(0L, "tag", new String[0]));
    }
}
