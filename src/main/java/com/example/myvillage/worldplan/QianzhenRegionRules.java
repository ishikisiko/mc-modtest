package com.example.myvillage.worldplan;

import com.google.gson.JsonObject;
import java.util.List;

/** Frozen siting and resource identity of the actual six Chapter 1 building sites. */
public final class QianzhenRegionRules {
    public record Building(String id,String name,int x,int y,int z) {}
    public final String id,resourceHash,layoutHash,sceneVersion,host;
    public final int size,height,margin,attempts,searchRadius,structureBuffer,maxRelief,minAboveSea,maxSurface,salt;
    public final int entryX,entryY,entryZ;
    public final List<String> biomes;
    public final List<Building> buildings;
    private final int[] sampleFractions;
    public QianzhenRegionRules(JsonObject r){
        if(r==null)throw new IllegalArgumentException("Missing frozen Chapter 1 settlement");
        id=text(r,"id");if(!id.equals("qianzhen_region_v1"))throw new IllegalArgumentException("Unknown Chapter 1 region");
        resourceHash=digest(r,"resource_sha256");layoutHash=digest(r,"layout_hash");sceneVersion=text(r,"scene_version");
        size=integer(r,"size",16,512);height=integer(r,"height",1,128);margin=integer(r,"margin",16,128);
        if(size%16!=0||margin%16!=0)throw new IllegalArgumentException("Chapter 1 region must align to chunks");
        var entry=r.getAsJsonArray("entry");if(entry==null||entry.size()!=3)throw new IllegalArgumentException("Chapter entry");
        entryX=bounded(entry.get(0),0,size-1);entryY=bounded(entry.get(1),1,height-1);entryZ=bounded(entry.get(2),0,size-1);
        var siting=r.getAsJsonObject("siting");host=text(siting,"host");
        attempts=integer(siting,"attempts",1,1024);searchRadius=integer(siting,"search_radius",128,2000);
        structureBuffer=integer(siting,"structure_buffer",0,256);maxRelief=integer(siting,"max_relief",1,64);
        minAboveSea=integer(siting,"min_above_sea",1,32);maxSurface=integer(siting,"max_surface",64,200);
        salt=integer(siting,"salt",0,Integer.MAX_VALUE);
        var fractions=siting.getAsJsonArray("sample_fractions");if(fractions==null||fractions.size()!=3)throw new IllegalArgumentException("Chapter sample fractions");
        sampleFractions=new int[3];for(int i=0;i<3;i++)sampleFractions[i]=bounded(fractions.get(i),-1,1);
        if(sampleFractions[0]!=-1||sampleFractions[1]!=0||sampleFractions[2]!=1)throw new IllegalArgumentException("Chapter footprint samples must include edges and centre");
        var names=new java.util.ArrayList<String>();
        for(var b:siting.getAsJsonArray("biomes")){String name=b.getAsString();if(!name.matches("minecraft:[a-z0-9_]+"))throw new IllegalArgumentException("Chapter biome");names.add(name);}
        if(names.isEmpty()||names.size()>32)throw new IllegalArgumentException("Chapter biomes");biomes=List.copyOf(names);
        var sites=new java.util.ArrayList<Building>();var ids=new java.util.HashSet<String>();
        for(var item:r.getAsJsonArray("sites")){
            var site=item.getAsJsonObject();String siteId=text(site,"id");var point=site.getAsJsonArray("entry");
            if(!ids.add(siteId)||point==null||point.size()!=3)throw new IllegalArgumentException("Duplicate or missing Chapter building entry");
            sites.add(new Building(siteId,text(site,"display_name"),bounded(point.get(0),0,size-1),bounded(point.get(1),1,height-1),bounded(point.get(2),0,size-1)));
        }
        if(!ids.equals(java.util.Set.of("relay_inn","apothecary","canal_gardens","old_relay","spring_array","initiation_court")))throw new IllegalArgumentException("The settlement must contain the actual six Chapter 1 buildings");
        buildings=List.copyOf(sites);
    }
    public int[] sampleFractions(){return sampleFractions.clone();}
    public static int integer(JsonObject r,String key,int low,int high){return bounded(r.get(key),low,high);}
    private static int bounded(com.google.gson.JsonElement e,int low,int high){
        if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected chapter integer");
        double d=e.getAsDouble();if(!Double.isFinite(d)||d!=Math.rint(d)||d<low||d>high)throw new IllegalArgumentException("Chapter number out of bounds");return (int)d;
    }
    public static String text(JsonObject r,String key){
        var e=r==null?null:r.get(key);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString()||e.getAsString().isBlank())throw new IllegalArgumentException("Missing chapter field "+key);return e.getAsString();
    }
    private static String digest(JsonObject r,String key){String s=text(r,key);if(!s.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Chapter hash "+key);return s;}
}
