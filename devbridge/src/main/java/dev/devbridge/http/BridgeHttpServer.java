package dev.devbridge.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.devbridge.BridgeConfig;
import dev.devbridge.DevBridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal JSON-over-HTTP server built on the JDK's built-in HttpServer.
 * No third-party dependencies, so the mod stays a single small jar.
 *
 * Routes are registered as {@code "GET /path"} or {@code "POST /path"}.
 * Every handler returns a JsonElement; exceptions become JSON error bodies.
 */
public final class BridgeHttpServer {

    @FunctionalInterface
    public interface Handler {
        JsonElement handle(Request req) throws Exception;
    }

    private record Route(String method, String path, String description, Handler handler) {}

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final BridgeConfig config;
    private final Map<String, Route> routes = new LinkedHashMap<>();
    private HttpServer server;
    private ExecutorService executor;

    public BridgeHttpServer(BridgeConfig config) {
        this.config = config;
        get("/routes", "List all available routes", req -> {
            var arr = new com.google.gson.JsonArray();
            for (Route r : routes.values()) {
                JsonObject o = new JsonObject();
                o.addProperty("method", r.method());
                o.addProperty("path", r.path());
                o.addProperty("description", r.description());
                arr.add(o);
            }
            return arr;
        });
    }

    public BridgeConfig config() {
        return config;
    }

    public void get(String path, String description, Handler handler) {
        routes.put("GET " + path, new Route("GET", path, description, handler));
    }

    public void post(String path, String description, Handler handler) {
        routes.put("POST " + path, new Route("POST", path, description, handler));
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(config.bind(), config.port()), 16);
        } catch (IOException e) {
            throw new IllegalStateException("[DevBridge] failed to bind " + config.bind() + ":" + config.port(), e);
        }
        AtomicInteger counter = new AtomicInteger();
        executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "devbridge-http-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/", this::dispatch);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void dispatch(HttpExchange ex) throws IOException {
        long started = System.nanoTime();
        int status = 200;
        JsonElement result;
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

            if (method.equals("OPTIONS")) {
                ex.getResponseHeaders().add("Allow", "GET, POST, OPTIONS");
                ex.sendResponseHeaders(204, -1);
                return;
            }

            if (!path.equals("/ping") && !authorized(ex)) {
                throw new BridgeException(401, "missing or invalid bearer token");
            }

            Route route = routes.get(method + " " + path);
            if (route == null) {
                // Allow POSTing to GET routes and vice versa; most handlers don't care.
                route = routes.get((method.equals("GET") ? "POST " : "GET ") + path);
            }
            if (route == null) throw BridgeException.notFound("no route for " + method + " " + path);

            Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());
            JsonObject body = null;
            if (method.equals("POST")) {
                try (InputStream in = ex.getRequestBody()) {
                    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    if (!text.isBlank()) {
                        JsonElement parsed = JsonParser.parseString(text);
                        if (!parsed.isJsonObject()) throw BridgeException.badRequest("body must be a JSON object");
                        body = parsed.getAsJsonObject();
                    }
                }
            }

            JsonElement payload = route.handler().handle(new Request(method, path, query, body));
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("ok", true);
            wrapper.add("result", payload == null ? com.google.gson.JsonNull.INSTANCE : payload);
            result = wrapper;
        } catch (BridgeException e) {
            status = e.status();
            result = error(e.getMessage(), e.getCause());
        } catch (Throwable t) {
            status = 500;
            DevBridge.LOGGER.warn("[DevBridge] request failed", t);
            result = error(t.toString(), t);
        }

        byte[] bytes = GSON.toJson(result).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("X-DevBridge-Millis", Long.toString((System.nanoTime() - started) / 1_000_000));
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static JsonObject error(String message, Throwable cause) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", message);
        if (cause != null) {
            List<String> frames = new ArrayList<>();
            Throwable t = cause;
            int depth = 0;
            while (t != null && depth++ < 5) {
                frames.add(t.toString());
                for (int i = 0; i < Math.min(8, t.getStackTrace().length); i++) {
                    frames.add("    at " + t.getStackTrace()[i]);
                }
                t = t.getCause();
            }
            o.addProperty("trace", String.join("\n", frames));
        }
        return o;
    }

    private boolean authorized(HttpExchange ex) {
        String expected = config.token();
        String provided = null;
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            provided = auth.substring(7).trim();
        }
        if (provided == null) provided = ex.getRequestHeaders().getFirst("X-DevBridge-Token");
        if (provided == null) return false;
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            out.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return out;
    }
}
