package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.meditation.MeditationState;

/** Pure, bounded decorative geometry in the approved 400-unit ink canvas. No gameplay state. */
public final class RealmInkMotion {
    public static final double TAU = Math.PI * 2;
    private static final double[] CORE_TILTS = {-.45, .55, -.9};
    private static final Point[] SOUL = {
            new Point(200, 266, 1), new Point(176, 250, 1), new Point(174, 219, 1), new Point(186, 188, 1),
            new Point(200, 148, 1), new Point(215, 188, 1), new Point(227, 219, 1), new Point(225, 250, 1)
    };

    public enum Mode {
        IDLE, PREPARING, NORMAL, SPIRIT, CONSOLIDATING, ADVANCING, BOTTLENECK, STUDY;
        public boolean moving() { return this != IDLE; }
        public boolean advancing() { return this == ADVANCING || this == BOTTLENECK; }
    }

    public record Point(double x, double y, double z) {}

    /** Hidden/closed screens cannot accumulate a large animation jump on the next frame. */
    public static final class Clock {
        private long last = -1;
        private double seconds;
        private boolean wasRunning;

        public double sample(long millis, boolean running) {
            if (last >= 0 && running && wasRunning) {
                seconds += Math.max(0, Math.min(50, millis - last)) / 1000.0;
            }
            last = millis;
            wasRunning = running;
            return seconds;
        }
    }

    private RealmInkMotion() {}

    public static Mode mode(MeditationState state, boolean full, boolean study) {
        if (state == null || state == MeditationState.IDLE) return Mode.IDLE;
        if (state.preparing()) return Mode.PREPARING;
        if (state == MeditationState.ADVANCING_BOTTLENECK) return Mode.BOTTLENECK;
        if (state == MeditationState.ADVANCING_ORDINARY) return Mode.ADVANCING;
        if (study) return Mode.STUDY;
        if (full) return Mode.CONSOLIDATING;
        return state == MeditationState.MEDITATING_SPIRIT ? Mode.SPIRIT : Mode.NORMAL;
    }

    public static double fraction(double n) { return n - Math.floor(n); }

    public static Point path(RealmInkStyle style, int index, double u, boolean consolidating) {
        return switch (style) {
            case FOUNDATION -> cubic(new Point(83 + index * 78, 62 + Math.sin(index) * 7, 1),
                    new Point(50 + index * 98, 118, 1), new Point(173 + index * 15, 145, 1),
                    new Point(200, 220, 1), Math.max(0, Math.min(1, u)));
            case CORE -> ellipse(u, (109 + index * 9) * (consolidating ? .83 : 1),
                    (41 + index * 10) * (consolidating ? .83 : 1),
                    CORE_TILTS[Math.floorMod(index, 3)], 200, 198);
            case SOUL -> soul(u);
            default -> {
                double a = u * TAU - Math.PI / 2;
                double r = (consolidating ? 79 : 118) + Math.sin(a * 3 + index) * 7;
                yield new Point(200 + Math.cos(a) * r, 198 + Math.sin(a) * r * .98, 1);
            }
        };
    }

    public static Point ellipse(double t, double rx, double ry, double rotation, double cx, double cy) {
        double a = t * TAU, x = Math.cos(a) * rx, y = Math.sin(a) * ry;
        return new Point(cx + x * Math.cos(rotation) - y * Math.sin(rotation),
                cy + x * Math.sin(rotation) + y * Math.cos(rotation), Math.sin(a));
    }

    public static Point inward(double u, int index) {
        double a = index * TAU / 5 + u * 1.2, r = 159 * (1 - u) + 16;
        return new Point(200 + Math.cos(a) * r, 200 + Math.sin(a) * r, 1);
    }

    public static Point spiral(double u, int index) {
        double a = u * TAU * 1.25 + index * Math.PI / 2, r = 48 * (1 - u) + 4;
        return new Point(202 + Math.cos(a) * r, 260 + Math.sin(a) * r * .6, 1);
    }

    public static Point soulFeeder(double u, int side) {
        return cubic(new Point(200 + side * 132, 224, 1), new Point(200 + side * 106, 277, 1),
                new Point(200 + side * 24, 270, 1), new Point(200, 266, 1), u);
    }

    private static Point cubic(Point a, Point b, Point c, Point d, double t) {
        double v = 1 - t;
        return new Point(v * v * v * a.x + 3 * v * v * t * b.x + 3 * v * t * t * c.x + t * t * t * d.x,
                v * v * v * a.y + 3 * v * v * t * b.y + 3 * v * t * t * c.y + t * t * t * d.y, 1);
    }

    private static Point soul(double u) {
        double t = fraction(u) * SOUL.length;
        int i = (int) t;
        double f = t - i;
        Point a = SOUL[(i + 7) % 8], b = SOUL[i], c = SOUL[(i + 1) % 8], d = SOUL[(i + 2) % 8];
        return new Point(catmull(a.x, b.x, c.x, d.x, f), catmull(a.y, b.y, c.y, d.y, f), 1);
    }

    private static double catmull(double a, double b, double c, double d, double f) {
        return .5 * (2 * b + (-a + c) * f + (2 * a - 5 * b + 4 * c - d) * f * f
                + (-a + 3 * b - 3 * c + d) * f * f * f);
    }
}
