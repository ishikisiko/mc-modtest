package com.example.myvillage.equipment;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class EquipmentDataTest {
    private static final String ENTRY = """
            {"id":"myvillage:sample_helmet","slot":"head","material":"minecraft:iron",
             "durability_multiplier":15,"model":"myvillage:equipment/sample.json",
             "texture":"myvillage:textures/equipment/sample.png"}
            """;
    private static String catalogue(String entries) { return "{\"version\":1,\"items\":[" + entries + "]}"; }

    @Test void arbitraryAuthoredItemIdentityDoesNotNeedJavaRegistrationChanges() {
        var items = EquipmentCatalogue.parse(new StringReader(catalogue(ENTRY)));
        assertEquals("myvillage:sample_helmet",items.getFirst().id().toString());
        assertEquals("head",items.getFirst().type().getSlot().getName());
        assertEquals(165,items.getFirst().type().getDurability(items.getFirst().durabilityMultiplier()));
    }

    @Test void duplicateIdentityUnknownSlotAndMaterialFailClosed() {
        assertThrows(IllegalArgumentException.class,()->EquipmentCatalogue.parse(new StringReader(catalogue(ENTRY+","+ENTRY))));
        for (String bad : new String[]{ENTRY.replace("\"head\"","\"hand\""),ENTRY.replace("minecraft:iron","minecraft:diamond"),
                ENTRY.replace("sample_helmet","../sample"),ENTRY.replace(":15",":0")})
            assertThrows(IllegalArgumentException.class,()->EquipmentCatalogue.parse(new StringReader(catalogue(bad))));
    }

    private static String model() throws Exception {
        var entry=EquipmentCatalogue.bundled().getFirst();
        try(var stream=EquipmentDataTest.class.getResourceAsStream("/assets/"+entry.model().getNamespace()+"/"+entry.model().getPath())) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
    }

    @Test void bundledModelsMatchAllFourSlotsAndBothProfiles() throws Exception {
        var data=EquipmentModelData.parse(new StringReader(model()));
        assertEquals(4,EquipmentCatalogue.bundled().size());
        for(var entry:EquipmentCatalogue.bundled()) for(String profile:new String[]{"standard","slim"}) {
            var piece=data.profiles().get(profile).get(entry.id());
            assertNotNull(piece);assertFalse(piece.boxes().isEmpty());
            assertEquals(entry.type().getSlot().getName(),piece.slot());
            assertEquals(entry.texture(),data.texture());
        }
    }

    @Test void wrongBoneOutOfAtlasAndMismatchedProfilesAreRejected() throws Exception {
        var source=JsonParser.parseString(model()).getAsJsonObject();
        var box=source.getAsJsonObject("profiles").getAsJsonArray("standard").get(0).getAsJsonObject().getAsJsonArray("boxes").get(0).getAsJsonObject();
        box.addProperty("bone","right_leg");
        assertThrows(IllegalArgumentException.class,()->EquipmentModelData.parse(new StringReader(source.toString())));
        box.addProperty("bone","head");box.getAsJsonArray("uv").set(0,new com.google.gson.JsonPrimitive(512));
        assertThrows(IllegalArgumentException.class,()->EquipmentModelData.parse(new StringReader(source.toString())));
        var other=JsonParser.parseString(model()).getAsJsonObject();other.getAsJsonObject("profiles").remove("slim");
        assertThrows(IllegalArgumentException.class,()->EquipmentModelData.parse(new StringReader(other.toString())));
    }
}
