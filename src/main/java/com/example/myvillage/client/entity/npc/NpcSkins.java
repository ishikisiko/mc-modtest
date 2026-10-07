package com.example.myvillage.client.entity.npc;

import com.example.myvillage.portrait.NpcColours;
import com.example.myvillage.portrait.NpcSkinComposer;
import com.example.myvillage.portrait.PortraitMaps;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recoloured NPC skins (发色瞳色): an NPC with {@link NpcColours} is drawn with a copy of its look's
 * baked texture whose hair and iris texels take the person's portrait colours
 * ({@link NpcSkinComposer}, driven by the look's role map {@link NpcRenderer#roleMap}). One
 * {@link DynamicTexture} per (NPC type, look, packed colours), at a deterministic location
 * {@code ns:npc/dynamic/<prefix>_<packed>}, kept until the next resource reload ({@link #clear()}).
 * The textures keep DynamicTexture's defaults (nearest filtering, no mipmaps), like the baked ones,
 * and the composer keeps the base alpha, so the cut-out holes stay holes.
 *
 * <p>Never breaks rendering: an NPC without colours, a look without a role map (remembered and
 * logged once at INFO), or any failure (remembered per key, logged once at ERROR) draws the baked
 * texture. Render thread only.
 */
public final class NpcSkins {
    private static final Logger LOGGER = LoggerFactory.getLogger(NpcSkins.class);

    private record Key(ResourceLocation npcId, String look, int packed) {
    }

    private static final Map<Key, ResourceLocation> TEXTURES = new HashMap<>();
    /** Role map locations found missing since the last reload. */
    private static final Set<ResourceLocation> NO_ROLES = new HashSet<>();
    /** Keys whose skin failed since the last reload. */
    private static final Set<Key> FAILED = new HashSet<>();
    private static boolean errorLogged;

    private NpcSkins() {
    }

    /**
     * The texture to draw a look in: {@code baked} for {@link NpcColours#NONE} (or a packed value
     * that names no colours), otherwise the recoloured copy, or {@code baked} when that cannot be made.
     */
    public static ResourceLocation texture(ResourceLocation npcId, String look, int packedColours,
            ResourceLocation baked) {
        if (packedColours == NpcColours.NONE) {
            return baked;
        }
        Key key = new Key(npcId, look, packedColours);
        ResourceLocation cached = TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }
        if (FAILED.contains(key)) {
            return baked;
        }
        ResourceLocation roles = NpcRenderer.roleMap(npcId, look);
        if (NO_ROLES.contains(roles)) {
            return baked;
        }
        try {
            NpcColours colours = NpcColours.unpack(packedColours);
            if (colours == null) {
                throw new IllegalArgumentException("packed colours " + packedColours + " name no colours");
            }
            ResourceManager resources = Minecraft.getInstance().getResourceManager();
            Optional<Resource> roleResource = resources.getResource(roles);
            if (roleResource.isEmpty()) {
                NO_ROLES.add(roles);
                LOGGER.info("NPC {} look {}: no role map {}; avatars keep the baked hair and eye colours",
                        npcId, look, NpcRenderer.assetPath(roles));
                return baked;
            }
            PortraitMaps.Image base = decode(resources.getResourceOrThrow(baked));
            PortraitMaps.Image roleMap = decode(roleResource.get());
            int[] argb = NpcSkinComposer.compose(base, roleMap, colours);
            ResourceLocation location =
                    npcId.withPath("npc/dynamic/" + NpcRenderer.fileName(npcId, look) + "_" + packedColours);
            upload(location, base.width(), base.height(), argb);
            TEXTURES.put(key, location);
            return location;
        } catch (Exception e) {
            FAILED.add(key);
            if (!errorLogged) {
                errorLogged = true;
                LOGGER.error("NPC {} look {}: recoloured skin for colours {} failed; drawing the baked texture"
                        + " (further failures are not logged until the next resource reload)",
                        npcId, look, packedColours, e);
            }
            return baked;
        }
    }

    /** Releases every recoloured texture and forgets the missing role maps and failures (resource reload). */
    public static void clear() {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation location : TEXTURES.values()) {
            textures.release(location);
        }
        TEXTURES.clear();
        NO_ROLES.clear();
        FAILED.clear();
        errorLogged = false;
    }

    /** NativeImage reads RGBA as little-endian ABGR ints; the composer works in ARGB. */
    private static PortraitMaps.Image decode(Resource resource) throws IOException {
        try (InputStream in = resource.open(); NativeImage image = NativeImage.read(NativeImage.Format.RGBA, in)) {
            int w = image.getWidth();
            int h = image.getHeight();
            int[] argb = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    argb[y * w + x] = swapRedBlue(image.getPixelRGBA(x, y));
                }
            }
            return new PortraitMaps.Image(w, h, argb);
        }
    }

    /** Registers the pixels at {@code location}, replacing (and closing) whatever was there. */
    private static void upload(ResourceLocation location, int width, int height, int[] argb) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setPixelRGBA(x, y, swapRedBlue(argb[y * width + x]));
                }
            }
        } catch (RuntimeException e) {
            image.close();
            throw e;
        }
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
    }

    /** Swaps the red and blue bytes; the same swap turns ARGB into ABGR and back. */
    private static int swapRedBlue(int c) {
        return (c & 0xFF00FF00) | (c >> 16 & 0xFF) | (c & 0xFF) << 16;
    }
}
