package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.sim.ClientWorldSimState;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.sim.runtime.WorldSimText;
import com.example.myvillage.sim.runtime.net.WorldSimQuery;
import com.example.myvillage.sim.runtime.net.WorldSimSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 天下: the world ledger (命簿) as the player may read it. Five sub-views (overview, sects, people,
 * chronicle, here) are switched from the dock; rows drill down to a sect or a person and a back
 * zone returns. The page holds no ledger: each frame it asks {@link ClientWorldSimState} for the
 * open view's query (the cache throttles repeats) and draws the latest answer. It is read-only.
 */
public final class WorldPage extends PanelPage {
    private static final int GAP = 4;
    /** Below this body width the dock takes two rows and the cards stack in one column. */
    private static final int WIDE = 300;
    private static final int BUTTON_HEIGHT = 18;
    private static final int SEARCH_MIN = 64;
    private static final int SEARCH_MAX = 120;
    private static final int BACK_HEIGHT = 14;
    private static final int MAX_BACK = 32;
    /** From this width a person row fits on one line. */
    private static final int ONE_LINE_PERSON = 240;
    /** A person row's thumbnail and the room it takes before the row's text. */
    private static final int THUMB = 16;
    private static final int THUMB_ROOM = 20;
    /** The person card's portrait and the indent of the rows beside it. */
    private static final int PORTRAIT = 64;
    private static final int PORTRAIT_ROOM = 70;
    /** Below this many pixels beside the portrait, the card's rows start under it instead. */
    private static final int MIN_BESIDE_PORTRAIT = 60;
    /** Narrower than this, an event's text starts under its date instead of beside it. */
    private static final int MIN_EVENT_TEXT = 90;
    private static final int PAD = PanelTheme.CARD_PADDING;
    private static final int ROW = PanelTheme.ROW;
    private static final List<ResourceLocation> ELEMENTS = List.of(
            ModCultivationRegistries.METAL_ELEMENT_ID,
            ModCultivationRegistries.WOOD_ELEMENT_ID,
            ModCultivationRegistries.WATER_ELEMENT_ID,
            ModCultivationRegistries.FIRE_ELEMENT_ID,
            ModCultivationRegistries.EARTH_ELEMENT_ID);
    private static final List<String> ELEMENT_KEYS = List.of(
            "screen.myvillage.cultivation.world.element.metal",
            "screen.myvillage.cultivation.world.element.wood",
            "screen.myvillage.cultivation.world.element.water",
            "screen.myvillage.cultivation.world.element.fire",
            "screen.myvillage.cultivation.world.element.earth");

    /** The dock's sub-views. */
    private enum Section {
        OVERVIEW("screen.myvillage.cultivation.world.view.overview"),
        SECTS("screen.myvillage.cultivation.world.view.sects"),
        PEOPLE("screen.myvillage.cultivation.world.view.people"),
        CHRONICLE("screen.myvillage.cultivation.world.view.chronicle"),
        HERE("screen.myvillage.cultivation.world.view.here");

        private final String titleKey;

        Section(String titleKey) {
            this.titleKey = titleKey;
        }
    }

    /** A drilled-down detail: one sect or one person. */
    private record Target(boolean sect, int id) {
        WorldSimQuery query() {
            return sect ? WorldSimQuery.sect(id) : WorldSimQuery.person(id);
        }
    }

    // The screen rebuilds its pages when the window resizes, so where the reader is stays static.
    private static Section section = Section.OVERVIEW;
    /** The open detail, or null for the section's own list. */
    private static Target detail;
    /** Earlier details (null entries are the section's list), most recent last. */
    private static final List<Target> BACK = new ArrayList<>();
    private static String searchText = "";
    private static boolean scrollToTop;

    private final Map<Section, PanelButton> buttons = new EnumMap<>(Section.class);
    private WorldSearchBox search;
    /** The last search answer, shown while a newer search is on its way so typing does not flicker. */
    private WorldSimSnapshot lastSearch;
    private List<WorldCanvas.Hot> hots = List.of();
    private int pointerX = -1;
    private int pointerY = -1;

    @Override
    public int init(int x, int y, int width, int height, Consumer<AbstractWidget> widgets) {
        Font font = Minecraft.getInstance().font;
        int widest = 0;
        for (Section candidate : Section.values()) {
            widest = Math.max(widest, font.width(Component.translatable(candidate.titleKey)) + 10);
        }
        int searchWidth = Math.max(SEARCH_MIN, Math.min(SEARCH_MAX, width / 4));
        int buttonArea = width - searchWidth - GAP;
        boolean twoRows = width < WIDE || (buttonArea - GAP * 4) / 5 < widest;
        int gap = twoRows ? 2 : GAP;
        if (twoRows) {
            buttonArea = width;
        }
        int rows = twoRows ? 2 : 1;
        int dock = GAP + rows * BUTTON_HEIGHT + (rows - 1) * GAP;
        int top = y + height - dock + GAP;

        Section[] sections = Section.values();
        for (int index = 0; index < sections.length; index++) {
            Section target = sections[index];
            int left = x + index * (buttonArea + gap) / sections.length;
            int right = x + (index + 1) * (buttonArea + gap) / sections.length - gap;
            PanelButton button = new PanelButton(
                    PanelButton.Style.NAV,
                    PanelTheme.GOLD,
                    left,
                    top,
                    Math.max(1, right - left),
                    BUTTON_HEIGHT,
                    Component.translatable(target.titleKey),
                    pressed -> open(target));
            buttons.put(target, button);
            widgets.accept(button);
        }

        int searchX = twoRows ? x : x + width - searchWidth;
        int searchY = twoRows ? top + BUTTON_HEIGHT + GAP : top;
        search = new WorldSearchBox(
                font,
                searchX,
                searchY,
                twoRows ? width : searchWidth,
                BUTTON_HEIGHT,
                Component.translatable("screen.myvillage.cultivation.world.search"));
        search.setMaxLength(WorldSimQuery.MAX_TEXT);
        search.setPanelHint(Component.translatable("screen.myvillage.cultivation.world.search_hint"));
        search.setValue(searchText);
        search.setResponder(WorldPage::onSearch);
        widgets.accept(search);
        return dock;
    }

    @Override
    public void setVisible(boolean visible) {
        for (PanelButton button : buttons.values()) {
            button.visible = visible;
        }
        search.visible = visible && section == Section.PEOPLE;
    }

    @Override
    public void refresh(PanelContext context, boolean visible) {
        for (Map.Entry<Section, PanelButton> entry : buttons.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == section);
        }
        search.visible = visible && section == Section.PEOPLE;
        if (!visible) {
            return;
        }
        WorldSimQuery query = currentQuery();
        if (query != null) {
            ClientWorldSimState.request(query);
        }
    }

    @Override
    public void pointer(int mouseX, int mouseY) {
        pointerX = mouseX;
        pointerY = mouseY;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        for (WorldCanvas.Hot hot : hots) {
            if (hot.contains(mouseX, mouseY)) {
                hot.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean takeScrollToTop() {
        boolean take = scrollToTop;
        scrollToTop = false;
        return take;
    }

    // --- navigation -------------------------------------------------------------------------

    private static void open(Section target) {
        section = target;
        detail = null;
        BACK.clear();
        scrollToTop = true;
    }

    private static void drill(Target target) {
        if (target.equals(detail)) {
            return;
        }
        if (BACK.size() >= MAX_BACK) {
            BACK.remove(0);
        }
        BACK.add(detail);
        detail = target;
        scrollToTop = true;
    }

    private static void back() {
        detail = BACK.isEmpty() ? null : BACK.remove(BACK.size() - 1);
        scrollToTop = true;
    }

    private static Runnable toSect(int sectId) {
        return sectId < 0 ? null : () -> drill(new Target(true, sectId));
    }

    private static Runnable toPerson(int personId) {
        return personId < 0 ? null : () -> drill(new Target(false, personId));
    }

    private static void onSearch(String text) {
        searchText = text;
        if (section == Section.PEOPLE) {
            detail = null;
            BACK.clear();
        }
        scrollToTop = true;
    }

    /** The query the open view reads; null while the people view has nothing to look for. */
    private static WorldSimQuery currentQuery() {
        if (detail != null) {
            return detail.query();
        }
        return switch (section) {
            case OVERVIEW -> WorldSimQuery.overview();
            case SECTS -> WorldSimQuery.sects();
            case PEOPLE -> searchText.isBlank() ? null : WorldSimQuery.personSearch(searchText);
            case CHRONICLE -> WorldSimQuery.chronicle();
            case HERE -> WorldSimQuery.here();
        };
    }

    // --- rendering --------------------------------------------------------------------------

    @Override
    public int render(GuiGraphics graphics, PanelContext context, int x, int y, int width, int viewportHeight) {
        WorldCanvas canvas = new WorldCanvas(graphics, context.font(), pointerX, pointerY);
        int height = draw(canvas, context, x, y, width);
        hots = List.copyOf(canvas.hots());
        return height;
    }

    private int draw(WorldCanvas c, PanelContext context, int x, int y, int width) {
        int cursor = y;
        if (detail != null) {
            cursor += backRow(c, x, cursor, width);
        }
        WorldSimQuery query = currentQuery();
        if (query == null) {
            return cursor - y + message(c, x, cursor, width,
                    tr("screen.myvillage.cultivation.world.card.people"),
                    tr("screen.myvillage.cultivation.world.search_prompt"),
                    PanelTheme.MUTED);
        }
        Optional<WorldSimSnapshot> fresh = ClientWorldSimState.latest(query);
        boolean searching = query.kind() == WorldSimQuery.Kind.PERSON_SEARCH;
        if (fresh.isPresent() && searching) {
            lastSearch = fresh.get();
        }
        WorldSimSnapshot snapshot = fresh.orElse(searching ? lastSearch : null);
        if (snapshot == null) {
            return cursor - y + message(c, x, cursor, width,
                    tr("screen.myvillage.cultivation.world.title"),
                    tr("screen.myvillage.cultivation.world.loading"),
                    PanelTheme.MUTED);
        }
        if (!snapshot.active()) {
            return cursor - y + message(c, x, cursor, width,
                    tr("screen.myvillage.cultivation.world.inactive_title"),
                    Component.translatable("commands.myvillage.world.inactive", snapshot.inactiveReason()),
                    PanelTheme.AMBER);
        }
        int height = switch (snapshot.query().kind()) {
            case OVERVIEW -> overview(c, context, snapshot, x, cursor, width);
            case SECTS -> sectList(c, snapshot, x, cursor, width);
            case SECT -> sectDetail(c, snapshot, x, cursor, width);
            case PERSON_SEARCH -> searchResults(c, snapshot, x, cursor, width);
            case PERSON -> personDetail(c, context, snapshot, x, cursor, width);
            case CHRONICLE -> chronicle(c, snapshot, x, cursor, width);
            case HERE -> here(c, snapshot, x, cursor, width);
        };
        return cursor - y + height;
    }

    /** The 「← 返回」 hot zone above a detail. */
    private int backRow(WorldCanvas c, int x, int y, int width) {
        String label = text("screen.myvillage.cultivation.world.back");
        int zone = Math.min(width, c.width(label) + 12);
        c.link(x, y, x + zone, y + BACK_HEIGHT - 2, WorldPage::back);
        c.text(label, x + 4, y + 2, zone - 4, PanelTheme.GOLD);
        return BACK_HEIGHT;
    }

    /** One card with a wrapped message: loading, inactive, empty, and outside states. */
    private int message(WorldCanvas c, int x, int y, int width, Component title, Component body, int color) {
        return c.card(x, y, width, title, (bx, by, bw) -> c.wrapped(body, bx, by + 1, bw, color) + 2);
    }

    /** Cards in two columns from {@link #WIDE} up (left and right), then the full-width ones. */
    private int layout(
            WorldCanvas c,
            int x,
            int y,
            int width,
            List<WorldCanvas.Body> left,
            List<WorldCanvas.Body> right,
            List<WorldCanvas.Body> below) {
        if (width < WIDE) {
            List<WorldCanvas.Body> all = new ArrayList<>(left);
            all.addAll(right);
            all.addAll(below);
            return stack(all, x, y, width);
        }
        int leftWidth = (width - GAP) / 2;
        int rightWidth = width - GAP - leftWidth;
        int top = Math.max(stack(left, x, y, leftWidth), stack(right, x + leftWidth + GAP, y, rightWidth));
        if (below.isEmpty()) {
            return top;
        }
        return top + GAP + stack(below, x, y + top + GAP, width);
    }

    private static int stack(List<WorldCanvas.Body> cards, int x, int y, int width) {
        int cursor = y;
        for (WorldCanvas.Body card : cards) {
            cursor += card.draw(x, cursor, width) + GAP;
        }
        return cards.isEmpty() ? 0 : cursor - GAP - y;
    }

    // --- 总览 ---------------------------------------------------------------------------------

    private int overview(WorldCanvas c, PanelContext context, WorldSimSnapshot s, int x, int y, int width) {
        WorldSimSnapshot.Overview o = s.overview();
        if (o == null) {
            return message(c, x, y, width, tr("screen.myvillage.cultivation.world.card.ledger"),
                    tr("screen.myvillage.cultivation.world.none"), PanelTheme.MUTED);
        }
        Component ledgerTitle = tr("screen.myvillage.cultivation.world.card.ledger");
        Component realmsTitle = tr("screen.myvillage.cultivation.world.card.realms");
        WorldCanvas.Body ledger = (bx, by, bw) -> ledgerBody(c, context, s, o, bx, by, bw);
        WorldCanvas.Body realms = (bx, by, bw) -> realmsBody(c, o, bx, by, bw);
        int top;
        if (width >= WIDE) {
            int leftWidth = (width - GAP) * 11 / 20;
            int rightWidth = width - GAP - leftWidth;
            int height = Math.max(
                    c.measure(() -> c.card(x, y, leftWidth, ledgerTitle, ledger)),
                    c.measure(() -> c.card(x, y, rightWidth, realmsTitle, realms)));
            c.card(x, y, leftWidth, height, ledgerTitle, ledger);
            c.card(x + leftWidth + GAP, y, rightWidth, height, realmsTitle, realms);
            top = height;
        } else {
            top = c.card(x, y, width, ledgerTitle, ledger);
            top += GAP + c.card(x, y + top + GAP, width, realmsTitle, realms);
        }
        int cursor = y + top + GAP;
        cursor += c.card(x, cursor, width, tr("screen.myvillage.cultivation.world.card.mine"),
                (bx, by, bw) -> mineBody(c, s, bx, by, bw)) + GAP;
        cursor += c.card(x, cursor, width, tr("screen.myvillage.cultivation.world.card.foremost"),
                (bx, by, bw) -> personList(c, s.persons(), bx, by, bw));
        return cursor - y;
    }

    private int ledgerBody(WorldCanvas c, PanelContext context, WorldSimSnapshot s,
            WorldSimSnapshot.Overview o, int x, int y, int width) {
        int cursor = y;
        int dayOfYear = WorldReadouts.dayOfYear(s.day(), s.prehistoryDays(), s.daysPerYear());
        // The week length is a cultivation-time setting, so it comes with the time snapshot.
        String date = context.time() == null
                ? text("screen.myvillage.cultivation.world.date_value", dateText(s, s.day()), dayOfYear)
                : text("screen.myvillage.cultivation.world.date_week_value",
                        dateText(s, s.day()),
                        PanelReadouts.weekOfYear(dayOfYear, context.time().daysPerWeek()),
                        PanelReadouts.dayOfWeek(dayOfYear, context.time().daysPerWeek()));
        c.pair(text("screen.myvillage.cultivation.world.date"), date, x, cursor, width, PanelTheme.TEXT);
        cursor += ROW + 2;

        String label = text("screen.myvillage.cultivation.world.settlement");
        int labelWidth = c.text(label, x, cursor + 2, width / 3, PanelTheme.MUTED);
        String state = text(o.paused() ? "commands.myvillage.world.paused" : "commands.myvillage.world.running");
        int chipWidth = Math.min(c.chipWidth(state), width - labelWidth - 6);
        int chipX = x + width - chipWidth;
        c.chip(state, chipX, cursor, chipWidth, o.paused() ? PanelTheme.RED : PanelTheme.JADE);
        int pendingRight = chipX - 4;
        c.textRight(text("screen.myvillage.cultivation.world.pending", o.pendingDays()),
                pendingRight, cursor + 2, pendingRight - (x + labelWidth + 6), PanelTheme.MUTED);
        cursor += PanelTheme.CHIP_HEIGHT + 2;

        c.pair(text("screen.myvillage.cultivation.world.tier"),
                text("commands.myvillage.world.tier." + o.tierId()), x, cursor, width, PanelTheme.TEXT);
        cursor += ROW + 1;
        c.pair(text("screen.myvillage.cultivation.world.population"),
                o.population() + " / " + o.targetPopulation(), x, cursor, width, PanelTheme.TEXT);
        cursor += ROW + 1;
        c.pair(text("screen.myvillage.cultivation.world.dead"),
                Integer.toString(o.deadCount()), x, cursor, width, PanelTheme.TEXT);
        cursor += ROW + 1;
        c.pair(text("screen.myvillage.cultivation.world.sects"),
                text("screen.myvillage.cultivation.world.sects_value", o.activeSects(), o.destroyedSects()),
                x, cursor, width, PanelTheme.TEXT);
        cursor += ROW + 1;
        c.pair(text("screen.myvillage.cultivation.world.events"),
                text("screen.myvillage.cultivation.world.events_value", o.eventCount()),
                x, cursor, width, PanelTheme.TEXT);
        cursor += ROW;
        return cursor - y;
    }

    /** 我的宗门: the player's own record, or a pointer to the gate steward when there is none. */
    private int mineBody(WorldCanvas c, WorldSimSnapshot s, int x, int y, int width) {
        WorldSimSnapshot.MySect mine = s.mine();
        if (mine == null) {
            return c.wrapped(tr("screen.myvillage.cultivation.world.mine_none"), x, y + 1, width, PanelTheme.MUTED)
                    + 2;
        }
        int cursor = y;
        String sectName = mine.sectActive()
                ? mine.sectName()
                : mine.sectName() + " · " + WorldSimText.sectState("destroyed").getString();
        cursor += linkPair(c, "screen.myvillage.cultivation.world.sect", orNone(sectName),
                toSect(mine.sectId()), x, cursor, width);
        cursor += pair(c, "screen.myvillage.cultivation.world.mine_rank",
                WorldSimText.rank(mine.rank()).getString(), x, cursor, width);
        cursor += pair(c, "screen.myvillage.cultivation.world.mine_joined", dateText(s, mine.joinedDay()),
                x, cursor, width);
        cursor += pair(c, "screen.myvillage.cultivation.world.mine_master", orNone(mine.masterName()),
                x, cursor, width);
        cursor += pair(c, "screen.myvillage.cultivation.world.mine_contribution",
                Integer.toString(mine.contribution()), x, cursor, width);
        cursor += pair(c, "screen.myvillage.cultivation.world.task", taskText(mine), x, cursor, width);
        c.pair(text("screen.myvillage.cultivation.world.mine_standing"), signed(mine.standing()),
                x, cursor, width, mine.standing() < 0 ? PanelTheme.RED : PanelTheme.TEXT);
        cursor += ROW;
        return cursor - y;
    }

    /** The open sect task as its name and progress over target, or 无 without one. */
    private static String taskText(WorldSimSnapshot.MySect mine) {
        if (mine.taskName().isEmpty()) {
            return text("screen.myvillage.cultivation.world.none");
        }
        return text("world_sim.task." + mine.taskName() + ".name")
                + text("screen.myvillage.cultivation.world.task_value", mine.taskProgress(), mine.taskCount());
    }

    private int realmsBody(WorldCanvas c, WorldSimSnapshot.Overview o, int x, int y, int width) {
        List<WorldSimSnapshot.RealmCount> counts = o.livingByRealm();
        if (counts.isEmpty()) {
            c.text(text("screen.myvillage.cultivation.world.none"), x, y, width, PanelTheme.MUTED);
            return ROW;
        }
        int largest = WorldReadouts.largestCount(counts);
        int nameWidth = 0;
        int countWidth = 0;
        for (WorldSimSnapshot.RealmCount count : counts) {
            nameWidth = Math.max(nameWidth, c.width(WorldSimText.realm(count.realmId()).getString()));
            countWidth = Math.max(countWidth, c.width(Integer.toString(count.count())));
        }
        nameWidth = Math.min(nameWidth, width * 2 / 5);
        int barX = x + nameWidth + 6;
        int barWidth = x + width - countWidth - 6 - barX;
        int cursor = y;
        for (WorldSimSnapshot.RealmCount count : counts) {
            c.text(WorldSimText.realm(count.realmId()).getString(), x, cursor, nameWidth, PanelTheme.TEXT);
            if (barWidth > 8) {
                c.bar(barX, cursor + 1, barWidth, 6, WorldReadouts.share(count.count(), largest),
                        PanelTheme.JADE, PanelTheme.JADE_DARK);
            }
            c.textRight(Integer.toString(count.count()), x + width, cursor, countWidth, PanelTheme.MUTED);
            cursor += ROW + 2;
        }
        return cursor - 2 - y;
    }

    // --- people rows ----------------------------------------------------------------------------

    private int personList(WorldCanvas c, List<WorldSimSnapshot.PersonSummary> persons, int x, int y, int width) {
        if (persons.isEmpty()) {
            c.text(text("screen.myvillage.cultivation.world.none"), x, y, width, PanelTheme.MUTED);
            return ROW;
        }
        int cursor = y;
        for (WorldSimSnapshot.PersonSummary person : persons) {
            cursor += personRow(c, person, x, cursor, width) + 2;
        }
        return cursor - 2 - y;
    }

    /**
     * A clickable person: the 16-px portrait thumbnail, then name and title, stage, sect and rank;
     * the dead are muted and tagged. One-line rows centre the text on the thumbnail.
     */
    private int personRow(WorldCanvas c, WorldSimSnapshot.PersonSummary p, int x, int y, int width) {
        boolean oneLine = width >= ONE_LINE_PERSON;
        int height = Math.max(oneLine ? ROW + 2 : ROW * 2 + 2, THUMB + 2);
        c.link(x - 2, y - 1, x + width + 2, y + height - 1, toPerson(p.id()));
        c.image(p.portrait(), x, y, THUMB);
        int nameColor = p.alive() ? PanelTheme.GOLD_BRIGHT : PanelTheme.MUTED;
        int plainColor = p.alive() ? PanelTheme.TEXT : PanelTheme.MUTED;
        String stage = WorldSimText.stage(p.realmId(), p.stage()).getString();
        String standing = standing(p.sectId(), p.sectName(), p.rank());
        int tx = x + THUMB_ROOM;
        int tw = Math.max(1, width - THUMB_ROOM);
        int ty = oneLine ? y + (THUMB - 8) / 2 : y + 1;
        int nameWidth = oneLine ? tw * 2 / 5 : tw * 3 / 5;
        int used = nameLine(c, p, tx, ty, nameWidth, nameColor, plainColor);
        if (oneLine) {
            int stageX = tx + Math.max(used + 8, tw * 2 / 5);
            int standingWidth = Math.max(0, Math.min(c.width(standing), tw - (stageX - tx) - 50));
            int stageWidth = tx + tw - standingWidth - 8 - stageX;
            c.text(stage, stageX, ty, stageWidth, plainColor);
            c.textRight(standing, tx + tw, ty, standingWidth, PanelTheme.MUTED);
        } else {
            c.textRight(stage, tx + tw, ty, tw - used - 8, plainColor);
            c.text(standing, tx + 6, ty + ROW, tw - 6, PanelTheme.MUTED);
        }
        return height;
    }

    /** Name, Daoist title, and a 已故 chip for the dead; returns the width used. */
    private int nameLine(
            WorldCanvas c, WorldSimSnapshot.PersonSummary p, int x, int y, int width, int nameColor, int titleColor) {
        String deadTag = text("screen.myvillage.cultivation.world.dead_tag");
        int chipRoom = p.alive() ? 0 : c.chipWidth(deadTag) + 4;
        int nameWidth = c.text(p.name(), x, y, width - chipRoom, nameColor);
        int used = nameWidth;
        if (!p.title().isEmpty()) {
            used += 4 + c.text(p.title(), x + used + 4, y, width - chipRoom - used - 4, titleColor);
        }
        if (!p.alive()) {
            used += 4 + c.chip(deadTag, x + used + 4, y - 2, width - used - 4, PanelTheme.MUTED);
        }
        return used;
    }

    /** "青云宗外门弟子", or the rank alone for a rogue. */
    private static String standing(int sectId, String sectName, String rank) {
        Component rankText = WorldSimText.rank(rank);
        if (sectId < 0 || sectName.isEmpty()) {
            return rankText.getString();
        }
        return Component.translatable("commands.myvillage.world.person.of_sect", rankText, sectName).getString();
    }

    // --- 宗门 ---------------------------------------------------------------------------------

    private int sectList(WorldCanvas c, WorldSimSnapshot s, int x, int y, int width) {
        return c.card(x, y, width,
                tr("screen.myvillage.cultivation.world.card.sects", s.sects().size()),
                (bx, by, bw) -> {
                    if (s.sects().isEmpty()) {
                        c.text(text("screen.myvillage.cultivation.world.sects_empty"), bx, by, bw, PanelTheme.MUTED);
                        return ROW;
                    }
                    return sectRows(c, s.sects(), false, bx, by, bw);
                });
    }

    private int sectRows(
            WorldCanvas c, List<WorldSimSnapshot.SectSummary> sects, boolean withDistance, int x, int y, int width) {
        int cursor = y;
        for (int index = 0; index < sects.size(); index++) {
            if (index > 0) {
                c.fill(x, cursor - 2, x + width, cursor - 1, PanelTheme.DIVIDER);
            }
            cursor += sectRow(c, sects.get(index), withDistance, x, cursor, width) + 3;
        }
        return cursor - 3 - y;
    }

    /** A clickable sect: number and name with a gate (or 已灭) chip, then master, members, standing. */
    private int sectRow(
            WorldCanvas c, WorldSimSnapshot.SectSummary sect, boolean withDistance, int x, int y, int width) {
        int height = ROW * 2 + 3;
        c.link(x - 2, y - 1, x + width + 2, y + height - 1, toSect(sect.id()));
        int top = y + 2;
        String chip = sect.active() ? gateChip(sect) : WorldSimText.sectState("destroyed").getString();
        int chipColor = sect.active() && sect.gateRealized() ? PanelTheme.JADE : PanelTheme.MUTED;
        int chipWidth = Math.min(c.chipWidth(chip), width / 3);
        c.chip(chip, x + width - chipWidth, y, chipWidth, chipColor);
        c.text("#" + sect.id() + " " + sect.name(), x, top, width - chipWidth - 6,
                sect.active() ? PanelTheme.GOLD_BRIGHT : PanelTheme.MUTED);
        String master = sect.masterName().isEmpty() ? text("screen.myvillage.cultivation.world.none") : sect.masterName();
        String line;
        if (withDistance && sect.distance() >= 0) {
            line = text("screen.myvillage.cultivation.world.here_sect_line",
                    sect.distance(), bearingText(sect.bearing()), master, sect.memberCount());
        } else if (withDistance) {
            line = text("screen.myvillage.cultivation.world.here_sect_line_plain", master, sect.memberCount());
        } else {
            line = text("screen.myvillage.cultivation.world.sect_line",
                    sect.regionName(), master, sect.memberCount(), realmText(sect.topRealmId()), sect.prestige());
        }
        c.text(line, x + 6, top + ROW + 1, width - 6, sect.active() ? PanelTheme.MUTED : PanelTheme.FAINT);
        return height;
    }

    private int sectDetail(WorldCanvas c, WorldSimSnapshot s, int x, int y, int width) {
        WorldSimSnapshot.SectDetail d = s.sect();
        if (d == null) {
            return message(c, x, y, width, tr("screen.myvillage.cultivation.world.card.sect"),
                    Component.translatable("commands.myvillage.world.sect.not_found", "#" + s.query().id()),
                    PanelTheme.MUTED);
        }
        WorldCanvas.Body info = (cx, cy, cw) -> c.card(cx, cy, cw,
                tr("screen.myvillage.cultivation.world.card.sect"),
                (bx, by, bw) -> sectInfo(c, s, d, bx, by, bw));
        WorldCanvas.Body relations = (cx, cy, cw) -> c.card(cx, cy, cw,
                tr("screen.myvillage.cultivation.world.card.relations"),
                (bx, by, bw) -> sectRelations(c, d, bx, by, bw));
        WorldCanvas.Body members = (cx, cy, cw) -> c.card(cx, cy, cw,
                tr("screen.myvillage.cultivation.world.card.members"),
                (bx, by, bw) -> personList(c, s.persons(), bx, by, bw));
        return layout(c, x, y, width, List.of(info), List.of(relations, members), List.of(recent(c, s)));
    }

    private int sectInfo(
            WorldCanvas c, WorldSimSnapshot s, WorldSimSnapshot.SectDetail d, int x, int y, int width) {
        WorldSimSnapshot.SectSummary sect = d.summary();
        int cursor = y;
        String state = WorldSimText.sectState(sect.active() ? "active" : "destroyed").getString();
        int chipWidth = Math.min(c.chipWidth(state), width / 3);
        c.chip(state, x + width - chipWidth, cursor, chipWidth, sect.active() ? PanelTheme.JADE : PanelTheme.MUTED);
        c.text("#" + sect.id() + " " + sect.name(), x, cursor + 2, width - chipWidth - 6,
                sect.active() ? PanelTheme.GOLD_BRIGHT : PanelTheme.MUTED);
        cursor += PanelTheme.CHIP_HEIGHT + 2;

        cursor += pair(c, "screen.myvillage.cultivation.world.region", sect.regionName(), x, cursor, width);
        String founded = dateText(s, d.foundedDay())
                + (d.founderName().isEmpty() ? "" : " · " + d.founderName());
        cursor += linkPair(c, "screen.myvillage.cultivation.world.founded", founded,
                toPerson(d.founderId()), x, cursor, width);
        if (d.parentSectId() >= 0) {
            cursor += linkPair(c, "screen.myvillage.cultivation.world.parent", d.parentSectName(),
                    toSect(d.parentSectId()), x, cursor, width);
        }
        if (d.destroyedDay() >= 0) {
            cursor += pair(c, "screen.myvillage.cultivation.world.destroyed", dateText(s, d.destroyedDay()),
                    x, cursor, width);
        }
        cursor += linkPair(c, "screen.myvillage.cultivation.world.master", orNone(sect.masterName()),
                toPerson(d.masterId()), x, cursor, width);
        cursor += pair(c, "screen.myvillage.cultivation.world.members",
                Integer.toString(sect.memberCount()), x, cursor, width);
        cursor += pair(c, "screen.myvillage.cultivation.world.top_realm", realmText(sect.topRealmId()),
                x, cursor, width);
        cursor += pair(c, "screen.myvillage.cultivation.world.resources", Integer.toString(d.resources()),
                x, cursor, width);
        cursor += pair(c, "screen.myvillage.cultivation.world.prestige", Integer.toString(sect.prestige()),
                x, cursor, width);
        cursor += pair(c, "screen.myvillage.cultivation.world.sect_technique",
                orNone(d.signatureTechniqueName()), x, cursor, width);
        cursor += pair(c, "screen.myvillage.cultivation.world.sect_heritage",
                orNone(d.heritageName()), x, cursor, width);
        // Coordinates as the value, and the same gate chip as the sect list at the right.
        String gateChip = gateChip(sect);
        int gateChipWidth = Math.min(c.chipWidth(gateChip), width / 3);
        c.chip(gateChip, x + width - gateChipWidth, cursor, gateChipWidth,
                sect.gateRealized() ? PanelTheme.JADE : PanelTheme.MUTED);
        c.pair(text("screen.myvillage.cultivation.world.gate"), sect.gateX() + ", " + sect.gateZ(),
                x, cursor + 2, width - gateChipWidth - 6, PanelTheme.TEXT);
        cursor += PanelTheme.CHIP_HEIGHT;
        WorldSimSnapshot.MySect mine = s.mine();
        if (mine != null && mine.sectId() == sect.id()) {
            cursor += 2;
            c.pair(text("screen.myvillage.cultivation.world.mine_here"),
                    text("screen.myvillage.cultivation.world.mine_rank_value",
                            WorldSimText.rank(mine.rank()), dateText(s, mine.joinedDay()), signed(mine.standing())),
                    x, cursor, width, PanelTheme.JADE);
            cursor += ROW;
        }
        return cursor - y;
    }

    /** 北 / 东北 ...; "-" when the server sent no bearing. */
    private static String bearingText(String bearing) {
        return bearing == null || bearing.isEmpty()
                ? "-"
                : Component.translatableWithFallback("world_sim.bearing." + bearing, bearing).getString();
    }

    /** A standing with its sign: "+20", "0", "-40". */
    private static String signed(int value) {
        return (value > 0 ? "+" : "") + value;
    }

    /** 已立 / 未立 without the brackets the command keys carry. */
    private static String gateChip(WorldSimSnapshot.SectSummary sect) {
        return WorldReadouts.unbracket(text(sect.gateRealized()
                ? "commands.myvillage.world.gate.realized"
                : "commands.myvillage.world.gate.unrealized"));
    }

    private int sectRelations(WorldCanvas c, WorldSimSnapshot.SectDetail d, int x, int y, int width) {
        if (d.relations().isEmpty()) {
            c.text(text("screen.myvillage.cultivation.world.none"), x, y, width, PanelTheme.MUTED);
            return ROW;
        }
        int cursor = y;
        for (WorldSimSnapshot.SectRelation relation : d.relations()) {
            int height = PanelTheme.CHIP_HEIGHT + 2;
            c.link(x - 2, cursor - 1, x + width + 2, cursor + height - 1, toSect(relation.otherSectId()));
            String state = text("commands.myvillage.world.relation." + relation.state());
            int chipWidth = Math.min(c.chipWidth(state), width / 3);
            c.chip(state, x + width - chipWidth, cursor, chipWidth, WorldReadouts.relationColor(relation.state()));
            String value = (relation.value() > 0 ? "+" : "") + relation.value();
            int valueRight = x + width - chipWidth - 6;
            int valueWidth = c.textRight(value, valueRight, cursor + 2, 40, PanelTheme.MUTED);
            c.text(relation.otherSectName(), x, cursor + 2, valueRight - valueWidth - 6 - x, PanelTheme.GOLD);
            cursor += height + 1;
        }
        return cursor - 1 - y;
    }

    // --- 人物 ---------------------------------------------------------------------------------

    private int searchResults(WorldCanvas c, WorldSimSnapshot s, int x, int y, int width) {
        return c.card(x, y, width,
                tr("screen.myvillage.cultivation.world.card.search", s.query().text()),
                (bx, by, bw) -> {
                    if (s.persons().isEmpty()) {
                        c.text(text("screen.myvillage.cultivation.world.search_empty"), bx, by, bw, PanelTheme.MUTED);
                        return ROW;
                    }
                    int height = personList(c, s.persons(), bx, by, bw);
                    if (s.persons().size() >= WorldSimSnapshot.MAX_SEARCH) {
                        height += 3 + c.wrapped(
                                Component.translatable("commands.myvillage.world.person.more",
                                        WorldSimSnapshot.MAX_SEARCH),
                                bx, by + height + 3, bw, PanelTheme.FAINT);
                    }
                    return height;
                });
    }

    private int personDetail(
            WorldCanvas c, PanelContext context, WorldSimSnapshot s, int x, int y, int width) {
        WorldSimSnapshot.PersonDetail d = s.person();
        if (d == null) {
            return message(c, x, y, width, tr("screen.myvillage.cultivation.world.card.person"),
                    Component.translatable("commands.myvillage.world.person.not_found", "#" + s.query().id()),
                    PanelTheme.MUTED);
        }
        WorldCanvas.Body info = (cx, cy, cw) -> c.card(cx, cy, cw,
                tr("screen.myvillage.cultivation.world.card.person"),
                (bx, by, bw) -> personInfo(c, context, s, d, bx, by, bw));
        WorldCanvas.Body relations = (cx, cy, cw) -> c.card(cx, cy, cw,
                tr("screen.myvillage.cultivation.world.card.bonds"),
                (bx, by, bw) -> personRelations(c, d, bx, by, bw));
        return layout(c, x, y, width, List.of(info), List.of(relations), List.of(recent(c, s)));
    }

    private int personInfo(
            WorldCanvas c,
            PanelContext context,
            WorldSimSnapshot s,
            WorldSimSnapshot.PersonDetail d,
            int x,
            int y,
            int width) {
        WorldSimSnapshot.PersonSummary p = d.summary();
        // The portrait sits at the card's top-left; rows that start beside it are indented by
        // PORTRAIT_ROOM, the rest run full width below it. A card too narrow for a column beside
        // the portrait starts its rows under it.
        int portraitTop = y + 2;
        int portraitEnd = portraitTop + PORTRAIT + 2;
        c.image(p.portrait(), x, portraitTop, PORTRAIT);
        boolean beside = width - PORTRAIT_ROOM >= MIN_BESIDE_PORTRAIT;
        int cursor = beside ? portraitTop : portraitEnd;
        int rx = beside(cursor, portraitEnd, x);
        nameLine(c, p, rx, cursor, x + width - rx, p.alive() ? PanelTheme.GOLD_BRIGHT : PanelTheme.MUTED,
                p.alive() ? PanelTheme.TEXT : PanelTheme.MUTED);
        cursor += ROW + 2;

        String stage = WorldSimText.stage(p.realmId(), p.stage()).getString();
        rx = beside(cursor, portraitEnd, x);
        if (p.alive()) {
            double progress = WorldReadouts.progress(d.progress());
            c.pair(stage, WorldReadouts.percent(d.progress()) + "%", rx, cursor, x + width - rx, PanelTheme.TEXT);
            c.bar(rx, cursor + ROW, x + width - rx, 5, progress, PanelTheme.JADE, PanelTheme.JADE_DARK);
            cursor += PanelTheme.METER_HEIGHT + 3;
        } else {
            cursor += pair(c, "screen.myvillage.cultivation.world.realm", stage, rx, cursor, x + width - rx);
        }

        if (!d.rootGrade().isEmpty()) {
            rx = beside(cursor, portraitEnd, x);
            cursor += pair(c, "screen.myvillage.cultivation.world.root",
                    WorldSimText.rootGrade(d.rootGrade()).getString(), rx, cursor, x + width - rx);
        }
        if (d.root().size() == ELEMENTS.size()) {
            rx = beside(cursor, portraitEnd, x);
            cursor += rootBars(c, context, d.root(), rx, cursor, x + width - rx) + 2;
        }

        rx = beside(cursor, portraitEnd, x);
        cursor += pair(c, "screen.myvillage.cultivation.world.age",
                text("screen.myvillage.cultivation.world.age_value",
                        WorldReadouts.age(p.alive(), d.birthDay(), d.deathDay(), s.day(), s.daysPerYear())),
                rx, cursor, x + width - rx);
        rx = beside(cursor, portraitEnd, x);
        cursor += linkPair(c, "screen.myvillage.cultivation.world.lineage", orNone(d.masterName()),
                toPerson(d.masterId()), rx, cursor, x + width - rx);
        rx = beside(cursor, portraitEnd, x);
        cursor += linkPair(c, "screen.myvillage.cultivation.world.sect",
                standing(p.sectId(), p.sectName(), p.rank()), toSect(p.sectId()), rx, cursor, x + width - rx);
        if (p.alive()) {
            String where = text("screen.myvillage.cultivation.world.whereabouts_value",
                    orNone(d.regionName()), WorldSimText.status(d.status()));
            rx = beside(cursor, portraitEnd, x);
            cursor += pair(c, "screen.myvillage.cultivation.world.whereabouts", where, rx, cursor, x + width - rx);
            rx = beside(cursor, portraitEnd, x);
            cursor += pair(c, "screen.myvillage.cultivation.world.technique", orNone(d.techniqueName()),
                    rx, cursor, x + width - rx);
            if (d.injury() > 0) {
                rx = beside(cursor, portraitEnd, x);
                c.pair(text("screen.myvillage.cultivation.world.injury"), Integer.toString(d.injury()),
                        rx, cursor, x + width - rx, PanelTheme.RED);
                cursor += ROW + 1;
            }
        } else {
            rx = beside(cursor, portraitEnd, x);
            cursor += pair(c, "screen.myvillage.cultivation.world.died", dateText(s, d.deathDay()),
                    rx, cursor, x + width - rx);
            rx = beside(cursor, portraitEnd, x);
            c.pair(text("screen.myvillage.cultivation.world.death_cause"),
                    WorldSimText.cause(d.deathCause()).getString(), rx, cursor, x + width - rx, PanelTheme.RED);
            cursor += ROW + 1;
            if (d.killerId() >= 0 || !d.killerName().isEmpty()) {
                rx = beside(cursor, portraitEnd, x);
                cursor += linkPair(c, "screen.myvillage.cultivation.world.killer", orNone(d.killerName()),
                        toPerson(d.killerId()), rx, cursor, x + width - rx);
            }
        }
        return Math.max(cursor - 1 - y, portraitTop + PORTRAIT - y);
    }

    /** Where a person-card row starting at {@code cursor} begins: beside the portrait, or at {@code x}. */
    private static int beside(int cursor, int portraitEnd, int x) {
        return cursor < portraitEnd ? x + PORTRAIT_ROOM : x;
    }

    /**
     * The root as one stacked bar, metal to earth, each segment the element's share, with a
     * legend under it: name in the element's colour, whole percent muted. When the legend is too
     * wide it drops the percent signs, then the numbers.
     */
    private int rootBars(WorldCanvas c, PanelContext context, List<Integer> root, int x, int y, int width) {
        int count = ELEMENTS.size();
        int[] colors = new int[count];
        String[] names = new String[count];
        String[] numbers = new String[count];
        String[] bare = new String[count];
        for (int index = 0; index < count; index++) {
            colors[index] = context.elementColor(ELEMENTS.get(index));
            names[index] = text(ELEMENT_KEYS.get(index));
            int percent = WorldReadouts.rootPercentWhole(root.get(index) == null ? 0 : root.get(index));
            bare[index] = Integer.toString(percent);
            numbers[index] = percent + "%";
        }

        c.fill(x, y, x + width, y + 5, PanelTheme.BAR_TRACK);
        int inner = Math.max(0, width - 2);
        int[] segments = WorldReadouts.rootSegments(root, inner);
        int from = x + 1;
        for (int index = 0; index < count; index++) {
            if (segments[index] > 0) {
                c.fill(from, y + 1, from + segments[index], y + 4, colors[index]);
            }
            from += segments[index];
        }
        if (!c.measuring()) {
            c.graphics.renderOutline(x, y, width, 5, PanelTheme.CARD_BORDER);
        }

        int legendY = y + 8;
        int nameGap = 1;
        int entryGap = c.width("  ");
        String[] shown = numbers;
        if (legendWidth(c, names, numbers, nameGap, entryGap) > width) {
            shown = legendWidth(c, names, bare, nameGap, entryGap) <= width ? bare : null;
        }
        int cursorX = x;
        for (int index = 0; index < count; index++) {
            int room = x + width - cursorX;
            if (room <= 0) {
                break;
            }
            cursorX += c.text(names[index], cursorX, legendY, room, colors[index]);
            if (shown != null) {
                cursorX += nameGap;
                cursorX += c.text(shown[index], cursorX, legendY, x + width - cursorX, PanelTheme.MUTED);
            }
            cursorX += entryGap;
        }
        return 8 + ROW;
    }

    private static int legendWidth(WorldCanvas c, String[] names, String[] numbers, int nameGap, int entryGap) {
        int total = 0;
        for (int index = 0; index < names.length; index++) {
            total += c.width(names[index]) + nameGap + c.width(numbers[index]);
        }
        return total + entryGap * (names.length - 1);
    }

    private int personRelations(WorldCanvas c, WorldSimSnapshot.PersonDetail d, int x, int y, int width) {
        Map<String, List<WorldSimSnapshot.PersonRelation>> groups = WorldReadouts.groupRelations(d.relations());
        if (groups.isEmpty()) {
            c.text(text("screen.myvillage.cultivation.world.none"), x, y, width, PanelTheme.MUTED);
            return ROW;
        }
        int labelWidth = 0;
        for (String kind : groups.keySet()) {
            labelWidth = Math.max(labelWidth, c.width(kindText(kind)));
        }
        labelWidth = Math.min(labelWidth, width / 4);
        int chipsX = x + labelWidth + 6;
        int lineHeight = PanelTheme.CHIP_HEIGHT + 2;
        int cursor = y;
        for (Map.Entry<String, List<WorldSimSnapshot.PersonRelation>> group : groups.entrySet()) {
            c.text(kindText(group.getKey()), x, cursor + 2, labelWidth, PanelTheme.MUTED);
            int color = WorldReadouts.kindColor(group.getKey());
            int chipX = chipsX;
            for (WorldSimSnapshot.PersonRelation relation : group.getValue()) {
                String name = relation.otherName().isEmpty() ? "#" + relation.otherId() : relation.otherName();
                int room = x + width - chipsX;
                int chipWidth = Math.min(c.chipWidth(name), room);
                if (chipX > chipsX && chipX + chipWidth > x + width) {
                    chipX = chipsX;
                    cursor += lineHeight;
                }
                Runnable action = toPerson(relation.otherId());
                c.link(chipX, cursor, chipX + chipWidth, cursor + PanelTheme.CHIP_HEIGHT, action);
                chipX += c.chip(name, chipX, cursor, chipWidth, color) + 3;
            }
            cursor += lineHeight + 1;
        }
        return cursor - 3 - y;
    }

    private static String kindText(String kind) {
        return Component.translatableWithFallback("screen.myvillage.cultivation.world.kind." + kind, kind).getString();
    }

    // --- 纪事 ---------------------------------------------------------------------------------

    private int chronicle(WorldCanvas c, WorldSimSnapshot s, int x, int y, int width) {
        return c.card(x, y, width,
                tr("screen.myvillage.cultivation.world.card.chronicle", s.events().size()),
                (bx, by, bw) -> eventList(c, s, bx, by, bw));
    }

    /** The 近事 card of a sect, person, or region. */
    private WorldCanvas.Body recent(WorldCanvas c, WorldSimSnapshot s) {
        return (cx, cy, cw) -> c.card(cx, cy, cw,
                tr("screen.myvillage.cultivation.world.card.recent"),
                (bx, by, bw) -> eventList(c, s, bx, by, bw));
    }

    private int eventList(WorldCanvas c, WorldSimSnapshot s, int x, int y, int width) {
        if (s.events().isEmpty()) {
            c.text(text("screen.myvillage.cultivation.world.events_empty"), x, y, width, PanelTheme.MUTED);
            return ROW;
        }
        int cursor = y;
        for (WorldSimSnapshot.EventLine event : WorldReadouts.newestFirst(s.events())) {
            cursor += eventRow(c, s, event, x, cursor, width) + 3;
        }
        return cursor - 3 - y;
    }

    /**
     * One chronicle line: a gold diamond for a major event, the date in muted brackets, the event
     * text wrapped beside it, and the cause it answers on the line below. Clicking opens the subject.
     */
    private int eventRow(
            WorldCanvas c, WorldSimSnapshot s, WorldSimSnapshot.EventLine event, int x, int y, int width) {
        WorldCanvas.Body body = (bx, by, bw) -> eventBody(c, s, event, bx, by, bw);
        int height = c.measure(() -> body.draw(x, y, width));
        c.link(x - 2, y - 1, x + width + 2, y + height, toPerson(event.subjectId()));
        body.draw(x, y, width);
        return height;
    }

    private int eventBody(
            WorldCanvas c, WorldSimSnapshot s, WorldSimSnapshot.EventLine event, int x, int y, int width) {
        int marker = 8;
        int color = WorldReadouts.importanceColor(event.importance());
        if (event.importance() >= 3) {
            c.diamond(x + 2, y + 3, 2, PanelTheme.GOLD);
        }
        String date = "[" + dateText(s, event.day()) + "]";
        int dateWidth = c.text(date, x + marker, y, width - marker, PanelTheme.MUTED);
        int textX = x + marker + dateWidth + 4;
        int textWidth = x + width - textX;
        int cursor = y;
        if (textWidth < MIN_EVENT_TEXT) {
            cursor += ROW;
            textX = x + marker;
            textWidth = width - marker;
        }
        cursor += c.wrapped(eventText(event), textX, cursor, textWidth, color);
        WorldSimSnapshot.EventLine cause = s.causeOf(event);
        if (cause != null) {
            cursor += c.wrapped(
                    Component.translatable("screen.myvillage.cultivation.world.cause", eventText(cause)),
                    x + marker, cursor, width - marker, PanelTheme.FAINT);
        }
        return cursor - y;
    }

    /** {@code Component.translatable(textKey, params)}; a param starting with {@code @} is a key itself. */
    private static Component eventText(WorldSimSnapshot.EventLine event) {
        Object[] args = new Object[event.params().size()];
        for (int index = 0; index < args.length; index++) {
            String param = event.params().get(index);
            args[index] = param.startsWith("@") ? Component.translatable(param.substring(1)) : Component.literal(param);
        }
        return Component.translatable(event.textKey(), args);
    }

    // --- 此地 ---------------------------------------------------------------------------------

    private int here(WorldCanvas c, WorldSimSnapshot s, int x, int y, int width) {
        WorldSimSnapshot.Region region = s.region();
        if (region == null) {
            return message(c, x, y, width, tr("screen.myvillage.cultivation.world.card.region"),
                    Component.translatable("commands.myvillage.world.here.outside"), PanelTheme.MUTED);
        }
        WorldCanvas.Body info = (cx, cy, cw) -> c.card(cx, cy, cw,
                tr("screen.myvillage.cultivation.world.card.region"),
                (bx, by, bw) -> regionInfo(c, region, bx, by, bw));
        WorldCanvas.Body seated = (cx, cy, cw) -> c.card(cx, cy, cw,
                tr("screen.myvillage.cultivation.world.card.seated"),
                (bx, by, bw) -> {
                    if (s.sects().isEmpty()) {
                        return c.wrapped(Component.translatable("commands.myvillage.world.here.no_sects"),
                                bx, by, bw, PanelTheme.MUTED);
                    }
                    return sectRows(c, s.sects(), true, bx, by, bw);
                });
        WorldCanvas.Body present = (cx, cy, cw) -> c.card(cx, cy, cw,
                tr("screen.myvillage.cultivation.world.card.present"),
                (bx, by, bw) -> personList(c, s.persons(), bx, by, bw));
        return layout(c, x, y, width, List.of(info, seated), List.of(present), List.of(recent(c, s)));
    }

    private int regionInfo(WorldCanvas c, WorldSimSnapshot.Region region, int x, int y, int width) {
        int cursor = y;
        String admits = text(region.admitsSects()
                ? "screen.myvillage.cultivation.world.admits_sects"
                : "screen.myvillage.cultivation.world.no_sects_allowed");
        int chipWidth = Math.min(c.chipWidth(admits), width / 2);
        c.chip(admits, x + width - chipWidth, cursor, chipWidth,
                region.admitsSects() ? PanelTheme.JADE : PanelTheme.MUTED);
        c.text(region.displayName(), x, cursor + 2, width - chipWidth - 6, PanelTheme.GOLD_BRIGHT);
        cursor += PanelTheme.CHIP_HEIGHT + 2;
        cursor += pair(c, "screen.myvillage.cultivation.world.region_tier", Integer.toString(region.tier()),
                x, cursor, width);
        cursor += pair(c, "screen.myvillage.cultivation.world.qi", region.qiLo() + " - " + region.qiHi(),
                x, cursor, width);
        cursor += pair(c, "screen.myvillage.cultivation.world.danger",
                region.dangerLo() + " - " + region.dangerHi(), x, cursor, width);
        c.pair(text("screen.myvillage.cultivation.world.cultivators"),
                text("screen.myvillage.cultivation.world.count_value", region.livingCount()),
                x, cursor, width, PanelTheme.TEXT);
        cursor += ROW;
        return cursor - y;
    }

    // --- small helpers ------------------------------------------------------------------------

    /** A plain label/value row; returns the height it takes. */
    private int pair(WorldCanvas c, String labelKey, String value, int x, int y, int width) {
        c.pair(text(labelKey), value, x, y, width, PanelTheme.TEXT);
        return ROW + 1;
    }

    /** A label/value row that opens {@code action} when clicked (plain when there is none). */
    private int linkPair(WorldCanvas c, String labelKey, String value, Runnable action, int x, int y, int width) {
        c.link(x - 2, y - 1, x + width + 2, y + ROW, action);
        c.pair(text(labelKey), value, x, y, width, action == null ? PanelTheme.TEXT : PanelTheme.GOLD);
        return ROW + 1;
    }

    private static String dateText(WorldSimSnapshot s, long day) {
        return WorldSimText.date(WorldReadouts.date(day, s.prehistoryDays(), s.daysPerYear())).getString();
    }

    private static String realmText(String realmId) {
        return realmId == null || realmId.isEmpty()
                ? text("screen.myvillage.cultivation.world.none")
                : WorldSimText.realm(realmId).getString();
    }

    private static String orNone(String value) {
        return value == null || value.isEmpty() ? text("screen.myvillage.cultivation.world.none") : value;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
