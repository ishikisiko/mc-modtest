package com.example.myvillage.sim;

/**
 * A display date in the 启元 era (design §3.1). {@code year} is always positive; {@code beforeEra}
 * selects {@code world_sim.date.before_era} (启元前 N 年) over {@code world_sim.date.era}.
 *
 * @param dayOfYear 0-based day within the sim year
 */
public record SimDate(long day, boolean beforeEra, long year, int dayOfYear) {
    public static final String KEY_ERA = "world_sim.date.era";
    public static final String KEY_BEFORE_ERA = "world_sim.date.before_era";

    public static SimDate of(long day, long prehistoryDays, int daysPerYear) {
        if (day >= prehistoryDays) {
            long since = day - prehistoryDays;
            return new SimDate(day, false, Math.floorDiv(since, daysPerYear) + 1, Math.floorMod(since, daysPerYear));
        }
        long before = prehistoryDays - day;
        long year = (before + daysPerYear - 1) / daysPerYear;
        return new SimDate(day, true, year, Math.floorMod(day - prehistoryDays, daysPerYear));
    }

    /** The language key for this date; its single param is {@link #year()}. */
    public String textKey() {
        return beforeEra ? KEY_BEFORE_ERA : KEY_ERA;
    }

    /** A sortable signed year: positive in the era, {@code -N} for 启元前 N 年. */
    public long signedYear() {
        return beforeEra ? -year : year;
    }
}
