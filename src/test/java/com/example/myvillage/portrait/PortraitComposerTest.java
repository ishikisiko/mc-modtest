package com.example.myvillage.portrait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The Java replay draws exactly what the Python render drew for every golden spec. */
final class PortraitComposerTest {
    private static final String GOLDENS = "/portrait_goldens/";

    @Test
    void everyGoldenMatchesPixelForPixel() throws IOException {
        List<String> failures = new ArrayList<>();
        int cases = 0;
        for (JsonElement element : goldens().getAsJsonArray("cases")) {
            JsonObject golden = element.getAsJsonObject();
            String file = golden.get("file").getAsString();
            PortraitSpec spec = PortraitTestData.spec(golden.getAsJsonObject("spec"));
            int[] expected = png(file);
            int[] actual = PortraitComposer.compose(PortraitTestData.maps(), spec);
            int diff = 0;
            String first = null;
            for (int i = 0; i < expected.length; i++) {
                int e = opaqueOrZero(expected[i]);
                int a = opaqueOrZero(actual[i]);
                if (e != a) {
                    diff++;
                    if (first == null) {
                        first = String.format("(%d,%d) expected %08X got %08X", i % 64, i / 64, e, a);
                    }
                }
            }
            if (diff > 0) {
                failures.add(file + ": " + diff + " pixels differ, first " + first);
            }
            cases++;
        }
        assertTrue(cases >= 8, "golden cases " + cases);
        assertEquals(List.of(), failures);
    }

    @Test
    void theManifestMapsAllLoad() {
        assertEquals(172, PortraitTestData.maps().size());
    }

    /** Alpha 0 is transparent whatever RGB the PNG kept there. */
    private static int opaqueOrZero(int argb) {
        return (argb >>> 24) == 0 ? 0 : argb;
    }

    private static int[] png(String file) throws IOException {
        try (InputStream in = PortraitComposerTest.class.getResourceAsStream(GOLDENS + file)) {
            assertNotNull(in, file);
            BufferedImage image = ImageIO.read(in);
            assertEquals(64, image.getWidth(), file);
            assertEquals(64, image.getHeight(), file);
            return image.getRGB(0, 0, 64, 64, null, 0, 64);
        }
    }

    private static JsonObject goldens() throws IOException {
        try (InputStream in = PortraitComposerTest.class.getResourceAsStream(GOLDENS + "portrait_goldens.json")) {
            assertNotNull(in, "portrait_goldens.json");
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
