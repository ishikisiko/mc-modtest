package com.example.myvillage.client.equipment;

import com.example.myvillage.equipment.EquipmentCatalogue;
import com.example.myvillage.equipment.EquipmentModelData;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.LivingEntity;

/** Resource-pack reload replaces all cached models together; only item registration is fixed. */
final class EquipmentModels implements ResourceManagerReloadListener {
    static final EquipmentModels INSTANCE = new EquipmentModels();
    private record Key(ResourceLocation item, String profile) { }
    private volatile Map<Key, HumanoidModel<LivingEntity>> models = Map.of();

    HumanoidModel<LivingEntity> get(ResourceLocation item, boolean slim) {
        var model = models.get(new Key(item, slim ? "slim" : "standard"));
        if (model == null) throw new IllegalStateException("Equipment model not loaded: " + item);
        return model;
    }

    @Override public void onResourceManagerReload(ResourceManager manager) {
        var parsed = new HashMap<ResourceLocation, EquipmentModelData>();
        var next = new HashMap<Key, HumanoidModel<LivingEntity>>();
        for (var entry : EquipmentCatalogue.bundled()) {
            var data = parsed.computeIfAbsent(entry.model(), location -> {
                try (var stream = manager.getResourceOrThrow(location).open();
                     var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    return EquipmentModelData.parse(reader);
                } catch (Exception error) {
                    throw new IllegalStateException("Cannot load armor model " + location, error);
                }
            });
            if (!data.texture().equals(entry.texture())) throw new IllegalArgumentException("Armor texture differs from catalogue");
            for (String profile : List.of("standard", "slim")) {
                var piece = data.profiles().get(profile).get(entry.id());
                if (piece == null || !piece.slot().equals(entry.type().getSlot().getName()))
                    throw new IllegalArgumentException("Armor model slot differs from catalogue: " + entry.id());
                next.put(new Key(entry.id(), profile), bake(data, piece));
            }
        }
        models = Map.copyOf(next);
        org.slf4j.LoggerFactory.getLogger(EquipmentModels.class).info("EQUIPMENT_MODELS loaded {} item/profile models", next.size());
    }

    private static HumanoidModel<LivingEntity> bake(EquipmentModelData data, EquipmentModelData.Piece piece) {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        for (String bone : List.of("head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"))
            root.addOrReplaceChild(bone, CubeListBuilder.create(), PartPose.ZERO);
        float scale = data.density();
        int index = 0;
        for (var box : piece.boxes()) {
            var f = box.from(); var s = box.size(); var p = box.pivot(); var r = box.rotation();
            // Scale coordinates up for texel density, then scale the child back down. UV and model
            // dimensions now follow ModelPart.Cube exactly, including fractional/slim dimensions.
            var cube = CubeListBuilder.create().texOffs(box.u(), box.v())
                    .addBox((f.x()-p.x())*scale, (f.y()-p.y())*scale, (f.z()-p.z())*scale,
                            s.x()*scale, s.y()*scale, s.z()*scale);
            root.getChild(box.bone()).addOrReplaceChild("part_" + index++, cube,
                    PartPose.offsetAndRotation(p.x(), p.y(), p.z(),
                            (float)Math.toRadians(r.x()), (float)Math.toRadians(r.y()), (float)Math.toRadians(r.z())));
        }
        var baked = LayerDefinition.create(mesh, data.textureSize(), data.textureSize()).bakeRoot();
        index = 0;
        for (var box : piece.boxes()) {
            var part = baked.getChild(box.bone()).getChild("part_" + index++);
            part.xScale = part.yScale = part.zScale = 1 / scale;
        }
        return new HumanoidModel<>(baked);
    }
}
