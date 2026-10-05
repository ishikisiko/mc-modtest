package dev.devbridge.endpoint;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.devbridge.DevBridge;
import dev.devbridge.http.BridgeException;
import dev.devbridge.http.BridgeHttpServer;
import dev.devbridge.http.Request;
import dev.devbridge.util.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static dev.devbridge.endpoint.ClientEndpoints.onClient;
import static dev.devbridge.endpoint.ClientWorldEndpoints.client;
import static dev.devbridge.endpoint.ClientWorldEndpoints.leave;
import static dev.devbridge.endpoint.ClientWorldEndpoints.message;
import static dev.devbridge.endpoint.ClientWorldEndpoints.openFolder;
import static dev.devbridge.endpoint.ClientWorldEndpoints.run;
import static dev.devbridge.endpoint.ClientWorldEndpoints.saves;
import static dev.devbridge.endpoint.ClientWorldEndpoints.started;
import static dev.devbridge.endpoint.ClientWorldEndpoints.switchTo;

/**
 * Client-only save management for test worlds: create, save, snapshot, restore, reset, delete.
 *
 * Each route is a world operation ({@link WorldOps}): it checks its arguments, returns an
 * operation id at once and runs on the client thread (leave, create, open) and a file thread
 * (copies and moves), so the game keeps drawing. Poll /client/world or /client/world/operation.
 *
 * Safety: restore, reset and delete only act on bridge-owned saves (a valid
 * {@value WorldFiles#MARKER} for that folder). Snapshots live in {@code devbridge-snapshots/}
 * and anything replaced or deleted goes to {@code devbridge-trash/}, both in the game directory.
 * A save is never copied or moved while a server has it open.
 */
public final class ClientSaveEndpoints {
    private ClientSaveEndpoints() {}

    private static final ExecutorService FILES = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "devbridge-worlds");
        t.setDaemon(true);
        return t;
    });

    @FunctionalInterface
    private interface FileStep {
        void run() throws IOException;
    }

    static Path snapshots() {
        return FMLPaths.GAMEDIR.get().toAbsolutePath().normalize().resolve("devbridge-snapshots");
    }

    static Path trash() {
        return FMLPaths.GAMEDIR.get().toAbsolutePath().normalize().resolve("devbridge-trash");
    }

    public static void register(BridgeHttpServer http) {

        http.post("/client/world/create", "Create a test world and open it in watch mode (leaving the current one). params: folder, name, preset flat (default) | default | void, "
                + "gameMode (creative), difficulty (normal), cheats (true), seed, hardcore (false), structures (default preset only), gameRules {name: value}, time, "
                + "open (true; false: go back to the world open before). Refuses an existing folder. Writes devbridge-world.json (bridge-owned). Returns an operation id", req -> {
            String folder = req.require("folder");
            Path dir = WorldFiles.child(saves(), "folder", folder);
            if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
                throw new BridgeException(409, "saves/" + folder + " already exists; create never overwrites (pick another folder, or reset it if DevBridge created it)");
            }
            WorldFiles.Spec spec = spec(req, folder);
            boolean open = req.bool("open", true);
            WorldOps.Op op = WorldOps.begin("create", folder);
            ClientWorldEndpoints.dropStartupRequest();
            run(op, client(() -> openFolder(Minecraft.getInstance())).thenCompose(prev -> create(op, folder, spec, open, prev)));
            JsonObject o = started(op);
            o.add("settings", spec.toJson());
            return o;
        });

        http.post("/client/world/save", "Save the open singleplayer world now (players, chunks, level.dat; like /save-all flush) without leaving it. Returns an operation id", req -> {
            Object[] open = onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                return new Object[]{mc.getSingleplayerServer(), openFolder(mc)};
            });
            if (!(open[0] instanceof IntegratedServer server)) throw BridgeException.badRequest("no singleplayer world is open");
            WorldOps.Op op = WorldOps.begin("save", (String) open[1]);
            op.phase("saving");
            run(op, server.submit(() -> {
                op.result.addProperty("saved", server.saveEverything(false, true, true));
                return null;
            }));
            return started(op);
        });

        http.post("/client/world/snapshot", "Copy a save (any world, the owner's too: it is only read) to devbridge-snapshots/<folder>/<name>. An open world is left first "
                + "and reopened afterwards. params: folder, name, overwrite (false; true moves the old snapshot to the trash), reopen (default: if it was open). Returns an operation id", req -> {
            String folder = req.require("folder"), name = req.require("name");
            Path dir = WorldFiles.child(saves(), "folder", folder);
            Path dest = WorldFiles.child(snapshots().resolve(folder), "snapshot name", name);
            if (!Files.isRegularFile(dir.resolve("level.dat"))) throw BridgeException.notFound("no world folder " + folder + " (see /client/worlds)");
            boolean overwrite = req.bool("overwrite", false);
            if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS) && !overwrite) {
                throw new BridgeException(409, "snapshot " + name + " of " + folder + " exists; pass overwrite=true to replace it (the old one goes to the trash)");
            }
            Boolean reopen = reopen(req);
            WorldOps.Op op = WorldOps.begin("snapshot", folder);
            ClientWorldEndpoints.dropStartupRequest();
            op.result.addProperty("name", name);
            run(op, closed(op, folder, reopen, () -> {
                op.phase("copying");
                if (WorldFiles.isLocked(dir)) throw new IOException("saves/" + folder + " is in use (its session.lock is held; is it open in another game?)");
                Path staging = WorldFiles.staging(snapshots());
                try {
                    WorldFiles.copySave(dir, staging);
                    if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS)) {
                        op.result.addProperty("replacedToTrash", WorldFiles.moveToTrash(dest, trash(), "snapshot-" + folder + "-" + name).toString());
                    }
                    WorldFiles.move(staging, dest, "store the snapshot");
                } catch (IOException e) {
                    dropStaging(staging);
                    throw new IOException("snapshot of " + folder + " failed: " + e.getMessage(), e);
                }
                op.result.addProperty("snapshot", dest.toString());
            }));
            return started(op);
        });

        http.post("/client/world/restore", "Replace a bridge-owned save with one of its snapshots; the replaced save goes to the trash. An open world is left first. "
                + "params: folder, name, reopen (default: if it was open). Refused for worlds DevBridge did not create. Returns an operation id", req -> {
            String folder = req.require("folder"), name = req.require("name");
            Path dir = WorldFiles.child(saves(), "folder", folder);
            Path snap = WorldFiles.child(snapshots().resolve(folder), "snapshot name", name);
            if (!Files.isRegularFile(snap.resolve("level.dat"))) throw BridgeException.notFound("no snapshot " + name + " of " + folder + " (see /client/world/snapshots)");
            if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) requireOwned(dir, folder, "restore");
            try {
                WorldFiles.readMarker(snap, folder);
            } catch (IllegalArgumentException e) {
                throw new BridgeException(403, "restore refused: snapshot " + name + " is not of a world DevBridge created (" + e.getMessage() + "). Nothing was changed.");
            }
            Boolean reopen = reopen(req);
            WorldOps.Op op = WorldOps.begin("restore", folder);
            ClientWorldEndpoints.dropStartupRequest();
            op.result.addProperty("name", name);
            run(op, closed(op, folder, reopen, () -> {
                op.phase("copying");
                Path staging = WorldFiles.staging(snapshots());
                try {
                    WorldFiles.copySave(snap, staging);
                } catch (IOException e) {
                    dropStaging(staging);
                    throw new IOException("copying snapshot " + name + " failed: " + e.getMessage() + "; saves/" + folder + " is unchanged", e);
                }
                op.phase("replacing");
                if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
                    moveOrDrop(staging, dir);
                } else {
                    Path old;
                    try {
                        requireOwned(dir, folder, "restore"); // again, now that the world is closed
                        if (WorldFiles.isLocked(dir)) throw new IOException("saves/" + folder + " is in use (its session.lock is held)");
                        old = WorldFiles.moveToTrash(dir, trash(), "restore-" + folder);
                    } catch (IOException | RuntimeException e) {
                        dropStaging(staging);
                        throw e;
                    }
                    op.result.addProperty("replacedToTrash", old.toString());
                    try {
                        WorldFiles.move(staging, dir, "put the snapshot in place");
                    } catch (IOException e) {
                        dropStaging(staging);
                        try {
                            WorldFiles.move(old, dir, "put the old save back");
                        } catch (IOException back) {
                            throw new IOException(e.getMessage() + "; putting the old save back failed too, it is at " + old, e);
                        }
                        throw new IOException(e.getMessage() + "; the old save is back in place", e);
                    }
                }
                op.result.addProperty("restored", name);
            }));
            return started(op);
        });

        http.post("/client/world/reset", "Recreate a bridge-owned world fresh from the settings in its devbridge-world.json (same seed); the old save goes to the trash. "
                + "params: folder, reopen (default: if it was open; false goes back to the world open before). Refused for worlds DevBridge did not create. Returns an operation id", req -> {
            String folder = req.require("folder");
            Path dir = WorldFiles.child(saves(), "folder", folder);
            if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) throw BridgeException.notFound("no world folder " + folder + " (see /client/worlds)");
            WorldFiles.Spec spec = requireOwned(dir, folder, "reset");
            JsonObject rules = new JsonObject();
            spec.gameRules().forEach(rules::addProperty);
            checkRules(rules); // a rule from a mod that is gone would be dropped silently
            Boolean reopen = reopen(req);
            WorldOps.Op op = WorldOps.begin("reset", folder);
            ClientWorldEndpoints.dropStartupRequest();
            run(op, client(() -> {
                Minecraft mc = Minecraft.getInstance();
                String prev = openFolder(mc);
                if (folder.equals(prev)) {
                    op.phase("leaving");
                    leave(mc);
                }
                op.result.addProperty("wasOpen", folder.equals(prev));
                return prev;
            }).thenCompose(prev -> files(() -> {
                op.phase("trashing");
                requireOwned(dir, folder, "reset");
                if (WorldFiles.isLocked(dir)) throw new IOException("saves/" + folder + " is in use (its session.lock is held)");
                op.result.addProperty("oldToTrash", WorldFiles.moveToTrash(dir, trash(), "reset-" + folder).toString());
            }).thenCompose(v -> {
                boolean was = folder.equals(prev);
                return create(op, folder, spec, reopen != null ? reopen : was, was ? null : prev);
            })));
            JsonObject o = started(op);
            o.add("settings", spec.toJson());
            return o;
        });

        http.post("/client/world/delete", "Move a bridge-owned world that is not open to devbridge-trash/ (nothing is erased). params: folder. "
                + "Refused for worlds DevBridge did not create. Finishes before it returns", req -> {
            String folder = req.require("folder");
            Path dir = WorldFiles.child(saves(), "folder", folder);
            if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) throw BridgeException.notFound("no world folder " + folder + " (see /client/worlds)");
            requireOwned(dir, folder, "delete");
            if (folder.equals(onClient(() -> openFolder(Minecraft.getInstance())))) {
                throw new BridgeException(409, folder + " is open; leave it first (/client/world/leave)");
            }
            WorldOps.Op op = WorldOps.begin("delete", folder);
            return now(op, () -> {
                if (WorldFiles.isLocked(dir)) throw new IOException("saves/" + folder + " is in use (its session.lock is held)");
                op.result.addProperty("trashed", WorldFiles.moveToTrash(dir, trash(), "world-" + folder).toString());
            });
        });

        http.get("/client/world/snapshots", "Snapshots in devbridge-snapshots/: of one save (folder) or of all, including saves that no longer exist", req -> {
            JsonObject o = Json.obj();
            o.addProperty("snapshotsDir", snapshots().toString());
            if (req.has("folder")) {
                String folder = WorldFiles.checkName("folder", req.require("folder"));
                o.addProperty("folder", folder);
                o.add("snapshots", WorldFiles.snapshots(snapshots(), folder));
                return o;
            }
            JsonObject all = Json.obj();
            if (Files.isDirectory(snapshots())) {
                try (Stream<Path> s = Files.list(snapshots())) {
                    s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).filter(n -> !n.startsWith(".")).sorted()
                            .forEach(n -> all.add(n, WorldFiles.snapshots(snapshots(), n)));
                } catch (IOException e) {
                    throw new BridgeException(500, "cannot list " + snapshots() + ": " + e.getMessage(), e);
                }
            }
            o.add("folders", all);
            return o;
        });

        http.post("/client/world/snapshot/delete", "Move a snapshot to devbridge-trash/. params: folder, name. Finishes before it returns", req -> {
            String folder = req.require("folder"), name = req.require("name");
            WorldFiles.checkName("folder", folder);
            Path snap = WorldFiles.child(snapshots().resolve(folder), "snapshot name", name);
            if (!Files.isDirectory(snap, LinkOption.NOFOLLOW_LINKS)) throw BridgeException.notFound("no snapshot " + name + " of " + folder);
            WorldOps.Op op = WorldOps.begin("snapshot-delete", folder);
            op.result.addProperty("name", name);
            return now(op, () -> op.result.addProperty("trashed", WorldFiles.moveToTrash(snap, trash(), "snapshot-" + folder + "-" + name).toString()));
        });
    }

    // ---------------------------------------------------------------- steps

    /**
     * Leaves the open world, makes the folder with its marker, creates and loads the world, sets the time,
     * then stays (stay) or leaves again and reopens prev. A save that never loaded goes to the trash.
     */
    private static CompletableFuture<Void> create(WorldOps.Op op, String folder, WorldFiles.Spec spec, boolean stay, String prev) {
        Path dir = saves().resolve(folder);
        AtomicBoolean made = new AtomicBoolean(), loaded = new AtomicBoolean();
        CompletableFuture<Void> chain = client(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                op.phase("leaving");
                leave(mc);
            }
            return null;
        }).thenCompose(v -> files(() -> {
            op.phase("preparing");
            Files.createDirectories(dir.getParent());
            Files.createDirectory(dir); // fails if it appeared meanwhile: never overwrite
            made.set(true);
            WorldFiles.writeMarker(dir, WorldFiles.marker(folder, spec, createdBy()));
        })).thenCompose(v -> client(() -> {
            op.phase("generating");
            return createWorld(folder, spec);
        })).thenCompose(f -> f).thenCompose(v -> client(() -> {
            loaded.set(true);
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            if (spec.time() != null && server != null) {
                long t = spec.time();
                server.execute(() -> server.getAllLevels().forEach(l -> l.setDayTime(t)));
            }
            op.result.addProperty("path", dir.toString());
            op.result.addProperty("seed", Long.toString(spec.seed()));
            return null;
        })).thenCompose(v -> stay ? CompletableFuture.completedFuture(null) : goBack(op, prev, folder));
        return chain.exceptionallyCompose(err -> {
            if (!made.get() || loaded.get()) return CompletableFuture.failedFuture(err);
            // Never loaded: move the half-made save away so the folder can be used again.
            return client(() -> openFolder(Minecraft.getInstance())).thenCompose(open -> {
                if (folder.equals(open) || !Files.isDirectory(dir) || WorldFiles.isLocked(dir)) {
                    return CompletableFuture.<Void>failedFuture(new IllegalStateException(message(err) + "; saves/" + folder + " is still there (bridge-owned)"));
                }
                return files(() -> op.result.addProperty("partialToTrash", WorldFiles.moveToTrash(dir, trash(), "failed-" + folder).toString()))
                        .<Void>handle((x, e2) -> {
                            throw new CompletionException(new IllegalStateException(message(err) + (e2 == null
                                    ? "; the half-made save was moved to " + op.result.get("partialToTrash").getAsString()
                                    : "; saves/" + folder + " is still there (" + message(e2) + ")")));
                        });
            });
        });
    }

    /** createFreshLevel with the spec, in watch mode; completes once the player is in it. Client thread. */
    private static CompletableFuture<Void> createWorld(String folder, WorldFiles.Spec spec) {
        Minecraft mc = Minecraft.getInstance();
        LevelSettings settings = new LevelSettings(spec.name(), GameType.byName(spec.gameMode()), spec.hardcore(),
                Difficulty.byName(spec.difficulty()), spec.cheats(), gameRules(spec.gameRules()), WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(spec.seed(), spec.structures(), false);
        ClientWorldEndpoints.prepareJoin();
        // Blocks while the server starts; on failure the game shows the title screen, which expect() reports.
        mc.createWorldOpenFlows().createFreshLevel(folder, settings, options, access -> dimensions(spec.preset(), access), new TitleScreen());
        return ClientWorldEndpoints.expect(folder);
    }

    private static WorldDimensions dimensions(String preset, RegistryAccess access) {
        return switch (preset) {
            case "default" -> WorldPresets.createNormalWorldDimensions(access);
            case "flat" -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
            case "void" -> dimensions("flat", access).replaceOverworldGenerator(access, new FlatLevelSource(
                    access.registryOrThrow(Registries.FLAT_LEVEL_GENERATOR_PRESET).getHolderOrThrow(FlatLevelGeneratorPresets.THE_VOID).value().settings()));
            default -> throw new IllegalArgumentException("unknown preset " + preset);
        };
    }

    /** After a create that should not stay open: leave it, then reopen prev (the world open before) or stay at the title screen. */
    private static CompletableFuture<Void> goBack(WorldOps.Op op, String prev, String created) {
        return client(() -> {
            Minecraft mc = Minecraft.getInstance();
            op.phase("leaving");
            if (mc.level != null) leave(mc);
            if (prev == null || prev.equals(created)) return CompletableFuture.<Void>completedFuture(null);
            op.phase("reopening " + prev);
            return switchTo(prev);
        }).thenCompose(f -> f);
    }

    /**
     * Leaves folder if it is open, runs the file step on the file thread, then reopens it when asked
     * (by default when it was open). A failed step still reopens the world, and its error is kept.
     */
    private static CompletableFuture<Void> closed(WorldOps.Op op, String folder, Boolean reopen, FileStep step) {
        return client(() -> {
            Minecraft mc = Minecraft.getInstance();
            boolean was = folder.equals(openFolder(mc));
            if (was) {
                op.phase("leaving");
                leave(mc);
            }
            op.result.addProperty("wasOpen", was);
            return was;
        }).thenCompose(was -> files(step).handle((v, err) -> err).thenCompose(err -> {
            boolean again = (reopen != null ? reopen : was) && Files.isRegularFile(saves().resolve(folder).resolve("level.dat"));
            CompletableFuture<Void> next = !again ? CompletableFuture.completedFuture(null) : client(() -> {
                op.phase("reopening");
                return switchTo(folder);
            }).thenCompose(f -> f);
            if (err == null) return next;
            return next.<Void>handle((v, e2) -> {
                throw err instanceof CompletionException ce ? ce : new CompletionException(err);
            });
        }));
    }

    private static CompletableFuture<Void> files(FileStep step) {
        return CompletableFuture.runAsync(() -> {
            try {
                step.run();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, FILES);
    }

    /** A quick file step run on the calling thread: finishes op before the route returns. */
    private static JsonObject now(WorldOps.Op op, FileStep step) {
        try {
            step.run();
            WorldOps.finish(op, null);
        } catch (IOException | RuntimeException e) {
            WorldOps.finish(op, message(e));
            throw e instanceof BridgeException be ? be : new BridgeException(500, op.name + " failed: " + message(e), e);
        }
        JsonObject o = started(op);
        o.add("result", op.result);
        return o;
    }

    private static void moveOrDrop(Path staging, Path dir) throws IOException {
        try {
            WorldFiles.move(staging, dir, "put the snapshot in place");
        } catch (IOException e) {
            dropStaging(staging);
            throw e;
        }
    }

    private static void dropStaging(Path staging) {
        if (!WorldFiles.deleteTree(staging)) DevBridge.LOGGER.warn("[DevBridge] could not remove the staging copy {}", staging);
    }

    /** The marker's settings, or 403 explaining that only bridge-owned saves are touched. */
    static WorldFiles.Spec requireOwned(Path dir, String folder, String what) {
        try {
            return WorldFiles.readMarker(dir, folder);
        } catch (IllegalArgumentException e) {
            throw new BridgeException(403, what + " refused: saves/" + folder + " is not a world DevBridge created (" + e.getMessage()
                    + "). Restore, reset and delete only touch bridge-owned worlds; nothing was changed.");
        }
    }

    private static Boolean reopen(Request req) {
        return req.has("reopen") ? req.bool("reopen", true) : null;
    }

    private static String createdBy() {
        return "DevBridge " + ModList.get().getModContainerById(DevBridge.MOD_ID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
    }

    // ---------------------------------------------------------------- settings

    private static WorldFiles.Spec spec(Request req, String folder) {
        String preset = WorldFiles.oneOf("preset", req.str("preset", "flat"), WorldFiles.PRESETS);
        boolean hardcore = req.bool("hardcore", false);
        // Hardcore is survival on hard, as in the create-world screen.
        String mode = hardcore ? "survival" : WorldFiles.oneOf("gameMode", req.str("gameMode", "creative"), WorldFiles.GAME_MODES);
        String difficulty = hardcore ? "hard" : WorldFiles.oneOf("difficulty", req.str("difficulty", "normal"), WorldFiles.DIFFICULTIES);
        long seed = WorldOptions.parseSeed(req.str("seed", "")).orElseGet(WorldOptions::randomSeed);
        Long time = null;
        if (req.has("time")) {
            try {
                time = Long.parseLong(req.str("time", "").strip());
            } catch (NumberFormatException e) {
                throw BridgeException.badRequest("time must be an integer number of ticks (6000 = noon)");
            }
        }
        String name = req.str("name", folder).strip();
        return new WorldFiles.Spec(name.isEmpty() ? folder : name, preset, mode, difficulty, req.bool("cheats", true), hardcore, seed,
                req.bool("structures", preset.equals("default")), checkRules(req.json("gameRules")), time);
    }

    /** Game rule names and values checked against the rules this game registers: name -> normalised value. */
    static Map<String, String> checkRules(JsonElement el) {
        Map<String, String> out = new LinkedHashMap<>();
        if (el == null || el.isJsonNull()) return out;
        if (!el.isJsonObject()) throw BridgeException.badRequest("gameRules must be an object, e.g. {\"doDaylightCycle\": false}");
        Map<String, String> kinds = new HashMap<>();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                kinds.put(key.getId(), "boolean");
            }

            @Override
            public void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {
                kinds.put(key.getId(), "integer");
            }
        });
        for (var e : el.getAsJsonObject().entrySet()) {
            String kind = kinds.get(e.getKey());
            if (kind == null) throw BridgeException.badRequest("unknown game rule " + e.getKey() + " (names as in /gamerule, e.g. doDaylightCycle)");
            String v = e.getValue().isJsonPrimitive() ? e.getValue().getAsString().strip() : "";
            if (kind.equals("boolean")) {
                if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) throw BridgeException.badRequest("game rule " + e.getKey() + " takes true or false");
                v = v.toLowerCase();
            } else {
                try {
                    v = Integer.toString(Integer.parseInt(v));
                } catch (NumberFormatException x) {
                    throw BridgeException.badRequest("game rule " + e.getKey() + " takes an integer");
                }
            }
            out.put(e.getKey(), v);
        }
        return out;
    }

    private static GameRules gameRules(Map<String, String> values) {
        GameRules rules = new GameRules();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                String v = values.get(key.getId());
                if (v != null) rules.getRule(key).set(Boolean.parseBoolean(v), null);
            }

            @Override
            public void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {
                String v = values.get(key.getId());
                if (v != null) rules.getRule(key).set(Integer.parseInt(v), null);
            }
        });
        return rules;
    }
}
