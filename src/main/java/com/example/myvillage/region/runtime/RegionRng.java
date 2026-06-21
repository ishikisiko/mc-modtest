package com.example.myvillage.region.runtime;

import java.util.List;

/**
 * A single seeded RNG stream over the world seed — Java mirror of
 * {@code RegionRng} in {@code tools/buildgen/region_topology.py}.
 *
 * <p>Each draw carries a unique monotonically-numbered tag so no two draws
 * collide, while remaining a pure function of {@code (seed, tag)}:
 * regenerating with the same seed replays the exact draw sequence. The tag
 * format is {@code "<stream>:<n>:<label>"} where {@code <n>} is the
 * monotonically-increasing counter; this is cheap to replicate and is the
 * parity contract between the Python and Java generators.
 *
 * <p>The method order matters: every {@code range}/{@code pick}/{@code chance}
 * call advances the counter, so the Java generator must invoke the stream in
 * the exact same control-flow order as the Python module to stay bit-identical.
 */
public final class RegionRng {
    private final long seed;
    private final String stream;
    private int n = 0;

    public RegionRng(long seed) {
        this(seed, "region");
    }

    public RegionRng(long seed, String stream) {
        this.seed = seed;
        this.stream = stream;
    }

    /** The world seed this stream is derived from. */
    public long seed() {
        return seed;
    }

    private String tag(String label) {
        n += 1;
        return stream + ":" + n + ":" + label;
    }

    /**
     * Inclusive deterministic integer in {@code [lo, hi]}. Matches Python
     * {@code RegionRng.range} bit-for-bit via unsigned modulus.
     */
    public int range(int lo, int hi, String label) {
        if (lo > hi) {
            throw new IllegalArgumentException(
                    "RegionRng.range: lo (" + lo + ") > hi (" + hi + ")");
        }
        int span = hi - lo;
        long h = RegionHash.hash64(seed, tag(label));
        return lo + (int) Long.remainderUnsigned(h, (span + 1L));
    }

    /** Inclusive deterministic integer with the default label {@code "r"}. */
    public int range(int lo, int hi) {
        return range(lo, hi, "r");
    }

    /**
     * Deterministic selection from a non-empty list by unsigned modulus.
     * Matches Python {@code RegionRng.pick} bit-for-bit. Returns the selected
     * index so the caller can resolve a list of immutable objects without a
     * mutation (the Python {@code pick} returns the element itself).
     */
    public int pickIndex(int size, String label) {
        if (size <= 0) {
            throw new IllegalArgumentException("RegionRng.pick: options must be non-empty");
        }
        long h = RegionHash.hash64(seed, tag(label));
        return (int) Long.remainderUnsigned(h, size);
    }

    /** Convenience: pick an element from a non-empty list. */
    public <T> T pick(List<T> options, String label) {
        return options.get(pickIndex(options.size(), label));
    }

    /** Convenience: pick an element with the default label {@code "p"}. */
    public <T> T pick(List<T> options) {
        return pick(options, "p");
    }

    /**
     * Deterministic boolean, true with probability {@code p} in {@code [0,1]}.
     * Matches Python {@code RegionRng.chance} bit-for-bit: draws
     * {@code hash64 % 1000} and compares to {@code (int)(p * 1000)} (truncation
     * toward zero, matching Python {@code int()}).
     */
    public boolean chance(double p, String label) {
        if (p <= 0.0) {
            return false;
        }
        if (p >= 1.0) {
            return true;
        }
        long h = RegionHash.hash64(seed, tag(label));
        return Long.remainderUnsigned(h, 1000L) < (int) (p * 1000);
    }

    /** Deterministic boolean with the default label {@code "c"}. */
    public boolean chance(double p) {
        return chance(p, "c");
    }
}
