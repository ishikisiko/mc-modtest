package dev.devbridge.endpoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.devbridge.LogBuffer;
import dev.devbridge.http.BridgeException;
import dev.devbridge.http.BridgeHttpServer;
import dev.devbridge.util.Json;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Log access: in-memory ring buffer, latest.log tail, and crash reports. */
public final class LogEndpoints {
    private LogEndpoints() {}

    private static final List<String> LEVELS = List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL");

    public static void register(BridgeHttpServer http) {

        http.get("/log", "Recent log lines from memory. params: since (seq), level (min, e.g. WARN), logger (substring), contains (substring), thread (substring), limit (default 200)", req -> {
            long since;
            try { since = Long.parseLong(req.str("since", "0").trim()); }
            catch (NumberFormatException e) { throw BridgeException.badRequest("since must be an integer"); }
            String level = req.str("level", null);
            String logger = req.str("logger", null);
            String contains = req.str("contains", null);
            String thread = req.str("thread", null);
            int limit = Math.min(req.integer("limit", 200), 5000);

            int minLevel = level == null ? 0 : LEVELS.indexOf(level.toUpperCase(Locale.ROOT));
            if (minLevel < 0) throw BridgeException.badRequest("level must be one of " + LEVELS);

            Predicate<LogBuffer.Entry> filter = e -> e.seq() > since
                    && LEVELS.indexOf(e.level()) >= minLevel
                    && (logger == null || e.logger().contains(logger))
                    && (thread == null || e.thread().contains(thread))
                    && (contains == null || e.message().contains(contains)
                        || (e.throwable() != null && e.throwable().contains(contains)));

            List<LogBuffer.Entry> entries = LogBuffer.get().query(filter, limit);
            JsonArray arr = new JsonArray();
            for (LogBuffer.Entry e : entries) {
                JsonObject o = Json.obj();
                o.addProperty("seq", e.seq());
                o.addProperty("time", e.time());
                o.addProperty("level", e.level());
                o.addProperty("logger", e.logger());
                o.addProperty("thread", e.thread());
                o.addProperty("message", e.message());
                if (e.throwable() != null) o.addProperty("throwable", e.throwable());
                arr.add(o);
            }
            JsonObject out = Json.obj();
            out.addProperty("latestSeq", LogBuffer.get().latestSeq());
            out.add("entries", arr);
            return out;
        });

        http.post("/log/clear", "Clear the in-memory log buffer", req -> {
            LogBuffer.get().clear();
            return Json.of("cleared");
        });

        http.get("/log/file", "Tail of a log file on disk. params: name (default latest.log), lines (default 200)", req -> {
            String name = req.str("name", "latest.log");
            int lines = Math.min(req.integer("lines", 200), 20000);
            Path file = safeResolve(FMLPaths.GAMEDIR.get().resolve("logs"), name);
            if (!Files.isRegularFile(file)) throw BridgeException.notFound("no such log file: " + name);
            List<String> all;
            try {
                all = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                // latest.log can contain non-UTF8 bytes from mods; fall back to ISO-8859-1
                all = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
            }
            List<String> tail = all.subList(Math.max(0, all.size() - lines), all.size());
            JsonObject out = Json.obj();
            out.addProperty("file", file.toString());
            out.addProperty("totalLines", all.size());
            out.addProperty("text", String.join("\n", tail));
            return out;
        });

        http.get("/crashes", "List crash reports (newest first)", req -> {
            Path dir = FMLPaths.GAMEDIR.get().resolve("crash-reports");
            JsonArray arr = new JsonArray();
            if (Files.isDirectory(dir)) {
                List<Path> files = new ArrayList<>();
                try (Stream<Path> s = Files.list(dir)) {
                    s.filter(Files::isRegularFile).forEach(files::add);
                }
                files.sort((a, b) -> {
                    try { return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a)); }
                    catch (IOException e) { return 0; }
                });
                for (Path p : files) {
                    JsonObject o = Json.obj();
                    o.addProperty("name", p.getFileName().toString());
                    o.addProperty("modified", Files.getLastModifiedTime(p).toMillis());
                    o.addProperty("size", Files.size(p));
                    arr.add(o);
                }
            }
            return arr;
        });

        http.get("/crashes/read", "Read a crash report. params: name (default = newest), maxChars (default 20000)", req -> {
            Path dir = FMLPaths.GAMEDIR.get().resolve("crash-reports");
            String name = req.str("name", null);
            Path file;
            if (name == null) {
                if (!Files.isDirectory(dir)) throw BridgeException.notFound("no crash reports");
                try (Stream<Path> s = Files.list(dir)) {
                    file = s.filter(Files::isRegularFile).max((a, b) -> {
                        try { return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b)); }
                        catch (IOException e) { return 0; }
                    }).orElseThrow(() -> BridgeException.notFound("no crash reports"));
                }
            } else {
                file = safeResolve(dir, name);
            }
            if (!Files.isRegularFile(file)) throw BridgeException.notFound("no such crash report");
            String text;
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                text = Files.readString(file, StandardCharsets.ISO_8859_1);
            }
            int max = req.integer("maxChars", 20000);
            JsonObject out = Json.obj();
            out.addProperty("name", file.getFileName().toString());
            out.addProperty("truncated", text.length() > max);
            out.addProperty("text", text.length() > max ? text.substring(0, max) : text);
            return out;
        });
    }

    private static Path safeResolve(Path base, String name) {
        Path p = base.resolve(name).normalize();
        if (!p.startsWith(base.normalize())) throw BridgeException.badRequest("path escapes base directory");
        return p;
    }
}
