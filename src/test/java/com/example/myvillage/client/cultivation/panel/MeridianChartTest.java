package com.example.myvillage.client.cultivation.panel;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeridianChartTest {
    @Test
    void pointsAreUniqueInsideTheFigureSquareAndNamedByTheirOwnKey() {
        Set<String> ids = new HashSet<>();
        for (MeridianChart.Acupoint point : MeridianChart.points()) {
            assertTrue(ids.add(point.id()), "duplicate " + point.id());
            assertInSquare(point.x(), point.y(), point.id());
            assertEquals("screen.myvillage.cultivation.acupoint." + point.id(), point.translationKey());
            point.label().ifPresent(label -> assertInSquare(label.x(), label.y(), point.id() + " label"));
        }
        assertEquals(1, MeridianChart.points().stream()
                .filter(point -> point.tier() == MeridianChart.Tier.DANTIAN).count());
        assertTrue(MeridianChart.point(MeridianChart.DANTIAN).isPresent());
        assertTrue(MeridianChart.point(MeridianChart.YUZHEN).isPresent());
        assertTrue(MeridianChart.point(MeridianChart.BAIHUI).isPresent());
    }

    @Test
    void namedPointsAreLabelledAndMinorOnesAreNot() {
        for (MeridianChart.Acupoint point : MeridianChart.points()) {
            assertEquals(point.tier() != MeridianChart.Tier.MINOR, point.label().isPresent(), point.id());
        }
    }

    @Test
    void channelsRunBetweenAcupointsThroughTheSquare() {
        Set<String> ids = new HashSet<>();
        for (MeridianChart.Channel channel : MeridianChart.channels()) {
            assertTrue(ids.add(channel.id()), "duplicate " + channel.id());
            assertTrue(channel.stops().size() >= 2, channel.id());
            assertTrue(MeridianChart.point(channel.from()).isPresent(), channel.id());
            assertTrue(MeridianChart.point(channel.to()).isPresent(), channel.id());
            for (MeridianChart.Stop stop : channel.stops()) {
                assertInSquare(stop.x(), stop.y(), channel.id());
                stop.acupoint().ifPresent(id -> {
                    MeridianChart.Acupoint point = MeridianChart.point(id).orElseThrow();
                    assertEquals(point.x(), stop.x(), id);
                    assertEquals(point.y(), stop.y(), id);
                });
            }
        }
        for (MeridianChart.Vessel vessel : MeridianChart.Vessel.values()) {
            assertTrue(MeridianChart.channels().stream().anyMatch(channel -> channel.vessel() == vessel), vessel.name());
        }
    }

    @Test
    void smallCircuitRisesUpTheBackAndFallsDownTheFrontThroughTheDantian() {
        List<MeridianChart.Channel> circuit = MeridianChart.smallCircuit().stream()
                .map(id -> MeridianChart.channel(id).orElseThrow())
                .toList();
        assertEquals(MeridianChart.Vessel.GOVERNING, circuit.get(0).vessel());
        assertEquals(MeridianChart.Vessel.CONCEPTION, circuit.get(1).vessel());
        assertEquals(MeridianChart.HUIYIN, circuit.get(0).from());
        assertEquals(MeridianChart.BAIHUI, circuit.get(0).to());
        assertEquals(circuit.get(0).to(), circuit.get(1).from());
        assertEquals(circuit.get(1).to(), circuit.get(0).from());
        assertTrue(circuit.get(1).stops().stream()
                .anyMatch(stop -> stop.acupoint().filter(MeridianChart.DANTIAN::equals).isPresent()));
        assertTrue(circuit.get(0).stops().stream()
                .anyMatch(stop -> stop.acupoint().filter(MeridianChart.YUZHEN::equals).isPresent()));
        // the figure faces right: the governing vessel runs behind (left of) the conception vessel
        float back = averageX(circuit.get(0));
        float front = averageX(circuit.get(1));
        assertTrue(back < front, back + " < " + front);
        // and the governing vessel rises: it ends higher (smaller y) than it starts
        assertTrue(circuit.get(0).stops().get(circuit.get(0).stops().size() - 1).y()
                < circuit.get(0).stops().get(0).y());
    }

    @Test
    void limbChannelsReachThePalmAndTheSole() {
        assertEquals("laogong", MeridianChart.channels().stream()
                .filter(channel -> channel.vessel() == MeridianChart.Vessel.HAND).findFirst().orElseThrow().to());
        assertEquals("yongquan", MeridianChart.channels().stream()
                .filter(channel -> channel.vessel() == MeridianChart.Vessel.FOOT).findFirst().orElseThrow().to());
        assertFalse(MeridianChart.smallCircuit().contains("hand"));
    }

    private static float averageX(MeridianChart.Channel channel) {
        float total = 0.0F;
        for (MeridianChart.Stop stop : channel.stops()) {
            total += stop.x();
        }
        return total / channel.stops().size();
    }

    private static void assertInSquare(float x, float y, String what) {
        assertTrue(x >= 0.0F && x <= 1.0F && y >= 0.0F && y <= 1.0F, what + " at " + x + "," + y);
    }
}
