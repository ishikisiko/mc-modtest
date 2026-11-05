package com.example.myvillage.worldplan.mine;

import com.example.myvillage.worldplan.WorldPlanRules;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;

/** 只保存种子和算法参数；连接段之外没有作者指定的洞室、矿脉或分支坐标。 */
public final class StarterMineCavePlan {
    public record Branch(long seed,double yaw,double pitch,double thickness,int length) {}
    public record Ore(int x,int y,int z,String block) {}
    public record Access(int x,int z,int feet2) {public int floorY(){return Math.floorDiv(feet2-1,2);}}
    public final long seed;
    public final int endZ,floorY,radius,coreAirBlocks;
    public final double heightScale,centerY;
    public final List<Branch> branches;
    public final List<Access> access;
    public final List<Ore> ores;
    public final List<MineAccessLighting.Torch> torches;
    public final String hash;
    private final Map<Long,Access> accessIndex;
    public StarterMineCavePlan(long seed,StarterMineDefinition def){
        this.seed=seed;var c=def.caves;var random=new Random(seed);
        endZ=c.endZ.sample(random);floorY=c.floorY.sample(random);radius=c.roomRadius.sample(random);heightScale=c.roomHeight.sample(random);
        centerY=floorY-1-c.floorLevel*radius*heightScale;
        List<Branch> generated=new ArrayList<>();int count=c.branches.sample(random);
        double offset=random.nextDouble()*Math.PI*2;
        for(int i=0;i<count;i++)generated.add(new Branch(random.nextLong(),offset+i*Math.PI*2/count,c.branchPitch.sample(random),c.branchThickness.sample(random),c.branchLength.sample(random)));
        branches=List.copyOf(generated);
        Map<Long,Access> path=new TreeMap<>();
        for(int z=def.entry.z();z<=endZ;z++){
            int feet=z<=c.flatEndZ?def.groundY*2:def.groundY*2+(int)Math.round((floorY-def.groundY)*2*(z-c.flatEndZ)/(double)(endZ-c.flatEndZ));
            for(int dx=-c.connectionRadius;dx<=c.connectionRadius;dx++)put(path,new Access(def.entry.x()+dx,z,feet));
        }
        int landing=0;
        for(int dz=-c.landingRadius;dz<=c.landingRadius;dz++)for(int dx=-c.landingRadius;dx<=c.landingRadius;dx++)if(dx*dx+dz*dz<=c.landingRadius*c.landingRadius){
            int x=def.entry.x()+dx,z=endZ+c.landingRadius-1+dz;
            // 入口来向继续保留半格坡道，避免落脚处反向抬高通道。
            if(!path.containsKey(key(x,z)))put(path,new Access(x,z,floorY*2));landing++;
        }
        if(landing<c.minimumLanding)throw new IllegalArgumentException("洞穴入口落脚面积不足");
        access=List.copyOf(path.values());accessIndex=Map.copyOf(path);
        torches=MineAccessLighting.plan(def,access,endZ-radius);
        for(var p:access){
            for(int[] d:new int[][]{{1,0},{0,1}}){var other=at(p.x()+d[0],p.z()+d[1]);if(other!=null&&Math.abs(p.feet2()-other.feet2())>1)throw new IllegalArgumentException("洞穴连接段出现不可步行台阶");}
            if(p.x()<c.margin||p.x()>=def.width-c.margin||p.z()<def.entry.z()||p.z()>=def.depth-c.margin||p.floorY()-c.floorDepth<c.minY)throw new IllegalArgumentException("洞穴通道越过生成预算");
        }
        int volume=0;
        for(int z=endZ-radius;z<=endZ+radius;z++)for(int x=def.entry.x()-radius;x<=def.entry.x()+radius;x++)for(int y=c.minY;y<def.height;y++)if(coreAir(def,x,y,z)){var p=at(x,z);if(p==null||y>p.floorY()||y<p.floorY()-c.floorDepth+1)volume++;}
        List<Ore> candidates=new ArrayList<>();
        for(int z=c.flatEndZ+2;z<=endZ-radius;z++)for(int side:new int[]{-1,1})for(int dy=0;dy<c.liningHeight;dy++){
            var p=at(def.entry.x(),z);candidates.add(new Ore(def.entry.x()+side*(c.connectionRadius+1),(int)Math.ceil(p.feet2()/2.0)+dy,z,""));
        }
        Collections.shuffle(candidates,random);int required=c.coal+c.iron+c.copper;
        if(candidates.size()<required)throw new IllegalArgumentException("入洞通道缺少可采掘的真实岩壁");
        List<Ore> minerals=new ArrayList<>();
        for(int i=0;i<required;i++){var p=candidates.get(i);minerals.add(new Ore(p.x(),p.y(),p.z(),i<c.coal?"minecraft:coal_ore":i<c.coal+c.iron?"minecraft:iron_ore":"minecraft:copper_ore"));}
        ores=List.copyOf(minerals);
        coreAirBlocks=volume;if(volume<c.minimumAir)throw new IllegalArgumentException("保证洞体的实际空腔不足："+volume);
        hash=WorldPlanRules.sha256(manifest().toString()+c.json);
    }
    private static void put(Map<Long,Access> out,Access p){out.put(key(p.x(),p.z()),p);}
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    public Access at(int x,int z){return accessIndex.get(key(x,z));}
    public boolean coreAir(StarterMineDefinition def,int x,int y,int z){
        var c=def.caves;if(x<c.margin||z<c.margin||x>=def.width-c.margin||z>=def.depth-c.margin||y<c.minY||y>def.top(x,z)-c.cover)return false;
        double dx=(x-def.entry.x())/(double)radius,dz=(z-endZ)/(double)radius,dy=(y-.5-centerY)/(radius*heightScale);
        return dy>c.floorLevel&&dx*dx+dy*dy+dz*dz<1;
    }
    public boolean accessReserved(StarterMineDefinition def,int x,int y,int z){
        var p=at(x,z);return p!=null&&y>=p.floorY()-def.caves.floorDepth+1&&y<=Math.ceil(p.feet2()/2.0)+def.caves.headroom-1||lining(def,x,y,z);
    }
    public boolean lining(StarterMineDefinition def,int x,int y,int z){
        var c=def.caves;if(z<c.flatEndZ+2||z>endZ-radius||Math.abs(x-def.entry.x())!=c.connectionRadius+1)return false;
        var p=at(def.entry.x(),z);int first=(int)Math.ceil(p.feet2()/2.0);return y>=first&&y<first+c.liningHeight;
    }
    public JsonObject manifest(){
        var out=new JsonObject();out.addProperty("seed",seed);out.addProperty("end_z",endZ);out.addProperty("floor_y",floorY);out.addProperty("room_radius",radius);out.addProperty("height_scale",heightScale);out.addProperty("center_y",centerY);
        out.addProperty("guaranteed_core_air_blocks",coreAirBlocks);var values=new JsonArray();
        for(var p:branches){var row=new JsonObject();row.addProperty("seed",p.seed());row.addProperty("yaw",p.yaw());row.addProperty("pitch",p.pitch());row.addProperty("thickness",p.thickness());row.addProperty("length",p.length());values.add(row);}out.add("branches",values);
        var path=new JsonArray();for(var p:access){var row=new JsonArray();row.add(p.x());row.add(p.z());row.add(p.feet2());path.add(row);}out.add("access",path);var minerals=new JsonArray();for(var p:ores){var row=new JsonObject();row.addProperty("x",p.x());row.addProperty("y",p.y());row.addProperty("z",p.z());row.addProperty("block",p.block());minerals.add(row);}out.add("ores",minerals);
        var lights=new JsonArray();for(var p:torches){var row=new JsonObject();row.addProperty("x",p.x());row.addProperty("y",p.y());row.addProperty("z",p.z());row.addProperty("facing",p.facing().getName());lights.add(row);}out.add("torches",lights);return out;
    }
    public static long seed(long world,int x,int z,long salt){long n=world^salt^((long)x<<32)^(z&0xffffffffL);n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;n=(n^(n>>>27))*0x94d049bb133111ebL;return n^(n>>>31);}
}
