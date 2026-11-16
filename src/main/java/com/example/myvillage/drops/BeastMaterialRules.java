package com.example.myvillage.drops;

import com.example.myvillage.resources.BuiltinResources;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Generated catalogue authority for registered material identities and exact recycle prices. */
public final class BeastMaterialRules {
    public record Variant(String id,String family,String realm,String rarity,int price,int color) {}
    private final String fallbackRealm;
    private final List<String> rarities;
    private final List<Variant> variants;
    private final Map<String,Variant> byId;
    private final Map<List<String>,Variant> combinations;

    private BeastMaterialRules(String fallback,List<String> rarities,List<Variant> variants) {
        this.fallbackRealm=fallback;this.rarities=List.copyOf(rarities);this.variants=List.copyOf(variants);
        Map<String,Variant> ids=new LinkedHashMap<>();Map<List<String>,Variant> keys=new HashMap<>();
        for(var v:variants) {
            if(ids.putIfAbsent(v.id(),v)!=null||keys.putIfAbsent(List.of(v.family(),v.realm(),v.rarity()),v)!=null)
                throw new IllegalArgumentException("重复妖兽材料："+v.id());
        }
        byId=Collections.unmodifiableMap(ids);combinations=Map.copyOf(keys);
    }
    public static BeastMaterialRules bundled(){return Holder.VALUE;}
    private static final class Holder {
        static final BeastMaterialRules VALUE=load();
        private static BeastMaterialRules load() {
            try(var stream=BuiltinResources.open("data/yuanxian/beast_materials/materials.json")) {
                if(stream==null)throw new IllegalStateException("缺少妖兽材料目录");
                return parse(new String(stream.readNBytes(256*1024+1),StandardCharsets.UTF_8));
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
    }
    public static BeastMaterialRules parse(String text) {
        if(text.length()>256*1024)throw new IllegalArgumentException("妖兽材料目录过大");
        var root=DropRules.strict(text);fields(root,"schema","fallback_realm","rarities","variants");
        if(root.get("schema").getAsBigDecimal().intValueExact()!=1)throw new IllegalArgumentException("妖兽材料目录格式错误");
        String fallback=root.get("fallback_realm").getAsString();
        List<String> rarities=new ArrayList<>();for(var r:root.getAsJsonArray("rarities"))rarities.add(r.getAsString());
        if(!rarities.equals(List.of("common","uncommon","rare","legendary")))throw new IllegalArgumentException("妖兽材料稀有度错误");
        List<Variant> variants=new ArrayList<>();
        for(var value:root.getAsJsonArray("variants")) {
            var v=value.getAsJsonObject();fields(v,"id","family","realm","rarity","price","color");
            String id=v.get("id").getAsString(),family=v.get("family").getAsString(),realm=v.get("realm").getAsString(),rarity=v.get("rarity").getAsString();
            int price=v.get("price").getAsBigDecimal().intValueExact(),color=v.get("color").getAsBigDecimal().intValueExact();
            if(!id.matches("yuanxian:[a-z0-9_]{1,96}")||!Set.of("beast_hide","beast_bone","beast_core").contains(family)
                    ||!realm.matches("yuanxian:[a-z0-9_]+")||!rarities.contains(rarity)||price<1||price>100_000||color<0||color>0xFFFFFF)
                throw new IllegalArgumentException("妖兽材料条目错误："+id);
            variants.add(new Variant(id,family,realm,rarity,price,color));
        }
        if(variants.isEmpty()||variants.size()>128||variants.stream().noneMatch(v->v.realm().equals(fallback)))
            throw new IllegalArgumentException("妖兽材料数量或默认境界错误");
        return new BeastMaterialRules(fallback,rarities,variants);
    }
    private static void fields(JsonObject o,String... expected) {
        if(!o.keySet().equals(Set.of(expected)))throw new IllegalArgumentException("妖兽材料目录字段错误");
    }
    public List<Variant> variants(){return variants;}
    public List<String> rarities(){return rarities;}
    public Optional<Variant> find(String id){return Optional.ofNullable(byId.get(id));}
    public Optional<Variant> resolve(String prototype,String realm,String rarity) {
        var v=byId.get(prototype);if(v==null)return Optional.empty();
        return Optional.ofNullable(combinations.get(List.of(v.family(),realm==null?fallbackRealm:realm,rarity)));
    }
}
