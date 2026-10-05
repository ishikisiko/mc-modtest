package com.example.myvillage.sim.runtime;

import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.cli.SimCli;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The shipped world-sim data from the source tree, one region graph and a small-tier world. */
final class RuntimeFixtures {
    static final long SEED = 20261002L;
    static final int DAYS_PER_YEAR = 6;
    private static SimData data;
    private static RegionGraph graph;
    private static byte[] genesis;

    private RuntimeFixtures() {
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

    /** A fresh copy of one small-tier world right after genesis (genesis runs once per test JVM). */
    static synchronized WorldSim world() {
        if (genesis == null) {
            genesis = WorldSim.genesis(SEED, graph(), data(), "small", DAYS_PER_YEAR).toBytes();
        }
        return WorldSim.fromBytes(genesis, graph(), data());
    }
}
