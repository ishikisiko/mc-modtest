package com.example.myvillage.client.entity.beast;

import com.example.myvillage.entity.beast.BeastEntity;
import java.util.Map;
import java.util.Optional;
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

    /** {@code gait}: the {@code animateWalk} rates that keep the walk and run clips' feet planted. */
    public BeastModel(ModelPart root, BeastModelFile modelFile, Map<String, AnimationDefinition> clips, BeastGait.Rates gait) {
        this.root = root;
        this.bones = modelFile.bonesOf(root);
        this.lookBone = bones.get(modelFile.look().bone());
        this.maxYawDegrees = modelFile.look().maxYaw();
        this.maxPitchDegrees = modelFile.look().maxPitch();
        this.clips = Map.copyOf(clips);
        this.gait = gait;
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
        if (!beast.isMoveShown() && !beast.isStaggerShown()) {
            lookBone.yRot += Mth.clamp(netHeadYaw, -maxYawDegrees, maxYawDegrees) * Mth.DEG_TO_RAD;
            lookBone.xRot += Mth.clamp(headPitch, -maxPitchDegrees, maxPitchDegrees) * Mth.DEG_TO_RAD;
        }
        float time = beast.clipTime(ageInTicks - beast.tickCount);
        for (Map.Entry<String, AnimationState> entry : beast.clipAnimationStates().entrySet()) {
            AnimationDefinition clip = clips.get(entry.getKey());
            if (clip != null) {
                animate(entry.getValue(), clip, time);
            }
        }
        // A move or flinch owns the legs; the run cycle must not play over a pounce in flight.
        if (!beast.isMoveShown() && !beast.isStaggerShown()) {
            AnimationDefinition locomotion = clips.get(beast.locomotionClip());
            if (locomotion != null) {
                float speed = BeastEntity.RUN_CLIP.equals(beast.locomotionClip()) ? gait.run() : gait.walk();
                animateWalk(locomotion, limbSwing, limbSwingAmount, speed, WALK_SCALE);
            }
        }
    }
}
