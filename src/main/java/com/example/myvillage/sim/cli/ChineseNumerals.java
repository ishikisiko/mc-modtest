package com.example.myvillage.sim.cli;

/**
 * Renders a non-negative integer the way a chronicle writes it: 41 → 四十一, 105 → 一百零五,
 * 12 → 十二, 2000 → 二千. Used for all-digit params when the language is Chinese.
 */
public final class ChineseNumerals {
    private static final String DIGITS = "零一二三四五六七八九";
    private static final String[] UNITS = {"", "十", "百", "千"};

    private ChineseNumerals() {
    }

    public static String of(long n) {
        if (n < 0) {
            return "负" + of(-n);
        }
        if (n < 10) {
            return String.valueOf(DIGITS.charAt((int) n));
        }
        if (n >= 10_000) {
            long high = n / 10_000;
            long low = n % 10_000;
            return of(high) + "万" + (low == 0 ? "" : (low < 1000 ? "零" : "") + of(low));
        }
        StringBuilder out = new StringBuilder();
        String s = Long.toString(n);
        boolean pendingZero = false;
        for (int i = 0; i < s.length(); i++) {
            int d = s.charAt(i) - '0';
            int unit = s.length() - 1 - i;
            if (d == 0) {
                pendingZero = out.length() > 0;
                continue;
            }
            if (pendingZero) {
                out.append('零');
                pendingZero = false;
            }
            if (!(d == 1 && unit == 1 && out.length() == 0)) {
                out.append(DIGITS.charAt(d));
            }
            out.append(UNITS[unit]);
        }
        return out.toString();
    }

    /** Whether a param is a plain non-negative integer. */
    public static boolean isNumber(String s) {
        if (s.isEmpty() || s.length() > 9) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }
}
