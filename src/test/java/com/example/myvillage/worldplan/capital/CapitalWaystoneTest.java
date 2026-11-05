package com.example.myvillage.worldplan.capital;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapitalWaystoneTest {
    private static JsonObject city() {
        return JsonParser.parseString("""
            {"waystones":[{"id":"donghai_01","name":"东海·双埠候船亭","position":[31,8,16],
            "facing":"south","block":"waystones:prismarine_waystone"}]}
            """).getAsJsonObject();
    }
    @Test void stationBelongsToExactlyItsGeneratingChunk() {
        var p=CapitalWaystone.read(city(),64,96,64).getFirst();
        assertTrue(p.inSlice(16,16));assertFalse(p.inSlice(32,16));assertFalse(p.inSlice(16,0));
        assertFalse(p.inSlice(0,16));assertFalse(p.inSlice(16,32));
    }
    @Test void duplicateIdsAndPositionsAreRejected() {
        var c=city();var copy=c.getAsJsonArray("waystones").get(0).deepCopy().getAsJsonObject();
        copy.addProperty("id","donghai_02");c.getAsJsonArray("waystones").add(copy);
        assertThrows(IllegalArgumentException.class,()->CapitalWaystone.read(c,64,96,64));
        copy.getAsJsonArray("position").set(0,new com.google.gson.JsonPrimitive(40));copy.addProperty("id","donghai_01");
        assertThrows(IllegalArgumentException.class,()->CapitalWaystone.read(c,64,96,64));
    }
    @Test void bothHalvesMustFitAndPositionsMustBeIntegral() {
        for(double x:new double[]{-.1,31.5,64}) {
            var c=city();c.getAsJsonArray("waystones").get(0).getAsJsonObject().getAsJsonArray("position").set(0,new com.google.gson.JsonPrimitive(x));
            assertThrows(IllegalArgumentException.class,()->CapitalWaystone.read(c,64,96,64));
        }
        var c=city();c.getAsJsonArray("waystones").get(0).getAsJsonObject().getAsJsonArray("position").set(1,new com.google.gson.JsonPrimitive(95));
        assertThrows(IllegalArgumentException.class,()->CapitalWaystone.read(c,64,96,64));
    }
    @Test void foreignBlocksAndUnknownFacingsAreRejected() {
        var foreign=city();foreign.getAsJsonArray("waystones").get(0).getAsJsonObject().addProperty("block","minecraft:air");
        assertThrows(IllegalArgumentException.class,()->CapitalWaystone.read(foreign,64,96,64));
        var invalid=city();invalid.getAsJsonArray("waystones").get(0).getAsJsonObject().addProperty("facing","up");
        assertThrows(IllegalArgumentException.class,()->CapitalWaystone.read(invalid,64,96,64));
    }
}
