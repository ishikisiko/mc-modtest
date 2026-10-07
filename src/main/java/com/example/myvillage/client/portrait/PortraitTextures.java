package com.example.myvillage.client.portrait;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.portrait.PortraitComposer;
import com.example.myvillage.portrait.PortraitMaps;
import com.example.myvillage.portrait.PortraitSpec;
import com.example.myvillage.portrait.PortraitThumbnail;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client textures of the ledger's portraits. A face is composed from the part maps the first time a
 * screen asks for it and kept as a {@link DynamicTexture} (nearest filtering, no mipmaps, so the pixel
 * art stays crisp at integer GUI scales). The 天下 page can list hundreds of people, so the cache is a
 * small LRU that releases what falls out. Any failure (missing maps, a broken export) logs once and
 * yields a magenta placeholder, so a portrait problem never takes a screen down. Render thread only.
 */
public final class PortraitTextures {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortraitTextures.class);
    private static final int CACHE_SIZE = 256;
    private static final int PLACEHOLDER_COLOUR = 0xFFFF00FF;
    private static final ResourceLocation PLACEHOLDER =
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "portrait/dynamic/placeholder");

    private static final Map<PortraitSpec, ResourceLocation> FULL = lru();
    private static final Map<PortraitSpec, ResourceLocation> THUMBS = lru();
    private static PortraitMaps maps;
    private static boolean mapsFailed;
    private static boolean placeholderRegistered;
    private static boolean warned;
    private static long counter;

    private PortraitTextures() {
    }

    /** The 64x64 portrait. */
    public static ResourceLocation texture(PortraitSpec spec) {
        ResourceLocation location = FULL.get(spec);
        if (location == null) {
            int[] argb = composeOrNull(spec);
            if (argb == null) {
                return placeholder();
            }
            location = upload("full", argb, PortraitMaps.SIZE);
            FULL.put(spec, location);
        }
        return location;
    }

    /** The 16x16 thumbnail for list rows (each pixel the average of the opaque ones in its 4x4 block). */
    public static ResourceLocation thumbnail(PortraitSpec spec) {
        ResourceLocation location = THUMBS.get(spec);
        if (location == null) {
            int[] argb = composeOrNull(spec);
            if (argb == null) {
                return placeholder();
            }
            location = upload("thumb", PortraitThumbnail.shrink(argb), PortraitThumbnail.SIZE);
            THUMBS.put(spec, location);
        }
        return location;
    }

    /**
     * Releases every composed portrait and forgets the part maps (and a failure to load them), so the
     * next request reads the maps again; called on every client resource reload.
     */
    public static void clear() {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation location : FULL.values()) {
            textures.release(location);
        }
        for (ResourceLocation location : THUMBS.values()) {
            textures.release(location);
        }
        FULL.clear();
        THUMBS.clear();
        maps = null;
        mapsFailed = false;
        warned = false;
    }

    /** Draws the portrait as a {@code size}-pixel square; at 16 or less the thumbnail, else the 64. */
    public static void draw(GuiGraphics graphics, PortraitSpec spec, int x, int y, int size) {
        ResourceLocation location = size <= PortraitThumbnail.SIZE ? thumbnail(spec) : texture(spec);
        graphics.blit(location, x, y, 0.0F, 0.0F, size, size, size, size);
    }

    private static int[] composeOrNull(PortraitSpec spec) {
        PortraitMaps loaded = maps();
        if (loaded == null) {
            return null;
        }
        try {
            return PortraitComposer.compose(loaded, spec);
        } catch (RuntimeException e) {
            warnOnce("Portrait compose failed for " + spec, e);
            return null;
        }
    }

    private static PortraitMaps maps() {
        if (maps == null && !mapsFailed) {
            ResourceManager resources = Minecraft.getInstance().getResourceManager();
            try {
                maps = PortraitMaps.load(path -> open(resources, path), PortraitTextures::decode);
                LOGGER.info("Portrait part maps loaded: {}", maps.size());
            } catch (RuntimeException e) {
                mapsFailed = true;
                warnOnce("Portrait part maps failed to load; portraits show a placeholder", e);
            }
        }
        return maps;
    }

    private static InputStream open(ResourceManager resources, String path) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "portrait/" + path);
        try {
            return resources.getResource(location).isPresent() ? resources.open(location) : null;
        } catch (IOException e) {
            throw new UncheckedIOException(location.toString(), e);
        }
    }

    /** NativeImage reads RGBA as little-endian ABGR ints; the composer works in ARGB. */
    private static PortraitMaps.Image decode(InputStream in) throws IOException {
        try (NativeImage image = NativeImage.read(NativeImage.Format.RGBA, in)) {
            int w = image.getWidth();
            int h = image.getHeight();
            int[] argb = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    argb[y * w + x] = abgrToArgb(image.getPixelRGBA(x, y));
                }
            }
            return new PortraitMaps.Image(w, h, argb);
        }
    }

    private static ResourceLocation upload(String kind, int[] argb, int size) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setPixelRGBA(x, y, abgrToArgb(argb[y * size + x]));
            }
        }
        ResourceLocation location =
                ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "portrait/dynamic/" + kind + "_" + counter++);
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
        return location;
    }

    private static ResourceLocation placeholder() {
        if (!placeholderRegistered) {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, PortraitMaps.SIZE, PortraitMaps.SIZE, false);
            image.fillRect(0, 0, PortraitMaps.SIZE, PortraitMaps.SIZE, abgrToArgb(PLACEHOLDER_COLOUR));
            Minecraft.getInstance().getTextureManager().register(PLACEHOLDER, new DynamicTexture(image));
            placeholderRegistered = true;
        }
        return PLACEHOLDER;
    }

    /** Swaps the red and blue bytes; the same swap turns ARGB into ABGR and back. */
    private static int abgrToArgb(int c) {
        return (c & 0xFF00FF00) | (c >> 16 & 0xFF) | (c & 0xFF) << 16;
    }

    private static void warnOnce(String message, Throwable e) {
        if (!warned) {
            warned = true;
            LOGGER.error(message, e);
        }
    }

    private static Map<PortraitSpec, ResourceLocation> lru() {
        return new LinkedHashMap<>(64, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<PortraitSpec, ResourceLocation> eldest) {
                if (size() > CACHE_SIZE) {
                    Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                    return true;
                }
                return false;
            }
        };
    }
}
