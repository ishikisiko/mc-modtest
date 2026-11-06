package com.example.myvillage.client.entity.beast;

import com.example.myvillage.entity.beast.BeastDataException;
import com.google.gson.JsonParser;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BeastSurfaceMeshTest {
    private static final String MODEL = """
            {"schema":1,"id":"myvillage:test","texture":{"width":64,"height":64},
             "look":{"bone":"root","max_yaw":0,"max_pitch":0},"shadow_radius":1,
             "bones":[{"name":"root","parent":null,"pivot":[0,24,0],"rotation":[0,0,0],"cubes":[]}]}
            """;
    private BeastModelFile skeleton() {
        return BeastModelFile.parse("fixture", ResourceLocation.parse("myvillage:test"), new StringReader(MODEL));
    }
    private BeastSurfaceMesh parse(String triangle, String bone) {
        return BeastSurfaceMesh.parse("mesh", skeleton(), new StringReader(
                "{\"schema\":1,\"id\":\"myvillage:test\",\"parts\":[{\"bone\":\"" + bone
                        + "\",\"triangles\":[" + triangle + "]}]}"));
    }
    @Test void computesTheActualSlopedFaceNormal() {
        var triangle = parse("[0,0,0,0,0, 2,0,0,1,0, 0,2,1,0,1]", "root").parts().getFirst().triangles().getFirst();
        assertEquals(0, triangle.nx(), 1e-6);
        assertEquals(-1 / Math.sqrt(5), triangle.ny(), 1e-6);
        assertEquals(2 / Math.sqrt(5), triangle.nz(), 1e-6);
    }
    @Test void refusesUnknownBonesAndInvalidTextureCoordinates() {
        assertThrows(BeastDataException.class, () -> parse("[0,0,0,0,0, 1,0,0,1,0, 0,1,0,0,1]", "missing"));
        assertThrows(BeastDataException.class, () -> parse("[0,0,0,0,0, 1,0,0,1.01,0, 0,1,0,0,1]", "root"));
    }
    @Test void refusesDegenerateAndUnboundedFaces() {
        assertThrows(BeastDataException.class, () -> parse("[0,0,0,0,0, 1,0,0,1,0, 2,0,0,0,1]", "root"));
        assertThrows(BeastDataException.class, () -> parse("[257,0,0,0,0, 1,0,0,1,0, 0,1,0,0,1]", "root"));
        assertThrows(BeastDataException.class, () -> parse("[0,0,0,0,0, 1,0,0,1,0, 0,1,0,0]", "root"));
    }
    @Test void retainsTheOrdinaryCuboidModelPath() {
        assertNull(skeleton().surfaceMesh());
        var json = JsonParser.parseString(MODEL).getAsJsonObject();
        json.addProperty("surface_mesh", "other:beast/test.json");
        assertThrows(BeastDataException.class, () -> BeastModelFile.parse("fixture", ResourceLocation.parse("myvillage:test"), json));
    }
    @Test void actualGuardianMeshBindsToItsAnimationSkeleton() throws Exception {
        Path root = Path.of("src/main/resources/assets/myvillage/beast");
        BeastModelFile model;
        try (var reader = Files.newBufferedReader(root.resolve("stele_guardian_model.json"))) {
            model = BeastModelFile.parse("stele_guardian", ResourceLocation.parse("myvillage:stele_guardian"), reader);
        }
        assertNotNull(model.surfaceMesh());
        try (var reader = Files.newBufferedReader(root.resolve("stele_guardian_surface.json"))) {
            var surface = BeastSurfaceMesh.parse("stele_guardian_surface", model, reader);
            assertEquals(8, surface.parts().size());
            assertTrue(surface.parts().stream().mapToInt(p -> p.triangles().size()).sum() > 500);
            assertTrue(surface.parts().stream().anyMatch(p -> p.bone().equals("forearm_left")));
        }
    }
}
