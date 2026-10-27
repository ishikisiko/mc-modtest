package com.example.myvillage.client.forging;

import com.example.myvillage.block.entity.ForgingDingBlockEntity;
import com.example.myvillage.forging.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import java.util.List;

/** Shared 3D workpiece, jade ribbons and gold seal glyphs; reads only server snapshots. */
public final class ForgingDingRenderer implements BlockEntityRenderer<ForgingDingBlockEntity> {
    private static final ResourceLocation RIBBON=id("infusion_ribbon"),RUNES=id("infusion_runes");
    private static final int FULL_BRIGHT=0xF000F0;
    private final ItemRenderer itemRenderer;
    public ForgingDingRenderer(BlockEntityRendererProvider.Context context){itemRenderer=context.getItemRenderer();}
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("myvillage","textures/effect/"+name+".png");}
    @Override public void render(ForgingDingBlockEntity block,float partialTick,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var item=block.displayItem();var level=block.getLevel();if(item.isEmpty()||level==null)return;
        var rules=InfusionVisuals.get();var craft=WorkshopEquipment.craft(item);if(craft.isEmpty())return;
        var display=rules.items().get(craft.get().recipe());if(display==null)return;
        boolean running=block.visuallyRunning();double progress=running?block.visualProgress(partialTick):1;
        double height=rules.height(progress),spin=rules.spin_degrees()*progress;
        pose.pushPose();pose.translate(.5,0,.5);
        // Emit each RenderType completely before requesting another buffer (ItemRenderer also switches buffers).
        if(running){
            var out=buffers.getBuffer(RenderType.entityTranslucent(RIBBON));
            double fade=rules.visibility(progress),contract=rules.contraction(progress);
            for(int stream=0;stream<rules.streams();stream++)for(int segment=0;segment<rules.segments();segment++){
                double t0=segment/(double)rules.segments(),t1=(segment+1)/(double)rules.segments();
                double[] a=rules.stream(progress,t0,stream),b=rules.stream(progress,t1,stream);
                strip(pose,out,a,b,rules.ribbon_width(),rules.stream_color(),fade*rules.stream_alpha(),overlay);
                strip(pose,out,a,b,rules.core_width(),rules.core_color(),fade,overlay);
            }
            ring(pose,out,rules.hearth_height(),rules.hearth_radius(),rules.ring_width(),rules.rune_color(),fade*.8,overlay,rules.segments());
            ring(pose,out,height,rules.ring_radius()*(1-contract),rules.ring_width(),rules.rune_color(),fade,overlay,rules.segments());
            var runes=buffers.getBuffer(RenderType.entityTranslucent(RUNES));
            for(int index=0;index<rules.rune_count();index++){
                double angle=index*Math.PI*2/rules.rune_count()-Math.toRadians(spin),radius=rules.ring_radius()*(1-contract);
                double cx=Math.cos(angle)*radius,cz=Math.sin(angle)*radius,dx=-Math.sin(angle)*rules.rune_size()/2,dz=Math.cos(angle)*rules.rune_size()/2;
                double cy=height+rules.rune_size()/2;
                quad(pose,runes,new double[][]{{cx-dx,cy-rules.rune_size()/2,cz-dz},{cx+dx,cy-rules.rune_size()/2,cz+dz},{cx+dx,cy+rules.rune_size()/2,cz+dz},{cx-dx,cy+rules.rune_size()/2,cz-dz}},
                        rules.rune_color(),fade*rules.rune_alpha(),index/8f,(index+1)/8f,overlay);
            }
        }
        pose.translate(0,height,0);pose.mulPose(Axis.YP.rotationDegrees((float)spin));pose.mulPose(Axis.ZP.rotationDegrees((float)display.rotate_z()));
        pose.scale((float)display.scale(),(float)display.scale(),(float)display.scale());
        pose.translate(.5-display.center().get(0),.5-display.center().get(1),.5-display.center().get(2));
        itemRenderer.renderStatic(item,ItemDisplayContext.NONE,FULL_BRIGHT,overlay,pose,buffers,level,block.getBlockPos().hashCode());
        pose.popPose();
    }
    private static void strip(PoseStack pose,VertexConsumer out,double[] a,double[] b,double width,List<Integer> color,double alpha,int overlay){
        // Two crossed strips retain volume from all viewpoints, without a camera-facing screen overlay.
        double dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2],horizontal=Math.hypot(dx,dz);
        double nx=horizontal>1e-9?-dz/horizontal:1,nz=horizontal>1e-9?dx/horizontal:0;
        double length=Math.sqrt(dx*dx+dy*dy+dz*dz),ux=length>1e-9?-dy*nz/length:0,uy=length>1e-9?(dz*nx-dx*nz)/length:1,uz=length>1e-9?dy*nx/length:0;
        band(pose,out,a,b,new double[]{nx*width/2,0,nz*width/2},color,alpha,overlay);
        band(pose,out,a,b,new double[]{ux*width/2,uy*width/2,uz*width/2},color,alpha,overlay);
    }
    private static void band(PoseStack pose,VertexConsumer out,double[] a,double[] b,double[] n,List<Integer> color,double alpha,int overlay){
        quad(pose,out,new double[][]{{a[0]-n[0],a[1]-n[1],a[2]-n[2]},{b[0]-n[0],b[1]-n[1],b[2]-n[2]},{b[0]+n[0],b[1]+n[1],b[2]+n[2]},{a[0]+n[0],a[1]+n[1],a[2]+n[2]}},color,alpha,0,1,overlay);
    }
    private static void ring(PoseStack pose,VertexConsumer out,double y,double r,double width,List<Integer> color,double alpha,int overlay,int segments){
        for(int i=0;i<segments;i++){
            double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
            quad(pose,out,new double[][]{{Math.cos(a)*(r-width/2),y,Math.sin(a)*(r-width/2)},{Math.cos(b)*(r-width/2),y,Math.sin(b)*(r-width/2)},{Math.cos(b)*(r+width/2),y,Math.sin(b)*(r+width/2)},{Math.cos(a)*(r+width/2),y,Math.sin(a)*(r+width/2)}},color,alpha,0,1,overlay);
        }
    }
    private static void quad(PoseStack pose,VertexConsumer out,double[][] p,List<Integer> color,double alpha,float u0,float u1,int overlay){
        int a=(int)(Math.max(0,Math.min(1,alpha))*255);
        for(int i=0;i<4;i++)out.addVertex(pose.last(),(float)p[i][0],(float)p[i][1],(float)p[i][2]).setColor(color.get(0),color.get(1),color.get(2),a)
                .setUv(i==1||i==2?u1:u0,i>=2?0:1).setOverlay(overlay).setLight(FULL_BRIGHT).setNormal(pose.last(),0,1,0);
    }
    @Override public AABB getRenderBoundingBox(ForgingDingBlockEntity block){return new AABB(block.getBlockPos()).inflate(1,2,1);}
    @Override public int getViewDistance(){return InfusionVisuals.get().view_distance();}
}
