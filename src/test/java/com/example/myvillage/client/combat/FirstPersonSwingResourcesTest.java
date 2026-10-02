package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.definition.CombatTestData;
import com.example.myvillage.combat.definition.WeaponDefinition;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Rigs load per weapon: one weapon's bad rig or geometry never takes another's away. */
final class FirstPersonSwingResourcesTest {
    @Test
    void invalidRigOrGeometryAffectsOnlyItsOwnWeapon() {
        WeaponDefinition qingfeng = CombatTestData.qingfeng();
        WeaponDefinition brokenRig = new WeaponDefinition(
                id("broken_rig_sword"), qingfeng.style(), id("combat/broken_rig.json"), qingfeng.geometry());
        WeaponDefinition brokenGeometry = new WeaponDefinition(
                id("broken_geometry_sword"), qingfeng.style(), qingfeng.firstPersonRig(), id("combat/broken_geometry.json"));
        WeaponDefinition missing = new WeaponDefinition(
                id("missing_sword"), qingfeng.style(), id("combat/missing_rig.json"), qingfeng.geometry());
        CombatStyles styles = new CombatStyles(
                List.of(CombatTestData.basicSword()), List.of(brokenRig, qingfeng, brokenGeometry, missing));

        Map<ResourceLocation, FirstPersonSwingResources.WeaponRig> rigs =
                FirstPersonSwingResources.loadAll(styles, FirstPersonSwingResourcesTest::read);

        assertEquals(1, rigs.size(), rigs.keySet().toString());
        FirstPersonSwingResources.WeaponRig rig = rigs.get(qingfeng.item());
        assertEquals(qingfeng, rig.weapon());
        assertEquals(CombatTestData.basicSword(), rig.style());
        assertEquals(5, rig.swing().moves().size());
        assertTrue(rig.swing().weapon().headLengthPixels() > 0.0F);
    }

    private static Optional<JsonObject> read(WeaponDefinition weapon, ResourceLocation location) {
        String path = location.getPath();
        if (path.equals("combat/broken_rig.json")) {
            return Optional.of(JsonParser.parseString("{\"rig\": {}}").getAsJsonObject());
        }
        if (path.equals("combat/broken_geometry.json")) {
            return Optional.of(new JsonObject());
        }
        Path file = CombatTestData.assetPath(location);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }
}
