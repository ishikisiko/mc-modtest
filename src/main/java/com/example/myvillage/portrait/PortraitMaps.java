package com.example.myvillage.portrait;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.imageio.ImageIO;

/**
 * The part maps {@code tools/portraitgen/export.py} writes under {@code assets/myvillage/portrait/}:
 * per layer and variant, the roles one compose step paints and the mask it leaves for the hair
 * finish. Loaded once through a resource opener so the client can pass its resource manager and a
 * test its class loader; every file the manifest lists, and every file a {@link PortraitSpec} can
 * ask for, is checked here so a broken export fails at load rather than on some later face.
 */
public final class PortraitMaps {
    public static final int SIZE = 64;
    public static final int CELLS = SIZE * SIZE;

    /** Decodes a PNG to straight (not premultiplied) ARGB; the client uses NativeImage, tests ImageIO. */
    @FunctionalInterface
    public interface Decoder {
        Image decode(InputStream in) throws IOException;
    }

    /** Decoded pixels, row-major ARGB. */
    public record Image(int width, int height, int[] argb) {
    }

    /**
     * One map: {@code roles[i]} is the role index plus one the step paints at cell {@code i} (0 = it
     * paints nothing there), {@code mask[i]} whether the cell is in the step's mask (green channel).
     */
    public record Part(byte[] roles, boolean[] mask) {
    }

    private final Map<String, Part> parts;
    private final Set<String> clearsFace;

    private PortraitMaps(Map<String, Part> parts, Set<String> clearsFace) {
        this.parts = parts;
        this.clearsFace = clearsFace;
    }

    /** Loads with ImageIO; for tests and tools (the client passes its own decoder). */
    public static PortraitMaps load(Function<String, InputStream> open) {
        return load(open, PortraitMaps::decodeImageIo);
    }

    /**
     * @param open path under the portrait folder ({@code manifest.json}, {@code behind/short.png}) to a
     *             stream, or null when there is no such file
     */
    public static PortraitMaps load(Function<String, InputStream> open, Decoder decoder) {
        JsonObject manifest;
        try (InputStream in = open.apply("manifest.json")) {
            if (in == null) {
                throw new IllegalStateException("portrait manifest.json missing");
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                manifest = JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("portrait manifest.json", e);
        }
        String roles = manifest.get("roles").getAsString();
        if (!roles.equals(PortraitPalette.ROLES)) {
            throw new IllegalStateException("portrait manifest roles " + roles + " differ from " + PortraitPalette.ROLES);
        }
        Set<String> clearsFace = new HashSet<>();
        for (JsonElement e : manifest.getAsJsonArray("clears_face")) {
            clearsFace.add(e.getAsString());
        }
        Map<String, Part> parts = new HashMap<>();
        for (Map.Entry<String, JsonElement> layer : manifest.getAsJsonObject("layers").entrySet()) {
            JsonArray variants = layer.getValue().getAsJsonArray();
            for (JsonElement variant : variants) {
                String path = layer.getKey() + "/" + variant.getAsString();
                parts.put(path, readPart(open, decoder, path + ".png"));
            }
        }
        PortraitMaps maps = new PortraitMaps(Map.copyOf(parts), Set.copyOf(clearsFace));
        maps.checkComplete();
        return maps;
    }

    private static Part readPart(Function<String, InputStream> open, Decoder decoder, String path) {
        Image image;
        try (InputStream in = open.apply(path)) {
            if (in == null) {
                throw new IllegalStateException("portrait map " + path + " missing");
            }
            image = decoder.decode(in);
        } catch (IOException e) {
            throw new UncheckedIOException("portrait map " + path, e);
        }
        if (image.width() != SIZE || image.height() != SIZE) {
            throw new IllegalStateException("portrait map " + path + " is " + image.width() + "x" + image.height());
        }
        byte[] roles = new byte[CELLS];
        boolean[] mask = new boolean[CELLS];
        for (int i = 0; i < CELLS; i++) {
            int argb = image.argb()[i];
            int r = (argb >> 16) & 0xFF;
            if (r > PortraitPalette.ROLES.length()) {
                throw new IllegalStateException("portrait map " + path + " role " + r + " at " + (i % SIZE) + "," + (i / SIZE));
            }
            roles[i] = (byte) r;
            mask[i] = ((argb >> 8) & 0xFF) != 0;
        }
        return new Part(roles, mask);
    }

    /** Every variant a spec can name has a map, except headwear none, which the composer skips. */
    private void checkComplete() {
        for (PortraitSpec.Back back : PortraitSpec.Back.values()) {
            String b = PortraitSpec.key(back);
            require("behind", b);
            require("locks", b);
            require("knot", b);
            require("marks", "scar_" + b);
            for (PortraitSpec.Front front : PortraitSpec.Front.values()) {
                require("bangs", PortraitSpec.key(front) + "_" + b);
                require("marks", "bandage_" + PortraitSpec.key(front) + "_" + b);
            }
            for (PortraitSpec.Headwear headwear : PortraitSpec.Headwear.values()) {
                if (headwear != PortraitSpec.Headwear.NONE) {
                    require("headwear", PortraitSpec.key(headwear) + "_" + b);
                }
            }
        }
        require("blush", "normal");
        require("blush", "old");
        for (String g : new String[] {"f", "m"}) {
            require("nose", g);
            for (PortraitSpec.Face face : PortraitSpec.Face.values()) {
                require("face", PortraitSpec.key(face) + "_" + g);
                require("face", PortraitSpec.key(face) + "_" + g + "_old");
            }
            for (PortraitSpec.EyeShape eye : PortraitSpec.EyeShape.values()) {
                require("eyes", PortraitSpec.key(eye) + "_" + g);
            }
            for (PortraitSpec.Brow brow : PortraitSpec.Brow.values()) {
                require("brows", PortraitSpec.key(brow) + "_" + g);
            }
            for (PortraitSpec.Robe robe : PortraitSpec.Robe.values()) {
                require("coat", PortraitSpec.key(robe) + "_" + g);
            }
        }
        for (PortraitSpec.Mouth mouth : PortraitSpec.Mouth.values()) {
            require("mouth", PortraitSpec.key(mouth));
            require("mouth", PortraitSpec.key(mouth) + "_old");
        }
    }

    private void require(String layer, String variant) {
        part(layer, variant);
    }

    /** The map of one variant; a variant the manifest does not list is an error. */
    public Part part(String layer, String variant) {
        Part part = parts.get(layer + "/" + variant);
        if (part == null) {
            throw new IllegalStateException("portrait map " + layer + "/" + variant + " missing");
        }
        return part;
    }

    /** Whether the back style's silhouette leaves the face out (manifest {@code clears_face}). */
    public boolean clearsFace(String back) {
        return clearsFace.contains(back.toLowerCase(Locale.ROOT));
    }

    public int size() {
        return parts.size();
    }

    private static Image decodeImageIo(InputStream in) throws IOException {
        BufferedImage image = ImageIO.read(in);
        if (image == null) {
            throw new IOException("not an image");
        }
        int w = image.getWidth();
        int h = image.getHeight();
        return new Image(w, h, image.getRGB(0, 0, w, h, null, 0, w));
    }
}
