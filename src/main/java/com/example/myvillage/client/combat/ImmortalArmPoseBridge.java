package com.example.myvillage.client.combat;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Read-only snapshots of the existing solve; never solves or changes a weapon/skin transform. */
public final class ImmortalArmPoseBridge {
    // Skin strip rows, including the existing fist's overlap behind its wrist.
    public static final float SHOULDER_Y = -2;
    public static final float ELBOW_Y = SHOULDER_Y + FirstPersonArmModel.UPPER_ARM_TEXTURE_PIXELS;
    public static final float WRIST_Y = SHOULDER_Y + FirstPersonArmModel.FIST_FIRST_ROW
            + FirstPersonArmIk.FIST_OVERLAP_PIXELS;
    public record ArmPose(Vector3f shoulder, Vector3f elbow, Vector3f wrist,
            Quaternionf upper, Quaternionf forearm, Quaternionf fist, float thickness) {
        public ArmPose {
            shoulder = new Vector3f(shoulder); elbow = new Vector3f(elbow); wrist = new Vector3f(wrist);
            upper = new Quaternionf(upper); forearm = new Quaternionf(forearm); fist = new Quaternionf(fist);
        }
        @Override public Vector3f shoulder() { return new Vector3f(shoulder); }
        @Override public Vector3f elbow() { return new Vector3f(elbow); }
        @Override public Vector3f wrist() { return new Vector3f(wrist); }
        @Override public Quaternionf upper() { return new Quaternionf(upper); }
        @Override public Quaternionf forearm() { return new Quaternionf(forearm); }
        @Override public Quaternionf fist() { return new Quaternionf(fist); }
    }

    static ArmPose snapshot(FirstPersonArmIk.Solution solved, FirstPersonSwing.Arm rig) {
        return new ArmPose(solved.shoulder(), solved.elbow(), solved.wrist(), solved.upperArmRotation(),
                solved.forearmRotation(), solved.fistRotation(), rig.thickness());
    }

    /** Same eligibility as the weapon pass, with no remembered frame that could survive unequip/reload. */
    public static boolean ownsArm(AbstractClientPlayer player, HumanoidArm arm, float partialTick) {
        if (!(player instanceof LocalPlayer local) || player.isInvisible() || local.isScoping()) return false;
        var swing = FirstPersonSwingResources.forStack(player.getMainHandItem());
        var pose = FirstPersonWeaponAnimator.displayedPose(local, partialTick);
        if (swing.isEmpty() || pose.isEmpty()) return false;
        if (arm == player.getMainArm()) return true;
        var rig = swing.get().swing();
        return player.getOffhandItem().isEmpty() && rig.rig().offHand() != null
                && (rig.rig().offHand().free()
                    || rig.weapon().offHandGripCenter().isPresent() && pose.get().offHandHold() > 0);
    }

    private ImmortalArmPoseBridge() { }
}
