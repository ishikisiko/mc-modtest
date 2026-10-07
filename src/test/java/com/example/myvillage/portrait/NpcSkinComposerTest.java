package com.example.myvillage.portrait;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The Java recolour of an NPC look draws exactly what {@code tools/npcgen} drew for every golden
 * (hair and eye colour per look), and its ramps match the Python vectors.
 */
final class NpcSkinComposerTest {
    private static final String GOLDENS = "/npc_skin_goldens/";
    private static final String TEXTURES = "/assets/myvillage/textures/entity/cultivator/";
    private static final String ROLES = "/assets/myvillage/npc/";

    @Test
    void everyGoldenMatchesPixelForPixel() throws IOException {
        List<String> failures = new ArrayList<>();
        int cases = 0;
        for (JsonElement element : goldens().getAsJsonArray("cases")) {
            JsonObject golden = element.getAsJsonObject();
            String name = golden.get("name").getAsString();
            String file = golden.get("file").getAsString();
            NpcColours colours = new NpcColours(enumOf(PortraitSpec.HairColour.class, golden.get("hair").getAsString()),
                    enumOf(PortraitSpec.EyeColour.class, golden.get("eye").getAsString()));
            PortraitMaps.Image base = png(TEXTURES + name + ".png");
            PortraitMaps.Image roles = png(ROLES + name + "_roles.png");
            PortraitMaps.Image expected = png(GOLDENS + file);
            assertEquals(base.width(), expected.width(), file);
            assertEquals(base.height(), expected.height(), file);
            int[] actual = NpcSkinComposer.compose(base, roles, colours);
            int diff = 0;
            String first = null;
            for (int i = 0; i < actual.length; i++) {
                int e = opaqueOrZero(expected.argb()[i]);
                int a = opaqueOrZero(actual[i]);
                if (e != a) {
                    diff++;
                    if (first == null) {
                        first = String.format("(%d,%d) expected %08X got %08X", i % base.width(), i / base.width(), e, a);
                    }
                }
            }
            if (diff > 0) {
                failures.add(file + " (" + golden.get("look").getAsString() + " " + colours + "): " + diff
                        + " texels differ, first " + first);
            }
            cases++;
        }
        assertTrue(cases >= 3, "golden cases " + cases);
        assertEquals(List.of(), failures);
    }

    @Test
    void hairRampsMatchThePythonVectors() throws IOException {
        JsonObject ramps = goldens().getAsJsonObject("ramp7");
        assertEquals(PortraitSpec.HairColour.values().length, ramps.size(), "ramp7 entries");
        for (PortraitSpec.HairColour colour : PortraitSpec.HairColour.values()) {
            String key = colour.name().toLowerCase(Locale.ROOT);
            assertTrue(ramps.has(key), "ramp7 has " + key);
            int[] expected = colours(ramps.getAsJsonArray(key));
            assertEquals(NpcSkinComposer.RAMP7, expected.length, key);
            assertArrayEquals(expected, NpcSkinComposer.hairRamp7(colour), key + " " + hex(NpcSkinComposer.hairRamp7(colour)));
        }
    }

    @Test
    void irisRowsMatchThePythonVectors() throws IOException {
        JsonObject iris = goldens().getAsJsonObject("iris");
        assertEquals(PortraitSpec.EyeColour.values().length, iris.size(), "iris entries");
        for (PortraitSpec.EyeColour colour : PortraitSpec.EyeColour.values()) {
            String key = colour.name().toLowerCase(Locale.ROOT);
            assertTrue(iris.has(key), "iris has " + key);
            int[] expected = colours(iris.getAsJsonArray(key));
            assertEquals(NpcSkinComposer.IRIS_WHITE_MIX + 1, expected.length, key);
            int[] eyes = PortraitPalette.eyes(colour);
            int[] actual = new int[expected.length];
            for (int i = 0; i < actual.length; i++) {
                actual[i] = NpcSkinComposer.iris(eyes, i);
            }
            assertArrayEquals(expected, actual, key + " " + hex(actual));
        }
    }

    @Test
    void hairTonesWalkTheRampInHalfSteps() {
        for (PortraitSpec.HairColour colour : PortraitSpec.HairColour.values()) {
            int[] ramp = NpcSkinComposer.hairRamp7(colour);
            assertEquals(ramp[NpcSkinComposer.RAMP7 - 1], NpcSkinComposer.hairTone(ramp, NpcSkinComposer.HAIR_TONES - 1),
                    colour + " tone 12 is the last step");
            for (int j = 0; j < NpcSkinComposer.RAMP7; j++) {
                assertEquals(ramp[j], NpcSkinComposer.hairTone(ramp, 2 * j), colour + " tone " + 2 * j);
            }
            for (int j = 0; j < NpcSkinComposer.RAMP7 - 1; j++) {
                assertEquals(PortraitPalette.mix(ramp[j], ramp[j + 1], 0.5), NpcSkinComposer.hairTone(ramp, 2 * j + 1),
                        colour + " tone " + (2 * j + 1));
            }
        }
        int[] ramp = NpcSkinComposer.hairRamp7(PortraitSpec.HairColour.INK_BLUE);
        assertThrows(IllegalArgumentException.class, () -> NpcSkinComposer.hairTone(ramp, -1));
        assertThrows(IllegalArgumentException.class, () -> NpcSkinComposer.hairTone(ramp, NpcSkinComposer.HAIR_TONES));
        int[] eyes = PortraitPalette.eyes(PortraitSpec.EyeColour.WATER);
        assertThrows(IllegalArgumentException.class, () -> NpcSkinComposer.iris(eyes, -1));
        assertThrows(IllegalArgumentException.class, () -> NpcSkinComposer.iris(eyes, NpcSkinComposer.IRIS_WHITE_MIX + 1));
    }

    @Test
    void composeRecoloursOnlyRoleTexelsAndLeavesTheBaseAlone() {
        int keep = 0xFF123456;
        int clear = 0x00ABCDEF;
        int[] baseArgb = {keep, 0xFF000000, 0xFF000000, clear, 0xFF000000, 0xFF000000};
        int[] roleArgb = {
                0x00010C00, // alpha 0: kept whatever the channels say
                role(NpcSkinComposer.MATERIAL_HAIR, 0),
                role(NpcSkinComposer.MATERIAL_HAIR, 12),
                0,
                role(NpcSkinComposer.MATERIAL_IRIS, 1),
                role(NpcSkinComposer.MATERIAL_IRIS, NpcSkinComposer.IRIS_WHITE_MIX)};
        PortraitMaps.Image base = new PortraitMaps.Image(3, 2, baseArgb.clone());
        PortraitMaps.Image roles = new PortraitMaps.Image(3, 2, roleArgb);
        NpcColours colours = new NpcColours(PortraitSpec.HairColour.GREY_DARK_BROWN, PortraitSpec.EyeColour.SPIRIT);
        int[] out = NpcSkinComposer.compose(base, roles, colours);
        int[] ramp = NpcSkinComposer.hairRamp7(colours.hair());
        int[] eyes = PortraitPalette.eyes(colours.eye());
        assertArrayEquals(new int[] {keep, ramp[0], ramp[6], clear, eyes[1],
                PortraitPalette.mix(eyes[2], NpcSkinComposer.EYE_WHITE, 0.5)}, out);
        assertArrayEquals(baseArgb, base.argb(), "the base is not modified");
    }

    @Test
    void composeRejectsASizeMismatch() {
        PortraitMaps.Image base = new PortraitMaps.Image(2, 2, new int[4]);
        NpcColours colours = new NpcColours(PortraitSpec.HairColour.SILVER, PortraitSpec.EyeColour.METAL);
        assertThrows(IllegalArgumentException.class,
                () -> NpcSkinComposer.compose(base, new PortraitMaps.Image(2, 1, new int[2]), colours));
        assertThrows(IllegalArgumentException.class,
                () -> NpcSkinComposer.compose(base, new PortraitMaps.Image(4, 1, new int[4]), colours));
    }

    @Test
    void composeRejectsAnUnknownMaterial() {
        PortraitMaps.Image base = new PortraitMaps.Image(2, 1, new int[] {0xFF000000, 0xFF000000});
        NpcColours colours = new NpcColours(PortraitSpec.HairColour.CHESTNUT, PortraitSpec.EyeColour.EARTH);
        for (int material : new int[] {0, 3, 255}) {
            PortraitMaps.Image roles = new PortraitMaps.Image(2, 1, new int[] {0, role(material, 0)});
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> NpcSkinComposer.compose(base, roles, colours), "material " + material);
            assertTrue(ex.getMessage().contains("material " + material), ex.getMessage());
        }
    }

    private static int role(int material, int index) {
        return 0xFF000000 | material << 16 | index << 8;
    }

    /** Alpha 0 is transparent whatever RGB the PNG kept there. */
    private static int opaqueOrZero(int argb) {
        return (argb >>> 24) == 0 ? 0 : argb;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String key) {
        return Enum.valueOf(type, key.toUpperCase(Locale.ROOT));
    }

    /** {@code #RRGGBB} (or {@code RRGGBB}) strings as opaque ARGB. */
    private static int[] colours(JsonArray hexes) {
        int[] out = new int[hexes.size()];
        for (int i = 0; i < out.length; i++) {
            String hex = hexes.get(i).getAsString();
            out[i] = 0xFF000000 | Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
        }
        return out;
    }

    private static String hex(int[] argb) {
        List<String> out = new ArrayList<>();
        for (int c : argb) {
            out.add(String.format("#%06X", c & 0xFFFFFF));
        }
        return out.toString();
    }

    private static PortraitMaps.Image png(String path) throws IOException {
        try (InputStream in = NpcSkinComposerTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing test resource " + path);
            BufferedImage image = ImageIO.read(in);
            assertNotNull(image, "not an image: " + path);
            int w = image.getWidth();
            int h = image.getHeight();
            return new PortraitMaps.Image(w, h, image.getRGB(0, 0, w, h, null, 0, w));
        }
    }

    private static JsonObject goldens() throws IOException {
        try (InputStream in = NpcSkinComposerTest.class.getResourceAsStream(GOLDENS + "goldens.json")) {
            assertNotNull(in, "missing test resource " + GOLDENS + "goldens.json (written by tools/npcgen)");
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
