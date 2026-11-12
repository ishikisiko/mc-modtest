package com.example.myvillage.client.qianji;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.Vec3;

final class QianjiAtmosphereMesh {
    record Face(Vec3[] points,int color,int layer) { }
    final List<Face> faces;final List<Vec3[]> edges;final List<Vec3[]> routes,links;final Vec3[] river;
    private QianjiAtmosphereMesh(JsonObject root){
        List<Face> ff=new ArrayList<>();for(var e:root.getAsJsonArray("faces")){var f=e.getAsJsonObject();var c=f.getAsJsonArray("c");Vec3[] p=vectors(f.getAsJsonArray("p"));if(p.length!=4)throw new IllegalArgumentException("千机阁异象面无效");ff.add(new Face(p,c.get(0).getAsInt()<<16|c.get(1).getAsInt()<<8|c.get(2).getAsInt(),f.get("layer").getAsInt()));}faces=List.copyOf(ff);
        edges=lists(root.getAsJsonArray("edges"));routes=lists(root.getAsJsonArray("routes"));links=lists(root.getAsJsonArray("links"));river=vectors(root.getAsJsonArray("river"));
        if(faces.size()>16000||routes.size()!=3||links.size()!=4)throw new IllegalArgumentException("千机阁动效网格规模无效");
    }
    static Vec3 point(JsonArray a){if(a.size()<3)throw new IllegalArgumentException("动效坐标缺失");double x=a.get(0).getAsDouble(),y=a.get(1).getAsDouble(),z=a.get(2).getAsDouble();if(!Double.isFinite(x+y+z))throw new IllegalArgumentException("动效坐标非有限值");return new Vec3(x,y,z);}
    static Vec3[] vectors(JsonArray a){Vec3[] out=new Vec3[a.size()];for(int i=0;i<a.size();i++)out[i]=point(a.get(i).getAsJsonArray());return out;}
    private static List<Vec3[]> lists(JsonArray a){List<Vec3[]> out=new ArrayList<>();for(var e:a)out.add(vectors(e.getAsJsonArray()));return List.copyOf(out);}
    static Vec3 sample(Vec3[] path,double u){double p=Math.clamp(u,0,1)*(path.length-1);int i=Math.min(path.length-2,(int)p);return path[i].lerp(path[i+1],p-i);}
    private static final class Holder {static final QianjiAtmosphereMesh INSTANCE=read();}
    static QianjiAtmosphereMesh get(){return Holder.INSTANCE;}
    private static QianjiAtmosphereMesh read(){try(var in=QianjiAtmosphereMesh.class.getResourceAsStream("/data/myvillage/qianji/atmosphere_mesh.json")){if(in==null)throw new IllegalStateException("缺少千机阁动效网格");return new QianjiAtmosphereMesh(JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());}catch(java.io.IOException e){throw new IllegalStateException(e);}}
}
