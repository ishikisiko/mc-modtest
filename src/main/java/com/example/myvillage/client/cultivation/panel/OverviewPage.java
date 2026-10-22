package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.data.AdvancementDefinition;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.network.CultivationTimeSnapshotPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The inward look: realm ladder, progress, lifespan, spiritual root, and the next advancement. */
public final class OverviewPage extends PanelPage {
    private static final int GAP = 4;
    private static final int WIDE = 330;
    private static final int PAD = InkPanelTheme.CARD_PADDING;
    private static final int ROW = InkPanelTheme.ROW;
    private static final int REALM_HEIGHT = 90;
    private static final int LIFESPAN_HEIGHT = 80;
    private static final int ADVANCEMENT_HEIGHT = 76;
    private static final int UNAWAKENED_ROOT_HEIGHT = 42;
    private static final int MAX_STRETCH = 24;

    @Override
    public int render(GuiGraphics graphics, PanelContext context, int x, int y, int width, int viewportHeight) {
        int rootHeight = rootHeight(context.profile());
        if (width >= WIDE) {
            int leftWidth = (width - GAP) * 3 / 5;
            int rightWidth = width - GAP - leftWidth;
            int rightX = x + leftWidth + GAP;
            int top = Math.max(REALM_HEIGHT, LIFESPAN_HEIGHT);
            int bottom = Math.max(rootHeight, ADVANCEMENT_HEIGHT);
            int spare = Math.max(0, Math.min(MAX_STRETCH, viewportHeight - top - GAP - bottom));
            top += spare / 2;
            bottom += spare - spare / 2;
            drawRealm(graphics, context, x, y, leftWidth, top);
            drawLifespan(graphics, context, rightX, y, rightWidth, top);
            drawRoot(graphics, context, x, y + top + GAP, leftWidth, bottom);
            drawAdvancement(graphics, context, rightX, y + top + GAP, rightWidth, bottom);
            return top + GAP + bottom;
        }
        int cursor = y;
        drawRealm(graphics, context, x, cursor, width, REALM_HEIGHT);
        cursor += REALM_HEIGHT + GAP;
        drawLifespan(graphics, context, x, cursor, width, LIFESPAN_HEIGHT);
        cursor += LIFESPAN_HEIGHT + GAP;
        drawRoot(graphics, context, x, cursor, width, rootHeight);
        cursor += rootHeight + GAP;
        drawAdvancement(graphics, context, x, cursor, width, ADVANCEMENT_HEIGHT);
        return cursor + ADVANCEMENT_HEIGHT - y;
    }

    private void drawRealm(GuiGraphics graphics, PanelContext context, int x, int y, int width, int height) {
        Font font = context.font();
        CultivationProfile profile = context.profile();
        InkPanelTheme.card(graphics, font, x, y, width, height,
                Component.translatable("screen.myvillage.cultivation.card.realm").append(profile.foundationQuality()>0?" · "+Component.translatable("alchemy.myvillage.quality."+profile.foundationQuality()).getString()+"根基":""));
        int innerX = x + PAD;
        int innerWidth = width - PAD * 2;
        int cursor = y + InkPanelTheme.CARD_TITLE_HEIGHT + 1;
        drawLadder(graphics, context, innerX, cursor, innerWidth);
        cursor += 24;
        InkPanelTheme.meter(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.progress"),
                context.progressValue(),
                innerX,
                cursor,
                innerWidth,
                context.progressFraction(),
                InkPanelTheme.JADE,
                InkPanelTheme.JADE_DARK);
        cursor += InkPanelTheme.METER_HEIGHT + 3;
        InkPanelTheme.meter(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.stability"),
                context.stabilityValue(),
                innerX,
                cursor,
                innerWidth,
                context.stabilityFraction(),
                InkPanelTheme.GOLD_BRIGHT,
                InkPanelTheme.GOLD_DIM);
        cursor += InkPanelTheme.METER_HEIGHT + 4;
        int half = (innerWidth - 10) / 2;
        InkPanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.power"),
                context.spiritualPowerValue(),
                innerX,
                cursor,
                half,
                InkPanelTheme.TEXT);
        InkPanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.spiritual_affinity"),
                Integer.toString(profile.spiritualAffinity()),
                innerX + half + 10,
                cursor,
                innerWidth - half - 10,
                InkPanelTheme.TEXT);
    }

    /**
     * One node per stage of the current realm: passed stages are solid, the current one is a gem,
     * and a later stage with neither a cap nor an advancement into it is a small faint outline.
     */
    private void drawLadder(GuiGraphics graphics, PanelContext context, int x, int y, int width) {
        Font font = context.font();
        List<RealmStageDefinition> stages = context.currentRealmStages();
        int current = PanelReadouts.stageIndex(stages, context.profile().stageId());
        if (stages.isEmpty() || current < 0) {
            graphics.drawString(
                    font,
                    context.stageName(context.profile().realmId(), context.profile().stageId()),
                    x,
                    y + 6,
                    InkPanelTheme.MUTED,
                    false);
            return;
        }
        int count = stages.size();
        int slot = width / count;
        List<String> names = new ArrayList<>(count);
        boolean namesFit = true;
        for (RealmStageDefinition stage : stages) {
            String name = Component.translatable(stage.translationKey()).getString();
            names.add(name);
            namesFit &= font.width(name) <= slot - 4;
        }
        int nodeY = y + 5;
        int firstX = x + slot / 2;
        graphics.fill(firstX, nodeY, x + slot * (count - 1) + slot / 2 + 1, nodeY + 1, InkPanelTheme.DIVIDER);
        if (current > 0) {
            graphics.fill(firstX, nodeY, x + slot * current + slot / 2 + 1, nodeY + 1, InkPanelTheme.GOLD);
        }
        for (int index = 0; index < count; index++) {
            int centerX = x + slot * index + slot / 2;
            int labelColor;
            if (index < current) {
                InkPanelTheme.diamond(graphics, centerX, nodeY, 3, InkPanelTheme.GOLD);
                labelColor = InkPanelTheme.MUTED;
            } else if (index == current) {
                InkPanelTheme.diamond(graphics, centerX, nodeY, 4, InkPanelTheme.GOLD_BRIGHT);
                InkPanelTheme.diamond(graphics, centerX, nodeY, 2, InkPanelTheme.JADE);
                labelColor = InkPanelTheme.GOLD_BRIGHT;
            } else {
                boolean reachable = stages.get(index).cultivationCap().isPresent()
                        || stages.get(index - 1).advancement().isPresent();
                InkPanelTheme.hollowDiamond(
                        graphics,
                        centerX,
                        nodeY,
                        reachable ? 3 : 2,
                        reachable ? InkPanelTheme.MUTED : InkPanelTheme.FAINT,
                        InkPanelTheme.CARD);
                labelColor = reachable ? InkPanelTheme.MUTED : InkPanelTheme.FAINT;
            }
            String label = namesFit ? names.get(index) : Integer.toString(index + 1);
            graphics.drawString(font, label, centerX - font.width(label) / 2, y + 13, labelColor, false);
        }
    }

    private void drawLifespan(GuiGraphics graphics, PanelContext context, int x, int y, int width, int height) {
        Font font = context.font();
        InkPanelTheme.card(graphics, font, x, y, width, height,
                Component.translatable("screen.myvillage.cultivation.card.lifespan"));
        int innerX = x + PAD;
        int innerWidth = width - PAD * 2;
        int cursor = y + InkPanelTheme.CARD_TITLE_HEIGHT + 1;
        CultivationTimeSnapshotPayload time = context.time();
        InkPanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.calendar"),
                context.calendarValue(),
                innerX,
                cursor,
                innerWidth,
                time == null ? InkPanelTheme.MUTED : InkPanelTheme.TEXT);
        cursor += ROW + 1;
        if (time != null) {
            boolean spent = time.lifespanAvailable() && time.exhausted();
            double remaining = time.lifespanAvailable()
                    ? PanelReadouts.fraction(time.remainingLifespanTicks(), time.maximumLifespanTicks())
                    : 0.0D;
            InkPanelTheme.pair(
                    graphics,
                    font,
                    context.text("screen.myvillage.cultivation.lifespan_remaining"),
                    context.remainingLifespanValue(),
                    innerX,
                    cursor,
                    innerWidth,
                    spent ? InkPanelTheme.RED : InkPanelTheme.TEXT);
            InkPanelTheme.bar(
                    graphics, innerX, cursor + ROW, innerWidth, 5, remaining, InkPanelTheme.AMBER, InkPanelTheme.AMBER_DARK);
            cursor += InkPanelTheme.METER_HEIGHT + 3;
            InkPanelTheme.pair(
                    graphics,
                    font,
                    context.text("screen.myvillage.cultivation.lifespan_consumed"),
                    context.text(
                            "screen.myvillage.cultivation.years_value",
                            PanelReadouts.yearsFloor(
                                    time.lifespanConsumedTicks(), time.ticksPerDay(), time.daysPerYear())),
                    innerX,
                    cursor,
                    innerWidth,
                    InkPanelTheme.TEXT);
            cursor += ROW + 1;
        }
        InkPanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.session"),
                context.sessionText(),
                innerX,
                cursor,
                innerWidth,
                context.sessionColor());
        cursor += ROW + 1;
        InkPanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.techniques"),
                context.text(
                        "screen.myvillage.cultivation.techniques_count",
                        context.profile().learnedTechniques().size()),
                innerX,
                cursor,
                innerWidth,
                InkPanelTheme.TEXT);
    }

    private static int rootHeight(CultivationProfile profile) {
        return profile.spiritualRoot()
                .map(root -> InkPanelTheme.CARD_TITLE_HEIGHT + 11 + root.affinitiesBasisPoints().size() * ROW + 4)
                .orElse(UNAWAKENED_ROOT_HEIGHT);
    }

    private void drawRoot(GuiGraphics graphics, PanelContext context, int x, int y, int width, int height) {
        Font font = context.font();
        InkPanelTheme.card(graphics, font, x, y, width, height,
                Component.translatable("screen.myvillage.cultivation.spiritual_root"));
        int innerX = x + PAD;
        int innerWidth = width - PAD * 2;
        int cursor = y + InkPanelTheme.CARD_TITLE_HEIGHT + 2;
        Optional<SpiritualRoot> root = context.profile().spiritualRoot();
        if (root.isEmpty()) {
            graphics.drawString(
                    font,
                    context.text("screen.myvillage.cultivation.unawakened"),
                    innerX,
                    cursor,
                    InkPanelTheme.MUTED,
                    false);
            graphics.drawString(
                    font,
                    InkPanelTheme.fit(font, context.text("screen.myvillage.cultivation.root_hint"), innerWidth),
                    innerX,
                    cursor + ROW + 2,
                    InkPanelTheme.FAINT,
                    false);
            return;
        }

        List<Map.Entry<ResourceLocation, Integer>> affinities = new ArrayList<>(
                root.get().affinitiesBasisPoints().entrySet());
        affinities.sort(Comparator
                .comparingInt((Map.Entry<ResourceLocation, Integer> entry) -> context.elementSortOrder(entry.getKey()))
                .thenComparing(entry -> entry.getKey().toString()));

        // One bar split by share, so the root's makeup reads at a glance.
        graphics.fill(innerX, cursor, innerX + innerWidth, cursor + 5, InkPanelTheme.BAR_TRACK);
        long total = 0;
        for (Map.Entry<ResourceLocation, Integer> affinity : affinities) {
            total += affinity.getValue();
        }
        long before = 0;
        for (Map.Entry<ResourceLocation, Integer> affinity : affinities) {
            int from = innerX + (int) (innerWidth * before / Math.max(1, total));
            before += affinity.getValue();
            int to = innerX + (int) (innerWidth * before / Math.max(1, total));
            if (to > from) {
                graphics.fill(from, cursor, to, cursor + 5, context.elementColor(affinity.getKey()));
            }
        }
        cursor += 9;

        int nameWidth = 0;
        for (Map.Entry<ResourceLocation, Integer> affinity : affinities) {
            nameWidth = Math.max(nameWidth, font.width(context.elementName(affinity.getKey())));
        }
        nameWidth = Math.min(nameWidth, innerWidth / 3);
        int percentWidth = font.width("100.0%");
        int trackX = innerX + 9 + nameWidth + 6;
        int trackWidth = innerX + innerWidth - percentWidth - 6 - trackX;
        for (Map.Entry<ResourceLocation, Integer> affinity : affinities) {
            int color = context.elementColor(affinity.getKey());
            String percent = String.format(Locale.ROOT, "%.1f%%", affinity.getValue() / 100.0D);
            graphics.fill(innerX, cursor + 2, innerX + 5, cursor + 7, color);
            graphics.drawString(
                    font,
                    InkPanelTheme.fit(font, context.elementName(affinity.getKey()).getString(), nameWidth),
                    innerX + 9,
                    cursor,
                    InkPanelTheme.TEXT,
                    false);
            if (trackWidth > 8) {
                graphics.fill(trackX, cursor + 3, trackX + trackWidth, cursor + 6, InkPanelTheme.BAR_TRACK);
                int filled = (int) Math.round(trackWidth * PanelReadouts.fraction(affinity.getValue(), 10_000));
                graphics.fill(trackX, cursor + 3, trackX + filled, cursor + 6, color);
            }
            graphics.drawString(
                    font,
                    percent,
                    innerX + innerWidth - font.width(percent),
                    cursor,
                    InkPanelTheme.MUTED,
                    false);
            cursor += ROW;
        }
    }

    private void drawAdvancement(GuiGraphics graphics, PanelContext context, int x, int y, int width, int height) {
        Font font = context.font();
        CultivationProfile profile = context.profile();
        InkPanelTheme.card(graphics, font, x, y, width, height,
                Component.translatable("screen.myvillage.cultivation.advancement"));
        int innerX = x + PAD;
        int innerWidth = width - PAD * 2;
        int cursor = y + InkPanelTheme.CARD_TITLE_HEIGHT + 1;
        RealmStageDefinition stage = context.currentStage().orElse(null);
        if (stage == null) {
            graphics.drawString(font, context.unavailableText(), innerX, cursor, InkPanelTheme.MUTED, false);
            return;
        }
        AdvancementDefinition advancement = stage.advancement().orElse(null);
        if (advancement == null) {
            Component message = Component.translatable(
                    context.releaseCeiling()
                            ? "screen.myvillage.cultivation.advancement_release_ceiling"
                            : "screen.myvillage.cultivation.advancement_unavailable");
            for (FormattedCharSequence line : font.split(message, innerWidth)) {
                graphics.drawString(font, line, innerX, cursor, InkPanelTheme.MUTED, false);
                cursor += ROW;
            }
            return;
        }

        String target = context.text(
                "screen.myvillage.cultivation.advancement_target",
                context.stageName(profile.realmId(), profile.stageId()),
                context.stageName(advancement.targetRealm(), advancement.targetStage()));
        graphics.drawString(font, InkPanelTheme.fit(font, target, innerWidth), innerX, cursor, InkPanelTheme.TEXT, false);
        cursor += ROW + 1;
        InkPanelTheme.chip(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.advancement_kind."
                        + advancement.kind().serializedName()),
                innerX,
                cursor,
                InkPanelTheme.GOLD);
        cursor += 14;
        InkPanelTheme.condition(
                graphics,
                font,
                context.progressFull(),
                context.text("screen.myvillage.cultivation.condition_progress"),
                context.progressValue(),
                innerX,
                cursor,
                innerWidth);
        cursor += ROW + 1;
        InkPanelTheme.condition(
                graphics,
                font,
                profile.stability() >= advancement.requiredStability(),
                context.text("screen.myvillage.cultivation.condition_stability"),
                profile.stability() + " / " + advancement.requiredStability(),
                innerX,
                cursor,
                innerWidth);
        cursor += ROW + 1;
        InkPanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.advancement_duration"),
                context.text("screen.myvillage.cultivation.ticks_value", advancement.durationTicks()),
                innerX,
                cursor,
                innerWidth,
                InkPanelTheme.TEXT);
    }
}
