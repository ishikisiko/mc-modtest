package com.example.myvillage.worldplan.capital;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

/** Shared strict decoding; does not coerce strings, booleans, or fractional numbers to integers. */
final class CapitalJson {
    private CapitalJson() {}

    static String id(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9_.:/-]{1,96}"))
            throw new IllegalArgumentException("非法城市元数据身份: " + value);
        return value;
    }

    static String text(JsonObject object, String key) {
        JsonElement value = required(object, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank())
            throw new IllegalArgumentException("城市元数据缺少非空文本: " + key);
        return value.getAsString();
    }

    static JsonElement required(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("城市元数据缺少字段: " + key);
        return value;
    }

    static JsonObject object(JsonObject parent, String key) {
        JsonElement value = required(parent, key);
        if (!value.isJsonObject()) throw new IllegalArgumentException("城市字段必须为对象: " + key);
        return value.getAsJsonObject();
    }

    static JsonArray array(JsonObject parent, String key) {
        JsonElement value = required(parent, key);
        if (!value.isJsonArray()) throw new IllegalArgumentException("城市字段必须为数组: " + key);
        return value.getAsJsonArray();
    }

    static double number(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("城市坐标或尺寸必须为数值");
        double result = value.getAsDouble();
        if (!Double.isFinite(result)) throw new IllegalArgumentException("城市数值必须有限");
        return result;
    }

    static int integer(JsonObject object, String key) {
        JsonElement value = required(object, key);
        number(value);
        try { return new BigDecimal(value.getAsString()).intValueExact(); }
        catch (ArithmeticException | NumberFormatException failure) {
            throw new IllegalArgumentException("城市字段必须为 int 整数: " + key, failure);
        }
    }

    static CapitalGeometry.Point point(JsonElement element) {
        if (!element.isJsonArray() || element.getAsJsonArray().size() != 3)
            throw new IllegalArgumentException("城市坐标必须为 [x,y,z]");
        JsonArray a = element.getAsJsonArray();
        return new CapitalGeometry.Point(number(a.get(0)), number(a.get(1)), number(a.get(2)));
    }

    static CapitalGeometry.Bounds bounds(JsonObject object) {
        return new CapitalGeometry.Bounds(point(required(object, "min")), point(required(object, "max")));
    }

    static <T> List<T> list(JsonObject root, String key, Function<JsonObject, T> decoder) {
        return array(root, key).asList().stream().map(element -> {
            if (!element.isJsonObject()) throw new IllegalArgumentException("城市数组元素必须为对象: " + key);
            return decoder.apply(element.getAsJsonObject());
        }).toList();
    }

    static <T> void unique(List<T> items, Function<T, String> identity) {
        var seen = new HashSet<String>();
        for (T item : items) if (!seen.add(identity.apply(item)))
            throw new IllegalArgumentException("城市元数据身份重复: " + identity.apply(item));
    }
}
