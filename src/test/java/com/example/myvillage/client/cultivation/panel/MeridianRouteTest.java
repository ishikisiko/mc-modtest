package com.example.myvillage.client.cultivation.panel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeridianRouteTest {
    @Test
    void xiaozhoutianIsTodaysSmallCircuitWithEveryChannelLit() {
        MeridianRoute route = MeridianRoute.of(Optional.of(MeridianRoute.XIAOZHOUTIAN));
        assertSame(MeridianRoute.SMALL_CIRCUIT, route);
        assertEquals(List.of("du", "ren"), route.circuit());
        assertEquals(MeridianChart.smallCircuit(), route.circuit());
        Set<String> chart = MeridianChart.channels().stream()
                .map(MeridianChart.Channel::id)
                .collect(Collectors.toSet());
        assertEquals(chart, route.channels());
        for (String id : chart) {
            assertTrue(route.lights(id), id);
        }
        assertEquals("screen.myvillage.cultivation.meridian.title", route.titleKey());
        assertEquals("screen.myvillage.cultivation.meridian.flow", route.flowKey());
    }

    @Test
    void noRunningCoreOrAnUnknownRouteFallsBackToTheSmallCircuit() {
        assertSame(MeridianRoute.SMALL_CIRCUIT, MeridianRoute.of(Optional.empty()));
        assertSame(MeridianRoute.SMALL_CIRCUIT, MeridianRoute.of(Optional.of("dazhoutian")));
        assertSame(MeridianRoute.SMALL_CIRCUIT, MeridianRoute.of(Optional.of("")));
    }

    @Test
    void everyRouteCirculatesOnLitChartChannels() {
        assertTrue(MeridianRoute.all().contains(MeridianRoute.SMALL_CIRCUIT));
        for (MeridianRoute route : MeridianRoute.all()) {
            assertEquals(route, MeridianRoute.of(Optional.of(route.id())));
            for (String channel : route.circuit()) {
                assertTrue(MeridianChart.channel(channel).isPresent(), channel);
                assertTrue(route.lights(channel), channel);
            }
        }
    }

    @Test
    void aRouteRejectsChannelsTheChartDoesNotHave() {
        assertThrows(IllegalArgumentException.class, () -> new MeridianRoute(
                "x", List.of("du", "nowhere"), Set.of("du", "nowhere"), "t", "f"));
        assertThrows(IllegalArgumentException.class, () -> new MeridianRoute(
                "x", List.of("du"), Set.of("ren"), "t", "f"));
        assertThrows(IllegalArgumentException.class, () -> new MeridianRoute(
                "x", List.of(), Set.of(), "t", "f"));
    }
}
