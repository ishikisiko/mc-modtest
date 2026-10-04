package dev.devbridge.endpoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import dev.devbridge.GameAccess;
import dev.devbridge.http.BridgeException;
import dev.devbridge.http.BridgeHttpServer;
import dev.devbridge.util.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.function.Supplier;

/**
 * Client-only endpoints: screenshots, open screen, client-side view of the world.
 *
 * This class must only be loaded on the client dist (it references client classes).
 * {@link dev.devbridge.DevBridge} guards the registration accordingly.
 */
public final class ClientEndpoints {
    private ClientEndpoints() {}

    /** Runs work on the client (render) thread and waits for it. */
    public static <T> T onClient(Supplier<T> work) {
        Minecraft mc = Minecraft.getInstance();
        return GameAccess.await(mc.submit(work));
    }

    public static Object minecraftInstance() {
        return Minecraft.getInstance();
    }

    public static void register(BridgeHttpServer http) {

        http.get("/client/state", "Client status: fps, in-world?, current screen, local player position/look, target block", req -> onClient(() -> {
            Minecraft mc = Minecraft.getInstance();
            JsonObject o = Json.obj();
            o.addProperty("fps", mc.getFps());
            o.addProperty("inWorld", mc.level != null);
            o.addProperty("paused", mc.isPaused());
            o.addProperty("singleplayer", mc.hasSingleplayerServer());
            o.addProperty("windowWidth", mc.getWindow().getWidth());
            o.addProperty("windowHeight", mc.getWindow().getHeight());
            o.addProperty("guiScale", mc.getWindow().getGuiScale());
            Screen screen = mc.screen;
            o.addProperty("screen", screen == null ? null : screen.getClass().getName());
            o.addProperty("screenTitle", screen == null ? null : screen.getTitle().getString());
            if (mc.level != null) {
                o.addProperty("dimension", mc.level.dimension().location().toString());
                o.addProperty("gameTime", mc.level.getGameTime());
            }
            LocalPlayer p = mc.player;
            if (p != null) {
                JsonObject pj = Json.entity(p, false);
                pj.add("blockPos", Json.pos(p.blockPosition()));
                pj.addProperty("selectedSlot", p.getInventory().selected);
                pj.add("mainHand", Json.itemStack(p.getMainHandItem(), mc.level.registryAccess()));
                o.add("player", pj);
            }
            HitResult hit = mc.hitResult;
            if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
                JsonObject t = Json.obj();
                t.add("pos", Json.pos(bhr.getBlockPos()));
                t.addProperty("face", bhr.getDirection().getName());
                t.add("state", Json.blockState(mc.level.getBlockState(bhr.getBlockPos())));
                o.add("targetBlock", t);
            } else if (hit instanceof EntityHitResult ehr) {
                o.add("targetEntity", Json.entity(ehr.getEntity(), false));
            }
            return o;
        }));

        http.get("/client/screenshot", "Capture the game window as PNG. params: save (default false -> also writes to screenshots/), maxWidth (downscale for token budget, optional)", req -> {
            boolean save = req.bool("save", false);
            int maxWidth = req.integer("maxWidth", 0);
            byte[] png = onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    byte[] bytes = image.asByteArray();
                    if (save) {
                        Path dir = FMLPaths.GAMEDIR.get().resolve("screenshots");
                        Files.createDirectories(dir);
                        Files.write(dir.resolve("devbridge-" + System.currentTimeMillis() + ".png"), bytes);
                    }
                    return bytes;
                } catch (IOException e) {
                    throw new BridgeException(500, "screenshot failed: " + e.getMessage(), e);
                }
            });
            if (maxWidth > 0) png = downscale(png, maxWidth);
            JsonObject o = Json.obj();
            o.addProperty("mime", "image/png");
            o.addProperty("bytes", png.length);
            o.addProperty("base64", Base64.getEncoder().encodeToString(png));
            return o;
        });

        http.get("/client/screen", "Describe the currently open GUI screen and its widgets", req -> onClient(() -> {
            Minecraft mc = Minecraft.getInstance();
            Screen screen = mc.screen;
            JsonObject o = Json.obj();
            if (screen == null) {
                o.addProperty("open", false);
                return o;
            }
            o.addProperty("open", true);
            o.addProperty("class", screen.getClass().getName());
            o.addProperty("title", screen.getTitle().getString());
            o.addProperty("width", screen.width);
            o.addProperty("height", screen.height);
            JsonArray widgets = new JsonArray();
            for (GuiEventListener child : screen.children()) {
                JsonObject w = Json.obj();
                w.addProperty("class", child.getClass().getName());
                if (child instanceof AbstractWidget aw) {
                    w.addProperty("message", aw.getMessage().getString());
                    w.addProperty("x", aw.getX());
                    w.addProperty("y", aw.getY());
                    w.addProperty("width", aw.getWidth());
                    w.addProperty("height", aw.getHeight());
                    w.addProperty("active", aw.active);
                    w.addProperty("visible", aw.visible);
                }
                widgets.add(w);
            }
            o.add("widgets", widgets);
            return o;
        }));

        http.post("/client/screen/close", "Close the current GUI screen", req -> onClient(() -> {
            Minecraft.getInstance().setScreen(null);
            return Json.of("closed");
        }));

        http.post("/client/chat", "Send a chat message or command as the local player. params: text (leading '/' = command)", req -> {
            String text = req.require("text");
            return onClient(() -> {
                LocalPlayer p = Minecraft.getInstance().player;
                if (p == null) throw BridgeException.unavailable("no local player");
                if (text.startsWith("/")) p.connection.sendCommand(text.substring(1));
                else p.connection.sendChat(text);
                return Json.of("sent");
            });
        });

        http.get("/client/block", "Client-side view of a block (works on remote servers too). params: x,y,z", req -> {
            int x = req.requireInt("x"), y = req.requireInt("y"), z = req.requireInt("z");
            return onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) throw BridgeException.unavailable("not in a world");
                BlockPos pos = new BlockPos(x, y, z);
                JsonObject o = Json.obj();
                o.add("pos", Json.pos(pos));
                o.addProperty("loaded", mc.level.isLoaded(pos));
                o.add("state", Json.blockState(mc.level.getBlockState(pos)));
                var be = mc.level.getBlockEntity(pos);
                if (be != null) o.add("blockEntity", Json.blockEntity(be, mc.level.registryAccess()));
                return o;
            });
        });

        http.post("/client/look", "Point the local player's camera. params: yaw, pitch", req -> {
            float yaw = (float) req.dbl("yaw", 0);
            float pitch = (float) req.dbl("pitch", 0);
            return onClient(() -> {
                LocalPlayer p = Minecraft.getInstance().player;
                if (p == null) throw BridgeException.unavailable("no local player");
                p.setYRot(yaw);
                p.setXRot(pitch);
                return Json.of("ok");
            });
        });
    }

    private static byte[] downscale(byte[] png, int maxWidth) {
        try {
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (src == null || src.getWidth() <= maxWidth) return png;
            int h = (int) Math.round(src.getHeight() * (maxWidth / (double) src.getWidth()));
            java.awt.image.BufferedImage dst = new java.awt.image.BufferedImage(maxWidth, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = dst.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, maxWidth, h, null);
            g.dispose();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(dst, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            return png;
        }
    }
}
