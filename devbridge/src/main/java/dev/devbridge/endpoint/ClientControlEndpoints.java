package dev.devbridge.endpoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.devbridge.http.BridgeException;
import dev.devbridge.http.BridgeHttpServer;
import dev.devbridge.util.Json;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static dev.devbridge.endpoint.ClientEndpoints.onClient;

/**
 * Client-only input endpoints: key mappings (move, jump, attack, use, hotbar, ...),
 * camera perspective, HUD, video options, and GUI clicks / typing.
 *
 * Key input goes through the game's own {@link KeyMapping}s, so it behaves like the
 * player pressing the bound key, whatever it is bound to. Held keys are released by a
 * client tick counter, so a hold keeps working after the HTTP call returns.
 */
public final class ClientControlEndpoints {
    private ClientControlEndpoints() {}

    /** Key mapping -> client tick count at which to release it. Client thread only. */
    private static final Map<KeyMapping, Long> HELD = new HashMap<>();
    private static final int TAP_TICKS = 2;
    private static long ticks;
    private static Field clickCount;
    private static boolean keepMouseFree;

    public static void register(BridgeHttpServer http) {
        keepMouseFree = http.config().keepMouseFree();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> releaseDueKeys());

        http.get("/client/keys", "Key mappings: name, category, bound key, down. params: contains (optional filter)", req -> {
            String contains = req.str("contains", null);
            return onClient(() -> {
                JsonArray arr = new JsonArray();
                for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
                    if (contains != null && !k.getName().contains(contains)) continue;
                    JsonObject o = Json.obj();
                    o.addProperty("name", k.getName());
                    o.addProperty("category", k.getCategory());
                    o.addProperty("key", k.getKey().getName());
                    o.addProperty("down", k.isDown());
                    arr.add(o);
                }
                return arr;
            });
        });

        http.post("/client/key", "Press key mappings as the player. params: name or names (e.g. jump, key.forward, hotbar.3, attack, use, togglePerspective), "
                + "action: click (default) | hold | down | up, ticks (hold length, default 10; 20 ticks = 1 s). Combine names to e.g. sprint forward.", req -> {
            List<String> names = names(req.json("names"), req.str("name", null));
            String action = req.str("action", "click");
            int holdTicks = Math.max(1, req.integer("ticks", 10));
            return onClient(() -> {
                JsonArray done = new JsonArray();
                for (String name : names) {
                    KeyMapping k = keyMapping(name);
                    switch (action) {
                        case "click" -> {
                            // A real tap: one counted press plus a short hold, so keys the game
                            // polls with isDown() (jump, sneak) react as well as consumeClick() ones.
                            k.setDown(true);
                            click(k);
                            HELD.merge(k, ticks + TAP_TICKS, Math::max);
                        }
                        case "down" -> { k.setDown(true); HELD.remove(k); }
                        case "up" -> { k.setDown(false); HELD.remove(k); }
                        case "hold" -> {
                            k.setDown(true);
                            click(k);
                            HELD.put(k, ticks + holdTicks);
                        }
                        default -> throw BridgeException.badRequest("action must be click, hold, down or up");
                    }
                    done.add(k.getName());
                }
                JsonObject o = Json.obj();
                o.add("keys", done);
                o.addProperty("action", action);
                if (action.equals("hold")) o.addProperty("ticks", holdTicks);
                return o;
            });
        });

        http.post("/client/key/release", "Release every key held through DevBridge (and all key mappings)", req -> onClient(() -> {
            HELD.clear();
            KeyMapping.releaseAll();
            return Json.of("released");
        }));

        http.get("/client/window", "Window size and position (screen coordinates), fullscreen, focused, mouseGrabbed", req -> onClient(() -> windowState()));

        http.post("/client/window", "Resize and/or move the game window. params: width, height, x, y (each optional; fullscreen windows are not resized)", req -> {
            int width = req.integer("width", 0), height = req.integer("height", 0);
            boolean move = req.has("x") && req.has("y");
            int x = req.integer("x", 0), y = req.integer("y", 0);
            return onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (width > 0 && height > 0) resizeWindow(mc, width, height);
                if (move) GLFW.glfwSetWindowPos(mc.getWindow().getWindow(), x, y);
                return windowState();
            });
        });

        http.post("/client/mouse", "Release (grab=false, default) or grab the mouse. A released mouse lets the user work while the game window has focus", req -> {
            boolean grab = req.bool("grab", false);
            return onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (grab) mc.mouseHandler.grabMouse();
                else mc.mouseHandler.releaseMouse();
                return windowState();
            });
        });

        http.post("/client/hotbar", "Select a hotbar slot. params: slot (0-8)", req -> {
            int slot = req.requireInt("slot");
            if (slot < 0 || slot > 8) throw BridgeException.badRequest("slot must be 0-8");
            return onClient(() -> {
                LocalPlayer p = player();
                p.getInventory().selected = slot;
                JsonObject o = Json.obj();
                o.addProperty("slot", slot);
                o.add("mainHand", Json.itemStack(p.getMainHandItem(), p.level().registryAccess()));
                return o;
            });
        });

        http.post("/client/perspective", "Camera perspective. params: mode = first | back | front (F5 cycle)", req -> {
            String mode = req.require("mode");
            CameraType type = switch (mode) {
                case "first", "first_person" -> CameraType.FIRST_PERSON;
                case "back", "third_person_back" -> CameraType.THIRD_PERSON_BACK;
                case "front", "third_person_front" -> CameraType.THIRD_PERSON_FRONT;
                default -> throw BridgeException.badRequest("mode must be first, back or front");
            };
            return onClient(() -> {
                Minecraft.getInstance().options.setCameraType(type);
                return Json.of(type.name());
            });
        });

        http.post("/client/hud", "Show or hide the HUD (F1). params: hidden (true/false)", req -> {
            boolean hidden = req.bool("hidden", true);
            return onClient(() -> {
                Minecraft.getInstance().options.hideGui = hidden;
                return Json.of(hidden);
            });
        });

        http.post("/client/option", "Read or set a video/game option by its Options accessor name, e.g. fov, renderDistance, gamma, simulationDistance. "
                + "params: name, value (omit to read), save (default false: change lasts until restart)", req -> {
            String name = req.require("name");
            JsonElement value = req.json("value");
            boolean save = req.bool("save", false);
            return onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                OptionInstance<Object> option = option(mc, name);
                JsonObject o = Json.obj();
                o.addProperty("name", name);
                o.addProperty("previous", String.valueOf(option.get()));
                if (value != null && !value.isJsonNull()) {
                    option.set(convert(option.get(), value));
                    if (save) mc.options.save();
                }
                o.addProperty("value", String.valueOf(option.get()));
                return o;
            });
        });

        http.post("/client/screen/click", "Click in the open GUI screen. params: widget (index from /client/screen) or x,y (GUI-scaled coords), button (0 left, 1 right; default 0)", req -> {
            int button = req.integer("button", 0);
            Integer widget = req.has("widget") ? req.requireInt("widget") : null;
            double x = req.dbl("x", -1), y = req.dbl("y", -1);
            return onClient(() -> {
                Screen screen = screen();
                double cx = x, cy = y;
                if (widget != null) {
                    List<? extends GuiEventListener> children = screen.children();
                    if (widget < 0 || widget >= children.size()) throw BridgeException.badRequest("no widget " + widget + " (screen has " + children.size() + ")");
                    if (!(children.get(widget) instanceof AbstractWidget aw)) throw BridgeException.badRequest("widget " + widget + " has no position; pass x,y");
                    cx = aw.getX() + aw.getWidth() / 2.0;
                    cy = aw.getY() + aw.getHeight() / 2.0;
                } else if (cx < 0 || cy < 0) {
                    throw BridgeException.badRequest("pass widget or x,y");
                }
                boolean handled = screen.mouseClicked(cx, cy, button);
                screen.mouseReleased(cx, cy, button);
                keepMouseFree();
                JsonObject o = Json.obj();
                o.addProperty("x", cx);
                o.addProperty("y", cy);
                o.addProperty("handled", handled);
                Screen after = Minecraft.getInstance().screen;
                o.addProperty("screenAfter", after == null ? null : after.getClass().getName());
                return o;
            });
        });

        http.post("/client/screen/type", "Type into the focused GUI widget. params: text (optional), key (optional, pressed after the text: "
                + "an InputConstants name such as key.keyboard.enter, key.keyboard.escape, key.keyboard.backspace)", req -> {
            String text = req.str("text", "");
            String key = req.str("key", null);
            return onClient(() -> {
                Screen screen = screen();
                text.codePoints().forEach(cp -> {
                    for (char c : Character.toChars(cp)) screen.charTyped(c, 0);
                });
                if (key != null) {
                    InputConstants.Key k = InputConstants.getKey(key);
                    if (k == InputConstants.UNKNOWN) throw BridgeException.badRequest("unknown key " + key);
                    screen.keyPressed(k.getValue(), 0, 0);
                    screen.keyReleased(k.getValue(), 0, 0);
                }
                keepMouseFree();
                return Json.of("typed");
            });
        });
    }

    /**
     * After DevBridge closed a screen (directly or by a click / key), give the cursor back:
     * setScreen(null) grabs the mouse whenever the game window has focus.
     */
    static void keepMouseFree() {
        Minecraft mc = Minecraft.getInstance();
        if (keepMouseFree && mc.screen == null) mc.mouseHandler.releaseMouse();
    }

    /** Windowed resize; a fullscreen window is left alone. Client thread. */
    static void resizeWindow(Minecraft mc, int width, int height) {
        if (mc.getWindow().isFullscreen()) return;
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), width, height);
    }

    private static void releaseDueKeys() {
        ticks++;
        for (Iterator<Map.Entry<KeyMapping, Long>> it = HELD.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<KeyMapping, Long> e = it.next();
            if (e.getValue() <= ticks) {
                e.getKey().setDown(false);
                it.remove();
            }
        }
    }

    private static JsonObject windowState() {
        Minecraft mc = Minecraft.getInstance();
        long handle = mc.getWindow().getWindow();
        int[] w = new int[1], h = new int[1], x = new int[1], y = new int[1];
        GLFW.glfwGetWindowSize(handle, w, h);
        GLFW.glfwGetWindowPos(handle, x, y);
        JsonObject o = Json.obj();
        o.addProperty("width", w[0]);
        o.addProperty("height", h[0]);
        o.addProperty("x", x[0]);
        o.addProperty("y", y[0]);
        o.addProperty("framebufferWidth", mc.getWindow().getWidth());
        o.addProperty("framebufferHeight", mc.getWindow().getHeight());
        o.addProperty("fullscreen", mc.getWindow().isFullscreen());
        o.addProperty("focused", mc.isWindowActive());
        o.addProperty("mouseGrabbed", mc.mouseHandler.isMouseGrabbed());
        return o;
    }

    private static List<String> names(JsonElement many, String one) {
        List<String> out = new ArrayList<>();
        if (many != null && many.isJsonArray()) for (JsonElement e : many.getAsJsonArray()) out.add(e.getAsString());
        if (one != null) out.add(one);
        if (out.isEmpty()) throw BridgeException.badRequest("pass name or names");
        return out;
    }

    /** Finds a key mapping by full name (key.jump) or without the "key." prefix (jump, hotbar.3). */
    private static KeyMapping keyMapping(String name) {
        String full = name.startsWith("key.") ? name : "key." + name;
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
            if (k.getName().equals(full) || k.getName().equals(name)) return k;
        }
        throw BridgeException.notFound("no key mapping " + name + " (see /client/keys)");
    }

    /** One press, as the game counts it (consumeClick), independent of what key it is bound to. */
    private static void click(KeyMapping k) {
        try {
            if (clickCount == null) {
                clickCount = KeyMapping.class.getDeclaredField("clickCount");
                clickCount.setAccessible(true);
            }
            clickCount.setInt(k, clickCount.getInt(k) + 1);
        } catch (ReflectiveOperationException e) {
            throw new BridgeException(500, "cannot click " + k.getName() + ": " + e, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static OptionInstance<Object> option(Minecraft mc, String name) {
        try {
            Method m = mc.options.getClass().getMethod(name);
            if (m.getParameterCount() == 0 && OptionInstance.class.isAssignableFrom(m.getReturnType())) {
                return (OptionInstance<Object>) m.invoke(mc.options);
            }
        } catch (NoSuchMethodException ignored) {
            // fall through
        } catch (ReflectiveOperationException e) {
            throw new BridgeException(500, "cannot read option " + name + ": " + e, e);
        }
        throw BridgeException.notFound("no option " + name + " (an Options method returning OptionInstance, e.g. fov, renderDistance, gamma)");
    }

    private static Object convert(Object current, JsonElement v) {
        if (current instanceof Integer) return v.getAsInt();
        if (current instanceof Double) return v.getAsDouble();
        if (current instanceof Boolean) return v.getAsBoolean();
        if (current instanceof Enum<?> e) {
            for (Object c : e.getDeclaringClass().getEnumConstants()) {
                if (((Enum<?>) c).name().equalsIgnoreCase(v.getAsString())) return c;
            }
            throw BridgeException.badRequest("no " + e.getDeclaringClass().getSimpleName() + " named " + v.getAsString());
        }
        if (current instanceof String) return v.getAsString();
        throw BridgeException.badRequest("cannot set an option of type " + current.getClass().getSimpleName());
    }

    private static LocalPlayer player() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) throw BridgeException.unavailable("no local player");
        return p;
    }

    private static Screen screen() {
        Screen s = Minecraft.getInstance().screen;
        if (s == null) throw BridgeException.unavailable("no screen is open");
        return s;
    }
}
