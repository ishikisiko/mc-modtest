package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The pure core (design §2) imports only java.*, Gson, its own package and a few pure region
 * classes, and never touches Minecraft, NeoForge, slf4j or Mojang, nor a source of nondeterminism.
 * {@code sim.runtime} is excluded. The offline CLI may also use the pure region generator.
 */
class SimPurityGuardTest {
    private static final Path ROOT = Path.of("src/main/java/com/example/myvillage/sim");
    private static final String REGION = "com.example.myvillage.region.runtime.";
    private static final Set<String> REGION_ALLOWED = Set.of(
            "RegionGraph", "GenRegion", "GenEdge", "IntRange", "RegionQueries", "RegionPlacement", "RegionContract");
    /** The CLI builds the seed's graph offline with the pure generator. */
    private static final Set<String> REGION_ALLOWED_CLI = Set.of("RegionTopologyGenerator", "Ruleset", "RegionProfile");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(static\\s+)?([\\w.]+)(\\.\\*)?;", Pattern.MULTILINE);
    private static final List<String> FORBIDDEN_TEXT = List.of(
            "net.minecraft", "net.neoforged", "org.slf4j", "com.mojang", "Math.random", "java.util.Random",
            "ThreadLocalRandom", "currentTimeMillis", "LocalDateTime", "Instant.now");

    @Test
    void coreImportsOnlyPureTypes() throws IOException {
        List<String> problems = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(ROOT)) {
            files = walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !ROOT.relativize(p).startsWith("runtime"))
                    .sorted().toList();
        }
        assertTrue(files.size() > 10, "expected the core sources under " + ROOT);
        for (Path file : files) {
            boolean cli = ROOT.relativize(file).startsWith("cli");
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = IMPORT.matcher(text);
            while (m.find()) {
                String name = m.group(2);
                if (name.startsWith("java.") || name.startsWith("com.google.gson.")
                        || name.startsWith("com.example.myvillage.sim.")) {
                    if (name.startsWith("com.example.myvillage.sim.runtime")) {
                        problems.add(file + ": core must not depend on sim.runtime (" + name + ")");
                    }
                    continue;
                }
                if (name.startsWith(REGION)) {
                    String simple = name.substring(REGION.length());
                    if (REGION_ALLOWED.contains(simple) || (cli && REGION_ALLOWED_CLI.contains(simple))) {
                        continue;
                    }
                }
                problems.add(file + ": imports " + name);
            }
            for (String bad : FORBIDDEN_TEXT) {
                if (text.contains(bad)) {
                    problems.add(file + ": mentions " + bad);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
