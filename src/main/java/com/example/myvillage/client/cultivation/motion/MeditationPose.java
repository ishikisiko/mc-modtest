package com.example.myvillage.client.cultivation.motion;

import java.util.Map;
import java.util.HashMap;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;

/** Frame-local pose. Floor fit is solved from the same deformed leg surface that is rendered. */
public record MeditationPose(float weight,float drop,float breath,Map<String,MeditationWarp> limbs) {
    public static MeditationPose sample(MeditationMotion motion,float weight,double age,boolean slim) {
        Map<String,MeditationWarp> limbs=new HashMap<>();
        for(String bone:motion.limbs().keySet())limbs.put(bone,motion.warp(bone,weight,slim));
        float bottom=Float.NEGATIVE_INFINITY;
        for(String bone:new String[]{"right_leg","left_leg"}) {
            MeditationWarp w=limbs.get(bone);
            for(float y=0;y<=12;y+=.5f)for(float x:new float[]{-2,2})for(float z:new float[]{-2,2})
                bottom=Math.max(bottom,12+w.position(x,y,z).y);
        }
        float breath=(float)Math.sin(age*Math.PI*2/motion.breathTicks())*motion.breathPixels()*weight;
        return new MeditationPose(weight,24-bottom,breath,Map.copyOf(limbs));
    }
    public static void clear(PlayerModel<?> m) {
        for(ModelPart p:new ModelPart[]{m.head,m.hat,m.body,m.jacket,m.rightArm,m.leftArm,m.rightSleeve,m.leftSleeve,
                m.rightLeg,m.leftLeg,m.rightPants,m.leftPants})((MeditationPart)(Object)p).myvillage$meditationWarp(null);
    }
    public void apply(PlayerModel<?> m,MeditationMotion motion) {
        for(ModelPart p:new ModelPart[]{m.head,m.body,m.rightArm,m.leftArm,m.rightLeg,m.leftLeg})p.resetPose();
        m.body.y+=drop-breath;m.head.y+=drop-breath;m.head.xRot=motion.headPitch()*weight;
        set(m.rightArm,"right_arm",drop-breath);set(m.leftArm,"left_arm",drop-breath);
        set(m.rightLeg,"right_leg",drop);set(m.leftLeg,"left_leg",drop);
        m.hat.copyFrom(m.head);m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);m.leftPants.copyFrom(m.leftLeg);
    }
    private void set(ModelPart p,String name,float shift) {
        p.y+=shift;((MeditationPart)(Object)p).myvillage$meditationWarp(limbs.get(name));
    }
}
