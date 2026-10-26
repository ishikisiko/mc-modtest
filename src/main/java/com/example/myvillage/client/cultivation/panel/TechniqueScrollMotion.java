package com.example.myvillage.client.cultivation.panel;

import java.util.Objects;

/** Decorative state observed from accepted snapshots. Never grants progress or predicts settlement. */
public final class TechniqueScrollMotion {
    public record Point(double x, double y) { }
    private static final double[][] WOOD_LEFT = {{510,735},{660,690},{785,670},{738,620},
            {700,550},{650,455},{701,377},{735,315},{665,250},{595,210}};
    private static final double[][] WOOD_RIGHT = {{1035,720},{900,685},{780,670},{738,620},
            {720,550},{695,455},{790,415},{930,340},{1080,265},{1195,319}};
    private static final double[][] SWORD_LEFT = {{390,690},{570,650},{615,520},{665,470},
            {740,370},{744,150},{765,75}};
    private static final double[][] SWORD_RIGHT = {{1120,690},{950,620},{885,520},{850,465},
            {790,365},{785,150},{774,75}};
    private static final double[][] BREATH_LOOP = {{768,575},{730,530},{760,480},{752,440},
            {744,390},{750,355},{768,352},{790,390},{780,440},{790,475},
            {800,530},{790,560},{768,575}};
    private static final double[][] BREATH_ARM = {{768,575},{690,565},{645,515},{612,506},
            {580,470},{625,440},{665,416}};
    private TechniqueScrollMotion() { }

    public static final class Tracker {
        private String identity, insight = "";
        private long lastTime = -1, points, revision;
        private int stage;
        private boolean initialized, running, preparing;
        private double flow, visibleTime, gainAge = 99, stageAge = 99, insightAge = 99;
        private long gained;

        public void hidden() {
            initialized = false; lastTime = -1; running = false;
            flow = 0; gainAge = stageAge = insightAge = 99; gained = 0;
        }
        public void observe(long now, String identity, long points, int stage, String insight, long revision,
                            boolean running, boolean preparing) {
            Objects.requireNonNull(identity); Objects.requireNonNull(insight);
            if (!initialized || !identity.equals(this.identity)) {
                this.identity = identity; this.points = points; this.stage = stage; this.insight = insight;
                this.revision = revision; this.running = running; this.preparing = preparing;
                lastTime = now; initialized = true; flow = visibleTime = 0;
                gainAge = stageAge = insightAge = 99; gained = 0; return;
            }
            double dt = Math.max(0, Math.min(50, now - lastTime)) / 1000.0;
            lastTime = now; visibleTime += dt; gainAge += dt; stageAge += dt; insightAge += dt;
            if (running && this.running && !preparing && !this.preparing) flow += dt;
            else if (!running || !this.running || preparing) flow = 0;
            // Stale/replaced snapshots establish a new baseline without a success animation.
            boolean forward = points >= this.points && revision >= this.revision;
            if (forward && points > this.points) {
                gained = points - this.points; gainAge = 0; flow = 0;
                if (stage > this.stage) stageAge = 0;
            }
            if (forward && !insight.isEmpty() && !insight.equals(this.insight)) insightAge = 0;
            if (!forward) { gainAge = stageAge = insightAge = 99; gained = 0; }
            this.points = points; this.stage = stage; this.insight = insight; this.revision = revision;
            this.running = running; this.preparing = preparing;
        }
        public double flow() { return flow; }
        public double visibleTime() { return visibleTime; }
        public boolean running() { return running; }
        public boolean preparing() { return preparing; }
        public double gainAge() { return gainAge; }
        public double stageAge() { return stageAge; }
        public double insightAge() { return insightAge; }
        public long gained() { return gained; }
    }

    public static double fraction(double v) { return v - Math.floor(v); }
    public static Point anchor(TechniqueScrollStyle style) {
        return switch (style) {
            case QINGMU, YIMU -> new Point(738, 620);
            case SWORD -> new Point(768, 490);
            default -> new Point(768, 575);
        };
    }
    public static Point incoming(TechniqueScrollStyle style, int side, double t) {
        Point end = anchor(style); int sign = side == 0 ? -1 : 1;
        return cubic(new Point(end.x + sign * 500, end.y + 100),
                new Point(end.x + sign * 360, end.y - 70),
                new Point(end.x + sign * 220, end.y + 90), end, t);
    }
    public static Point path(TechniqueScrollStyle style, int index, double t) {
        t = Math.max(0, Math.min(1, t));
        return switch (style) {
            case QINGMU, YIMU -> chain(t, index == 0 ? WOOD_LEFT : WOOD_RIGHT);
            case SWORD -> chain(t, index == 0 ? SWORD_LEFT : SWORD_RIGHT);
            default -> chain(t, index == 0 ? BREATH_LOOP : BREATH_ARM);
        };
    }
    private static Point chain(double t, double[][] values) {
        int segments = (values.length - 1) / 3;
        int segment = Math.min(segments - 1, (int) (t * segments));
        double local = t * segments - segment;
        int n = segment * 3;
        return cubic(point(values[n]), point(values[n+1]), point(values[n+2]), point(values[n+3]), local);
    }
    private static Point point(double[] p) { return new Point(p[0], p[1]); }
    private static Point cubic(Point a, Point b, Point c, Point d, double t) {
        t = Math.max(0, Math.min(1, t)); double v = 1 - t;
        return new Point(v*v*v*a.x + 3*v*v*t*b.x + 3*v*t*t*c.x + t*t*t*d.x,
                v*v*v*a.y + 3*v*v*t*b.y + 3*v*t*t*c.y + t*t*t*d.y);
    }
}
