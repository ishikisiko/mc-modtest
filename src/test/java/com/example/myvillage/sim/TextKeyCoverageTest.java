package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.cli.ChronicleWriter;
import com.example.myvillage.sim.engine.TextKeys;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every key the engine (and the chronicle report) can emit exists in both language files, and
 * each template uses exactly the positional slots {@code %1$s..%n$s} for its n params.
 */
class TextKeyCoverageTest {
    private static final Path LANG = Path.of("src/main/resources/assets/myvillage/lang");
    private static final Pattern SLOT = Pattern.compile("%(\\d+)\\$s");
    private static final Pattern ANY_FORMAT = Pattern.compile("%(?!\\d+\\$s)");

    private static Map<String, String> lang(String id) throws IOException {
        JsonObject json = JsonParser.parseString(
                Files.readString(LANG.resolve(id + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            out.put(e.getKey(), e.getValue().getAsString());
        }
        return out;
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void everyEmittableKeyExistsWithMatchingSlots(String langId) throws IOException {
        Map<String, String> lang = lang(langId);
        Map<String, Integer> keys = new TreeMap<>(TextKeys.all(SimFixtures.data()));
        keys.putAll(ChronicleWriter.KEYS);
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Integer> e : keys.entrySet()) {
            String template = lang.get(e.getKey());
            if (template == null) {
                problems.add("missing " + e.getKey());
                continue;
            }
            TreeSet<Integer> slots = new TreeSet<>();
            Matcher m = SLOT.matcher(template);
            while (m.find()) {
                slots.add(Integer.parseInt(m.group(1)));
            }
            TreeSet<Integer> expected = new TreeSet<>();
            for (int i = 1; i <= e.getValue(); i++) {
                expected.add(i);
            }
            if (!slots.equals(expected)) {
                problems.add(e.getKey() + " uses slots " + slots + " but takes " + e.getValue() + " params");
            }
            if (ANY_FORMAT.matcher(template).find()) {
                problems.add(e.getKey() + " has a non-positional % in \"" + template + "\"");
            }
        }
        for (String key : lang.keySet()) {
            if (key.startsWith(TextKeys.P) && !keys.containsKey(key)) {
                problems.add("orphan event key " + key + " (nothing emits it)");
            }
        }
        assertTrue(problems.isEmpty(), langId + ":\n" + String.join("\n", problems));
    }
}
