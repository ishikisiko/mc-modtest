package com.example.myvillage.town;

/**
 * Deterministic, parity-safe hash primitives — bit-identical mirror of
 * {@code tools/buildgen/town_hash.py}. Every seed-derived town-geometry
 * parameter (perimeter vocabulary selection, grid jitter, modifier presence)
 * routes through this class so the Java realizer reproduces the Python
 * planner's parameters from the same {@code (seed, tag)} pair without sharing
 * an RNG stream.
 *
 * <p>The primitives are pure-integer (no float). All right shifts use
 * {@code >>>} (logical) because the Python side masks every step to a
 * non-negative 64-bit value; signed vs unsigned interpretation of the
 * intermediate {@code long} does not affect the low 64 bits after add / mul /
 * shift / xor, so the bit pattern is identical across ends.
 */
public final class TownHash {
    // splitmix64 constants (Knuth / Steele & Marsaglia).
    private static final long SPLITMIX_GOLDEN = 0x9E3779B97F4A7C15L;
    private static final long SPLITMIX_M1 = 0xBF58476D1CE4E5B9L;
    private static final long SPLITMIX_M2 = 0x94D049BB133111EBL;
    private static final long FNV_OFFSET = 0xCBF29CE484222325L;  // FNV-1a basis
    private static final long FNV_PRIME = 0x100000001B3L;

    private TownHash() {
    }

    /**
     * 64-bit FNV-1a over the UTF-8 bytes of {@code "seed=<u>;</tag>"} where
     * {@code <u>} is the unsigned decimal of {@code seed}. Matches the Python
     * {@code _fnv1a_tagged} byte-for-byte.
     */
    private static long fnv1aTagged(long seed, String tag) {
        String payload = "seed=" + Long.toUnsignedString(seed) + ";tag=" + tag;
        byte[] bytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        long h = FNV_OFFSET;
        for (byte b : bytes) {
            h ^= (b & 0xFFL);
            h *= FNV_PRIME;  // wraps at 64 bits, matching Python & MASK64
        }
        return h;
    }

    /** splitmix64 finalizer over a 64-bit input (unsigned interpretation). */
    private static long splitmix64(long z) {
        z = z + SPLITMIX_GOLDEN;
        z = (z ^ (z >>> 30)) * SPLITMIX_M1;
        z = (z ^ (z >>> 27)) * SPLITMIX_M2;
        return z ^ (z >>> 31);
    }

    /**
     * Deterministic 64-bit hash of {@code (seed, tag)}. The returned
     * {@code long} carries the same bit pattern as the Python {@code hash64}
     * unsigned integer (compare via {@link Long#compareUnsigned} or bit
     * patterns, not signed ordering).
     */
    public static long hash64(long seed, String tag) {
        return splitmix64(fnv1aTagged(seed, tag));
    }

    /**
     * Inclusive deterministic integer in {@code [lo, hi]} derived from
     * {@code hash64}. Uses unsigned modulus so the Java value matches the
     * Python {@code lo + (hash64 % (span + 1))} bit-for-bit.
     */
    public static int range64(long seed, String tag, int lo, int hi) {
        if (lo > hi) {
            throw new IllegalArgumentException("range64: lo (" + lo + ") > hi (" + hi + ")");
        }
        int span = hi - lo;
        long h = hash64(seed, tag);
        // Unsigned remainder: hash64 is interpreted unsigned on the Python side.
        return lo + (int) Long.remainderUnsigned(h, (span + 1L));
    }

    /**
     * Deterministic selection from a non-empty array by unsigned modulus.
     * Matches Python {@code pick} bit-for-bit.
     */
    public static <T> T pick(long seed, String tag, T[] options) {
        if (options.length == 0) {
            throw new IllegalArgumentException("pick: options must be non-empty");
        }
        return options[(int) Long.remainderUnsigned(hash64(seed, tag), options.length)];
    }
}
