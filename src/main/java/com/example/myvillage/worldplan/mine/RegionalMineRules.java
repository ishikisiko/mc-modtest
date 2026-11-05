package com.example.myvillage.worldplan.mine;

import com.google.gson.JsonObject;
import java.util.*;

/** 普通矿口只接入天然洞体；数量、搜索预算及筛选阈值均由作者配置拥有。 */
public final class RegionalMineRules {
    public final int count,salt,radius,attempts,spacing,minDepth,maxDepth,stances,span,scanRadius,scanLimit;
    public final List<Integer> ends;
    public RegionalMineRules(JsonObject r,StarterMineDefinition def){
        if(def.caves.seal>=def.caves.cover)throw new IllegalArgumentException("矿口衬砌必须留在地下覆盖层内");
        count=n(r,"per_region",1,8);salt=n(r,"seed_salt",0,Integer.MAX_VALUE);radius=n(r,"search_radius",256,8192);
        attempts=n(r,"maximum_candidates",32,8192);spacing=n(r,"minimum_spacing",128,2048);
        minDepth=n(r,"minimum_depth",8,64);maxDepth=n(r,"maximum_depth",minDepth,128);
        stances=n(r,"minimum_stances",16,256);span=n(r,"minimum_span",6,32);scanRadius=n(r,"scan_radius",span,48);scanLimit=n(r,"scan_limit",stances,1024);
        List<Integer> values=new ArrayList<>();for(var v:r.getAsJsonArray("end_z"))values.add(StarterMineDefinition.bounded(v.getAsInt(),def.caves.flatEndZ+8,256));
        if(values.isEmpty()||new HashSet<>(values).size()!=values.size()||!StarterMineDefinition.text(r,"on_candidate_exhaustion").equals("record_shortfall"))throw new IllegalArgumentException("普通矿口搜索策略不合法");ends=List.copyOf(values);
    }
    private static int n(JsonObject r,String k,int a,int b){return StarterMineDefinition.integer(r,k,a,b);}
}
