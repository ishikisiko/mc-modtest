package com.example.myvillage.region.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Python⇄Java parity cross-check for the region-runtime generator. Reads the
 * golden fixtures emitted by {@code tools/buildgen/tests/
 * generate_region_runtime_fixtures.py} and asserts the Java port reproduces
 * every graph <b>byte-identically</b>.
 *
 * <p>The fixture set is enumerated from {@code index.json} (one entry per case),
 * so adding a fixture case and regenerating never requires touching this test.
 *
 * <p>Regenerate the fixtures whenever {@code region_topology.py} or the
 * placement transform changes:
 * <pre>{@code
 * python3 tools/buildgen/tests/generate_region_runtime_fixtures.py
 * }</pre>
 */
class RegionRuntimeParityTest {

    private static final Path WORLDFEN_DIR =
            Path.of("src/main/resources/data/myvillage/worldgen");

    private static RegionCatalogLoader.RegionData DATA;

    @BeforeAll
    static void loadShippedData() {
        DATA = RegionCatalogLoader.loadFromFilesystem(WORLDFEN_DIR);
    }

    static Stream<Arguments> fixtures() {
        JsonObject index = readResource("/region_runtime_fixtures/index.json");
        List<Arguments> out = new ArrayList<>();
        for (JsonElement e : index.getAsJsonArray("cases")) {
            JsonObject c = e.getAsJsonObject();
            out.add(Arguments.of(
                    c.get("case").getAsString(),
                    c.get("seed").getAsLong(),
                    c.get("file").getAsString()));
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} seed={1}")
    @MethodSource("fixtures")
    void graphMatchesGoldenFixtureByteForByte(String caseName, long seed, String file) {
        JsonObject fixture = readResource("/region_runtime_fixtures/" + file).getAsJsonObject();
        RegionGraph graph = RegionTopologyGenerator.generate(seed, DATA.ruleset(), DATA.catalog());

        // Canonical strings: the Java output and the fixture's graph sub-tree,
        // both normalized through the same sorted-keys / 2-space-indent path so
        // any Gson serialization quirk applies equally to both sides.
        String actual = graph.toCanonicalJson();
        String expected = canonicalize(fixture.get("graph"));

        assertEquals(stripTrailing(expected), stripTrailing(actual),
                () -> "graph drift for " + caseName + " (seed " + seed + "):\n"
                        + "--- expected ---\n" + expected + "\n--- actual ---\n" + actual);
    }

    @Test
    void indexAdvertisesAtLeastOneCasePerStructuralCategory() {
        JsonObject index = readResource("/region_runtime_fixtures/index.json");
        List<String> cases = new ArrayList<>();
        for (JsonElement e : index.getAsJsonArray("cases")) {
            cases.add(e.getAsJsonObject().get("case").getAsString());
        }
        assertTrue(cases.contains("min_count"), "missing min_count fixture");
        assertTrue(cases.contains("max_count"), "missing max_count fixture");
        assertTrue(cases.contains("tier_tie"), "missing tier_tie fixture");
        assertTrue(cases.contains("walled_low"), "missing walled_low fixture");
        assertTrue(cases.contains("shipped"), "missing shipped fixture");
    }

    // ------------------------------------------------------------------ #
    // Canonicalization helpers.
    // ------------------------------------------------------------------ #

    /**
     * Re-serialize a {@link JsonElement} through the same sorted-keys /
     * 2-space-indent path the generator uses, so a fixture value loaded from
     * disk compares byte-for-byte against the Java generator's
     * {@link RegionGraph#toCanonicalJson()}. Numbers keep their int-vs-float
     * flavor (Python emits {@code 4} for ints and {@code 1.044} for floats).
     */
    private static String canonicalize(JsonElement element) {
        return RegionJson.PRETTY.toJson(toSorted(element));
    }

    private static Object toSorted(JsonElement e) {
        if (e.isJsonNull()) {
            return null;
        }
        if (e.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement el : e.getAsJsonArray()) {
                list.add(toSorted(el));
            }
            return list;
        }
        if (e.isJsonObject()) {
            TreeMap<String, Object> map = new TreeMap<>();
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                map.put(en.getKey(), toSorted(en.getValue()));
            }
            return map;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) {
            return p.getAsBoolean();
        }
        if (p.isNumber()) {
            String s = p.getAsString();
            if (s.indexOf('.') >= 0 || s.indexOf('e') >= 0 || s.indexOf('E') >= 0) {
                return p.getAsDouble();
            }
            return p.getAsLong();
        }
        return p.getAsString();
    }

    private static String stripTrailing(String s) {
        return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
    }

    private static JsonObject readResource(String name) {
        try (Reader reader = new InputStreamReader(
                Objects.requireNonNull(
                        RegionRuntimeParityTest.class.getResourceAsStream(name),
                        name + " not on test classpath"),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException ioe) {
            throw new AssertionError("failed reading " + name, ioe);
        }
    }
}
