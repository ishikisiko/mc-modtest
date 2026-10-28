package com.example.myvillage.client.cultivation.motion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/** Tessellates existing faces across the joint, preserving texture coordinates and continuous clothing. */
public final class MeditationSurface {
    public record Vertex(float x,float y,float z,float u,float v,float nx,float ny,float nz) {}
    private static final Map<ModelPart.Cube,List<Vertex>> CACHE=new WeakHashMap<>();
    public static void clear(){CACHE.clear();}
    public static void cubes(List<ModelPart.Cube> cubes,PoseStack.Pose pose,VertexConsumer out,MeditationWarp warp,
            int light,int overlay,int color) {
        for(ModelPart.Cube cube:cubes)draw(CACHE.computeIfAbsent(cube,MeditationSurface::capture),pose,out,warp,light,overlay,color,false);
    }
    private static List<Vertex> capture(ModelPart.Cube cube) {
        List<Vertex> points=new ArrayList<>();
        cube.compile(new PoseStack().last(),new VertexConsumer() {
            @Override public void addVertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz) {
                points.add(new Vertex(x*16,y*16,z*16,u,v,nx,ny,nz));
            }
            @Override public VertexConsumer addVertex(float x,float y,float z){throw new UnsupportedOperationException();}
            @Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
            @Override public VertexConsumer setUv(float u,float v){return this;}
            @Override public VertexConsumer setUv1(int u,int v){return this;}
            @Override public VertexConsumer setUv2(int u,int v){return this;}
            @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
        },0,0,-1);
        List<Vertex> triangles=new ArrayList<>();
        for(int i=0;i<points.size();i+=4) {
            triangles.addAll(List.of(points.get(i),points.get(i+1),points.get(i+2),points.get(i),points.get(i+2),points.get(i+3)));
        }
        return tessellate(triangles);
    }
    /** Flat triangle list -> flat triangle list. Both skins and custom equipment use the same cuts. */
    public static List<Vertex> tessellate(List<Vertex> triangles) {
        if(triangles.size()%3!=0)throw new IllegalArgumentException("Triangle list required");
        List<Vertex> out=new ArrayList<>();
        for(int i=0;i<triangles.size();i+=3) {
            List<Vertex> rest=triangles.subList(i,i+3);
            for(float y=-3;y<=14;y+=.5f) {
                float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
                for(Vertex v:rest){min=Math.min(min,v.y);max=Math.max(max,v.y);}
                if(min<y&&max>y){emit(clip(rest,y,true),out);rest=clip(rest,y,false);}
            }
            emit(rest,out);
        }
        return List.copyOf(out);
    }
    private static void emit(List<Vertex> p,List<Vertex> out) {
        for(int i=1;i<p.size()-1;i++) {
            Vertex a=p.getFirst(),b=p.get(i),c=p.get(i+1);
            Vector3f cross=new Vector3f(b.x-a.x,b.y-a.y,b.z-a.z).cross(new Vector3f(c.x-a.x,c.y-a.y,c.z-a.z));
            if(cross.lengthSquared()>1e-12f)out.addAll(List.of(a,b,c));
        }
    }
    private static List<Vertex> clip(List<Vertex> p,float y,boolean below) {
        List<Vertex> out=new ArrayList<>();Vertex previous=p.getLast();
        boolean prev=below?previous.y<=y:previous.y>=y;
        for(Vertex current:p) {
            boolean inside=below?current.y<=y:current.y>=y;
            if(inside!=prev)out.add(intersection(previous,current,y));
            if(inside)out.add(current);
            previous=current;prev=inside;
        }
        return out;
    }
    private static Vertex intersection(Vertex a,Vertex b,float y) {
        if(a.y>b.y){Vertex swap=a;a=b;b=swap;}
        if(y==a.y)return a;if(y==b.y)return b;
        float t=(y-a.y)/(b.y-a.y);
        Vector3f n=new Vector3f(mix(a.nx,b.nx,t),mix(a.ny,b.ny,t),mix(a.nz,b.nz,t)).normalize();
        return new Vertex(mix(a.x,b.x,t),y,mix(a.z,b.z,t),mix(a.u,b.u,t),mix(a.v,b.v,t),n.x,n.y,n.z);
    }
    private static float mix(float a,float b,float t){return a+(b-a)*t;}
    public static void draw(List<Vertex> triangles,PoseStack.Pose pose,VertexConsumer out,MeditationWarp warp,
            int light,int overlay,int color,boolean glint) {
        for(int i=0;i<triangles.size();i+=3) {
            vertex(triangles.get(i),pose,out,warp,light,overlay,color,glint);
            vertex(triangles.get(i+1),pose,out,warp,light,overlay,color,glint);
            vertex(triangles.get(i+2),pose,out,warp,light,overlay,color,glint);
            vertex(triangles.get(i+2),pose,out,warp,light,overlay,color,glint);
        }
    }
    private static void vertex(Vertex v,PoseStack.Pose pose,VertexConsumer out,MeditationWarp warp,int light,int overlay,int color,boolean glint) {
        Vector3f p=warp.position(v.x,v.y,v.z);
        out.addVertex(pose,p.x/16,p.y/16,p.z/16).setUv(v.u,v.v);
        if(!glint) {
            Vector3f n=warp.normal(v.x,v.y,v.z,v.nx,v.ny,v.nz);
            out.setColor(color).setOverlay(overlay).setLight(light).setNormal(pose,n.x,n.y,n.z);
        }
    }
    private MeditationSurface(){}
}
