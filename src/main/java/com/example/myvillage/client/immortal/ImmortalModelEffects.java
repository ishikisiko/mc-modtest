package com.example.myvillage.client.immortal;

import com.example.myvillage.equipment.immortal.ImmortalMeshData.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;

/** Bounded immediate geometry. Tick-derived positions follow the selected actual wearing bone. */
final class ImmortalModelEffects {
    static void draw(ImmortalMeshRenderer.Wearing w,HumanoidModel<?> model,PoseStack poses,MultiBufferSource buffers,
            double seconds,int[] remaining,String onlyBone) {
        draw(w,model,poses,buffers,seconds,remaining,onlyBone,null);
    }
    static void draw(ImmortalMeshRenderer.Wearing w,HumanoidModel<?> model,PoseStack poses,MultiBufferSource buffers,
            double seconds,int[] remaining,String onlyBone,ImmortalArmDraw warp) {
        if(remaining[0]<=0)return;
        for(int flowIndex=0;flowIndex<w.mesh().data().effects().flows().size();flowIndex++) {
            Flow f=w.mesh().data().effects().flows().get(flowIndex);
            if(!f.slot().equals(w.slot())||onlyBone!=null&&!onlyBone.equals(f.bone())||warp!=null&&!warp.includes(f.bone()))continue;
            VertexConsumer out=buffers.getBuffer(ImmortalRenderTypes.EFFECTS);
            poses.pushPose();ImmortalMeshRenderer.bind(poses,model,f.bone(),warp);
            for(int i=0;i<f.count()&&remaining[0]>0;i++) {
                double t=fract(seconds/f.period()+(double)i/f.count()+flowIndex*.17);
                float fade=(float)Math.pow(Math.sin(Math.PI*t),1.3);
                Vec3 position=w.mesh().local(w.profile(),f.bone(),point(f.path(),t));
                float size=f.size()*(float)(.65+.35*Math.sin(t*Math.PI));
                float rotate=f.shape().equals("leaf")?(float)(-.4*Math.sin(t*Math.PI*2+i)):0;
                spark(poses,out,position,size,f.shape(),f.color(),f.opacity()*fade,rotate,(float)(-seconds*.2));remaining[0]--;

            }
            poses.popPose();
            if(f.trail()>0&&remaining[0]>0) {
                out=buffers.getBuffer(ImmortalRenderTypes.TRAILS);
                poses.pushPose();ImmortalMeshRenderer.bind(poses,model,f.bone(),warp);
                for(int i=0;i<f.count()&&remaining[0]>0;i++) {
                    double t=fract(seconds/f.period()+(double)i/f.count()+flowIndex*.17);
                    float fade=(float)Math.pow(Math.sin(Math.PI*t),1.3);
                    for(int k=1;k<=6&&remaining[0]>0;k++) {
                        double old=Math.max(0,t-f.trail()*k/6),nextT=Math.max(0,t-f.trail()*(k-1)/6);
                        if(old==nextT)continue;
                        Vec3 previous=w.mesh().local(w.profile(),f.bone(),point(f.path(),old)),next=w.mesh().local(w.profile(),f.bone(),point(f.path(),nextT));
                        ribbon(poses,out,previous,next,f.size()*.12f,f.color(),f.opacity()*fade*.72f*(1-(k-.5f)/6));remaining[0]--;
                    }
                }
                poses.popPose();
            }
        }
        VertexConsumer out=buffers.getBuffer(ImmortalRenderTypes.EFFECTS);
        for(Arc a:w.mesh().data().effects().arcs()) {
            if(!a.slot().equals(w.slot())||onlyBone!=null&&!onlyBone.equals(a.bone())||warp!=null&&!warp.includes(a.bone()))continue;
            double cycle=fract(seconds/a.period()-a.phase())*a.period();
            if(cycle>=a.duration())continue;
            float fade=(float)Math.pow(Math.sin(Math.PI*cycle/a.duration()),2);
            poses.pushPose();ImmortalMeshRenderer.bind(poses,model,a.bone(),warp);
            for(int i=1;i<a.points().size()&&remaining[0]>0;i++) {
                ribbon(poses,out,w.mesh().local(w.profile(),a.bone(),a.points().get(i-1)),w.mesh().local(w.profile(),a.bone(),a.points().get(i)),.055f,a.color(),a.opacity()*fade);remaining[0]--;
            }
            poses.popPose();
        }
    }
    private static double fract(double a) {return a-Math.floor(a);}
    // Arc-length interpolation matches core/effects.js, including uneven authored segments.
    private static Vec3 point(List<Vec3> path,double t) {
        double total=0;for(int i=1;i<path.size();i++)total+=distance(path.get(i-1),path.get(i));
        double remaining=t*total;
        for(int i=1;i<path.size();i++) {
            Vec3 a=path.get(i-1),b=path.get(i);double length=distance(a,b);
            if(remaining<=length||i==path.size()-1) {
                float f=length>0?(float)(remaining/length):0;
                return new Vec3(a.x()+(b.x()-a.x())*f,a.y()+(b.y()-a.y())*f,a.z()+(b.z()-a.z())*f);
            }
            remaining-=length;
        }
        return path.getFirst();
    }
    private static double distance(Vec3 a,Vec3 b) {double x=a.x()-b.x(),y=a.y()-b.y(),z=a.z()-b.z();return Math.sqrt(x*x+y*y+z*z);}
    private static void spark(PoseStack poses,VertexConsumer out,Vec3 p,float size,String shape,int rgb,float alpha,float rotate,float spin) {
        poses.pushPose();poses.translate(p.x()/16,p.y()/16,p.z()/16);
        if(rotate!=0)poses.mulPose(com.mojang.math.Axis.ZP.rotation(rotate));
        if(shape.equals("spark")) {
            poses.mulPose(com.mojang.math.Axis.YP.rotation(spin));float s=size*.5f;
            quad(poses,out,-s,-s,-s,s,-s,-s,s,s,-s,-s,s,-s,rgb,alpha);
            quad(poses,out,-s,-s,s,s,-s,s,s,s,s,-s,s,s,rgb,alpha);
            quad(poses,out,-s,-s,-s,-s,-s,s,-s,s,s,-s,s,-s,rgb,alpha);
            quad(poses,out,s,-s,-s,s,-s,s,s,s,s,s,s,-s,rgb,alpha);
            quad(poses,out,-s,-s,-s,s,-s,-s,s,-s,s,-s,-s,s,rgb,alpha);
            quad(poses,out,-s,s,-s,s,s,-s,s,s,s,-s,s,s,rgb,alpha);
        } else if(shape.equals("seal")) {
            // Hollow seal frame and inner stroke, rather than a filled luminous panel.
            float s=size,h=s*.63f;
            quad(poses,out,-s,-s,0,s,-s,0,s,-h,0,-s,-h,0,rgb,alpha);
            quad(poses,out,-s,h,0,s,h,0,s,s,0,-s,s,0,rgb,alpha);
            quad(poses,out,-s,-h,0,-h,-h,0,-h,h,0,-s,h,0,rgb,alpha);
            quad(poses,out,h,-h,0,s,-h,0,s,h,0,h,h,0,rgb,alpha);
            quad(poses,out,-s*.13f,-s*.35f,0,s*.13f,-s*.35f,0,s*.13f,s*.45f,0,-s*.13f,s*.45f,0,rgb,alpha);
            quad(poses,out,s*.13f,-s*.35f,0,s*.45f,-s*.35f,0,s*.45f,-s*.12f,0,s*.13f,-s*.12f,0,rgb,alpha);
        } else {
            float[][] points=shape.equals("star")?STAR:LEAF;
            for(int i=0;i<points.length;i++) {
                float[] a=points[i],b=points[(i+1)%points.length];
                quad(poses,out,0,0,0,a[0]*size,-a[1]*size,0,b[0]*size,-b[1]*size,0,b[0]*size,-b[1]*size,0,rgb,alpha);
            }
        }
        poses.popPose();
    }
    private static final float[][] STAR={{0,1},{.23f,.23f},{1,0},{.23f,-.23f},{0,-1},{-.23f,-.23f},{-1,0},{-.23f,.23f}};
    private static final float[][] LEAF={{0,1},{.6f,.25f},{.4f,-.6f},{0,-1},{-.4f,-.6f},{-.6f,.25f}};
    private static void ribbon(PoseStack poses,VertexConsumer out,Vec3 a,Vec3 b,float width,int rgb,float alpha) {
        float dx=b.x()-a.x(),dy=b.y()-a.y(),length=(float)Math.sqrt(dx*dx+dy*dy);
        float x=length>.0001f?-dy/length*width:width,y=length>.0001f?dx/length*width:0;
        quad(poses,out,a.x()-x,a.y()-y,a.z(),b.x()-x,b.y()-y,b.z(),b.x()+x,b.y()+y,b.z(),a.x()+x,a.y()+y,a.z(),rgb,alpha);
    }
    private static void quad(PoseStack p,VertexConsumer out,float ax,float ay,float az,float bx,float by,float bz,
            float cx,float cy,float cz,float dx,float dy,float dz,int rgb,float alpha) {
        vertex(p,out,ax,ay,az,rgb,alpha);vertex(p,out,bx,by,bz,rgb,alpha);
        vertex(p,out,cx,cy,cz,rgb,alpha);vertex(p,out,dx,dy,dz,rgb,alpha);
    }
    private static void vertex(PoseStack p,VertexConsumer out,float x,float y,float z,int rgb,float alpha) {
        out.addVertex(p.last(),x/16,y/16,z/16).setColor((rgb>>16)&255,(rgb>>8)&255,rgb&255,Math.round(alpha*255));
    }
    private ImmortalModelEffects() { }
}
