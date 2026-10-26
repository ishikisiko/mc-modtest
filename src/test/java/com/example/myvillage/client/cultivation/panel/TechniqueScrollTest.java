package com.example.myvillage.client.cultivation.panel;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TechniqueScrollTest {
    private static final String BASIC = "myvillage:basic_breathing";

    @Test void openingAnExistingArchiveDoesNotInventAGainOrInsight() {
        var t = new TechniqueScrollMotion.Tracker();
        t.observe(100, BASIC, 180, 4, "steady", 9, false, false);
        assertEquals(0, t.gained()); assertTrue(t.stageAge() > 2); assertTrue(t.insightAge() > 2);
        t.observe(150, BASIC, 180, 4, "steady", 9, false, false);
        assertEquals(0, t.gained()); assertEquals(0, t.flow());
    }

    @Test void acceptedMasteryAndChoiceAreTheOnlySuccessTriggers() {
        var t = new TechniqueScrollMotion.Tracker();
        t.observe(0, BASIC, 46, 1, "", 0, true, false);
        for (int i = 1; i <= 500; i++) t.observe(i * 20L, BASIC, 46, 1, "", 0, true, false);
        assertEquals(0, t.gained()); assertTrue(t.stageAge() > 2); assertTrue(t.flow() >= 9.9);
        t.observe(10020, BASIC, 56, 2, "", 0, true, false);
        assertEquals(10, t.gained()); assertEquals(0, t.stageAge()); assertEquals(0, t.flow());
        t.observe(10040, BASIC, 56, 2, "", 0, false, false);
        assertTrue(t.stageAge() > 0);
        t.observe(10060, BASIC, 56, 2, "steady", 1, false, false);
        assertEquals(0, t.insightAge());
        t.observe(10080, BASIC, 56, 2, "steady", 1, false, false);
        assertTrue(t.insightAge() > 0);
    }

    @Test void cancellationAndPreparationNeverLookLikeCompletedPractice() {
        var t = new TechniqueScrollMotion.Tracker();
        t.observe(0, BASIC, 46, 1, "", 0, true, true);
        t.observe(50, BASIC, 46, 1, "", 0, true, true);
        assertEquals(0, t.flow()); assertTrue(t.preparing());
        t.observe(100, BASIC, 46, 1, "", 0, true, false);
        t.observe(150, BASIC, 46, 1, "", 0, true, false);
        assertEquals(.05, t.flow(), 1e-9);
        t.observe(200, BASIC, 46, 1, "", 0, false, false);
        assertFalse(t.running()); assertEquals(0, t.flow()); assertEquals(0, t.gained());
    }

    @Test void hiddenTimeAndTechniqueSwitchesDoNotReplayOldEvents() {
        var t = new TechniqueScrollMotion.Tracker();
        t.observe(100, BASIC, 46, 1, "", 0, true, false);
        t.observe(150, BASIC, 56, 2, "", 0, true, false);
        t.hidden();
        t.observe(50000, BASIC, 100, 3, "steady", 1, true, false);
        assertEquals(0, t.flow()); assertEquals(0, t.gained()); assertTrue(t.stageAge() > 2);
        t.observe(90000, BASIC, 100, 3, "steady", 1, true, false);
        assertEquals(.05, t.flow(), 1e-9);
        t.observe(90020, "myvillage:qingmu_changchun_gong", 360, 4, "flow", 8, false, false);
        assertEquals(0, t.gained()); assertTrue(t.insightAge() > 2);
    }

    @Test void ReplacedOrStaleSnapshotDoesNotEmitSuccess() {
        var t = new TechniqueScrollMotion.Tracker();
        t.observe(0, BASIC, 100, 3, "steady", 4, false, false);
        t.observe(20, BASIC, 90, 2, "flow", 3, false, false);
        assertEquals(0, t.gained()); assertTrue(t.gainAge() > 2); assertTrue(t.insightAge() > 2);
    }

    @Test void exactMappingsAndAllMotionStayInsideTheirArtwork() {
        assertEquals(TechniqueScrollStyle.BREATHING, TechniqueScrollStyle.forTechnique(BASIC));
        assertEquals(TechniqueScrollStyle.UNKNOWN, TechniqueScrollStyle.forTechnique("other:basic_breathing"));
        assertEquals(TechniqueScrollStyle.UNKNOWN, TechniqueScrollStyle.forTechnique("myvillage:basic_breathing_extra"));
        assertEquals(TechniqueScrollStyle.UNKNOWN, TechniqueScrollStyle.forTechnique(null));
        for (var style : TechniqueScrollStyle.values()) for (int side = 0; side < 2; side++) {
            for (int i = 0; i <= 200; i++) {
                var p = TechniqueScrollMotion.path(style, side, i / 200.0);
                assertTrue(Double.isFinite(p.x()) && Double.isFinite(p.y()));
                assertTrue(p.x() >= 0 && p.x() <= 1536 && p.y() >= 0 && p.y() <= 1024);
                p = TechniqueScrollMotion.incoming(style, side, i / 200.0);
                assertTrue(p.x() >= 0 && p.x() <= 1536 && p.y() >= 0 && p.y() <= 1024);
            }
            assertEquals(TechniqueScrollMotion.anchor(style), TechniqueScrollMotion.incoming(style, side, 1));
        }
    }

    @Test void realHViewportKeepsEveryActionInsideAndSeparateFromProgress() {
        for (int[] size : new int[][] {{180,120},{235,168},{397,190},{398,208}}) {
            var l = TechniqueScrollLayout.of(size[0], size[1]);
            for (var r : new TechniqueScrollLayout.Rect[] {l.art(),l.practice(),l.firstChoice(),l.secondChoice()}) {
                assertTrue(r.x() >= 0 && r.y() >= 0 && r.width() > 0 && r.height() > 0);
                assertTrue(r.right() <= l.width() && r.bottom() <= l.height(), r.toString());
            }
            assertTrue(l.firstChoice().right() < l.secondChoice().x());
            assertTrue(l.firstChoice().bottom() < l.practice().y());
            assertTrue(l.practice().bottom() + 8 < l.barY());
            assertTrue(l.stagesY() >= l.barY() + 3 && l.stagesY() + 9 <= l.height());
            assertEquals(1.5, l.art().width() / (double) l.art().height(), .01);
        }
    }

    @Test void runtimePaintingsFollowAuthoredDimensionsAndStayWithinJarBudget() throws Exception {
        var cfg = JsonParser.parseString(Files.readString(Path.of("tools/technique_scroll/style.json"))).getAsJsonObject();
        int width = cfg.getAsJsonArray("size").get(0).getAsInt(), height = cfg.getAsJsonArray("size").get(1).getAsInt();
        long total = 0;
        var base = Path.of("src/main/resources/assets/myvillage/textures/gui/cultivation/technique_scroll");
        for (var entry : cfg.getAsJsonObject("techniques").entrySet()) {
            var style = TechniqueScrollStyle.forTechnique(entry.getKey());
            assertNotEquals(TechniqueScrollStyle.UNKNOWN, style);
            assertEquals(entry.getValue().getAsString(), style.texture());
            Path file = base.resolve(style.texture() + ".png");
            var image = ImageIO.read(file.toFile()); assertNotNull(image);
            assertEquals(width, image.getWidth()); assertEquals(height, image.getHeight());
            assertTrue(Files.size(file) <= cfg.get("max_texture_bytes").getAsLong());
            total += Files.size(file) + Files.size(base.resolve(style.texture() + ".png.mcmeta"));
        }
        assertTrue(total <= cfg.get("max_total_bytes").getAsLong());
    }
}
