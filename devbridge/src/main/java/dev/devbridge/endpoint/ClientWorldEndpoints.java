package dev.devbridge.endpoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.devbridge.DevBridge;
import dev.devbridge.LogBuffer;
import dev.devbridge.http.BridgeException;
import dev.devbridge.http.BridgeHttpServer;
import dev.devbridge.util.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.GenericWaitingScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static dev.devbridge.endpoint.ClientEndpoints.onClient;

/**
 * Client-only world lifecycle: which singleplayer world is open, the saves list,
 * leaving / opening a world and quitting the game.
 *
 * Leave and open are world operations ({@link WorldOps}): queued on the client thread, they
 * return an operation id at once (saving a world can take longer than the HTTP timeout);
 * poll /client/world (busy, lastOperation, lastError) or /client/world/operation?id=.
 * Creating, saving, snapshots and the rest are in {@link ClientSaveEndpoints}.
 *
 * A launcher-side helper can ask for a world on the next start by writing its folder
 * name (UTF-8) to {@code config/devbridge-open-world.txt}: the file is read and deleted
 * at startup, and the world opens once the title screen is up.
 */
public final class ClientWorldEndpoints {
    private ClientWorldEndpoints() {}

    static final String OPEN_WORLD_FILE = "devbridge-open-world.txt";
    /** A world that takes longer to load than this has failed; a prompt screen gets PROMPT_SECONDS. */
    private static final int LOAD_SECONDS = 600, PROMPT_SECONDS = 120;
    /** World folder to open once the title screen shows; dropped when an API call opens a world first. */
    private static volatile String pendingWorld;
    /** Window size for an automated launch, applied on the first client tick. */
    private static int[] pendingWindow;
    /** Open the inventory once the world DevBridge opened has settled (automationScreen). */
    private static boolean inventoryOnJoin;
    private static boolean openInventoryAfterJoin;
    private static int settledTicks;
    /** The world an operation waits to be in. Client thread only. */
    private static Loading loading;

    private record Loading(String folder, CompletableFuture<Void> done, long started, long logSeq) {}

    public static void register(BridgeHttpServer http) {
        pendingWorld = takeOpenWorldRequest();
        if (pendingWorld != null) pendingWindow = parseSize(http.config().automationWindow());
        String screen = http.config().automationScreen();
        inventoryOnJoin = screen.equalsIgnoreCase("inventory");
        if (!screen.isEmpty() && !inventoryOnJoin) DevBridge.LOGGER.warn("[DevBridge] automationScreen '{}' is not 'inventory' or empty; ignored", screen);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> onClientTick());

        http.get("/client/world", "The open world: inWorld, singleplayer, folder (saves/ directory name), name; and world operations: "
                + "busy (running operation or null), current, lastOperation {id, name, folder, ok, error, result}, lastError", req -> {
            JsonObject o;
            try {
                // DevHost reads the first "folder" in this body: the world's folder must stay ahead of the operations.
                o = onClient(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    JsonObject w = Json.obj();
                    w.addProperty("inWorld", mc.level != null);
                    IntegratedServer server = mc.getSingleplayerServer();
                    w.addProperty("singleplayer", server != null);
                    if (server != null) {
                        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                        w.addProperty("folder", root.getFileName().toString());
                        w.addProperty("name", server.getWorldData().getLevelName());
                        w.addProperty("path", root.toString());
                    } else if (mc.getCurrentServer() != null) {
                        w.addProperty("server", mc.getCurrentServer().ip);
                    }
                    return w;
                });
            } catch (BridgeException e) {
                if (e.status() != 504) throw e;
                o = Json.obj();
                o.addProperty("clientThread", "not responding: " + e.getMessage());
            }
            WorldOps.status(o);
            return o;
        });

        http.get("/client/world/operation", "One world operation by id (what the world routes return): state running | done | failed, phase, error, result", req -> {
            long id = req.requireInt("id");
            return WorldOps.get(id);
        });

        http.get("/client/worlds", "Singleplayer saves, newest first: folder, lastPlayed, name, gameMode, cheats, difficulty, hardcore (from level.dat), "
                + "open, bridgeOwned (DevBridge created it; only those can be restored, reset or deleted), preset, snapshots", req -> {
            Path saves = saves();
            String open = openFolderRacy();
            List<Path> dirs = new ArrayList<>();
            try (Stream<Path> s = Files.list(saves)) {
                s.filter(d -> Files.isRegularFile(d.resolve("level.dat"))).forEach(dirs::add);
            } catch (IOException e) {
                throw new BridgeException(500, "cannot list " + saves + ": " + e.getMessage(), e);
            }
            dirs.sort(Comparator.comparingLong(ClientWorldEndpoints::lastPlayed).reversed());
            JsonArray arr = new JsonArray();
            for (Path d : dirs) {
                String folder = d.getFileName().toString();
                JsonObject o = Json.obj();
                o.addProperty("folder", folder);
                o.addProperty("lastPlayed", lastPlayed(d));
                levelInfo(d, o);
                o.addProperty("open", folder.equals(open));
                try {
                    WorldFiles.Spec spec = WorldFiles.readMarker(d);
                    o.addProperty("bridgeOwned", true);
                    o.addProperty("preset", spec.preset());
                } catch (IllegalArgumentException e) {
                    o.addProperty("bridgeOwned", false);
                    if (Files.exists(d.resolve(WorldFiles.MARKER))) o.addProperty("markerError", e.getMessage());
                }
                o.add("snapshots", WorldFiles.snapshots(ClientSaveEndpoints.snapshots(), folder));
                arr.add(o);
            }
            JsonObject o = Json.obj();
            o.addProperty("savesDir", saves.toString());
            o.addProperty("snapshotsDir", ClientSaveEndpoints.snapshots().toString());
            o.addProperty("trashDir", ClientSaveEndpoints.trash().toString());
            o.add("worlds", arr);
            return o;
        });

        http.post("/client/world/leave", "Save and quit to the title screen, like the pause menu button. A world operation: returns its id at once; poll /client/world", req -> {
            String folder = onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) throw BridgeException.badRequest("not in a world");
                return openFolder(mc);
            });
            WorldOps.Op op = WorldOps.begin("leave", folder);
            run(op, client(() -> {
                op.phase("leaving");
                leave(Minecraft.getInstance());
                return null;
            }));
            return started(op);
        });

        http.post("/client/world/open", "Open a singleplayer world by folder (see /client/worlds) in watch mode, leaving the current one first. "
                + "A world operation: returns its id at once; poll /client/world", req -> {
            String folder = req.require("folder");
            Path dir = WorldFiles.child(saves(), "folder", folder);
            if (!Files.isRegularFile(dir.resolve("level.dat"))) throw BridgeException.notFound("no world folder " + folder + " (see /client/worlds)");
            WorldOps.Op op = WorldOps.begin("open", folder);
            dropStartupRequest();
            run(op, client(() -> {
                op.phase("loading");
                return switchTo(folder);
            }).thenCompose(f -> f));
            JsonObject o = started(op);
            o.addProperty("opening", folder);
            return o;
        });

        http.post("/client/quit", "Save the open world and close the game. The bridge goes away with it", req -> onClient(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.level != null) leave(mc);
                mc.stop();
            });
            return Json.of("quitting");
        }));
    }

    // ---------------------------------------------------------------- operations

    /** An API call is opening a world: forget the startup request so it cannot fire later at the title screen. */
    static void dropStartupRequest() {
        pendingWorld = null;
    }

    static Path saves() {
        return Minecraft.getInstance().getLevelSource().getBaseDir().toAbsolutePath().normalize();
    }

    /** Runs on the client thread, after whatever is queued there. */
    static <T> CompletableFuture<T> client(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, Minecraft.getInstance());
    }

    /** Finishes op when the chain completes; the chain's error becomes lastError. */
    static void run(WorldOps.Op op, CompletableFuture<?> chain) {
        chain.whenComplete((v, err) -> {
            String error = err == null ? null : message(err);
            if (error != null) DevBridge.LOGGER.warn("[DevBridge] world operation {} {} failed: {}", op.name, op.folder, error);
            WorldOps.finish(op, error);
        });
    }

    static JsonObject started(WorldOps.Op op) {
        JsonObject o = Json.obj();
        o.add("operation", op.ref());
        return o;
    }

    static String message(Throwable t) {
        while ((t instanceof CompletionException || t instanceof java.util.concurrent.ExecutionException) && t.getCause() != null) t = t.getCause();
        return t instanceof BridgeException || t instanceof IOException || t instanceof IllegalArgumentException || t instanceof IllegalStateException
                ? t.getMessage() : t.toString();
    }

    /** The saves/ folder of the open singleplayer world, or null. Client thread. */
    static String openFolder(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        return server == null ? null : server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
    }

    /** openFolder from another thread, for listings only (file work checks session.lock as well). */
    private static String openFolderRacy() {
        try {
            return openFolder(Minecraft.getInstance());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Leaves the open world (if any) and opens folder in watch mode; the future completes once the player is in it. Client thread. */
    static CompletableFuture<Void> switchTo(String folder) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) leave(mc);
        openWorld(mc, folder);
        return expect(folder);
    }

    /** Before a world DevBridge opens or creates: watch mode, and the optional inventory once it settles. Client thread. */
    static void prepareJoin() {
        ClientMouseGuard.watch();
        openInventoryAfterJoin = inventoryOnJoin;
        settledTicks = 0;
    }

    /** Completes once the player is in folder; fails if the game goes back to the title screen or hangs. Client thread. */
    static CompletableFuture<Void> expect(String folder) {
        if (loading != null) loading.done.completeExceptionally(new IllegalStateException("superseded by opening " + folder));
        loading = new Loading(folder, new CompletableFuture<>(), System.currentTimeMillis(), LogBuffer.get().latestSeq());
        return loading.done;
    }

    private static void checkLoading(Minecraft mc) {
        Loading w = loading;
        if (w == null) return;
        long seconds = (System.currentTimeMillis() - w.started) / 1000;
        Screen screen = mc.screen;
        String fail = null;
        if (mc.level != null && mc.player != null && mc.getOverlay() == null && !isLoadingScreen(screen) && w.folder.equals(openFolder(mc))) {
            loading = null;
            w.done.complete(null);
            return;
        }
        if (mc.level == null && screen instanceof TitleScreen && mc.getOverlay() == null) {
            fail = "the game went back to the title screen instead of opening " + w.folder;
        } else if (mc.level == null && screen != null && !isLoadingScreen(screen) && seconds > PROMPT_SECONDS) {
            fail = "opening " + w.folder + " stopped at screen '" + screen.getTitle().getString() + "' (" + screen.getClass().getSimpleName()
                    + "); answer it in the game window or through /client/screen";
        } else if (seconds > LOAD_SECONDS) {
            fail = "opening " + w.folder + " took longer than " + LOAD_SECONDS + " s";
        }
        if (fail != null) {
            loading = null;
            w.done.completeExceptionally(new IllegalStateException(fail + lastWarnings(w.logSeq)));
        }
    }

    private static boolean isLoadingScreen(Screen s) {
        return s instanceof GenericMessageScreen || s instanceof LevelLoadingScreen || s instanceof ReceivingLevelScreen
                || s instanceof ProgressScreen || s instanceof ConnectScreen || s instanceof GenericWaitingScreen;
    }

    /** The last warnings logged since seq, so a failed load says why. */
    private static String lastWarnings(long seq) {
        var lines = LogBuffer.get().query(e -> e.seq() > seq && (e.level().equals("WARN") || e.level().equals("ERROR") || e.level().equals("FATAL")), 3);
        if (lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("; last warnings:");
        for (var e : lines) sb.append(" [").append(e.logger()).append("] ").append(e.message());
        return sb.toString();
    }

    // ---------------------------------------------------------------- startup and ticks

    private static String takeOpenWorldRequest() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(OPEN_WORLD_FILE);
        if (!Files.isRegularFile(file)) return null;
        try {
            String folder = Files.readString(file, StandardCharsets.UTF_8).strip();
            Files.delete(file);
            if (folder.isEmpty()) return null;
            DevBridge.LOGGER.info("[DevBridge] will open world '{}' at the title screen ({})", folder, OPEN_WORLD_FILE);
            return folder;
        } catch (IOException e) {
            DevBridge.LOGGER.warn("[DevBridge] could not read {}", file, e);
            return null;
        }
    }

    private static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (pendingWindow != null) {
            int[] size = pendingWindow;
            pendingWindow = null;
            ClientControlEndpoints.resizeWindow(mc, size[0], size[1]);
            DevBridge.LOGGER.info("[DevBridge] automated launch: window set to {}x{}", size[0], size[1]);
        }
        String folder = pendingWorld;
        if (folder != null && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            pendingWorld = null;
            openRequested(mc, folder);
        }
        checkLoading(mc);
        // Joining passes through several screens; wait until the world has been on screen for 1 s.
        if (openInventoryAfterJoin) {
            boolean settled = mc.level != null && mc.player != null && mc.screen == null && mc.getOverlay() == null;
            settledTicks = settled ? settledTicks + 1 : 0;
            if (settledTicks >= 20) {
                openInventoryAfterJoin = false;
                ClientControlEndpoints.click(mc.options.keyInventory); // the game picks survival or creative
            }
        }
    }

    /** The startup request, as an "open" operation so a failure shows in lastError. */
    private static void openRequested(Minecraft mc, String folder) {
        WorldOps.Op op;
        try {
            op = WorldOps.begin("open", folder);
        } catch (BridgeException e) {
            DevBridge.LOGGER.warn("[DevBridge] requested world '{}' not opened: {}", folder, e.getMessage());
            return;
        }
        if (!mc.getLevelSource().levelExists(folder)) {
            DevBridge.LOGGER.warn("[DevBridge] requested world '{}' does not exist; staying at the title screen", folder);
            WorldOps.finish(op, "requested world " + folder + " (" + OPEN_WORLD_FILE + ") does not exist");
            return;
        }
        DevBridge.LOGGER.info("[DevBridge] opening requested world '{}'", folder);
        op.phase("loading");
        openWorld(mc, folder);
        run(op, expect(folder));
    }

    /** Opens a world in watch mode (ClientMouseGuard): joining would otherwise capture the mouse. */
    private static void openWorld(Minecraft mc, String folder) {
        prepareJoin();
        mc.createWorldOpenFlows().openWorld(folder, () -> {
            openInventoryAfterJoin = false;
            mc.setScreen(new TitleScreen());
        });
    }

    /** "1600x900" -> {1600, 900}; null when empty or malformed. */
    private static int[] parseSize(String s) {
        String[] p = s.toLowerCase().split("x");
        try {
            if (p.length == 2) {
                int w = Integer.parseInt(p[0].strip()), h = Integer.parseInt(p[1].strip());
                if (w > 0 && h > 0) return new int[]{w, h};
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        if (!s.isEmpty()) DevBridge.LOGGER.warn("[DevBridge] automationWindow '{}' is not WIDTHxHEIGHT; ignored", s);
        return null;
    }

    /**
     * What PauseScreen's "Save and Quit to Title" does. Blocks while the integrated server saves;
     * when it returns the server thread has ended and the save's session.lock is released.
     */
    static void leave(Minecraft mc) {
        boolean local = mc.isLocalServer();
        mc.level.disconnect();
        if (local) mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        else mc.disconnect();
        mc.setScreen(new TitleScreen());
    }

    /** Level name, game mode, cheats, difficulty, hardcore and version from level.dat (cheap: one small file). */
    private static void levelInfo(Path dir, JsonObject o) {
        try {
            CompoundTag data = NbtIo.readCompressed(dir.resolve("level.dat"), NbtAccounter.unlimitedHeap()).getCompound("Data");
            o.addProperty("name", data.getString("LevelName"));
            o.addProperty("gameMode", GameType.byId(data.getInt("GameType")).getName());
            o.addProperty("cheats", data.getBoolean("allowCommands"));
            o.addProperty("difficulty", Difficulty.byId(data.getByte("Difficulty")).getKey());
            o.addProperty("hardcore", data.getBoolean("hardcore"));
            if (data.contains("Version")) o.addProperty("version", data.getCompound("Version").getString("Name"));
        } catch (IOException | RuntimeException e) {
            o.addProperty("levelDatError", e.toString());
        }
    }

    private static long lastPlayed(Path dir) {
        return WorldFiles.mtime(dir.resolve("level.dat"));
    }
}
