package dev.devbridge.endpoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.devbridge.DevBridge;
import dev.devbridge.http.BridgeException;
import dev.devbridge.http.BridgeHttpServer;
import dev.devbridge.util.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
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
import java.util.stream.Stream;

import static dev.devbridge.endpoint.ClientEndpoints.onClient;

/**
 * Client-only world lifecycle: which singleplayer world is open, the saves list,
 * leaving / opening a world and quitting the game.
 *
 * Leave, open and quit are queued on the client thread and return at once (saving a
 * world can take longer than the HTTP timeout); poll /client/state or /client/world.
 *
 * A launcher-side helper can ask for a world on the next start by writing its folder
 * name (UTF-8) to {@code config/devbridge-open-world.txt}: the file is read and deleted
 * at startup, and the world opens once the title screen is up.
 */
public final class ClientWorldEndpoints {
    private ClientWorldEndpoints() {}

    static final String OPEN_WORLD_FILE = "devbridge-open-world.txt";
    /** World folder to open once the title screen shows. Client thread only after register(). */
    private static String pendingWorld;
    /** Window size for an automated launch, applied on the first client tick. */
    private static int[] pendingWindow;
    /** Release the mouse once the world DevBridge opened is up. */
    private static boolean releaseMouseInWorld;
    private static boolean keepMouseFree;

    public static void register(BridgeHttpServer http) {
        keepMouseFree = http.config().keepMouseFree();
        pendingWorld = takeOpenWorldRequest();
        if (pendingWorld != null) pendingWindow = parseSize(http.config().automationWindow());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> onClientTick());

        http.get("/client/world", "The open world: inWorld, singleplayer, folder (saves/ directory name, what /client/world/open takes), name", req -> onClient(() -> {
            Minecraft mc = Minecraft.getInstance();
            JsonObject o = Json.obj();
            o.addProperty("inWorld", mc.level != null);
            IntegratedServer server = mc.getSingleplayerServer();
            o.addProperty("singleplayer", server != null);
            if (server != null) {
                Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                o.addProperty("folder", root.getFileName().toString());
                o.addProperty("name", server.getWorldData().getLevelName());
                o.addProperty("path", root.toString());
            } else if (mc.getCurrentServer() != null) {
                o.addProperty("server", mc.getCurrentServer().ip);
            }
            return o;
        }));

        http.get("/client/worlds", "Singleplayer saves, newest first: folder, lastPlayed (level.dat mtime, epoch ms)", req -> {
            Path saves = Minecraft.getInstance().getLevelSource().getBaseDir().toAbsolutePath().normalize();
            List<Path> dirs = new ArrayList<>();
            try (Stream<Path> s = Files.list(saves)) {
                s.filter(d -> Files.isRegularFile(d.resolve("level.dat"))).forEach(dirs::add);
            } catch (IOException e) {
                throw new BridgeException(500, "cannot list " + saves + ": " + e.getMessage(), e);
            }
            dirs.sort(Comparator.comparingLong(ClientWorldEndpoints::lastPlayed).reversed());
            JsonArray arr = new JsonArray();
            for (Path d : dirs) {
                JsonObject o = Json.obj();
                o.addProperty("folder", d.getFileName().toString());
                o.addProperty("lastPlayed", lastPlayed(d));
                arr.add(o);
            }
            JsonObject o = Json.obj();
            o.addProperty("savesDir", saves.toString());
            o.add("worlds", arr);
            return o;
        });

        http.post("/client/world/leave", "Save and quit to the title screen, like the pause menu button. Returns at once; poll /client/state", req -> onClient(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) throw BridgeException.badRequest("not in a world");
            mc.execute(() -> leave(mc));
            return Json.of("leaving");
        }));

        http.post("/client/world/open", "Open a singleplayer world by folder (see /client/worlds), leaving the current one first. Returns at once; poll /client/world", req -> {
            String folder = req.require("folder");
            return onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (!mc.getLevelSource().levelExists(folder)) throw BridgeException.notFound("no world folder " + folder + " (see /client/worlds)");
                mc.execute(() -> {
                    if (mc.level != null) leave(mc);
                    openWorld(mc, folder);
                });
                JsonObject o = Json.obj();
                o.addProperty("opening", folder);
                return o;
            });
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
        if (pendingWorld != null && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            String folder = pendingWorld;
            pendingWorld = null;
            if (mc.getLevelSource().levelExists(folder)) {
                DevBridge.LOGGER.info("[DevBridge] opening requested world '{}'", folder);
                openWorld(mc, folder);
            } else {
                DevBridge.LOGGER.warn("[DevBridge] requested world '{}' does not exist; staying at the title screen", folder);
            }
        }
        // Joining ends with setScreen(null), which grabs the mouse when the window has focus.
        if (releaseMouseInWorld && mc.level != null && mc.player != null && mc.screen == null) {
            releaseMouseInWorld = false;
            mc.mouseHandler.releaseMouse();
        }
    }

    private static void openWorld(Minecraft mc, String folder) {
        releaseMouseInWorld = keepMouseFree;
        mc.createWorldOpenFlows().openWorld(folder, () -> {
            releaseMouseInWorld = false;
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

    /** What PauseScreen's "Save and Quit to Title" does. Blocks while the integrated server saves. */
    private static void leave(Minecraft mc) {
        boolean local = mc.isLocalServer();
        mc.level.disconnect();
        if (local) mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        else mc.disconnect();
        mc.setScreen(new TitleScreen());
    }

    private static long lastPlayed(Path dir) {
        try {
            return Files.getLastModifiedTime(dir.resolve("level.dat")).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
