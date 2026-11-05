package com.example.myvillage.worldplan.mine;

import com.google.gson.JsonObject;

/** 洞穴参数仅来自矿场作者输入；不保存固定洞室坐标或分支路线。 */
public final class StarterMineCaveRules {
    public record IntRange(int min,int max) { int sample(java.util.Random random){return min+random.nextInt(max-min+1);} }
    public record Range(double min,double max) { double sample(java.util.Random random){return min+random.nextDouble()*(max-min);} }
    public final JsonObject json;
    public final long salt;
    public final int flatEndZ,connectionRadius,headroom,liningHeight,floorDepth,landingRadius,minY,margin,cover,seal,minimumAir,minimumLanding;
    public final IntRange endZ,floorY,roomRadius,branches,branchLength;
    public final Range roomHeight,branchThickness,branchPitch;
    public final double floorLevel,horizontalMultiplier,verticalMultiplier;
    public final int coal,iron,copper;
    public StarterMineCaveRules(JsonObject r,StarterMineDefinition def){
        if(r==null||!StarterMineDefinition.text(r,"algorithm").equals("minecraft_cave_carver_v1"))throw new IllegalArgumentException("矿场缺少当前洞穴生成规则");
        json=r.deepCopy();salt=StarterMineDefinition.integer(r,"seed_salt",0,Integer.MAX_VALUE);
        flatEndZ=integer(r,"flat_end_z",def.entry.z()+1,def.depth/2);
        endZ=ints(r,"end_z",flatEndZ+8,def.depth-20);floorY=ints(r,"floor_y",8,def.groundY-4);
        connectionRadius=integer(r,"connection_radius",1,3);headroom=integer(r,"headroom",3,6);floorDepth=integer(r,"floor_depth",1,4);
        liningHeight=integer(r,"lining_height",2,headroom);landingRadius=integer(r,"landing_radius",3,6);roomRadius=ints(r,"room_radius",8,16);roomHeight=range(r,"room_height_scale",0.5,1.5);
        floorLevel=number(r,"floor_level",-0.9,-0.4);branches=ints(r,"branches",3,8);branchLength=ints(r,"branch_length",16,80);
        branchThickness=range(r,"branch_thickness",1.5,6);branchPitch=range(r,"branch_pitch",-0.3,0.3);
        horizontalMultiplier=number(r,"horizontal_multiplier",0.5,2);verticalMultiplier=number(r,"vertical_multiplier",0.5,2);
        minY=integer(r,"minimum_y",2,floorY.min()-floorDepth-1);margin=integer(r,"margin",2,8);cover=integer(r,"surface_cover",3,8);seal=integer(r,"fluid_seal",1,3);
        minimumAir=integer(r,"minimum_air_blocks",1000,20000);minimumLanding=integer(r,"minimum_landing_stances",25,100);
        if(def.groundY*2-floorY.min()*2>endZ.min()-flatEndZ)throw new IllegalArgumentException("入洞通道不能形成连续半格坡道");
        if(def.entry.x()-roomRadius.max()<margin||def.entry.x()+roomRadius.max()>=def.width-margin||endZ.max()+roomRadius.max()>=def.depth-margin)throw new IllegalArgumentException("保证洞体越过矿场边界");
        var ores=r.getAsJsonObject("entry_ores");coal=integer(ores,"coal",1,64);iron=integer(ores,"iron",1,64);copper=integer(ores,"copper",0,64);
    }
    private static int integer(JsonObject r,String k,int lo,int hi){return StarterMineDefinition.integer(r,k,lo,hi);}
    private static double number(JsonObject r,String k,double lo,double hi){
        var v=r.get(k);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("洞穴参数缺失："+k);
        double n=v.getAsDouble();if(!Double.isFinite(n)||n<lo||n>hi)throw new IllegalArgumentException("洞穴参数越界："+k);return n;
    }
    private static Range range(JsonObject r,String k,double lo,double hi){
        var a=r.getAsJsonArray(k);if(a==null||a.size()!=2)throw new IllegalArgumentException("洞穴参数区间缺失："+k);
        var v=new JsonObject();v.add("min",a.get(0));v.add("max",a.get(1));double min=number(v,"min",lo,hi),max=number(v,"max",min,hi);return new Range(min,max);
    }
    private static IntRange ints(JsonObject r,String k,int lo,int hi){var v=range(r,k,lo,hi);if(v.min()!=Math.rint(v.min())||v.max()!=Math.rint(v.max()))throw new IllegalArgumentException("洞穴区间必须是整数："+k);return new IntRange((int)v.min(),(int)v.max());}
}
