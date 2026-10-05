package com.example.myvillage.sim.cli;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A language file read the way Minecraft formats it: positional {@code %1$s} templates, and event
 * params starting with {@code @} translated as keys. Missing keys render as {@code [key]} so a gap
 * shows up in the text instead of vanishing.
 */
public final class Lang {
    private final Map<String, String> entries = new HashMap<>();

    public Lang(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                entries.put(e.getKey(), e.getValue().getAsString());
            }
        }
    }

    public boolean has(String key) {
        return entries.containsKey(key);
    }

    public String raw(String key) {
        String value = entries.get(key);
        return value == null ? "[" + key + "]" : value;
    }

    /** Formats {@code key} with params; a param starting with {@code @} is itself translated. */
    public String format(String key, List<String> params) {
        Object[] args = new Object[params.size()];
        for (int i = 0; i < args.length; i++) {
            String p = params.get(i);
            args[i] = p.startsWith("@") ? raw(p.substring(1)) : p;
        }
        String template = raw(key);
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (java.util.IllegalFormatException e) {
            return template + " " + params;
        }
    }

    public String format(String key, Object... params) {
        String[] strings = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            strings[i] = String.valueOf(params[i]);
        }
        return format(key, List.of(strings));
    }
}
