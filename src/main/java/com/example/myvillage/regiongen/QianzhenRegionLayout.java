package com.example.myvillage.regiongen;

import com.example.myvillage.worldplan.QianzhenRegionRules;
import com.example.myvillage.worldplan.WorldPlanRules;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Building-only view of the existing Chapter 1 scene. No story lifecycle writes. */
public final class QianzhenRegionLayout {
    public static final String RESOURCE="/data/myvillage/regiongen/qianzhen_region_v1.json";
    public record Point(int x,int z) {}
    public record Tile(int x,int z,String template,String sha256) {}
    public final String id,resourceHash,layoutHash,sceneVersion,surface,foundation;
    public final int size,height,margin,soilDepth;
    private final Map<Long,Tile> tiles;
    private static final class Holder { private static final QianzhenRegionLayout VALUE=load(); }
    public static QianzhenRegionLayout current(){return Holder.VALUE;}
    private static QianzhenRegionLayout load(){
        try(var in=QianzhenRegionLayout.class.getResourceAsStream(RESOURCE)){
            if(in==null)throw new IllegalStateException("Missing Chapter 1 settlement assets");
            return new QianzhenRegionLayout(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot load Chapter 1 settlement",e);}
    }
    public QianzhenRegionLayout(String json){
        var r=JsonParser.parseString(json).getAsJsonObject();resourceHash=WorldPlanRules.sha256(json);
        QianzhenRegionRules.integer(r,"schema",1,1);id=QianzhenRegionRules.text(r,"id");
        if(!id.equals("qianzhen_region_v1"))throw new IllegalArgumentException("Unsupported Chapter 1 settlement asset");
        layoutHash=QianzhenRegionRules.text(r,"layout_hash");sceneVersion=QianzhenRegionRules.text(r,"scene_version");
        size=QianzhenRegionRules.integer(r,"size",16,512);height=QianzhenRegionRules.integer(r,"height",1,128);
        var terrain=r.getAsJsonObject("terrain");margin=QianzhenRegionRules.integer(terrain,"margin",16,128);
        soilDepth=QianzhenRegionRules.integer(terrain,"soil_depth",1,16);
        surface=QianzhenRegionRules.text(terrain,"surface");foundation=QianzhenRegionRules.text(terrain,"foundation");
        if(size%16!=0||margin%16!=0||!layoutHash.matches("[a-f0-9]{64}")
                ||!surface.matches("minecraft:[a-z0-9_]+")||!foundation.matches("minecraft:[a-z0-9_]+"))throw new IllegalArgumentException("Invalid Chapter 1 region geometry/materials");
        Map<Long,Tile> selected=new HashMap<>();
        for(var item:r.getAsJsonArray("tiles")){
            var t=item.getAsJsonObject();int x=QianzhenRegionRules.integer(t,"x",0,size-16),z=QianzhenRegionRules.integer(t,"z",0,size-16);
            String template=QianzhenRegionRules.text(t,"template"),hash=QianzhenRegionRules.text(t,"sha256");
            if(x%16!=0||z%16!=0||!hash.matches("[a-f0-9]{64}")||!template.equals("myvillage:regiongen/"+id+"/tile_"+x/16+"_"+z/16))throw new IllegalArgumentException("Invalid Chapter 1 tile");
            if(selected.put(key(x,z),new Tile(x,z,template,hash))!=null)throw new IllegalArgumentException("Duplicate Chapter 1 tile");
        }
        if(selected.isEmpty())throw new IllegalArgumentException("Empty Chapter 1 settlement");tiles=Map.copyOf(selected);
    }
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    public Tile tile(int x,int z){return tiles.get(key(x,z));}
    public Point rotate(int x,int z,int turns){
        if(turns<0||turns>3)throw new IllegalArgumentException("Chapter 1 rotation");
        for(int i=0;i<turns;i++){int next=size-1-z;z=x;x=next;}return new Point(x,z);
    }
    public Point inverse(int x,int z,int turns){return rotate(x,z,(4-turns)&3);}
    public double weight(int x,int z){
        int distance=Math.min(Math.min(x+margin,z+margin),Math.min(size+margin-1-x,size+margin-1-z));
        double t=Math.clamp(distance/(double)margin,0,1);return t*t*(3-2*t);
    }
}
