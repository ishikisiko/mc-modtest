package com.example.myvillage.wilderness;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One validated source for ordinary encounters and their production effects. */
public final class ExpeditionRules {
    public static final String RESOURCE="/data/myvillage/wilderness/roster.json";
    public record Medicine(int duration,double strength,double secondary,boolean qualityDuration) {
        public int durationTicks(int quality){double factor=com.example.myvillage.alchemy.AlchemyRules.get().qualityMultiplier(quality);return (int)Math.round(duration*(qualityDuration?factor:1));}
        public double strengthForQuality(int quality){double factor=com.example.myvillage.alchemy.AlchemyRules.get().qualityMultiplier(quality);return strength*(qualityDuration?1:factor);}
    }
    public record Trait(List<Double> values,int cooldown,int duration) {
        public Trait { values=List.copyOf(values); }
        public double value(int quality){return values.get(Math.clamp(quality,1,4)-1);}
    }
    private final JsonObject root;
    private final Map<String,JsonObject> mobs;
    private final Map<String,Medicine> medicines;
    private final Map<String,Trait> traits;
    public ExpeditionRules(JsonObject root) {
        this.root=root.deepCopy();
        if(integer(root,"schema",1,1)!=1)throw new IllegalArgumentException("roster schema");
        var mm=new LinkedHashMap<String,JsonObject>();var materials=new HashSet<String>();
        for(var value:root.getAsJsonArray("mobs")) {
            var m=value.getAsJsonObject();String id=m.get("id").getAsString();
            if(!id.matches("[a-z0-9_]+")||mm.putIfAbsent(id,m.deepCopy())!=null)throw new IllegalArgumentException("roster id");
            if(!materials.add(m.get("material_id").getAsString()))throw new IllegalArgumentException("duplicate material");
            var d=m.getAsJsonObject("dimensions");number(d,"width",.1,3);number(d,"height",.1,3);number(d,"eye_height",0,3);
            number(m,"body_scale",.25,3);integer(m,"spawn_weight",1,30);integer(m,"experience",0,30);
            if(m.getAsJsonArray("biomes").isEmpty())throw new IllegalArgumentException("no habitats");
        }
        if(mm.size()!=6)throw new IllegalArgumentException("six roster identities required");
        mobs=Collections.unmodifiableMap(mm);
        var meds=new LinkedHashMap<String,Medicine>();
        root.getAsJsonObject("medicines").entrySet().forEach(e->{var o=e.getValue().getAsJsonObject();meds.put(e.getKey(),new Medicine(integer(o,"duration_ticks",1,24000),number(o,"strength",0,1),number(o,"secondary",0,1),optionalBoolean(o,"quality_duration")));});
        medicines=Map.copyOf(meds);
        var ts=new LinkedHashMap<String,Trait>();
        root.getAsJsonObject("traits").entrySet().forEach(e->{var o=e.getValue().getAsJsonObject();var values=new ArrayList<Double>();for(var v:o.getAsJsonArray("values")){double n=v.getAsDouble();if(!Double.isFinite(n)||n<=0||n>1)throw new IllegalArgumentException("trait value");values.add(n);}if(values.size()!=4)throw new IllegalArgumentException("four qualities");ts.put(e.getKey(),new Trait(values,integer(o,"cooldown_ticks",1,24000),integer(o,"duration_ticks",1,24000)));});
        traits=Map.copyOf(ts);
        for(var m:mobs.values())if(!medicines.containsKey(m.get("pill_id").getAsString())||!traits.containsKey(m.get("trait_id").getAsString()))throw new IllegalArgumentException("unclosed loot use");
        for(String k:List.of("decision_ticks","pursuit_radius","lost_target_ticks","projectile_lifetime","field_ticks","field_interval","maximum_targets","retreat_interval","guard_ticks","guard_cycle","spore_ticks","slow_ticks"))integer(root,k,1,12000);
        for(String k:List.of("projectile_speed","projectile_radius","field_radius","retreat_distance","guard_fraction"))number(root,k,.001,32);
        if(number("guard_fraction")>=1)throw new IllegalArgumentException("guard must remain damageable");
    }
    private static final class Holder {static final ExpeditionRules RULES=load();}
    public static ExpeditionRules get(){return Holder.RULES;}
    private static ExpeditionRules load(){try(var in=ExpeditionRules.class.getResourceAsStream(RESOURCE)){if(in==null)throw new IOException(RESOURCE);return new ExpeditionRules(JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());}catch(IOException e){throw new IllegalStateException("Missing expedition rules",e);}}
    public JsonObject mob(String id){var m=mobs.get(id);if(m==null)throw new IllegalArgumentException("Unknown encounter "+id);return m.deepCopy();}
    public float bodyScale(String id){return mobs.get(id).get("body_scale").getAsFloat();}
    public boolean contains(String id){return mobs.containsKey(id);}
    public Set<String> ids(){return mobs.keySet();}
    public boolean isMedicine(String id){return medicines.containsKey(id);}
    public Medicine medicine(String id){return medicines.get(id);}
    public Trait trait(String id){return traits.get(id);}
    public Set<String> traitIds(){return traits.keySet();}
    public boolean isMaterial(String id){return mobs.values().stream().anyMatch(m->("myvillage:"+m.get("material_id").getAsString()).equals(id));}
    public double number(String key){return root.get(key).getAsDouble();}
    public int ticks(String key){return root.get(key).getAsInt();}
    public String mode(String entity,String animation){for(var v:mobs.get(entity).getAsJsonArray("moves")){var m=v.getAsJsonObject();if(m.get("animation").getAsString().equals(animation))return m.get("_mode").getAsString();}return "melee";}
    private static boolean optionalBoolean(JsonObject o,String key){
        if(!o.has(key))return false;
        if(!o.get(key).isJsonPrimitive()||!o.getAsJsonPrimitive(key).isBoolean())throw new IllegalArgumentException("Boolean "+key);
        return o.get(key).getAsBoolean();
    }
    private static double number(JsonObject o,String k,double lo,double hi){if(!o.has(k)||!o.get(k).isJsonPrimitive()||!o.getAsJsonPrimitive(k).isNumber())throw new IllegalArgumentException("Number "+k);double v=o.get(k).getAsDouble();if(!Double.isFinite(v)||v<lo||v>hi)throw new IllegalArgumentException("Invalid "+k);return v;}
    private static int integer(JsonObject o,String k,int lo,int hi){double n=number(o,k,lo,hi);if(n!=Math.rint(n))throw new IllegalArgumentException("Integer "+k);return (int)n;}
}
