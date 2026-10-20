package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.meditation.MeditationState;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class RealmInkMotionTest {
    @Test
    void cosmeticSelectionUsesExactIdsAndNeverGuessesProgression() {
        assertEquals(RealmInkStyle.QI, RealmInkStyle.forRealm("myvillage:qi_refining"));
        assertEquals(RealmInkStyle.FOUNDATION, RealmInkStyle.forRealm("myvillage:foundation_establishment"));
        assertEquals(RealmInkStyle.CORE, RealmInkStyle.forRealm("myvillage:golden_core"));
        assertEquals(RealmInkStyle.SOUL, RealmInkStyle.forRealm("myvillage:nascent_soul"));
        assertEquals(RealmInkStyle.UNKNOWN, RealmInkStyle.forRealm("other:golden_core"));
        assertEquals(RealmInkStyle.UNKNOWN, RealmInkStyle.forRealm("myvillage:golden_core_new"));
        assertEquals(RealmInkStyle.UNKNOWN, RealmInkStyle.forRealm(null));
    }

    @Test
    void serverModeDistinguishesStudyConsolidationAndBreakthrough() {
        assertEquals(RealmInkMotion.Mode.IDLE, RealmInkMotion.mode(null, true, false));
        assertEquals(RealmInkMotion.Mode.IDLE, RealmInkMotion.mode(MeditationState.IDLE, true, false));
        assertEquals(RealmInkMotion.Mode.PREPARING, RealmInkMotion.mode(MeditationState.PREPARING_SPIRIT, true, false));
        assertEquals(RealmInkMotion.Mode.STUDY, RealmInkMotion.mode(MeditationState.MEDITATING_NORMAL, true, true));
        assertEquals(RealmInkMotion.Mode.CONSOLIDATING, RealmInkMotion.mode(MeditationState.MEDITATING_SPIRIT, true, false));
        assertEquals(RealmInkMotion.Mode.SPIRIT, RealmInkMotion.mode(MeditationState.MEDITATING_SPIRIT, false, false));
        assertEquals(RealmInkMotion.Mode.NORMAL, RealmInkMotion.mode(MeditationState.MEDITATING_NORMAL, false, false));
        assertEquals(RealmInkMotion.Mode.BOTTLENECK, RealmInkMotion.mode(MeditationState.ADVANCING_BOTTLENECK, true, false));
        assertEquals(RealmInkMotion.Mode.ADVANCING, RealmInkMotion.mode(MeditationState.ADVANCING_ORDINARY, true, false));
    }

    @Test
    void idleClockStopsAndResumeCannotCatchUpHiddenTime() {
        var clock = new RealmInkMotion.Clock();
        assertEquals(0, clock.sample(100, true));
        assertEquals(.04, clock.sample(140, true), 1e-9);
        assertEquals(.04, clock.sample(180, false), 1e-9);
        assertEquals(.04, clock.sample(50_000, false), 1e-9);
        assertEquals(.04, clock.sample(50_020, true), 1e-9);
        assertEquals(.06, clock.sample(50_040, true), 1e-9);
        assertEquals(.11, clock.sample(90_000, true), 1e-9);
        assertEquals(.11, clock.sample(89_000, true), 1e-9);
    }

    @Test
    void allFlowPathsStayFiniteInTheArtworkAndAreDistinct() {
        var signatures = new HashSet<RealmInkMotion.Point>();
        for (var style : new RealmInkStyle[] {RealmInkStyle.QI, RealmInkStyle.FOUNDATION, RealmInkStyle.CORE, RealmInkStyle.SOUL}) {
            signatures.add(RealmInkMotion.path(style, 0, .37, false));
            for (boolean solid : new boolean[] {false, true}) for (int i = 0; i < 3; i++) {
                for (int sample = 0; sample <= 200; sample++) {
                    var p = RealmInkMotion.path(style, i, sample / 200.0, solid);
                    assertTrue(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()));
                    assertTrue(p.x() >= 0 && p.x() <= 400 && p.y() >= 0 && p.y() <= 400, p.toString());
                }
            }
        }
        assertEquals(4, signatures.size());
    }

    @Test
    void closedCircuitsWrapButFoundationFeedsIntoThePool() {
        for (var style : new RealmInkStyle[] {RealmInkStyle.QI, RealmInkStyle.CORE, RealmInkStyle.SOUL}) {
            var a = RealmInkMotion.path(style, 0, 0, false);
            var b = RealmInkMotion.path(style, 0, 1, false);
            assertEquals(a.x(), b.x(), 1e-6);
            assertEquals(a.y(), b.y(), 1e-6);
        }
        for (int i = 0; i < 3; i++) {
            var end = RealmInkMotion.path(RealmInkStyle.FOUNDATION, i, 1, false);
            assertEquals(200, end.x());
            assertEquals(220, end.y());
        }
    }

    @Test
    void coreOrbitsCrossBothSidesOfTheFixedBitmap() {
        assertTrue(RealmInkMotion.path(RealmInkStyle.CORE, 0, .25, false).z() > 0);
        assertTrue(RealmInkMotion.path(RealmInkStyle.CORE, 0, .75, false).z() < 0);
        assertNotEquals(RealmInkMotion.path(RealmInkStyle.CORE, 0, .25, false),
                RealmInkMotion.path(RealmInkStyle.CORE, 0, .25, true));
    }

    @Test
    void approvedOriginalsArePackagedUnchangedWithRealTransparency() throws Exception {
        for (String name : new String[] {"qi", "foundation", "core", "soul"}) {
            Path source = Path.of("tools/cultivation_ink/source", name + ".png");
            Path packed = Path.of("src/main/resources/assets/myvillage/textures/gui/cultivation/ink", name + ".png");
            assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(packed));
            var image = ImageIO.read(packed.toFile());
            assertNotNull(image);
            assertTrue(image.getColorModel().hasAlpha());
            assertTrue(image.getWidth() >= 512 && image.getHeight() >= 512);
            boolean transparent = false, drawn = false;
            for (int y = 0; y < image.getHeight(); y += 11) for (int x = 0; x < image.getWidth(); x += 11) {
                int alpha = image.getRGB(x, y) >>> 24;
                transparent |= alpha == 0;
                drawn |= alpha > 100;
            }
            assertTrue(transparent && drawn, name);
        }
    }
}
