package com.example.myvillage.client.cultivation.panel;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Which part of the meridian diagram a running core technique lights: the loop its motes
 * circulate on and the channels drawn lit. The route id is the core technique's
 * {@code effects.core.meditation_route}. It is a picture only; the server keeps no meridian state.
 *
 * <p>{@code xiaozhoutian} (the small circuit, today's look) is the only route so far, and every
 * id without a route here, as well as no running core technique at all, falls back to it.
 */
public record MeridianRoute(
        String id,
        List<String> circuit,
        Set<String> channels,
        String titleKey,
        String flowKey) {
    public static final String XIAOZHOUTIAN = "xiaozhoutian";

    /** The small circuit through the lower dantian, with every channel of the chart lit. */
    public static final MeridianRoute SMALL_CIRCUIT = new MeridianRoute(
            XIAOZHOUTIAN,
            MeridianChart.smallCircuit(),
            allChannels(),
            "screen.myvillage.cultivation.meridian.title",
            "screen.myvillage.cultivation.meridian.flow");

    private static final Map<String, MeridianRoute> ROUTES = Map.of(XIAOZHOUTIAN, SMALL_CIRCUIT);

    public MeridianRoute {
        Objects.requireNonNull(id, "id");
        circuit = List.copyOf(circuit);
        channels = Set.copyOf(channels);
        if (circuit.isEmpty()) {
            throw new IllegalArgumentException("A meridian route needs a circuit");
        }
        for (String channel : circuit) {
            if (MeridianChart.channel(channel).isEmpty()) {
                throw new IllegalArgumentException("Unknown circuit channel " + channel);
            }
            if (!channels.contains(channel)) {
                throw new IllegalArgumentException("Circuit channel " + channel + " must be lit");
            }
        }
        for (String channel : channels) {
            if (MeridianChart.channel(channel).isEmpty()) {
                throw new IllegalArgumentException("Unknown lit channel " + channel);
            }
        }
    }

    /** The route for {@code routeId}; an unknown or absent id gets the small circuit. */
    public static MeridianRoute of(Optional<String> routeId) {
        return routeId.map(ROUTES::get).orElse(SMALL_CIRCUIT);
    }

    /** Every route this panel can draw. */
    public static List<MeridianRoute> all() {
        return List.copyOf(ROUTES.values());
    }

    /** Whether the channel is drawn lit on this route (an unlit channel stays a faint line). */
    public boolean lights(String channelId) {
        return channels.contains(channelId);
    }

    private static Set<String> allChannels() {
        Set<String> ids = new LinkedHashSet<>();
        for (MeridianChart.Channel channel : MeridianChart.channels()) {
            ids.add(channel.id());
        }
        return ids;
    }
}
