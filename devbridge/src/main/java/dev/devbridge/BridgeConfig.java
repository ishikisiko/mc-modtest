package dev.devbridge;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Configuration for the bridge. Stored at {@code config/devbridge.json}.
 *
 * Resolution order for every key: JVM system property {@code devbridge.<key>},
 * then environment variable {@code DEVBRIDGE_<KEY>}, then the JSON file.
 * A random token is generated and written to the file on first run.
 *
 * @param bind          address to bind. "127.0.0.1" by default. Use your Tailscale
 *                      IP (100.x.y.z) to reach it from another machine.
 * @param port          TCP port.
 * @param token         bearer token required on every request except /ping.
 * @param allowReflect  whether the reflection endpoints are enabled.
 * @param timeoutMillis how long an HTTP request may wait for the game thread.
 * @param logBufferSize how many log lines to keep in memory.
 * @param automationWindow window size "WIDTHxHEIGHT" applied when the game was started with an
 *                      open-world request (an automated launch); empty to leave the window alone.
 * @param keepMouseFree  worlds DevBridge opens start in watch mode: the game never captures the
 *                      user's mouse until the toggle key (default F8) switches to play.
 * @param automationScreen screen to open once a world DevBridge opened is up: "inventory", or
 *                      empty (default) for none.
 */
public record BridgeConfig(String bind, int port, String token, boolean allowReflect,
                           long timeoutMillis, int logBufferSize, String automationWindow,
                           boolean keepMouseFree, String automationScreen) {

    public static BridgeConfig load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("devbridge.json");
        JsonObject json = new JsonObject();
        boolean dirty = false;

        if (Files.exists(file)) {
            try {
                json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                DevBridge.LOGGER.warn("[DevBridge] could not parse {}, using defaults", file, e);
            }
        }

        if (!json.has("bind")) { json.addProperty("bind", "127.0.0.1"); dirty = true; }
        if (!json.has("port")) { json.addProperty("port", 8787); dirty = true; }
        if (!json.has("allowReflect")) { json.addProperty("allowReflect", true); dirty = true; }
        if (!json.has("timeoutMillis")) { json.addProperty("timeoutMillis", 15000); dirty = true; }
        if (!json.has("logBufferSize")) { json.addProperty("logBufferSize", 5000); dirty = true; }
        if (!json.has("automationWindow")) { json.addProperty("automationWindow", "1600x900"); dirty = true; }
        if (!json.has("keepMouseFree")) { json.addProperty("keepMouseFree", true); dirty = true; }
        if (!json.has("automationScreen")) { json.addProperty("automationScreen", ""); dirty = true; }
        if (!json.has("token") || json.get("token").getAsString().isBlank()) {
            json.addProperty("token", randomToken());
            dirty = true;
        }

        if (dirty) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
            } catch (IOException e) {
                DevBridge.LOGGER.warn("[DevBridge] could not write {}", file, e);
            }
        }

        return new BridgeConfig(
                override("bind", json.get("bind").getAsString()),
                Integer.parseInt(override("port", json.get("port").getAsString())),
                override("token", json.get("token").getAsString()),
                Boolean.parseBoolean(override("allowReflect", json.get("allowReflect").getAsString())),
                Long.parseLong(override("timeoutMillis", json.get("timeoutMillis").getAsString())),
                Integer.parseInt(override("logBufferSize", json.get("logBufferSize").getAsString())),
                override("automationWindow", json.get("automationWindow").getAsString()).strip(),
                Boolean.parseBoolean(override("keepMouseFree", json.get("keepMouseFree").getAsString())),
                override("automationScreen", json.get("automationScreen").getAsString()).strip()
        );
    }

    private static String override(String key, String fallback) {
        String sys = System.getProperty("devbridge." + key);
        if (sys != null && !sys.isBlank()) return sys;
        String env = System.getenv("DEVBRIDGE_" + key.toUpperCase());
        if (env != null && !env.isBlank()) return env;
        return fallback;
    }

    private static String randomToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
