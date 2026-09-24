package dev.devbridge;

import dev.devbridge.http.BridgeException;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Helpers for touching game state from HTTP threads.
 *
 * All world access must happen on the server thread; these helpers submit the
 * work there and block the HTTP thread (with a timeout) until it completes.
 */
public final class GameAccess {
    private static long timeoutMillis = 15000;

    private GameAccess() {}

    public static void setTimeout(long millis) {
        timeoutMillis = millis;
    }

    /** The running server (dedicated, or the integrated server in singleplayer / LAN). */
    public static MinecraftServer server() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) throw BridgeException.unavailable("no server is running (not in a world yet?)");
        return s;
    }

    public static MinecraftServer serverOrNull() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    /** Runs a function on the server thread and waits for its result. */
    public static <T> T onServer(Function<MinecraftServer, T> fn) {
        MinecraftServer server = server();
        return await(server.submit(() -> fn.apply(server)));
    }

    public static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new BridgeException(504, "game thread did not respond within " + timeoutMillis
                    + " ms (is the game paused? press F3+P to disable pause-on-lost-focus)");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof BridgeException be) throw be;
            throw new BridgeException(500, cause.toString(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BridgeException(500, "interrupted");
        }
    }

    /** Resolves a dimension id such as "minecraft:overworld"; null/empty means overworld. */
    public static ServerLevel level(MinecraftServer server, String dimension) {
        if (dimension == null || dimension.isBlank()) return server.overworld();
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) throw BridgeException.badRequest("invalid dimension id: " + dimension);
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) throw BridgeException.notFound("unknown dimension: " + dimension);
        return level;
    }

    public static ServerPlayer player(MinecraftServer server, String name) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        if (p == null) throw BridgeException.notFound("no online player named " + name);
        return p;
    }
}
