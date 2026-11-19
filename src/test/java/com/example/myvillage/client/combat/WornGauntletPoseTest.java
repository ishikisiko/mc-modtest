package com.example.myvillage.client.combat;

import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WornGauntletPoseTest {
    private static final Vector3f GRIP = new Vector3f(8, 8, 8);
    private static final float EPS = 1.0E-6F;

    @Test
    void keepsTheApprovedFistFaceFlushWithBothStockArmWidths() {
        for (HumanoidArm side : HumanoidArm.values()) {
            float sign = side == HumanoidArm.RIGHT ? 1 : -1;
            for (boolean slim : new boolean[]{false, true}) {
                Matrix4f fit = WornGauntletPose.itemOnArm(side, slim, GRIP);
                // Asset's dorsal contact (x=4) and hand end (y=12) are on the
                // exact sides of the original PlayerModel skin cuboid.
                Vector3f palmEdge = fit.transformPosition(new Vector3f(4 / 16.0F, 12 / 16.0F, .5F));
                assertEquals(-sign * (slim ? 2 : 3) / 16.0F, palmEdge.x, EPS);
                assertEquals(10 / 16.0F, palmEdge.y, EPS);
                assertEquals(0, palmEdge.z, EPS);
                assertEquals(sign, Math.signum(fit.determinant()), EPS);
            }
        }
    }

    @Test
    void weaponScaleSizesAllThreeArmAxesEvenly() {
        Matrix4f grip = new Matrix4f().translation(.27F, -.11F, -.62F).rotateX(1.1F).rotateY(.6F);
        for (HumanoidArm side : HumanoidArm.values()) {
            float sign = side == HumanoidArm.RIGHT ? 1 : -1;
            for (boolean slim : new boolean[]{false, true}) {
                Matrix4f frame = WornGauntletPose.armAtGrip(grip, .3F, side, slim);
                Vector3f hand = frame.transformPosition(new Vector3f(-sign * (slim ? .5F : 1) / 16, .5F, 0));
                assertEquals(0, hand.distance(grip.getTranslation(new Vector3f())), EPS);
                Vector3f scales = frame.getScale(new Vector3f());
                assertEquals(0, scales.distance(new Vector3f(.6F, .6F, .6F)), EPS);
            }
        }
    }

    @Test
    void onlyTheBakedItemIsMirroredOnTheOffHand() {
        Matrix4f grip = new Matrix4f().translation(-.22F, -.18F, -.41F).rotateZ(-.37F).rotateY(.18F);
        for (HumanoidArm arm : HumanoidArm.values()) {
            float sign = arm == HumanoidArm.RIGHT ? 1 : -1;
            Matrix4f item = new Matrix4f(grip).scale(sign * .29F, .29F, .29F).translate(-.5F, -.5F, -.5F);
            Matrix4f posed = WornGauntletPose.armAtOffHand(item, GRIP, arm, false);
            assertTrue(posed.determinant() > 0);
            Matrix4f refitted = posed.mul(WornGauntletPose.itemOnArm(arm, false, GRIP));
            assertTrue(item.equals(refitted, EPS), () -> item + "\n" + refitted);
        }
    }
}
