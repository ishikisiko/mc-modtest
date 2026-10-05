package com.example.myvillage.sim;

import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.sim.cli.SimCli;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared test inputs: the shipped data from the source tree and per-seed region graphs. */
final class SimFixtures {
    static final Path RESOURCES = Path.of("src/main/resources");
    private static SimData data;
    private static final Map<Long, RegionGraph> GRAPHS = new HashMap<>();

    private SimFixtures() {
    }

    /** Opener over the source tree; {@code -Dworldsim.resources=DIR} is searched first (local iteration). */
    static SimData.ResourceOpener opener() {
        List<Path> roots = new ArrayList<>();
        String extra = System.getProperty("worldsim.resources");
        if (extra != null && !extra.isEmpty()) {
            roots.add(Path.of(extra));
        }
        roots.add(RESOURCES);
        return path -> {
            for (Path root : roots) {
                Path file = root.resolve(path);
                if (Files.isRegularFile(file)) {
                    return Files.newInputStream(file);
                }
            }
            return null;
        };
    }

    static synchronized SimData data() {
        if (data == null) {
            data = WorldSim.loadData(opener());
        }
        return data;
    }

    static synchronized RegionGraph graph(long seed) {
        return GRAPHS.computeIfAbsent(seed, s -> {
            try {
                return SimCli.buildGraph(Path.of("."), s);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    static WorldSim genesis(long seed, String tier, int dpy) {
        return WorldSim.genesis(seed, graph(seed), data(), tier, dpy);
    }

    /** Collects every event the world emits. */
    static final class Collector implements SimObserver {
        final List<SimEvent> events = new ArrayList<>();

        @Override
        public void onEvent(SimEvent event) {
            events.add(event);
        }
    }

    static void run(WorldSim sim, long days, int dpy) {
        for (long d = 0; d < days; d++) {
            sim.step(dpy);
        }
    }
}
