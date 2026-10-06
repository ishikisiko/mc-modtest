package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.data.TechniqueCategory;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The 功法 page's arithmetic without Minecraft rendering: which group a learned technique goes in,
 * the order inside a group, the grade name, a heritage position, and how chips flow into rows.
 */
final class TechniqueShelf {
    /** Group order on the page: 心法, 绝技, 身法, 炼体. */
    static final List<TechniqueCategory> ORDER = List.of(
            TechniqueCategory.CORE, TechniqueCategory.ACTIVE, TechniqueCategory.MOVEMENT, TechniqueCategory.BODY);

    private static final String GROUP_KEY = "screen.myvillage.cultivation.technique_group.";
    private static final String UNRESOLVED_GROUP_KEY = GROUP_KEY + "unresolved";
    private static final String GRADE_NAME_KEY = "screen.myvillage.cultivation.grade_name.";

    /** Highest grade first, then by name, then by id so the order never depends on map order. */
    static final Comparator<Item> ITEM_ORDER = Comparator.comparingInt(Item::grade).reversed()
            .thenComparing(Item::name)
            .thenComparing(item -> item.id().toString());

    private TechniqueShelf() {
    }

    /**
     * A learned technique as the page sorts it. {@code category} is null when the definition is
     * not in the synchronized registry; such an item goes in a last group of its own.
     */
    record Item(ResourceLocation id, TechniqueCategory category, int grade, String name) {
        Item {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }
    }

    /** One card on the page; {@code category} is null for the unresolved group. */
    record Group(TechniqueCategory category, List<Item> items) {
        Group {
            items = List.copyOf(items);
        }

        String titleKey() {
            return category == null ? UNRESOLVED_GROUP_KEY : GROUP_KEY + category.serializedName();
        }
    }

    /** The groups in {@link #ORDER}, each sorted by {@link #ITEM_ORDER}; empty groups are left out. */
    static List<Group> group(Collection<Item> items) {
        List<Group> groups = new ArrayList<>(ORDER.size() + 1);
        for (TechniqueCategory category : ORDER) {
            addGroup(groups, category, items);
        }
        addGroup(groups, null, items);
        return List.copyOf(groups);
    }

    private static void addGroup(List<Group> groups, TechniqueCategory category, Collection<Item> items) {
        List<Item> members = new ArrayList<>();
        for (Item item : items) {
            if (item.category() == category) {
                members.add(item);
            }
        }
        if (!members.isEmpty()) {
            members.sort(ITEM_ORDER);
            groups.add(new Group(category, members));
        }
    }

    /** The key naming a grade (0 凡 .. 4 天), or null for a grade outside that range. */
    static String gradeNameKey(int grade) {
        return grade >= 0 && grade <= 4 ? GRADE_NAME_KEY + grade : null;
    }

    /** "2/4" for the second of four techniques in a chain; empty when the index is not in it. */
    static String chainPosition(int index, int size) {
        return index >= 0 && index < size ? (index + 1) + "/" + size : "";
    }

    /**
     * Flows chips into rows: each chip in order goes on the current row when it fits beside the
     * chips already there, otherwise it starts the next row. The first row is {@code firstRow}
     * wide (it shares its line with the entry's action), later rows {@code row}; the caller cuts
     * any chip wider than {@code row}. Returns each chip's row, or -1 for chips past
     * {@code maxRows}; once a chip is dropped every later one is.
     */
    static int[] chipRows(int[] widths, int firstRow, int row, int gap, int maxRows) {
        int[] rows = new int[widths.length];
        int current = 0;
        int used = 0;
        boolean dropped = false;
        for (int index = 0; index < widths.length; index++) {
            int width = widths[index];
            if (!dropped) {
                int limit = current == 0 ? firstRow : row;
                if ((used == 0 ? width : used + gap + width) > limit) {
                    current++;
                    used = 0;
                    dropped = current >= maxRows;
                }
            }
            if (dropped) {
                rows[index] = -1;
                continue;
            }
            rows[index] = current;
            used = used == 0 ? width : used + gap + width;
        }
        return rows;
    }

    /** How many rows {@link #chipRows} used; at least one, so an entry keeps its shape. */
    static int rowCount(int[] rows) {
        int count = 1;
        for (int row : rows) {
            count = Math.max(count, row + 1);
        }
        return count;
    }
}
