package com.example.myvillage.client.gathering;

import com.example.myvillage.gathering.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Low, bounded rune ribbons. Server state owns activation; no entities or global particles. */
public final class GatheringRenderer implements BlockEntityRenderer<GatheringBlockEntity> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("myvillage","textures/block/gathering_glow.png");
    public GatheringRenderer(BlockEntityRendererProvider.Context context){ }
    @Override public void render(GatheringBlockEntity core,float delta,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(!core.formed()||core.getLevel()==null)return;
        var rules=GatheringRules.get();
        light=core.running()?0xD000D0:LevelRenderer.getLightColor(core.getLevel(),core.getBlockPos().above());
        var out=buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        float t=(core.getLevel().getGameTime()+delta)/rules.flowPeriodTicks();
        float y=1.025f;
        int color=core.running()?0xC888C6A6:0x50597565;
        for(int n=0;n<8;n++){
            double a=n*Math.PI/4+Math.PI/8,b=(n+1)*Math.PI/4+Math.PI/8;
            for(double radius:new double[]{rules.ringRadius(),rules.ringRadius()-.20})
                ribbon(pose,out,.5+Math.cos(a)*radius,.5+Math.sin(a)*radius,.5+Math.cos(b)*radius,.5+Math.sin(b)*radius,y,rules.ringWidth(),color,light,overlay);
        }
        for(var node:GatheringPattern.nodes()){
            double dx=node.getX()/(double)rules.radius(),dz=node.getZ()/(double)rules.radius();
            ribbon(pose,out,.5+dx*.65,.5+dz*.65,.5+dx*(rules.radius()-.4),.5+dz*(rules.radius()-.4),y,rules.ringWidth(),color,light,overlay);
            if(core.running())for(int n=0;n<3;n++){
                double phase=(t+n/3.0)%1,dist=.65+(rules.radius()-1.05)*(1-phase);
                ribbon(pose,out,.5+dx*dist,.5+dz*dist,.5+dx*(dist+.10),.5+dz*(dist+.10),y+.012f,.055,0xDDA6D7BA,0xF000F0,overlay);
            }
        }
    }
    private static void ribbon(PoseStack pose,VertexConsumer out,double x1,double z1,double x2,double z2,float y,double width,int color,int light,int overlay){
        double length=Math.hypot(x2-x1,z2-z1),px=-(z2-z1)/length*width,pz=(x2-x1)/length*width;
        vertex(pose,out,x1-px,y,z1-pz,0,0,color,light,overlay);vertex(pose,out,x1+px,y,z1+pz,0,1,color,light,overlay);
        vertex(pose,out,x2+px,y,z2+pz,1,1,color,light,overlay);vertex(pose,out,x2-px,y,z2-pz,1,0,color,light,overlay);
    }
    private static void vertex(PoseStack pose,VertexConsumer out,double x,float y,double z,float u,float v,int color,int light,int overlay){
        out.addVertex(pose.last(),(float)x,y,(float)z).setColor(color).setUv(u,v).setOverlay(overlay).setLight(light).setNormal(pose.last(),0,1,0);
    }
    @Override public AABB getRenderBoundingBox(GatheringBlockEntity core){return new AABB(core.getBlockPos()).inflate(GatheringRules.get().radius());}
    @Override public boolean shouldRenderOffScreen(GatheringBlockEntity core){return true;}
    @Override public int getViewDistance(){return 48;}
}
