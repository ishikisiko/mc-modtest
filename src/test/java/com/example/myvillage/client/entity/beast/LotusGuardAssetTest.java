package com.example.myvillage.client.entity.beast;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.entity.beast.BeastDefinitions;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

final class LotusGuardAssetTest {
    private static Reader read(String name) {
        var stream = LotusGuardAssetTest.class.getResourceAsStream("/assets/yuanxian/beast/lotus_guard_" + name + ".json");
        assertNotNull(stream, name);
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    @Test
    void exportedGameMeshKeepsTheApprovedSizeAndBothSidesLandOnTheirHitFrame() throws Exception {
        var id = ResourceLocation.parse("yuanxian:lotus_guard");
        try (var source = read("model"); var assets = read("animations"); var triangles = read("surface")) {
            var model = BeastModelFile.parse("lotus_guard_model", id, source);
            var animation = BeastAnimationFile.parse("lotus_guard_animations", id, assets);
            var surface = BeastSurfaceMesh.parse("lotus_guard_surface", model, triangles);
            var def = BeastDefinitions.bundled().require(id);
            animation.check("lotus_guard_animations", model, def);
            assertTrue(BeastGait.plantedFootSpeed(model, animation.clip("walk").orElseThrow()) > 0);
            assertTrue(BeastGait.plantedFootSpeed(model, animation.clip("run").orElseThrow()) > 0);

            var offsets = new HashMap<String, Vector3f>();
            for (var bone : model.bones()) {
                var pivot = new Vector3f(bone.pivot().x(), bone.pivot().y(), bone.pivot().z());
                if (bone.parent() != null) pivot.add(offsets.get(bone.parent()));
                offsets.put(bone.name(), pivot);
            }
            var min = new Vector3f(Float.POSITIVE_INFINITY);
            var max = new Vector3f(Float.NEGATIVE_INFINITY);
            for (var part : surface.parts()) {
                for (var t : part.triangles()) {
                    for (var v : java.util.List.of(t.a(), t.b(), t.c())) {
                        var vertex = new Vector3f(v.x(), v.y(), v.z()).add(offsets.get(part.bone()));
                        min.min(vertex); max.max(vertex);
                    }
                }
            }
            var size = max.sub(min).div(16);
            assertEquals(10.020, size.x, .005);
            assertEquals(6.764, size.y, .005);
            assertEquals(11.840, size.z, .005);

            for (var move : def.moves()) {
                if (!move.animation().endsWith("_slam")) continue;
                String side = move.animation().substring(0, move.animation().indexOf('_'));
                String bone = side + "_armor";
                var clip = animation.clip(move.animation()).orElseThrow();
                var rotation = BeastGait.sample(clip, bone, BeastAnimationFile.Target.ROTATION, move.activeTicks().first()/20F, 0);
                assertEquals(side.equals("left") ? 1 : -1, Math.signum(rotation.z), 0);
                var plate = surface.parts().stream().filter(p -> p.bone().equals(bone)).findFirst().orElseThrow();
                float lowest = Float.POSITIVE_INFINITY;
                for (var t : plate.triangles()) {
                    for (var v : java.util.List.of(t.a(), t.b(), t.c())) {
                        var point = new Vector3f(v.x(), v.y(), v.z()).rotateZ((float)Math.toRadians(rotation.z)).add(offsets.get(bone));
                        lowest = Math.min(lowest, 1.501F - point.y / 16F);
                    }
                }
                assertEquals(.036, lowest, .005);
            }
        }
    }
}
