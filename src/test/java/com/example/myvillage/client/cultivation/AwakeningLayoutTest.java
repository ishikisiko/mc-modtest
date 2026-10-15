package com.example.myvillage.client.cultivation;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AwakeningLayoutTest {
    @Test void allSixCandidatesAndFooterFitTheThreeSupportedGuiSizes() {
        for (int[] size : new int[][]{{480,270},{427,240},{320,240}}) {
            var layout = AwakeningLayout.at(size[0], size[1]);
            var f = layout.frame();
            assertTrue(f.x() >= 8 && f.y() >= 8);
            assertTrue(f.right() <= size[0] - 8 && f.bottom() <= size[1] - 8);
            assertEquals(size[0] >= 400 ? 3 : 2, layout.columns());
            List<AwakeningLayout.Rect> targets = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                var card = layout.card(i);
                assertTrue(card.width() >= 120, "name and cost stay readable");
                assertTrue(card.height() >= 26, "two text rows cannot overlap");
                assertTrue(card.y() >= layout.root().bottom() + 20);
                assertTrue(card.bottom() < layout.summary().y());
                targets.add(card);
            }
            targets.addAll(List.of(layout.reroll(), layout.lock(), layout.confirm(), layout.close()));
            for (int a = 0; a < targets.size(); a++) {
                var r = targets.get(a);
                assertTrue(r.x() >= f.x() && r.y() >= f.y());
                assertTrue(r.right() <= f.right() && r.bottom() <= f.bottom());
                for (int b = a + 1; b < targets.size(); b++) assertFalse(overlaps(r, targets.get(b)));
            }
            assertTrue(layout.confirm().y() >= layout.summary().bottom());
        }
    }
    @Test void hitBoundsAreExclusiveAtTheSharedFarEdges() {
        var rect = AwakeningLayout.at(320, 240).card(0);
        assertTrue(rect.contains(rect.x(), rect.y()));
        assertFalse(rect.contains(rect.right(), rect.y()));
        assertFalse(rect.contains(rect.x(), rect.bottom()));
        assertThrows(IllegalArgumentException.class, () -> AwakeningLayout.at(320,240).card(6));
    }
    private boolean overlaps(AwakeningLayout.Rect a, AwakeningLayout.Rect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }
}
