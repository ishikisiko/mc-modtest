package dev.devbridge.endpoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.devbridge.DevBridge;
import dev.devbridge.GameAccess;
import dev.devbridge.http.BridgeException;
import dev.devbridge.http.BridgeHttpServer;
import dev.devbridge.http.Request;
import dev.devbridge.util.Json;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Reflection endpoints: a small REPL-ish escape hatch for development.
 *
 * Lets an agent read any field or call any method on any class — including the
 * mod under test — without DevBridge having a compile-time dependency on it.
 * Disabled with {@code allowReflect=false} in config.
 */
public final class ReflectEndpoints {
    private ReflectEndpoints() {}

    public static void register(BridgeHttpServer http) {
        boolean enabled = http.config().allowReflect();

        http.get("/reflect/describe", "Public/declared members of a class. params: class (FQN), declaredOnly (default true)", req -> {
            check(enabled);
            Class<?> cls = loadClass(req.require("class"));
            boolean declaredOnly = req.bool("declaredOnly", true);
            JsonObject o = Json.obj();
            o.addProperty("class", cls.getName());
            o.addProperty("superclass", cls.getSuperclass() == null ? null : cls.getSuperclass().getName());
            JsonArray interfaces = new JsonArray();
            for (Class<?> i : cls.getInterfaces()) interfaces.add(i.getName());
            o.add("interfaces", interfaces);

            JsonArray fields = new JsonArray();
            for (Field f : declaredOnly ? cls.getDeclaredFields() : cls.getFields()) {
                fields.add(Modifier.toString(f.getModifiers()) + " " + f.getType().getSimpleName() + " " + f.getName());
            }
            o.add("fields", fields);

            JsonArray methods = new JsonArray();
            for (Method m : declaredOnly ? cls.getDeclaredMethods() : cls.getMethods()) {
                StringBuilder sb = new StringBuilder();
                sb.append(Modifier.toString(m.getModifiers())).append(' ')
                  .append(m.getReturnType().getSimpleName()).append(' ')
                  .append(m.getName()).append('(');
                Class<?>[] params = m.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(params[i].getSimpleName());
                }
                sb.append(')');
                methods.add(sb.toString());
            }
            o.add("methods", methods);
            return o;
        });

        http.post("/reflect/field", "Read a field. params: class, field, target (see /reflect/targets; omit for static), mainThread (default true)", req -> {
            check(enabled);
            Class<?> cls = loadClass(req.require("class"));
            String fieldName = req.require("field");
            Supplier<JsonElement> work = () -> {
                try {
                    Object target = resolveTarget(req);
                    Field f = findField(target != null ? target.getClass() : cls, fieldName);
                    if (f == null) f = findField(cls, fieldName);
                    if (f == null) throw BridgeException.notFound("no field " + fieldName + " on " + cls.getName());
                    f.setAccessible(true);
                    Object value = f.get(Modifier.isStatic(f.getModifiers()) ? null : target);
                    return describeValue(value);
                } catch (ReflectiveOperationException e) {
                    throw new BridgeException(500, e.toString(), e);
                }
            };
            return maybeMainThread(req, work);
        });

        http.post("/reflect/invoke", "Invoke a method. params: class, method, args (JSON array), target (omit for static), mainThread (default true)", req -> {
            check(enabled);
            Class<?> cls = loadClass(req.require("class"));
            String methodName = req.require("method");
            JsonElement argsEl = req.json("args");
            if (argsEl != null && !argsEl.isJsonNull() && !argsEl.isJsonArray()) throw BridgeException.badRequest("args must be a JSON array");
            JsonArray args = argsEl == null || argsEl.isJsonNull() ? new JsonArray() : argsEl.getAsJsonArray();
            Supplier<JsonElement> work = () -> {
                try {
                    Object target = resolveTarget(req);
                    Class<?> searchIn = target != null ? target.getClass() : cls;
                    Method m = findMethod(searchIn, methodName, args);
                    if (m == null && target != null) m = findMethod(cls, methodName, args);
                    if (m == null) throw BridgeException.notFound("no method " + methodName + "/" + args.size() + " on " + searchIn.getName());
                    m.setAccessible(true);
                    Object[] converted = convertArgs(m.getParameterTypes(), args);
                    Object value = m.invoke(Modifier.isStatic(m.getModifiers()) ? null : target, converted);
                    return describeValue(value);
                } catch (ReflectiveOperationException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    throw new BridgeException(500, cause.toString(), cause);
                }
            };
            return maybeMainThread(req, work);
        });

        http.get("/reflect/targets", "Named instances usable as 'target' in reflect calls", req -> Json.of(List.of(
                "server                      -> the MinecraftServer",
                "level:<dimension id>        -> a ServerLevel (default overworld)",
                "player:<name>               -> a ServerPlayer",
                "blockentity:<dim>:<x>:<y>:<z> -> a BlockEntity",
                "entity:<uuid>               -> an Entity (searched in all dimensions)",
                "mod:<modid>                 -> the ModContainer",
                "minecraft                   -> Minecraft client instance (client only)",
                "static:<class>:<field>      -> value of a static field")));
    }

    private static void check(boolean enabled) {
        if (!enabled) throw new BridgeException(403, "reflection disabled (allowReflect=false in config/devbridge.json)");
    }

    /**
     * Picks the thread to run reflective work on.
     * thread=server|client|none overrides; default is the server thread when a server is
     * running, except for client targets ("minecraft") which always use the render thread.
     */
    private static JsonElement maybeMainThread(Request req, Supplier<JsonElement> work) {
        String thread = req.str("thread", null);
        if (thread == null) {
            if (!req.bool("mainThread", true)) thread = "none";
            else if (req.str("target", "").startsWith("minecraft")) thread = "client";
            else thread = GameAccess.serverOrNull() != null ? "server" : (FMLEnvironment.dist.isClient() ? "client" : "none");
        }
        switch (thread) {
            case "none" -> { return work.get(); }
            case "server" -> {
                MinecraftServer server = GameAccess.server();
                return GameAccess.await(server.submit(work));
            }
            case "client" -> {
                if (!FMLEnvironment.dist.isClient()) throw BridgeException.badRequest("no client thread on a dedicated server");
                return ClientEndpoints.onClient(work);
            }
            default -> throw BridgeException.badRequest("thread must be server, client or none");
        }
    }

    static Class<?> loadClass(String name) {
        try {
            return Class.forName(name, true, DevBridge.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw BridgeException.notFound("class not found: " + name);
        }
    }

    private static Object resolveTarget(Request req) {
        String target = req.str("target", null);
        if (target == null || target.isBlank()) return null;
        String[] parts = target.split(":", 2);
        String kind = parts[0];
        String rest = parts.length > 1 ? parts[1] : "";
        switch (kind) {
            case "server" -> { return GameAccess.server(); }
            case "level" -> { return GameAccess.level(GameAccess.server(), rest); }
            case "player" -> { return GameAccess.player(GameAccess.server(), rest); }
            case "mod" -> {
                return ModList.get().getModContainerById(rest)
                        .orElseThrow(() -> BridgeException.notFound("no mod " + rest));
            }
            case "entity" -> {
                var uuid = java.util.UUID.fromString(rest);
                for (var level : GameAccess.server().getAllLevels()) {
                    var e = level.getEntity(uuid);
                    if (e != null) return e;
                }
                throw BridgeException.notFound("entity not found: " + rest);
            }
            case "blockentity" -> {
                // dim:x:y:z  — dim may itself contain a colon, so parse from the right
                String[] p = rest.split(":");
                if (p.length < 4) throw BridgeException.badRequest("blockentity target must be dim:x:y:z");
                int z = Integer.parseInt(p[p.length - 1]);
                int y = Integer.parseInt(p[p.length - 2]);
                int x = Integer.parseInt(p[p.length - 3]);
                String dim = String.join(":", java.util.Arrays.copyOf(p, p.length - 3));
                var level = GameAccess.level(GameAccess.server(), dim);
                var be = level.getBlockEntity(new BlockPos(x, y, z));
                if (be == null) throw BridgeException.notFound("no block entity at " + x + "," + y + "," + z);
                return be;
            }
            case "minecraft" -> {
                if (!FMLEnvironment.dist.isClient()) throw BridgeException.badRequest("'minecraft' target only exists on the client");
                return ClientEndpoints.minecraftInstance();
            }
            case "static" -> {
                String[] p = rest.split(":", 2);
                if (p.length != 2) throw BridgeException.badRequest("static target must be class:field");
                try {
                    Field f = findField(loadClass(p[0]), p[1]);
                    if (f == null) throw BridgeException.notFound("no field " + p[1]);
                    f.setAccessible(true);
                    return f.get(null);
                } catch (IllegalAccessException e) {
                    throw new BridgeException(500, e.toString(), e);
                }
            }
            default -> throw BridgeException.badRequest("unknown target kind: " + kind);
        }
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static Method findMethod(Class<?> cls, String name, JsonArray args) {
        List<Method> candidates = new ArrayList<>();
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.size()) candidates.add(m);
            }
            for (Class<?> i : c.getInterfaces()) {
                for (Method m : i.getMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == args.size()) candidates.add(m);
                }
            }
        }
        for (Method m : candidates) {
            try {
                convertArgs(m.getParameterTypes(), args);
                return m;
            } catch (BridgeException ignored) {}
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static Object[] convertArgs(Class<?>[] types, JsonArray args) {
        Object[] out = new Object[types.length];
        for (int i = 0; i < types.length; i++) out[i] = convert(types[i], args.get(i));
        return out;
    }

    /** Converts a JSON value to a Java parameter type. Supports primitives, strings, enums, ResourceLocation, BlockPos and a few game objects. */
    private static Object convert(Class<?> type, JsonElement el) {
        if (el == null || el.isJsonNull()) {
            if (type.isPrimitive()) throw BridgeException.badRequest("null for primitive " + type);
            return null;
        }
        if (type == String.class || type == CharSequence.class) return el.isJsonPrimitive() ? el.getAsString() : el.toString();
        if (type == int.class || type == Integer.class) return el.getAsInt();
        if (type == long.class || type == Long.class) return el.getAsLong();
        if (type == double.class || type == Double.class) return el.getAsDouble();
        if (type == float.class || type == Float.class) return el.getAsFloat();
        if (type == boolean.class || type == Boolean.class) return el.getAsBoolean();
        if (type == short.class || type == Short.class) return el.getAsShort();
        if (type == byte.class || type == Byte.class) return el.getAsByte();
        if (type == char.class || type == Character.class) return el.getAsString().charAt(0);
        if (type.isEnum()) {
            for (Object c : type.getEnumConstants()) if (((Enum<?>) c).name().equalsIgnoreCase(el.getAsString())) return c;
            throw BridgeException.badRequest("no enum constant " + el.getAsString() + " in " + type.getName());
        }
        if (type == ResourceLocation.class) return ResourceLocation.parse(el.getAsString());
        if (type == BlockPos.class) {
            JsonObject o = el.getAsJsonObject();
            return new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
        }
        if (type == JsonElement.class) return el;
        if (type == Object.class && el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsNumber();
            return p.getAsString();
        }
        // Allow a string of the form "target:..." to resolve a game object (e.g. a player or level).
        if (el.isJsonPrimitive() && el.getAsString().contains(":")) {
            Object resolved = resolveTarget(new Request("POST", "/", java.util.Map.of("target", el.getAsString()), null));
            if (resolved != null && type.isInstance(resolved)) return resolved;
        }
        throw BridgeException.badRequest("cannot convert " + el + " to " + type.getName());
    }

    private static JsonElement describeValue(Object value) {
        JsonObject o = Json.obj();
        if (value == null) {
            o.add("value", com.google.gson.JsonNull.INSTANCE);
            return o;
        }
        o.addProperty("class", value.getClass().getName());
        o.add("value", Json.of(value));
        o.addProperty("toString", String.valueOf(value));
        return o;
    }
}
