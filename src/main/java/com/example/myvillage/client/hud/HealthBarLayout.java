package com.example.myvillage.client.hud;

import java.util.Locale;

/** Fixed screen geometry and display-only arithmetic; never modifies player attributes. */
public record HealthBarLayout(int x, int y, int width, int reservedHeight) {
    public static HealthBarLayout at(int screenWidth, int screenHeight, int baseHeight) {
        return new HealthBarLayout(screenWidth / 2 - 91, screenHeight - baseHeight - 18, 182, baseHeight + 40);
    }
    public int headerY() { return y - 10; }
    public int absorptionY() { return y + 15; }
    public static double finite(double value) { return Double.isFinite(value) ? Math.max(0, value) : 0; }
    public static double ratio(double current, double maximum) {
        return maximum > 0 && Double.isFinite(maximum) ? Math.clamp(finite(current) / maximum, 0, 1) : 0;
    }
    public static int fill(double current, double maximum, int width) {
        return (int)Math.ceil(ratio(current, maximum) * Math.max(0, width));
    }
    public static String number(double value) {
        value = finite(value);
        if (value > 0 && value < 0.1) return "<0.1";
        String text = String.format(Locale.ROOT, "%,.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
    public static String balance(double health, double maximum) {
        return number(health) + " / " + number(maximum);
    }
}
