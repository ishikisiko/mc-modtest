package dev.devbridge.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * A parsed request: query parameters and (for POST) a JSON body.
 * Lookup helpers check the JSON body first, then query parameters, so every
 * endpoint can be called either way.
 */
public final class Request {
    private final String method;
    private final String path;
    private final Map<String, String> query;
    private final JsonObject body;

    public Request(String method, String path, Map<String, String> query, JsonObject body) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.body = body == null ? new JsonObject() : body;
    }

    public String method() { return method; }
    public String path() { return path; }
    public JsonObject body() { return body; }

    public boolean has(String key) {
        return (body.has(key) && !body.get(key).isJsonNull()) || query.containsKey(key);
    }

    public String str(String key, String def) {
        if (body.has(key) && !body.get(key).isJsonNull()) {
            JsonElement e = body.get(key);
            return e.isJsonPrimitive() ? e.getAsString() : e.toString();
        }
        return query.getOrDefault(key, def);
    }

    public String require(String key) {
        String v = str(key, null);
        if (v == null || v.isEmpty()) throw BridgeException.badRequest("missing parameter: " + key);
        return v;
    }

    public int integer(String key, int def) {
        String v = str(key, null);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { throw BridgeException.badRequest("parameter " + key + " must be an integer"); }
    }

    public int requireInt(String key) {
        String v = require(key);
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { throw BridgeException.badRequest("parameter " + key + " must be an integer"); }
    }

    public double dbl(String key, double def) {
        String v = str(key, null);
        if (v == null) return def;
        try { return Double.parseDouble(v.trim()); }
        catch (NumberFormatException e) { throw BridgeException.badRequest("parameter " + key + " must be a number"); }
    }

    public boolean bool(String key, boolean def) {
        String v = str(key, null);
        if (v == null) return def;
        return v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
    }

    public JsonElement json(String key) {
        return body.get(key);
    }
}
