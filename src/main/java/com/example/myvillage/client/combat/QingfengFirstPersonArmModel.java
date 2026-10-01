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
 * Three box segments cut from the player skin (upper arm, forearm, fist), for one arm and one
 * skin width. Boxes are authored in skin pixels along +Y from their joint and centred on the bone;
 * the renderer places, orients and stretches them. The arm strip's twelve rows are shared out
 * shoulder-to-hand: upper arm rows 0-5, forearm rows 4-8, fist rows 8-11 plus the hand's end cap,
 * so the fist carries the skin's hand. The sleeve uses the same boxes with the sleeve UVs and is
 * inflated by scaling when drawn.
 */
final class QingfengFirstPersonArmModel {
    private static final int TEXTURE_SIZE = 64;
    static final float UPPER_ARM_TEXTURE_PIXELS = 6.0F;
    static final int FOREARM_FIRST_ROW = 4;
    static final float FOREARM_TEXTURE_PIXELS = 5.0F;
    static final int FIST_FIRST_ROW = 8;
    static final float DEPTH_PIXELS = QingfengFirstPersonArmIk.DEPTH_PIXELS;
    static final float SLEEVE_INFLATION_PIXELS = 0.25F;
    private static final Set<Direction> SIDES = EnumSet.of(
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
    /** In model space UP is the +Y (knuckle-end) face and samples the arm's bottom texture. */
    private static final Set<Direction> HAND_CAP = EnumSet.of(Direction.UP);
    /**
     * The fist is wider than the forearm, so its wrist end (DOWN, -Y) shows around the forearm when
     * the wrist bends; it is closed with the hand rows of the strip rather than left open.
     */
    private static final Set<Direction> WRIST_CAP = EnumSet.of(Direction.DOWN);
    /** A DOWN face samples (u + depth, v)..(u + depth + width, v + depth): offset so that is the hand. */
    static final int WRIST_CAP_ROW = 12;
    /** The wrist cap sits a little inside the fist's end so it never shares a plane or edge with anything. */
    static final float WRIST_CAP_INSET_PIXELS = 0.1F;

    private final float widthPixels;
    private final ModelPart skinUpper;
    private final ModelPart skinForearm;
    private final ModelPart skinFist;
    private final ModelPart sleeveUpper;
    private final ModelPart sleeveForearm;
    private final ModelPart sleeveFist;

    private QingfengFirstPersonArmModel(float widthPixels, ModelPart root) {
        this.widthPixels = widthPixels;
        this.skinUpper = root.getChild("skin_upper");
        this.skinForearm = root.getChild("skin_forearm");
        this.skinFist = root.getChild("skin_fist");
        this.sleeveUpper = root.getChild("sleeve_upper");
        this.sleeveForearm = root.getChild("sleeve_forearm");
        this.sleeveFist = root.getChild("sleeve_fist");
    }

    static QingfengFirstPersonArmModel create(boolean slim, HumanoidArm arm) {
        float width = slim ? 3.0F : 4.0F;
        int skinU = arm == HumanoidArm.RIGHT ? 40 : 32;
        int skinV = arm == HumanoidArm.RIGHT ? 16 : 48;
        int sleeveU = arm == HumanoidArm.RIGHT ? 40 : 48;
        int sleeveV = arm == HumanoidArm.RIGHT ? 32 : 48;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        addSegments(root, "skin", width, skinU, skinV, true);
        addSegments(root, "sleeve", width, sleeveU, sleeveV, false);
        return new QingfengFirstPersonArmModel(
                width, LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE).bakeRoot());
    }

    private static void addSegments(PartDefinition root, String prefix, float width, int u, int v, boolean skin) {
        float minX = -width / 2.0F;
        float minZ = -DEPTH_PIXELS / 2.0F;
        // A box with texOffs(u, v + row) samples arm-strip rows row.. on its sides.
        root.addOrReplaceChild(
                prefix + "_upper",
                CubeListBuilder.create()
                        .texOffs(u, v)
                        .addBox(minX, 0.0F, minZ, width, UPPER_ARM_TEXTURE_PIXELS, DEPTH_PIXELS, SIDES),
                PartPose.ZERO);
        root.addOrReplaceChild(
                prefix + "_forearm",
                CubeListBuilder.create()
                        .texOffs(u, v + FOREARM_FIRST_ROW)
                        .addBox(minX, 0.0F, minZ, width, FOREARM_TEXTURE_PIXELS, DEPTH_PIXELS, SIDES),
                PartPose.ZERO);
        float fistStart = -QingfengFirstPersonArmIk.FIST_OVERLAP_PIXELS;
        float fistLength = QingfengFirstPersonArmIk.FIST_LENGTH_PIXELS;
        CubeListBuilder fist = CubeListBuilder.create()
                .texOffs(u, v + FIST_FIRST_ROW)
                .addBox(minX, fistStart, minZ, width, fistLength, DEPTH_PIXELS, SIDES)
                .texOffs(u, v)
                .addBox(minX, fistStart, minZ, width, fistLength, DEPTH_PIXELS, HAND_CAP);
        if (skin) {
            // Only the skin layer closes the wrist end; a translucent sleeve cap would flicker.
            float inset = WRIST_CAP_INSET_PIXELS;
            fist.texOffs(u, v + WRIST_CAP_ROW)
                    .addBox(minX + inset, fistStart + inset, minZ + inset,
                            width - 2.0F * inset, fistLength - 2.0F * inset, DEPTH_PIXELS - 2.0F * inset, WRIST_CAP);
        }
        root.addOrReplaceChild(prefix + "_fist", fist, PartPose.ZERO);
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

    ModelPart fist(boolean sleeve) {
        return sleeve ? sleeveFist : skinFist;
    }
}
