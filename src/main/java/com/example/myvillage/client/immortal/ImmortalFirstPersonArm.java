package com.example.myvillage.client.immortal;

import com.example.myvillage.client.combat.ImmortalArmPoseBridge.ArmPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;

/** Adapts every existing material/effect pass through one immediate local-space vertex sink. */
public final class ImmortalFirstPersonArm {
    public static void render(AbstractClientPlayer player,HumanoidArm arm,ArmPose solved,
            ArmPose offHand,PoseStack poses,MultiBufferSource buffers,int light,float partialTick) {
        if(player.isInvisible()||player.isSpectator())return;
        var pieces=ImmortalArmorLayer.wearing(player);
        if(pieces.isEmpty())return;
        boolean slim=player.getSkin().model()==PlayerSkin.Model.SLIM;
        java.util.Map<String,ImmortalArmDeformation> arms=new java.util.HashMap<>();
        arms.put(arm==HumanoidArm.RIGHT?"right_arm":"left_arm",new ImmortalArmDeformation(solved,slim,arm==HumanoidArm.RIGHT));
        if(offHand!=null)arms.put(arm==HumanoidArm.RIGHT?"left_arm":"right_arm",new ImmortalArmDeformation(offHand,slim,arm!=HumanoidArm.RIGHT));
        var warp=new ImmortalArmDraw(arms);
        ImmortalMeshRenderer.draw(pieces,null,poses,buffers,light,(player.tickCount+partialTick)/20.0,
                ImmortalArmorLayer.effects(player),ImmortalClientConfig.elements(),
                null,warp);
    }

    static VertexConsumer consumer(VertexConsumer target,ImmortalArmDeformation warp,PoseStack.Pose outer) {
        return consumer(target,()->warp,outer);
    }
    static VertexConsumer consumer(VertexConsumer target,ImmortalArmDraw arms,PoseStack.Pose outer) {
        return consumer(target,arms::current,outer);
    }
    private static VertexConsumer consumer(VertexConsumer target,java.util.function.Supplier<ImmortalArmDeformation> warp,PoseStack.Pose outer) {
        return new VertexConsumer() {
            private float x,y,z;
            @Override public VertexConsumer addVertex(float px,float py,float pz) {
                x=px*16;y=py*16;z=pz*16;
                Vector3f p=warp.get().position(x,y,z);
                target.addVertex(outer,p.x,p.y,p.z);return this;
            }
            @Override public VertexConsumer setColor(int r,int g,int b,int a) {target.setColor(r,g,b,a);return this;}
            @Override public VertexConsumer setUv(float u,float v) {target.setUv(u,v);return this;}
            @Override public VertexConsumer setUv1(int u,int v) {target.setUv1(u,v);return this;}
            @Override public VertexConsumer setUv2(int u,int v) {target.setUv2(u,v);return this;}
            @Override public VertexConsumer setNormal(float nx,float ny,float nz) {
                Vector3f n=warp.get().normal(x,y,z,nx,ny,nz);
                target.setNormal(outer,n.x,n.y,n.z);return this;
            }
        };
    }
    private ImmortalFirstPersonArm() { }
}
