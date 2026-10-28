package com.example.myvillage.client.cultivation.motion;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeditationMotionTest {
    private final MeditationMotion motion=MeditationMotion.bundled();
    @Test void standingSurfaceIsExactlyTheOriginalForBothSkinWidths() {
        for(boolean slim:new boolean[]{false,true})for(String bone:motion.limbs().keySet()) {
            var warp=motion.warp(bone,0,slim);
            for(float y=-3;y<=14;y+=.25f) {
                Vector3f p=warp.position(1.2f,y,-2.3f);
                assertEquals(1.2f,p.x,1e-5);assertEquals(y,p.y,1e-5);assertEquals(-2.3f,p.z,1e-5);
            }
        }
    }
    @Test void jointsRemainContinuousAndNormalsFiniteThroughEntryAndExit() {
        for(float weight=0;weight<=1;weight+=.05f)for(String bone:motion.limbs().keySet()) {
            var warp=motion.warp(bone,weight,false);
            for(float y=-2;y<=12;y+=.25f) {
                Vector3f a=warp.position(2,y,-2),b=warp.position(2,y+.001f,-2);
                assertTrue(a.isFinite());assertTrue(a.distance(b)<.02f,bone+" at "+y);
                Vector3f n=warp.normal(2,y,-2,0,0,-1);
                assertTrue(n.isFinite());assertEquals(1,n.length(),1e-5);
            }
        }
    }
    @Test void wholeMotionKeepsBareFeetOnGroundAndFitsVanillaHeight() {
        for(float weight=0;weight<=1;weight+=.025f) {
            var pose=MeditationPose.sample(motion,weight,0,false);
            float bottom=Float.NEGATIVE_INFINITY;
            for(String leg:new String[]{"left_leg","right_leg"})for(float y=0;y<=12;y+=.5f)
                for(float x:new float[]{-2,2})for(float z:new float[]{-2,2})
                    bottom=Math.max(bottom,pose.drop()+12+pose.limbs().get(leg).position(x,y,z).y);
            assertEquals(24,bottom,1e-4);
            // The corner of a slightly tilted foot needs a subpixel lift before the pelvis descends.
            assertTrue(pose.drop()>=-1&&pose.drop()<12);
        }
    }
    @Test void seatedHandsMeetInFrontAndFeetCrossWithoutStretchingTheCenterlineBones() {
        var pose=MeditationPose.sample(motion,1,0,false);
        var right=pose.limbs().get("right_arm");var left=pose.limbs().get("left_arm");
        Vector3f rh=right.position(-1,10,0).add(-5,2+pose.drop(),0);
        Vector3f lh=left.position(1,10,0).add(5,2+pose.drop(),0);
        assertTrue(rh.z< -4 && lh.z< -4);
        assertTrue(Math.abs(rh.x-lh.x)<5);
        for(String leg:new String[]{"right_leg","left_leg"}) {
            var warp=pose.limbs().get(leg);
            assertEquals(6,warp.position(0,0,0).distance(warp.position(0,6,0)),1e-4);
            assertEquals(6,warp.position(0,6,0).distance(warp.position(0,12,0)),1e-4);
        }
    }
}
