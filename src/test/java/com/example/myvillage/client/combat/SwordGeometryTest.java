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

final class SwordGeometryTest {
    private static final Path GEOMETRY = CombatTestData.assetPath(CombatTestData.qingfeng().geometry());

    @Test
    void shippedContractStacksPommelHandleGuardAndBlade() throws IOException {
        SwordGeometry sword = SwordGeometry.parse(json());
        assertTrue(sword.pommelTop() <= sword.handleBottom() + 1.0E-3F);
        assertTrue(sword.handleBottom() < sword.gripCenter().y && sword.gripCenter().y < sword.handleTop());
        assertTrue(sword.handleTop() <= sword.guardBottom() + 1.0E-3F);
        assertTrue(sword.guardTop() <= sword.bladeBase().y + 1.0E-3F);
        assertTrue(sword.bladeLengthPixels() > 8.0F);
        // The grip centre maps to the grip-frame origin and the tip straight up the blade (+Y).
        assertEquals(0.0F, sword.toGrip(sword.gripCenter(), 0.6F).length(), 1.0E-6F);
        Vector3f tip = sword.toGrip(sword.bladeTip(), 0.6F);
        assertEquals(0.0F, tip.x, 1.0E-6F);
        assertEquals(0.0F, tip.z, 1.0E-6F);
        assertEquals((sword.bladeTip().y - sword.gripCenter().y) * 0.6F / 16.0F, tip.y, 1.0E-6F);
    }

    @Test
    void invalidContractsAreRejected() throws IOException {
        JsonObject units = json();
        units.addProperty("units", "blocks");
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(units));

        JsonObject axes = json();
        axes.getAsJsonObject("axes").addProperty("blade", "+z");
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(axes));

        JsonObject offHandle = json();
        offHandle.add("grip_center", vector(8.0F, 12.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(offHandle));

        JsonObject inverted = json();
        inverted.add("blade_tip", vector(8.0F, 2.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(inverted));

        JsonObject crooked = json();
        crooked.add("blade_tip", vector(12.0F, 24.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(crooked));

        JsonObject missingGuard = json();
        missingGuard.remove("guard");
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(missingGuard));

        JsonObject flatHandle = json();
        flatHandle.getAsJsonObject("handle").addProperty("half_width", 0.0F);
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(flatHandle));
    }

    @Test
    void offHandGripIsOptionalAndCheckedLikeTheGrip() throws IOException {
        assertTrue(SwordGeometry.parse(json()).offHandGripCenter().isEmpty(), "the one-handed sword has no off hand");
        SwordGeometry spear = SwordGeometry.parse(spear());
        assertEquals(new Vector3f(8.0F, 11.0F, 8.0F), spear.offHandGripCenter().orElseThrow());
        assertTrue(spear.offHandGripCenter().orElseThrow().y > spear.gripCenter().y);

        JsonObject offAxis = spear();
        offAxis.add("off_hand_grip_center", vector(8.5F, 11.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(offAxis));
        JsonObject offHandle = spear();
        offHandle.add("off_hand_grip_center", vector(8.0F, 18.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(offHandle));
        JsonObject behind = spear();
        behind.add("off_hand_grip_center", vector(8.0F, -6.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(behind));
        JsonObject shortPoint = spear();
        shortPoint.add("off_hand_grip_center", new JsonArray());
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(shortPoint));
    }

    @Test
    void trailSpanDefaultsToTheBladeAndIsCheckedLikeTheBlade() throws IOException {
        SwordGeometry sword = SwordGeometry.parse(json());
        assertTrue(!json().has("trail"), "the sword trails along its blade");
        assertEquals(sword.bladeBase(), sword.trailBase());
        assertEquals(sword.bladeTip(), sword.trailTip());
        assertEquals(sword.bladeLengthPixels(), sword.trailLengthPixels(), 1.0E-6F);
        assertEquals(sword.bladeTip().y - sword.gripCenter().y, sword.gripToTrailTipPixels(), 1.0E-6F);

        // The spear's head is short; its trail span adds the front of the shaft, about a sword blade.
        SwordGeometry spear = SwordGeometry.parse(spear());
        assertEquals(new Vector3f(8.0F, 16.0F, 8.0F), spear.trailBase());
        assertEquals(spear.bladeTip(), spear.trailTip());
        assertTrue(spear.trailBase().y < spear.bladeBase().y);
        assertTrue(spear.bladeLengthPixels() < 0.6F * sword.bladeLengthPixels());
        assertEquals(sword.trailLengthPixels(), spear.trailLengthPixels(), 0.5F);

        JsonObject offAxis = spear();
        offAxis.add("trail", trail(vector(8.0F, 16.0F, 8.5F), vector(8.0F, 32.0F, 8.0F)));
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(offAxis));
        JsonObject inverted = spear();
        inverted.add("trail", trail(vector(8.0F, 32.0F, 8.0F), vector(8.0F, 16.0F, 8.0F)));
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(inverted));
        JsonObject pastTip = spear();
        pastTip.add("trail", trail(vector(8.0F, 16.0F, 8.0F), vector(8.0F, 33.0F, 8.0F)));
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(pastTip));
        JsonObject belowButt = spear();
        belowButt.add("trail", trail(vector(8.0F, -17.0F, 8.0F), vector(8.0F, 32.0F, 8.0F)));
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(belowButt));
        JsonObject missingTip = spear();
        missingTip.getAsJsonObject("trail").remove("tip");
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(missingTip));
        JsonObject notObject = spear();
        notObject.add("trail", vector(8.0F, 16.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> SwordGeometry.parse(notObject));
        // The whole weapon is a valid span.
        JsonObject whole = spear();
        whole.add("trail", trail(vector(8.0F, -16.0F, 8.0F), vector(8.0F, 32.0F, 8.0F)));
        assertEquals(48.0F, SwordGeometry.parse(whole).trailLengthPixels(), 1.0E-6F);
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
