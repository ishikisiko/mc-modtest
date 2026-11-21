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
    static final int REALM_RANK_STRIDE=16;
    public enum Mode { INHERIT, APPEND, REPLACE, DISABLED }
    public enum Scene { NORMAL, HABITAT, QUEST_REALM, SECT_TRIAL, APTITUDE_TRIAL, BATTLE_RUIN;
        public String id(){return name().toLowerCase(Locale.ROOT);}
        public static Scene parse(String id){try{return valueOf(id.toUpperCase(Locale.ROOT));}catch(Exception e){throw bad("scene","未知场景："+id);}}
    }
    public record Catalog(Predicate<String> item,Predicate<String> entity,Predicate<String> region,ToIntFunction<RealmKey> realmRank,
                          BiPredicate<String,Integer> quality,Predicate<String> rarity) {}
    public record Context(String entity,Scene scene,String region,Integer realmRank,boolean playerKill,boolean fireCaptured) {}
    public record Entry(String item,int min,int max,double chance,double lootingMin,double lootingMax,Integer limit,String factory,Integer quality,String rarity,
                        double chancePerLootingLevel,int chanceLootingCap,List<Integer> materialWeights,boolean scaleByRealm) {
        public Entry { materialWeights=List.copyOf(materialWeights); }
        public double effectiveChance(int looting){return Math.min(1,chance+chancePerLootingLevel*Math.clamp(looting,0,chanceLootingCap));}

        private Entry atRealm(int level) {
            if (!scaleByRealm) return this;
            var bounds = SpiritStoneAmounts.forRealm(min, max, level);
            return new Entry(item,bounds.min(),bounds.max(),chance,lootingMin,lootingMax,limit,factory,quality,rarity,
                    chancePerLootingLevel,chanceLootingCap,materialWeights,false);
        }
    }
    public record Policy(Mode mode,List<Entry> drops) {public Policy{drops=List.copyOf(drops);}}
    public record Match(Set<String> entities,Set<Scene> scenes,Set<String> regions,Integer minRealm,Integer maxRealm,Boolean playerKill,Boolean fireCaptured) {
        public boolean matches(Context c){return (entities.isEmpty()||entities.contains(c.entity()))&&(scenes.isEmpty()||scenes.contains(c.scene()))
                &&(regions.isEmpty()||regions.contains(c.region()))&&(minRealm==null||c.realmRank()!=null&&c.realmRank()>=minRealm)
                &&(maxRealm==null||c.realmRank()!=null&&c.realmRank()<=maxRealm)&&(playerKill==null||playerKill==c.playerKill())
                &&(fireCaptured==null||fireCaptured==c.fireCaptured());}
    }
    public record Override(String id,Match when,Policy policy) {}
    public record Addition(String id,Match when,List<Entry> drops) {public Addition{drops=List.copyOf(drops);}}
    private record RealmPolicy(String realm,Policy policy) {}
    public record Resolution(String origin,Policy policy,List<Addition> additions) {public Resolution{additions=List.copyOf(additions);}}
    public record Reward(String item,int count,String factory,Integer quality,String rarity,String materialRarity) {}
    private final Policy defaults;
    private final Map<String,Policy> entities;
    private final Map<String,Map<Integer,RealmPolicy>> realmEntities;
    private final Set<String> configuredEntities;
    private final List<Override> overrides;
    private final List<Addition> additions;
    private final JsonObject json;
    private DropRules(Policy defaults,Map<String,Policy> entities,Map<String,Map<Integer,RealmPolicy>> realmEntities,
                      List<Override> overrides,List<Addition> additions,JsonObject json){
        this.defaults=defaults;this.entities=Map.copyOf(entities);this.realmEntities=Map.copyOf(realmEntities);this.overrides=List.copyOf(overrides);
        var configured=new HashSet<>(entities.keySet());configured.addAll(realmEntities.keySet());
        this.configuredEntities=Set.copyOf(configured);
        this.additions=List.copyOf(additions);this.json=json.deepCopy();
    }
    public JsonObject json(){return json.deepCopy();}
    public Set<String> entities(){return configuredEntities;}
    public static int realmLevel(Integer rank){return rank==null?0:Math.floorDiv(rank,REALM_RANK_STRIDE)+1;}
    public Resolution resolve(Context c){
        Policy selected=entities.getOrDefault(c.entity(),defaults);String origin=entities.containsKey(c.entity())?"entities."+c.entity():"defaults";
        for(var rule:overrides)if(rule.when().matches(c)){selected=rule.policy();origin="overrides."+rule.id();break;}
        if(selected.mode()==Mode.DISABLED)return new Resolution(origin,selected,List.of());
        int level=realmLevel(c.realmRank());
        var perRealm=realmEntities.get(c.entity());
        if(perRealm!=null&&c.realmRank()!=null){
            var match=perRealm.get(level-1);
            if(match!=null){
                origin="realm_entities."+c.entity()+"."+match.realm();
                Policy specific=match.policy();
                if(specific.mode()!=Mode.APPEND)return new Resolution(origin,policyAtRealm(specific,level),List.of());
                var combined=new ArrayList<>(selected.drops());
                combined.addAll(specific.drops());
                selected=new Policy(selected.mode()==Mode.REPLACE?Mode.REPLACE:Mode.APPEND,combined);
            }
        }
        List<Addition> extra=additions.stream().filter(a->a.when().matches(c)).map(a->{
            var drops=entriesAtRealm(a.drops(),level);
            return drops==a.drops()?a:new Addition(a.id(),a.when(),drops);
        }).toList();
        return new Resolution(origin,policyAtRealm(selected,level),extra);
    }

    private static Policy policyAtRealm(Policy policy,int level){
        var drops=entriesAtRealm(policy.drops(),level);
        return drops==policy.drops()?policy:new Policy(policy.mode(),drops);
    }

    private static List<Entry> entriesAtRealm(List<Entry> drops,int level){
        if (level<=0||drops.stream().noneMatch(Entry::scaleByRealm)) return drops;
        return drops.stream().map(entry->entry.atRealm(level)).toList();
    }
    public static List<Reward> roll(Resolution result,int looting,RandomGenerator random){
        if(result.policy().mode()==Mode.DISABLED)return List.of();
        looting=Math.clamp(looting,0,255);List<Reward> out=new ArrayList<>();
        draw(result.policy().drops(),looting,random,out);for(var addition:result.additions())draw(addition.drops(),looting,random,out);
        return List.copyOf(out);
    }
    private static void draw(List<Entry> entries,int looting,RandomGenerator random,List<Reward> out){
        for(var e:entries){double chance=e.effectiveChance(looting);if(chance==0||chance<1&&random.nextDouble()>=chance)continue;
            int count=e.min()+random.nextInt(e.max()-e.min()+1);
            if(looting>0&&e.lootingMax()>0)count+=Math.round((float)(looting*(e.lootingMin()+(e.lootingMax()-e.lootingMin())*random.nextDouble())));
            if(e.limit()!=null)count=Math.min(count,e.limit());
            if(e.factory().equals("beast_material")) {
                // Each physical unit receives its own rarity draw, including all fixed boss units.
                for(int unit=0;unit<count;unit++) {
                    int selected=random.nextInt(e.materialWeights().stream().mapToInt(Integer::intValue).sum()),index=0;
                    while(selected>=e.materialWeights().get(index)){selected-=e.materialWeights().get(index);index++;}
                    out.add(new Reward(e.item(),1,e.factory(),null,null,BeastMaterialRules.bundled().rarities().get(index)));
                }
            } else if(count>0)out.add(new Reward(e.item(),count,e.factory(),e.quality(),e.rarity(),null));
        }
    }
    public static DropRules parse(String input,Catalog catalog){
        if(input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)throw bad("$","文件超过1 MiB");
        JsonObject root=strict(input);fields(root,"$","schema","defaults","entities","overrides","additions","realm_entities");
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
        return new DropRules(defaults,entities,realmEntities(root,catalog),overrides,additions,root);
    }
    private static Map<String,Map<Integer,RealmPolicy>> realmEntities(JsonObject root,Catalog catalog){
        if(!root.has("realm_entities"))return Map.of();
        String path="$.realm_entities";
        JsonObject rows=object(root,"realm_entities","$");
        if(rows.size()>512)throw bad(path,"最多512个实体");
        Map<String,Map<Integer,RealmPolicy>> result=new HashMap<>();
        for(var entity:rows.entrySet()){
            String p=path+"."+entity.getKey();
            id(entity.getKey(),p);
            if(!catalog.entity().test(entity.getKey()))throw bad(p,"实体不存在");
            JsonObject realms=asObject(entity.getValue(),p);
            if(realms.isEmpty())throw bad(p,"至少需要一个境界规则");
            Map<Integer,RealmPolicy> policies=new HashMap<>();
            for(var realm:realms.entrySet()){
                String q=p+"."+realm.getKey();
                id(realm.getKey(),q);
                int order;
                try{order=Math.floorDiv(catalog.realmRank().applyAsInt(new RealmKey(realm.getKey(),0)),REALM_RANK_STRIDE);}
                catch(IllegalArgumentException error){throw bad(q,error.getMessage());}
                var data=asObject(realm.getValue(),q);
                fields(data,q,"mode","drops");
                Policy policy=policy(data,q,catalog);
                if(policy.mode()==Mode.INHERIT)throw bad(q+".mode","沿用其他规则时应移除此境界配置");
                if(policies.putIfAbsent(order,new RealmPolicy(realm.getKey(),policy))!=null)throw bad(q,"境界重复");
            }
            result.put(entity.getKey(),Map.copyOf(policies));
        }
        return result;
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
            String q=p+".drops["+i+"]";JsonObject e=asObject(a.get(i),q);fields(e,q,"item","min","max","chance","looting","factory","quality","rarity","chance_looting","rarity_weights","scale_by_realm");
            String item=string(e,"item",q);id(item,q+".item");if(item.equals("minecraft:air")||!c.item().test(item))throw bad(q+".item","物品不存在或为空气");
            Boolean scaling=optionalBoolean(e,"scale_by_realm",q);
            boolean stones=SpiritStoneAmounts.isStone(item);
            if(scaling!=null&&!stones)throw bad(q+".scale_by_realm","境界数量倍率仅适用于灵石");
            boolean scaleByRealm=Boolean.TRUE.equals(scaling);
            int quantityLimit=stones&&!scaleByRealm?SpiritStoneAmounts.countLimit():64;
            int min=integer(e,"min",q,0,quantityLimit),max=integer(e,"max",q,min,quantityLimit);double chance=decimal(e,"chance",q,0,1);
            double lo=0,hi=0;Integer limit=null;if(e.has("looting")){
                JsonObject l=object(e,"looting",q);fields(l,q+".looting","min_per_level","max_per_level","limit");
                lo=decimal(l,"min_per_level",q+".looting",0,64);hi=decimal(l,"max_per_level",q+".looting",lo,64);
                if(l.has("limit"))limit=integer(l,"limit",q+".looting",1,64);
            }
            String factory=e.has("factory")?string(e,"factory",q):"plain";
            if(!Set.of("plain","beast_fire","beast_material").contains(factory)||factory.equals("beast_fire")&&!item.equals("yuanxian:beast_fire_seed")
                    ||factory.equals("beast_material")&&BeastMaterialRules.bundled().find(item).isEmpty())throw bad(q+".factory","物品与掉落工厂不匹配");
            Integer quality=e.has("quality")?integer(e,"quality",q,0,4):null;
            if(quality!=null&&!c.quality().test(item,quality))throw bad(q+".quality","该物品不支持此品质");
            String rarity=e.has("rarity")?string(e,"rarity",q):null;
            if(rarity!=null&&(!Set.of("common","uncommon","rare","epic").contains(rarity)||!c.rarity().test(item)))throw bad(q+".rarity","该物品不支持此稀有度");
            if(quality!=null&&rarity!=null)throw bad(q,"品质与原版稀有度不能同时指定");
            double chancePerLevel=0;int chanceCap=0;
            if(e.has("chance_looting")) {
                var l=object(e,"chance_looting",q);fields(l,q+".chance_looting","per_level","max_level");
                chancePerLevel=decimal(l,"per_level",q+".chance_looting",0,1);chanceCap=integer(l,"max_level",q+".chance_looting",1,255);
            }
            List<Integer> weights=new ArrayList<>();
            if(factory.equals("beast_material")) {
                if(quality!=null||rarity!=null)throw bad(q,"妖兽材料使用自身境界与稀有度，不接受品质覆盖");
                var w=array(e,"rarity_weights",q,4);if(w.size()!=4)throw bad(q+".rarity_weights","需要四种稀有度权重");
                int total=0;for(var value:w) {
                    if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw bad(q+".rarity_weights","权重须为非负整数");
                    int n;try{n=value.getAsBigDecimal().intValueExact();}catch(ArithmeticException ex){throw bad(q+".rarity_weights","权重须为整数");}
                    if(n<0||n>1_000_000)throw bad(q+".rarity_weights","权重超出范围");weights.add(n);total+=n;
                }
                if(total==0)throw bad(q+".rarity_weights","总权重必须大于零");
            } else if(e.has("rarity_weights"))throw bad(q+".rarity_weights","仅妖兽材料工厂使用权重");
            out.add(new Entry(item,min,max,chance,lo,hi,limit,factory,quality,rarity,chancePerLevel,chanceCap,weights,scaleByRealm));
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
    static JsonObject strict(String text){
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
