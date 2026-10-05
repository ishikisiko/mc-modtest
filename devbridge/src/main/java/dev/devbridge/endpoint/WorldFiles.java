package dev.devbridge.endpoint;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.devbridge.http.BridgeException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The file side of test worlds: folder and snapshot names, the ownership marker, copying a save
 * and moving things to the trash. No game classes, so {@code WorldFilesCheck} runs it on its own.
 *
 * A save is bridge-owned when its folder holds a valid {@value #MARKER} written for that very
 * folder name; only such saves may be restored, reset or deleted. Every move is one atomic
 * rename inside the game directory, so a failure leaves the old state in place.
 */
final class WorldFiles {
    private WorldFiles() {}

    static final String MARKER = "devbridge-world.json";
    static final String LOCK = "session.lock";
    static final String STAGING = ".staging";
    static final Set<String> PRESETS = Set.of("flat", "default", "void");
    static final Set<String> GAME_MODES = Set.of("survival", "creative", "adventure", "spectator");
    static final Set<String> DIFFICULTIES = Set.of("peaceful", "easy", "normal", "hard");
    /** Device names Windows reserves, with or without an extension (NeoForge's list). */
    private static final Pattern RESERVED = Pattern.compile("(?:CON|PRN|AUX|NUL|CLOCK\\$|CONIN\\$|CONOUT\\$|(?:COM|LPT)[¹²³0-9])(?:\\..*)?",
            Pattern.CASE_INSENSITIVE);
    private static final String FORBIDDEN = "<>:\"/\\|?*";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** One portable path segment by Windows rules, else 400. Used for save folders and snapshot names. */
    static String checkName(String what, String name) {
        if (name == null || name.isEmpty()) throw BridgeException.badRequest(what + " is empty");
        if (name.length() > 80) throw BridgeException.badRequest(what + " is longer than 80 characters");
        for (char c : name.toCharArray()) {
            if (c < 32 || FORBIDDEN.indexOf(c) >= 0) {
                String shown = c < 32 ? String.format("\\u%04x", (int) c) : String.valueOf(c);
                throw BridgeException.badRequest(what + " '" + name + "' contains '" + shown + "' (no path separators or characters Windows forbids)");
            }
        }
        if (name.startsWith(".") || name.contains("..")) throw BridgeException.badRequest(what + " '" + name + "' must not start with '.' or contain '..'");
        if (name.startsWith(" ") || name.endsWith(" ") || name.endsWith(".")) throw BridgeException.badRequest(what + " '" + name + "' must not start or end with a space or end with '.'");
        if (RESERVED.matcher(name).matches()) throw BridgeException.badRequest(what + " '" + name + "' is a reserved Windows device name");
        return name;
    }

    /** base/name after checkName, refusing anything that does not normalise to a direct child of base. */
    static Path child(Path base, String what, String name) {
        checkName(what, name);
        Path b = base.toAbsolutePath().normalize();
        Path p = b.resolve(name).normalize();
        if (!b.equals(p.getParent()) || !p.getFileName().toString().equals(name)) {
            throw BridgeException.badRequest(what + " '" + name + "' does not stay inside " + b);
        }
        return p;
    }

    static String oneOf(String what, String value, Set<String> allowed) {
        String v = value == null ? "" : value.strip().toLowerCase();
        if (!allowed.contains(v)) throw BridgeException.badRequest(what + " must be one of " + String.join(", ", allowed.stream().sorted().toList()) + ", not '" + value + "'");
        return v;
    }

    // ---------------------------------------------------------------- settings and marker

    /** What a bridge world is created from; stored in its marker so reset can recreate it. */
    record Spec(String name, String preset, String gameMode, String difficulty, boolean cheats, boolean hardcore,
                long seed, boolean structures, Map<String, String> gameRules, Long time) {

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("name", name);
            o.addProperty("preset", preset);
            o.addProperty("gameMode", gameMode);
            o.addProperty("difficulty", difficulty);
            o.addProperty("cheats", cheats);
            o.addProperty("hardcore", hardcore);
            o.addProperty("seed", Long.toString(seed)); // a string: JSON readers lose precision on 64-bit numbers
            o.addProperty("structures", structures);
            JsonObject rules = new JsonObject();
            gameRules.forEach(rules::addProperty);
            o.add("gameRules", rules);
            if (time != null) o.addProperty("time", time);
            return o;
        }

        /** Strict: a marker that does not parse completely does not make a world bridge-owned. */
        static Spec fromJson(JsonObject o) {
            try {
                Map<String, String> rules = new LinkedHashMap<>();
                if (o.has("gameRules")) for (var e : o.getAsJsonObject("gameRules").entrySet()) rules.put(e.getKey(), e.getValue().getAsString());
                String name = o.get("name").getAsString();
                return new Spec(name, oneOf("preset", o.get("preset").getAsString(), PRESETS),
                        oneOf("gameMode", o.get("gameMode").getAsString(), GAME_MODES),
                        oneOf("difficulty", o.get("difficulty").getAsString(), DIFFICULTIES),
                        bool(o, "cheats"), bool(o, "hardcore"), Long.parseLong(o.get("seed").getAsString()), bool(o, "structures"),
                        rules, o.has("time") && !o.get("time").isJsonNull() ? o.get("time").getAsLong() : null);
            } catch (BridgeException e) {
                throw new IllegalArgumentException("bad settings: " + e.getMessage());
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("bad settings: " + e);
            }
        }

        private static boolean bool(JsonObject o, String key) {
            JsonElement e = o.get(key);
            if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key + " is not true/false");
            return e.getAsBoolean();
        }
    }

    static JsonObject marker(String folder, Spec spec, String createdBy) {
        JsonObject o = new JsonObject();
        o.addProperty("devbridge", "world");
        o.addProperty("version", 1);
        o.addProperty("folder", folder);
        o.addProperty("createdBy", createdBy);
        o.addProperty("createdAt", LocalDateTime.now().toString());
        o.add("settings", spec.toJson());
        return o;
    }

    static void writeMarker(Path dir, JsonObject marker) throws IOException {
        Files.writeString(dir.resolve(MARKER), new GsonBuilder().setPrettyPrinting().create().toJson(marker), StandardCharsets.UTF_8);
    }

    /** The settings of a bridge-owned save, or IllegalArgumentException saying why the folder is not one. */
    static Spec readMarker(Path dir) {
        return readMarker(dir, dir.getFileName().toString());
    }

    /** readMarker for a copy of save folder (a snapshot): the marker must name that folder. */
    static Spec readMarker(Path dir, String folder) {
        Path file = dir.resolve(MARKER);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("no " + MARKER + " in it");
        JsonObject o;
        try {
            o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException(MARKER + " is unreadable: " + e.getMessage());
        }
        if (!o.has("devbridge") || !"world".equals(o.get("devbridge").getAsString()) || !o.has("version") || o.get("version").getAsInt() != 1) {
            throw new IllegalArgumentException(MARKER + " is not a DevBridge world marker (version 1)");
        }
        String marked = o.has("folder") ? o.get("folder").getAsString() : null;
        // A copy of a bridge world under another name is somebody's own save now.
        if (!folder.equals(marked)) throw new IllegalArgumentException(MARKER + " was written for folder '" + marked + "', not '" + folder + "'");
        if (!o.has("settings") || !o.get("settings").isJsonObject()) throw new IllegalArgumentException(MARKER + " has no settings");
        return Spec.fromJson(o.getAsJsonObject("settings"));
    }

    // ---------------------------------------------------------------- files

    /**
     * Whether some process (this one included) holds the save's session.lock. Callers check that the world
     * is not open in this game first: on Linux, closing the probe channel drops this process's own lock.
     */
    static boolean isLocked(Path dir) {
        Path lock = dir.resolve(LOCK);
        try (FileChannel ch = FileChannel.open(lock, StandardOpenOption.WRITE)) {
            try (FileLock l = ch.tryLock()) {
                return l == null;
            }
        } catch (OverlappingFileLockException | AccessDeniedException e) {
            return true;
        } catch (NoSuchFileException e) {
            return false;
        } catch (IOException e) {
            return true; // cannot tell: treat as in use
        }
    }

    /** Copies a save to dst (which must not exist), leaving out its session.lock. */
    static void copySave(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getParent().equals(src) && file.getFileName().toString().equals(LOCK)) return FileVisitResult.CONTINUE;
                Files.copy(file, dst.resolve(src.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** One atomic rename; on failure nothing moved and the message says which step it was. */
    static void move(Path src, Path dst, String step) throws IOException {
        try {
            // Linux would rename onto an empty directory and Windows refuses: refuse on both.
            if (Files.exists(dst, LinkOption.NOFOLLOW_LINKS)) throw new java.nio.file.FileAlreadyExistsException(dst.toString());
            Files.createDirectories(dst.getParent());
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IOException(step + ": could not move " + src + " to " + dst + " (" + e
                    + "; on Windows a file in it may be open in another program)", e);
        }
    }

    /** Moves src into trash as {@code <yyyyMMdd-HHmmss>-<label>} and returns where it went. */
    static Path moveToTrash(Path src, Path trash, String label) throws IOException {
        String base = LocalDateTime.now().format(STAMP) + "-" + label;
        Path dst = trash.resolve(base);
        for (int i = 2; Files.exists(dst, LinkOption.NOFOLLOW_LINKS); i++) dst = trash.resolve(base + "-" + i);
        move(src, dst, "move to trash");
        return dst;
    }

    /** A fresh staging directory under the snapshot store (same volume as the saves, so the final move is a rename). */
    static Path staging(Path snapshots) {
        return snapshots.resolve(STAGING).resolve(LocalDateTime.now().format(STAMP) + "-" + Long.toHexString(System.nanoTime()));
    }

    /** Deletes one of our own staging copies (never a save); false if something stayed behind. */
    static boolean deleteTree(Path p) {
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return true;
        try (Stream<Path> s = Files.walk(p)) {
            for (Path q : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(q);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Snapshots of one save, oldest first: name, created (epoch ms), savedAt (its level.dat time). */
    static JsonArray snapshots(Path snapshots, String folder) {
        JsonArray arr = new JsonArray();
        Path dir = snapshots.resolve(folder);
        if (!Files.isDirectory(dir)) return arr;
        List<Path> list = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isDirectory).forEach(list::add);
        } catch (IOException e) {
            return arr;
        }
        list.sort(Comparator.comparingLong(WorldFiles::created));
        for (Path p : list) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.getFileName().toString());
            o.addProperty("created", created(p));
            o.addProperty("savedAt", mtime(p.resolve("level.dat")));
            arr.add(o);
        }
        return arr;
    }

    static long created(Path p) {
        try {
            return Files.readAttributes(p, BasicFileAttributes.class).creationTime().toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    static long mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
