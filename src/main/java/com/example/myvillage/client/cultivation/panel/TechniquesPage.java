package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueRequirements;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Learned techniques: category, grade, elements, mastery, and what each one required. */
public final class TechniquesPage extends PanelPage {
    private static final int GAP = 4;
    private static final int PAD = PanelTheme.CARD_PADDING;
    private static final int ENTRY_HEIGHT = 45;
    private static final int EMPTY_HEIGHT = 42;

    @Override
    public int render(GuiGraphics graphics, PanelContext context, int x, int y, int width, int viewportHeight) {
        Font font = context.font();
        Map<ResourceLocation, TechniqueProgress> learned = context.profile().learnedTechniques();
        if (learned.isEmpty()) {
            PanelTheme.card(graphics, font, x, y, width, EMPTY_HEIGHT,
                    Component.translatable("screen.myvillage.cultivation.techniques"));
            int textY = y + PanelTheme.CARD_TITLE_HEIGHT + 2;
            graphics.drawString(
                    font,
                    context.text("screen.myvillage.cultivation.techniques_empty"),
                    x + PAD,
                    textY,
                    PanelTheme.MUTED,
                    false);
            graphics.drawString(
                    font,
                    PanelTheme.fit(
                            font, context.text("screen.myvillage.cultivation.techniques_hint"), width - PAD * 2),
                    x + PAD,
                    textY + PanelTheme.ROW + 2,
                    PanelTheme.FAINT,
                    false);
            return EMPTY_HEIGHT;
        }

        int cursor = y;
        for (Map.Entry<ResourceLocation, TechniqueProgress> entry : learned.entrySet()) {
            drawEntry(graphics, context, entry.getKey(), entry.getValue(), x, cursor, width);
            cursor += ENTRY_HEIGHT + GAP;
        }
        return cursor - GAP - y;
    }

    private void drawEntry(
            GuiGraphics graphics,
            PanelContext context,
            ResourceLocation techniqueId,
            TechniqueProgress progress,
            int x,
            int y,
            int width) {
        Font font = context.font();
        TechniqueDefinition definition = context.technique(techniqueId).orElse(null);
        graphics.fill(x, y, x + width, y + ENTRY_HEIGHT, PanelTheme.CARD);
        graphics.renderOutline(x, y, width, ENTRY_HEIGHT, PanelTheme.CARD_BORDER);
        graphics.fill(x + 1, y + 1, x + 3, y + ENTRY_HEIGHT - 1, definition == null ? PanelTheme.RED : PanelTheme.GOLD);
        int innerX = x + PAD + 2;
        int innerWidth = width - PAD * 2 - 2;

        String mastery = context.text("screen.myvillage.cultivation.mastery", progress.masteryPoints());
        graphics.drawString(
                font, mastery, innerX + innerWidth - font.width(mastery), y + 5, PanelTheme.MUTED, false);
        int nameWidth = Math.max(1, innerWidth - font.width(mastery) - 8);
        if (definition == null) {
            Component missing = context.unavailable(techniqueId);
            graphics.drawString(
                    font, PanelTheme.fit(font, missing.getString(), nameWidth), innerX, y + 5, PanelTheme.RED, false);
            return;
        }
        graphics.drawString(
                font,
                PanelTheme.fit(font, context.text(definition.translationKey()), nameWidth),
                innerX,
                y + 5,
                PanelTheme.GOLD_BRIGHT,
                false);

        int chipX = innerX;
        int chipY = y + 17;
        chipX += GAP + PanelTheme.chip(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.category." + definition.category().serializedName()),
                chipX,
                chipY,
                PanelTheme.GOLD);
        chipX += GAP + PanelTheme.chip(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.grade", definition.grade()),
                chipX,
                chipY,
                PanelTheme.JADE);
        if (definition.elements().isEmpty()) {
            PanelTheme.chip(
                    graphics,
                    font,
                    context.text("screen.myvillage.cultivation.technique_no_element"),
                    chipX,
                    chipY,
                    PanelTheme.MUTED);
        } else {
            for (ResourceLocation elementId : definition.elements()) {
                String name = context.elementName(elementId).getString();
                if (chipX + PanelTheme.chipWidth(font, name) > innerX + innerWidth) {
                    break;
                }
                chipX += GAP + PanelTheme.chip(
                        graphics, font, name, chipX, chipY, context.elementColor(elementId));
            }
        }

        PanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.technique_requirement"),
                requirementText(context, definition.requirements()),
                innerX,
                y + 32,
                innerWidth,
                PanelTheme.TEXT);
    }

    /** The definition's own requirements, as stated; the panel does not evaluate them. */
    private String requirementText(PanelContext context, TechniqueRequirements requirements) {
        List<String> parts = new ArrayList<>();
        requirements.minimumRealm().ifPresent(realmId -> {
            String realm = context.realmName(realmId).getString();
            parts.add(requirements.minimumStage()
                    .map(stageId -> realm + " · " + context.stageName(realmId, stageId).getString())
                    .orElse(realm));
        });
        requirements.minimumElementAffinities().forEach((elementId, basisPoints) -> parts.add(
                context.elementName(elementId).getString()
                        + String.format(Locale.ROOT, " ≥ %.1f%%", basisPoints / 100.0D)));
        return parts.isEmpty()
                ? context.text("screen.myvillage.cultivation.none")
                : String.join("  ", parts);
    }
}
