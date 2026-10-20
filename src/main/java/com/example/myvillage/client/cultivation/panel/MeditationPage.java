package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.cultivation.ClientCultivationIntentSender;
import com.example.myvillage.client.cultivation.ClientCultivationKeyMappings;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.meditation.BasicBreathingSettlement;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.cultivation.network.MeditationIntentAction;
import com.example.myvillage.item.SpiritStoneTags;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.function.Consumer;

/** Server-owned meditation controls with the approved per-realm ink presentation. */
public final class MeditationPage extends PanelPage {
    private static final int GAP = 4;
    private static final int BUTTON_HEIGHT = 18;

    static final int SPIRIT_PROGRESS_PER_BATCH = BasicBreathingSettlement.SPIRIT_PROGRESS_PER_SETTLEMENT;

    private final RealmInkPage inkPage = new RealmInkPage();

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
        button.setInk(true);
        widgets.accept(button);
        return button;
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) inkPage.hidden();
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
        boolean eligible = context.profile() != null && context.profile().awakened()
                && context.profile().learnedTechniques().containsKey(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                && context.currentStage().flatMap(RealmStageDefinition::cultivationCap).isPresent();
        normalButton.active = visible && synchronizedStatus && !activeSession && eligible;
        spiritButton.active = normalButton.active;
        advancementButton.active = normalButton.active && context.progressFull()
                && context.currentStage().flatMap(RealmStageDefinition::advancement)
                .map(a -> context.profile().stability() >= a.requiredStability()).orElse(false);
        stopButton.active = visible && activeSession;
    }

    @Override
    public void pointer(int mouseX, int mouseY) { inkPage.pointer(mouseX, mouseY); }

    @Override
    public void renderOverlay(GuiGraphics graphics, PanelContext context, int mouseX, int mouseY) {
        inkPage.renderOverlay(graphics, context, mouseX, mouseY);
    }

    @Override
    public int render(GuiGraphics graphics, PanelContext context, int x, int y, int width, int viewportHeight) {
        return inkPage.render(graphics, context, x, y, width, viewportHeight);
    }
    static java.util.List<String> gainDetails(PanelContext c) {
        return java.util.List.of(
                c.text("screen.myvillage.cultivation.normal_rate") + " "
                        + c.text("screen.myvillage.cultivation.rate_per_ten_ticks", c.profile().spiritualAffinity()),
                c.text("screen.myvillage.cultivation.spirit_rate") + " "
                        + c.text("screen.myvillage.cultivation.rate_per_ten_ticks", SPIRIT_PROGRESS_PER_BATCH),
                c.text("screen.myvillage.cultivation.spirit_cost") + " "
                        + spiritCostValue(c, c.currentStage(), c.unavailableText()),
                c.text("screen.myvillage.cultivation.stability_gain") + " "
                        + stabilityGainValue(c, c.currentStage(), c.unavailableText()),
                c.text("screen.myvillage.cultivation.basic_breathing_mastery") + " "
                        + basicBreathingMastery(c.profile(), c.unavailableText()),
                c.text("screen.myvillage.cultivation.spirit_inventory") + " "
                        + spiritStoneInventory(c, c.unavailableText()));
    }

    static String stabilityGainValue(
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

    static String spiritCostValue(
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

    static String basicBreathingMastery(CultivationProfile profile, String unavailable) {
        TechniqueProgress progress = profile.learnedTechniques()
                .get(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID);
        return progress == null ? unavailable : Long.toString(progress.masteryPoints());
    }

    static String spiritStoneInventory(PanelContext context, String unavailable) {
        if (context.minecraft() == null || context.minecraft().player == null) {
            return unavailable;
        }
        var count = com.example.myvillage.spiritstone.SpiritStoneSources.slots(context.minecraft().player, false).stream()
                .map(slot -> com.example.myvillage.spiritstone.SpiritStoneStorage.count(
                        slot.read().get(), stack -> stack.is(SpiritStoneTags.LOW_GRADE_SPIRIT_STONE))).reduce(java.math.BigInteger.ZERO, java.math.BigInteger::add);
        return count.toString();
    }
}
