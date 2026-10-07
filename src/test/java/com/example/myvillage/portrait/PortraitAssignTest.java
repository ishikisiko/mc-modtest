package com.example.myvillage.portrait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.PersonView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * The Java port picks the same parts as {@code tools/portraitgen/person.py} for every parity vector,
 * and a spec survives the wire.
 */
class PortraitAssignTest {
    private static JsonArray cases() throws Exception {
        try (InputStream in = PortraitAssignTest.class.getResourceAsStream("/portrait_assign_vectors.json")) {
            assertNotNull(in, "portrait_assign_vectors.json on the test classpath");
            JsonObject doc = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return doc.getAsJsonArray("cases");
        }
    }

    private static int[] ints(JsonArray a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsInt();
        }
        return out;
    }

    @Test
    void everyVectorMatchesPython() throws Exception {
        JsonArray cases = cases();
        assertEquals(240, cases.size());
        List<String> mismatches = new ArrayList<>();
        for (JsonElement e : cases) {
            JsonObject c = e.getAsJsonObject();
            int id = c.get("id").getAsInt();
            PortraitSpec s = PortraitAssign.of(id, "f".equals(c.get("gender").getAsString()),
                    c.get("realm").getAsString(), c.get("rank").getAsString(), c.get("sect_id").getAsInt(),
                    c.get("age_years").getAsDouble(), ints(c.getAsJsonArray("root")),
                    ints(c.getAsJsonArray("traits")), c.get("injury").getAsInt(), c.get("alive").getAsBoolean());
            JsonObject want = c.getAsJsonObject("choice");
            List<String> marks = new ArrayList<>();
            for (JsonElement m : want.getAsJsonArray("marks")) {
                marks.add(m.getAsString());
            }
            check(mismatches, id, "face", want.get("face").getAsString(), PortraitSpec.key(s.face()));
            check(mismatches, id, "skin", want.get("skin").getAsString(), PortraitSpec.key(s.skin()));
            check(mismatches, id, "hair_colour", want.get("hair_colour").getAsString(), PortraitSpec.key(s.hairColour()));
            check(mismatches, id, "front", want.get("front").getAsString(), PortraitSpec.key(s.front()));
            check(mismatches, id, "back", want.get("back").getAsString(), PortraitSpec.key(s.back()));
            check(mismatches, id, "eye_shape", want.get("eye_shape").getAsString(), PortraitSpec.key(s.eyeShape()));
            check(mismatches, id, "eye_colour", want.get("eye_colour").getAsString(), PortraitSpec.key(s.eyeColour()));
            check(mismatches, id, "brow", want.get("brow").getAsString(), PortraitSpec.key(s.brow()));
            check(mismatches, id, "mouth", want.get("mouth").getAsString(), PortraitSpec.key(s.mouth()));
            check(mismatches, id, "nose", want.get("nose").getAsBoolean(), s.nose());
            check(mismatches, id, "blush", want.get("blush").getAsBoolean(), s.blush());
            check(mismatches, id, "robe", want.get("robe").getAsString(), s.robeKey());
            check(mismatches, id, "accent", want.get("accent").getAsInt(), s.accent());
            check(mismatches, id, "headwear", want.get("headwear").getAsString(), PortraitSpec.key(s.headwear()));
            check(mismatches, id, "bandage", marks.contains("bandage"), s.bandage());
            check(mismatches, id, "scar", marks.contains("scar"), s.scar());
            check(mismatches, id, "old", want.get("old").getAsBoolean(), s.old());
            check(mismatches, id, "dead", want.get("dead").getAsBoolean(), s.dead());
            check(mismatches, id, "female", "f".equals(c.get("gender").getAsString()), s.female());
        }
        assertTrue(mismatches.isEmpty(), mismatches.size() + " mismatches, first: "
                + mismatches.subList(0, Math.min(10, mismatches.size())));
    }

    private static void check(List<String> out, int id, String field, Object want, Object got) {
        if (!want.equals(got)) {
            out.add("id " + id + " " + field + ": python " + want + ", java " + got);
        }
    }

    @Test
    void mixIsSplitmix64AsInPython() {
        // python3 -c "import sys; sys.path.insert(0, 'tools/portraitgen'); from person import mix; print(mix(1, 1))"
        assertEquals(0L, PortraitAssign.mix(0, 0));
        assertEquals(7258340960826406041L, PortraitAssign.mix(1, 1));
        assertEquals(4872776713211004634L, PortraitAssign.mix(4711, 7));
        assertEquals(297844463344220369L, PortraitAssign.mix(-5, 13), "negative seeds wrap like Python's mask");
    }

    private static PersonView view(boolean alive, long birthDay, long deathDay, List<Integer> root,
                                   List<Integer> traits) {
        return new PersonView(77, "林七", "", "f", alive, birthDay, deathDay, alive ? "" : "old_age", -1, root,
                "mixed", "golden_core", 0, 0.0, 3, "青云宗", "inner", -1, "r", alive ? "at_sect" : "dead", "", "",
                alive ? 30 : 0, traits, List.of());
    }

    @Test
    void aViewCountsItsAgeToTodayOrToItsDeath() {
        int dpy = 24;
        List<Integer> root = List.of(1000, 1000, 6000, 1000, 1000);
        List<Integer> traits = List.of(10, 80, 10, 10, 10);
        // golden_core lives 500 years: 450 years old is old, 300 is greying
        PortraitSpec living = PortraitAssign.of(view(true, 0, -1, root, traits), 450L * dpy, dpy);
        assertEquals(PortraitAssign.of(77, true, "golden_core", "inner", 3, 450.0, new int[] {1000, 1000, 6000, 1000,
                1000}, new int[] {10, 80, 10, 10, 10}, 30, true), living);
        assertTrue(living.old());
        assertEquals(PortraitSpec.EyeColour.WATER, living.eyeColour());
        assertEquals(PortraitSpec.Brow.ANGRY, living.brow());
        assertTrue(living.bandage() && living.scar(), "aggressive and hurt: bandage and scar");

        PortraitSpec dead = PortraitAssign.of(view(false, 0, 300L * dpy, List.of(), List.of()), 900L * dpy, dpy);
        assertTrue(dead.dead());
        assertFalse(dead.old(), "the dead keep the age they died at");
        assertTrue(dead.hairColour().name().startsWith("GREY_"));
        assertEquals(PortraitSpec.EyeColour.METAL, dead.eyeColour(), "no root on record: all equal, metal first");
        assertFalse(dead.bandage());
        assertEquals(PortraitAssign.of(77, true, "golden_core", "inner", 3, 300.0, null, null, 0, false), dead);
        assertThrows(IllegalArgumentException.class, () -> PortraitAssign.of(view(true, 0, -1, root, traits), 1, 0));
    }

    @Test
    void anUnknownRealmOrRankFallsBackQuietly() {
        PortraitSpec s = PortraitAssign.of(5, false, "", "", -1, 20.0, null, null, 0, true);
        assertEquals(PortraitSpec.Robe.PLAIN, s.robe());
        assertEquals(PortraitSpec.Headwear.NONE, s.headwear());
        assertEquals(PortraitAssign.ROGUE_ACCENT, s.accent());
    }

    @Test
    void everyAssignedSpecRoundTripsTheWire() throws Exception {
        for (JsonElement e : cases()) {
            JsonObject c = e.getAsJsonObject();
            PortraitSpec spec = PortraitAssign.of(c.get("id").getAsInt(), "f".equals(c.get("gender").getAsString()),
                    c.get("realm").getAsString(), c.get("rank").getAsString(), c.get("sect_id").getAsInt(),
                    c.get("age_years").getAsDouble(), ints(c.getAsJsonArray("root")),
                    ints(c.getAsJsonArray("traits")), c.get("injury").getAsInt(), c.get("alive").getAsBoolean());
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                spec.write(buf);
                // a flag byte, eleven enum ordinals, the accent
                assertEquals(13, buf.readableBytes());
                assertEquals(spec, PortraitSpec.read(buf));
                assertEquals(0, buf.readableBytes());
            } finally {
                buf.release();
            }
        }
    }

    @Test
    void aBadOrdinalOrAccentIsRejectedOnRead() {
        for (int at : new int[] {1, 12}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                for (int i = 0; i < 13; i++) {
                    buf.writeByte(i == at ? 40 : 0);
                }
                assertThrows(DecoderException.class, () -> PortraitSpec.read(buf), "byte " + at);
            } finally {
                buf.release();
            }
        }
    }
}
