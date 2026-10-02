package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.example.myvillage.combat.definition.CombatTestData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

final class WeaponGeometryTest {
    private static final Path GEOMETRY = CombatTestData.assetPath(CombatTestData.qingfeng().geometry());

    @Test
    void shippedContractStacksButtHandleCollarAndHead() throws IOException {
        WeaponGeometry sword = WeaponGeometry.parse(json());
        assertTrue(sword.buttTop() <= sword.handleBottom() + 1.0E-3F);
        assertTrue(sword.handleBottom() < sword.gripCenter().y && sword.gripCenter().y < sword.handleTop());
        assertTrue(sword.handleTop() <= sword.collarBottom() + 1.0E-3F);
        assertTrue(sword.collarTop() <= sword.headBase().y + 1.0E-3F);
        assertTrue(sword.headLengthPixels() > 8.0F);
        // The grip centre maps to the grip-frame origin and the tip straight up the weapon (+Y).
        assertEquals(0.0F, sword.toGrip(sword.gripCenter(), 0.6F).length(), 1.0E-6F);
        Vector3f tip = sword.toGrip(sword.headTip(), 0.6F);
        assertEquals(0.0F, tip.x, 1.0E-6F);
        assertEquals(0.0F, tip.z, 1.0E-6F);
        assertEquals((sword.headTip().y - sword.gripCenter().y) * 0.6F / 16.0F, tip.y, 1.0E-6F);
    }

    @Test
    void invalidContractsAreRejected() throws IOException {
        JsonObject units = json();
        units.addProperty("units", "blocks");
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(units));

        JsonObject axes = json();
        axes.getAsJsonObject("axes").addProperty("length", "+z");
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(axes));

        JsonObject offHandle = json();
        offHandle.add("grip_center", vector(8.0F, 12.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(offHandle));

        JsonObject inverted = json();
        inverted.add("head_tip", vector(8.0F, 2.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(inverted));

        JsonObject crooked = json();
        crooked.add("head_tip", vector(12.0F, 24.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(crooked));

        JsonObject missingCollar = json();
        missingCollar.remove("collar");
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(missingCollar));

        JsonObject flatHandle = json();
        flatHandle.getAsJsonObject("handle").addProperty("half_width", 0.0F);
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(flatHandle));
    }

    @Test
    void formatOneContractsFailNamingTheNewField() throws IOException {
        JsonObject missing = json();
        missing.remove("format");
        assertTrue(message(missing).contains("format must be 2"), message(missing));
        JsonObject formatOne = json();
        formatOne.addProperty("format", 1);
        assertTrue(message(formatOne).contains("format must be 2, got 1"), message(formatOne));
        assertTrue(message(formatOne).contains("pommel to butt"), message(formatOne));

        // Each format 1 name is rejected on its own, even beside its format 2 replacement, so an
        // old field can never be read in place of (or silently next to) the new one.
        String[][] renamed = {
                {"guard", "collar"}, {"pommel", "butt"}, {"blade", "head"},
                {"blade_base", "head_base"}, {"blade_tip", "head_tip"}};
        for (String[] names : renamed) {
            JsonObject old = json();
            old.add(names[0], old.get(names[1]).deepCopy());
            assertEquals("Weapon geometry field " + names[0] + " was renamed " + names[1] + " in format 2",
                    message(old));
            JsonObject moved = json();
            moved.add(names[0], moved.remove(names[1]));
            assertEquals("Weapon geometry field " + names[0] + " was renamed " + names[1] + " in format 2",
                    message(moved));
        }
        JsonObject oldAxis = json();
        JsonObject axes = oldAxis.getAsJsonObject("axes");
        axes.add("blade", axes.remove("length"));
        assertEquals("Weapon geometry field axes.blade was renamed axes.length in format 2", message(oldAxis));
    }

    private static String message(JsonObject json) {
        return assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(json)).getMessage();
    }

    @Test
    void offHandGripIsOptionalAndCheckedLikeTheGrip() throws IOException {
        assertTrue(WeaponGeometry.parse(json()).offHandGripCenter().isEmpty(), "the one-handed sword has no off hand");
        WeaponGeometry spear = WeaponGeometry.parse(spear());
        assertEquals(new Vector3f(8.0F, 11.0F, 8.0F), spear.offHandGripCenter().orElseThrow());
        assertTrue(spear.offHandGripCenter().orElseThrow().y > spear.gripCenter().y);

        JsonObject offAxis = spear();
        offAxis.add("off_hand_grip_center", vector(8.5F, 11.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(offAxis));
        JsonObject offHandle = spear();
        offHandle.add("off_hand_grip_center", vector(8.0F, 18.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(offHandle));
        JsonObject behind = spear();
        behind.add("off_hand_grip_center", vector(8.0F, -6.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(behind));
        JsonObject shortPoint = spear();
        shortPoint.add("off_hand_grip_center", new JsonArray());
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(shortPoint));
    }

    @Test
    void trailSpanDefaultsToTheHeadAndIsCheckedLikeTheHead() throws IOException {
        WeaponGeometry sword = WeaponGeometry.parse(json());
        assertTrue(!json().has("trail"), "the sword trails along its head (the blade)");
        assertEquals(sword.headBase(), sword.trailBase());
        assertEquals(sword.headTip(), sword.trailTip());
        assertEquals(sword.headLengthPixels(), sword.trailLengthPixels(), 1.0E-6F);
        assertEquals(sword.headTip().y - sword.gripCenter().y, sword.gripToTrailTipPixels(), 1.0E-6F);

        // The spear's head is short; its trail span adds the front of the shaft, about a sword blade.
        WeaponGeometry spear = WeaponGeometry.parse(spear());
        assertEquals(new Vector3f(8.0F, 16.0F, 8.0F), spear.trailBase());
        assertEquals(spear.headTip(), spear.trailTip());
        assertTrue(spear.trailBase().y < spear.headBase().y);
        assertTrue(spear.headLengthPixels() < 0.6F * sword.headLengthPixels());
        assertEquals(sword.trailLengthPixels(), spear.trailLengthPixels(), 0.5F);

        JsonObject offAxis = spear();
        offAxis.add("trail", trail(vector(8.0F, 16.0F, 8.5F), vector(8.0F, 32.0F, 8.0F)));
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(offAxis));
        JsonObject inverted = spear();
        inverted.add("trail", trail(vector(8.0F, 32.0F, 8.0F), vector(8.0F, 16.0F, 8.0F)));
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(inverted));
        JsonObject pastTip = spear();
        pastTip.add("trail", trail(vector(8.0F, 16.0F, 8.0F), vector(8.0F, 33.0F, 8.0F)));
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(pastTip));
        JsonObject belowButt = spear();
        belowButt.add("trail", trail(vector(8.0F, -17.0F, 8.0F), vector(8.0F, 32.0F, 8.0F)));
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(belowButt));
        JsonObject missingTip = spear();
        missingTip.getAsJsonObject("trail").remove("tip");
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(missingTip));
        JsonObject notObject = spear();
        notObject.add("trail", vector(8.0F, 16.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> WeaponGeometry.parse(notObject));
        // The whole weapon is a valid span.
        JsonObject whole = spear();
        whole.add("trail", trail(vector(8.0F, -16.0F, 8.0F), vector(8.0F, 32.0F, 8.0F)));
        assertEquals(48.0F, WeaponGeometry.parse(whole).trailLengthPixels(), 1.0E-6F);
    }

    private static JsonObject trail(JsonArray base, JsonArray tip) {
        JsonObject trail = new JsonObject();
        trail.add("base", base);
        trail.add("tip", tip);
        return trail;
    }

    private static JsonObject json() throws IOException {
        return JsonParser.parseString(Files.readString(GEOMETRY)).getAsJsonObject();
    }

    private static JsonObject spear() throws IOException {
        return JsonParser.parseString(Files.readString(
                CombatTestData.assetPath(CombatTestData.lingxiao().geometry()))).getAsJsonObject();
    }

    private static JsonArray vector(float x, float y, float z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }
}
