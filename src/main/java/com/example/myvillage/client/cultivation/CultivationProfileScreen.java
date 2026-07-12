package com.example.myvillage.client.cultivation;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.data.SpiritualElementDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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

    private int techniqueScroll;
    private int techniqueListX;
    private int techniqueListY;
    private int techniqueListWidth;
    private int techniqueListHeight;
    private int techniqueContentHeight;

    public CultivationProfileScreen() {
        super(Component.translatable("screen.myvillage.cultivation.title"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Screen#render applies the vanilla blur pass; this screen owns its backdrop so its content stays crisp.
        graphics.fill(0, 0, width, height, BACKDROP);

        int panelWidth = Math.min(440, width - 20);
        int panelHeight = Math.min(250, height - 20);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int right = left + panelWidth;
        int bottom = top + panelHeight;

        graphics.fill(left, top, right, bottom, PANEL);
        graphics.renderOutline(left, top, panelWidth, panelHeight, BORDER);
        graphics.fill(left + 1, top + 1, right - 1, top + 57, PANEL_HEADER);
        graphics.hLine(left + 1, right - 2, top + 57, DIVIDER);

        CultivationProfile profile = ClientCultivationState.latest().orElse(null);
        drawHeader(graphics, profile, left, top, panelWidth);
        if (profile == null) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("screen.myvillage.cultivation.no_snapshot"),
                    left + panelWidth / 2,
                    top + panelHeight / 2,
                    MUTED);
            return;
        }

        RegistryAccess registries = minecraft != null && minecraft.level != null
                ? minecraft.level.registryAccess()
                : RegistryAccess.EMPTY;
        int innerLeft = left + 12;
        int innerWidth = panelWidth - 24;
        int gap = 14;
        int columnWidth = (innerWidth - gap) / 2;
        int contentTop = top + 67;
        int contentBottom = bottom - 17;

        drawCultivationColumn(graphics, profile, registries, innerLeft, contentTop, columnWidth, contentBottom);
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
        graphics.drawString(
                font,
                Component.translatable("screen.myvillage.cultivation.status"),
                x,
                top,
                ACCENT,
                false);
        drawPair(graphics, "screen.myvillage.cultivation.progress", Long.toString(profile.cultivationProgress()),
                x, top + 15, columnWidth);
        drawPair(graphics, "screen.myvillage.cultivation.power", Long.toString(profile.currentSpiritualPower()),
                x, top + 28, columnWidth);
        drawPair(graphics, "screen.myvillage.cultivation.stability", profile.stability() + " / 100",
                x, top + 41, columnWidth);

        int barY = top + 53;
        graphics.fill(x, barY, x + columnWidth, barY + 4, BAR_BACKGROUND);
        int fillWidth = Math.round(columnWidth * (profile.stability() / 100.0F));
        graphics.fill(x, barY, x + fillWidth, barY + 4, STABILITY);

        int rootTitleY = top + 65;
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
                    rootTitleY + 14,
                    MUTED,
                    false);
            return;
        }

        List<Map.Entry<ResourceLocation, Integer>> affinities = new ArrayList<>(
                root.get().affinitiesBasisPoints().entrySet());
        affinities.sort(Comparator
                .comparingInt((Map.Entry<ResourceLocation, Integer> entry) -> elementSortOrder(registries, entry.getKey()))
                .thenComparing(entry -> entry.getKey().toString()));
        int rowY = rootTitleY + 14;
        int visibleRows = Math.max(1, (bottom - rowY) / 11);
        int rendered = Math.min(visibleRows, affinities.size());
        for (int index = 0; index < rendered; index++) {
            Map.Entry<ResourceLocation, Integer> affinity = affinities.get(index);
            String name = displayElement(registries, affinity.getKey()).getString();
            String percent = String.format(Locale.ROOT, "%.1f%%", affinity.getValue() / 100.0D);
            int color = elementColor(registries, affinity.getKey());
            graphics.fill(x, rowY + index * 11 + 2, x + 5, rowY + index * 11 + 7, color);
            graphics.drawString(font, fit(name, columnWidth - 49), x + 9, rowY + index * 11, TEXT, false);
            graphics.drawString(
                    font,
                    percent,
                    x + columnWidth - font.width(percent),
                    rowY + index * 11,
                    MUTED,
                    false);
        }
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
        graphics.drawString(
                font,
                Component.translatable("screen.myvillage.cultivation.techniques"),
                x,
                top,
                ACCENT,
                false);
        if (profile.learnedTechniques().isEmpty()) {
            graphics.drawString(
                    font,
                    Component.translatable("screen.myvillage.cultivation.none"),
                    x,
                    top + 15,
                    MUTED,
                    false);
            techniqueContentHeight = 0;
            return;
        }

        techniqueListX = x;
        techniqueListY = top + 14;
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

    private void drawPair(
            GuiGraphics graphics,
            String labelKey,
            String value,
            int x,
            int y,
            int width) {
        String label = Component.translatable(labelKey).getString();
        graphics.drawString(font, label, x, y, MUTED, false);
        int valueX = x + font.width(label) + 5;
        graphics.drawString(font, fit(value, Math.max(1, x + width - valueX)), valueX, y, TEXT, false);
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
        RealmStageDefinition stage = registries.registry(ModCultivationRegistries.REALMS)
                .flatMap(registry -> registry.getOptional(profile.realmId()))
                .flatMap(realm -> realm.stages().stream()
                        .filter(candidate -> candidate.id().equals(profile.stageId()))
                        .findFirst())
                .orElse(null);
        return stage == null
                ? unavailable(profile.stageId())
                : Component.translatable(stage.translationKey());
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
        if (overTechniqueList && techniqueContentHeight > techniqueListHeight) {
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
