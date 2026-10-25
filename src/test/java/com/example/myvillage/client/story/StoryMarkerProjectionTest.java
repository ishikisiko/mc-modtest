package com.example.myvillage.client.story;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class StoryMarkerProjectionTest {
    @Test void actualClipCoordinatesRespectAspectRatioAndGuiScale() {
        for (int[] size : new int[][]{{320, 240}, {427, 240}, {640, 360}, {960, 540}, {344, 180}}) {
            var point = StoryMarkerProjection.project(.2, .3, 1, size[0], size[1]);
            assertNotNull(point);
            assertFalse(point.edge());
            assertEquals(size[0] * .6, point.x(), 1e-8);
            assertEquals(size[1] * .35, point.y(), 1e-8);
        }
    }
    @Test void rearBearingNeverMirrorsLeftAndRightAndExactRearStaysStable() {
        var right = StoryMarkerProjection.project(1, 0, -1, 320, 240);
        var left = StoryMarkerProjection.project(-1, 0, -1, 320, 240);
        var rear = StoryMarkerProjection.project(0, 0, -1, 320, 240);
        assertTrue(right.edge()); assertTrue(right.x() > 160);
        assertTrue(left.edge()); assertTrue(left.x() < 160);
        assertEquals(160, rear.x(), 1e-8); assertEquals(176, rear.y(), 1e-8);
        assertEquals(1, rear.directionY(), 1e-8);
    }
    @Test void offscreenNearPlaneAndRearAreAlwaysBoundedAwayFromHotbar() {
        for (int[] size : new int[][]{{320, 240}, {427, 240}, {640, 360}, {960, 540}}) {
            for (double w : new double[]{-100, -1, -1e-7, 0, 1e-7, 1, 100}) {
                for (double x : new double[]{-10000, -1, 0, 1, 10000}) {
                    for (double y : new double[]{-10000, -1, 0, 1, 10000}) {
                        var point = StoryMarkerProjection.project(x, y, w, size[0], size[1]);
                        assertNotNull(point);
                        assertTrue(point.x() >= 24 - 1e-6 && point.x() <= size[0] - 24 + 1e-6);
                        assertTrue(point.y() >= 24 - 1e-6 && point.y() <= size[1] - 64 + 1e-6);
                        if (point.edge()) assertEquals(1, Math.hypot(point.directionX(), point.directionY()), 1e-8);
                    }
                }
            }
        }
    }
    @Test void invalidViewportAndNonFiniteMatrixCannotCreateHudArtifacts() {
        assertNull(StoryMarkerProjection.project(1, 1, 1, 100, 240));
        assertNull(StoryMarkerProjection.project(1, 1, 1, 320, 100));
        assertNull(StoryMarkerProjection.project(Double.NaN, 0, 1, 320, 240));
        assertNull(StoryMarkerProjection.project(0, Double.POSITIVE_INFINITY, 1, 320, 240));
        assertNull(StoryMarkerProjection.project(0, 0, Double.NaN, 320, 240));
    }
}
