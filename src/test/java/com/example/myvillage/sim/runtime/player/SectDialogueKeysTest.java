package com.example.myvillage.sim.runtime.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.Admission;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every dialogue line {@link SectDialogueKeys} can emit exists in both language files with exactly
 * the positional slots {@code %1$s..%n$s} for its params, no {@code world_sim.dialogue.*} key is
 * orphaned, and the screen, name-tag and chat keys of the dialogue exist with their slots.
 */
class SectDialogueKeysTest {
    private static final Path LANG = Path.of("src/main/resources/assets/myvillage/lang");
    private static final Pattern SLOT = Pattern.compile("%(\\d+)\\$s");
    private static final Pattern ANY_FORMAT = Pattern.compile("%(?!\\d+\\$s)");

    /** The other keys of the dialogue and their param counts. */
    private static final Map<String, Integer> OTHER_KEYS = Map.ofEntries(
            Map.entry("entity.myvillage.cultivator.avatar.steward", 3),
            Map.entry("screen.myvillage.sect_dialogue.title", 3),
            Map.entry("screen.myvillage.sect_dialogue.option.join", 0),
            Map.entry("screen.myvillage.sect_dialogue.option.leave", 0),
            Map.entry("screen.myvillage.sect_dialogue.option.farewell", 0),
            Map.entry("screen.myvillage.sect_dialogue.option.apprentice", 0),
            Map.entry("screen.myvillage.sect_dialogue.option.task_accept", 0),
            Map.entry("screen.myvillage.sect_dialogue.option.task_turn_in", 0),
            Map.entry("screen.myvillage.sect_dialogue.role.steward", 0),
            Map.entry("screen.myvillage.sect_dialogue.role.elder", 0),
            Map.entry("screen.myvillage.sect_dialogue.none", 0),
            Map.entry("screen.myvillage.sect_dialogue.standing", 1),
            Map.entry("message.myvillage.world.sect.joined", 1),
            Map.entry("message.myvillage.world.sect.left", 1),
            Map.entry("message.myvillage.world.sect.unavailable", 0));

    private static Map<String, String> lang(String id) throws IOException {
        JsonObject json = JsonParser.parseString(
                Files.readString(LANG.resolve(id + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            out.put(e.getKey(), e.getValue().getAsString());
        }
        return out;
    }

    private static void checkSlots(String key, String template, int params, List<String> problems) {
        TreeSet<Integer> slots = new TreeSet<>();
        Matcher m = SLOT.matcher(template);
        while (m.find()) {
            slots.add(Integer.parseInt(m.group(1)));
        }
        TreeSet<Integer> expected = new TreeSet<>();
        for (int i = 1; i <= params; i++) {
            expected.add(i);
        }
        if (!slots.equals(expected)) {
            problems.add(key + " uses slots " + slots + " but takes " + params + " params");
        }
        if (ANY_FORMAT.matcher(template).find()) {
            problems.add(key + " has a non-positional % in \"" + template + "\"");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void everyDialogueKeyExistsWithMatchingSlots(String langId) throws IOException {
        Map<String, String> lang = lang(langId);
        List<String> problems = new ArrayList<>();
        TreeSet<String> emitted = new TreeSet<>();
        for (Map.Entry<String, Integer> e : SectDialogueKeys.VARIANTS.entrySet()) {
            int params = SectDialogueKeys.PARAMS.get(e.getKey());
            for (int n = 1; n <= e.getValue(); n++) {
                String key = SectDialogueKeys.key(e.getKey(), n);
                emitted.add(key);
                String template = lang.get(key);
                if (template == null) {
                    problems.add("missing " + key);
                } else {
                    checkSlots(key, template, params, problems);
                }
            }
        }
        for (String key : lang.keySet()) {
            if (key.startsWith(SectDialogueKeys.PREFIX) && !emitted.contains(key)) {
                problems.add("orphan dialogue key " + key + " (no variant table lists it)");
            }
        }
        for (Map.Entry<String, Integer> e : OTHER_KEYS.entrySet()) {
            String template = lang.get(e.getKey());
            if (template == null) {
                problems.add("missing " + e.getKey());
            } else {
                checkSlots(e.getKey(), template, e.getValue(), problems);
            }
        }
        assertTrue(problems.isEmpty(), langId + ":\n" + String.join("\n", problems));
    }

    @Test
    void bothTablesListTheSameScenes() {
        assertEquals(SectDialogueKeys.VARIANTS.keySet(), SectDialogueKeys.PARAMS.keySet());
        for (int n : SectDialogueKeys.VARIANTS.values()) {
            assertTrue(n >= 1);
        }
    }

    @Test
    void everyAdmissionReasonHasARefusalLine() throws IllegalAccessException {
        for (Field f : Admission.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                String reason = (String) f.get(null);
                if (!Admission.OK.equals(reason)) {
                    assertTrue(SectDialogueKeys.VARIANTS.containsKey("steward.refuse." + reason), reason);
                }
            }
        }
    }
}
