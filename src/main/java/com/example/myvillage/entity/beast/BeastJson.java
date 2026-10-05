package com.example.myvillage.entity.beast;

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
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

/**
 * Strict JSON for beast files (server data and client assets alike): no comments, lenient syntax
 * or repeated keys, and {@link Fields} accessors that reject unknown keys and name the file and
 * field in every {@link BeastDataException}.
 */
public final class BeastJson {
    public static final int SCHEMA_VERSION = 1;
    private static final Pattern CLIP_NAME = Pattern.compile("[a-z0-9_]+");

    private BeastJson() {
    }

    /** Parses one file as strict JSON with no repeated keys; the result must be an object. */
    public static JsonObject parseStrict(String file, Reader source) {
        JsonReader reader = new JsonReader(source);
        reader.setLenient(false);
        try {
            JsonElement element = readStrict(reader, file, "");
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new BeastDataException(file, "<root>", "is not valid JSON: content after the top-level value");
            }
            if (!element.isJsonObject()) {
                throw new BeastDataException(file, "<root>", "must be a JSON object");
            }
            return element.getAsJsonObject();
        } catch (IOException | IllegalStateException | NumberFormatException exception) {
            throw new BeastDataException(file, "<root>", "is not valid JSON: " + exception.getMessage(), exception);
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
                        throw new BeastDataException(file, child, "duplicate key");
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
            default -> throw new BeastDataException(file, path.isEmpty() ? "<root>" : path,
                    "is not valid JSON: unexpected " + reader.peek());
        }
    }

    public static <T> T build(Fields fields, Supplier<T> factory) {
        return build(fields.file, fields.path.isEmpty() ? "<root>" : fields.path, factory);
    }

    public static <T> T build(String file, String field, Supplier<T> factory) {
        try {
            return factory.get();
        } catch (IllegalArgumentException exception) {
            throw new BeastDataException(file, field, exception.getMessage(), exception);
        }
    }

    /** One JSON object whose keys must all be known; every accessor names the file and field. */
    public static final class Fields {
        private final String file;
        private final String path;
        private final JsonObject json;

        public Fields(String file, String path, JsonElement element, Set<String> allowed) {
            this.file = file;
            this.path = path;
            if (element == null || !element.isJsonObject()) {
                throw new BeastDataException(file, path.isEmpty() ? "<root>" : path, "must be a JSON object");
            }
            this.json = element.getAsJsonObject();
            // A null allow-list accepts any key (an object used as a map keyed by name).
            Set<String> unknown = new LinkedHashSet<>(json.keySet());
            unknown.removeAll(allowed == null ? json.keySet() : allowed);
            if (!unknown.isEmpty()) {
                throw new BeastDataException(file, field(unknown.iterator().next()), "unknown field");
            }
        }

        public String field(String key) {
            return path.isEmpty() ? key : path + "." + key;
        }

        public BeastDataException error(String key, String problem) {
            return new BeastDataException(file, field(key), problem);
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
            return integerValue(key, primitive.getAsBigDecimal());
        }

        private int integerValue(String key, BigDecimal value) {
            try {
                return value.intValueExact();
            } catch (ArithmeticException exception) {
                throw error(key, "must be an integer, got " + value);
            }
        }

        public int positiveInteger(String key) {
            int value = integer(key);
            if (value <= 0) {
                throw error(key, "must be positive, got " + value);
            }
            return value;
        }

        public int nonNegativeInteger(String key) {
            int value = integer(key);
            if (value < 0) {
                throw error(key, "must not be negative, got " + value);
            }
            return value;
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

        public String clipName(String key) {
            String value = string(key);
            if (!CLIP_NAME.matcher(value).matches()) {
                throw error(key, "must be a clip name matching [a-z0-9_]+, got \"" + value + "\"");
            }
            return value;
        }

        public ResourceLocation id(String key) {
            return parseId(key, string(key));
        }

        private ResourceLocation parseId(String key, String value) {
            int separator = value.indexOf(':');
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (separator <= 0 || separator == value.length() - 1 || id == null) {
                throw error(key, "must be a namespaced id like myvillage:name, got \"" + value + "\"");
            }
            return id;
        }

        public List<ResourceLocation> idList(String key) {
            JsonArray array = array(key);
            if (array.isEmpty()) {
                throw error(key, "needs at least one entry");
            }
            List<ResourceLocation> ids = new ArrayList<>();
            for (int index = 0; index < array.size(); index++) {
                String entryKey = key + "[" + index + "]";
                JsonElement element = array.get(index);
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw error(entryKey, "must be a string id");
                }
                ResourceLocation id = parseId(entryKey, element.getAsString());
                if (ids.contains(id)) {
                    throw error(entryKey, "lists " + id + " twice");
                }
                ids.add(id);
            }
            return ids;
        }

        public JsonArray array(String key) {
            JsonElement element = require(key);
            if (!element.isJsonArray()) {
                throw error(key, "must be a list");
            }
            return element.getAsJsonArray();
        }

        public int[] integerPair(String key) {
            JsonArray array = array(key);
            if (array.size() != 2) {
                throw error(key, "must be [first, last]");
            }
            int[] values = new int[2];
            for (int index = 0; index < 2; index++) {
                JsonElement element = array.get(index);
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                    throw error(key, "must be [first, last] integers");
                }
                values[index] = integerValue(key, element.getAsBigDecimal());
            }
            return values;
        }

        public double[] numberPair(String key) {
            JsonArray array = array(key);
            if (array.size() != 2) {
                throw error(key, "must be a pair [a, b]");
            }
            double[] values = new double[2];
            for (int index = 0; index < 2; index++) {
                JsonElement element = array.get(index);
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()
                        || !Double.isFinite(element.getAsDouble())) {
                    throw error(key, "must be a pair of finite numbers");
                }
                values[index] = element.getAsDouble();
            }
            return values;
        }

        public Fields object(String key, Set<String> allowed) {
            return new Fields(file, field(key), require(key), allowed);
        }

        public String file() {
            return file;
        }

        /** The keys of this object in file order, for maps keyed by name. */
        public List<String> keys() {
            return List.copyOf(json.keySet());
        }

        public boolean bool(String key) {
            JsonPrimitive primitive = primitive(key, "true or false");
            if (!primitive.isBoolean()) {
                throw error(key, "must be true or false");
            }
            return primitive.getAsBoolean();
        }

        /** A string that must be present but may be {@code null}. */
        public String nullableString(String key) {
            if (!json.has(key)) {
                throw error(key, "is required (use null for none)");
            }
            JsonElement element = json.get(key);
            return element.isJsonNull() ? null : string(key);
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

        /** A list of exactly {@code size} non-negative integers. */
        public int[] nonNegativeIntegers(String key, int size) {
            JsonArray array = array(key);
            if (array.size() != size) {
                throw error(key, "must be a list of " + size + " integers, got " + array.size());
            }
            int[] values = new int[size];
            for (int index = 0; index < size; index++) {
                JsonElement element = array.get(index);
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                    throw error(key, "must be a list of " + size + " integers");
                }
                values[index] = integerValue(key, element.getAsBigDecimal());
                if (values[index] < 0) {
                    throw error(key, "must not hold negative values, got " + values[index]);
                }
            }
            return values;
        }
    }
}
