package com.example.myvillage.worldplan.mine;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.WorldPlanRules;
import com.example.myvillage.worldplan.roads.RoadLayout;
import com.example.myvillage.worldplan.roads.RoadPlan;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;

/** 持久保存选址和接路列；再加载只核对身份，不寻找新的矿场位置。 */
public final class StarterMineLayout {
    public record Path(int x,int z,int feet2){
        static final Codec<Path> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("x").forGetter(Path::x),Codec.INT.fieldOf("z").forGetter(Path::z),Codec.INT.fieldOf("feet2").forGetter(Path::feet2)).apply(i,Path::new));
        public int floorY(){return Math.floorDiv(feet2-1,2);}
        public long key(){return RoadPlan.Cell.key(x,z);}
    }
    private record Stored(String world,String roads,String definition,String region,int x,int y,int z,int rotation,int anchorX,int anchorZ,List<Path> path,long caveSeed,String hash){
        static final Codec<Stored> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("world_hash").forGetter(Stored::world),Codec.STRING.fieldOf("roads_hash").forGetter(Stored::roads),Codec.STRING.fieldOf("definition_hash").forGetter(Stored::definition),
            Codec.STRING.fieldOf("region").forGetter(Stored::region),Codec.INT.fieldOf("x").forGetter(Stored::x),Codec.INT.fieldOf("y").forGetter(Stored::y),Codec.INT.fieldOf("z").forGetter(Stored::z),
            Codec.INT.fieldOf("rotation").forGetter(Stored::rotation),Codec.INT.fieldOf("anchor_x").forGetter(Stored::anchorX),Codec.INT.fieldOf("anchor_z").forGetter(Stored::anchorZ),
            Path.CODEC.listOf().fieldOf("path").forGetter(Stored::path),Codec.LONG.fieldOf("cave_seed").forGetter(Stored::caveSeed),Codec.STRING.fieldOf("layout_hash").forGetter(Stored::hash)).apply(i,Stored::new));
    }
    public static final Codec<StarterMineLayout> CODEC=Stored.CODEC.comapFlatMap(s->{
        try{var layout=new StarterMineLayout(s.world,s.roads,s.definition,s.region,s.x,s.y,s.z,s.rotation,s.anchorX,s.anchorZ,s.path,s.caveSeed);if(!layout.hash.equals(s.hash))throw new IllegalArgumentException("矿场布局摘要不匹配");return DataResult.success(layout);}
        catch(IllegalArgumentException e){return DataResult.error(e::getMessage);}
    },v->new Stored(v.worldHash,v.roadsHash,v.definitionHash,v.region,v.x,v.y,v.z,v.rotation,v.anchorX,v.anchorZ,v.path,v.caveSeed,v.hash));
    public final String worldHash,roadsHash,definitionHash,region,hash;
    public final int x,y,z,rotation,anchorX,anchorZ;
    public final List<Path> path;
    public final long caveSeed;
    private final Map<Long,Path> pathIndex;

    public StarterMineLayout(String world,String roads,String definition,String region,int x,int y,int z,int rotation,int anchorX,int anchorZ,List<Path> path,long caveSeed){
        for(String value:List.of(world,roads,definition))if(!value.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("矿场身份摘要无效");
        if(!region.matches("[a-z0-9_]+")||rotation<0||rotation>3||path.size()>4096||Math.abs((long)x)>30_000_000||Math.abs((long)z)>30_000_000||y< -64||y>=320)throw new IllegalArgumentException("矿场选址数据无效");
        this.caveSeed=caveSeed;worldHash=world;roadsHash=roads;definitionHash=definition;this.region=region;this.x=x;this.y=y;this.z=z;this.rotation=rotation;this.anchorX=anchorX;this.anchorZ=anchorZ;
        Map<Long,Path> index=new TreeMap<>();for(var cell:path)if(cell.feet2< -128||cell.feet2>=640||Math.abs((long)cell.x-x)>512||Math.abs((long)cell.z-z)>512||index.putIfAbsent(cell.key(),cell)!=null)throw new IllegalArgumentException("矿场接路列重复或越界");
        this.path=List.copyOf(index.values());pathIndex=Map.copyOf(index);hash=WorldPlanRules.sha256(manifest().toString());
    }
    public int worldX(int lx,int lz){return x+switch(rotation){case 0->lx;case 1->-lz;case 2->-lx;default->lz;};}
    public int worldZ(int lx,int lz){return z+switch(rotation){case 0->lz;case 1->lx;case 2->-lz;default->-lx;};}
    public int localX(int wx,int wz){int dx=wx-x,dz=wz-z;return switch(rotation){case 0->dx;case 1->dz;case 2->-dx;default->-dz;};}
    public int localZ(int wx,int wz){int dx=wx-x,dz=wz-z;return switch(rotation){case 0->dz;case 1->-dx;case 2->-dz;default->dx;};}
    public WorldPlan.Position position(StarterMineDefinition.Point p){return new WorldPlan.Position(worldX(p.x(),p.z()),y+p.y(),worldZ(p.x(),p.z()));}
    public boolean inside(StarterMineDefinition def,int wx,int wz){int lx=localX(wx,wz),lz=localZ(wx,wz);return lx>=0&&lx<def.width&&lz>=0&&lz<def.depth;}
    public int minX(StarterMineDefinition def){return Math.min(Math.min(worldX(0,0),worldX(def.width-1,0)),Math.min(worldX(0,def.depth-1),worldX(def.width-1,def.depth-1)));}
    public int maxX(StarterMineDefinition def){return Math.max(Math.max(worldX(0,0),worldX(def.width-1,0)),Math.max(worldX(0,def.depth-1),worldX(def.width-1,def.depth-1)));}
    public int minZ(StarterMineDefinition def){return Math.min(Math.min(worldZ(0,0),worldZ(def.width-1,0)),Math.min(worldZ(0,def.depth-1),worldZ(def.width-1,def.depth-1)));}
    public int maxZ(StarterMineDefinition def){return Math.max(Math.max(worldZ(0,0),worldZ(def.width-1,0)),Math.max(worldZ(0,def.depth-1),worldZ(def.width-1,def.depth-1)));}
    public Path pathAt(int wx,int wz){return pathIndex.get(RoadPlan.Cell.key(wx,wz));}
    public int surface(WorldPlan world,StarterMineDefinition def,int wx,int wz){return def.surface(y,localX(wx,wz),localZ(wx,wz),world.surfaceY(wx,wz));}
    public boolean reserved(WorldPlan world,StarterMineDefinition def,int wx,int wy,int wz){
        if(inside(def,wx,wz))return wy>=y+def.minBlockY&&wy<=Math.max(y+def.maxBlockY,world.surfaceY(wx,wz)+def.clearance);
        var cell=pathAt(wx,wz);return cell!=null&&wy>=Math.min(world.surfaceY(wx,wz),cell.floorY())-def.foundationDepth&&wy<=Math.max(world.surfaceY(wx,wz),cell.floorY()+def.clearance);
    }
    public boolean intersects(WorldPlan world,StarterMineDefinition def,int x0,int y0,int z0,int x1,int y1,int z1){
        if(x0<=maxX(def)&&x1>=minX(def)&&z0<=maxZ(def)&&z1>=minZ(def)&&y1>=y+def.minBlockY&&y0<=y+def.height+def.placement.terrainAdjustment())return true;
        for(var p:path)if(p.x>=x0&&p.x<=x1&&p.z>=z0&&p.z<=z1&&reserved(world,def,p.x,Math.clamp(p.floorY(),y0,y1),p.z))return true;
        return false;
    }
    public void requireIdentity(WorldPlan world,RoadLayout roads,StarterMineDefinition def){
        if(!worldHash.equals(world.fingerprint())||!roadsHash.equals(roads.hash())||!definitionHash.equals(def.hash)||!region.equals(world.spawn().regionId()))throw new IllegalStateException("矿场与当前世界、道路或资源身份不一致");
        if(caveSeed!=StarterMineCavePlan.seed(world.seed(),x,z,def.caves.salt))throw new IllegalArgumentException("洞穴种子与矿场选址不一致");
        StarterMinePlanner.validate(world,roads,def,this);
    }
    public JsonObject manifest(){
        JsonObject out=new JsonObject();out.addProperty("world_hash",worldHash);out.addProperty("roads_hash",roadsHash);out.addProperty("definition_hash",definitionHash);out.addProperty("region",region);
        out.addProperty("x",x);out.addProperty("y",y);out.addProperty("z",z);out.addProperty("rotation",rotation);out.addProperty("anchor_x",anchorX);out.addProperty("anchor_z",anchorZ);out.addProperty("cave_seed",caveSeed);
        JsonArray values=new JsonArray();for(var p:path){JsonArray row=new JsonArray();row.add(p.x);row.add(p.z);row.add(p.feet2);values.add(row);}out.add("path",values);return out;
    }
}
