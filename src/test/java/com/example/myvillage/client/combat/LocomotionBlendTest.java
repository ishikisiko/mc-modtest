package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.client.combat.CombatAnimationController.Lifecycle;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import org.junit.jupiter.api.Test;

final class LocomotionBlendTest {
    @Test
    void standingKeepsThePlantedStanceAndWalkingIsFullyVanilla() {
        assertEquals(0.0F, LocomotionBlend.target(0.0F));
        assertEquals(0.0F, LocomotionBlend.target(LocomotionBlend.START_SPEED));
        assertEquals(1.0F, LocomotionBlend.target(LocomotionBlend.FULL_SPEED));
        // Vanilla walking sets the limb-swing amount to about 0.8.
        assertEquals(1.0F, LocomotionBlend.target(0.8F));
        float mid = LocomotionBlend.target((LocomotionBlend.START_SPEED + LocomotionBlend.FULL_SPEED) / 2.0F);
        assertEquals(0.5F, mid, 1.0e-6F);
        assertEquals(0.0F, LocomotionBlend.target(Float.NaN));
    }

    @Test
    void blendRampsInAndOutOverFourTicks() {
        float blend = 0.0F;
        for (int tick = 0; tick < 3; tick++) {
            blend = LocomotionBlend.step(blend, 1.0F);
            assertTrue(blend < 1.0F, "no snap to vanilla on the first ticks");
        }
        blend = LocomotionBlend.step(blend, 1.0F);
        assertEquals(1.0F, blend);
        blend = LocomotionBlend.step(blend, 0.0F);
        assertEquals(0.75F, blend);
        assertEquals(0.0F, LocomotionBlend.step(0.1F, 0.0F));
    }

    @Test
    void onlyTheLegsAndTheBodyRootFollowTheWalk() {
        assertEquals(LocomotionBlend.Part.LEG, LocomotionBlend.part("right_leg"));
        assertEquals(LocomotionBlend.Part.LEG, LocomotionBlend.part("left_leg"));
        assertEquals(LocomotionBlend.Part.BODY, LocomotionBlend.part("body"));
        for (String guard : new String[] {"torso", "head", "right_arm", "left_arm", "right_item"}) {
            assertNull(LocomotionBlend.part(guard), guard + " keeps the guard");
        }
    }

    @Test
    void legsTakeTheVanillaSwingAndTheBodyKeepsItsLean() {
        PlayerAnimBone vanillaLeg = bone("right_leg", 0.0F, 0.6F, 0.0F, 0.0F);
        PlayerAnimBone stanceLeg = bone("right_leg", 0.0F, -0.49F, 0.23F, 0.0F);
        LocomotionBlend.apply(LocomotionBlend.Part.LEG, vanillaLeg, stanceLeg, 1.0F);
        assertEquals(0.6F, stanceLeg.rotX);
        assertEquals(0.0F, stanceLeg.rotY);

        PlayerAnimBone neutralBody = bone("body", 0.0F, 0.0F, 0.0F, 0.0F);
        PlayerAnimBone guardBody = bone("body", -0.97F, 0.07F, -0.26F, 0.0F);
        LocomotionBlend.apply(LocomotionBlend.Part.BODY, neutralBody, guardBody, 0.5F);
        assertEquals(-0.485F, guardBody.positionY, 1.0e-6F);
        assertEquals(-0.13F, guardBody.rotY, 1.0e-6F);
        assertEquals(0.07F, guardBody.rotX, "the forward lean is kept");
    }

    @Test
    void onlyTheGuardAndTheModeEntryHandTheLegsBack() {
        Lifecycle lifecycle = new Lifecycle();
        assertFalse(lifecycle.allowsLocomotion());
        lifecycle.startEnter();
        assertTrue(lifecycle.allowsLocomotion());
        lifecycle.idle();
        assertTrue(lifecycle.allowsLocomotion());
        lifecycle.startMove();
        assertFalse(lifecycle.allowsLocomotion(), "moves keep their authored footwork");
        lifecycle.onTriggeredFinished(100L);
        assertFalse(lifecycle.allowsLocomotion(), "the held idle after a move waits for its STOP");
        lifecycle.requestStop(100L);
        assertFalse(lifecycle.allowsLocomotion());
    }

    private static PlayerAnimBone bone(String name, float posY, float rotX, float rotY, float rotZ) {
        PlayerAnimBone bone = new PlayerAnimBone(name);
        bone.positionY = posY;
        bone.rotX = rotX;
        bone.rotY = rotY;
        bone.rotZ = rotZ;
        return bone;
    }
}
