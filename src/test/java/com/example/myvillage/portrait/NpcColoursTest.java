package com.example.myvillage.portrait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The synced int of an avatar's colours: every pair round-trips, nothing else decodes. */
final class NpcColoursTest {
    @Test
    void everyHairAndEyePairRoundTrips() {
        Set<Integer> packed = new HashSet<>();
        for (PortraitSpec.HairColour hair : PortraitSpec.HairColour.values()) {
            for (PortraitSpec.EyeColour eye : PortraitSpec.EyeColour.values()) {
                NpcColours colours = new NpcColours(hair, eye);
                int p = colours.pack();
                assertNotEquals(NpcColours.NONE, p, colours.toString());
                assertEquals(hair.ordinal() << 4 | eye.ordinal(), p, colours.toString());
                assertEquals(colours, NpcColours.unpack(p), colours.toString());
                packed.add(p);
            }
        }
        assertEquals(PortraitSpec.HairColour.values().length * PortraitSpec.EyeColour.values().length, packed.size());
    }

    @Test
    void noneAndOutOfRangeValuesAreNull() {
        assertNull(NpcColours.unpack(NpcColours.NONE));
        assertNull(NpcColours.unpack(-2));
        assertNull(NpcColours.unpack(Integer.MIN_VALUE));
        int hairs = PortraitSpec.HairColour.values().length;
        int eyes = PortraitSpec.EyeColour.values().length;
        assertNull(NpcColours.unpack(hairs << 4), "hair past the last colour");
        assertNull(NpcColours.unpack(eyes), "eye past the last colour");
        assertNull(NpcColours.unpack(0xF), "eye nibble 15");
        assertNull(NpcColours.unpack(Integer.MAX_VALUE));
    }

    @Test
    void ofTakesTheSpecsHairAndEyeColours() {
        JsonObject o = new JsonObject();
        o.addProperty("female", true);
        o.addProperty("face", "oval");
        o.addProperty("skin", "pale");
        o.addProperty("hairColour", "grey_chestnut");
        o.addProperty("front", "hime");
        o.addProperty("back", "long");
        o.addProperty("eyeShape", "round");
        o.addProperty("eyeColour", "fire");
        o.addProperty("brow", "arched");
        o.addProperty("mouth", "smile");
        o.addProperty("nose", true);
        o.addProperty("blush", true);
        o.addProperty("robe", "plain");
        o.addProperty("accent", 2);
        o.addProperty("headwear", "none");
        o.addProperty("bandage", false);
        o.addProperty("scar", false);
        o.addProperty("old", false);
        o.addProperty("dead", false);
        PortraitSpec spec = PortraitTestData.spec(o);
        assertEquals(new NpcColours(PortraitSpec.HairColour.GREY_CHESTNUT, PortraitSpec.EyeColour.FIRE),
                NpcColours.of(spec));
    }

    @Test
    void bothColoursAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new NpcColours(null, PortraitSpec.EyeColour.METAL));
        assertThrows(IllegalArgumentException.class, () -> new NpcColours(PortraitSpec.HairColour.SILVER, null));
    }
}
