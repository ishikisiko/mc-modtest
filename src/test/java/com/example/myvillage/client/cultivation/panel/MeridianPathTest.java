package com.example.myvillage.client.cultivation.panel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeridianPathTest {
    private static final float EPSILON = 1.0E-5F;

    private static MeridianChart.Stop at(String id, float x, float y) {
        return new MeridianChart.Stop(Optional.ofNullable(id), x, y);
    }

    @Test
    void aStraightRouteIsMeasuredByArcLength() {
        MeridianChart.Channel line = new MeridianChart.Channel("line", MeridianChart.Vessel.HAND,
                List.of(at("a", 0.0F, 0.0F), at(null, 0.5F, 0.0F), at("b", 1.0F, 0.0F)));
        MeridianPath path = MeridianPath.of(line);
        assertEquals(1.0F, path.length(), EPSILON);
        assertEquals(0.25F, path.pointAt(0.25D)[0], 1.0E-3F);
        assertEquals(0.0F, path.pointAt(0.25D)[1], EPSILON);
        assertEquals(0.0F, path.fractionOf("a").orElseThrow(), EPSILON);
        assertEquals(1.0F, path.fractionOf("b").orElseThrow(), EPSILON);
        assertTrue(path.fractionOf("missing").isEmpty());
    }

    @Test
    void theSplinePassesThroughEveryStopAndClampsOutsideTheRange() {
        MeridianChart.Channel hand = MeridianChart.channel("hand").orElseThrow();
        MeridianPath path = MeridianPath.of(hand);
        assertEquals(hand.stops().size(), (path.size() - 1) / MeridianPath.SAMPLES_PER_SEGMENT + 1);
        for (int stop = 0; stop < hand.stops().size(); stop++) {
            int sample = stop * MeridianPath.SAMPLES_PER_SEGMENT;
            assertEquals(hand.stops().get(stop).x(), path.x(sample), EPSILON);
            assertEquals(hand.stops().get(stop).y(), path.y(sample), EPSILON);
        }
        float[] start = path.pointAt(-1.0D);
        float[] end = path.pointAt(2.0D);
        assertEquals(hand.stops().get(0).x(), start[0], EPSILON);
        assertEquals(hand.stops().get(hand.stops().size() - 1).y(), end[1], EPSILON);
    }

    @Test
    void theSmallCircuitClosesOnThePerineumAndOrdersItsPassesUpTheBack() {
        MeridianPath circuit = MeridianPath.of(List.of(
                MeridianChart.channel("du").orElseThrow(), MeridianChart.channel("ren").orElseThrow()));
        float[] start = circuit.pointAt(0.0D);
        float[] end = circuit.pointAt(1.0D);
        assertEquals(start[0], end[0], EPSILON);
        assertEquals(start[1], end[1], EPSILON);
        float weilu = circuit.fractionOf("weilu").orElseThrow();
        float yuzhen = circuit.fractionOf(MeridianChart.YUZHEN).orElseThrow();
        float baihui = circuit.fractionOf(MeridianChart.BAIHUI).orElseThrow();
        float dantian = circuit.fractionOf(MeridianChart.DANTIAN).orElseThrow();
        assertTrue(0.0F < weilu && weilu < yuzhen && yuzhen < baihui && baihui < dantian && dantian < 1.0F);
        // arc length grows monotonically along the samples
        double previous = -1.0D;
        for (int step = 0; step <= 100; step++) {
            int sample = circuit.sampleAt(step / 100.0D);
            assertTrue(sample >= previous);
            previous = sample;
        }
    }
}
