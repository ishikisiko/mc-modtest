package com.example.myvillage.client.entity.beast;

import com.example.myvillage.entity.beast.BeastEntity;
import com.example.myvillage.entity.beast.ThunderTigerEntity;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import javax.annotation.Nullable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;

/**
 * Generic model for any {@link BeastEntity}: the bones of its model file and its keyframe clips.
 * Each frame it resets the pose, turns the model file's {@code look.bone} by the clamped head yaw
 * and pitch, plays every started state of {@link BeastEntity#clipAnimationStates()} with the clip
 * of the same name, and drives {@code animateWalk} with {@link BeastEntity#locomotionClip()}.
 * Clip time is {@link BeastEntity#clipTime}: a hit-stop pauses the clips, and a frozen still shows
 * the server's move tick.
 */
public class BeastModel<T extends BeastEntity> extends HierarchicalModel<T> {
    /** Limb swing amount times this is the clip weight (capped at 1). */
    static final float WALK_SCALE = 2.5F;

    private final ModelPart root;
    private final Map<String, ModelPart> bones;
    private final ModelPart lookBone;
    private final float maxYawDegrees;
    private final float maxPitchDegrees;
    private final Map<String, AnimationDefinition> clips;
    private final BeastGait.Rates gait;
    @Nullable private final BeastSurfaceMesh surfaceMesh;
    private final Map<String, List<ModelPart>> surfaceChains;

    /** {@code gait}: the {@code animateWalk} rates that keep the walk and run clips' feet planted. */
    public BeastModel(ModelPart root, BeastModelFile modelFile, Map<String, AnimationDefinition> clips, BeastGait.Rates gait) {
        this(root, modelFile, clips, gait, null);
    }

    public BeastModel(ModelPart root, BeastModelFile modelFile, Map<String, AnimationDefinition> clips,
            BeastGait.Rates gait, @Nullable BeastSurfaceMesh surfaceMesh) {
        this.root = root;
        this.bones = modelFile.bonesOf(root);
        this.lookBone = bones.get(modelFile.look().bone());
        this.maxYawDegrees = modelFile.look().maxYaw();
        this.maxPitchDegrees = modelFile.look().maxPitch();
        this.clips = Map.copyOf(clips);
        this.gait = gait;
        this.surfaceMesh = surfaceMesh;
        Map<String, List<ModelPart>> chains = new LinkedHashMap<>();
        if (surfaceMesh != null) for (var bone : modelFile.bones()) {
            List<ModelPart> chain = new ArrayList<>();
            if (bone.parent() != null) chain.addAll(chains.get(bone.parent()));
            chain.add(bones.get(bone.name()));
            chains.put(bone.name(), List.copyOf(chain));
        }
        this.surfaceChains = Map.copyOf(chains);
    }

    @Override
    public void renderToBuffer(PoseStack poses, VertexConsumer out, int light, int overlay, int color) {
        if (surfaceMesh == null) {
            super.renderToBuffer(poses, out, light, overlay, color);
            return;
        }
        if (!root.visible) return;
        for (var part : surfaceMesh.parts()) {
            List<ModelPart> chain = surfaceChains.get(part.bone());
            if (chain.stream().anyMatch(bone -> !bone.visible)) continue;
            poses.pushPose();
            root.translateAndRotate(poses);
            for (ModelPart bone : chain) bone.translateAndRotate(poses);
            for (var triangle : part.triangles()) {
                surfaceVertex(poses, out, triangle.a(), triangle, light, overlay, color);
                surfaceVertex(poses, out, triangle.b(), triangle, light, overlay, color);
                surfaceVertex(poses, out, triangle.c(), triangle, light, overlay, color);
                // Vanilla entity buffers consume quads; repeat C to make the second triangle degenerate.
                surfaceVertex(poses, out, triangle.c(), triangle, light, overlay, color);
            }
            poses.popPose();
        }
    }

    private static void surfaceVertex(PoseStack poses, VertexConsumer out, BeastSurfaceMesh.Vertex vertex,
            BeastSurfaceMesh.Triangle triangle, int light, int overlay, int color) {
        out.addVertex(poses.last(), vertex.x() / 16, vertex.y() / 16, vertex.z() / 16)
                .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255)
                .setUv(vertex.u(), vertex.v()).setOverlay(overlay).setLight(light)
                .setNormal(poses.last(), triangle.nx(), triangle.ny(), triangle.nz());
    }

    @Override
    public ModelPart root() {
        return root;
    }

    /**
     * Bones by their model-file name. Vanilla's lookup short-cuts {@code "root"} to the mesh root,
     * which would send a bone named {@code root} to the wrong pivot.
     */
    @Override
    public Optional<ModelPart> getAnyDescendantWithName(String name) {
        return Optional.ofNullable(bones.get(name));
    }

    @Override
    public void setupAnim(T beast, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        ThunderTigerEntity tiger = beast instanceof ThunderTigerEntity value ? value : null;
        boolean presentation = tiger != null && (tiger.isPresentationShown() || tiger.isHurtShown());
        if (tiger != null) {
            ModelPart hornTip = bones.get("horn_tip");
            if (hornTip != null) {
                hornTip.visible = !tiger.isHornBroken();
            }
        }
        if (!beast.isMoveShown() && !beast.isStaggerShown() && !presentation) {
            lookBone.yRot += Mth.clamp(netHeadYaw, -maxYawDegrees, maxYawDegrees) * Mth.DEG_TO_RAD;
            lookBone.xRot += Mth.clamp(headPitch, -maxPitchDegrees, maxPitchDegrees) * Mth.DEG_TO_RAD;
        }
        float time = beast.clipTime(ageInTicks - beast.tickCount);
        for (Map.Entry<String, AnimationState> entry : beast.clipAnimationStates().entrySet()) {
            if (tiger != null && "hurt".equals(entry.getKey()) && !tiger.isHurtShown()) continue;
            if (presentation && BeastEntity.IDLE_CLIP.equals(entry.getKey())) {
                continue;
            }
            AnimationDefinition clip = clips.get(entry.getKey());
            if (clip != null) {
                animate(entry.getValue(), clip, time);
            }
        }
        if(beast instanceof com.example.myvillage.legacy.LegacyBeastEntity legacy&&legacy.toad()&&legacy.heat()>0) {
            var belly=bones.get("belly");var rules=com.example.myvillage.legacy.LegacyRules.get();
            if(belly!=null) {
                float heat=legacy.heat()/(float)rules.ticks("toad_max_heat");
                float scale=(float)(1+heat*(rules.visual("toad_breath_scale")+rules.visual("toad_breath_pulse")*(.5+.5*Math.sin(ageInTicks*rules.visual("toad_breath_frequency")))));
                belly.xScale*=scale;belly.yScale*=scale;belly.zScale*=scale;
            }
        }
        // A move or flinch owns the legs; the run cycle must not play over a pounce in flight.
        if (!beast.isMoveShown() && !beast.isStaggerShown() && !presentation) {
            AnimationDefinition locomotion = clips.get(beast.locomotionClip());
            if (locomotion != null) {
                float speed = BeastEntity.RUN_CLIP.equals(beast.locomotionClip()) ? gait.run() : gait.walk();
                animateWalk(locomotion, limbSwing, limbSwingAmount, speed, WALK_SCALE);
            }
        }
    }
}
