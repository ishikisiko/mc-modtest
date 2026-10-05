package com.example.myvillage.client.entity.beast;

import com.example.myvillage.entity.beast.BeastDataException;
import com.example.myvillage.entity.beast.BeastJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * A beast's cuboid model file (schema 1, {@code assets/<ns>/beast/<name>_model.json}, written by
 * {@code tools/beastgen}). It maps 1:1 onto vanilla geometry: a bone is
 * {@code parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(pivot, rotation in
 * radians))} and a cube is {@code texOffs(u, v).mirror(mirror).addBox(origin, size, new
 * CubeDeformation(inflate))}; there is no coordinate conversion. Parsing is pure and strict:
 * unknown or missing fields, unknown parents, repeated names and UVs outside the texture throw a
 * {@link BeastDataException} naming the file and field.
 */
public record BeastModelFile(
        ResourceLocation id,
        int textureWidth,
        int textureHeight,
        Look look,
        float shadowRadius,
        List<Bone> bones) {

    private static final Pattern BONE_NAME = Pattern.compile("[a-z0-9_]+");
    private static final Set<String> ROOT_FIELDS = Set.of("schema", "id", "texture", "look", "shadow_radius", "bones");
    private static final Set<String> TEXTURE_FIELDS = Set.of("width", "height");
    private static final Set<String> LOOK_FIELDS = Set.of("bone", "max_yaw", "max_pitch");
    private static final Set<String> BONE_FIELDS = Set.of("name", "parent", "pivot", "rotation", "cubes");
    private static final Set<String> CUBE_FIELDS = Set.of("origin", "size", "uv", "inflate", "mirror");

    public BeastModelFile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(look, "look");
        bones = List.copyOf(bones);
    }

    /** Three floats: a position, a size or a rotation in degrees. */
    public record Vec(float x, float y, float z) {
        static Vec of(double[] values) {
            return new Vec((float) values[0], (float) values[1], (float) values[2]);
        }
    }

    /** The bone that receives head yaw and pitch, with their clamps in degrees. */
    public record Look(String bone, float maxYaw, float maxPitch) {
    }

    public record Bone(String name, @Nullable String parent, Vec pivot, Vec rotation, List<Cube> cubes) {
        public Bone {
            cubes = List.copyOf(cubes);
        }
    }

    public record Cube(Vec origin, Vec size, int u, int v, float inflate, boolean mirror) {
    }

    /** Parses one model file; {@code expectedId} is the entity id the file must declare. */
    public static BeastModelFile parse(String file, ResourceLocation expectedId, Reader source) {
        return parse(file, expectedId, BeastJson.parseStrict(file, source));
    }

    public static BeastModelFile parse(String file, ResourceLocation expectedId, JsonObject json) {
        BeastJson.Fields root = new BeastJson.Fields(file, "", json, ROOT_FIELDS);
        root.schema();
        ResourceLocation id = root.id("id");
        if (!id.equals(expectedId)) {
            throw root.error("id", "is " + id + " but this file belongs to " + expectedId);
        }
        BeastJson.Fields texture = root.object("texture", TEXTURE_FIELDS);
        int width = texture.positiveInteger("width");
        int height = texture.positiveInteger("height");
        float shadow = (float) root.nonNegativeNumber("shadow_radius");

        JsonArray bonesJson = root.array("bones");
        if (bonesJson.isEmpty()) {
            throw root.error("bones", "needs at least one bone");
        }
        List<Bone> bones = new ArrayList<>();
        Map<String, Integer> names = new HashMap<>();
        for (int index = 0; index < bonesJson.size(); index++) {
            BeastJson.Fields bone = new BeastJson.Fields(file, "bones[" + index + "]", bonesJson.get(index), BONE_FIELDS);
            String name = bone.string("name");
            if (!BONE_NAME.matcher(name).matches()) {
                throw bone.error("name", "must match [a-z0-9_]+, got \"" + name + "\"");
            }
            if (names.putIfAbsent(name, index) != null) {
                throw bone.error("name", "repeats bone " + name + " (bones[" + names.get(name) + "])");
            }
            String parent = bone.nullableString("parent");
            if (parent != null && !names.containsKey(parent)) {
                throw bone.error("parent", "names " + parent + ", which is not an earlier bone (parents come first)");
            }
            if (name.equals(parent)) {
                throw bone.error("parent", "is the bone itself");
            }
            Vec pivot = Vec.of(bone.numbers("pivot", 3));
            Vec rotation = Vec.of(bone.numbers("rotation", 3));
            JsonArray cubesJson = bone.array("cubes");
            List<Cube> cubes = new ArrayList<>();
            for (int cubeIndex = 0; cubeIndex < cubesJson.size(); cubeIndex++) {
                BeastJson.Fields cube = new BeastJson.Fields(
                        file, bone.field("cubes[" + cubeIndex + "]"), cubesJson.get(cubeIndex), CUBE_FIELDS);
                cubes.add(parseCube(cube, width, height));
            }
            bones.add(new Bone(name, parent, pivot, rotation, cubes));
        }

        BeastJson.Fields look = root.object("look", LOOK_FIELDS);
        String lookBone = look.string("bone");
        if (!names.containsKey(lookBone)) {
            throw look.error("bone", "names " + lookBone + ", which is not a bone of this model");
        }
        float maxYaw = (float) angleLimit(look, "max_yaw");
        float maxPitch = (float) angleLimit(look, "max_pitch");
        return new BeastModelFile(id, width, height, new Look(lookBone, maxYaw, maxPitch), shadow, bones);
    }

    private static double angleLimit(BeastJson.Fields fields, String key) {
        double value = fields.nonNegativeNumber(key);
        if (value > 180.0) {
            throw fields.error(key, "must be at most 180 degrees, got " + value);
        }
        return value;
    }

    private static Cube parseCube(BeastJson.Fields cube, int textureWidth, int textureHeight) {
        Vec origin = Vec.of(cube.numbers("origin", 3));
        double[] size = cube.numbers("size", 3);
        for (double extent : size) {
            if (extent < 0.0) {
                throw cube.error("size", "must not be negative, got " + extent);
            }
        }
        int[] uv = cube.nonNegativeIntegers("uv", 2);
        float inflate = (float) cube.number("inflate");
        boolean mirror = cube.bool("mirror");
        // Vanilla box unwrap: 2 * (depth + width) wide, depth + height tall.
        double right = uv[0] + 2.0 * (size[2] + size[0]);
        double bottom = uv[1] + size[2] + size[1];
        if (right > textureWidth || bottom > textureHeight) {
            throw cube.error("uv", "box unwrap at [" + uv[0] + ", " + uv[1] + "] reaches [" + right + ", " + bottom
                    + "], outside the " + textureWidth + "x" + textureHeight + " texture");
        }
        return new Cube(origin, Vec.of(size), uv[0], uv[1], inflate, mirror);
    }

    /** The vanilla layer definition this file describes. */
    public LayerDefinition toLayerDefinition() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition meshRoot = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        for (Bone bone : bones) {
            PartDefinition parent = bone.parent() == null ? meshRoot : parts.get(bone.parent());
            CubeListBuilder cubes = CubeListBuilder.create();
            for (Cube cube : bone.cubes()) {
                cubes.texOffs(cube.u(), cube.v())
                        .mirror(cube.mirror())
                        .addBox(cube.origin().x(), cube.origin().y(), cube.origin().z(),
                                cube.size().x(), cube.size().y(), cube.size().z(),
                                new CubeDeformation(cube.inflate()));
            }
            PartPose pose = PartPose.offsetAndRotation(
                    bone.pivot().x(), bone.pivot().y(), bone.pivot().z(),
                    radians(bone.rotation().x()), radians(bone.rotation().y()), radians(bone.rotation().z()));
            parts.put(bone.name(), parent.addOrReplaceChild(bone.name(), cubes, pose));
        }
        return LayerDefinition.create(mesh, textureWidth, textureHeight);
    }

    /** Every bone of a root baked from {@link #toLayerDefinition()}, by name, parents first. */
    public Map<String, ModelPart> bonesOf(ModelPart meshRoot) {
        Map<String, ModelPart> parts = new LinkedHashMap<>();
        for (Bone bone : bones) {
            ModelPart parent = bone.parent() == null ? meshRoot : parts.get(bone.parent());
            parts.put(bone.name(), parent.getChild(bone.name()));
        }
        return parts;
    }

    public boolean hasBone(String name) {
        return bones.stream().anyMatch(bone -> bone.name().equals(name));
    }

    static float radians(float degrees) {
        return degrees * ((float) Math.PI / 180.0F);
    }
}
