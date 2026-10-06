package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.sim.SimDate;
import com.example.myvillage.sim.runtime.net.WorldSimSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Display arithmetic for the 天下 page, without Minecraft rendering types. It derives nothing the
 * server did not send: ages, dates, groupings, and bar widths are read straight off a snapshot.
 */
public final class WorldReadouts {
    /** Relation kinds in display order; any other kind follows in the order it first appears. */
    public static final List<String> RELATION_ORDER = List.of("master", "disciple", "friend", "enemy");
    /** A five-element root is basis points over this total. */
    public static final int ROOT_TOTAL = 10_000;

    private WorldReadouts() {
    }

    /** Whole sim years from {@code fromDay} to {@code toDay}, floored; never negative. */
    public static long years(long fromDay, long toDay, int daysPerYear) {
        return Math.max(0L, Math.floorDiv(toDay - fromDay, Math.max(1, daysPerYear)));
    }

    /** A person's age: to today while living, to the day of death once dead. */
    public static long age(boolean alive, long birthDay, long deathDay, long today, int daysPerYear) {
        return years(birthDay, alive || deathDay < 0 ? today : deathDay, daysPerYear);
    }

    /** The ledger's display date for a sim day; a non-positive year length counts as one day. */
    public static SimDate date(long day, long prehistoryDays, int daysPerYear) {
        return SimDate.of(day, prehistoryDays, Math.max(1, daysPerYear));
    }

    /** 1-based day within the 启元 year that {@code day} falls in. */
    public static int dayOfYear(long day, long prehistoryDays, int daysPerYear) {
        return date(day, prehistoryDays, daysPerYear).dayOfYear() + 1;
    }

    /** {@code value / max} clamped to 0..1; 0 when {@code max} is not positive. */
    public static double share(long value, long max) {
        if (max <= 0 || value <= 0) {
            return 0.0D;
        }
        return Math.min(1.0D, value / (double) max);
    }

    /** A cultivation progress value clamped to 0..1 (NaN reads as empty). */
    public static double progress(double value) {
        if (!(value > 0.0D)) {
            return 0.0D;
        }
        return Math.min(1.0D, value);
    }

    /** Whole percent of a 0..1 progress value, rounded down so a full bar means truly full. */
    public static int percent(double value) {
        return (int) Math.floor(progress(value) * 100.0D + 1.0E-9D);
    }

    /** The largest count in a realm tally, 0 when empty. */
    public static int largestCount(List<WorldSimSnapshot.RealmCount> counts) {
        int largest = 0;
        for (WorldSimSnapshot.RealmCount count : counts) {
            largest = Math.max(largest, count.count());
        }
        return largest;
    }

    /** Filled pixels of a bar {@code width} wide for {@code basisPoints} out of {@link #ROOT_TOTAL}. */
    public static int rootWidth(int basisPoints, int width) {
        if (width <= 0) {
            return 0;
        }
        return (int) Math.round(width * share(basisPoints, ROOT_TOTAL));
    }

    /** One filled width per element in a five-element root, each against the same bar width. */
    public static int[] rootWidths(List<Integer> basisPoints, int width) {
        int[] widths = new int[basisPoints.size()];
        for (int index = 0; index < widths.length; index++) {
            Integer value = basisPoints.get(index);
            widths[index] = rootWidth(value == null ? 0 : value, width);
        }
        return widths;
    }

    /** Basis points as a percent string with one decimal ("12.5%"). */
    public static String rootPercent(int basisPoints) {
        int clamped = Math.max(0, Math.min(ROOT_TOTAL, basisPoints));
        return clamped / 100 + "." + clamped % 100 / 10 + "%";
    }

    /**
     * Relations grouped by kind: master, disciple, friend, and enemy first, then any other kind in
     * the order it first appears. Within a group the server's order is kept.
     */
    public static Map<String, List<WorldSimSnapshot.PersonRelation>> groupRelations(
            List<WorldSimSnapshot.PersonRelation> relations) {
        Map<String, List<WorldSimSnapshot.PersonRelation>> seen = new LinkedHashMap<>();
        for (WorldSimSnapshot.PersonRelation relation : relations) {
            String kind = relation.kind() == null || relation.kind().isBlank() ? "other" : relation.kind();
            seen.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(relation);
        }
        Map<String, List<WorldSimSnapshot.PersonRelation>> ordered = new LinkedHashMap<>();
        for (String kind : RELATION_ORDER) {
            List<WorldSimSnapshot.PersonRelation> group = seen.remove(kind);
            if (group != null) {
                ordered.put(kind, List.copyOf(group));
            }
        }
        seen.forEach((kind, group) -> ordered.put(kind, List.copyOf(group)));
        return ordered;
    }

    /** Chronicle lines newest first (the snapshot sends them oldest first). */
    public static List<WorldSimSnapshot.EventLine> newestFirst(List<WorldSimSnapshot.EventLine> events) {
        List<WorldSimSnapshot.EventLine> copy = new ArrayList<>(events);
        Collections.reverse(copy);
        return copy;
    }

    /** Text color for a chronicle line: major gold, notable plain, minor muted. */
    public static int importanceColor(int importance) {
        if (importance >= 3) {
            return PanelTheme.GOLD_BRIGHT;
        }
        return importance == 2 ? PanelTheme.TEXT : PanelTheme.MUTED;
    }

    /** Chip color for a sect relation state: war red, feud amber, anything else muted. */
    public static int relationColor(String state) {
        if ("war".equals(state)) {
            return PanelTheme.RED;
        }
        return "feud".equals(state) ? PanelTheme.AMBER : PanelTheme.MUTED;
    }

    /** Chip color for a person-relation kind. */
    public static int kindColor(String kind) {
        return switch (kind) {
            case "master", "disciple" -> PanelTheme.GOLD;
            case "friend" -> PanelTheme.JADE;
            case "enemy" -> PanelTheme.RED;
            default -> PanelTheme.MUTED;
        };
    }

    /** Strips one pair of surrounding brackets, ASCII or full-width: "（已立）" reads "已立" in a chip. */
    public static String unbracket(String text) {
        String value = text.strip();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '(' && last == ')') || (first == '（' && last == '）')) {
                return value.substring(1, value.length() - 1).strip();
            }
        }
        return value;
    }
}
