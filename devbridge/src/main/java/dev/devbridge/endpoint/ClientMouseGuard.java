package dev.devbridge.endpoint;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * Watch mode for a game that shares the user's desktop.
 *
 * Vanilla captures the mouse on its own whenever the window has focus and no screen is open
 * (joining a world, closing a screen). While watching, DevBridge undoes those captures, so the
 * agent can drive the game through the API without taking the user's cursor. A click into the
 * game view is the user taking over: it captures the mouse and switches to play (the click
 * itself does not attack). Leaving the window (Alt+Tab) returns to watch mode, and the toggle
 * key (default F8) switches either way. Worlds DevBridge opens start in watch mode.
 */
public final class ClientMouseGuard {
    private ClientMouseGuard() {}

    static final KeyMapping TOGGLE = new KeyMapping("key.devbridge.toggle_mouse", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.devbridge");

    private static boolean enabled;
    private static boolean watching;
    private static boolean wasActive;

    public static void init(IEventBus modBus, boolean keepMouseFree) {
        enabled = keepMouseFree;
        watching = false; // a game the user started by hand behaves normally
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(TOGGLE));
        NeoForge.EVENT_BUS.addListener(InputEvent.MouseButton.Pre.class, ClientMouseGuard::onMouseButton);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> onTick());
    }

    static boolean watching() {
        return watching;
    }

    /** Back to watch mode (does nothing when keepMouseFree is off). Client thread. */
    static void watch() {
        if (!enabled) return;
        watching = true;
        Minecraft.getInstance().mouseHandler.releaseMouse();
    }

    /** Normal play: the game may capture the mouse again. Client thread. */
    static void play() {
        watching = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) mc.mouseHandler.grabMouse();
    }

    /** A click into the game view while watching: the user takes over. */
    private static void onMouseButton(InputEvent.MouseButton.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (watching && e.getAction() == GLFW.GLFW_PRESS && mc.screen == null && mc.getOverlay() == null
                && !mc.mouseHandler.isMouseGrabbed()) {
            e.setCanceled(true); // the take-over click must not attack or use
            play();
            showMode(mc);
        }
    }

    private static void showMode(Minecraft mc) {
        if (mc.player == null) return;
        String key = TOGGLE.getTranslatedKeyMessage().getString();
        mc.gui.setOverlayMessage(watching
                ? Component.translatable("devbridge.mouse.watching", key)
                : Component.translatable("devbridge.mouse.playing", key), false);
    }

    private static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        while (TOGGLE.consumeClick()) {
            if (watching) play();
            else {
                enabled = true;
                watch();
            }
            showMode(mc);
        }
        // Leaving the window hands the mouse back for good: coming back needs a click again.
        boolean active = mc.isWindowActive();
        if (enabled && !watching && wasActive && !active && mc.level != null) watch();
        wasActive = active;
        if (watching && mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
    }
}
