package com.example.myvillage.client.combat;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.HumanoidArm;

import java.util.EnumSet;
import java.util.Set;

/**
 * Two box segments (upper arm, forearm with fist) cut from the player skin, for one arm and one
 * skin width. Each segment's model space runs from its joint at y=0 along +Y (in pixels), centred
 * on the bone; the renderer places and orients it. The sleeve uses the same boxes with the sleeve
 * UVs and is inflated by scaling when drawn.
 */
final class QingfengFirstPersonArmModel {
    private static final int TEXTURE_SIZE = 64;
    /** Upper arm length in pixels, plus overlap past the elbow so the joint never gaps. */
    static final float UPPER_ARM_PIXELS = QingfengFirstPersonArmIk.UPPER_ARM_LENGTH * 16.0F + 1.0F;
    /** Forearm starts one pixel behind the elbow ... */
    static final float FOREARM_START_PIXELS = -1.0F;
    /** ... and ends two pixels past the grip, so the handle crosses the fist. */
    static final float FOREARM_END_PIXELS = QingfengFirstPersonArmIk.FOREARM_LENGTH * 16.0F + 2.0F;
    static final float DEPTH_PIXELS = 4.0F;
    static final float SLEEVE_INFLATION_PIXELS = 0.25F;
    private static final Set<Direction> SIDES = EnumSet.of(
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
    /** In model space UP is the +Y (hand-end) face and samples the arm's bottom texture. */
    private static final Set<Direction> HAND_CAP = EnumSet.of(Direction.UP);

    private final float widthPixels;
    private final ModelPart skinUpper;
    private final ModelPart skinForearm;
    private final ModelPart sleeveUpper;
    private final ModelPart sleeveForearm;

    private QingfengFirstPersonArmModel(float widthPixels, ModelPart root) {
        this.widthPixels = widthPixels;
        this.skinUpper = root.getChild("skin_upper");
        this.skinForearm = root.getChild("skin_forearm");
        this.sleeveUpper = root.getChild("sleeve_upper");
        this.sleeveForearm = root.getChild("sleeve_forearm");
    }

    static QingfengFirstPersonArmModel create(boolean slim, HumanoidArm arm) {
        float width = slim ? 3.0F : 4.0F;
        int skinU = arm == HumanoidArm.RIGHT ? 40 : 32;
        int skinV = arm == HumanoidArm.RIGHT ? 16 : 48;
        int sleeveU = arm == HumanoidArm.RIGHT ? 40 : 48;
        int sleeveV = arm == HumanoidArm.RIGHT ? 32 : 48;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        addSegments(root, "skin", width, skinU, skinV);
        addSegments(root, "sleeve", width, sleeveU, sleeveV);
        return new QingfengFirstPersonArmModel(
                width, LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE).bakeRoot());
    }

    private static void addSegments(PartDefinition root, String prefix, float width, int u, int v) {
        float minX = -width / 2.0F;
        float minZ = -DEPTH_PIXELS / 2.0F;
        // Upper arm: the top of the skin's arm strip (side faces start at v + depth).
        root.addOrReplaceChild(
                prefix + "_upper",
                CubeListBuilder.create()
                        .texOffs(u, v)
                        .addBox(minX, 0.0F, minZ, width, UPPER_ARM_PIXELS, DEPTH_PIXELS, SIDES),
                PartPose.ZERO);
        // Forearm and fist: the bottom eight pixels of the strip, plus the hand's end cap.
        float forearmLength = FOREARM_END_PIXELS - FOREARM_START_PIXELS;
        int forearmV = v + (int) (12.0F - forearmLength);
        root.addOrReplaceChild(
                prefix + "_forearm",
                CubeListBuilder.create()
                        .texOffs(u, forearmV)
                        .addBox(minX, FOREARM_START_PIXELS, minZ, width, forearmLength, DEPTH_PIXELS, SIDES)
                        .texOffs(u, v)
                        .addBox(minX, FOREARM_START_PIXELS, minZ, width, forearmLength, DEPTH_PIXELS, HAND_CAP),
                PartPose.ZERO);
    }

    float widthPixels() {
        return widthPixels;
    }

    ModelPart upper(boolean sleeve) {
        return sleeve ? sleeveUpper : skinUpper;
    }

    ModelPart forearm(boolean sleeve) {
        return sleeve ? sleeveForearm : skinForearm;
    }
}
