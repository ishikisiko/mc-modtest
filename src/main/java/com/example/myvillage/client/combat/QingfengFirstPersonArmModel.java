package com.example.myvillage.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.HumanoidArm;

import java.util.Set;

final class QingfengFirstPersonArmModel {
    private static final int TEXTURE_SIZE = 64;
    static final float CONNECTOR_LENGTH_UNITS = 12.0F / 16.0F;
    private static final float SLEEVE_DEFORMATION = 0.25F;
    private static final Set<Direction> SEGMENT_SIDE_FACES = Set.of(
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST);
    private static final Set<Direction> HAND_CAP_FACE = Set.of(Direction.DOWN);

    private final Chain skin;
    private final Chain sleeve;
    private final ModelPart skinConnector;
    private final ModelPart sleeveConnector;
    private final float gripAnchorX;
    private final float gripAnchorY;

    private QingfengFirstPersonArmModel(
            Chain skin,
            Chain sleeve,
            ModelPart skinConnector,
            ModelPart sleeveConnector,
            float gripAnchorX,
            float gripAnchorY) {
        this.skin = skin;
        this.sleeve = sleeve;
        this.skinConnector = skinConnector;
        this.sleeveConnector = sleeveConnector;
        this.gripAnchorX = gripAnchorX;
        this.gripAnchorY = gripAnchorY;
    }

    static QingfengFirstPersonArmModel create(boolean slim, HumanoidArm arm) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float rootX = arm == HumanoidArm.RIGHT ? -5.0F : 5.0F;
        float rootY = slim ? 2.5F : 2.0F;
        float width = slim ? 3.0F : 4.0F;
        float minX = arm == HumanoidArm.RIGHT
                ? (slim ? -2.0F : -3.0F)
                : -1.0F;
        int skinU = arm == HumanoidArm.RIGHT ? 40 : 32;
        int skinV = arm == HumanoidArm.RIGHT ? 16 : 48;
        int sleeveU = arm == HumanoidArm.RIGHT ? 40 : 48;
        int sleeveV = arm == HumanoidArm.RIGHT ? 32 : 48;

        addChain(
                root,
                "skin",
                rootX,
                rootY,
                minX,
                width,
                skinU,
                skinV,
                0.0F);
        addConnector(root, "skin", width, skinU, skinV, 0.0F);
        addChain(
                root,
                "sleeve",
                rootX,
                rootY,
                minX,
                width,
                sleeveU,
                sleeveV,
                SLEEVE_DEFORMATION);
        addConnector(
                root,
                "sleeve",
                width,
                sleeveU,
                sleeveV,
                SLEEVE_DEFORMATION);

        ModelPart bakedRoot = LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE)
                .bakeRoot();
        return new QingfengFirstPersonArmModel(
                Chain.from(bakedRoot, "skin"),
                Chain.from(bakedRoot, "sleeve"),
                bakedRoot.getChild("skin_connector"),
                bakedRoot.getChild("sleeve_connector"),
                rootX,
                rootY + FirstPersonArmPose.GRIP_ENDPOINT_Y);
    }

    float gripAnchorX() {
        return gripAnchorX;
    }

    float gripAnchorY() {
        return gripAnchorY;
    }

    float rootY() {
        return gripAnchorY - FirstPersonArmPose.GRIP_ENDPOINT_Y;
    }

    void applyPose(FirstPersonArmPose.Pose pose, HumanoidArm arm) {
        skin.apply(pose.joints(), arm);
        sleeve.apply(pose.joints(), arm);
    }

    void renderSkin(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay) {
        skin.upperArm.render(poseStack, consumer, packedLight, packedOverlay);
    }

    void renderSleeve(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay) {
        sleeve.upperArm.render(poseStack, consumer, packedLight, packedOverlay);
    }

    void renderSkinConnector(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay) {
        skinConnector.render(poseStack, consumer, packedLight, packedOverlay);
    }

    void renderSleeveConnector(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay) {
        sleeveConnector.render(poseStack, consumer, packedLight, packedOverlay);
    }

    private static void addChain(
            PartDefinition root,
            String prefix,
            float rootX,
            float rootY,
            float minX,
            float width,
            int textureU,
            int textureV,
            float inflation) {
        PartDefinition upperArm = root.addOrReplaceChild(
                prefix + "_upper_arm",
                CubeListBuilder.create(),
                PartPose.offset(rootX, rootY, 0.0F));
        PartDefinition forearm = upperArm.addOrReplaceChild(
                prefix + "_forearm",
                segment(
                        textureU,
                        textureV + 5,
                        minX,
                        0.0F,
                        width,
                        FirstPersonArmPose.FOREARM_LENGTH,
                        inflation,
                        SEGMENT_SIDE_FACES),
                PartPose.offset(0.0F, FirstPersonArmPose.SHOULDER_TO_ELBOW_Y, 0.0F));
        forearm.addOrReplaceChild(
                prefix + "_hand",
                handSegment(
                        textureU,
                        textureV + 10,
                        textureV,
                        minX,
                        0.0F,
                        width,
                        FirstPersonArmPose.HAND_LENGTH,
                        inflation),
                PartPose.offset(0.0F, FirstPersonArmPose.FOREARM_LENGTH, 0.0F));
    }

    private static void addConnector(
            PartDefinition root,
            String prefix,
            float width,
            int textureU,
            int textureV,
            float inflation) {
        float connectorWidth = Math.max(2.0F, width - 1.0F);
        float connectorDepth = 2.5F;
        root.addOrReplaceChild(
                prefix + "_connector",
                CubeListBuilder.create()
                        .texOffs(textureU, textureV)
                        .addBox(
                                -connectorWidth / 2.0F - inflation,
                                -inflation,
                                -connectorDepth / 2.0F - inflation,
                                connectorWidth + inflation * 2.0F,
                                12.0F + inflation * 2.0F,
                                connectorDepth + inflation * 2.0F,
                                SEGMENT_SIDE_FACES),
                PartPose.ZERO);
    }

    private static CubeListBuilder segment(
            int textureU,
            int textureV,
            float minX,
            float minY,
            float width,
            float length,
            float inflation,
            Set<Direction> visibleFaces) {
        return CubeListBuilder.create()
                .texOffs(textureU, textureV)
                .addBox(
                        minX - inflation,
                        minY - inflation,
                        -2.0F - inflation,
                        width + inflation * 2.0F,
                        length + inflation * 2.0F,
                        4.0F + inflation * 2.0F,
                        visibleFaces);
    }

    private static CubeListBuilder handSegment(
            int textureU,
            int sideTextureV,
            int capTextureV,
            float minX,
            float minY,
            float width,
            float length,
            float inflation) {
        float inflatedMinX = minX - inflation;
        float inflatedMinY = minY - inflation;
        float inflatedWidth = width + inflation * 2.0F;
        float inflatedLength = length + inflation * 2.0F;
        float inflatedDepth = 4.0F + inflation * 2.0F;
        return CubeListBuilder.create()
                .texOffs(textureU, sideTextureV)
                .addBox(
                        inflatedMinX,
                        inflatedMinY,
                        -2.0F - inflation,
                        inflatedWidth,
                        inflatedLength,
                        inflatedDepth,
                        SEGMENT_SIDE_FACES)
                .texOffs(textureU, capTextureV)
                .addBox(
                        inflatedMinX,
                        inflatedMinY,
                        -2.0F - inflation,
                        inflatedWidth,
                        inflatedLength,
                        inflatedDepth,
                        HAND_CAP_FACE);
    }

    private record Chain(ModelPart upperArm, ModelPart forearm, ModelPart hand) {
        private static Chain from(ModelPart root, String prefix) {
            ModelPart upperArm = root.getChild(prefix + "_upper_arm");
            ModelPart forearm = upperArm.getChild(prefix + "_forearm");
            ModelPart hand = forearm.getChild(prefix + "_hand");
            return new Chain(upperArm, forearm, hand);
        }

        private void apply(FirstPersonArmPose.JointPose pose, HumanoidArm arm) {
            upperArm.resetPose();
            forearm.resetPose();
            hand.resetPose();
            float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            upperArm.setRotation(
                    radians(pose.upperPitch()),
                    radians(side * pose.upperYaw()),
                    radians(side * pose.upperRoll()));
            forearm.setRotation(
                    radians(pose.forearmPitch()),
                    radians(side * pose.forearmYaw()),
                    radians(side * pose.forearmRoll()));
            hand.setRotation(
                    radians(pose.handPitch()),
                    radians(side * pose.handYaw()),
                    radians(side * pose.handRoll()));
        }

        private static float radians(float degrees) {
            return (float) Math.toRadians(degrees);
        }
    }
}
