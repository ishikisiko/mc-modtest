package com.example.myvillage.sim;

/**
 * Hash-derived randomness (design §4): every decision draws from
 * {@code at(seed, day, subject, purpose[, salt])}, a splitmix64 mix of those longs, and never from a
 * shared stream, so adding a behaviour or a person does not reshuffle any other decision. The
 * returned instance is a tiny local stream for that one decision. Purpose codes are explicit int
 * constants ({@code engine.Purpose}), never enum ordinals.
 */
public final class SimRng {
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;
    private static final long M1 = 0xBF58476D1CE4E5B9L;
    private static final long M2 = 0x94D049BB133111EBL;
    private static final long SEED_SALT = 0x5851F42D4C957F2DL;

    private long state;

    private SimRng(long state) {
        this.state = state;
    }

    public static SimRng at(long seed, long day, long subject, int purpose) {
        return at(seed, day, subject, purpose, 0L);
    }

    public static SimRng at(long seed, long day, long subject, int purpose, long salt) {
        long h = mix(seed ^ SEED_SALT);
        h = mix(h ^ day);
        h = mix(h ^ subject);
        h = mix(h ^ purpose);
        h = mix(h ^ salt);
        return new SimRng(h);
    }

    /** splitmix64 finalizer with the golden-ratio increment. */
    public static long mix(long z) {
        z += GOLDEN;
        z = (z ^ (z >>> 30)) * M1;
        z = (z ^ (z >>> 27)) * M2;
        return z ^ (z >>> 31);
    }

    public long nextLong() {
        state += GOLDEN;
        long z = state;
        z = (z ^ (z >>> 30)) * M1;
        z = (z ^ (z >>> 27)) * M2;
        return z ^ (z >>> 31);
    }

    /** Uniform in [0, 1). */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** Uniform in [0, bound). */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive, got " + bound);
        }
        return (int) Long.remainderUnsigned(nextLong(), bound);
    }

    /** Uniform integer in [lo, hi]. */
    public int range(int lo, int hi) {
        if (hi < lo) {
            throw new IllegalArgumentException("range " + lo + ".." + hi);
        }
        return lo + nextInt(hi - lo + 1);
    }

    /** Uniform double in [lo, hi). */
    public double uniform(double lo, double hi) {
        return lo + (hi - lo) * nextDouble();
    }

    public boolean chance(double p) {
        if (p <= 0.0) {
            return false;
        }
        if (p >= 1.0) {
            return true;
        }
        return nextDouble() < p;
    }

    /** Index drawn with probability proportional to {@code weights[i]}; -1 when all are zero. */
    public int weighted(double[] weights) {
        double total = 0.0;
        for (double w : weights) {
            total += Math.max(0.0, w);
        }
        if (total <= 0.0) {
            return -1;
        }
        double roll = nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            double w = Math.max(0.0, weights[i]);
            if (roll < w) {
                return i;
            }
            roll -= w;
        }
        for (int i = weights.length - 1; i >= 0; i--) {
            if (weights[i] > 0.0) {
                return i;
            }
        }
        return -1;
    }

    /** Rounds {@code value} up or down at random so the expectation is {@code value}. */
    public int roundStochastic(double value) {
        double floor = Math.floor(value);
        return (int) floor + (chance(value - floor) ? 1 : 0);
    }
}
