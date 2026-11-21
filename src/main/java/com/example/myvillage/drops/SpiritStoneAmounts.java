package com.example.myvillage.drops;

import java.util.Set;

/** Quantity bounds before Looting, using the monster's major cultivation realm. */
public final class SpiritStoneAmounts {
    static final int MAX_FIXED_COUNT = 2048;
    private static final Set<String> ITEMS = Set.of(
            "yuanxian:low_grade_spirit_stone", "yuanxian:medium_grade_spirit_stone", "yuanxian:high_grade_spirit_stone");

    private SpiritStoneAmounts() {}

    static boolean isStone(String item) { return ITEMS.contains(item); }

    public record Bounds(int min, int max) {}

    public static Bounds forRealm(int min, int max, int level) {
        return new Bounds(scale(min, Math.max(0, level - 1)), scale(max, Math.max(0, level)));
    }

    private static int scale(int value, int exponent) {
        return Math.toIntExact((long) Math.scalb(value, exponent));
    }
}
