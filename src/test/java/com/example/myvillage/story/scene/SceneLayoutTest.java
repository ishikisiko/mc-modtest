package com.example.myvillage.story.scene;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneLayoutTest {
    private JsonObject resource() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(System.getProperty("myvillage.test.sceneLayout",
                "build/generated/qianzhen/resources/data/myvillage/story/qianzhen_scene.json")))).getAsJsonObject();
    }
    @Test void productionContractContainsEveryRoleAndClickableTarget() throws Exception {
        SceneLayout layout = SceneLayout.parse(resource());
        assertEquals(6, layout.sites().size()); assertEquals(9, layout.targets().size());
        assertEquals(new SceneLayout.Pos(201, 1, 51), layout.positions().get("zhou_he").pos());
        assertEquals(new SceneLayout.Pos(230, 3, -24), layout.positions().get("warden").pos());
        assertEquals(new SceneLayout.Pos(161, 2, 76), layout.positions().get("water_sample").pos());
        assertNotEquals(layout.positions().get("water_sample").pos(), layout.positions().get("water_sample").approach());
        assertThrows(UnsupportedOperationException.class, () -> layout.positions().clear());
    }
    @Test void duplicateTargetsAndMismatchedStationsAreRejected() throws Exception {
        JsonObject raw = resource(); raw.getAsJsonArray("targets").add(raw.getAsJsonArray("targets").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> SceneLayout.parse(raw));
        JsonObject different = resource(); different.getAsJsonArray("targets").get(0).getAsJsonObject().addProperty("block", "minecraft:air");
        assertThrows(IllegalArgumentException.class, () -> SceneLayout.parse(different));
    }
    @Test void fractionalCoordinatesAndOverlappingRoadsFailClosed() throws Exception {
        JsonObject fractional = resource();
        fractional.getAsJsonObject("positions").getAsJsonObject("guide").getAsJsonArray("pos").set(0, new com.google.gson.JsonPrimitive(36.5));
        assertThrows(IllegalArgumentException.class, () -> SceneLayout.parse(fractional));
        JsonObject overlap = resource();
        overlap.getAsJsonArray("roads").get(0).getAsJsonObject().add("pos", JsonParser.parseString("[0,0,114]"));
        assertThrows(IllegalArgumentException.class, () -> SceneLayout.parse(overlap));
    }
    @Test void openingFacilitiesAreSeparateSolidTargetsWithDistinctApproaches() throws Exception {
        SceneLayout layout = SceneLayout.parse(resource());
        assertEquals(3, layout.facilities().size()); assertEquals(9, layout.targets().size());
        var soil = layout.facilities().stream().filter(f -> f.id().equals("apprentice_plot")).findFirst().orElseThrow();
        assertEquals("myvillage:spirit_soil", soil.block());
        assertEquals(new SceneLayout.Pos(96, 0, 102), soil.pos());
        assertEquals(new SceneLayout.Pos(95, 1, 102), soil.approach());
        assertNotEquals(soil.pos(), soil.approach());
        assertThrows(UnsupportedOperationException.class, () -> layout.facilities().clear());
    }
    @Test void legacySchemaOneHasNoOpeningFacilitiesAndKeepsEveryOldTarget() throws Exception {
        JsonObject legacy = resource(); legacy.remove("facilities");
        for (String key : new String[]{"testing_stele", "inheritance_stele", "apprentice_plot"})
            legacy.getAsJsonObject("positions").remove(key);
        SceneLayout layout = SceneLayout.parse(legacy);
        assertTrue(layout.facilities().isEmpty()); assertEquals(9, layout.targets().size());
        assertEquals(15, layout.positions().size());
    }
    @Test void partialFacilitiesWrongBlocksOrStandingInsideSolidFacilityFailClosed() throws Exception {
        JsonObject partial = resource(); partial.getAsJsonArray("facilities").remove(0);
        assertThrows(IllegalArgumentException.class, () -> SceneLayout.parse(partial));
        JsonObject air = resource();
        air.getAsJsonArray("facilities").get(0).getAsJsonObject().addProperty("state", "minecraft:air");
        assertThrows(IllegalArgumentException.class, () -> SceneLayout.parse(air));
        JsonObject inside = resource();
        JsonObject first = inside.getAsJsonArray("facilities").get(0).getAsJsonObject();
        first.add("approach", first.get("pos").deepCopy());
        inside.getAsJsonObject("positions").getAsJsonObject(first.get("id").getAsString())
                .add("approach", first.get("pos").deepCopy());
        assertThrows(IllegalArgumentException.class, () -> SceneLayout.parse(inside));
        JsonObject orphan = resource(); orphan.remove("facilities");
        assertThrows(IllegalArgumentException.class, () -> SceneLayout.parse(orphan));
    }
}
