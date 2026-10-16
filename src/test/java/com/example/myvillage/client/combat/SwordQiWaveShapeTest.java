package com.example.myvillage.client.combat;

import org.junit.jupiter.api.Test;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class SwordQiWaveShapeTest {
    @Test void allChargeLevelsHaveFiniteVerticesInsideReceivedWidthAndHeight() {
        for (int level : new int[]{0, 1, 127, 255}) {
            var skill = com.example.myvillage.combat.definition.CombatTestData.styles().skill(
                    net.minecraft.resources.ResourceLocation.parse("myvillage:jianqi_jue")).orElseThrow();
            double scale = skill.scale(level), width = skill.halfWidth() * scale, height = skill.halfHeight() * scale;
            vertices(width, height).forEach(v -> {
                assertTrue(Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z()));
                assertTrue(Math.abs(v.x()) <= width + 1e-12);
                assertTrue(Math.abs(v.y()) <= height + 1e-12);
                assertTrue(v.z() <= 0, "No forward extrapolation ahead of received center");
                assertTrue(v.alpha() >= 0 && v.alpha() <= 255);
            });
            assertEquals(width, vertices(width, height).mapToDouble(v -> Math.abs(v.x())).max().orElseThrow(), 1e-12);
            assertTrue(vertices(width, height).mapToDouble(v -> Math.abs(v.y())).max().orElseThrow() >= height * .89);
        }
    }

    @Test void concaveTailTapersToTipsAndWhiteOccursOnlyOnNarrowLeadingEdge() {
        var all = vertices(1.1, .25).toList();
        double centerTail = all.stream().filter(v -> Math.abs(v.x()) < 1e-12).mapToDouble(SwordQiWaveShape.Vertex::z).min().orElseThrow();
        double tipTail = all.stream().filter(v -> Math.abs(v.x()) > 1.099).mapToDouble(SwordQiWaveShape.Vertex::z).min().orElseThrow();
        assertTrue(centerTail > tipTail, "The rear outline must be concave, not lens shaped");
        all.stream().filter(v -> Math.abs(v.x()) > 1.099).forEach(v -> assertEquals(0, v.y(), 1e-12));
        all.stream().filter(v -> v.red() > 220).forEach(v ->
                assertEquals(-1.1 * .70 * Math.pow(v.x() / 1.1, 2), v.z(), 1e-12));
        assertTrue(all.stream().anyMatch(v -> v.alpha() == 0 && v.red() < 100));
    }

    @Test void freshRevisionAndStaleGapNeverInterpolateFromPreviousRelease() {
        assertTrue(SwordQiWaveShape.continuous(4, 4, 20, 21));
        assertFalse(SwordQiWaveShape.continuous(4, 5, 20, 21));
        assertFalse(SwordQiWaveShape.continuous(4, 4, 20, 25));
        assertFalse(SwordQiWaveShape.continuous(4, 4, 20, 19));
        assertEquals(0, SwordQiWaveShape.interpolationFraction(-1));
        assertEquals(.5, SwordQiWaveShape.interpolationFraction(.5));
        assertEquals(1, SwordQiWaveShape.interpolationFraction(12));
        assertEquals(0, SwordQiWaveShape.interpolationFraction(Double.NaN));
    }

    @Test void invalidDimensionsAreRejectedBeforeMeshCreation() {
        assertThrows(IllegalArgumentException.class, () -> SwordQiWaveShape.mesh(Double.NaN, .25));
        assertThrows(IllegalArgumentException.class, () -> SwordQiWaveShape.mesh(1.1, 0));
    }

    @Test void verticalAndSteepAimKeepFiniteOrthogonalMeshAxes() {
        for (double[] aim : new double[][]{{0, 1, 0}, {0, -1, 0}, {1e-6, 1, 1e-6}, {.4, .8, .2}, {0, 0, 1}}) {
            var frame = SwordQiWaveShape.frame(aim[0], aim[1], aim[2]);
            for (var axis : new SwordQiWaveShape.Axis[]{frame.right(), frame.up(), frame.forward()}) {
                assertEquals(1, dot(axis, axis), 1e-9);
            }
            assertEquals(0, dot(frame.up(), frame.forward()), 1e-9);
            assertEquals(0, dot(frame.right(), frame.up()), 1e-9);
            assertEquals(0, dot(frame.right(), frame.forward()), 1e-9);
            vertices(1.1, .25).forEach(v -> {
                var p = frame.orient(v);
                assertTrue(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()));
                assertEquals(v.y(), dot(p, frame.up()), 2e-6);
            });
        }
    }

    private static double dot(SwordQiWaveShape.Axis a, SwordQiWaveShape.Axis b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }

    private static Stream<SwordQiWaveShape.Vertex> vertices(double width, double height) {
        return SwordQiWaveShape.mesh(width, height).stream().flatMap(q -> Stream.of(q.a(), q.b(), q.c(), q.d()));
    }
}
