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
    private final float modelScale;
    private final Map<String, AnimationDefinition> combatClips;
    private final Map<String, String> parents = new java.util.HashMap<>();
    private final org.joml.Vector3f animationScratch = new org.joml.Vector3f();

    /** {@code walkRate}: the {@code animateWalk} rate that keeps the walk clip's planted foot still. */
    public NpcModel(ModelPart root, BeastModelFile modelFile, AnimationDefinition idle, AnimationDefinition walk,
            float walkRate, Map<String, AnimationDefinition> combatClips) {
        this.root = root;
        this.bones = modelFile.bonesOf(root);
        this.lookBone = bones.get(modelFile.look().bone());
        this.maxYawDegrees = modelFile.look().maxYaw();
        this.maxPitchDegrees = modelFile.look().maxPitch();
        this.idle = idle;
        this.walk = walk;
        this.walkRate = walkRate;
        this.modelScale = modelFile.scale();
        this.combatClips = combatClips;
        modelFile.bones().forEach(bone -> parents.put(bone.name(), bone.parent()));
    }

    public float modelScale() { return modelScale; }

    /** Walk the authored bone hierarchy; hand offsets are generated from the hand cube centre. */
    public void applyGrip(com.mojang.blaze3d.vertex.PoseStack poses, String name) {
        String parent = parents.get(name);
        if (parent != null && !parent.isEmpty()) applyGrip(poses, parent);
        ModelPart part = bones.get(name);
        if (part != null) part.translateAndRotate(poses);
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
        var combat = com.example.myvillage.client.combat.NpcCombatPresentation.pose(npc, ageInTicks - npc.tickCount);
        if (combat != null && combatClips.containsKey(combat.clip())) {
            net.minecraft.client.animation.KeyframeAnimations.animate(this, combatClips.get(combat.clip()),
                    (long) (combat.ticks() * 50), 1, animationScratch);
            return;
        }
        if (npc.combatEngaged() && combatClips.containsKey("sword_ready")) {
            Map<String, net.minecraft.client.model.geom.PartPose> locomotion = new java.util.HashMap<>();
            if (limbSwingAmount > 0) {
                animateWalk(walk, limbSwing, limbSwingAmount, walkRate, WALK_SCALE);
                bones.forEach((name, bone) -> {
                    if (name.equals("root") || name.startsWith("leg_") || name.startsWith("skirt_")
                            || name.startsWith("vest_front") || name.equals("vest_back")
                            || name.equals("sash") || name.equals("pendant")) locomotion.put(name, bone.storePose());
                });
                root.getAllParts().forEach(ModelPart::resetPose);
            }
            net.minecraft.client.animation.KeyframeAnimations.animate(this, combatClips.get("sword_ready"),
                    (long) (ageInTicks * 50), 1, animationScratch);
            locomotion.forEach((name, pose) -> bones.get(name).loadPose(pose));
            return;
        }
        lookBone.yRot += Mth.clamp(netHeadYaw, -maxYawDegrees, maxYawDegrees) * Mth.DEG_TO_RAD;
        lookBone.xRot += Mth.clamp(headPitch, -maxPitchDegrees, maxPitchDegrees) * Mth.DEG_TO_RAD;
        animate(npc.idleAnimationState(), idle, ageInTicks);
        animateWalk(walk, limbSwing, limbSwingAmount, walkRate, WALK_SCALE);
    }
}
