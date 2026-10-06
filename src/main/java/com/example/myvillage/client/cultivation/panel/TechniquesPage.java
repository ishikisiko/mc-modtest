package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.cultivation.ClientCultivationIntentSender;
import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.data.HeritageDefinition;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.SchoolDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueRequirements;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Learned techniques as a browser: one card per category (心法, 绝技, 身法, 炼体), each entry with
 * its name, mastery, chips (category, grade, school, heritage and position, elements), and the
 * requirement it stated. A core technique shows whether it is the one running; the others carry
 * a button that asks the server to run them instead. The server decides every switch and its
 * cost; the page only shows the synced profile.
 */
public final class TechniquesPage extends PanelPage {
    private static final int GAP = 4;
    private static final int PAD = PanelTheme.CARD_PADDING;
    private static final int EMPTY_HEIGHT = 42;
    /** Name row, then chip rows, then the requirement row. */
    private static final int NAME_ROW = 14;
    private static final int CHIP_ROW = 14;
    private static final int REQUIREMENT_ROW = 13;
    private static final int UNRESOLVED_HEIGHT = 15;
    private static final int MAX_CHIP_ROWS = 3;
    private static final int BUTTON_HEIGHT = 14;
    private static final int CARD_BOTTOM = 2;

    /** One "run this" button per learned core technique, kept across frames. */
    private final Map<ResourceLocation, PanelButton> switchButtons = new HashMap<>();
    /** The buttons drawn this frame, in screen coordinates; only these take clicks. */
    private final List<PanelButton> shownButtons = new ArrayList<>();
    private int pointerX = -1;
    private int pointerY = -1;

    @Override
    public void pointer(int mouseX, int mouseY) {
        pointerX = mouseX;
        pointerY = mouseY;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (PanelButton candidate : shownButtons) {
            if (candidate.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    /** The only place a core-technique switch is sent from. */
    private PanelButton switchButton(ResourceLocation techniqueId) {
        return switchButtons.computeIfAbsent(techniqueId, id -> new PanelButton(
                PanelButton.Style.ACTION,
                PanelTheme.JADE,
                0,
                0,
                1,
                BUTTON_HEIGHT,
                Component.translatable("screen.myvillage.cultivation.technique_run"),
                pressed -> ClientCultivationIntentSender.sendCoreSwitch(id)));
    }

    @Override
    public int render(GuiGraphics graphics, PanelContext context, int x, int y, int width, int viewportHeight) {
        shownButtons.clear();
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

        Optional<ResourceLocation> running = context.profile().activeCoreTechnique();
        List<TechniqueShelf.Item> items = new ArrayList<>(learned.size());
        for (ResourceLocation techniqueId : learned.keySet()) {
            TechniqueDefinition definition = context.technique(techniqueId).orElse(null);
            items.add(definition == null
                    ? new TechniqueShelf.Item(techniqueId, null, -1, techniqueId.toString())
                    : new TechniqueShelf.Item(techniqueId, definition.category(), definition.grade(),
                            context.text(definition.translationKey())));
        }

        int cursor = y;
        for (TechniqueShelf.Group group : TechniqueShelf.group(items)) {
            List<Entry> entries = new ArrayList<>(group.items().size());
            for (TechniqueShelf.Item item : group.items()) {
                entries.add(entry(context, item, learned.get(item.id()), running, width));
            }
            int height = PanelTheme.CARD_TITLE_HEIGHT + CARD_BOTTOM;
            for (Entry entry : entries) {
                height += entry.height();
            }
            height += entries.size() - 1;
            drawGroup(graphics, context, group, entries, x, cursor, width, height);
            cursor += height + GAP;
        }
        return cursor - GAP - y;
    }

    // ---- layout ----------------------------------------------------------------------------

    private record Chip(String text, int color) {
    }

    /** What the entry's action slot shows: nothing, the running marker, or the switch button. */
    private enum Action {
        NONE,
        RUNNING,
        SWITCH
    }

    /** One entry measured for the card's width; {@code rows} gives each chip's row (-1 = dropped). */
    private record Entry(
            TechniqueShelf.Item item,
            TechniqueDefinition definition,
            TechniqueProgress progress,
            List<Chip> chips,
            int[] rows,
            Action action,
            int actionWidth,
            int height) {
    }

    private Entry entry(
            PanelContext context,
            TechniqueShelf.Item item,
            TechniqueProgress progress,
            Optional<ResourceLocation> running,
            int width) {
        Font font = context.font();
        TechniqueDefinition definition = context.technique(item.id()).orElse(null);
        if (definition == null) {
            return new Entry(item, null, progress, List.of(), new int[0], Action.NONE, 0, UNRESOLVED_HEIGHT);
        }
        int innerWidth = innerWidth(width);
        Action action = !definition.isCore()
                ? Action.NONE
                : running.filter(item.id()::equals).isPresent() ? Action.RUNNING : Action.SWITCH;
        int actionWidth = switch (action) {
            case NONE -> 0;
            case RUNNING -> PanelTheme.chipWidth(font, context.text("screen.myvillage.cultivation.technique_running"));
            case SWITCH -> Math.min(innerWidth / 2,
                    font.width(context.text("screen.myvillage.cultivation.technique_run")) + 12);
        };

        List<Chip> chips = new ArrayList<>();
        // every chip is cut to the row, so the flow below never has to move one twice
        int chipText = Math.max(1, innerWidth - 7);
        chips.add(new Chip(PanelTheme.fit(font,
                context.text("screen.myvillage.cultivation.category." + definition.category().serializedName()),
                chipText), PanelTheme.GOLD));
        chips.add(new Chip(PanelTheme.fit(font, gradeName(context, definition.grade()), chipText), PanelTheme.JADE));
        definition.school().ifPresent(schoolId -> chips.add(new Chip(
                PanelTheme.fit(font, schoolName(context, schoolId), chipText), PanelTheme.AMBER)));
        for (Map.Entry<ResourceLocation, HeritageDefinition> heritage
                : ModCultivationRegistries.heritagesContaining(context.registries(), item.id())) {
            HeritageDefinition chain = heritage.getValue();
            chips.add(new Chip(PanelTheme.fit(font, context.text(
                    "screen.myvillage.cultivation.heritage_position",
                    context.text(chain.translationKey()),
                    TechniqueShelf.chainPosition(chain.indexOf(item.id()), chain.techniques().size())),
                    chipText), PanelTheme.GOLD_BRIGHT));
        }
        if (definition.elements().isEmpty()) {
            chips.add(new Chip(context.text("screen.myvillage.cultivation.technique_no_element"), PanelTheme.MUTED));
        } else {
            for (ResourceLocation elementId : definition.elements()) {
                chips.add(new Chip(PanelTheme.fit(font, context.elementName(elementId).getString(), chipText),
                        context.elementColor(elementId)));
            }
        }
        int[] widths = new int[chips.size()];
        for (int index = 0; index < widths.length; index++) {
            widths[index] = PanelTheme.chipWidth(font, chips.get(index).text());
        }
        int firstRow = action == Action.NONE ? innerWidth : innerWidth - actionWidth - GAP;
        int[] rows = TechniqueShelf.chipRows(widths, firstRow, innerWidth, GAP, MAX_CHIP_ROWS);
        int height = NAME_ROW + TechniqueShelf.rowCount(rows) * CHIP_ROW + REQUIREMENT_ROW;
        return new Entry(item, definition, progress, List.copyOf(chips), rows, action, actionWidth, height);
    }

    private static int innerWidth(int width) {
        return width - PAD * 2 - 2;
    }

    private static String gradeName(PanelContext context, int grade) {
        String key = TechniqueShelf.gradeNameKey(grade);
        return key != null ? context.text(key) : context.text("screen.myvillage.cultivation.grade", grade);
    }

    private static String schoolName(PanelContext context, ResourceLocation schoolId) {
        return ModCultivationRegistries.school(context.registries(), schoolId)
                .map(SchoolDefinition::translationKey)
                .map(context::text)
                .orElseGet(() -> context.unavailable(schoolId).getString());
    }

    // ---- drawing ---------------------------------------------------------------------------

    private void drawGroup(GuiGraphics graphics, PanelContext context, TechniqueShelf.Group group,
                           List<Entry> entries, int x, int y, int width, int height) {
        Font font = context.font();
        String count = context.text("screen.myvillage.cultivation.techniques_count", entries.size());
        PanelTheme.card(graphics, font, x, y, width, height, Component.translatable(group.titleKey()));
        int countX = x + width - PAD - font.width(count);
        graphics.fill(countX - 4, y + 1, x + width - 1, y + PanelTheme.CARD_TITLE_HEIGHT - 1, PanelTheme.CARD);
        graphics.drawString(font, count, countX, y + 3, PanelTheme.FAINT, false);
        int cursor = y + PanelTheme.CARD_TITLE_HEIGHT;
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                graphics.fill(x + PAD, cursor, x + width - PAD, cursor + 1, PanelTheme.DIVIDER);
                cursor++;
            }
            drawEntry(graphics, context, entries.get(index), x, cursor, width);
            cursor += entries.get(index).height();
        }
    }

    private void drawEntry(GuiGraphics graphics, PanelContext context, Entry entry, int x, int y, int width) {
        Font font = context.font();
        TechniqueDefinition definition = entry.definition();
        int accent = definition == null ? PanelTheme.RED
                : entry.action() == Action.RUNNING ? PanelTheme.JADE : PanelTheme.GOLD;
        graphics.fill(x + 1, y + 1, x + 3, y + entry.height() - 1, accent);
        int innerX = x + PAD + 2;
        int innerWidth = innerWidth(width);

        String mastery = entry.progress() == null
                ? context.unavailableText()
                : context.text("screen.myvillage.cultivation.mastery", entry.progress().masteryPoints());
        graphics.drawString(
                font, mastery, innerX + innerWidth - font.width(mastery), y + 3, PanelTheme.MUTED, false);
        int nameWidth = Math.max(1, innerWidth - font.width(mastery) - 8);
        if (definition == null) {
            Component missing = context.unavailable(entry.item().id());
            graphics.drawString(
                    font, PanelTheme.fit(font, missing.getString(), nameWidth), innerX, y + 3, PanelTheme.RED, false);
            return;
        }
        graphics.drawString(
                font, PanelTheme.fit(font, entry.item().name(), nameWidth), innerX, y + 3, PanelTheme.GOLD_BRIGHT, false);

        int chipTop = y + NAME_ROW;
        int[] used = new int[MAX_CHIP_ROWS];
        for (int index = 0; index < entry.chips().size(); index++) {
            int row = entry.rows()[index];
            if (row < 0) {
                break;
            }
            Chip chip = entry.chips().get(index);
            int chipX = innerX + used[row];
            used[row] += GAP + PanelTheme.chip(graphics, font, chip.text(), chipX, chipTop + row * CHIP_ROW,
                    chip.color());
        }
        int actionX = innerX + innerWidth - entry.actionWidth();
        if (entry.action() == Action.RUNNING) {
            PanelTheme.chip(graphics, font, context.text("screen.myvillage.cultivation.technique_running"),
                    actionX, chipTop, PanelTheme.JADE);
        } else if (entry.action() == Action.SWITCH) {
            PanelButton button = switchButton(entry.item().id());
            button.setPosition(actionX, chipTop - 1);
            button.setWidth(entry.actionWidth());
            button.render(graphics, pointerX, pointerY, 0.0F);
            shownButtons.add(button);
        }

        int requirementY = chipTop + TechniqueShelf.rowCount(entry.rows()) * CHIP_ROW + 1;
        PanelTheme.pair(
                graphics,
                font,
                context.text("screen.myvillage.cultivation.technique_requirement"),
                requirementText(context, definition.requirements()),
                innerX,
                requirementY,
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
