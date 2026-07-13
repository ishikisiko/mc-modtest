package com.example.myvillage.client.cultivation;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.AdvancementDefinition;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.data.SpiritualElementDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.network.MeditationIntentAction;
import com.example.myvillage.cultivation.network.CultivationTimeSnapshotPayload;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CultivationProfileScreen extends Screen {
    private static final int BACKDROP = 0xA8000000;
    private static final int PANEL = 0xF31B1F22;
    private static final int PANEL_HEADER = 0xFF262C2C;
    private static final int BORDER = 0xFF8C784C;
    private static final int DIVIDER = 0xFF4A504D;
    private static final int TEXT = 0xFFF1E9D2;
    private static final int MUTED = 0xFFAEB6AF;
    private static final int ACCENT = 0xFFD6B965;
    private static final int BAR_BACKGROUND = 0xFF3C4240;
    private static final int STABILITY = 0xFF5AA58A;
    private static final int FALLBACK_ELEMENT = 0xFF7C8B88;
    private static final int TAB_HEIGHT = 20;
    private static final int ACTION_HEIGHT = 20;
    private static final int WIDGET_GAP = 4;
    private static final int SPIRIT_PROGRESS_PER_BATCH = 50;

    private View view = View.PROFILE;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int techniqueScroll;
    private int techniqueListX;
    private int techniqueListY;
    private int techniqueListWidth;
    private int techniqueListHeight;
    private int techniqueContentHeight;
    private Button profileTab;
    private Button meditationTab;
    private Button normalButton;
    private Button spiritButton;
    private Button stopButton;
    private Button advancementButton;

    private enum View {
        PROFILE,
        MEDITATION
    }

    public CultivationProfileScreen() {
        super(Component.translatable("screen.myvillage.cultivation.title"));
    }

    @Override
    protected void init() {
        updatePanelBounds();
        int innerLeft = panelLeft + 12;
        int innerWidth = panelWidth - 24;
        int tabWidth = Math.max(1, (innerWidth - WIDGET_GAP) / 2);
        int secondTabWidth = Math.max(1, innerWidth - WIDGET_GAP - tabWidth);
        int tabY = panelTop + 48;

        profileTab = addRenderableWidget(Button.builder(
                        Component.translatable("screen.myvillage.cultivation.tab.profile"),
                        button -> setView(View.PROFILE))
                .bounds(innerLeft, tabY, tabWidth, TAB_HEIGHT)
                .build());
        meditationTab = addRenderableWidget(Button.builder(
                        Component.translatable("screen.myvillage.cultivation.tab.meditation"),
                        button -> setView(View.MEDITATION))
                .bounds(innerLeft + tabWidth + WIDGET_GAP, tabY, secondTabWidth, TAB_HEIGHT)
                .build());

        int actionWidth = Math.max(1, (innerWidth - WIDGET_GAP) / 2);
        int secondActionWidth = Math.max(1, innerWidth - WIDGET_GAP - actionWidth);
        int secondRowY = panelTop + panelHeight - ACTION_HEIGHT - 8;
        int firstRowY = secondRowY - ACTION_HEIGHT - WIDGET_GAP;
        normalButton = actionButton(
                "screen.myvillage.cultivation.button.normal",
                MeditationIntentAction.START_NORMAL,
                innerLeft,
                firstRowY,
                actionWidth);
        spiritButton = actionButton(
                "screen.myvillage.cultivation.button.spirit",
                MeditationIntentAction.START_SPIRIT,
                innerLeft + actionWidth + WIDGET_GAP,
                firstRowY,
                secondActionWidth);
        stopButton = actionButton(
                "screen.myvillage.cultivation.button.stop",
                MeditationIntentAction.STOP,
                innerLeft,
                secondRowY,
                actionWidth);
        advancementButton = actionButton(
                "screen.myvillage.cultivation.button.advancement",
                MeditationIntentAction.START_BREAKTHROUGH,
                innerLeft + actionWidth + WIDGET_GAP,
                secondRowY,
                secondActionWidth);
        refreshButtons();
    }

    private Button actionButton(
            String translationKey,
            MeditationIntentAction action,
            int x,
            int y,
            int buttonWidth) {
        return addRenderableWidget(Button.builder(
                        Component.translatable(translationKey),
                        button -> ClientCultivationIntentSender.send(action))
                .bounds(x, y, buttonWidth, ACTION_HEIGHT)
                .build());
    }

    private void updatePanelBounds() {
        panelWidth = Math.max(1, Math.min(520, width - 20));
        panelHeight = Math.max(1, Math.min(300, height - 20));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
    }

    private void setView(View newView) {
        view = newView;
        refreshButtons();
    }

    @Override
    public void tick() {
        refreshButtons();
    }

    private void refreshButtons() {
        if (profileTab == null || meditationTab == null) {
            return;
        }
        profileTab.active = view != View.PROFILE;
        meditationTab.active = view != View.MEDITATION;

        boolean meditationVisible = view == View.MEDITATION;
        normalButton.visible = meditationVisible;
        spiritButton.visible = meditationVisible;
        stopButton.visible = meditationVisible;
        advancementButton.visible = meditationVisible;

        MeditationStatus status = ClientCultivationState.meditation().orElse(null);
        boolean synchronizedStatus = status != null;
        boolean activeSession = synchronizedStatus && status.state().active();
        normalButton.active = meditationVisible && synchronizedStatus && !activeSession;
        spiritButton.active = meditationVisible && synchronizedStatus && !activeSession;
        advancementButton.active = meditationVisible && synchronizedStatus && !activeSession;
        stopButton.active = meditationVisible && activeSession;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKDROP);
        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;

        graphics.fill(panelLeft, panelTop, right, bottom, PANEL);
        graphics.renderOutline(panelLeft, panelTop, panelWidth, panelHeight, BORDER);
        graphics.fill(panelLeft + 1, panelTop + 1, right - 1, panelTop + 45, PANEL_HEADER);
        graphics.hLine(panelLeft + 1, right - 2, panelTop + 45, DIVIDER);
        graphics.hLine(panelLeft + 12, right - 13, panelTop + 71, DIVIDER);

        CultivationProfile profile = ClientCultivationState.latest().orElse(null);
        drawHeader(graphics, profile, panelLeft, panelTop, panelWidth);
        if (profile == null) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("screen.myvillage.cultivation.no_snapshot"),
                    panelLeft + panelWidth / 2,
                    panelTop + panelHeight / 2,
                    MUTED);
        } else {
            RegistryAccess registries = minecraft != null && minecraft.level != null
                    ? minecraft.level.registryAccess()
                    : RegistryAccess.EMPTY;
            int innerLeft = panelLeft + 12;
            int innerWidth = panelWidth - 24;
            int contentTop = panelTop + 77;
            if (view == View.PROFILE) {
                int gap = 14;
                int columnWidth = (innerWidth - gap) / 2;
                int contentBottom = bottom - 17;
                drawCultivationColumn(
                        graphics, profile, registries, innerLeft, contentTop, columnWidth, contentBottom);
                int dividerX = innerLeft + columnWidth + gap / 2;
                graphics.vLine(dividerX, contentTop, contentBottom, DIVIDER);
                drawTechniqueColumn(
                        graphics,
                        profile,
                        registries,
                        dividerX + gap / 2,
                        contentTop,
                        columnWidth,
                        contentBottom,
                        mouseX,
                        mouseY);

                String schema = Component.translatable(
                        "screen.myvillage.cultivation.schema", profile.schemaVersion()).getString();
                graphics.drawString(font, schema, right - 10 - font.width(schema), bottom - 12, MUTED, false);
            } else {
                drawMeditationView(graphics, profile, registries, innerLeft, contentTop, innerWidth);
            }
        }

        refreshButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The screen renders a stable, sharp backdrop before Screen renders its widgets.
    }

    private void drawHeader(
            GuiGraphics graphics,
            CultivationProfile profile,
            int left,
            int top,
            int panelWidth) {
        int faceX = left + 13;
        int faceY = top + 13;
        if (minecraft != null && minecraft.player != null) {
            PlayerFaceRenderer.draw(graphics, minecraft.player.getSkin(), faceX, faceY, 32);
        } else {
            graphics.fill(faceX, faceY, faceX + 32, faceY + 32, DIVIDER);
        }

        int textX = faceX + 42;
        int textWidth = panelWidth - 68;
        String playerName = minecraft != null && minecraft.player != null
                ? minecraft.player.getDisplayName().getString()
                : Component.translatable("screen.myvillage.cultivation.unknown_player").getString();
        graphics.drawString(font, fit(playerName, textWidth), textX, top + 12, TEXT, false);

        String status;
        if (profile == null || minecraft == null || minecraft.level == null) {
            status = Component.translatable("screen.myvillage.cultivation.waiting").getString();
        } else {
            RegistryAccess registries = minecraft.level.registryAccess();
            status = displayRealm(profile, registries).getString()
                    + "  /  "
                    + displayStage(profile, registries).getString();
        }
        graphics.drawString(font, fit(status, textWidth), textX, top + 30, ACCENT, false);
    }

    private void drawCultivationColumn(
            GuiGraphics graphics,
            CultivationProfile profile,
            RegistryAccess registries,
            int x,
            int top,
            int columnWidth,
            int bottom) {
        drawPair(graphics, "screen.myvillage.cultivation.progress", progressValue(profile, registries),
                x, top, columnWidth);
        drawPair(graphics, "screen.myvillage.cultivation.power", Long.toString(profile.currentSpiritualPower()),
                x, top + 10, columnWidth);
        drawPair(graphics, "screen.myvillage.cultivation.stability", stabilityValue(profile, registries),
                x, top + 20, columnWidth);

        int barY = top + 30;
        graphics.fill(x, barY, x + columnWidth, barY + 4, BAR_BACKGROUND);
        int fillWidth = stabilityFillWidth(profile, registries, columnWidth);
        graphics.fill(x, barY, x + fillWidth, barY + 4, STABILITY);

        drawPair(
                graphics,
                "screen.myvillage.cultivation.spiritual_affinity",
                Integer.toString(profile.spiritualAffinity()),
                x,
                top + 37,
                columnWidth);

        CultivationTimeSnapshotPayload time = ClientCultivationState.time().orElse(null);
        if (time == null) {
            drawPair(
                    graphics,
                    "screen.myvillage.cultivation.calendar",
                    Component.translatable("screen.myvillage.cultivation.time_waiting").getString(),
                    x,
                    top + 47,
                    columnWidth);
        } else {
            drawPair(
                    graphics,
                    "screen.myvillage.cultivation.calendar",
                    calendarValue(time),
                    x,
                    top + 47,
                    columnWidth);
            drawPair(
                    graphics,
                    "screen.myvillage.cultivation.lifespan_consumed",
                    Component.translatable(
                            "screen.myvillage.cultivation.years_value",
                            yearsFloor(time.lifespanConsumedTicks(), time)).getString(),
                    x,
                    top + 57,
                    columnWidth);
            drawPair(
                    graphics,
                    "screen.myvillage.cultivation.lifespan_remaining",
                    remainingValue(time),
                    x,
                    top + 67,
                    columnWidth);
        }

        MeditationStatus meditation = ClientCultivationState.meditation().orElse(null);
        drawPair(
                graphics,
                "screen.myvillage.cultivation.session",
                meditation == null
                        ? Component.translatable("screen.myvillage.cultivation.time_waiting").getString()
                        : Component.translatable(
                                "screen.myvillage.cultivation.session."
                                        + meditation.state().name().toLowerCase(Locale.ROOT)).getString(),
                x,
                top + 77,
                columnWidth);

        int rootTitleY = top + 91;
        graphics.drawString(
                font,
                Component.translatable("screen.myvillage.cultivation.spiritual_root"),
                x,
                rootTitleY,
                ACCENT,
                false);
        Optional<SpiritualRoot> root = profile.spiritualRoot();
        if (root.isEmpty()) {
            graphics.drawString(
                    font,
                    Component.translatable("screen.myvillage.cultivation.unawakened"),
                    x,
                    rootTitleY + 12,
                    MUTED,
                    false);
            return;
        }

        List<Map.Entry<ResourceLocation, Integer>> affinities = new ArrayList<>(
                root.get().affinitiesBasisPoints().entrySet());
        affinities.sort(Comparator
                .comparingInt((Map.Entry<ResourceLocation, Integer> entry) -> elementSortOrder(registries, entry.getKey()))
                .thenComparing(entry -> entry.getKey().toString()));
        int rowY = rootTitleY + 12;
        int visibleRows = Math.max(1, (bottom - rowY) / 9);
        int rendered = Math.min(visibleRows, affinities.size());
        for (int index = 0; index < rendered; index++) {
            Map.Entry<ResourceLocation, Integer> affinity = affinities.get(index);
            String name = displayElement(registries, affinity.getKey()).getString();
            String percent = String.format(Locale.ROOT, "%.1f%%", affinity.getValue() / 100.0D);
            int color = elementColor(registries, affinity.getKey());
            graphics.fill(x, rowY + index * 9 + 2, x + 5, rowY + index * 9 + 7, color);
            graphics.drawString(font, fit(name, columnWidth - 49), x + 9, rowY + index * 9, TEXT, false);
            graphics.drawString(
                    font,
                    percent,
                    x + columnWidth - font.width(percent),
                    rowY + index * 9,
                    MUTED,
                    false);
        }
    }

    private void drawMeditationView(
            GuiGraphics graphics,
            CultivationProfile profile,
            RegistryAccess registries,
            int x,
            int top,
            int contentWidth) {
        MeditationStatus meditation = ClientCultivationState.meditation().orElse(null);
        drawPair(
                graphics,
                "screen.myvillage.cultivation.session",
                meditation == null
                        ? Component.translatable("screen.myvillage.cultivation.time_waiting").getString()
                        : Component.translatable(
                                "screen.myvillage.cultivation.session."
                                        + meditation.state().name().toLowerCase(Locale.ROOT)).getString(),
                x,
                top,
                contentWidth);
        drawPair(
                graphics,
                "screen.myvillage.cultivation.progress",
                progressValue(profile, registries),
                x,
                top + 13,
                contentWidth);

        int progressBarY = top + 26;
        graphics.fill(x, progressBarY, x + contentWidth, progressBarY + 5, BAR_BACKGROUND);
        int progressFill = progressFillWidth(profile, registries, contentWidth);
        graphics.fill(x, progressBarY, x + progressFill, progressBarY + 5, ACCENT);

        int pairGap = 14;
        int columnWidth = (contentWidth - pairGap) / 2;
        int rightX = x + columnWidth + pairGap;
        Optional<RealmStageDefinition> stage = currentStage(profile, registries);
        String unavailable = Component.translatable("screen.myvillage.cultivation.unavailable").getString();

        drawPair(
                graphics,
                "screen.myvillage.cultivation.spiritual_affinity",
                Integer.toString(profile.spiritualAffinity()),
                x,
                top + 36,
                columnWidth);
        drawPair(
                graphics,
                "screen.myvillage.cultivation.stability",
                stabilityValue(profile, registries),
                rightX,
                top + 36,
                columnWidth);
        drawPair(
                graphics,
                "screen.myvillage.cultivation.normal_rate",
                Component.translatable(
                        "screen.myvillage.cultivation.rate_per_ten_ticks",
                        profile.spiritualAffinity()).getString(),
                x,
                top + 49,
                columnWidth);
        drawPair(
                graphics,
                "screen.myvillage.cultivation.basic_breathing_mastery",
                basicBreathingMastery(profile, unavailable),
                rightX,
                top + 49,
                columnWidth);
        drawPair(
                graphics,
                "screen.myvillage.cultivation.spirit_rate",
                Component.translatable(
                        "screen.myvillage.cultivation.rate_per_ten_ticks",
                        SPIRIT_PROGRESS_PER_BATCH).getString(),
                x,
                top + 62,
                columnWidth);
        drawPair(
                graphics,
                "screen.myvillage.cultivation.spirit_cost",
                spiritCostValue(profile, stage, unavailable),
                rightX,
                top + 62,
                columnWidth);
        drawPair(
                graphics,
                "screen.myvillage.cultivation.spirit_inventory",
                spiritStoneInventory(unavailable),
                x,
                top + 75,
                columnWidth);
        drawPair(
                graphics,
                "screen.myvillage.cultivation.stability_gain",
                stabilityGainValue(profile, stage, unavailable),
                rightX,
                top + 75,
                columnWidth);
    }

    private int progressFillWidth(
            CultivationProfile profile,
            RegistryAccess registries,
            int width) {
        return currentStage(profile, registries)
                .flatMap(RealmStageDefinition::cultivationCap)
                .map(cap -> (int) Math.round(width * Math.min(
                        1.0D,
                        profile.cultivationProgress() / (double) cap)))
                .orElse(0);
    }

    private String stabilityValue(CultivationProfile profile, RegistryAccess registries) {
        String unavailable = Component.translatable(
                "screen.myvillage.cultivation.unavailable").getString();
        return currentStage(profile, registries)
                .flatMap(RealmStageDefinition::stabilityCap)
                .map(cap -> profile.stability() + " / " + cap)
                .orElse(profile.stability() + " / " + unavailable);
    }

    private int stabilityFillWidth(
            CultivationProfile profile,
            RegistryAccess registries,
            int width) {
        return currentStage(profile, registries)
                .flatMap(RealmStageDefinition::stabilityCap)
                .map(cap -> (int) Math.round(width * Math.min(
                        1.0D,
                        profile.stability() / (double) cap)))
                .orElse(0);
    }

    private String stabilityGainValue(
            CultivationProfile profile,
            Optional<RealmStageDefinition> stage,
            String unavailable) {
        if (stage.isEmpty()
                || stage.orElseThrow().cultivationCap().isEmpty()
                || stage.orElseThrow().stabilityCap().isEmpty()) {
            return unavailable;
        }
        RealmStageDefinition definition = stage.orElseThrow();
        if (profile.cultivationProgress() < definition.cultivationCap().orElseThrow()) {
            return Component.translatable(
                    "screen.myvillage.cultivation.stability_locked").getString();
        }
        if (profile.stability() >= definition.stabilityCap().orElseThrow()) {
            return Component.translatable(
                    "screen.myvillage.cultivation.stability_capped").getString();
        }
        return Component.translatable(
                "screen.myvillage.cultivation.rate_per_ten_ticks",
                profile.spiritualAffinity()).getString();
    }

    private String spiritCostValue(
            CultivationProfile profile,
            Optional<RealmStageDefinition> stage,
            String unavailable) {
        if (stage.isEmpty()) {
            return unavailable;
        }
        RealmStageDefinition definition = stage.orElseThrow();
        if (definition.cultivationCap().isPresent()
                && profile.cultivationProgress() >= definition.cultivationCap().orElseThrow()) {
            return Component.translatable(
                    "screen.myvillage.cultivation.stability_no_stone_cost").getString();
        }
        return definition.spiritStoneCost()
                .map(cost -> Component.translatable(
                        "screen.myvillage.cultivation.cost_per_ten_ticks", cost).getString())
                .orElse(unavailable);
    }

    private String basicBreathingMastery(CultivationProfile profile, String unavailable) {
        TechniqueProgress progress = profile.learnedTechniques()
                .get(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID);
        return progress == null ? unavailable : Long.toString(progress.masteryPoints());
    }

    private String spiritStoneInventory(String unavailable) {
        if (minecraft == null || minecraft.player == null) {
            return unavailable;
        }
        Inventory inventory = minecraft.player.getInventory();
        long count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.LOW_GRADE_SPIRIT_STONE.get())) {
                count = Math.min(Integer.MAX_VALUE, count + stack.getCount());
            }
        }
        return Long.toString(count);
    }

    private String calendarValue(CultivationTimeSnapshotPayload time) {
        long elapsedDays = time.elapsedCalendarTicks() / time.ticksPerDay();
        long year = elapsedDays / time.daysPerYear();
        long oneBasedYear = year == Long.MAX_VALUE ? Long.MAX_VALUE : year + 1;
        long day = elapsedDays % time.daysPerYear() + 1;
        return Component.translatable(
                "screen.myvillage.cultivation.calendar_value", oneBasedYear, day).getString();
    }

    private String remainingValue(CultivationTimeSnapshotPayload time) {
        if (!time.lifespanAvailable()) {
            return Component.translatable("screen.myvillage.cultivation.unavailable").getString();
        }
        if (time.exhausted()) {
            return Component.translatable(
                    "screen.myvillage.cultivation.lifespan_exhausted", time.maximumLifespanYears()).getString();
        }
        long remainingYears = yearsCeil(time.remainingLifespanTicks(), time);
        return Component.translatable(
                "screen.myvillage.cultivation.lifespan_fraction",
                remainingYears,
                time.maximumLifespanYears()).getString();
    }

    private long yearsFloor(long ticks, CultivationTimeSnapshotPayload time) {
        return ticks / ticksPerYear(time);
    }

    private long yearsCeil(long ticks, CultivationTimeSnapshotPayload time) {
        long ticksPerYear = ticksPerYear(time);
        long whole = ticks / ticksPerYear;
        return ticks % ticksPerYear == 0 || whole == Long.MAX_VALUE ? whole : whole + 1;
    }

    private long ticksPerYear(CultivationTimeSnapshotPayload time) {
        if (time.ticksPerDay() > Long.MAX_VALUE / time.daysPerYear()) {
            return Long.MAX_VALUE;
        }
        return time.ticksPerDay() * time.daysPerYear();
    }

    private void drawTechniqueColumn(
            GuiGraphics graphics,
            CultivationProfile profile,
            RegistryAccess registries,
            int x,
            int top,
            int columnWidth,
            int bottom,
            int mouseX,
            int mouseY) {
        drawAdvancementSummary(graphics, profile, registries, x, top, columnWidth);
        int techniqueTop = top + 59;
        graphics.drawString(
                font,
                Component.translatable("screen.myvillage.cultivation.techniques"),
                x,
                techniqueTop,
                ACCENT,
                false);
        if (profile.learnedTechniques().isEmpty()) {
            graphics.drawString(
                    font,
                    Component.translatable("screen.myvillage.cultivation.none"),
                    x,
                    techniqueTop + 15,
                    MUTED,
                    false);
            techniqueListX = x;
            techniqueListY = techniqueTop + 14;
            techniqueListWidth = columnWidth;
            techniqueListHeight = Math.max(1, bottom - techniqueListY);
            techniqueContentHeight = 0;
            return;
        }

        techniqueListX = x;
        techniqueListY = techniqueTop + 14;
        techniqueListWidth = columnWidth;
        techniqueListHeight = Math.max(1, bottom - techniqueListY);
        techniqueContentHeight = profile.learnedTechniques().size() * 29;
        int maxScroll = Math.max(0, techniqueContentHeight - techniqueListHeight);
        techniqueScroll = Math.min(techniqueScroll, maxScroll);

        graphics.enableScissor(
                techniqueListX,
                techniqueListY,
                techniqueListX + techniqueListWidth,
                techniqueListY + techniqueListHeight);
        int rowY = techniqueListY - techniqueScroll;
        for (Map.Entry<ResourceLocation, TechniqueProgress> entry : profile.learnedTechniques().entrySet()) {
            TechniqueDefinition definition = techniqueDefinition(registries, entry.getKey()).orElse(null);
            Component name = definition == null
                    ? unavailable(entry.getKey())
                    : Component.translatable(definition.translationKey());
            graphics.drawString(font, fit(name.getString(), columnWidth - 5), x, rowY + 2, TEXT, false);

            String detail = techniqueDetail(definition, entry.getValue());
            graphics.drawString(font, fit(detail, columnWidth - 5), x, rowY + 15, MUTED, false);
            graphics.hLine(x, x + columnWidth - 5, rowY + 27, DIVIDER);
            rowY += 29;
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = x + columnWidth - 2;
            int thumbHeight = Math.max(12, techniqueListHeight * techniqueListHeight / techniqueContentHeight);
            int thumbY = techniqueListY
                    + (techniqueListHeight - thumbHeight) * techniqueScroll / maxScroll;
            graphics.fill(trackX, techniqueListY, trackX + 2, techniqueListY + techniqueListHeight, BAR_BACKGROUND);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, ACCENT);
        }
    }

    private void drawAdvancementSummary(
            GuiGraphics graphics,
            CultivationProfile profile,
            RegistryAccess registries,
            int x,
            int top,
            int columnWidth) {
        graphics.drawString(
                font,
                Component.translatable("screen.myvillage.cultivation.advancement"),
                x,
                top,
                ACCENT,
                false);
        RealmStageDefinition stage = currentStage(profile, registries).orElse(null);
        if (stage == null) {
            graphics.drawString(
                    font,
                    Component.translatable("screen.myvillage.cultivation.unavailable"),
                    x,
                    top + 15,
                    MUTED,
                    false);
            graphics.hLine(x, x + columnWidth - 5, top + 51, DIVIDER);
            return;
        }

        AdvancementDefinition advancement = stage.advancement().orElse(null);
        if (advancement == null) {
            String value = stage.id().equals(ModCultivationRegistries.QI_REFINING_4_STAGE_ID)
                    ? Component.translatable(
                            "screen.myvillage.cultivation.advancement_release_ceiling").getString()
                    : Component.translatable(
                            "screen.myvillage.cultivation.advancement_unavailable").getString();
            graphics.drawString(font, fit(value, columnWidth - 5), x, top + 15, MUTED, false);
            graphics.hLine(x, x + columnWidth - 5, top + 51, DIVIDER);
            return;
        }

        String kind = Component.translatable(
                "screen.myvillage.cultivation.advancement_kind."
                        + advancement.kind().serializedName()).getString();
        drawPair(
                graphics,
                "screen.myvillage.cultivation.advancement_rule",
                Component.translatable(
                        "screen.myvillage.cultivation.advancement_rule_value",
                        kind,
                        advancement.durationTicks()).getString(),
                x,
                top + 14,
                columnWidth);
        drawPair(
                graphics,
                "screen.myvillage.cultivation.advancement_stability",
                Component.translatable(
                "screen.myvillage.cultivation.advancement_stability_value",
                        advancement.requiredStability()).getString(),
                x,
                top + 27,
                columnWidth);

        MeditationStatus status = ClientCultivationState.meditation().orElse(null);
        String runtime = status != null && status.state().advancing()
                ? Component.translatable(
                        "screen.myvillage.cultivation.advancement_runtime_value",
                        status.advancementTicksRemaining(),
                        status.advancementDurationTicks()).getString()
                : Component.translatable(
                        "screen.myvillage.cultivation.advancement_inactive").getString();
        drawPair(
                graphics,
                "screen.myvillage.cultivation.advancement_runtime",
                runtime,
                x,
                top + 40,
                columnWidth);
        graphics.hLine(x, x + columnWidth - 5, top + 51, DIVIDER);
    }

    private String progressValue(CultivationProfile profile, RegistryAccess registries) {
        RealmStageDefinition stage = currentStage(profile, registries).orElse(null);
        if (stage == null) {
            return Component.translatable(
                    "screen.myvillage.cultivation.progress_unavailable",
                    profile.cultivationProgress()).getString();
        }
        if (stage.cultivationCap().isPresent()) {
            return Component.translatable(
                    "screen.myvillage.cultivation.progress_capped",
                    profile.cultivationProgress(),
                    stage.cultivationCap().orElseThrow()).getString();
        }
        String suffix = stage.id().equals(ModCultivationRegistries.QI_REFINING_4_STAGE_ID)
                ? Component.translatable(
                        "screen.myvillage.cultivation.progress_release_ceiling").getString()
                : Component.translatable(
                        "screen.myvillage.cultivation.progress_unsupported").getString();
        return profile.cultivationProgress() + " / " + suffix;
    }

    private void drawPair(
            GuiGraphics graphics,
            String labelKey,
            String value,
            int x,
            int y,
            int width) {
        String label = Component.translatable(labelKey).getString();
        int gap = 4;
        int labelWidth = Math.min(font.width(label), Math.max(1, (width - gap) * 2 / 5));
        int valueX = x + labelWidth + gap;
        graphics.drawString(font, fit(label, labelWidth), x, y, MUTED, false);
        graphics.drawString(font, fit(value, Math.max(1, width - labelWidth - gap)), valueX, y, TEXT, false);
    }

    private String techniqueDetail(TechniqueDefinition definition, TechniqueProgress progress) {
        String mastery = Component.translatable(
                "screen.myvillage.cultivation.mastery", progress.masteryPoints()).getString();
        if (definition == null) {
            return mastery;
        }
        String category = Component.translatable(
                "screen.myvillage.cultivation.category." + definition.category().serializedName()).getString();
        String grade = Component.translatable(
                "screen.myvillage.cultivation.grade", definition.grade()).getString();
        return category + "  |  " + grade + "  |  " + mastery;
    }

    private Component displayRealm(CultivationProfile profile, RegistryAccess registries) {
        return registries.registry(ModCultivationRegistries.REALMS)
                .flatMap(registry -> registry.getOptional(profile.realmId()))
                .<Component>map(definition -> Component.translatable(definition.translationKey()))
                .orElseGet(() -> unavailable(profile.realmId()));
    }

    private Component displayStage(CultivationProfile profile, RegistryAccess registries) {
        RealmStageDefinition stage = currentStage(profile, registries).orElse(null);
        return stage == null
                ? unavailable(profile.stageId())
                : Component.translatable(stage.translationKey());
    }

    private Optional<RealmStageDefinition> currentStage(
            CultivationProfile profile, RegistryAccess registries) {
        return registries.registry(ModCultivationRegistries.REALMS)
                .flatMap(registry -> registry.getOptional(profile.realmId()))
                .flatMap(realm -> realm.stages().stream()
                        .filter(candidate -> candidate.id().equals(profile.stageId()))
                        .findFirst());
    }

    private Component displayElement(RegistryAccess registries, ResourceLocation elementId) {
        return elementDefinition(registries, elementId)
                .<Component>map(definition -> Component.translatable(definition.translationKey()))
                .orElseGet(() -> unavailable(elementId));
    }

    private Optional<SpiritualElementDefinition> elementDefinition(
            RegistryAccess registries,
            ResourceLocation elementId) {
        return registries.registry(ModCultivationRegistries.SPIRITUAL_ELEMENTS)
                .flatMap(registry -> registry.getOptional(elementId));
    }

    private Optional<TechniqueDefinition> techniqueDefinition(
            RegistryAccess registries,
            ResourceLocation techniqueId) {
        return registries.registry(ModCultivationRegistries.TECHNIQUES)
                .flatMap(registry -> registry.getOptional(techniqueId));
    }

    private int elementSortOrder(RegistryAccess registries, ResourceLocation elementId) {
        return elementDefinition(registries, elementId)
                .map(SpiritualElementDefinition::sortOrder)
                .orElse(Integer.MAX_VALUE);
    }

    private int elementColor(RegistryAccess registries, ResourceLocation elementId) {
        return 0xFF000000 | elementDefinition(registries, elementId)
                .flatMap(SpiritualElementDefinition::displayColor)
                .orElse(FALLBACK_ELEMENT & 0xFFFFFF);
    }

    private Component unavailable(ResourceLocation id) {
        return Component.literal(id.toString())
                .append(" ")
                .append(Component.translatable("screen.myvillage.cultivation.unavailable"))
                .withStyle(ChatFormatting.RED);
    }

    private String fit(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        int available = Math.max(1, maxWidth - font.width(suffix));
        return font.plainSubstrByWidth(value, available) + suffix;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean overTechniqueList = mouseX >= techniqueListX
                && mouseX < techniqueListX + techniqueListWidth
                && mouseY >= techniqueListY
                && mouseY < techniqueListY + techniqueListHeight;
        if (view == View.PROFILE && overTechniqueList && techniqueContentHeight > techniqueListHeight) {
            int maxScroll = techniqueContentHeight - techniqueListHeight;
            techniqueScroll = Math.max(0, Math.min(maxScroll, techniqueScroll - (int) Math.signum(scrollY) * 18));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientCultivationKeyMappings.OPEN_PROFILE.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
