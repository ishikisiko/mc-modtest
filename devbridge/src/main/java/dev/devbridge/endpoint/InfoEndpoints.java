package dev.devbridge.endpoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.devbridge.GameAccess;
import dev.devbridge.http.BridgeException;
import dev.devbridge.http.BridgeHttpServer;
import dev.devbridge.util.Json;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Instance information, mod list, and registry inspection. */
public final class InfoEndpoints {
    private InfoEndpoints() {}

    public static void register(BridgeHttpServer http) {

        http.get("/ping", "Liveness check (no auth required)", req -> {
            JsonObject o = Json.obj();
            o.addProperty("mod", "devbridge");
            o.addProperty("dist", FMLEnvironment.dist.name());
            o.addProperty("time", System.currentTimeMillis());
            o.addProperty("pid", ProcessHandle.current().pid());
            o.addProperty("version", ModList.get().getModContainerById("devbridge")
                    .map(c -> c.getModInfo().getVersion().toString()).orElse("unknown"));
            return o;
        });

        http.get("/info", "Game/loader versions, dist, server status, players, dimensions", req -> {
            JsonObject o = Json.obj();
            o.addProperty("minecraft", SharedConstants.getCurrentVersion().getName());
            o.addProperty("neoforge", ModList.get().getModContainerById("neoforge")
                    .map(c -> c.getModInfo().getVersion().toString()).orElse("unknown"));
            o.addProperty("dist", FMLEnvironment.dist.name());
            o.addProperty("production", FMLEnvironment.production);
            o.addProperty("java", System.getProperty("java.version"));
            o.addProperty("gameDir", net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toAbsolutePath().toString());

            boolean running = GameAccess.serverOrNull() != null;
            o.addProperty("serverRunning", running);
            if (running) {
                o.add("server", GameAccess.onServer(server -> {
                    JsonObject s = Json.obj();
                    s.addProperty("dedicated", server.isDedicatedServer());
                    s.addProperty("tickCount", server.getTickCount());
                    s.addProperty("averageTickMillis", server.getAverageTickTimeNanos() / 1_000_000.0);
                    s.addProperty("motd", server.getMotd());
                    JsonArray players = new JsonArray();
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        JsonObject pj = Json.obj();
                        pj.addProperty("name", p.getGameProfile().getName());
                        pj.addProperty("uuid", p.getUUID().toString());
                        pj.addProperty("dimension", p.level().dimension().location().toString());
                        pj.add("pos", Json.vec(p.position()));
                        pj.addProperty("gameMode", p.gameMode.getGameModeForPlayer().getName());
                        players.add(pj);
                    }
                    s.add("players", players);
                    JsonArray dims = new JsonArray();
                    for (ServerLevel level : server.getAllLevels()) {
                        JsonObject d = Json.obj();
                        d.addProperty("id", level.dimension().location().toString());
                        d.addProperty("gameTime", level.getGameTime());
                        d.addProperty("dayTime", level.getDayTime());
                        d.addProperty("loadedChunks", level.getChunkSource().getLoadedChunksCount());
                        int entities = 0;
                        for (var ignored : level.getAllEntities()) entities++;
                        d.addProperty("entities", entities);
                        dims.add(d);
                    }
                    s.add("dimensions", dims);
                    return s;
                }));
            }
            return o;
        });

        http.get("/mods", "List loaded mods (id, name, version)", req -> {
            JsonArray arr = new JsonArray();
            for (IModInfo info : ModList.get().getMods()) {
                JsonObject m = Json.obj();
                m.addProperty("id", info.getModId());
                m.addProperty("name", info.getDisplayName());
                m.addProperty("version", info.getVersion().toString());
                m.addProperty("file", info.getOwningFile().getFile().getFileName());
                arr.add(m);
            }
            return arr;
        });

        http.get("/registries", "List all registry ids (static + dynamic if a server is running)", req -> {
            List<String> ids = new ArrayList<>();
            for (ResourceLocation key : BuiltInRegistries.REGISTRY.keySet()) ids.add(key.toString());
            MinecraftServer server = GameAccess.serverOrNull();
            if (server != null) {
                server.registryAccess().registries().forEach(e -> {
                    String id = e.key().location().toString();
                    if (!ids.contains(id)) ids.add(id);
                });
            }
            ids.sort(String::compareTo);
            return Json.of(ids);
        });

        http.get("/registry", "Entries of a registry. params: id (e.g. minecraft:block), namespace (filter), contains (filter), limit", req -> {
            String id = req.require("id");
            String namespace = req.str("namespace", null);
            String contains = req.str("contains", null);
            int limit = req.integer("limit", 2000);

            Registry<?> registry = findRegistry(id);
            JsonArray arr = new JsonArray();
            int total = 0;
            for (ResourceLocation key : registry.keySet()) {
                if (namespace != null && !key.getNamespace().equals(namespace)) continue;
                if (contains != null && !key.toString().contains(contains)) continue;
                total++;
                if (arr.size() < limit) arr.add(key.toString());
            }
            JsonObject o = Json.obj();
            o.addProperty("registry", id);
            o.addProperty("total", total);
            o.add("entries", arr);
            return o;
        });

        http.get("/registry/entry", "Describe one registry entry: params id (registry), key (entry). Returns class + toString", req -> {
            Registry<?> registry = findRegistry(req.require("id"));
            ResourceLocation key = ResourceLocation.tryParse(req.require("key"));
            if (key == null) throw BridgeException.badRequest("invalid key");
            Object value = registry.get(key);
            if (value == null) throw BridgeException.notFound("no entry " + key + " in " + req.require("id"));
            JsonObject o = Json.obj();
            o.addProperty("key", key.toString());
            o.addProperty("class", value.getClass().getName());
            o.addProperty("toString", String.valueOf(value));
            o.addProperty("rawId", rawId(registry, value));
            return o;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> int rawId(Registry<T> registry, Object value) {
        return registry.getId((T) value);
    }

    /** Looks up a registry by id, preferring the server's registry access (which includes datapack registries). */
    public static Registry<?> findRegistry(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) throw BridgeException.badRequest("invalid registry id: " + id);
        MinecraftServer server = GameAccess.serverOrNull();
        if (server != null) {
            ResourceKey<Registry<Object>> key = ResourceKey.createRegistryKey(rl);
            Optional<Registry<Object>> dyn = server.registryAccess().registry(key);
            if (dyn.isPresent()) return dyn.get();
        }
        Registry<?> stat = BuiltInRegistries.REGISTRY.get(rl);
        if (stat == null) throw BridgeException.notFound("unknown registry: " + id);
        return stat;
    }
}
