package com.example.myvillage.client.entity.beast;

import com.example.myvillage.entity.beast.BeastDataException;
import com.example.myvillage.entity.beast.BeastJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A bounded, bone-local triangle surface. The skeleton and animations stay in BeastModelFile. */
public record BeastSurfaceMesh(List<Part> parts) {
    private static final int MAX_TRIANGLES = 8192;
    private static final Set<String> ROOT_FIELDS = Set.of("schema", "id", "parts");
    private static final Set<String> PART_FIELDS = Set.of("bone", "triangles");

    public BeastSurfaceMesh { parts = List.copyOf(parts); }
    public record Vertex(float x, float y, float z, float u, float v) { }
    public record Triangle(Vertex a, Vertex b, Vertex c, float nx, float ny, float nz) { }
    public record Part(String bone, List<Triangle> triangles) {
        public Part { triangles = List.copyOf(triangles); }
    }

    public static BeastSurfaceMesh parse(String file, BeastModelFile model, Reader source) {
        var root = new BeastJson.Fields(file, "", BeastJson.parseStrict(file, source), ROOT_FIELDS);
        root.schema();
        if (!root.id("id").equals(model.id())) throw root.error("id", "does not match its skeleton");
        JsonArray rows = root.array("parts");
        if (rows.isEmpty() || rows.size() > model.bones().size())
            throw root.error("parts", "needs one or more unique skeleton bones");
        List<Part> parts = new ArrayList<>();
        Set<String> used = new HashSet<>();
        int total = 0;
        for (int i = 0; i < rows.size(); i++) {
            var row = new BeastJson.Fields(file, "parts[" + i + "]", rows.get(i), PART_FIELDS);
            String bone = row.string("bone");
            if (!model.hasBone(bone) || !used.add(bone)) throw row.error("bone", "must name an unused skeleton bone");
            JsonArray triangles = row.array("triangles");
            total += triangles.size();
            if (triangles.isEmpty() || total > MAX_TRIANGLES)
                throw row.error("triangles", "must be nonempty and the mesh must not exceed " + MAX_TRIANGLES + " triangles");
            List<Triangle> parsed = new ArrayList<>();
            for (int j = 0; j < triangles.size(); j++) {
                String field = row.field("triangles[" + j + "]");
                JsonElement element = triangles.get(j);
                if (!element.isJsonArray() || element.getAsJsonArray().size() != 15)
                    throw new BeastDataException(file, field, "needs three [x,y,z,u,v] vertices, flattened");
                JsonArray values = element.getAsJsonArray();
                float[] v = new float[15];
                for (int k = 0; k < 15; k++) {
                    JsonElement number = values.get(k);
                    if (!number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber())
                        throw new BeastDataException(file, field + "[" + k + "]", "must be a number");
                    double value = number.getAsDouble();
                    boolean uv = k % 5 >= 3;
                    if (!Double.isFinite(value) || (uv ? value < 0 || value > 1 : Math.abs(value) > 256))
                        throw new BeastDataException(file, field + "[" + k + "]", uv ? "UV must be in [0,1]" : "position must be finite and within 256 model units");
                    v[k] = (float) value;
                }
                Vertex a = vertex(v, 0), b = vertex(v, 5), c = vertex(v, 10);
                double bx = b.x - a.x, by = b.y - a.y, bz = b.z - a.z;
                double cx = c.x - a.x, cy = c.y - a.y, cz = c.z - a.z;
                double nx = by * cz - bz * cy, ny = bz * cx - bx * cz, nz = bx * cy - by * cx;
                double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (length < 1.0e-7) throw new BeastDataException(file, field, "degenerate triangle");
                parsed.add(new Triangle(a, b, c, (float) (nx / length), (float) (ny / length), (float) (nz / length)));
            }
            parts.add(new Part(bone, parsed));
        }
        return new BeastSurfaceMesh(parts);
    }

    private static Vertex vertex(float[] values, int offset) {
        return new Vertex(values[offset], values[offset + 1], values[offset + 2], values[offset + 3], values[offset + 4]);
    }
}
