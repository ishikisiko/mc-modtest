package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.sim.runtime.net.WorldSimSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldReadoutsTest {
    private static final int DAYS_PER_YEAR = 12;

    @Test
    void ageCountsWholeYearsToTodayOrToDeath() {
        assertEquals(0, WorldReadouts.age(true, 100, -1, 111, DAYS_PER_YEAR));
        assertEquals(1, WorldReadouts.age(true, 100, -1, 112, DAYS_PER_YEAR));
        assertEquals(5, WorldReadouts.age(true, 100, -1, 160, DAYS_PER_YEAR));
        // The dead stop ageing at the day of death.
        assertEquals(2, WorldReadouts.age(false, 100, 124, 1_000, DAYS_PER_YEAR));
        // Negative spans and a broken year length never go below zero or divide by zero.
        assertEquals(0, WorldReadouts.age(true, 200, -1, 100, DAYS_PER_YEAR));
        assertEquals(60, WorldReadouts.age(true, 100, -1, 160, 0));
    }

    @Test
    void datesFollowTheLedgerEraAndCountDaysFromOne() {
        long prehistory = 120;
        assertEquals(1, WorldReadouts.date(120, prehistory, DAYS_PER_YEAR).year());
        assertEquals(1, WorldReadouts.dayOfYear(120, prehistory, DAYS_PER_YEAR));
        assertEquals(12, WorldReadouts.dayOfYear(131, prehistory, DAYS_PER_YEAR));
        assertEquals(2, WorldReadouts.date(132, prehistory, DAYS_PER_YEAR).year());
        assertEquals(1, WorldReadouts.dayOfYear(132, prehistory, DAYS_PER_YEAR));
        assertTrue(WorldReadouts.date(119, prehistory, DAYS_PER_YEAR).beforeEra());
        assertEquals(1, WorldReadouts.date(119, prehistory, DAYS_PER_YEAR).year());
        assertEquals(12, WorldReadouts.dayOfYear(119, prehistory, DAYS_PER_YEAR));
    }

    @Test
    void sharesAndProgressClamp() {
        assertEquals(0.0D, WorldReadouts.share(5, 0));
        assertEquals(0.5D, WorldReadouts.share(5, 10));
        assertEquals(1.0D, WorldReadouts.share(15, 10));
        assertEquals(0.0D, WorldReadouts.progress(Double.NaN));
        assertEquals(0.0D, WorldReadouts.progress(-0.2D));
        assertEquals(0.25D, WorldReadouts.progress(0.25D));
        assertEquals(1.0D, WorldReadouts.progress(3.0D));
        assertEquals(99, WorldReadouts.percent(0.999D));
        assertEquals(100, WorldReadouts.percent(1.0D));
        assertEquals(29, WorldReadouts.percent(0.29D));
    }

    @Test
    void largestRealmCountScalesTheBars() {
        List<WorldSimSnapshot.RealmCount> counts = List.of(
                new WorldSimSnapshot.RealmCount("qi_refining", 120),
                new WorldSimSnapshot.RealmCount("foundation_establishment", 30),
                new WorldSimSnapshot.RealmCount("golden_core", 0));
        assertEquals(120, WorldReadouts.largestCount(counts));
        assertEquals(0, WorldReadouts.largestCount(List.of()));
        assertEquals(0.25D, WorldReadouts.share(30, WorldReadouts.largestCount(counts)));
    }

    @Test
    void rootBasisPointsBecomeBarWidths() {
        assertArrayEquals(
                new int[] {40, 0, 20, 10, 30},
                WorldReadouts.rootWidths(List.of(4000, 0, 2000, 1000, 3000), 100));
        assertEquals(0, WorldReadouts.rootWidth(5000, 0));
        assertEquals(37, WorldReadouts.rootWidth(10_000, 37));
        assertEquals(37, WorldReadouts.rootWidth(12_000, 37));
        assertEquals("12.5%", WorldReadouts.rootPercent(1250));
        assertEquals("100.0%", WorldReadouts.rootPercent(10_000));
        assertEquals("0.0%", WorldReadouts.rootPercent(-4));
    }

    @Test
    void relationsGroupInDisplayOrderWithUnknownKindsLast() {
        List<WorldSimSnapshot.PersonRelation> relations = List.of(
                new WorldSimSnapshot.PersonRelation(7, "仇甲", "enemy", -60),
                new WorldSimSnapshot.PersonRelation(8, "道侣", "partner", 90),
                new WorldSimSnapshot.PersonRelation(3, "徒乙", "disciple", 40),
                new WorldSimSnapshot.PersonRelation(4, "徒丙", "disciple", 30),
                new WorldSimSnapshot.PersonRelation(1, "师父", "master", 50));
        Map<String, List<WorldSimSnapshot.PersonRelation>> groups = WorldReadouts.groupRelations(relations);
        assertEquals(List.of("master", "disciple", "enemy", "partner"), List.copyOf(groups.keySet()));
        assertEquals(List.of(3, 4), groups.get("disciple").stream()
                .map(WorldSimSnapshot.PersonRelation::otherId).toList());
        assertTrue(WorldReadouts.groupRelations(List.of()).isEmpty());
    }

    @Test
    void chronicleReadsNewestFirstWithoutTouchingTheSnapshot() {
        WorldSimSnapshot.EventLine older = new WorldSimSnapshot.EventLine(1, 10, 2, -1, "k", List.of(), -1);
        WorldSimSnapshot.EventLine newer = new WorldSimSnapshot.EventLine(2, 20, 3, 5, "k", List.of(), 1);
        List<WorldSimSnapshot.EventLine> sent = List.of(older, newer);
        assertEquals(List.of(newer, older), WorldReadouts.newestFirst(sent));
        assertEquals(List.of(older, newer), sent);
    }

    @Test
    void coloursFollowImportanceRelationStateAndKind() {
        assertEquals(PanelTheme.GOLD_BRIGHT, WorldReadouts.importanceColor(3));
        assertEquals(PanelTheme.TEXT, WorldReadouts.importanceColor(2));
        assertEquals(PanelTheme.MUTED, WorldReadouts.importanceColor(1));
        assertEquals(PanelTheme.RED, WorldReadouts.relationColor("war"));
        assertEquals(PanelTheme.AMBER, WorldReadouts.relationColor("feud"));
        assertEquals(PanelTheme.MUTED, WorldReadouts.relationColor("none"));
        assertEquals(PanelTheme.RED, WorldReadouts.kindColor("enemy"));
        assertEquals(PanelTheme.MUTED, WorldReadouts.kindColor("partner"));
    }

    @Test
    void unbracketStripsOnePairOfBrackets() {
        assertEquals("已立", WorldReadouts.unbracket("（已立）"));
        assertEquals("built", WorldReadouts.unbracket("(built)"));
        assertEquals("plain", WorldReadouts.unbracket(" plain "));
        assertEquals("(", WorldReadouts.unbracket("("));
    }
}
