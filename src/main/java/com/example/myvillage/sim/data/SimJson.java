package com.example.myvillage.sim.data;

import com.example.myvillage.sim.SimDataException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict JSON for world-sim data files: no comments, lenient syntax or repeated keys, and
 * {@link Fields} accessors that reject unknown keys and name the file and field in every
 * {@link SimDataException}. Same contract as {@code entity/beast/BeastJson}, without Minecraft types.
 */
public final class SimJson {
    public static final int SCHEMA_VERSION = 1;

    private SimJson() {
    }

    /** Parses one file as strict JSON with no repeated keys; the result must be an object. */
    public static JsonObject parseStrict(String file, Reader source) {
        JsonReader reader = new JsonReader(source);
        reader.setLenient(false);
        try {
            JsonElement element = readStrict(reader, file, "");
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new SimDataException(file, "<root>", "is not valid JSON: content after the top-level value");
            }
            if (!element.isJsonObject()) {
                throw new SimDataException(file, "<root>", "must be a JSON object");
            }
            return element.getAsJsonObject();
        } catch (IOException | IllegalStateException | NumberFormatException exception) {
            throw new SimDataException(file, "<root>", "is not valid JSON: " + exception.getMessage(), exception);
        }
    }

    private static JsonElement readStrict(JsonReader reader, String file, String path) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    String child = path.isEmpty() ? name : path + "." + name;
                    if (object.has(name)) {
                        throw new SimDataException(file, child, "duplicate key");
                    }
                    object.add(name, readStrict(reader, file, child));
                }
                reader.endObject();
                return object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    array.add(readStrict(reader, file, path + "[" + array.size() + "]"));
                }
                reader.endArray();
                return array;
            }
            case STRING -> {
                return new JsonPrimitive(reader.nextString());
            }
            case NUMBER -> {
                return new JsonPrimitive(new BigDecimal(reader.nextString()));
            }
            case BOOLEAN -> {
                return new JsonPrimitive(reader.nextBoolean());
            }
            case NULL -> {
                reader.nextNull();
                return JsonNull.INSTANCE;
            }
            default -> throw new SimDataException(file, path.isEmpty() ? "<root>" : path,
                    "is not valid JSON: unexpected " + reader.peek());
        }
    }

    /** One JSON object whose keys must all be known; every accessor names the file and field. */
    public static final class Fields {
        private final String file;
        private final String path;
        private final JsonObject json;

        /** {@code allowed == null} accepts any key (an object used as a map keyed by name). */
        public Fields(String file, String path, JsonElement element, Set<String> allowed) {
            this.file = file;
            this.path = path;
            if (element == null || !element.isJsonObject()) {
                throw new SimDataException(file, path.isEmpty() ? "<root>" : path, "must be a JSON object");
            }
            this.json = element.getAsJsonObject();
            if (allowed != null) {
                Set<String> unknown = new LinkedHashSet<>(json.keySet());
                unknown.removeAll(allowed);
                if (!unknown.isEmpty()) {
                    throw new SimDataException(file, field(unknown.iterator().next()), "unknown field");
                }
            }
        }

        public static Fields root(String file, JsonObject json, Set<String> allowed) {
            return new Fields(file, "", json, allowed);
        }

        public String file() {
            return file;
        }

        public String field(String key) {
            return path.isEmpty() ? key : path + "." + key;
        }

        public SimDataException error(String key, String problem) {
            return new SimDataException(file, field(key), problem);
        }

        public boolean has(String key) {
            return json.has(key) && !json.get(key).isJsonNull();
        }

        public JsonElement require(String key) {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) {
                throw error(key, "is required");
            }
            return element;
        }

        public void schema() {
            int schema = integer("schema");
            if (schema != SCHEMA_VERSION) {
                throw error("schema", "must be " + SCHEMA_VERSION + ", got " + schema);
            }
        }

        /** The keys of this object in file order, for maps keyed by name. */
        public List<String> keys() {
            return List.copyOf(json.keySet());
        }

        private JsonPrimitive primitive(String key, String expected) {
            JsonElement element = require(key);
            if (!element.isJsonPrimitive()) {
                throw error(key, "must be " + expected);
            }
            return element.getAsJsonPrimitive();
        }

        public int integer(String key) {
            JsonPrimitive primitive = primitive(key, "an integer");
            if (!primitive.isNumber()) {
                throw error(key, "must be an integer");
            }
            try {
                return primitive.getAsBigDecimal().intValueExact();
            } catch (ArithmeticException exception) {
                throw error(key, "must be an integer, got " + primitive.getAsBigDecimal());
            }
        }

        public int integer(String key, int min, int max) {
            int value = integer(key);
            if (value < min || value > max) {
                throw error(key, "must be in " + min + ".." + max + ", got " + value);
            }
            return value;
        }

        public int positiveInteger(String key) {
            return integer(key, 1, Integer.MAX_VALUE);
        }

        public int nonNegativeInteger(String key) {
            return integer(key, 0, Integer.MAX_VALUE);
        }

        public double number(String key) {
            JsonPrimitive primitive = primitive(key, "a number");
            if (!primitive.isNumber()) {
                throw error(key, "must be a number");
            }
            double value = primitive.getAsDouble();
            if (!Double.isFinite(value)) {
                throw error(key, "must be finite");
            }
            return value;
        }

        public double positiveNumber(String key) {
            double value = number(key);
            if (!(value > 0.0)) {
                throw error(key, "must be positive, got " + value);
            }
            return value;
        }

        public double nonNegativeNumber(String key) {
            double value = number(key);
            if (value < 0.0) {
                throw error(key, "must not be negative, got " + value);
            }
            return value;
        }

        public double fraction(String key) {
            double value = number(key);
            if (value < 0.0 || value > 1.0) {
                throw error(key, "must be in 0..1, got " + value);
            }
            return value;
        }

        public String string(String key) {
            JsonPrimitive primitive = primitive(key, "a string");
            if (!primitive.isString()) {
                throw error(key, "must be a string");
            }
            return primitive.getAsString();
        }

        public String nonEmptyString(String key) {
            String value = string(key);
            if (value.isBlank()) {
                throw error(key, "must not be empty");
            }
            return value;
        }

        public String oneOf(String key, Set<String> options) {
            String value = string(key);
            if (!options.contains(value)) {
                throw error(key, "must be one of " + new java.util.TreeSet<>(options) + ", got \"" + value + "\"");
            }
            return value;
        }

        /** A string that must be present but may be {@code null}. */
        public String nullableString(String key) {
            if (!json.has(key)) {
                throw error(key, "is required (use null for none)");
            }
            return json.get(key).isJsonNull() ? null : string(key);
        }

        public boolean bool(String key) {
            JsonPrimitive primitive = primitive(key, "true or false");
            if (!primitive.isBoolean()) {
                throw error(key, "must be true or false");
            }
            return primitive.getAsBoolean();
        }

        public JsonArray array(String key) {
            JsonElement element = require(key);
            if (!element.isJsonArray()) {
                throw error(key, "must be a list");
            }
            return element.getAsJsonArray();
        }

        public JsonArray nonEmptyArray(String key) {
            JsonArray array = array(key);
            if (array.isEmpty()) {
                throw error(key, "needs at least one entry");
            }
            return array;
        }

        /** A non-empty list of distinct, non-blank strings. */
        public List<String> stringList(String key) {
            JsonArray array = nonEmptyArray(key);
            List<String> values = new ArrayList<>(array.size());
            for (int index = 0; index < array.size(); index++) {
                String entryKey = key + "[" + index + "]";
                JsonElement element = array.get(index);
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
                        || element.getAsString().isBlank()) {
                    throw error(entryKey, "must be a non-empty string");
                }
                if (values.contains(element.getAsString())) {
                    throw error(entryKey, "repeats \"" + element.getAsString() + "\"");
                }
                values.add(element.getAsString());
            }
            return List.copyOf(values);
        }

        /** A list of exactly {@code size} finite numbers. */
        public double[] numbers(String key, int size) {
            JsonArray array = array(key);
            if (array.size() != size) {
                throw error(key, "must be a list of " + size + " numbers, got " + array.size());
            }
            double[] values = new double[size];
            for (int index = 0; index < size; index++) {
                JsonElement element = array.get(index);
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()
                        || !Double.isFinite(element.getAsDouble())) {
                    throw error(key, "must be a list of " + size + " finite numbers");
                }
                values[index] = element.getAsDouble();
            }
            return values;
        }

        /** A non-empty list of non-negative finite numbers. */
        public double[] numberList(String key) {
            JsonArray array = nonEmptyArray(key);
            return numbers(key, array.size());
        }

        /** {@code [lo, hi]} integers with {@code lo <= hi}. */
        public int[] intRange(String key) {
            double[] pair = numbers(key, 2);
            int lo = (int) pair[0];
            int hi = (int) pair[1];
            if (lo != pair[0] || hi != pair[1] || lo > hi) {
                throw error(key, "must be [lo, hi] integers with lo <= hi");
            }
            return new int[] {lo, hi};
        }

        public Fields object(String key, Set<String> allowed) {
            return new Fields(file, field(key), require(key), allowed);
        }

        /** Fields over the {@code index}-th element of a list. */
        public Fields element(String key, JsonArray array, int index, Set<String> allowed) {
            return new Fields(file, field(key) + "[" + index + "]", array.get(index), allowed);
        }
    }
}
