package com.example.myvillage.cultivation.crafting;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Strict bundled balance rules for initial rolls and same-recipe product quality ceilings. */
public final class CraftingRules {
    public static final String RESOURCE = "/data/myvillage/crafting/rules.json";
    public record Threshold(int quality, int minimum) { }
    private final int initialMin, initialMax;
    private final List<Threshold> thresholds;
    private CraftingRules(JsonObject root) {
        fields(root, "version", "initial_roll", "quality_thresholds");
        integer(root, "version", 1, 1);
        JsonObject roll = object(root.get("initial_roll")); fields(roll, "min", "max");
        initialMin = integer(roll, "min", CraftingAptitudes.MIN_SCORE, CraftingAptitudes.MAX_SCORE);
        initialMax = integer(roll, "max", initialMin, CraftingAptitudes.MAX_SCORE);
        JsonElement ladder = root.get("quality_thresholds");
        if (!ladder.isJsonArray() || ladder.getAsJsonArray().size() != 4) throw invalid("Four quality thresholds required");
        List<Threshold> values = new ArrayList<>(); int previous = -1;
        for (JsonElement entry : ladder.getAsJsonArray()) {
            JsonObject t = object(entry); fields(t, "quality", "minimum");
            int quality = integer(t, "quality", 1, 4), minimum = integer(t, "minimum", 0, 100);
            if (quality != values.size() + 1 || minimum <= previous || (quality == 1 && minimum != 0))
                throw invalid("Quality thresholds must be complete, ordered and start at zero");
            values.add(new Threshold(quality, minimum)); previous = minimum;
        }
        thresholds = List.copyOf(values);
    }
    private static final class Holder { static final CraftingRules RULES = load(); }
    public static CraftingRules get() { return Holder.RULES; }
    private static CraftingRules load() {
        try (InputStream stream = CraftingRules.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            return parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) { throw new IllegalStateException("Cannot load " + RESOURCE, e); }
    }
    public static CraftingRules parse(Reader source) {
        try (JsonReader reader = new JsonReader(source)) {
            reader.setLenient(false);
            JsonElement root = read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("Trailing JSON content");
            return new CraftingRules(object(root));
        } catch (IOException | NumberFormatException | IllegalStateException e) { throw new IllegalArgumentException("Invalid crafting rules", e); }
    }
    public int initialMin() { return initialMin; }
    public int initialMax() { return initialMax; }
    public List<Threshold> thresholds() { return thresholds; }
    public int qualityCap(int score) {
        CraftingAptitudes.checkScore(score);
        int cap = 1;
        for (Threshold threshold : thresholds) if (score >= threshold.minimum()) cap = threshold.quality();
        return cap;
    }
    public boolean allowsQuality(int score, int requested) {
        return requested >= 1 && requested <= qualityCap(score);
    }
    private static void fields(JsonObject object, String... fields) {
        Set<String> expected = Set.of(fields);
        if (!object.keySet().equals(expected)) throw invalid("Unexpected or missing rule fields");
    }
    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw invalid("Object required"); return value.getAsJsonObject();
    }
    private static int integer(JsonObject object, String field, int min, int max) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("Integer required: " + field);
        try { int n = value.getAsBigDecimal().intValueExact();
            if (n < min || n > max) throw invalid("Out of bounds: " + field); return n;
        } catch (ArithmeticException e) { throw invalid("Integer required: " + field); }
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    private static JsonElement read(JsonReader reader) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject value = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (value.has(key)) throw invalid("Duplicate rule field: " + key);
                    value.add(key, read(reader));
                }
                reader.endObject(); yield value;
            }
            case BEGIN_ARRAY -> {
                JsonArray value = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) value.add(read(reader));
                reader.endArray(); yield value;
            }
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case STRING -> new JsonPrimitive(reader.nextString());
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw invalid("Unexpected JSON token");
        };
    }
}
