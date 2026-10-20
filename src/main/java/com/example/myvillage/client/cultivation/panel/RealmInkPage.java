package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.data.AdvancementDefinition;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Read-only paper page. All values come from PanelContext; actions remain in MeditationPage. */
final class RealmInkPage {
    private final RealmInkView view = new RealmInkView();
    // The stage ordinal needs its full book-font line below the ladder.
    private static final int HEADER = 36;
    // Fits the 134-GUI-pixel body left by two rows of controls at 320x240.
    private static final int INFO_HEIGHT = 134;
    private int pointerX = -1, pointerY = -1;
    private boolean showDetails;

    void hidden() { view.hidden(); }
    void pointer(int x, int y) { pointerX = x; pointerY = y; }

    void renderOverlay(GuiGraphics g, PanelContext c, int mouseX, int mouseY) {
        if (!showDetails) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(CultivationInkTheme.tr("details")));
        for (String line : MeditationPage.gainDetails(c)) lines.add(Component.literal(line));
        g.renderComponentTooltip(c.font(), lines, mouseX, mouseY);
    }

    int render(GuiGraphics g, PanelContext c, int x, int y, int width, int viewport) {
        RealmInkStyle style = RealmInkStyle.forRealm(c.profile().realmId().toString());
        CultivationInkTheme.title(g, c.font(), c.stageName(c.profile().realmId(), c.profile().stageId()).getString(),
                x + 3, y, width - 85);
        CultivationInkTheme.text(g, c.font(), c.sessionText(), x + width - 80, y + 3, 78, CultivationInkTheme.JADE);
        ladder(g, c, x + 3, y + 18, width - 6);
        int top = y + HEADER;
        if (width >= 330) {
            int left = (int) (width * .44), gap = 9;
            int height = Math.max(INFO_HEIGHT, viewport - HEADER);
            view.render(g, c, style, x, top, left, height);
            g.fill(x + left + 3, top, x + left + 4, top + height, CultivationInkTheme.RULE);
            info(g, c, style, x + left + gap, top, width - left - gap);
            return HEADER + height;
        }
        int figureHeight = Math.max(125, Math.min(170, viewport - HEADER));
        view.render(g, c, style, x, top, width, figureHeight);
        g.fill(x + 3, top + figureHeight + 4, x + width - 3, top + figureHeight + 5, CultivationInkTheme.RULE);
        info(g, c, style, x + 3, top + figureHeight + 11, width - 6);
        return HEADER + figureHeight + 11 + INFO_HEIGHT;
    }

    private static void ladder(GuiGraphics g, PanelContext c, int x, int y, int width) {
        List<RealmStageDefinition> stages = c.currentRealmStages();
        if (stages.isEmpty()) return;
        int current = -1;
        for (int i = 0; i < stages.size(); i++) if (stages.get(i).id().equals(c.profile().stageId())) current = i;
        g.fill(x, y + 2, x + width, y + 3, CultivationInkTheme.RULE);
        for (int i = 0; i < stages.size(); i++) {
            int center = x + width * (2 * i + 1) / (2 * stages.size());
            int color = i == current ? CultivationInkTheme.GOLD : i < current ? CultivationInkTheme.JADE : CultivationInkTheme.FAINT;
            if (i <= current) PanelTheme.diamond(g, center, y + 2, 2, color);
            else PanelTheme.hollowDiamond(g, center, y + 2, 2, color, CultivationInkTheme.PAPER);
            // Ordinals are visual positions only; the synchronized stage list decides their count/order.
            CultivationInkTheme.centered(g, c.font(), Integer.toString(i + 1), center - 8, y + 6, 16, false, color);
        }
    }

    private static void meter(GuiGraphics g, PanelContext c, String label, String value, double fraction,
                              int x, int y, int width, int color) {
        CultivationInkTheme.pair(g, c.font(), label, value, x, y, width, CultivationInkTheme.INK);
        CultivationInkTheme.bar(g, x, y + 12, width, fraction, color);
    }

    private void info(GuiGraphics g, PanelContext c, RealmInkStyle style, int x, int y, int width) {
        meter(g, c, CultivationInkTheme.tr(style.key() + ".progress"), c.progressValue(), c.progressFraction(),
                x, y, width, CultivationInkTheme.JADE);
        meter(g, c, CultivationInkTheme.tr(style.key() + ".stability"), c.stabilityValue(), c.stabilityFraction(),
                x, y + 20, width, CultivationInkTheme.GOLD);
        CultivationInkTheme.pair(g, c.font(), CultivationInkTheme.tr("power"), c.spiritualPowerValue(),
                x, y + 39, width, CultivationInkTheme.MUTED);
        int modeY = y + 51;
        showDetails = pointerX >= x && pointerX < x + width && pointerY >= modeY && pointerY < modeY + 29;
        g.fill(x, modeY, x + width, modeY + 29, 0x40FFFFFF);
        if (c.study().isPresent()) {
            var study = c.study().orElseThrow();
            CultivationInkTheme.pair(g, c.font(),
                    c.text("screen.myvillage.cultivation.study.title", c.techniqueName(study.techniqueId())),
                    PanelReadouts.studyPercent(study) + "%", x + 3, modeY + 2, width - 6, CultivationInkTheme.JADE);
            CultivationInkTheme.bar(g, x + 3, modeY + 13, width - 6, PanelReadouts.studyFraction(study), CultivationInkTheme.GOLD);
            CultivationInkTheme.text(g, c.font(), c.text(PanelReadouts.studyGateKey(study), study.nextGatePoints(), study.gateStabilityCost()),
                    x + 3, modeY + 18, width - 6, CultivationInkTheme.MUTED);
        } else {
            String hint = c.progressFull() ? CultivationInkTheme.tr("full_hint") : CultivationInkTheme.tr("normal_hint");
            CultivationInkTheme.text(g, c.font(), hint, x + 3, modeY + 2, width - 6, CultivationInkTheme.JADE);
            String cost = c.progressFull() ? CultivationInkTheme.tr("consolidation_free")
                    : c.currentStage().flatMap(RealmStageDefinition::spiritStoneCost).map(costValue -> CultivationInkTheme.tr("stone_cost", costValue))
                    .orElse(c.unavailableText());
            CultivationInkTheme.text(g, c.font(), cost, x + 3, modeY + 16, width - 6, CultivationInkTheme.MUTED);
        }
        int ay = y + 83;
        g.fill(x, ay, x + width, ay + 1, CultivationInkTheme.RULE);
        AdvancementDefinition advancement = c.currentStage().flatMap(RealmStageDefinition::advancement).orElse(null);
        if (advancement == null) {
            String key = c.releaseCeiling() ? "terminal" : c.profile().awakened() ? "unavailable" : "initiation";
            CultivationInkTheme.text(g, c.font(), CultivationInkTheme.tr(key), x, ay + 6, width, CultivationInkTheme.MUTED);
            return;
        }
        CultivationInkTheme.text(g, c.font(),
                CultivationInkTheme.tr("next", c.stageName(advancement.targetRealm(), advancement.targetStage())),
                x, ay + 5, width, CultivationInkTheme.INK);
        boolean stable = c.profile().stability() >= advancement.requiredStability();
        CultivationInkTheme.pair(g, c.font(),
                CultivationInkTheme.tr(c.progressFull() ? "progress_met" : "progress_short"),
                CultivationInkTheme.tr(stable ? "stability_met" : "stability_short"), x, ay + 17, width,
                c.progressFull() && stable ? CultivationInkTheme.JADE : CultivationInkTheme.RED);
        CultivationInkTheme.text(g, c.font(),
                CultivationInkTheme.tr("advance_hint", advancement.durationTicks() / 20.0),
                x, ay + 29, width, CultivationInkTheme.MUTED);
        if (advancement.interruptionStabilityLoss() > 0) {
            CultivationInkTheme.text(g, c.font(), CultivationInkTheme.tr("interruption_loss", advancement.interruptionStabilityLoss()),
                    x, ay + 41, width, CultivationInkTheme.RED);
        }
    }
}
