package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.growth.TechniqueGrowthRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.DoubleFunction;

/** A compact live scroll, painted in the actual H viewport with existing fonts and four small bitmaps. */
final class TechniqueScrollView {
    private static final Map<TechniqueScrollStyle, ResourceLocation> TEXTURES = new EnumMap<>(TechniqueScrollStyle.class);
    static {
        for (var style : TechniqueScrollStyle.values()) if (style != TechniqueScrollStyle.UNKNOWN)
            TEXTURES.put(style, ResourceLocation.fromNamespaceAndPath("myvillage",
                    "textures/gui/cultivation/technique_scroll/" + style.texture() + ".png"));
    }

    void render(GuiGraphics g, PanelContext c, TechniqueScrollLayout l, TechniqueScrollStyle style,
                TechniqueScrollMotion.Tracker motion, TechniqueGrowthRules rules,
                TechniqueGrowthRules.Technique spec, TechniqueProgress progress, String name, int x, int y) {
        int w = l.width(), h = l.height(), pad = l.padding();
        g.fill(x, y, x + w, y + h, 0xFFECDEBD);
        var a = l.art();
        g.flush();
        if (TEXTURES.containsKey(style)) VectorBrush.texture(g, TEXTURES.get(style),
                x + a.x(), y + a.y(), x + a.right(), y + a.bottom(), 0xFFFFFFFF);
        flows(g, new Box(x + a.x(), y + a.y(), a.width() / 1536f), style, motion, rules.practiceTicks() / 20.0);
        // Gentle paper at the bottom supports numbers without an opaque dashboard card.
        g.fillGradient(x + pad, y + l.progressY() - 7, x + w - pad, y + h - 2, 0x05F1E3C8, 0xDDF1E3C8);
        CultivationInkTheme.text(g, c.font(), "修行录·功法", x + pad, y + 5, Math.max(1, w / 3), InkPanelTheme.MUTED);
        int lineageWidth = Math.min(96, w / 2);
        CultivationInkTheme.text(g, c.font(), style.lineage(), x + w - pad - lineageWidth, y + 5,
                lineageWidth, InkPanelTheme.MUTED);
        if (h >= 140) {
            int[] letters = name.codePoints().toArray();
            for (int i = 0; i < Math.min(6, letters.length); i++)
                scaled(g, c, new String(Character.toChars(letters[i])), x + pad + 1, y + 23 + i * 12, 1.22f, InkPanelTheme.TEXT);
        } else CultivationInkTheme.title(g, c.font(), name, x + pad, y + 18, Math.max(1, w / 2));

        if (motion.running()) {
            int phase = (int) (TechniqueScrollMotion.fraction(motion.flow() / Math.max(1, rules.practiceTicks() / 20.0)) * 3);
            CultivationInkTheme.centered(g, c.font(), motion.preparing() ? "凝神" : style.phase(phase),
                    x + w / 4, y + 15, Math.max(30, w / 4), true, InkPanelTheme.GOLD_BRIGHT);
        }
        int stage = spec.stage(progress.masteryPoints());
        if (motion.stageAge() < 2.2) {
            float settle = (float) Math.max(0, 1 - motion.stageAge() / .35);
            int sx = x + Math.max(pad + 22, w / 5), sy = y + Math.max(30, l.progressY() - 40);
            g.pose().pushPose();
            g.pose().translate(sx + 8, sy + 13, 0);
            g.pose().scale(1 + settle * .35f, 1 + settle * .35f, 1);
            g.renderOutline(-8, -13, 19, 31, InkPanelTheme.RED);
            int[] chars = rules.stageNames().get(stage).codePoints().toArray();
            for (int i = 0; i < chars.length; i++) CultivationInkTheme.title(g, c.font(),
                    new String(Character.toChars(chars[i])), -3, -8 + i * 12, 14);
            g.pose().popPose();
        }
        int progressWidth = Math.max(1, l.practice().x() - pad - 5);
        CultivationInkTheme.title(g, c.font(), rules.stageNames().get(stage), x + pad, y + l.progressY() + 2, 30);
        String total = Math.min(spec.cap(), progress.masteryPoints()) + " / " + spec.cap();
        CultivationInkTheme.text(g, c.font(), total, x + pad + 29, y + l.progressY() + 2,
                Math.max(1, progressWidth - 29), InkPanelTheme.TEXT);
        String next = stage == rules.stageNames().size() - 1 ? "本篇圆满·停止扣费"
                : "距" + rules.stageNames().get(stage + 1) + "还差 "
                + (spec.thresholds().get(stage + 1) - progress.masteryPoints());
        CultivationInkTheme.text(g, c.font(), next, x + pad, y + l.progressY() + 16, progressWidth, InkPanelTheme.MUTED);
        CultivationInkTheme.centered(g, c.font(), stage == rules.stageNames().size() - 1 ? "不再扣费"
                : spec.practiceStones() + " 下品灵石", x + l.practice().x(), y + l.practice().bottom() + 1,
                l.practice().width(), false, InkPanelTheme.MUTED);
        int barWidth = Math.max(1, w - pad * 2);
        CultivationInkTheme.bar(g, x + pad, y + l.barY(), barWidth,
                Math.min(1, progress.masteryPoints() / (double) spec.cap()), InkPanelTheme.JADE);
        for (int i = 0; i < rules.stageNames().size(); i++) {
            int left = x + pad + i * Math.max(0, barWidth - 20) / (rules.stageNames().size() - 1);
            CultivationInkTheme.text(g, c.font(), rules.stageNames().get(i), left, y + l.stagesY(), 22,
                    i == stage ? InkPanelTheme.RED : i < stage ? InkPanelTheme.JADE : InkPanelTheme.FAINT);
        }
        if (motion.gainAge() < 1.6 && motion.gained() > 0) {
            int alpha = (int) (255 * Math.min(1, (1.6 - motion.gainAge()) * 2));
            g.drawString(c.font(), "+" + motion.gained(), x + w / 2,
                    y + l.progressY() - 5 - (int) (motion.gainAge() * 7),
                    InkPanelTheme.withAlpha(InkPanelTheme.GOLD_BRIGHT, alpha), false);
        }
    }
    private static void scaled(GuiGraphics g, PanelContext c, String label, int x, int y, float scale, int color) {
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(c.font(), CultivationInkTheme.brush(label), 0, 0, color, false); g.pose().popPose();
    }
    private record Box(float x, float y, float scale) {
        float px(double value) { return x + (float) value * scale; }
        float py(double value) { return y + (float) value * scale; }
    }
    private static void flows(GuiGraphics g, Box b, TechniqueScrollStyle style,
                              TechniqueScrollMotion.Tracker m, double cycle) {
        if (!m.running() && m.gainAge() >= 2.2 && m.insightAge() >= 1.8) return;
        g.flush(); var brush = new VectorBrush(g).begin();
        double t = TechniqueScrollMotion.fraction(m.flow() / Math.max(1, cycle));
        if (m.running()) {
            float alpha = m.preparing() ? .24f : .82f;
            for (int i = 0; i < 2; i++) {
                int path = i;
                if (t < .3 || m.preparing()) {
                    trail(brush, b, u -> TechniqueScrollMotion.incoming(style, path, u),
                            TechniqueScrollMotion.fraction(t / .3 + i * .12), .3, alpha, 5);
                } else {
                    for (int n = 0; n < 2; n++) trail(brush, b, u -> TechniqueScrollMotion.path(style, path, u),
                            TechniqueScrollMotion.fraction((t - .3) * 2 + i * .12 + n * .4), .24, alpha, 5);
                }
            }
            var p = TechniqueScrollMotion.anchor(style);
            float glow = m.preparing() ? .06f : .12f + (float) Math.max(0, t - .65) * .5f;
            brush.disc(b.px(p.x()), b.py(p.y()), 3 * b.scale, 85 * b.scale, 0xFFF1DF97, glow, .02f);
        }
        if (m.gainAge() < 1.7) {
            float alpha = (float) (1 - m.gainAge() / 1.7);
            var center = TechniqueScrollMotion.anchor(style);
            brush.disc(b.px(center.x()), b.py(center.y()), 7 * b.scale, 125 * b.scale,
                    0xFFFFE8A7, alpha * .6f, .03f);
            if (style == TechniqueScrollStyle.SWORD) {
                brush.line(b.px(768), b.py(480), b.px(768), b.py(60), Math.max(.6f, 4*b.scale),
                        7*b.scale, 0xFFFFEBC0, alpha * .9f);
            } else {
                for (int i = 0; i < 2; i++) {
                    int path = i;
                    trail(brush, b, u -> TechniqueScrollMotion.path(style, path, u),
                            Math.min(1, m.gainAge() / .8), .4, alpha, 6);
                }
                if (style == TechniqueScrollStyle.QINGMU || style == TechniqueScrollStyle.YIMU) {
                    for (int i = 0; i < 12; i++) {
                        var p = TechniqueScrollMotion.path(style, i % 2, .56 + i * .035);
                        float px = b.px(p.x() + Math.sin(i*2.4) * m.gainAge()*35);
                        float py = b.py(p.y() + Math.cos(i*2.4) * m.gainAge()*25);
                        int color = style == TechniqueScrollStyle.YIMU ? 0xFFFFF0CD : 0xFFD9E6AF;
                        brush.line(px, py, px+2*b.scale, py-7*b.scale, Math.max(.5f, 5*b.scale), b.scale, color, alpha);
                    }
                }
            }
        }
        if (m.insightAge() < 1.4) {
            float alpha = (float) (1 - m.insightAge()/1.4);
            var p = TechniqueScrollMotion.anchor(style);
            brush.disc(b.px(p.x()), b.py(p.y()), 2*b.scale, 105*b.scale, 0xFFE3D9A3, alpha*.4f, 0);
        }
        brush.end(false);
    }
    private static void trail(VectorBrush brush, Box b, DoubleFunction<TechniqueScrollMotion.Point> path,
                              double head, double tail, float alpha, float thickness) {
        int count = 20;
        float[] xs = new float[count], ys = new float[count], alphas = new float[count];
        for (int i = 0; i < count; i++) {
            double u = Math.max(0, head - tail + tail * i / (count - 1));
            var p = path.apply(u); xs[i] = b.px(p.x()); ys[i] = b.py(p.y()); alphas[i] = i / (float) (count - 1);
        }
        brush.stroke(xs, ys, 0, count-1, Math.max(.7f, thickness*b.scale), 3*b.scale, 0xFFB9CC95, alpha, alphas);
        brush.stroke(xs, ys, 0, count-1, Math.max(.35f, 1.5f*b.scale), b.scale, 0xFFFFF0BE, alpha*.85f, alphas);
    }
}
