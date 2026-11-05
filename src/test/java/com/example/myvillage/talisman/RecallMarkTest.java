package com.example.myvillage.talisman;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecallMarkTest {
    private static TravelPoint point(String dimension, double x) {
        return new TravelPoint(ResourceLocation.parse(dimension), x, 80, -.25, 130, -15);
    }
    @Test void oneMarkIsReplacedAndSurvivesCodecRoundTripWithDimensionAndRotation() {
        var first = RecallMark.EMPTY.replace(point("minecraft:overworld", 1.25));
        var second = first.replace(point("minecraft:the_nether", 80.75));
        var json = RecallMark.CODEC.encodeStart(JsonOps.INSTANCE, second).getOrThrow();
        assertEquals(second, RecallMark.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals("minecraft:the_nether", second.point().orElseThrow().dimension().toString());
        assertEquals(1.25, first.point().orElseThrow().x());
        assertEquals(1, json.getAsJsonObject().size());
    }
    @Test void missingOrMalformedCoordinatesDoNotBecomeAValidOrigin() {
        var json = RecallMark.CODEC.encodeStart(JsonOps.INSTANCE, RecallMark.EMPTY.replace(point("minecraft:overworld", 1))).getOrThrow();
        json.getAsJsonObject().getAsJsonObject("point").remove("x");
        assertTrue(RecallMark.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
        assertFalse(point("minecraft:overworld", Double.NaN).valid());
        assertFalse(point("minecraft:overworld", Double.POSITIVE_INFINITY).valid());
        assertFalse(point("minecraft:overworld", 30_000_000).valid());
        assertThrows(IllegalArgumentException.class, () -> RecallMark.EMPTY.replace(point("minecraft:overworld", Double.NaN)));
    }
    @Test void playersWithNoMarkRemainUnmarkedAfterSerialization() {
        var json = RecallMark.CODEC.encodeStart(JsonOps.INSTANCE, RecallMark.EMPTY).getOrThrow();
        assertEquals(RecallMark.EMPTY, RecallMark.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }
}
