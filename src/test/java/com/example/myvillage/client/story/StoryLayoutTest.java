package com.example.myvillage.client.story;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class StoryLayoutTest {
    @Test void allFourOptionsRemainVisibleAtSmallestMinecraftGuiAndWideScreens() {
        for (int[] viewport : new int[][]{{320, 240}, {427, 240}, {640, 360}, {960, 540}}) {
            for (int count = 0; count <= 4; count++) {
                var layout = StoryLayout.paper(viewport[0], viewport[1], count);
                var panel = layout.panel();
                assertTrue(panel.x() >= 0 && panel.y() >= 0);
                assertTrue(panel.right() <= viewport[0] && panel.bottom() <= viewport[1]);
                assertTrue(layout.body().height() >= 50, "a usable scrolling body remains at 320x240");
                for (int i = 0; i < count; i++) {
                    var option = layout.option(i);
                    assertTrue(option.x() >= panel.x() && option.right() <= panel.right());
                    assertTrue(option.y() >= layout.body().bottom() + 8);
                    assertTrue(option.bottom() <= panel.bottom() - 20);
                    for (int j = 0; j < i; j++) {
                        var other = layout.option(j);
                        assertTrue(option.x() >= other.right() || other.x() >= option.right()
                                || option.y() >= other.bottom() || other.y() >= option.bottom());
                    }
                }
            }
        }
    }
    @Test void trackerCannotCoverTheCrosshair() {
        for (int[] viewport : new int[][]{{320, 240}, {640, 360}, {960, 540}}) {
            var tracker = StoryLayout.tracker(viewport[0], viewport[1]);
            assertTrue(tracker.x() > viewport[0] / 2 + 8);
            assertTrue(tracker.right() < viewport[0]);
            assertFalse(tracker.contains(viewport[0] / 2.0, viewport[1] / 2.0));
        }
    }
    @Test void scrollCanReachLastLongJournalRowAndNeverOverruns() {
        assertEquals(0, StoryLayout.scroll(-30, 1200, 60));
        assertEquals(1140, StoryLayout.scroll(Integer.MAX_VALUE, 1200, 60));
        assertEquals(0, StoryLayout.scroll(9, 30, 60));
        assertEquals(77, StoryLayout.scroll(77, 1200, 60));
    }
    @Test void pCyclesThreePresentationStatesWithoutTouchingStoryState() {
        var mode = StoryLayout.TrackerMode.COMPACT;
        assertEquals(StoryLayout.TrackerMode.EXPANDED, mode.next());
        assertEquals(StoryLayout.TrackerMode.HIDDEN, mode.next().next());
        assertEquals(mode, mode.next().next().next());
    }
}
