package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.cultivation.ClientCultivationIntentSender;
import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.growth.TechniqueGrowth;
import com.example.myvillage.cultivation.growth.TechniqueGrowthRules;
import com.example.myvillage.cultivation.growth.TechniqueGrowthRules.Effect;
import com.example.myvillage.cultivation.network.MeditationIntentAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;

/** Detail inside the existing H catalogue. Buttons send intentions; every value comes from synced state. */
final class TechniqueGrowthPage extends PanelPage {
    static final int DOCK = 58;
    private final Runnable back;
    private TechniqueScrollButton backButton, detailsButton, practice;
    private final TechniqueScrollButton[] choices = new TechniqueScrollButton[2];
    private final TechniqueScrollView scrollView = new TechniqueScrollView();
    private final TechniqueScrollMotion.Tracker motion = new TechniqueScrollMotion.Tracker();
    private ResourceLocation selected;
    private int x, width, sceneY, sceneHeight;
    private boolean visible, reading, scrollToTop;

    TechniqueGrowthPage(Runnable back) { this.back = back; }
    void select(ResourceLocation id) { selected = id; reading = false; scrollToTop = true; motion.hidden(); }
    @Override public int init(int x, int y, int width, int height, Consumer<AbstractWidget> widgets) {
        this.x = x; this.width = width;
        backButton = button(TechniqueScrollButton.Kind.LINK, x, y, 64, 16, "‹ 返回功法", ignored -> back.run(), widgets);
        detailsButton = button(TechniqueScrollButton.Kind.LINK, x + width - 64, y, 64, 16, "功法详解", ignored -> {
            reading = !reading; scrollToTop = true; motion.hidden();
        }, widgets);
        practice = button(TechniqueScrollButton.Kind.PRACTICE, x, y, 54, 26, "闭关修习", ignored -> send(0), widgets);
        for (int i = 0; i < 2; i++) {
            final int action = i + 1;
            choices[i] = button(TechniqueScrollButton.Kind.INSIGHT, x, y, 28, 57, "参悟", ignored -> send(action), widgets);
        }
        return 0;
    }
    private TechniqueScrollButton button(TechniqueScrollButton.Kind kind, int x, int y, int width, int height,
            String label, net.minecraft.client.gui.components.Button.OnPress action,
            Consumer<AbstractWidget> widgets) {
        var b = new TechniqueScrollButton(kind, x, y, width, height, label, action);
        widgets.accept(b); return b;
    }
    private void send(int action) {
        var c = PanelContext.capture(Minecraft.getInstance(), PanelFonts.get());
        if (selected == null || c.profile() == null) return;
        if (action == 0 && c.meditation() != null && c.meditation().state().active()) {
            ClientCultivationIntentSender.send(MeditationIntentAction.STOP); return;
        }
        var progress = c.profile().learnedTechniques().get(selected);
        if (progress != null) ClientCultivationIntentSender.sendGrowth(selected, action, progress.revision());
    }
    @Override public void setVisible(boolean visible) {
        this.visible = visible;
        backButton.visible = detailsButton.visible = visible;
        practice.visible = visible && !reading;
        for (var choice : choices) choice.visible = visible && !reading;
        if (!visible || reading) motion.hidden();
    }
    @Override public boolean takeScrollToTop() { boolean result = scrollToTop; scrollToTop = false; return result; }
    @Override public void refresh(PanelContext c, boolean shown) {
        var rules = TechniqueGrowthRules.bundled();
        var spec = selected == null ? null : rules.find(selected).orElse(null);
        var progress = c.profile() == null ? null : c.profile().learnedTechniques().get(selected);
        boolean active = c.meditation() != null && c.meditation().state().active();
        boolean ready = shown && spec != null && progress != null && c.meditation() != null;
        detailsButton.label(reading ? "返回内景" : "功法详解");
        practice.label(active ? "收功" : progress != null && spec != null && TechniqueGrowth.mastered(spec, progress) ? "本篇圆满" : "闭关修习");
        practice.active = ready && (active || !TechniqueGrowth.mastered(spec, progress));
        if (ready && !reading) {
            boolean ownPractice = active && c.meditation().practice().filter(selected::equals).isPresent();
            motion.observe(Util.getMillis(), selected.toString(), progress.masteryPoints(), spec.stage(progress.masteryPoints()),
                    progress.insight(), progress.revision(), ownPractice, ownPractice && c.meditation().state().preparing());
            practice.setTooltip(Tooltip.create(Component.literal(active ? "收功；仅保留已结算的修习。"
                    : "每 " + rules.practiceTicks() / 20 + " 秒：修习 +" + spec.practiceGain() + "，消耗 "
                    + spec.practiceStones() + " 下品灵石。\n未完成批次不扣费；圆满停止修习。")));
        } else motion.hidden();
        for (int i = 0; i < 2; i++) {
            String label = spec == null ? "参悟" : spec.insights().get(i).name();
            boolean chosen = ready && progress.insight().equals(spec.insights().get(i).id());
            var bonuses = spec == null ? java.util.Map.<Effect,Integer>of() : spec.insights().get(i).bonuses();
            Effect first = bonuses.keySet().stream().sorted().findFirst().orElse(null);
            String effectName = first == null ? "" : switch (first) {
                case CULTIVATION -> "修为"; case RECOVERY -> "回气"; case DAMAGE -> "伤害"; case QI_COST -> "耗灵";
            };
            int bonus = first == null ? 0 : bonuses.get(first);
            choices[i].insight(label, effectName, first == null ? "" : (bonus >= 0 ? "+" : "−") + compactPercent(bonus), chosen);
            choices[i].insightFeedback(motion.insightAge());
            choices[i].active = ready && !active && !chosen && TechniqueGrowth.choose(rules, selected, i,
                    progress.revision(), c.profile(), id -> c.technique(id).orElse(null)).isPresent();
            if (ready) {
                String cost = progress.insight().isEmpty() ? "首次 " + TechniqueGrowth.firstInsightCost(rules, selected,
                        c.profile(), id -> c.technique(id).orElse(null)) + " 心得" : "改选 " + rules.respecStones() + " 下品灵石";
                choices[i].setTooltip(Tooltip.create(Component.literal(label + "：" + effectText(bonuses)
                        + "\n" + rules.stageNames().get(rules.insightUnlockStage()) + "开放；" + cost
                        + "\n可用心得 " + TechniqueGrowth.availableInsight(rules, spec, progress)
                        + (active ? "；收功后可参悟" : chosen ? "；当前已悟" : ""))));
            }
        }
    }
    @Override public int render(GuiGraphics g, PanelContext c, int x, int y, int width, int viewport) {
        sceneY = y; sceneHeight = viewport;
        if (reading) return renderDetails(g, c, x, y, width, viewport);
        var rules = TechniqueGrowthRules.bundled();
        var spec = rules.find(selected).orElse(null);
        var progress = c.profile().learnedTechniques().get(selected);
        if (spec == null || progress == null) return 0;
        var layout = TechniqueScrollLayout.of(width, viewport);
        position(practice, layout.practice(), x, y);
        position(choices[0], layout.firstChoice(), x, y);
        position(choices[1], layout.secondChoice(), x, y);
        scrollView.render(g, c, layout, TechniqueScrollStyle.forTechnique(selected.toString()), motion,
                rules, spec, progress, TechniqueTiles.name(c, selected), x, y);
        return viewport;
    }
    private static void position(TechniqueScrollButton button, TechniqueScrollLayout.Rect rect, int x, int y) {
        button.setPosition(x + rect.x(), y + rect.y());
        button.resize(rect.width(), rect.height());
    }
    private int renderDetails(GuiGraphics g, PanelContext c, int x, int y, int width, int viewport) {
        var rules = TechniqueGrowthRules.bundled();
        var spec = rules.find(selected).orElse(null);
        var progress = c.profile().learnedTechniques().get(selected);
        if (spec == null || progress == null) return 0;
        int top = y, stage = spec.stage(progress.masteryPoints());
        y = text(g, c, x, y, width, TechniqueTiles.name(c, selected) + " · " + rules.stageNames().get(stage), InkPanelTheme.TEXT);
        y = text(g, c, x, y, width, "修习 " + Math.min(spec.cap(), progress.masteryPoints()) + " / " + spec.cap()
                + "　可用心得 " + TechniqueGrowth.availableInsight(rules, spec, progress), InkPanelTheme.MUTED);
        CultivationInkTheme.bar(g, x + 3, y + 2, width - 6, Math.min(1, progress.masteryPoints() / (double) spec.cap()), InkPanelTheme.JADE);
        y += 12;
        for (int i = 0; i < 5; i++) {
            int left = x + i * width / 5;
            g.drawString(c.font(), rules.stageNames().get(i), left + 2, y, i <= stage ? InkPanelTheme.JADE : InkPanelTheme.MUTED, false);
        }
        y += 18;
        y = text(g, c, x, y, width, "当前收益 · " + currentEffects(c, selected), InkPanelTheme.JADE);
        if (spec.category() == com.example.myvillage.cultivation.data.TechniqueCategory.CORE)
            y = text(g, c, x, y, width, "仅运转此心法时生效；未主修功法不叠加属性。", InkPanelTheme.MUTED);
        y += 5;
        for (var insight : spec.insights()) {
            boolean chosen = insight.id().equals(progress.insight());
            y = text(g, c, x, y, width, (chosen ? "◆ " : "◇ ") + insight.name() + "：" + effectText(insight.bonuses()), chosen ? InkPanelTheme.JADE : InkPanelTheme.TEXT);
        }
        y += 8;
        y = text(g, c, x, y, width, "功法潜力", InkPanelTheme.GOLD);
        y = text(g, c, x, y, width, spec.potential(), InkPanelTheme.TEXT);
        if (spec.next().isPresent()) {
            var next = spec.next().get(); var nextDef = c.technique(next).orElse(null);
            y = text(g, c, x, y + 4, width, "后篇：" + TechniqueTiles.name(c, next) + " · 前篇圆满并参悟后，持秘籍右键研读。", InkPanelTheme.JADE);
            if (nextDef != null) {
                String requirement = nextDef.requirements().minimumRealm().map(realm -> nextDef.requirements().minimumStage()
                        .map(s -> c.stageName(realm, s).getString()).orElse(c.realmName(realm).getString())).orElse("无境界门槛");
                y = text(g, c, x, y, width, "后篇条件：" + requirement, InkPanelTheme.MUTED);
                for (var entry : nextDef.requirements().minimumElementAffinities().entrySet())
                    y = text(g, c, x, y, width, c.elementName(entry.getKey()).getString() + "亲和 ≥ " + percent(entry.getValue()), InkPanelTheme.MUTED);
            }
        }
        y += 8;
        y = text(g, c, x, y, width, "触类旁通 · 只取最佳来源", InkPanelTheme.GOLD);
        var support = TechniqueGrowth.support(rules, selected, c.profile(), id -> c.technique(id).orElse(null));
        if (support.isPresent()) {
            var s = support.get();
            y = text(g, c, x, y, width, "已有根基：" + TechniqueTiles.name(c, s.source()) + "·圆满。首次参悟少耗 "
                    + s.firstInsightDiscount() + " 心得；已学功法不再获得研读加速。", InkPanelTheme.JADE);
        }
        boolean helps = false;
        for (var target : rules.techniques().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            for (var link : target.getValue().transfers()) if (link.source().equals(selected)) {
                helps = true;
                y = text(g, c, x, y, width, "圆满后 → " + TechniqueTiles.name(c, target.getKey()) + "：研读速度 +" + percent(link.studyBonusBp())
                        + (link.inheritedMastery() > 0 ? "，承接 " + link.inheritedMastery() + " 修习" : "")
                        + (link.inheritInsight() ? "，保留适用参悟方向" : "") + "。", InkPanelTheme.TEXT);
            }
        }
        if (!helps) y = text(g, c, x, y, width, "本次样例未为此法配置其他新法的学习加速。", InkPanelTheme.MUTED);
        y = text(g, c, x, y + 6, width, "闭关每 " + rules.practiceTicks() / 20 + " 秒完成一批：修习 +" + spec.practiceGain()
                + "，消耗 " + spec.practiceStones() + " 下品灵石。未完成批次不扣费，不发修习。", InkPanelTheme.MUTED);
        if (spec.hitGain() > 0) y = text(g, c, x, y, width, "有效命中每次施放修习 +" + spec.hitGain() + "；空放、多目标重复命中不额外增长。", InkPanelTheme.MUTED);
        return y - top + 8;
    }
    @Override public void renderOverlay(GuiGraphics g, PanelContext c, int mouseX, int mouseY) {
        if (!visible || reading || selected == null || c.profile() == null) return;
        var spec = TechniqueGrowthRules.bundled().find(selected).orElse(null);
        var progress = c.profile().learnedTechniques().get(selected);
        if (spec == null || progress == null) return;
        var rules = TechniqueGrowthRules.bundled();
        if (mouseX >= x && mouseX < practice.getX() && mouseY >= sceneY + sceneHeight - 48
                && mouseY < sceneY + sceneHeight) {
            var lines = new ArrayList<Component>();
            lines.add(Component.literal("当前收益：" + currentEffects(c, selected)));
            lines.add(Component.literal("可用心得 " + TechniqueGrowth.availableInsight(rules, spec, progress)));
            if (spec.category() == com.example.myvillage.cultivation.data.TechniqueCategory.CORE)
                lines.add(Component.literal("仅主修时生效；多门心法不叠加。"));
            g.renderComponentTooltip(c.font(), lines, mouseX, mouseY);
        }
    }
    private static String compactPercent(int value) {
        int magnitude = Math.abs(value);
        return magnitude % 100 == 0 ? magnitude / 100 + "%" : percent(magnitude);
    }
    private static int text(GuiGraphics g, PanelContext c, int x, int y, int width, String text, int color) {
        for (var line : c.font().split(Component.literal(text), Math.max(1, width - 6))) {
            g.drawString(c.font(), line, x + 3, y, color, false); y += 12;
        }
        return y + 3;
    }
    static String currentEffects(PanelContext c, ResourceLocation id) {
        var rules = TechniqueGrowthRules.bundled(); var map = new java.util.EnumMap<Effect, Integer>(Effect.class);
        for (Effect effect : Effect.values()) {
            int bonus = TechniqueGrowth.multiplier(rules, id, c.profile(), effect) - TechniqueGrowthRules.UNIT;
            if (bonus != 0) map.put(effect, bonus);
        }
        return map.isEmpty() ? "保持功法原有效果" : effectText(map);
    }
    static String effectText(java.util.Map<Effect, Integer> values) {
        var lines = new ArrayList<String>();
        for (Effect effect : Effect.values()) if (values.getOrDefault(effect, 0) != 0) {
            int value = values.get(effect);
            String name = switch (effect) { case CULTIVATION -> "修为获取"; case RECOVERY -> "灵力恢复"; case DAMAGE -> "伤害"; case QI_COST -> "耗灵"; };
            lines.add(name + (value > 0 ? " +" : " −") + percent(Math.abs(value)));
        }
        return String.join("；", lines);
    }
    static String percent(int bp) { return String.format(Locale.ROOT, "%.1f%%", bp / 100.0); }
}
