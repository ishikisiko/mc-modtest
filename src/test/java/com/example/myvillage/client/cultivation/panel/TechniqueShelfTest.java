package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.data.TechniqueCategory;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechniqueShelfTest {
    private static TechniqueShelf.Item item(String id, TechniqueCategory category, int grade, String name) {
        return new TechniqueShelf.Item(ResourceLocation.fromNamespaceAndPath("myvillage", id), category, grade, name);
    }

    private static List<String> ids(TechniqueShelf.Group group) {
        return group.items().stream().map(item -> item.id().getPath()).toList();
    }

    @Test
    void groupsFollowCoreActiveMovementBodyAndSkipEmptyOnes() {
        List<TechniqueShelf.Group> groups = TechniqueShelf.group(List.of(
                item("iron_body", TechniqueCategory.BODY, 1, "铁骨功"),
                item("basic_breathing", TechniqueCategory.CORE, 0, "基础吐纳"),
                item("sword_art", TechniqueCategory.ACTIVE, 2, "庚金剑诀")));
        assertEquals(3, groups.size());
        assertEquals(TechniqueCategory.CORE, groups.get(0).category());
        assertEquals(TechniqueCategory.ACTIVE, groups.get(1).category());
        assertEquals(TechniqueCategory.BODY, groups.get(2).category());
        assertEquals("screen.myvillage.cultivation.technique_group.core", groups.get(0).titleKey());
        assertEquals("screen.myvillage.cultivation.technique_group.active", groups.get(1).titleKey());
        assertEquals("screen.myvillage.cultivation.technique_group.body", groups.get(2).titleKey());
        assertEquals(List.of(TechniqueCategory.CORE, TechniqueCategory.ACTIVE, TechniqueCategory.MOVEMENT,
                TechniqueCategory.BODY), TechniqueShelf.ORDER);
        assertTrue(TechniqueShelf.group(List.of()).isEmpty());
    }

    @Test
    void entriesSortByGradeDescendingThenNameThenId() {
        List<TechniqueShelf.Group> groups = TechniqueShelf.group(List.of(
                item("b", TechniqueCategory.CORE, 1, "乙"),
                item("basic_breathing", TechniqueCategory.CORE, 0, "基础吐纳"),
                item("heaven", TechniqueCategory.CORE, 4, "太白剑经"),
                item("a", TechniqueCategory.CORE, 1, "甲"),
                item("twin_two", TechniqueCategory.CORE, 2, "同名"),
                item("twin_one", TechniqueCategory.CORE, 2, "同名")));
        assertEquals(1, groups.size());
        // names compare by UTF-16 code: 乙 (U+4E59) before 甲 (U+7532)
        assertEquals(List.of("heaven", "twin_one", "twin_two", "b", "a", "basic_breathing"), ids(groups.get(0)));
    }

    @Test
    void unresolvedTechniquesGoInALastGroupOfTheirOwn() {
        List<TechniqueShelf.Group> groups = TechniqueShelf.group(List.of(
                item("gone", null, -1, "myvillage:gone"),
                item("move", TechniqueCategory.MOVEMENT, 1, "踏云步")));
        assertEquals(2, groups.size());
        assertEquals(TechniqueCategory.MOVEMENT, groups.get(0).category());
        assertNull(groups.get(1).category());
        assertEquals("screen.myvillage.cultivation.technique_group.unresolved", groups.get(1).titleKey());
        assertEquals(List.of("gone"), ids(groups.get(1)));
    }

    @Test
    void gradesAreNamedFromMortalToHeaven() {
        for (int grade = 0; grade <= 4; grade++) {
            assertEquals("screen.myvillage.cultivation.grade_name." + grade, TechniqueShelf.gradeNameKey(grade));
        }
        assertNull(TechniqueShelf.gradeNameKey(-1));
        assertNull(TechniqueShelf.gradeNameKey(5));
    }

    @Test
    void chainPositionCountsFromOne() {
        assertEquals("1/3", TechniqueShelf.chainPosition(0, 3));
        assertEquals("2/4", TechniqueShelf.chainPosition(1, 4));
        assertEquals("4/4", TechniqueShelf.chainPosition(3, 4));
        assertEquals("", TechniqueShelf.chainPosition(-1, 4));
        assertEquals("", TechniqueShelf.chainPosition(4, 4));
    }

    @Test
    void chipsFlowIntoRowsAndTheFirstRowLeavesRoomForTheAction() {
        // 100 wide rows, the first one 60 because the action takes the rest; gap 4
        assertArrayEquals(new int[] {0, 0, 1, 1, 2},
                TechniqueShelf.chipRows(new int[] {30, 26, 40, 50, 30}, 60, 100, 4, 3));
        // everything fits on one row
        assertArrayEquals(new int[] {0, 0, 0}, TechniqueShelf.chipRows(new int[] {20, 20, 20}, 100, 100, 4, 3));
        // a chip wider than the first row starts on the second
        assertArrayEquals(new int[] {1, 1}, TechniqueShelf.chipRows(new int[] {80, 10}, 60, 100, 4, 3));
        // past the last row every later chip is dropped, even one that would fit
        assertArrayEquals(new int[] {0, 1, -1, -1},
                TechniqueShelf.chipRows(new int[] {90, 90, 90, 5}, 100, 100, 4, 2));
        assertEquals(1, TechniqueShelf.rowCount(new int[0]));
        assertEquals(2, TechniqueShelf.rowCount(new int[] {0, 1, -1}));
    }
}
