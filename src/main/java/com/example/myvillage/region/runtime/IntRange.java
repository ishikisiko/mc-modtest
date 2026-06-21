package com.example.myvillage.region.runtime;

/**
 * Immutable {@code [lo, hi]} integer range — the Java mirror of the Python
 * {@code (lo, hi)} tuples used for {@code qi}, {@code danger},
 * {@code region_count}, and {@code tier_range}. {@code lo <= hi} is the
 * constructor's contract (validated by the loaders that produce instances).
 */
public record IntRange(int lo, int hi) {
    public IntRange {
        if (lo > hi) {
            throw new IllegalArgumentException(
                    "IntRange: lo (" + lo + ") > hi (" + hi + ")");
        }
    }
}
