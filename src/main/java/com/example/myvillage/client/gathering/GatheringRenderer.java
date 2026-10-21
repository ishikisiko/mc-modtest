package com.example.myvillage.client.gathering;

import com.example.myvillage.gathering.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Bounded ground runes: original octagon, jade lotus loops, or purple-gold trigram rings. */
public final class GatheringRenderer implements BlockEntityRenderer<GatheringBlockEntity> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("myvillage","textures/block/gathering_glow.png");
    public GatheringRenderer(BlockEntityRendererProvider.Context context){ }
    @Override public void render(GatheringBlockEntity core,float delta,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(!core.formed()||core.getLevel()==null)return;
        var rules=GatheringRules.get();var geometry=rules.tier(core.tier());var style=GatheringAppearance.get(core.tier());
        light=core.running()?0xD000D0:LevelRenderer.getLightColor(core.getLevel(),core.getBlockPos().above());
        var out=buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        float t=(core.getLevel().getGameTime()+delta)/rules.flowPeriodTicks(),y=1.025f;
        int primary=core.running()?style.primary():style.idle(),secondary=core.running()?style.secondary():style.idle();
        double width=rules.ringWidth();
        for(int ring=0;ring<style.rings();ring++){
            double radius=geometry.ringRadius()-ring*.20;
            for(int n=0;n<style.segments();n++){
                double a=n*2*Math.PI/style.segments()+Math.PI/8,b=(n+1)*2*Math.PI/style.segments()+Math.PI/8;
                ribbon(pose,out,.5+Math.cos(a)*radius,.5+Math.sin(a)*radius,.5+Math.cos(b)*radius,.5+Math.sin(b)*radius,y,width,ring%2==0?secondary:primary,light,overlay);
            }
        }
        if(core.tier()==GatheringTier.MEDIUM){
            // Four closed petal paths, scaled with the larger cultivation area.
            for(int petal=0;petal<4;petal++)for(int n=0;n<32;n++){
                double angle=petal*Math.PI/2,a=n*Math.PI/16,b=(n+1)*Math.PI/16;
                double ra=geometry.innerRadius()*(.65+.32*Math.cos(a)),rb=geometry.innerRadius()*(.65+.32*Math.cos(b));
                double sideA=.36*geometry.innerRadius()*Math.sin(a),sideB=.36*geometry.innerRadius()*Math.sin(b);
                ribbon(pose,out,.5+Math.cos(angle)*ra-Math.sin(angle)*sideA,.5+Math.sin(angle)*ra+Math.cos(angle)*sideA,
                        .5+Math.cos(angle)*rb-Math.sin(angle)*sideB,.5+Math.sin(angle)*rb+Math.cos(angle)*sideB,y,width,primary,light,overlay);
            }
        }else if(core.tier()==GatheringTier.HIGH){
            for(int n=0;n<8;n++)for(int stroke=0;stroke<3;stroke++){
                double angle=n*Math.PI/4,dx=Math.cos(angle),dz=Math.sin(angle),radius=geometry.ringRadius()+.10+stroke*.12;
                boolean broken=((n>>stroke)&1)!=0;
                if(broken){
                    tangent(pose,out,dx,dz,radius,-.22,-.06,y,width,secondary,light,overlay);
                    tangent(pose,out,dx,dz,radius,.06,.22,y,width,secondary,light,overlay);
                }else tangent(pose,out,dx,dz,radius,-.22,.22,y,width,secondary,light,overlay);
            }
        }
        for(var node:GatheringPattern.nodes(core.tier())){
            double dx=node.getX()/(double)geometry.radius(),dz=node.getZ()/(double)geometry.radius();
            ribbon(pose,out,.5+dx*.65,.5+dz*.65,.5+dx*(geometry.radius()-.4),.5+dz*(geometry.radius()-.4),y,width,primary,light,overlay);
            if(core.running())for(int n=0;n<3;n++){
                double phase=(t+n/3.0)%1,dist=.65+(geometry.radius()-1.05)*(1-phase);
                ribbon(pose,out,.5+dx*dist,.5+dz*dist,.5+dx*(dist+.10),.5+dz*(dist+.10),y+.012f,.055,style.flow(),0xF000F0,overlay);
            }
        }
    }
    private static void tangent(PoseStack p,VertexConsumer out,double dx,double dz,double radius,double a,double b,float y,double w,int color,int light,int overlay){
        ribbon(p,out,.5+dx*radius-dz*a,.5+dz*radius+dx*a,.5+dx*radius-dz*b,.5+dz*radius+dx*b,y,w,color,light,overlay);
    }
    private static void ribbon(PoseStack pose,VertexConsumer out,double x1,double z1,double x2,double z2,float y,double width,int color,int light,int overlay){
        double length=Math.hypot(x2-x1,z2-z1);if(length<1e-9)return;
        double px=-(z2-z1)/length*width,pz=(x2-x1)/length*width;
        vertex(pose,out,x1-px,y,z1-pz,0,0,color,light,overlay);vertex(pose,out,x1+px,y,z1+pz,0,1,color,light,overlay);
        vertex(pose,out,x2+px,y,z2+pz,1,1,color,light,overlay);vertex(pose,out,x2-px,y,z2-pz,1,0,color,light,overlay);
    }
    private static void vertex(PoseStack pose,VertexConsumer out,double x,float y,double z,float u,float v,int color,int light,int overlay){
        out.addVertex(pose.last(),(float)x,y,(float)z).setColor(color).setUv(u,v).setOverlay(overlay).setLight(light).setNormal(pose.last(),0,1,0);
    }
    @Override public AABB getRenderBoundingBox(GatheringBlockEntity core){return new AABB(core.getBlockPos()).inflate(GatheringRules.get().tier(core.tier()).radius());}
    @Override public boolean shouldRenderOffScreen(GatheringBlockEntity core){return true;}
    @Override public int getViewDistance(){return 48;}
}
