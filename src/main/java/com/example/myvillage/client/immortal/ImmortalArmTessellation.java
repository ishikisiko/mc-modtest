package com.example.myvillage.client.immortal;

import com.example.myvillage.client.combat.ImmortalArmPoseBridge;
import com.example.myvillage.equipment.immortal.ImmortalMeshData;
import com.example.myvillage.equipment.immortal.ImmortalMeshData.*;
import java.util.*;

/** Barycentric subdivisions of the ORIGINAL faces, never new silhouette geometry. Shared
 * horizontal cuts avoid T-junctions; sparse loft sides must sample the nonlinear elbow warp. */
final class ImmortalArmTessellation {
    private static final List<Float> CUTS=cuts();
    private static List<Float> cuts() {
        SortedSet<Float> cuts=new TreeSet<>();
        // Half a source skin pixel, only over the nonlinear shoulder/elbow/wrist interval.
        for(float y=ImmortalArmPoseBridge.SHOULDER_Y;y<=ImmortalArmPoseBridge.WRIST_Y+1;y+=.5f)cuts.add(y);
        for(float y:new float[]{ImmortalArmPoseBridge.ELBOW_Y-1,ImmortalArmPoseBridge.ELBOW_Y+1,
                ImmortalArmPoseBridge.WRIST_Y-1,ImmortalArmPoseBridge.WRIST_Y,ImmortalArmPoseBridge.WRIST_Y+1})cuts.add(y);
        return List.copyOf(cuts);
    }
    static Map<Part,Part> parts(ImmortalMeshData data) {
        Map<Part,Part> result=new IdentityHashMap<>();
        for(Profile profile:data.profiles().values())for(Part part:profile.parts())
            if(part.bone().equals("right_arm")||part.bone().equals("left_arm"))result.put(part,split(part));
        return Collections.unmodifiableMap(result);
    }
    static Part split(Part part) {
        List<Vertex> vertices=new ArrayList<>();List<Integer> indices=new ArrayList<>();
        for(int i=0;i<part.indices().size();i+=3) {
            List<Vertex> rest=List.of(part.vertices().get(part.indices().get(i)),
                    part.vertices().get(part.indices().get(i+1)),part.vertices().get(part.indices().get(i+2)));
            for(float y:CUTS) {
                float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY;
                for(Vertex v:rest){lo=Math.min(lo,v.y());hi=Math.max(hi,v.y());}
                if(lo<y&&hi>y){emit(clip(rest,y,true),vertices,indices);rest=clip(rest,y,false);}
            }
            emit(rest,vertices,indices);
        }
        return new Part(part.id(),part.slot(),part.bone(),part.material(),part.side(),List.copyOf(vertices),List.copyOf(indices));
    }
    private static void emit(List<Vertex> polygon,List<Vertex> out,List<Integer> indices) {
        if(polygon.size()<3)return;
        int start=out.size();out.addAll(polygon);
        for(int i=1;i<polygon.size()-1;i++) {
            Vertex a=polygon.getFirst(),b=polygon.get(i),c=polygon.get(i+1);
            double ux=b.x()-a.x(),uy=b.y()-a.y(),uz=b.z()-a.z();
            double vx=c.x()-a.x(),vy=c.y()-a.y(),vz=c.z()-a.z();
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            if(nx*nx+ny*ny+nz*nz<1e-20)continue;
            indices.add(start);indices.add(start+i);indices.add(start+i+1);
        }
    }
    private static List<Vertex> clip(List<Vertex> polygon,float y,boolean below) {
        List<Vertex> out=new ArrayList<>();Vertex previous=polygon.getLast();
        boolean previousIn=below?previous.y()<=y:previous.y()>=y;
        for(Vertex current:polygon) {
            boolean in=below?current.y()<=y:current.y()>=y;
            if(in!=previousIn)out.add(intersection(previous,current,y));
            if(in)out.add(current);
            previous=current;previousIn=in;
        }
        return out;
    }
    private static Vertex intersection(Vertex a,Vertex b,float y) {
        // Canonical endpoint order gives identical shared-edge coordinates in adjacent faces.
        if(a.y()>b.y()){Vertex swap=a;a=b;b=swap;}
        if(y==a.y())return a;if(y==b.y())return b;
        float t=(y-a.y())/(b.y()-a.y());
        float nx=mix(a.nx(),b.nx(),t),ny=mix(a.ny(),b.ny(),t),nz=mix(a.nz(),b.nz(),t);
        float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(length<1e-6f){nx=a.nx();ny=a.ny();nz=a.nz();length=1;}
        return new Vertex(mix(a.x(),b.x(),t),y,mix(a.z(),b.z(),t),mix(a.u(),b.u(),t),mix(a.v(),b.v(),t),nx/length,ny/length,nz/length);
    }
    private static float mix(float a,float b,float t){return a+(b-a)*t;}
    private ImmortalArmTessellation() { }
}
