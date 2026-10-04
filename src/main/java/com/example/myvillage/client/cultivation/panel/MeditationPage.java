package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.cultivation.ClientCultivationIntentSender;
import com.example.myvillage.client.cultivation.ClientCultivationKeyMappings;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.data.AdvancementDefinition;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
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

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Meditation and advancement: the session state, what each mode yields and costs, the next
 * advancement's conditions, and the four action buttons. The buttons send the same bounded
 * intents as the keys; every decision stays with the server.
 */
public final class MeditationPage extends PanelPage {
    private static final int GAP = 4;
    private static final int WIDE = 300;
    private static final int PAD = PanelTheme.CARD_PADDING;
    private static final int ROW = PanelTheme.ROW;
    private static final int BUTTON_HEIGHT = 18;
    private static final int MAX_STRETCH = 18;
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
        boolean wide = width >= WIDE;
        int stateHeight = wide ? 47 : 65;
        int modeHeight = PanelTheme.CARD_TITLE_HEIGHT + 1 + 3 * ROW + 4;
        int advancementHeight = wide ? 50 : 71;
        int natural = stateHeight + GAP + (wide ? modeHeight : modeHeight * 2 + GAP) + GAP + advancementHeight;
        int stretch = Math.max(0, Math.min(MAX_STRETCH, viewportHeight - natural)) / 3;
        stateHeight += stretch;
        modeHeight += stretch;
        advancementHeight += stretch;

        int cursor = y;
        drawState(graphics, context, x, cursor, width, stateHeight, wide);
        cursor += stateHeight + GAP;
        if (wide) {
            int half = (width - GAP) / 2;
            drawNormalMode(graphics, context, x, cursor, half, modeHeight);
            drawSpiritMode(graphics, context, x + half + GAP, cursor, width - half - GAP, modeHeight);
            cursor += modeHeight + GAP;
        } else {
            drawNormalMode(graphics, context, x, cursor, width, modeHeight);
            cursor += modeHeight + GAP;
            drawSpiritMode(graphics, context, x, cursor, width, modeHeight);
            cursor += modeHeight + GAP;
        }
        drawAdvancement(graphics, context, x, cursor, width, advancementHeight, wide);
        return cursor + advancementHeight - y;
    }

    private void drawState(
            GuiGraphics graphics, PanelContext context, int x, int y, int width, int height, boolean wide) {
        Font font = context.font();
        PanelTheme.card(graphics, font, x, y, width, height,
                Component.translatable("screen.myvillage.cultivation.session"));
        int innerX = x + PAD;
        int innerWidth = width - PAD * 2;
        int cursor = y + PanelTheme.CARD_TITLE_HEIGHT + 1;
        int color = context.sessionColor();
        PanelTheme.diamond(graphics, innerX + 2, cursor + 3, 2, color);
        graphics.drawString(font, context.sessionText(), innerX + 9, cursor, color, false);

        MeditationStatus status = context.meditation();
        // Only advancement reports its remaining ticks while it runs (the server repeats the
        // status at its feedback interval); preparation is announced once, so it gets no countdown.
        if (status != null && status.state().advancing()) {
            String runtime = context.text(
                    "screen.myvillage.cultivation.advancement_runtime_value",
                    status.advancementTicksRemaining(),
                    status.advancementDurationTicks());
            int runtimeWidth = innerWidth - 9 - font.width(context.sessionText()) - 8;
            String fitted = PanelTheme.fit(font, runtime, Math.max(1, runtimeWidth));
            graphics.drawString(
                    font, fitted, innerX + innerWidth - font.width(fitted), cursor, PanelTheme.MUTED, false);
            double done = 1.0D - PanelReadouts.fraction(
                    status.advancementTicksRemaining(), status.advancementDurationTicks());
            graphics.fill(x + 1, y + height - 3, x + 1 + (int) Math.round((width - 2) * done), y + height - 1, color);
        }
        cursor += ROW + 2;

        int meterWidth = wide ? (innerWidth - 10) / 2 : innerWidth;
        PanelTheme.meter(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.progress"),
                context.progressValue(),
                innerX,
                cursor,
                meterWidth,
                context.progressFraction(),
                PanelTheme.JADE,
                PanelTheme.JADE_DARK);
        int stabilityX = wide ? innerX + meterWidth + 10 : innerX;
        int stabilityY = wide ? cursor : cursor + PanelTheme.METER_HEIGHT + 3;
        PanelTheme.meter(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.stability"),
                context.stabilityValue(),
                stabilityX,
                stabilityY,
                wide ? innerWidth - meterWidth - 10 : innerWidth,
                context.stabilityFraction(),
                PanelTheme.GOLD_BRIGHT,
                PanelTheme.GOLD_DIM);
    }

    private void drawNormalMode(GuiGraphics graphics, PanelContext context, int x, int y, int width, int height) {
        Font font = context.font();
        CultivationProfile profile = context.profile();
        PanelTheme.card(graphics, font, x, y, width, height,
                Component.translatable("screen.myvillage.cultivation.button.normal"));
        int innerX = x + PAD;
        int innerWidth = width - PAD * 2;
        int cursor = y + PanelTheme.CARD_TITLE_HEIGHT + 1;
        Optional<RealmStageDefinition> stage = context.currentStage();
        String unavailable = context.unavailableText();
        PanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.normal_rate"),
                context.text("screen.myvillage.cultivation.rate_per_ten_ticks", profile.spiritualAffinity()),
                innerX,
                cursor,
                innerWidth,
                PanelTheme.TEXT);
        cursor += ROW;
        PanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.stability_gain"),
                stabilityGainValue(context, stage, unavailable),
                innerX,
                cursor,
                innerWidth,
                PanelTheme.TEXT);
        cursor += ROW;
        PanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.basic_breathing_mastery"),
                basicBreathingMastery(profile, unavailable),
                innerX,
                cursor,
                innerWidth,
                PanelTheme.TEXT);
    }

    private void drawSpiritMode(GuiGraphics graphics, PanelContext context, int x, int y, int width, int height) {
        Font font = context.font();
        PanelTheme.card(graphics, font, x, y, width, height,
                Component.translatable("screen.myvillage.cultivation.button.spirit"));
        int innerX = x + PAD;
        int innerWidth = width - PAD * 2;
        int cursor = y + PanelTheme.CARD_TITLE_HEIGHT + 1;
        Optional<RealmStageDefinition> stage = context.currentStage();
        String unavailable = context.unavailableText();
        PanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.spirit_rate"),
                context.text("screen.myvillage.cultivation.rate_per_ten_ticks", SPIRIT_PROGRESS_PER_BATCH),
                innerX,
                cursor,
                innerWidth,
                PanelTheme.TEXT);
        cursor += ROW;
        PanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.spirit_cost"),
                spiritCostValue(context, stage, unavailable),
                innerX,
                cursor,
                innerWidth,
                PanelTheme.TEXT);
        cursor += ROW;
        PanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.spirit_inventory"),
                spiritStoneInventory(context, unavailable),
                innerX,
                cursor,
                innerWidth,
                PanelTheme.TEXT);
    }

    private void drawAdvancement(
            GuiGraphics graphics, PanelContext context, int x, int y, int width, int height, boolean wide) {
        Font font = context.font();
        CultivationProfile profile = context.profile();
        PanelTheme.card(graphics, font, x, y, width, height,
                Component.translatable("screen.myvillage.cultivation.advancement"));
        int innerX = x + PAD;
        int innerWidth = width - PAD * 2;
        int cursor = y + PanelTheme.CARD_TITLE_HEIGHT + 1;
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
            graphics.drawString(
                    font, PanelTheme.fit(font, message, innerWidth), innerX, cursor, PanelTheme.MUTED, false);
            return;
        }

        String kind = context.text(
                "screen.myvillage.cultivation.advancement_kind." + advancement.kind().serializedName());
        int chipX = x + width - PAD - PanelTheme.chipWidth(font, kind);
        graphics.fill(chipX - 3, y + 1, x + width - 1, y + PanelTheme.CARD_TITLE_HEIGHT, PanelTheme.CARD);
        PanelTheme.chip(graphics, font, kind, chipX, y + 1, PanelTheme.GOLD);

        String target = context.text(
                "screen.myvillage.cultivation.advancement_target",
                context.stageName(profile.realmId(), profile.stageId()),
                context.stageName(advancement.targetRealm(), advancement.targetStage()));
        String loss = advancement.interruptionStabilityLoss() > 0
                ? context.text(
                        "screen.myvillage.cultivation.advancement_interruption_value",
                        advancement.interruptionStabilityLoss())
                : "";
        int lossWidth = loss.isEmpty() ? 0 : font.width(loss) + 8;
        graphics.drawString(
                font,
                PanelTheme.fit(font, target, Math.max(1, innerWidth - lossWidth)),
                innerX,
                cursor,
                PanelTheme.TEXT,
                false);
        if (!loss.isEmpty() && lossWidth < innerWidth / 2) {
            graphics.drawString(
                    font, loss, innerX + innerWidth - font.width(loss), cursor, PanelTheme.AMBER, false);
        }
        cursor += ROW + 1;

        int columnWidth = wide ? (innerWidth - 12) / 2 : innerWidth;
        int secondX = wide ? innerX + columnWidth + 12 : innerX;
        int step = wide ? 0 : ROW;
        PanelTheme.condition(
                graphics,
                font,
                context.progressFull(),
                context.text("screen.myvillage.cultivation.condition_progress"),
                context.progressValue(),
                innerX,
                cursor,
                columnWidth);
        PanelTheme.condition(
                graphics,
                font,
                profile.stability() >= advancement.requiredStability(),
                context.text("screen.myvillage.cultivation.condition_stability"),
                profile.stability() + " / " + advancement.requiredStability(),
                secondX,
                cursor + step,
                wide ? innerWidth - columnWidth - 12 : innerWidth);
        cursor += ROW + step;
        PanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.advancement_duration"),
                context.text("screen.myvillage.cultivation.ticks_value", advancement.durationTicks()),
                innerX + 8,
                cursor,
                columnWidth - 8,
                PanelTheme.TEXT);
        PanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.advancement_cost"),
                Integer.toString(advancement.stabilityCost()),
                secondX + 8,
                cursor + step,
                (wide ? innerWidth - columnWidth - 12 : innerWidth) - 8,
                PanelTheme.TEXT);
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
