package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.cultivation.ClientCultivationIntentSender;
import com.example.myvillage.client.cultivation.ClientCultivationKeyMappings;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.data.AdvancementDefinition;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.meditation.MeditationState;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.cultivation.meditation.StudyProgress;
import com.example.myvillage.cultivation.network.MeditationIntentAction;
import com.example.myvillage.item.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Meditation and advancement, built around the meridian diagram ({@link MeridianView}): a seated
 * figure whose channels light and circulate with the session state. Beside it (below it when
 * narrow) are cards for progress and stability, what each mode yields and costs, and the next
 * advancement's conditions; the four action buttons are docked under the body. The buttons send
 * the same bounded intents as the keys; every decision stays with the server.
 *
 * <p>While a study (研读) session runs, a study card (technique, comprehension bar, next gate, stop
 * hint) takes the place of the normal and spirit cards, which describe modes that are not running:
 * at the top of the readout column beside the figure, or above the figure when narrow. It reads
 * only the synced {@link StudyProgress}; starting a study is a right-click on the manual, not a
 * button, and the stop button ends it like any session.
 */
public final class MeditationPage extends PanelPage {
    private static final int GAP = 4;
    /** From this body width the figure and the readouts sit side by side. */
    private static final int WIDE = 330;
    private static final float STAGE_SHARE = 0.46F;
    private static final int STAGE_MIN = 120;
    private static final int STAGE_AIR = 16;
    private static final int STAGE_MIN_HEIGHT = 120;
    private static final int STAGE_MAX_HEIGHT = 175;
    private static final int PAD = PanelTheme.CARD_PADDING;
    private static final int ROW = PanelTheme.ROW;
    private static final int TWIN_GAP = 8;
    private static final int CARD_BOTTOM = 3;
    private static final int BARE_TOP = 4;
    private static final int BUTTON_HEIGHT = 18;
    private static final int SPIRIT_PROGRESS_PER_BATCH = 50;

    private PanelButton normalButton;
    private PanelButton spiritButton;
    private PanelButton advancementButton;
    private PanelButton stopButton;

    @Override
    public int init(int x, int y, int width, int height, Consumer<AbstractWidget> widgets) {
        Font font = Minecraft.getInstance().font;
        Component normal = label(
                "screen.myvillage.cultivation.button.normal",
                ClientCultivationKeyMappings.START_NORMAL_MEDITATION);
        Component spirit = label(
                "screen.myvillage.cultivation.button.spirit",
                ClientCultivationKeyMappings.START_SPIRIT_MEDITATION);
        Component advancement = label(
                "screen.myvillage.cultivation.button.advancement",
                ClientCultivationKeyMappings.START_ADVANCEMENT);
        Component stop = label(
                "screen.myvillage.cultivation.button.stop",
                ClientCultivationKeyMappings.STOP_MEDITATION);
        int widest = Math.max(
                Math.max(font.width(normal), font.width(spirit)),
                Math.max(font.width(advancement), font.width(stop))) + 12;
        int columns = widest * 4 + GAP * 3 <= width ? 4 : 2;
        int rows = 4 / columns;
        int dock = GAP + rows * BUTTON_HEIGHT + (rows - 1) * GAP;
        int top = y + height - dock + GAP;
        int cell = (width - GAP * (columns - 1)) / columns;
        int secondRow = top + (rows - 1) * (BUTTON_HEIGHT + GAP);
        int[] columnX = new int[4];
        for (int index = 0; index < 4; index++) {
            columnX[index] = x + (index % columns) * (cell + GAP);
        }
        normalButton = action(normal, PanelTheme.JADE, MeditationIntentAction.START_NORMAL,
                columnX[0], top, cell, widgets);
        spiritButton = action(spirit, PanelTheme.JADE, MeditationIntentAction.START_SPIRIT,
                columnX[1], top, cell, widgets);
        advancementButton = action(advancement, PanelTheme.GOLD_BRIGHT, MeditationIntentAction.START_BREAKTHROUGH,
                columnX[2], secondRow, cell, widgets);
        stopButton = action(stop, PanelTheme.RED, MeditationIntentAction.STOP,
                columnX[3], secondRow, cell, widgets);
        return dock;
    }

    private static Component label(String translationKey, KeyMapping key) {
        return Component.translatable(
                "screen.myvillage.cultivation.button_with_key",
                Component.translatable(translationKey),
                key.getTranslatedKeyMessage());
    }

    private static PanelButton action(
            Component label,
            int accent,
            MeditationIntentAction action,
            int x,
            int y,
            int width,
            Consumer<AbstractWidget> widgets) {
        PanelButton button = new PanelButton(
                PanelButton.Style.ACTION,
                accent,
                x,
                y,
                width,
                BUTTON_HEIGHT,
                label,
                pressed -> ClientCultivationIntentSender.send(action));
        widgets.accept(button);
        return button;
    }

    @Override
    public void setVisible(boolean visible) {
        normalButton.visible = visible;
        spiritButton.visible = visible;
        advancementButton.visible = visible;
        stopButton.visible = visible;
    }

    @Override
    public void refresh(PanelContext context, boolean visible) {
        MeditationStatus status = context.meditation();
        boolean synchronizedStatus = status != null;
        boolean activeSession = synchronizedStatus && status.state().active();
        normalButton.active = visible && synchronizedStatus && !activeSession;
        spiritButton.active = visible && synchronizedStatus && !activeSession;
        advancementButton.active = visible && synchronizedStatus && !activeSession;
        stopButton.active = visible && activeSession;
    }

    @Override
    public int render(GuiGraphics graphics, PanelContext context, int x, int y, int width, int viewportHeight) {
        MeditationStatus status = context.meditation();
        MeridianLook look = MeridianLook.of(status == null ? null : status.state(), channelsOpen(context.profile()));
        MeridianRoute route = runningRoute(context);
        if (width >= WIDE) {
            int stageHeight = Math.max(STAGE_MIN_HEIGHT, viewportHeight);
            // the figure takes the width its drawing and labels need; the readouts get the rest
            int stageWidth = Math.max(STAGE_MIN, Math.min(Math.round(width * STAGE_SHARE),
                    MeridianView.preferredWidth(context, look, route, stageHeight) + STAGE_AIR));
            int infoWidth = width - stageWidth - GAP;
            List<Card> cards = new ArrayList<>();
            studyCard(context, look).ifPresent(cards::add);
            cards.addAll(cards(context, look, infoWidth));
            int infoHeight = Math.max(stageHeight, naturalHeight(context.font(), cards, infoWidth));
            MeridianView.render(graphics, context, look, route, x, y, stageWidth, stageHeight);
            drawCards(graphics, context.font(), cards, x + stageWidth + GAP, y, infoWidth, infoHeight);
            return infoHeight;
        }
        // narrow: the figure fills the first screen, the readouts follow below it; a study card
        // goes above the figure so its progress shows without scrolling
        int top = y;
        Optional<Card> study = studyCard(context, look);
        if (study.isPresent()) {
            int studyHeight = cardHeight(context.font(), study.get(), width);
            drawCard(graphics, context.font(), study.get(), x, top, width, studyHeight, 0);
            top += studyHeight + GAP;
        }
        int stageHeight = Math.max(STAGE_MIN_HEIGHT, Math.min(STAGE_MAX_HEIGHT, viewportHeight));
        MeridianView.render(graphics, context, look, route, x, top, width, stageHeight);
        List<Card> cards = cards(context, look, width);
        int infoHeight = naturalHeight(context.font(), cards, width);
        drawCards(graphics, context.font(), cards, x, top + stageHeight + GAP, width, infoHeight);
        return top - y + stageHeight + GAP + infoHeight;
    }

    /** The running core technique's meditation route; the small circuit when there is none. */
    private static MeridianRoute runningRoute(PanelContext context) {
        return MeridianRoute.of(context.profile().activeCoreTechnique()
                .flatMap(context::technique)
                .flatMap(TechniqueDefinition::meditationRoute));
    }

    private static boolean channelsOpen(CultivationProfile profile) {
        return profile.awakened()
                && profile.learnedTechniques().containsKey(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID);
    }

    // ---- readout cards ---------------------------------------------------------------------

    /** A label and its value; {@code met} marks a condition row (null for a plain pair). */
    private record Item(String label, String value, Boolean met) {
        static Item pair(String label, String value) {
            return new Item(label, value, null);
        }

        int naturalWidth(Font font) {
            return (met == null ? 0 : 8) + font.width(label) + 6 + font.width(value);
        }
    }

    private sealed interface Row permits Single, Twin, Note, Meters, Gauge {
    }

    private record Single(Item item) implements Row {
    }

    /** Two items side by side when both fit in half the width, otherwise one under the other. */
    private record Twin(Item left, Item right) implements Row {
    }

    /** A line of text with an optional note right-aligned after it. */
    private record Note(String text, int color, String aside, int asideColor) implements Row {
    }

    /** The progress and stability meters. */
    private record Meters(String progressLabel, String progressValue, double progress,
                          String stabilityLabel, String stabilityValue, double stability) implements Row {
    }

    /** One full-width meter: a label and value over a bar. */
    private record Gauge(String label, String value, double fraction, int top, int bottom) implements Row {
    }

    /** A readout card; a null title draws a bare card without a title row. */
    private record Card(String title, int accent, String chip, List<Row> rows) {
    }

    private List<Card> cards(PanelContext context, MeridianLook look, int width) {
        CultivationProfile profile = context.profile();
        MeditationStatus status = context.meditation();
        MeditationState state = status == null ? null : status.state();
        Optional<RealmStageDefinition> stage = context.currentStage();
        String unavailable = context.unavailableText();
        List<Card> cards = new ArrayList<>();

        cards.add(new Card(null, 0, null, List.of(new Meters(
                context.text("screen.myvillage.cultivation.progress"), context.progressValue(), context.progressFraction(),
                context.text("screen.myvillage.cultivation.stability"), context.stabilityValue(),
                context.stabilityFraction()))));
        if (context.study().isPresent()) {
            // the study card stands in for the two meditation modes while a manual is read
            cards.add(advancementCard(context, look, state));
            return cards;
        }

        boolean normalActive = state == MeditationState.PREPARING_NORMAL || state == MeditationState.MEDITATING_NORMAL;
        cards.add(new Card(context.text("screen.myvillage.cultivation.button.normal"),
                normalActive ? look.color() : 0, null, List.of(
                        new Twin(
                                Item.pair(context.text("screen.myvillage.cultivation.normal_rate"),
                                        context.text("screen.myvillage.cultivation.rate_per_ten_ticks",
                                                profile.spiritualAffinity())),
                                Item.pair(context.text("screen.myvillage.cultivation.basic_breathing_mastery"),
                                        basicBreathingMastery(profile, unavailable))),
                        new Single(Item.pair(context.text("screen.myvillage.cultivation.stability_gain"),
                                stabilityGainValue(context, stage, unavailable))))));

        boolean spiritActive = state == MeditationState.PREPARING_SPIRIT || state == MeditationState.MEDITATING_SPIRIT;
        cards.add(new Card(context.text("screen.myvillage.cultivation.button.spirit"),
                spiritActive ? look.color() : 0, null, List.of(
                        new Twin(
                                Item.pair(context.text("screen.myvillage.cultivation.spirit_rate"),
                                        context.text("screen.myvillage.cultivation.rate_per_ten_ticks",
                                                SPIRIT_PROGRESS_PER_BATCH)),
                                Item.pair(context.text("screen.myvillage.cultivation.spirit_inventory"),
                                        spiritStoneInventory(context, unavailable))),
                        new Single(Item.pair(context.text("screen.myvillage.cultivation.spirit_cost"),
                                spiritCostValue(context, stage, unavailable))))));

        cards.add(advancementCard(context, look, state));
        return cards;
    }

    /** The study (研读) card while a study session runs: title, comprehension bar, next gate, stop hint. */
    private static Optional<Card> studyCard(PanelContext context, MeridianLook look) {
        Optional<StudyProgress> study = context.study();
        if (study.isEmpty()) {
            return Optional.empty();
        }
        StudyProgress progress = study.get();
        String title = context.text("screen.myvillage.cultivation.study.title",
                context.techniqueName(progress.techniqueId()));
        String percent = context.text("screen.myvillage.cultivation.study.percent",
                PanelReadouts.studyPercent(progress));
        String gate = context.text(PanelReadouts.studyGateKey(progress),
                progress.nextGatePoints(), progress.gateStabilityCost());
        int gateColor = progress.nextGatePoints() == StudyProgress.NO_GATE
                ? PanelTheme.MUTED
                : PanelReadouts.studyGateShort(progress, context.profile().stability()) ? PanelTheme.AMBER : PanelTheme.TEXT;
        String hint = context.text("screen.myvillage.cultivation.study.stop_hint",
                ClientCultivationKeyMappings.STOP_MEDITATION.getTranslatedKeyMessage());
        return Optional.of(new Card(title, look.color(), percent, List.of(
                new Gauge(context.text("screen.myvillage.cultivation.study.comprehension"),
                        progress.points() + " / " + progress.totalPoints(),
                        PanelReadouts.studyFraction(progress), PanelTheme.AMBER, PanelTheme.AMBER_DARK),
                new Note(gate, gateColor, "", 0),
                new Note(hint, PanelTheme.MUTED, "", 0))));
    }

    private Card advancementCard(PanelContext context, MeridianLook look, MeditationState state) {
        CultivationProfile profile = context.profile();
        int accent = state != null && state.advancing() ? look.color() : 0;
        RealmStageDefinition stage = context.currentStage().orElse(null);
        AdvancementDefinition advancement = stage == null ? null : stage.advancement().orElse(null);
        if (advancement == null) {
            String message;
            if (stage == null) {
                message = context.unavailableText();
            } else if (stage.id().equals(ModCultivationRegistries.QI_REFINING_4_STAGE_ID)) {
                message = context.text("screen.myvillage.cultivation.advancement_release_ceiling");
            } else {
                message = context.text("screen.myvillage.cultivation.advancement_unavailable");
            }
            return new Card(context.text("screen.myvillage.cultivation.advancement"), accent, null,
                    List.of(new Note(message, PanelTheme.MUTED, "", 0)));
        }
        String kind = context.text(
                "screen.myvillage.cultivation.advancement_kind." + advancement.kind().serializedName());
        String target = context.text(
                "screen.myvillage.cultivation.advancement_target",
                context.stageName(profile.realmId(), profile.stageId()),
                context.stageName(advancement.targetRealm(), advancement.targetStage()));
        String loss = advancement.interruptionStabilityLoss() > 0
                ? context.text(
                        "screen.myvillage.cultivation.advancement_interruption_value",
                        advancement.interruptionStabilityLoss())
                : "";
        List<Row> rows = new ArrayList<>();
        if (!loss.isEmpty()) {
            rows.add(new Note(loss, PanelTheme.AMBER, "", 0));
        }
        rows.add(new Twin(
                new Item(context.text("screen.myvillage.cultivation.condition_progress"),
                        context.progressValue(), context.progressFull()),
                new Item(context.text("screen.myvillage.cultivation.condition_stability"),
                        profile.stability() + " / " + advancement.requiredStability(),
                        profile.stability() >= advancement.requiredStability())));
        rows.add(new Twin(
                Item.pair(context.text("screen.myvillage.cultivation.advancement_duration"),
                        context.text("screen.myvillage.cultivation.ticks_value", advancement.durationTicks())),
                Item.pair(context.text("screen.myvillage.cultivation.advancement_cost"),
                        Integer.toString(advancement.stabilityCost()))));
        // the card is titled by the advancement itself; its kind chip names what it is
        return new Card(target, accent, kind, rows);
    }

    private static boolean twinFits(Font font, Twin twin, int innerWidth) {
        int half = (innerWidth - TWIN_GAP) / 2;
        return twin.left().naturalWidth(font) <= half && twin.right().naturalWidth(font) <= half;
    }

    private static boolean metersFit(Font font, Meters meters, int innerWidth) {
        int half = (innerWidth - TWIN_GAP) / 2;
        return font.width(meters.progressLabel()) + 6 + font.width(meters.progressValue()) <= half
                && font.width(meters.stabilityLabel()) + 6 + font.width(meters.stabilityValue()) <= half;
    }

    private static int rowHeight(Font font, Row row, int innerWidth) {
        return switch (row) {
            case Single single -> itemHeight(font, single.item(), innerWidth);
            case Twin twin -> twinFits(font, twin, innerWidth)
                    ? ROW
                    : itemHeight(font, twin.left(), innerWidth) + itemHeight(font, twin.right(), innerWidth);
            case Note note -> ROW + 1;
            case Meters meters -> metersFit(font, meters, innerWidth)
                    ? PanelTheme.METER_HEIGHT + 1
                    : PanelTheme.METER_HEIGHT * 2 + 4;
            case Gauge gauge -> PanelTheme.METER_HEIGHT + 1;
        };
    }

    private static int cardHeight(Font font, Card card, int width) {
        int height = (card.title() == null ? BARE_TOP : PanelTheme.CARD_TITLE_HEIGHT) + CARD_BOTTOM;
        for (Row row : card.rows()) {
            height += rowHeight(font, row, width - PAD * 2);
        }
        return height;
    }

    private static int naturalHeight(Font font, List<Card> cards, int width) {
        int height = GAP * (cards.size() - 1);
        for (Card card : cards) {
            height += cardHeight(font, card, width);
        }
        return height;
    }

    /** Draws the cards top to bottom, sharing any height beyond their natural size evenly. */
    private static void drawCards(GuiGraphics graphics, Font font, List<Card> cards, int x, int y, int width, int height) {
        int extra = Math.max(0, height - naturalHeight(font, cards, width));
        int cursor = y;
        for (int index = 0; index < cards.size(); index++) {
            Card card = cards.get(index);
            int share = extra / cards.size() + (index < extra % cards.size() ? 1 : 0);
            int cardHeight = cardHeight(font, card, width) + share;
            drawCard(graphics, font, card, x, cursor, width, cardHeight, share / 2);
            cursor += cardHeight + GAP;
        }
    }

    private static void drawCard(GuiGraphics graphics, Font font, Card card, int x, int y, int width, int height,
                                 int offset) {
        if (card.title() == null) {
            graphics.fill(x, y, x + width, y + height, PanelTheme.CARD);
            graphics.renderOutline(x, y, width, height, PanelTheme.CARD_BORDER);
        } else {
            PanelTheme.card(graphics, font, x, y, width, height, Component.literal(card.title()));
        }
        if (card.accent() != 0) {
            // the card of the running session takes the diagram's colour
            graphics.fill(x + 1, y + 1, x + 3, y + height - 1, card.accent());
            PanelTheme.diamond(graphics, x + 8, y + 7, 2, card.accent());
        }
        if (card.chip() != null) {
            int chipX = x + width - PAD - PanelTheme.chipWidth(font, card.chip());
            graphics.fill(chipX - 3, y + 1, x + width - 1, y + PanelTheme.CARD_TITLE_HEIGHT, PanelTheme.CARD);
            PanelTheme.chip(graphics, font, card.chip(), chipX, y + 1, PanelTheme.GOLD);
        }
        int innerX = x + PAD;
        int innerWidth = width - PAD * 2;
        int cursor = y + (card.title() == null ? BARE_TOP : PanelTheme.CARD_TITLE_HEIGHT) + offset;
        for (Row row : card.rows()) {
            drawRow(graphics, font, row, innerX, cursor, innerWidth);
            cursor += rowHeight(font, row, innerWidth);
        }
    }

    private static void drawRow(GuiGraphics graphics, Font font, Row row, int x, int y, int width) {
        switch (row) {
            case Single single -> drawItem(graphics, font, single.item(), x, y, width);
            case Twin twin -> {
                if (twinFits(font, twin, width)) {
                    int half = (width - TWIN_GAP) / 2;
                    drawItem(graphics, font, twin.left(), x, y, half);
                    drawItem(graphics, font, twin.right(), x + width - half, y, half);
                } else {
                    drawItem(graphics, font, twin.left(), x, y, width);
                    drawItem(graphics, font, twin.right(), x, y + itemHeight(font, twin.left(), width), width);
                }
            }
            case Note note -> {
                int asideWidth = note.aside().isEmpty() ? 0 : font.width(note.aside()) + 8;
                boolean aside = asideWidth > 0 && asideWidth <= width / 2;
                graphics.drawString(font, PanelTheme.fit(font, note.text(), Math.max(1, width - (aside ? asideWidth : 0))),
                        x, y, note.color(), false);
                if (aside) {
                    graphics.drawString(font, note.aside(), x + width - font.width(note.aside()), y,
                            note.asideColor(), false);
                }
            }
            case Meters meters -> {
                boolean side = metersFit(font, meters, width);
                int meterWidth = side ? (width - TWIN_GAP) / 2 : width;
                PanelTheme.meter(graphics, font, meters.progressLabel(), meters.progressValue(), x, y, meterWidth,
                        meters.progress(), PanelTheme.JADE, PanelTheme.JADE_DARK);
                PanelTheme.meter(graphics, font, meters.stabilityLabel(), meters.stabilityValue(),
                        side ? x + width - meterWidth : x, side ? y : y + PanelTheme.METER_HEIGHT + 3, meterWidth,
                        meters.stability(), PanelTheme.GOLD_BRIGHT, PanelTheme.GOLD_DIM);
            }
            case Gauge gauge -> PanelTheme.meter(graphics, font, gauge.label(), gauge.value(), x, y, width,
                    gauge.fraction(), gauge.top(), gauge.bottom());
        }
    }

    /** One row, or two when the label and value do not fit side by side. */
    private static int itemHeight(Font font, Item item, int width) {
        return item.naturalWidth(font) <= width ? ROW : ROW * 2;
    }

    private static void drawItem(GuiGraphics graphics, Font font, Item item, int x, int y, int width) {
        if (item.naturalWidth(font) > width) {
            // too long for one line: the label, then the value right-aligned under it
            int indent = item.met() == null ? 0 : 8;
            if (item.met() != null) {
                PanelTheme.condition(graphics, font, item.met(), item.label(), "", x, y, width);
            } else {
                graphics.drawString(font, PanelTheme.fit(font, item.label(), width), x, y, PanelTheme.MUTED, false);
            }
            String value = PanelTheme.fit(font, item.value(), width - indent);
            int color = item.met() != null && item.met() ? PanelTheme.JADE : PanelTheme.TEXT;
            graphics.drawString(font, value, x + width - font.width(value), y + ROW, color, false);
            return;
        }
        if (item.met() == null) {
            PanelTheme.pair(graphics, font, item.label(), item.value(), x, y, width, PanelTheme.TEXT);
        } else {
            PanelTheme.condition(graphics, font, item.met(), item.label(), item.value(), x, y, width);
        }
    }

    private String stabilityGainValue(
            PanelContext context,
            Optional<RealmStageDefinition> stage,
            String unavailable) {
        if (stage.isEmpty()
                || stage.orElseThrow().cultivationCap().isEmpty()
                || stage.orElseThrow().stabilityCap().isEmpty()) {
            return unavailable;
        }
        RealmStageDefinition definition = stage.orElseThrow();
        CultivationProfile profile = context.profile();
        if (profile.cultivationProgress() < definition.cultivationCap().orElseThrow()) {
            return context.text("screen.myvillage.cultivation.stability_locked");
        }
        if (profile.stability() >= definition.stabilityCap().orElseThrow()) {
            return context.text("screen.myvillage.cultivation.stability_capped");
        }
        return context.text("screen.myvillage.cultivation.rate_per_ten_ticks", profile.spiritualAffinity());
    }

    private String spiritCostValue(
            PanelContext context,
            Optional<RealmStageDefinition> stage,
            String unavailable) {
        if (stage.isEmpty()) {
            return unavailable;
        }
        RealmStageDefinition definition = stage.orElseThrow();
        if (definition.cultivationCap().isPresent()
                && context.profile().cultivationProgress() >= definition.cultivationCap().orElseThrow()) {
            return context.text("screen.myvillage.cultivation.stability_no_stone_cost");
        }
        return definition.spiritStoneCost()
                .map(cost -> context.text("screen.myvillage.cultivation.cost_per_ten_ticks", cost))
                .orElse(unavailable);
    }

    private String basicBreathingMastery(CultivationProfile profile, String unavailable) {
        TechniqueProgress progress = profile.learnedTechniques()
                .get(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID);
        return progress == null ? unavailable : Long.toString(progress.masteryPoints());
    }

    private String spiritStoneInventory(PanelContext context, String unavailable) {
        if (context.minecraft() == null || context.minecraft().player == null) {
            return unavailable;
        }
        Inventory inventory = context.minecraft().player.getInventory();
        long count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.LOW_GRADE_SPIRIT_STONE.get())) {
                count = Math.min(Integer.MAX_VALUE, count + stack.getCount());
            }
        }
        return Long.toString(count);
    }
}
