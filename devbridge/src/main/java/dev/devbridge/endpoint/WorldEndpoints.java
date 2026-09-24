package dev.devbridge.endpoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.devbridge.GameAccess;
import dev.devbridge.http.BridgeException;
import dev.devbridge.http.BridgeHttpServer;
import dev.devbridge.util.Json;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side world inspection and small mutations. */
public final class WorldEndpoints {
    private WorldEndpoints() {}

    public static void register(BridgeHttpServer http) {

        http.get("/world/block", "Block state + block entity at a position. params: x,y,z, dimension (optional)", req -> {
            int x = req.requireInt("x"), y = req.requireInt("y"), z = req.requireInt("z");
            String dim = req.str("dimension", null);
            return GameAccess.onServer(server -> {
                ServerLevel level = GameAccess.level(server, dim);
                BlockPos pos = new BlockPos(x, y, z);
                JsonObject o = Json.obj();
                o.add("pos", Json.pos(pos));
                o.addProperty("dimension", level.dimension().location().toString());
                o.addProperty("loaded", level.isLoaded(pos));
                if (!level.isLoaded(pos)) return o;
                BlockState state = level.getBlockState(pos);
                o.add("state", Json.blockState(state));
                o.addProperty("light", level.getMaxLocalRawBrightness(pos));
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) o.add("blockEntity", Json.blockEntity(be, level.registryAccess()));
                return o;
            });
        });

        http.get("/world/area", "Block states in a box (max 32^3 = 32768 blocks), grouped by state string. params: x1,y1,z1,x2,y2,z2, dimension, includeAir (default false), positions (default false)", req -> {
            int x1 = req.requireInt("x1"), y1 = req.requireInt("y1"), z1 = req.requireInt("z1");
            int x2 = req.requireInt("x2"), y2 = req.requireInt("y2"), z2 = req.requireInt("z2");
            String dim = req.str("dimension", null);
            boolean includeAir = req.bool("includeAir", false);
            boolean positions = req.bool("positions", false);
            int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
            long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            if (volume > 32768) throw BridgeException.badRequest("area too large: " + volume + " blocks (max 32768)");

            return GameAccess.onServer(server -> {
                ServerLevel level = GameAccess.level(server, dim);
                Map<String, Integer> counts = new HashMap<>();
                Map<String, JsonArray> posLists = new HashMap<>();
                int blockEntities = 0;
                for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
                    BlockState state = level.getBlockState(pos);
                    if (!includeAir && state.isAir()) continue;
                    String key = BlockStateParser.serialize(state);
                    counts.merge(key, 1, Integer::sum);
                    if (positions) posLists.computeIfAbsent(key, k -> new JsonArray()).add(Json.pos(pos.immutable()));
                    if (level.getBlockEntity(pos) != null) blockEntities++;
                }
                JsonObject o = Json.obj();
                o.addProperty("volume", volume);
                o.addProperty("blockEntities", blockEntities);
                o.add("counts", Json.of(counts));
                if (positions) o.add("positions", Json.of(posLists));
                return o;
            });
        });

        http.post("/world/block", "Set a block. params: x,y,z, state (e.g. minecraft:oak_log[axis=y]), dimension, flags (default 3)", req -> {
            int x = req.requireInt("x"), y = req.requireInt("y"), z = req.requireInt("z");
            String stateText = req.require("state");
            String dim = req.str("dimension", null);
            int flags = req.integer("flags", Block.UPDATE_ALL);
            return GameAccess.onServer(server -> {
                ServerLevel level = GameAccess.level(server, dim);
                BlockStateParser.BlockResult parsed;
                try {
                    parsed = BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), stateText, true);
                } catch (Exception e) {
                    throw BridgeException.badRequest("invalid block state '" + stateText + "': " + e.getMessage());
                }
                BlockPos pos = new BlockPos(x, y, z);
                boolean changed = level.setBlock(pos, parsed.blockState(), flags);
                if (parsed.nbt() != null) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be != null) {
                        be.loadWithComponents(parsed.nbt(), level.registryAccess());
                        be.setChanged();
                    }
                }
                JsonObject o = Json.obj();
                o.addProperty("changed", changed);
                o.add("state", Json.blockState(level.getBlockState(pos)));
                return o;
            });
        });

        http.get("/world/entities", "Entities near a point or in the whole dimension. params: dimension, x,y,z + radius (optional), type (substring filter), nbt (default false), limit (default 100)", req -> {
            String dim = req.str("dimension", null);
            boolean withNbt = req.bool("nbt", false);
            String type = req.str("type", null);
            int limit = req.integer("limit", 100);
            boolean hasCenter = req.has("x") && req.has("y") && req.has("z");
            double cx = req.dbl("x", 0), cy = req.dbl("y", 0), cz = req.dbl("z", 0);
            double radius = req.dbl("radius", 16);

            return GameAccess.onServer(server -> {
                ServerLevel level = GameAccess.level(server, dim);
                JsonArray arr = new JsonArray();
                int total = 0;
                Iterable<? extends Entity> source = hasCenter
                        ? level.getEntitiesOfClass(Entity.class, new AABB(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius))
                        : level.getAllEntities();
                for (Entity e : source) {
                    if (type != null && !BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().contains(type)) continue;
                    total++;
                    if (arr.size() < limit) arr.add(Json.entity(e, withNbt));
                }
                JsonObject o = Json.obj();
                o.addProperty("total", total);
                o.add("entities", arr);
                return o;
            });
        });

        http.get("/world/entity", "One entity by uuid or numeric id, with full NBT. params: uuid | id, dimension", req -> {
            String dim = req.str("dimension", null);
            String uuid = req.str("uuid", null);
            int id = req.integer("id", -1);
            if (uuid == null && id < 0) throw BridgeException.badRequest("uuid or id required");
            return GameAccess.onServer(server -> {
                ServerLevel level = GameAccess.level(server, dim);
                Entity e;
                if (uuid != null) {
                    UUID parsed;
                    try { parsed = UUID.fromString(uuid); }
                    catch (IllegalArgumentException ex) { throw BridgeException.badRequest("invalid uuid: " + uuid); }
                    e = level.getEntity(parsed);
                } else {
                    e = level.getEntity(id);
                }
                if (e == null) throw BridgeException.notFound("entity not found");
                return Json.entity(e, true);
            });
        });

        http.get("/player", "Player state: position, health, food, xp, gamemode, held items, full inventory. params: name (default: first online player)", req -> {
            String name = req.str("name", null);
            return GameAccess.onServer(server -> {
                ServerPlayer p;
                if (name != null) p = GameAccess.player(server, name);
                else {
                    var players = server.getPlayerList().getPlayers();
                    if (players.isEmpty()) throw BridgeException.notFound("no players online");
                    p = players.get(0);
                }
                var registries = server.registryAccess();
                JsonObject o = Json.entity(p, false);
                o.addProperty("name", p.getGameProfile().getName());
                o.addProperty("gameMode", p.gameMode.getGameModeForPlayer().getName());
                o.addProperty("food", p.getFoodData().getFoodLevel());
                o.addProperty("saturation", p.getFoodData().getSaturationLevel());
                o.addProperty("xpLevel", p.experienceLevel);
                o.addProperty("xpProgress", p.experienceProgress);
                o.addProperty("selectedSlot", p.getInventory().selected);
                o.add("mainHand", Json.itemStack(p.getMainHandItem(), registries));
                o.add("offHand", Json.itemStack(p.getOffhandItem(), registries));
                o.add("blockPos", Json.pos(p.blockPosition()));

                JsonArray inv = new JsonArray();
                var items = p.getInventory().items;
                for (int i = 0; i < items.size(); i++) {
                    ItemStack s = items.get(i);
                    if (s.isEmpty()) continue;
                    JsonObject slot = Json.itemStack(s, registries);
                    slot.addProperty("slot", i);
                    inv.add(slot);
                }
                o.add("inventory", inv);
                JsonArray armor = new JsonArray();
                for (ItemStack s : p.getInventory().armor) armor.add(Json.itemStack(s, registries));
                o.add("armor", armor);
                return o;
            });
        });

        http.get("/players", "Names of online players", req -> GameAccess.onServer(server -> {
            JsonArray arr = new JsonArray();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) arr.add(p.getGameProfile().getName());
            return arr;
        }));

        http.get("/world/chunk", "Whether a chunk is loaded and its status. params: chunkX, chunkZ (or x,z block coords), dimension", req -> {
            String dim = req.str("dimension", null);
            int chunkX = req.has("chunkX") ? req.requireInt("chunkX") : req.requireInt("x") >> 4;
            int chunkZ = req.has("chunkZ") ? req.requireInt("chunkZ") : req.requireInt("z") >> 4;
            return GameAccess.onServer(server -> {
                ServerLevel level = GameAccess.level(server, dim);
                JsonObject o = Json.obj();
                o.addProperty("chunkX", chunkX);
                o.addProperty("chunkZ", chunkZ);
                o.addProperty("loaded", level.hasChunk(chunkX, chunkZ));
                var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk != null) {
                    o.addProperty("status", chunk.getPersistedStatus().toString());
                    o.addProperty("blockEntities", chunk.getBlockEntities().size());
                    o.addProperty("inhabitedTime", chunk.getInhabitedTime());
                }
                return o;
            });
        });

        http.get("/world/biome", "Biome at a position. params: x,y,z, dimension", req -> {
            int x = req.requireInt("x"), y = req.requireInt("y"), z = req.requireInt("z");
            String dim = req.str("dimension", null);
            return GameAccess.onServer(server -> {
                ServerLevel level = GameAccess.level(server, dim);
                var holder = level.getBiome(new BlockPos(x, y, z));
                JsonObject o = Json.obj();
                o.addProperty("biome", holder.unwrapKey().map(k -> k.location().toString()).orElse("unregistered"));
                return o;
            });
        });
    }
}
