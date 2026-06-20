package com.example.myvillage.town;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Python⇄Java parity cross-check for {@link TownHash}. Reads the JSON fixture
 * emitted by {@code tools/buildgen/tests/generate_town_hash_fixture.py} from
 * the classpath and asserts the Java mirror reproduces every hash, range, and
 * pick vector bit-identically.
 *
 * <p>Regenerate the fixture whenever {@code town_hash.py} / {@link TownHash}
 * changes:
 * <pre>{@code
 * python3 tools/buildgen/tests/generate_town_hash_fixture.py
 * }</pre>
 */
class TownHashParityTest {

    private static final long MASK64 = 0xFFFFFFFFFFFFFFFFL;

    @Test
    void hashVectorsMatchFixture() {
        JsonObject root = loadFixture();
        JsonArray cases = root.getAsJsonArray("hash_cases");
        assertTrue(cases.size() >= 50, "fixture must contain >= 50 hash cases");
        for (JsonElement e : cases) {
            JsonObject c = e.getAsJsonObject();
            long seed = c.get("seed").getAsLong();
            String tag = c.get("tag").getAsString();
            // Python writes the unsigned decimal; compare by bit pattern.
            long expected = Long.parseUnsignedLong(c.get("hash").getAsString());
            long actual = TownHash.hash64(seed, tag);
            assertEquals(expected, actual,
                    "hash64 drift for (" + seed + ", " + tag + ")");
        }
    }

    @Test
    void rangeVectorsMatchFixture() {
        JsonObject root = loadFixture();
        JsonArray cases = root.getAsJsonArray("range_cases");
        for (JsonElement e : cases) {
            JsonObject c = e.getAsJsonObject();
            long seed = c.get("seed").getAsLong();
            String tag = c.get("tag").getAsString();
            int lo = c.get("lo").getAsInt();
            int hi = c.get("hi").getAsInt();
            int expected = c.get("value").getAsInt();
            int actual = TownHash.range64(seed, tag, lo, hi);
            assertEquals(expected, actual,
                    "range64 drift for (" + seed + ", " + tag + ", " + lo + ", " + hi + ")");
        }
    }

    @Test
    void pickVectorsMatchFixture() {
        JsonObject root = loadFixture();
        JsonArray cases = root.getAsJsonArray("pick_cases");
        for (JsonElement e : cases) {
            JsonObject c = e.getAsJsonObject();
            long seed = c.get("seed").getAsLong();
            String tag = c.get("tag").getAsString();
            JsonArray optsArr = c.getAsJsonArray("options");
            String[] opts = new String[optsArr.size()];
            for (int i = 0; i < optsArr.size(); i++) {
                opts[i] = optsArr.get(i).getAsString();
            }
            int expectedIndex = c.get("index").getAsInt();
            String actual = TownHash.pick(seed, tag, opts);
            assertEquals(opts[expectedIndex], actual,
                    "pick drift for (" + seed + ", " + tag + ")");
        }
    }

    private JsonObject loadFixture() {
        try (Reader reader = new InputStreamReader(
                Objects.requireNonNull(
                        TownHashParityTest.class.getResourceAsStream("/town_hash_parity.json"),
                        "town_hash_parity.json not on test classpath"),
                StandardCharsets.UTF_8)) {
            JsonElement elt = JsonParser.parseReader(reader);
            return elt.getAsJsonObject();
        } catch (java.io.IOException ioe) {
            throw new AssertionError("failed reading town_hash_parity.json", ioe);
        }
    }
}
