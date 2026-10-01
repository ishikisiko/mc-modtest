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

    private static JsonObject json() throws IOException {
        return JsonParser.parseString(Files.readString(GEOMETRY)).getAsJsonObject();
    }

    private static JsonArray vector(float x, float y, float z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }
}
