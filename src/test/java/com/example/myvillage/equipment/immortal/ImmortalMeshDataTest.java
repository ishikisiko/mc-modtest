package com.example.myvillage.equipment.immortal;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Pure data checks; no client initialization or simulated player acceptance. */
final class ImmortalMeshDataTest {
    private static JsonObject bundled(String id) {
        var stream = ImmortalMeshDataTest.class.getResourceAsStream("/assets/myvillage/immortal_equipment/" + id + ".json");
        assertNotNull(stream, "缺正式网格 " + id);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }

    private static JsonObject firstPart(JsonObject source, String profile) {
        return source.getAsJsonObject("profiles").getAsJsonObject(profile).getAsJsonArray("parts").get(0).getAsJsonObject();
    }

    private static void rejects(Consumer<JsonObject> mutation) {
        var data = bundled("qi_moqing");
        mutation.accept(data);
        assertThrows(IllegalArgumentException.class, () -> ImmortalMeshData.parse(new StringReader(data.toString())));
    }

    @Test void allFifteenRealSetsHaveBothProfilesAndFourVisibleSlots() {
        var names = List.of("qi_moqing", "qi_tingyu", "qi_chixia", "zhuji_baiqing_lingyun", "zhuji_yuheng_yizhuang",
                "zhuji_wuyao_yeque", "jindan_jinwu", "jindan_taiyin", "jindan_zixiao", "yuanying_xinghe",
                "yuanying_jiuse", "yuanying_qingdi", "huashen_taixu", "huashen_xuanji", "huashen_dari");
        for (String name : names) {
            var mesh = ImmortalMeshData.parse(new StringReader(bundled(name).toString()));
            assertEquals(name, mesh.setId());
            assertEquals(java.util.Set.of("standard", "slim"), mesh.profiles().keySet());
            for (var profile : mesh.profiles().values()) {
                assertEquals(java.util.Set.of("head", "chest", "legs", "feet"),
                        profile.parts().stream().map(ImmortalMeshData.Part::slot).collect(java.util.stream.Collectors.toSet()));
                assertTrue(profile.parts().stream().noneMatch(p -> p.id().startsWith("avatar_")));
                assertTrue(profile.parts().stream().allMatch(p -> !p.vertices().isEmpty() && p.indices().size() % 3 == 0));
            }
        }
    }

    @Test void missingIdentityProfileAndUnknownBoneFailClosed() {
        rejects(d -> d.remove("set_id"));
        rejects(d -> d.getAsJsonObject("profiles").remove("slim"));
        rejects(d -> firstPart(d, "standard").addProperty("bone", "tail"));
    }

    @Test void unknownMaterialAndTextureTraversalFailClosed() {
        rejects(d -> firstPart(d, "standard").addProperty("material", "missing"));
        rejects(d -> d.getAsJsonObject("materials").entrySet().iterator().next().getValue().getAsJsonObject()
                .addProperty("texture", "myvillage:textures/../private.png"));
    }

    @Test void invalidIndexUvAndNormalFailClosed() {
        rejects(d -> firstPart(d, "standard").getAsJsonArray("indices").set(0, new JsonPrimitive(-1)));
        rejects(d -> firstPart(d, "standard").getAsJsonArray("indices").set(0, new JsonPrimitive(.5)));
        rejects(d -> firstPart(d, "standard").getAsJsonArray("vertices").get(0).getAsJsonArray().set(3, new JsonPrimitive(1.1)));
        rejects(d -> {
            var v = firstPart(d, "standard").getAsJsonArray("vertices").get(0).getAsJsonArray();
            for (int i = 5; i < 8; i++) v.set(i, new JsonPrimitive(0));
        });
        rejects(d -> firstPart(d, "standard").getAsJsonArray("vertices").get(0).getAsJsonArray().set(0, new JsonPrimitive(1e100)));
    }

    @Test void duplicatePartAndDegenerateTriangleFailClosed() {
        rejects(d -> {
            var parts = d.getAsJsonObject("profiles").getAsJsonObject("standard").getAsJsonArray("parts");
            parts.add(parts.get(0).deepCopy());
        });
        rejects(d -> {
            var indices = firstPart(d, "standard").getAsJsonArray("indices");
            indices.set(1, indices.get(0).deepCopy());
        });
    }

    @Test void slotMustUseItsOwnSkeletonBone() {
        rejects(d -> {
            var p = firstPart(d, "standard");
            assertEquals("head", p.get("slot").getAsString());
            p.addProperty("bone", "right_leg");
        });
    }

    @Test void effectBindingCountAndUnknownTargetsFailClosed() {
        rejects(d -> d.getAsJsonObject("effect_bindings").getAsJsonArray("flows").add("body"));
        var data = bundled("jindan_zixiao");
        var targets = data.getAsJsonObject("effects").getAsJsonArray("motions").get(0).getAsJsonObject().getAsJsonArray("targets");
        targets.set(0, new JsonPrimitive("missing_component"));
        assertThrows(IllegalArgumentException.class, () -> ImmortalMeshData.parse(new StringReader(data.toString())));
    }

    @Test void motionCannotSpanSkeletonBones() {
        var data = bundled("jindan_zixiao");
        var parts = data.getAsJsonObject("profiles").getAsJsonObject("standard").getAsJsonArray("parts");
        String head = null;
        for (var row : parts) if (row.getAsJsonObject().get("bone").getAsString().equals("head")) {
            head = row.getAsJsonObject().get("id").getAsString(); break;
        }
        assertNotNull(head);
        data.getAsJsonObject("effects").getAsJsonArray("motions").get(0).getAsJsonObject().getAsJsonArray("targets").add(head);
        assertThrows(IllegalArgumentException.class, () -> ImmortalMeshData.parse(new StringReader(data.toString())));
    }
}
