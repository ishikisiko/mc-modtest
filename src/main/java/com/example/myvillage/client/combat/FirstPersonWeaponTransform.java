package com.example.myvillage.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Places the held weapon for one {@link FirstPersonSwing.Pose}. The left hand mirrors the right
 * across the view's vertical plane. The same chain also yields the grip frame the first-person
 * arm holds, so the weapon, its trail and the arm all read one pose.
 *
 * <p>The weapon model's own first-person display transform is undone rather than compensated with
 * measured constants: {@link #apply} multiplies by the inverse of the baked
 * {@code firstperson_*} transform, then maps the geometry contract's {@code grip_center} onto the
 * grip frame origin (model +Y along the weapon, +X flat normal, +Z edge), scaled by the rig's
 * {@code weapon_scale}. Editing the model's display values therefore never moves the grip.
 */
final class FirstPersonWeaponTransform {
    static final float EQUIP_DROP = 0.60F;
    private static final float SINGULAR_DETERMINANT = 1.0E-9F;

    private FirstPersonWeaponTransform() {
    }

    /**
     * Moves {@code poseStack} into the grip frame: the origin is the hand's grip point, local +Y is
     * the weapon axis (toward the tip), local +X the weapon's flat normal and local +Z its edge.
     */
    static void applyGripFrame(
            PoseStack poseStack,
            HumanoidArm arm,
            float equipProgress,
            FirstPersonSwing.Rig rig,
            FirstPersonSwing.Pose pose) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.translate(
                side * (rig.shoulderX() + pose.x()),
                rig.shoulderY() + pose.y() - equipProgress * EQUIP_DROP,
                rig.shoulderZ() + pose.z());
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * pose.plane()));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * pose.sweep()));
        poseStack.translate(0.0F, 0.0F, -pose.reach());
        poseStack.mulPose(Axis.YP.rotationDegrees(side * pose.lead()));
        poseStack.mulPose(Axis.XP.rotationDegrees(pose.lift()));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * pose.twist()));
    }

    /**
     * Places the item for vanilla's item pass: after this, the renderer's own display transform and
     * {@code translate(-0.5)} land the model's {@code grip_center} on the grip frame origin.
     *
     * @param display the baked model's first-person display transform for this hand
     */
    static void apply(
            PoseStack poseStack,
            HumanoidArm arm,
            float equipProgress,
            FirstPersonSwing swing,
            FirstPersonSwing.Pose pose,
            Matrix4f display) {
        applyGripFrame(poseStack, arm, equipProgress, swing.rig(), pose);
        poseStack.mulPose(itemToGrip(swing.weapon(), swing.rig().weaponScale(), display));
    }

    /**
     * Grip-frame matrix for the item pass: {@code scale * translate(0.5 - grip / 16) * display^-1}.
     * A singular display (a zero scale hides the item anyway) is treated as the identity.
     */
    static Matrix4f itemToGrip(WeaponGeometry weapon, float weaponScale, Matrix4f display) {
        Vector3f grip = weapon.gripCenter().div(16.0F);
        Matrix4f result = new Matrix4f()
                .scale(weaponScale)
                .translate(0.5F - grip.x, 0.5F - grip.y, 0.5F - grip.z);
        if (Math.abs(display.determinant()) > SINGULAR_DETERMINANT) {
            result.mul(new Matrix4f(display).invert());
        }
        return result;
    }

    /**
     * Where a paired weapon's second (see {@link PairedWeapons}) sits on the free off hand: item quad
     * coordinates (blocks, the model's 16 px cube as 0..1) to hand-render space. The off fist wears
     * it as the main fist wears the weapon at {@code grip_diagonal} 90 and no grip roll: model +X on
     * the palm, +Y from the wrist to the knuckles, -Z toward the thumb, the contract's
     * {@code grip_center} on the off fist's grip point, scaled by {@code weapon_scale} times the off
     * arm's thickness over the main arm's (the fist it wraps is that much smaller or larger). The
     * off fist is solved as a right hand and reflected for a right main arm, and so is this
     * placement: for a right main arm the determinant is negative and the model is mirrored, a left
     * gauntlet on the left hand.
     */
    static Matrix4f pairedItem(HumanoidArm mainArm, FirstPersonSwing swing, FirstPersonArmIk.Solution offArm) {
        boolean mirror = mainArm == HumanoidArm.RIGHT;
        FirstPersonArmIk.Solution asRight = mirror ? offArm.mirrored() : offArm;
        Vector3f thumb = asRight.fistRotation().transform(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f hand = asRight.fistRotation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f palm = asRight.fistRotation().transform(new Vector3f(0.0F, 0.0F, 1.0F));
        FirstPersonSwing.Rig rig = swing.rig();
        float scale = rig.weaponScale() * rig.offArm().thickness() / rig.arm().thickness();
        Vector3f grip = swing.weapon().gripCenter().div(16.0F);
        Matrix4f placed = new Matrix4f()
                .translate(asRight.grip())
                .mul(new Matrix4f(new Matrix3f(palm, hand, thumb.negate())))
                .scale(scale)
                .translate(-grip.x, -grip.y, -grip.z);
        return mirror ? new Matrix4f().scale(-1.0F, 1.0F, 1.0F).mul(placed) : placed;
    }

    /** The grip frame (see {@link #applyGripFrame}) relative to the hand-render pose stack. */
    static Matrix4f gripFrame(
            HumanoidArm arm,
            float equipProgress,
            FirstPersonSwing.Rig rig,
            FirstPersonSwing.Pose pose) {
        PoseStack poseStack = new PoseStack();
        applyGripFrame(poseStack, arm, equipProgress, rig, pose);
        return new Matrix4f(poseStack.last().pose());
    }

    /** A weapon model point (item-model pixels) in hand-render space for one pose. */
    static Vector3f weaponPoint(
            HumanoidArm arm,
            float equipProgress,
            FirstPersonSwing swing,
            FirstPersonSwing.Pose pose,
            Vector3f modelPixels) {
        return gripFrame(arm, equipProgress, swing.rig(), pose).transformPosition(
                swing.weapon().toGrip(modelPixels, swing.rig().weaponScale()));
    }

    /** The swing pivot (rig shoulder plus the pose's body offset), mirrored for the left hand. */
    static Vector3f pivot(
            HumanoidArm arm,
            float equipProgress,
            FirstPersonSwing.Rig rig,
            FirstPersonSwing.Pose pose) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        return new Vector3f(
                side * (rig.shoulderX() + pose.x()),
                rig.shoulderY() + pose.y() - equipProgress * EQUIP_DROP,
                rig.shoulderZ() + pose.z());
    }
}
