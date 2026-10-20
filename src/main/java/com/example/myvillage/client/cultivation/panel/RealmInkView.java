package com.example.myvillage.client.cultivation.panel;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.DoubleFunction;

/** Static approved ink originals, independently batched moving currents, server-driven mode. */
final class RealmInkView {
    private static final Map<RealmInkStyle, ResourceLocation> TEXTURES = new EnumMap<>(RealmInkStyle.class);
    private final RealmInkMotion.Clock clock = new RealmInkMotion.Clock();

    static {
        for (RealmInkStyle style : RealmInkStyle.values()) {
            TEXTURES.put(style, ResourceLocation.fromNamespaceAndPath("myvillage",
                    "textures/gui/cultivation/ink/" + style.texture() + ".png"));
        }
    }

    void hidden() { clock.sample(Util.getMillis(), false); }

    void render(GuiGraphics g, PanelContext c, RealmInkStyle style, int x, int y, int width, int height) {
        var status = c.meditation();
        var mode = RealmInkMotion.mode(status == null ? null : status.state(), c.progressFull(), c.study().isPresent());
        double seconds = clock.sample(Util.getMillis(), mode.moving());
        float size = Math.max(1, Math.min(width - 4, height - 28));
        Box box = new Box(x + (width - size) / 2f, y + Math.max(0, (height - 28 - size) / 2f), size / 400f);
        int color = switch (mode) {
            case SPIRIT -> 0xFF3A726F;
            case ADVANCING -> 0xFF977637;
            case BOTTLENECK -> 0xFF934831;
            default -> 0xFF3A6650;
        };
        float alpha = mode == RealmInkMotion.Mode.IDLE ? .20f
                : mode == RealmInkMotion.Mode.PREPARING ? .42f
                : mode == RealmInkMotion.Mode.STUDY ? .38f : .90f;
        g.flush();
        if (style == RealmInkStyle.CORE || style == RealmInkStyle.FOUNDATION && mode == RealmInkMotion.Mode.CONSOLIDATING) {
            flows(new VectorBrush(g).begin(), box, style, mode, seconds, color, alpha, -1);
        }
        VectorBrush.texture(g, TEXTURES.get(style), box.x, box.y, box.x + size, box.y + size,
                style == RealmInkStyle.MORTAL && !c.profile().awakened() ? 0x60FFFFFF : 0xFFFFFFFF);
        flows(new VectorBrush(g).begin(), box, style, mode, seconds, color, alpha, 1);
        if (mode == RealmInkMotion.Mode.CONSOLIDATING || mode.advancing()) {
            double progress = mode.advancing() && status != null && status.advancementDurationTicks() > 0
                    ? 1.0 - status.advancementTicksRemaining() / (double) status.advancementDurationTicks()
                    : c.stabilityFraction();
            VectorBrush ring = new VectorBrush(g).begin();
            arc(ring, box, u -> RealmInkMotion.ellipse(u, 147,
                    style == RealmInkStyle.FOUNDATION ? 54 : 147, 0, 200, 201),
                    Math.max(0, Math.min(1, progress)), CultivationInkTheme.GOLD, .35f);
            ring.end(false);
        }
        CultivationInkTheme.centered(g, c.font(), CultivationInkTheme.tr(style.key() + ".title"),
                x, y + height - 26, width, true, CultivationInkTheme.INK);
        String flowKey = mode == RealmInkMotion.Mode.IDLE ? "flow.idle"
                : mode.advancing() ? "flow.advancing"
                : mode == RealmInkMotion.Mode.CONSOLIDATING ? "flow.consolidating"
                : "flow." + style.key();
        CultivationInkTheme.centered(g, c.font(), CultivationInkTheme.tr(flowKey),
                x, y + height - 10, width, false, CultivationInkTheme.JADE);
    }

    private record Box(float x, float y, float scale) {
        float px(double value) { return x + (float) value * scale; }
        float py(double value) { return y + (float) value * scale; }
    }

    /** layer -1 is behind the static bitmap; layer +1 is in front. */
    private static void flows(VectorBrush brush, Box box, RealmInkStyle style, RealmInkMotion.Mode mode,
                              double time, int color, float alpha, int layer) {
        boolean consolidate = mode == RealmInkMotion.Mode.CONSOLIDATING;
        boolean spirit = mode == RealmInkMotion.Mode.SPIRIT;
        if (style == RealmInkStyle.CORE) {
            double speed = consolidate ? .06 : spirit ? .19 : .105;
            for (int i = 0; i < 3; i++) {
                int index = i;
                for (int n = 0; n < 2; n++) {
                    trail(brush, box, u -> RealmInkMotion.path(style, index, u, consolidate),
                            RealmInkMotion.fraction(time * speed * (i == 1 ? -1 : 1) + i * .23 + n * .5),
                            .18, color, alpha, 3.6f, layer, true);
                }
            }
        } else if (style == RealmInkStyle.FOUNDATION) {
            if (consolidate) {
                for (int i = 0; i < 3; i++) {
                    int index = i;
                    trail(brush, box, u -> RealmInkMotion.ellipse(u, 78 + index * 13, 24 + index * 4, 0, 200, 220),
                            RealmInkMotion.fraction(time * .075 + i * .33), .2, color, alpha, 2.8f, layer, true);
                }
            } else if (layer > 0) {
                double speed = mode.advancing() ? .40 : .19;
                for (int i = 0; i < 3; i++) {
                    int index = i;
                    trail(brush, box, u -> RealmInkMotion.path(style, index, u, false),
                            RealmInkMotion.fraction(time * speed + i * .31), .17, color, alpha,
                            mode.advancing() ? 3.7f : 2.8f, 0, false);
                    double age = RealmInkMotion.fraction(time * speed + i * .31 - .96);
                    if (age < .65) {
                        double radius = 10 + age * 102;
                        arc(brush, box, u -> RealmInkMotion.ellipse(u, radius, radius * .29, 0, 200, 220),
                                1, color, alpha * (float) (1 - age / .65) * .28f);
                    }
                }
            }
        } else if (style == RealmInkStyle.SOUL) {
            if (layer > 0) {
                double speed = consolidate ? .06 : spirit ? .20 : .11;
                for (int i = 0; i < 4; i++) {
                    trail(brush, box, u -> RealmInkMotion.path(style, 0, u, false),
                            RealmInkMotion.fraction(time * speed + i * .25), .17, color, alpha, 3.2f, 0, true);
                }
                for (int side : new int[] {-1, 1}) {
                    trail(brush, box, u -> RealmInkMotion.soulFeeder(u, side),
                            RealmInkMotion.fraction(time * .16 + (side + 1) * .23),
                            .2, color, alpha * .65f, 2.5f, 0, false);
                }
            }
        } else if (layer > 0) {
            int count = spirit ? 7 : 5;
            double speed = consolidate ? .065 : spirit ? .22 : .12;
            for (int i = 0; i < count; i++) {
                int index = i % 2;
                trail(brush, box, u -> RealmInkMotion.path(style, index, u, consolidate),
                        RealmInkMotion.fraction(time * speed + i / (double) count),
                        consolidate ? .15 : .20, color, alpha, 4.2f, 0, true);
            }
            for (int i = 0; i < 4; i++) {
                int index = i;
                trail(brush, box, u -> RealmInkMotion.spiral(u, index),
                        RealmInkMotion.fraction(time * .15 + i * .25), .23, color, alpha * .65f, 2.3f, 0, false);
            }
        }
        if (mode.advancing() && layer > 0) {
            for (int i = 0; i < 5; i++) {
                int index = i;
                trail(brush, box, u -> RealmInkMotion.inward(u, index),
                        RealmInkMotion.fraction(time * .30 + i * .18), .16, color, alpha * .8f, 3, 0, false);
            }
        }
        brush.end(false);
    }

    private static void trail(VectorBrush brush, Box box, DoubleFunction<RealmInkMotion.Point> path,
                              double head, double tail, int color, float alpha, float width, int layer, boolean closed) {
        final int count = 29;
        float[] xs = new float[count], ys = new float[count], alphas = new float[count];
        int start = -1;
        for (int i = 0; i < count; i++) {
            double u = head - tail + tail * i / (count - 1);
            var p = path.apply(closed ? RealmInkMotion.fraction(u) : Math.max(0, Math.min(1, u)));
            boolean visible = (closed || u >= 0 && u <= 1) && (layer == 0 || (p.z() >= 0) == (layer > 0));
            xs[i] = box.px(p.x()); ys[i] = box.py(p.y()); alphas[i] = i / (float) (count - 1);
            if (visible && start < 0) start = i;
            if (start >= 0 && (!visible || i == count - 1)) {
                int end = visible ? i : i - 1;
                if (end > start) {
                    brush.stroke(xs, ys, start, end, width * box.scale, .55f * box.scale, color, alpha * .75f, alphas);
                    brush.stroke(xs, ys, start, end, .65f * box.scale, .2f * box.scale,
                            0xFFE5E2BC, alpha * .6f, alphas);
                }
                start = -1;
            }
        }
        var tip = path.apply(closed ? RealmInkMotion.fraction(head) : head);
        if (layer == 0 || (tip.z() >= 0) == (layer > 0)) {
            brush.disc(box.px(tip.x()), box.py(tip.y()), Math.max(.35f, width * .4f * box.scale),
                    .35f * box.scale, color, alpha * .9f, alpha * .5f);
        }
    }

    private static void arc(VectorBrush brush, Box box, DoubleFunction<RealmInkMotion.Point> path,
                            double portion, int color, float alpha) {
        float[] xs = new float[65], ys = new float[65];
        for (int i = 0; i < xs.length; i++) {
            var p = path.apply(portion * i / (xs.length - 1));
            xs[i] = box.px(p.x()); ys[i] = box.py(p.y());
        }
        brush.stroke(xs, ys, 0, xs.length - 1, Math.max(.4f, box.scale), .3f, color, alpha, null);
    }
}
