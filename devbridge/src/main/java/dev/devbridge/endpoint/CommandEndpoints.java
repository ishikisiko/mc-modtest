package dev.devbridge.endpoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.devbridge.GameAccess;
import dev.devbridge.http.BridgeHttpServer;
import dev.devbridge.util.Json;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes server commands with captured output.
 *
 * This is the main extension point: anything mod-specific should be exposed by
 * the mod under test as a normal command, and driven through here.
 */
public final class CommandEndpoints {
    private CommandEndpoints() {}

    /** A CommandSource that collects messages instead of printing them. */
    private static final class Capture implements CommandSource {
        final List<String> lines = new ArrayList<>();

        @Override public void sendSystemMessage(Component component) { lines.add(component.getString()); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }

    public static void register(BridgeHttpServer http) {

        http.post("/command", "Run a server command with permission level 4 and capture output. params: command (without leading slash), as (player name, optional), dimension + x/y/z (optional position)", req -> {
            String command = req.require("command");
            String cmd = command.startsWith("/") ? command.substring(1) : command;
            String as = req.str("as", null);
            String dimension = req.str("dimension", null);
            boolean hasPos = req.has("x") && req.has("y") && req.has("z");
            double x = req.dbl("x", 0), y = req.dbl("y", 0), z = req.dbl("z", 0);

            return GameAccess.onServer(server -> {
                Capture capture = new Capture();
                boolean[] success = {false};
                int[] result = {0};
                boolean[] called = {false};
                CommandResultCallback callback = (ok, value) -> {
                    called[0] = true;
                    success[0] = ok;
                    result[0] = value;
                };

                CommandSourceStack stack = baseStack(server, as)
                        .withSource(capture)
                        .withPermission(4)
                        .withCallback(callback);
                if (dimension != null) {
                    ServerLevel level = GameAccess.level(server, dimension);
                    stack = stack.withLevel(level);
                }
                if (hasPos) {
                    stack = stack.withPosition(new Vec3(x, y, z));
                }

                server.getCommands().performPrefixedCommand(stack, cmd);

                JsonObject o = Json.obj();
                o.addProperty("command", cmd);
                o.addProperty("success", called[0] ? success[0] : capture.lines.isEmpty() || !looksLikeError(capture.lines));
                o.addProperty("result", result[0]);
                JsonArray out = new JsonArray();
                for (String l : capture.lines) out.add(l);
                o.add("output", out);
                return o;
            });
        });

        http.get("/command/tree", "List top-level command names (optionally filtered by 'contains')", req -> {
            String contains = req.str("contains", null);
            return GameAccess.onServer(server -> {
                JsonArray arr = new JsonArray();
                var root = server.getCommands().getDispatcher().getRoot();
                for (var child : root.getChildren()) {
                    String name = child.getName();
                    if (contains == null || name.contains(contains)) arr.add(name);
                }
                return arr;
            });
        });

        http.get("/command/usage", "Usage strings for a command. params: name", req -> {
            String name = req.require("name");
            return GameAccess.onServer(server -> {
                var dispatcher = server.getCommands().getDispatcher();
                var node = dispatcher.getRoot().getChild(name);
                if (node == null) throw dev.devbridge.http.BridgeException.notFound("no command named " + name);
                CommandSourceStack stack = server.createCommandSourceStack().withPermission(4);
                JsonArray arr = new JsonArray();
                for (String usage : dispatcher.getAllUsage(node, stack, false)) arr.add(name + " " + usage);
                return arr;
            });
        });
    }

    private static CommandSourceStack baseStack(MinecraftServer server, String as) {
        if (as != null && !as.isBlank()) {
            ServerPlayer player = GameAccess.player(server, as);
            return player.createCommandSourceStack();
        }
        return server.createCommandSourceStack();
    }

    private static boolean looksLikeError(List<String> lines) {
        for (String l : lines) {
            String s = l.toLowerCase();
            if (s.startsWith("unknown ") || s.startsWith("incorrect ") || s.startsWith("expected ") || s.startsWith("invalid ")) return true;
        }
        return false;
    }
}
