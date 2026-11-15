package com.example.myvillage.drops;

import com.example.myvillage.stats.RealmKey;
import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.*;
import java.util.random.RandomGenerator;

/** Immutable server drop rules. The normal loot table remains the inherited input. */
public final class DropRules {
    public static final int MAX_BYTES=1024*1024;
    public enum Mode { INHERIT, APPEND, REPLACE, DISABLED }
    public enum Scene { NORMAL, HABITAT, QUEST_REALM, SECT_TRIAL, APTITUDE_TRIAL, BATTLE_RUIN;
        public String id(){return name().toLowerCase(Locale.ROOT);}
        public static Scene parse(String id){try{return valueOf(id.toUpperCase(Locale.ROOT));}catch(Exception e){throw bad("scene","未知场景："+id);}}
    }
    public record Catalog(Predicate<String> item,Predicate<String> entity,Predicate<String> region,ToIntFunction<RealmKey> realmRank) {}
    public record Context(String entity,Scene scene,String region,Integer realmRank,boolean playerKill,boolean fireCaptured) {}
    public record Entry(String item,int min,int max,double chance,double lootingMin,double lootingMax,int limit,String factory) {}
    public record Policy(Mode mode,List<Entry> drops) {public Policy{drops=List.copyOf(drops);}}
    public record Match(Set<String> entities,Set<Scene> scenes,Set<String> regions,Integer minRealm,Integer maxRealm,Boolean playerKill,Boolean fireCaptured) {
        public boolean matches(Context c){return (entities.isEmpty()||entities.contains(c.entity()))&&(scenes.isEmpty()||scenes.contains(c.scene()))
                &&(regions.isEmpty()||regions.contains(c.region()))&&(minRealm==null||c.realmRank()!=null&&c.realmRank()>=minRealm)
                &&(maxRealm==null||c.realmRank()!=null&&c.realmRank()<=maxRealm)&&(playerKill==null||playerKill==c.playerKill())
                &&(fireCaptured==null||fireCaptured==c.fireCaptured());}
    }
    public record Override(String id,Match when,Policy policy) {}
    public record Addition(String id,Match when,List<Entry> drops) {public Addition{drops=List.copyOf(drops);}}
    public record Resolution(String origin,Policy policy,List<Addition> additions) {public Resolution{additions=List.copyOf(additions);}}
    public record Reward(String item,int count,String factory) {}
    private final Policy defaults;
    private final Map<String,Policy> entities;
    private final List<Override> overrides;
    private final List<Addition> additions;
    private final JsonObject json;
    private DropRules(Policy defaults,Map<String,Policy> entities,List<Override> overrides,List<Addition> additions,JsonObject json){
        this.defaults=defaults;this.entities=Collections.unmodifiableMap(new LinkedHashMap<>(entities));this.overrides=List.copyOf(overrides);
        this.additions=List.copyOf(additions);this.json=json.deepCopy();
    }
    public JsonObject json(){return json.deepCopy();}
    public Set<String> entities(){return entities.keySet();}
    public Resolution resolve(Context c){
        Policy selected=entities.getOrDefault(c.entity(),defaults);String origin=entities.containsKey(c.entity())?"entities."+c.entity():"defaults";
        for(var rule:overrides)if(rule.when().matches(c)){selected=rule.policy();origin="overrides."+rule.id();break;}
        List<Addition> extra=selected.mode()==Mode.DISABLED?List.of():additions.stream().filter(a->a.when().matches(c)).toList();
        return new Resolution(origin,selected,extra);
    }
    public static List<Reward> roll(Resolution result,int looting,RandomGenerator random){
        if(result.policy().mode()==Mode.DISABLED)return List.of();
        looting=Math.clamp(looting,0,255);List<Reward> out=new ArrayList<>();
        draw(result.policy().drops(),looting,random,out);for(var addition:result.additions())draw(addition.drops(),looting,random,out);
        return List.copyOf(out);
    }
    private static void draw(List<Entry> entries,int looting,RandomGenerator random,List<Reward> out){
        for(var e:entries){if(e.chance()==0||e.chance()<1&&random.nextDouble()>=e.chance())continue;
            int count=e.min()+random.nextInt(e.max()-e.min()+1);
            if(looting>0&&e.lootingMax()>0)count+=Math.round((float)(looting*(e.lootingMin()+(e.lootingMax()-e.lootingMin())*random.nextDouble())));
            count=Math.min(count,e.limit());if(count>0)out.add(new Reward(e.item(),count,e.factory()));
        }
    }
    public static DropRules parse(String input,Catalog catalog){
        if(input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)throw bad("$","文件超过1 MiB");
        JsonObject root=strict(input);fields(root,"$","schema","defaults","entities","overrides","additions");
        if(integer(root,"schema","$",1,1)!=1)throw bad("$.schema","未知格式");
        Policy defaults=policy(object(root,"defaults","$"),"$.defaults",catalog);
        JsonObject entityData=object(root,"entities","$");if(entityData.size()>512)throw bad("$.entities","最多512个实体");
        Map<String,Policy> entities=new LinkedHashMap<>();for(var row:entityData.entrySet()){
            String p="$.entities."+row.getKey();id(row.getKey(),p);if(!catalog.entity().test(row.getKey()))throw bad(p,"实体不存在");
            entities.put(row.getKey(),policy(asObject(row.getValue(),p),p,catalog));
        }
        Set<String> ids=new HashSet<>();List<Override> overrides=new ArrayList<>();JsonArray os=array(root,"overrides","$",256);
        for(int i=0;i<os.size();i++){
            String p="$.overrides["+i+"]";JsonObject o=asObject(os.get(i),p);fields(o,p,"id","when","mode","drops");
            String name=ruleId(o,p,ids);overrides.add(new Override(name,match(object(o,"when",p),p+".when",catalog),policy(o,p,catalog)));
        }
        List<Addition> additions=new ArrayList<>();JsonArray ads=array(root,"additions","$",64);
        for(int i=0;i<ads.size();i++){
            String p="$.additions["+i+"]";JsonObject o=asObject(ads.get(i),p);fields(o,p,"id","when","drops");
            additions.add(new Addition(ruleId(o,p,ids),match(object(o,"when",p),p+".when",catalog),entries(o,p,catalog)));
        }
        return new DropRules(defaults,entities,overrides,additions,root);
    }
    private static String ruleId(JsonObject o,String p,Set<String> ids){String id=string(o,"id",p);if(!id.matches("[a-z0-9_]{1,64}")||!ids.add(id))throw bad(p+".id","规则名非法或重复");return id;}
    private static Policy policy(JsonObject o,String p,Catalog c){
        if(!o.has("when"))fields(o,p,"mode","drops");
        Mode mode;try{mode=Mode.valueOf(string(o,"mode",p).toUpperCase(Locale.ROOT));}catch(Exception e){throw bad(p+".mode","应为inherit、append、replace或disabled");}
        List<Entry> entries=entries(o,p,c);if((mode==Mode.INHERIT||mode==Mode.DISABLED)&&!entries.isEmpty())throw bad(p+".drops","inherit与disabled模式的drops必须为空");
        return new Policy(mode,entries);
    }
    private static List<Entry> entries(JsonObject o,String p,Catalog c){
        JsonArray a=array(o,"drops",p,64);List<Entry> out=new ArrayList<>();
        for(int i=0;i<a.size();i++){
            String q=p+".drops["+i+"]";JsonObject e=asObject(a.get(i),q);fields(e,q,"item","min","max","chance","looting","factory");
            String item=string(e,"item",q);id(item,q+".item");if(item.equals("minecraft:air")||!c.item().test(item))throw bad(q+".item","物品不存在或为空气");
            int min=integer(e,"min",q,0,64),max=integer(e,"max",q,min,64);double chance=decimal(e,"chance",q,0,1);
            double lo=0,hi=0;int limit=64;if(e.has("looting")){
                JsonObject l=object(e,"looting",q);fields(l,q+".looting","min_per_level","max_per_level","limit");
                lo=decimal(l,"min_per_level",q+".looting",0,64);hi=decimal(l,"max_per_level",q+".looting",lo,64);limit=integer(l,"limit",q+".looting",1,64);
            }
            String factory=e.has("factory")?string(e,"factory",q):"plain";
            if(!Set.of("plain","beast_fire").contains(factory)||factory.equals("beast_fire")&&!item.equals("myvillage:beast_fire_seed"))throw bad(q+".factory","工厂应为plain；beast_fire仅用于兽火");
            out.add(new Entry(item,min,max,chance,lo,hi,limit,factory));
        }
        return List.copyOf(out);
    }
    private static Match match(JsonObject o,String p,Catalog c){
        fields(o,p,"entities","scenes","regions","min_realm","max_realm","player_kill","fire_captured");
        Set<String> entities=strings(o,"entities",p,512);for(String id:entities){id(id,p+".entities");if(!c.entity().test(id))throw bad(p+".entities","实体不存在："+id);}
        Set<Scene> scenes=new LinkedHashSet<>();for(String s:strings(o,"scenes",p,6)){try{scenes.add(Scene.parse(s));}catch(Exception e){throw bad(p+".scenes","未知场景："+s);}}
        Set<String> regions=strings(o,"regions",p,64);for(String r:regions)if(!c.region().test(r))throw bad(p+".regions","地区不存在："+r);
        Integer min=realm(o,"min_realm",p,c),max=realm(o,"max_realm",p,c);if(min!=null&&max!=null&&min>max)throw bad(p,"境界下限高于上限");
        return new Match(Set.copyOf(entities),Set.copyOf(scenes),Set.copyOf(regions),min,max,optionalBoolean(o,"player_kill",p),optionalBoolean(o,"fire_captured",p));
    }
    private static Integer realm(JsonObject o,String key,String p,Catalog c){
        if(!o.has(key))return null;String q=p+"."+key;JsonObject r=object(o,key,p);fields(r,q,"realm","stage");
        try{return c.realmRank().applyAsInt(new RealmKey(string(r,"realm",q),integer(r,"stage",q,0,8)));}
        catch(IllegalArgumentException e){throw bad(q,e.getMessage());}
    }
    private static Set<String> strings(JsonObject o,String key,String p,int max){
        if(!o.has(key))return Set.of();JsonArray a=array(o,key,p,max);if(a.isEmpty())throw bad(p+"."+key,"条件列表不能为空");Set<String> out=new LinkedHashSet<>();
        for(int i=0;i<a.size();i++){var v=a.get(i);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString()||v.getAsString().length()>128||!out.add(v.getAsString()))throw bad(p+"."+key,"应为不重复的短字符串");}return out;
    }
    private static Boolean optionalBoolean(JsonObject o,String key,String p){if(!o.has(key))return null;var v=o.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw bad(p+"."+key,"应为布尔值");return v.getAsBoolean();}
    private static void id(String id,String p){if(id.length()>128||!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw bad(p,"应为命名空间ID");}
    private static void fields(JsonObject o,String p,String... allowed){Set<String> set=Set.of(allowed);for(String key:o.keySet())if(!set.contains(key))throw bad(p+"."+key,"未知字段");}
    private static JsonObject object(JsonObject o,String key,String p){if(!o.has(key))throw bad(p+"."+key,"缺少对象");return asObject(o.get(key),p+"."+key);}
    private static JsonObject asObject(JsonElement e,String p){if(!e.isJsonObject())throw bad(p,"应为对象");return e.getAsJsonObject();}
    private static JsonArray array(JsonObject o,String key,String p,int max){if(!o.has(key)||!o.get(key).isJsonArray())throw bad(p+"."+key,"应为数组");var a=o.getAsJsonArray(key);if(a.size()>max)throw bad(p+"."+key,"条目超过"+max);return a;}
    private static String string(JsonObject o,String key,String p){if(!o.has(key)||!o.get(key).isJsonPrimitive()||!o.getAsJsonPrimitive(key).isString())throw bad(p+"."+key,"应为字符串");return o.get(key).getAsString();}
    private static double decimal(JsonObject o,String key,String p,double min,double max){
        if(!o.has(key)||!o.get(key).isJsonPrimitive()||!o.getAsJsonPrimitive(key).isNumber())throw bad(p+"."+key,"应为数字");
        double value=o.get(key).getAsDouble();if(!Double.isFinite(value)||value<min||value>max)throw bad(p+"."+key,"应在"+min+"至"+max+"之间");return value;
    }
    private static int integer(JsonObject o,String key,String p,int min,int max){double v=decimal(o,key,p,min,max);if(v!=Math.rint(v))throw bad(p+"."+key,"应为整数");return (int)v;}
    private static IllegalArgumentException bad(String p,String message){return new IllegalArgumentException(p+"："+message);}
    private static JsonObject strict(String text){
        try(JsonReader r=new JsonReader(new StringReader(text))){r.setLenient(false);var root=read(r,0,new int[]{0});if(r.peek()!=JsonToken.END_DOCUMENT)throw bad("$","存在多余文档");return asObject(root,"$");}
        catch(IOException|IllegalStateException e){throw bad("$","JSON格式错误："+e.getMessage());}
    }
    private static JsonElement read(JsonReader r,int depth,int[] count)throws IOException{
        if(depth>24||++count[0]>100000)throw bad(r.getPath(),"JSON嵌套或条目过多");
        return switch(r.peek()){
            case BEGIN_OBJECT->{JsonObject o=new JsonObject();r.beginObject();while(r.hasNext()){String key=r.nextName();if(o.has(key))throw bad(r.getPath(),"重复字段");o.add(key,read(r,depth+1,count));}r.endObject();yield o;}
            case BEGIN_ARRAY->{JsonArray a=new JsonArray();r.beginArray();while(r.hasNext())a.add(read(r,depth+1,count));r.endArray();yield a;}
            case STRING->new JsonPrimitive(r.nextString());case NUMBER->new JsonPrimitive(new BigDecimal(r.nextString()));case BOOLEAN->new JsonPrimitive(r.nextBoolean());
            case NULL->{r.nextNull();yield JsonNull.INSTANCE;}default->throw bad(r.getPath(),"JSON值错误");
        };
    }
}
