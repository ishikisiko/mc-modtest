package com.example.myvillage.sim.engine;

/**
 * Per-year to per-day conversion (design §3.1). StrictMath keeps results identical on every JVM.
 */
public final class Rates {
    private Rates() {
    }

    /** Probability per day for an event with probability {@code perYear} per year. */
    public static double perDay(double perYear, int daysPerYear) {
        if (perYear <= 0.0) {
            return 0.0;
        }
        if (perYear >= 1.0) {
            return 1.0;
        }
        return 1.0 - StrictMath.pow(1.0 - perYear, 1.0 / daysPerYear);
    }

    /** Chance per day of at least one occurrence, for an expected {@code countPerYear} occurrences a year. */
    public static double poissonPerDay(double countPerYear, int daysPerYear) {
        if (countPerYear <= 0.0) {
            return 0.0;
        }
        return 1.0 - StrictMath.exp(-countPerYear / daysPerYear);
    }

    /** Linear quantity per day. */
    public static double linear(double perYear, int daysPerYear) {
        return perYear / daysPerYear;
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
