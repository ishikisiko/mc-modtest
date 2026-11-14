package com.example.myvillage.qianji;

import com.example.myvillage.manor.ManorCatalog;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 总舵构件由作者几何生成，材质、模型与碰撞共用同一目录。 */
public final class QianjiCatalog {
    public record Material(String id,String name,String role,boolean collides,boolean opaque,String sound,int light) { }
    public record Bank(String id,List<ManorCatalog.Profile> profiles,List<Integer> lights,Map<Integer,ProceduralBookcaseOutline> bookcases) {
        public ManorCatalog.Profile profile(int shape){return profiles.get(shape);}
    }
    public final List<Material> materials;
    public final List<Bank> banks;
    public final float strength,resistance;
    public final JsonObject layout;
    private QianjiCatalog(JsonObject root) {
        if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("千机阁构件目录格式错误");
        strength=root.get("strength").getAsFloat();resistance=root.get("explosion_resistance").getAsFloat();
        layout=root.getAsJsonObject("layout").deepCopy();
        List<Material> mm=new ArrayList<>();List<Bank> bb=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(var e:root.getAsJsonArray("materials")){
            var m=e.getAsJsonObject();String id=m.get("id").getAsString();if(!ids.add(id))throw new IllegalArgumentException("千机阁材质重复");
            mm.add(new Material(id,m.get("name").getAsString(),m.get("role").getAsString(),m.get("collides").getAsBoolean(),m.get("opaque").getAsBoolean(),m.get("sound").getAsString(),m.get("light_level").getAsInt()));
        }
        for(var e:root.getAsJsonArray("banks")){
            var b=e.getAsJsonObject();String id=b.get("id").getAsString();if(!ids.add(id))throw new IllegalArgumentException("千机阁构件重复");
            List<ManorCatalog.Profile> profiles=new ArrayList<>();List<Integer> lights=new ArrayList<>();Map<Integer,ProceduralBookcaseOutline> bookcases=new HashMap<>();
            for(var p:b.getAsJsonArray("profiles")){var v=p.getAsJsonObject();if(v.getAsJsonArray("boxes").asList().stream().anyMatch(box->box.getAsJsonArray().get(6).getAsString().equals(ProceduralBooks.rules().markerRole())))bookcases.put(profiles.size(),new ProceduralBookcaseOutline(v));profiles.add(ManorCatalog.Profile.fromJson(v));int light=v.get("light_level").getAsInt();if(light<0||light>15)throw new IllegalArgumentException("千机阁光照越界");lights.add(light);}
            if(profiles.size()!=32)throw new IllegalArgumentException("千机阁构件状态不齐全");bb.add(new Bank(id,List.copyOf(profiles),List.copyOf(lights),Map.copyOf(bookcases)));
        }
        if(mm.isEmpty()||bb.isEmpty()||bb.size()>2048)throw new IllegalArgumentException("千机阁构件规模无效");materials=List.copyOf(mm);banks=List.copyOf(bb);
    }
    private static final class Holder {static final QianjiCatalog INSTANCE=read();}
    public static QianjiCatalog get(){return Holder.INSTANCE;}
    private static QianjiCatalog read(){
        try(var in=QianjiCatalog.class.getResourceAsStream("/data/myvillage/qianji/components/catalog.json")){
            if(in==null)throw new IllegalStateException("缺少千机阁构件目录");return new QianjiCatalog(JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
