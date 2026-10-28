package com.example.myvillage.worldplan.atlas;

import com.example.myvillage.worldplan.travel.AtlasCityTravelRules;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasCityInteractionTest {
    private final WorldAtlas.City city=new WorldAtlas.City("capital_donghai","沧海城","donghai",1200,64,-900);
    private final AtlasCityTravelRules rules=AtlasCityTravelRules.bundled();

    @Test void markerHitTracksRoundedRenderCoordinatesAfterZoomPanAndLocate() {
        var view=new AtlasViewport();
        for (int size : new int[]{211,493,880}) {
            var area=new AtlasCityInteraction.MapArea(13,27,size);
            view.reset(); view.scale(2,.43,.62); view.pan(-.08,.06);
            assertHit(view,area);
            view.locate(city.x(),city.z(),10000);
            assertHit(view,area);
            assertTrue(AtlasCityInteraction.hit(List.of(city),view,10000,area,-1,-1,rules.markerHitRadiusPixels()).isEmpty());
        }
    }
    private void assertHit(AtlasViewport view, AtlasCityInteraction.MapArea area) {
        var pixel=AtlasCityInteraction.marker(area,view,city,10000);
        assertEquals(city,AtlasCityInteraction.hit(List.of(city),view,10000,area,pixel.x(),pixel.y(),rules.markerHitRadiusPixels()).orElseThrow());
    }
    @Test void releaseRequiresTheSamePressedMarker() {
        var gesture=new AtlasCityInteraction(rules.clickSlopPixels());
        gesture.press(100,100,city.id());
        assertEquals(city.id(),gesture.release(101,100,city.id()).orElseThrow());
        gesture.press(100,100,null);assertTrue(gesture.release(100,100,city.id()).isEmpty());
        gesture.press(100,100,city.id());assertTrue(gesture.release(100,100,"capital_ximo").isEmpty());
    }
    @Test void dragOutAndBackAndReleaseWithoutDragEventCannotTriggerTravel() {
        var gesture=new AtlasCityInteraction(rules.clickSlopPixels());
        gesture.press(100,100,city.id());
        assertEquals(20,gesture.drag(120,100).x());
        gesture.drag(100,100);
        assertTrue(gesture.release(100,100,city.id()).isEmpty());
        gesture.press(100,100,city.id());
        assertTrue(gesture.release(120,100,city.id()).isEmpty());
    }
    @Test void scrollingResizingAndSubThresholdJitterHaveDistinctClickOutcomes() {
        var gesture=new AtlasCityInteraction(rules.clickSlopPixels());
        gesture.press(100,100,city.id());gesture.suppressClick();
        assertTrue(gesture.release(100,100,city.id()).isEmpty());
        gesture.press(100,100,city.id());gesture.cancel();
        assertTrue(gesture.release(100,100,city.id()).isEmpty());
        gesture.press(100,100,city.id());
        assertEquals(0,gesture.drag(101,100).x());
        assertEquals(city.id(),gesture.release(101,100,city.id()).orElseThrow());
    }
    @Test void legacyEmptyOverlayHasNoClickableMarker() {
        assertTrue(AtlasCityInteraction.hit(List.of(),new AtlasViewport(),10000,
                new AtlasCityInteraction.MapArea(0,0,400),200,200,rules.markerHitRadiusPixels()).isEmpty());
    }
}
