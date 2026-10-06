package com.example.myvillage.sim.runtime.net;

import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.cli.SimCli;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/** One small-tier genesis world from the shipped data, built once per test JVM. */
final class NetFixtures {
    static final long SEED = 20261002L;
    static final int DAYS_PER_YEAR = 6;
    /** A region-name resolver that marks what it resolved, so tests can tell it was used. */
    static final Function<String, String> REGION_NAME = id -> "域:" + id;
    private static SimData data;
    private static RegionGraph graph;
    private static byte[] genesis;

    private NetFixtures() {
    }

    static synchronized SimData data() {
        if (data == null) {
            Path resources = Path.of("src/main/resources");
            data = WorldSim.loadData(path -> {
                Path file = resources.resolve(path);
                return Files.isRegularFile(file) ? Files.newInputStream(file) : null;
            });
        }
        return data;
    }

    static synchronized RegionGraph graph() {
        if (graph == null) {
            try {
                graph = SimCli.buildGraph(Path.of("."), SEED);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return graph;
    }

    /** A fresh copy of the world right after genesis. */
    static synchronized WorldSim world() {
        if (genesis == null) {
            genesis = WorldSim.genesis(SEED, graph(), data(), "small", DAYS_PER_YEAR).toBytes();
        }
        return WorldSim.fromBytes(genesis, graph(), data());
    }
}
