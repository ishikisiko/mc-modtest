package com.example.myvillage.client.entity.npc;

import com.example.myvillage.client.entity.beast.BeastModelFile;
import com.example.myvillage.entity.npc.NpcEntity;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Generic model for any {@link NpcEntity}: the bones of its model file, an {@code idle} clip that
 * always runs and a {@code walk} clip driven by limb swing. Each frame it resets the pose, turns
 * the model file's {@code look.bone} by the clamped head yaw and pitch, and adds both clips.
 */
public class NpcModel<T extends NpcEntity> extends HierarchicalModel<T> {
    /** Limb swing amount times this is the walk clip's weight (capped at 1). */
    static final float WALK_SCALE = 2.5F;

    private final ModelPart root;
    private final Map<String, ModelPart> bones;
    private final ModelPart lookBone;
    private final float maxYawDegrees;
    private final float maxPitchDegrees;
    private final AnimationDefinition idle;
    private final AnimationDefinition walk;
    private final float walkRate;

    /** {@code walkRate}: the {@code animateWalk} rate that keeps the walk clip's planted foot still. */
    public NpcModel(ModelPart root, BeastModelFile modelFile, AnimationDefinition idle, AnimationDefinition walk,
            float walkRate) {
        this.root = root;
        this.bones = modelFile.bonesOf(root);
        this.lookBone = bones.get(modelFile.look().bone());
        this.maxYawDegrees = modelFile.look().maxYaw();
        this.maxPitchDegrees = modelFile.look().maxPitch();
        this.idle = idle;
        this.walk = walk;
        this.walkRate = walkRate;
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
    public void setupAnim(T npc, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        lookBone.yRot += Mth.clamp(netHeadYaw, -maxYawDegrees, maxYawDegrees) * Mth.DEG_TO_RAD;
        lookBone.xRot += Mth.clamp(headPitch, -maxPitchDegrees, maxPitchDegrees) * Mth.DEG_TO_RAD;
        animate(npc.idleAnimationState(), idle, ageInTicks);
        animateWalk(walk, limbSwing, limbSwingAmount, walkRate, WALK_SCALE);
    }
}
