package dev.devbridge.endpoint;

import com.google.gson.JsonObject;
import dev.devbridge.http.BridgeException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Self-check of the game-free world logic (names, marker, copy and trash, operation tracking).
 * Runs with {@code gradle selfCheck} (part of {@code check}/{@code build}); no test framework.
 */
public final class WorldFilesCheck {
    private static int failures, checks;

    public static void main(String[] args) throws Exception {
        names();
        Path tmp = Files.createTempDirectory("devbridge-selfcheck");
        try {
            marker(tmp);
            files(tmp);
        } finally {
            try (Stream<Path> s = Files.walk(tmp)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
        ops();
        System.out.println("WorldFilesCheck: " + checks + " checks, " + failures + " failed");
        if (failures > 0) System.exit(1);
    }

    private static void names() {
        for (String ok : new String[]{"dbtest1", "New World (1)", "测试 世界", "a.b", "x-y_z", "COM", "CONSOLE", "lpt10"}) {
            check(accepts(ok), "accepts name '" + ok + "'");
        }
        for (String bad : new String[]{"", ".", "..", "../x", "a/b", "a\\b", "/abs", "C:", "C:\\x", ".hidden", "x..y", "CON", "con.txt", "Aux",
                "NUL.dat", "COM1", "lpt9.x", "COM¹", "CONIN$", "name.", "name ", " name", "a*b", "a?b", "a<b", "a|b", "a\"b", "a\u0001b",
                "x".repeat(81)}) {
            check(!accepts(bad), "refuses name '" + bad.replace("\u0001", "\\u0001") + "'");
        }
        Path base = Path.of("/tmp/saves");
        check(WorldFiles.child(base, "folder", "w1").equals(base.resolve("w1")), "child resolves inside base");
    }

    private static boolean accepts(String name) {
        try {
            WorldFiles.checkName("folder", name);
            WorldFiles.child(Path.of("/tmp/saves"), "folder", name);
            return true;
        } catch (BridgeException e) {
            check(e.status() == 400, "name error is a 400: " + e.getMessage());
            return false;
        }
    }

    private static void marker(Path tmp) throws IOException {
        WorldFiles.Spec spec = new WorldFiles.Spec("Test", "flat", "creative", "normal", true, false, -1234567890123456789L, false,
                Map.of("doDaylightCycle", "false", "randomTickSpeed", "0"), 6000L);
        check(WorldFiles.Spec.fromJson(spec.toJson()).equals(spec), "spec survives JSON (64-bit seed kept)");
        WorldFiles.Spec noTime = new WorldFiles.Spec("T", "void", "survival", "hard", false, true, 1, true, Map.of(), null);
        check(WorldFiles.Spec.fromJson(noTime.toJson()).equals(noTime), "spec without time survives JSON");

        Path w1 = Files.createDirectories(tmp.resolve("saves/w1"));
        refused(w1, "a save without a marker is not bridge-owned");
        WorldFiles.writeMarker(w1, WorldFiles.marker("w1", spec, "DevBridge test"));
        check(spec.equals(WorldFiles.readMarker(w1)), "marker written for w1 makes w1 bridge-owned");

        Path w2 = Files.createDirectories(tmp.resolve("saves/w2"));
        Files.copy(w1.resolve(WorldFiles.MARKER), w2.resolve(WorldFiles.MARKER));
        refused(w2, "a marker copied into another folder does not count");
        check(spec.equals(WorldFiles.readMarker(w2, "w1")), "a snapshot of w1 (any directory name) is checked against folder w1");

        Files.writeString(w2.resolve(WorldFiles.MARKER), "{not json", StandardCharsets.UTF_8);
        refused(w2, "an unreadable marker does not count");
        JsonObject m = WorldFiles.marker("w2", spec, "x");
        m.addProperty("version", 2);
        WorldFiles.writeMarker(w2, m);
        refused(w2, "an unknown marker version does not count");
        m = WorldFiles.marker("w2", spec, "x");
        m.getAsJsonObject("settings").addProperty("gameMode", "god");
        WorldFiles.writeMarker(w2, m);
        refused(w2, "a marker with bad settings does not count");
        m = WorldFiles.marker("w2", spec, "x");
        m.getAsJsonObject("settings").addProperty("cheats", "yes");
        WorldFiles.writeMarker(w2, m);
        refused(w2, "a marker with a non-boolean flag does not count");
    }

    private static void refused(Path dir, String what) {
        try {
            WorldFiles.readMarker(dir);
            check(false, what);
        } catch (IllegalArgumentException e) {
            check(true, what + " (" + e.getMessage() + ")");
        }
    }

    private static void files(Path tmp) throws IOException {
        Path save = Files.createDirectories(tmp.resolve("saves/s1"));
        Files.writeString(save.resolve("level.dat"), "L");
        Files.writeString(save.resolve(WorldFiles.LOCK), "lock");
        Files.createDirectories(save.resolve("region"));
        Files.writeString(save.resolve("region/r.0.0.mca"), "R");
        Files.createDirectories(save.resolve("data/" + WorldFiles.LOCK)); // a nested name like the lock file is copied

        Path snaps = tmp.resolve("devbridge-snapshots");
        Path staging = WorldFiles.staging(snaps);
        check(staging.getParent().getFileName().toString().equals(WorldFiles.STAGING), "staging lives in the snapshot store");
        WorldFiles.copySave(save, staging);
        check(Files.isRegularFile(staging.resolve("level.dat")) && Files.readString(staging.resolve("region/r.0.0.mca")).equals("R"), "copySave copies the tree");
        check(!Files.exists(staging.resolve(WorldFiles.LOCK)), "copySave leaves out session.lock");
        check(Files.isDirectory(staging.resolve("data/" + WorldFiles.LOCK)), "copySave keeps nested entries named like the lock");

        Path dest = snaps.resolve("s1/first");
        WorldFiles.move(staging, dest, "store");
        check(Files.isDirectory(dest) && !Files.exists(staging), "move renames the staging copy into place");
        check(WorldFiles.snapshots(snaps, "s1").size() == 1 && WorldFiles.snapshots(snaps, "s1").get(0).getAsJsonObject().get("name").getAsString().equals("first"),
                "snapshots lists the stored snapshot");
        check(WorldFiles.snapshots(snaps, "none").isEmpty(), "snapshots of an unknown folder is empty");

        Path other = Files.createDirectories(tmp.resolve("other"));
        try {
            WorldFiles.move(other, dest, "store");
            check(false, "move refuses an existing target");
        } catch (IOException e) {
            check(Files.isDirectory(other) && Files.isRegularFile(dest.resolve("level.dat")), "move refuses an existing target and changes nothing");
        }

        Path trash = tmp.resolve("devbridge-trash");
        Path t1 = WorldFiles.moveToTrash(dest, trash, "snapshot-s1-first");
        check(!Files.exists(dest) && Files.isRegularFile(t1.resolve("level.dat")) && t1.getParent().equals(trash)
                && t1.getFileName().toString().endsWith("-snapshot-s1-first"), "moveToTrash moves into a timestamped entry");
        Path again = Files.createDirectories(tmp.resolve("again"));
        Path t2 = WorldFiles.moveToTrash(again, trash, "snapshot-s1-first");
        Path again2 = Files.createDirectories(tmp.resolve("again2"));
        Path t3 = WorldFiles.moveToTrash(again2, trash, "snapshot-s1-first");
        check(!t1.equals(t2) && !t2.equals(t3) && !t1.equals(t3), "same-second trash entries get distinct names");

        Path w = Files.createDirectories(tmp.resolve("saves/locked"));
        check(!WorldFiles.isLocked(w), "no session.lock: not locked");
        try (FileChannel ch = FileChannel.open(w.resolve(WorldFiles.LOCK), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = ch.lock()) {
            check(lock.isValid() && WorldFiles.isLocked(w), "session.lock held by this process: locked");
        }
        check(WorldFiles.deleteTree(tmp.resolve("saves/s1")) && !Files.exists(tmp.resolve("saves/s1")), "deleteTree removes a staging tree");
    }

    private static void ops() {
        WorldOps.Op a = WorldOps.begin("create", "w1");
        check(WorldOps.busy(), "begin makes the bridge busy");
        try {
            WorldOps.begin("restore", "w1");
            check(false, "a second operation is refused while one runs");
        } catch (BridgeException e) {
            check(e.status() == 409 && e.getMessage().contains("create"), "a second operation is refused with 409 naming the running one");
        }
        JsonObject s = new JsonObject();
        WorldOps.status(s);
        check(s.get("busy").getAsString().equals("create") && s.getAsJsonObject("current").get("id").getAsLong() == a.id, "status shows the running operation");
        check(WorldOps.get(a.id).get("state").getAsString().equals("running"), "get(id) of the running operation");
        a.result.addProperty("path", "x");
        WorldOps.finish(a, null);
        s = new JsonObject();
        WorldOps.status(s);
        check(s.get("busy").isJsonNull() && s.get("lastError").isJsonNull() && s.getAsJsonObject("lastOperation").get("ok").getAsBoolean(), "a finished operation clears busy, no lastError");
        WorldOps.Op b = WorldOps.begin("restore", "w1");
        WorldOps.finish(b, "restore refused: not bridge-owned");
        s = new JsonObject();
        WorldOps.status(s);
        check(s.get("lastError").getAsString().contains("refused") && s.getAsJsonObject("lastOperation").get("id").getAsLong() == b.id, "a failed operation shows in lastError");
        check(WorldOps.get(a.id).get("state").getAsString().equals("done") && WorldOps.get(a.id).getAsJsonObject("result").get("path").getAsString().equals("x"),
                "get(id) keeps earlier operations with their result");
        check(WorldOps.get(b.id).get("state").getAsString().equals("failed"), "get(id) of a failed operation");
        check(WorldOps.get(b.id + 5).get("state").getAsString().equals("not started"), "get(id) of a future id");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL " + what);
        }
    }
}
