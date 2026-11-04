package com.example.myvillage.forging;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.lang.reflect.*;
import com.google.gson.*;

/** Authored foundry values. All gameplay consumers use this single generated resource. */
public record FoundryRules(int version, Heat heat, Skill skill, Bonus manual, Bonus caps,
        int automatic_ticks_per_point, List<Hammer> hammers, List<Handle> handles,
        List<Fire> fires, FireRules fire, Bonus fire_bonus, Assembly assembly, Map<String,String> palette, Map<String,List<String>> affinity_items, Worldgen worldgen, HandleEffects handle_effects) {
    public record Heat(int capacity,int minimum,int ideal,int gain_per_tick,int passive_ticks_per_unit,
                       int cost_per_strike,int fuel_ticks,int temper_ticks,double minimum_efficiency) {}
    public record Skill(int experience_per_level,int max_level,int maximum_experience,double power_per_level,
                        int quality_per_level,double overleveled_multiplier,int grade_level_span) {}
    public record Bonus(double attack,double speed,double durability,double armor,double qi_discount) {}
    public record Hammer(String id,String name,int power,int quality,int durability,String material,String color) {}
    public record Handle(String id,String name,String ingredient,double attack,double speed,double durability,
                         double qi,double movement,double knockback,String effect,double effect_value,int effect_ticks,String color) {}
    public record Fire(String id,String name,double strength,double speed,String nurture_item,String affinity,String color) {}
    public record FireRules(int max_maturity,int nurture_gain,int nurture_qi,int samadhi_qi,double maturity_scale,
                            int completed_gain,double affinity_bonus,int thunder_pulses,double thunder_bonus,
                            int earth_radius,double capture_health_fraction,double beast_drop_chance,int capture_cooldown_ticks,
                            int samadhi_skill_level,int samadhi_creation_qi,Map<String,Integer> samadhi_creation_cost,int samadhi_interval_ticks) {}
    public record HandleEffects(int nurture_idle_ticks,double nurture_min_power_fraction,int nurture_repair) {}
    public record Worldgen(int search_radius,int earth_attempts,int cold_attempts,int earth_min_y,int earth_max_y,int cold_min_y,int cold_max_y) {}
    public record Assembly(int ticks,int repair_grace_ticks,int handle_cooldown_ticks,String armor_material) {}
    public FoundryRules {
        hammers=List.copyOf(hammers);handles=List.copyOf(handles);fires=List.copyOf(fires);palette=Map.copyOf(palette);affinity_items=Map.copyOf(affinity_items);
        if(version!=1||heat.capacity()<1||heat.capacity()>30000||heat.minimum()<1||heat.ideal()<heat.minimum()
                ||heat.ideal()>heat.capacity()||heat.gain_per_tick()<1||heat.passive_ticks_per_unit()<1
                ||heat.cost_per_strike()<1||heat.fuel_ticks()<1||heat.temper_ticks()<1
                ||skill.experience_per_level()<1||skill.max_level()<1||skill.grade_level_span()<1||skill.maximum_experience()>10000000
                ||skill.maximum_experience()!=skill.experience_per_level()*skill.max_level()
                ||automatic_ticks_per_point<1||automatic_ticks_per_point>100||assembly.ticks()<1
                ||fire.max_maturity()<1||fire.max_maturity()>10000||fire.nurture_gain()<1||fire.nurture_qi()<1
                ||fire.completed_gain()<0||fire.thunder_pulses()<1||fire.thunder_pulses()>16||fire.earth_radius()<1||fire.earth_radius()>256
                ||fire.nurture_gain()>fire.max_maturity()||fire.samadhi_interval_ticks()<1||fire.capture_cooldown_ticks()<1
                ||fire.samadhi_qi()<1||fire.samadhi_creation_qi()<1||fire.samadhi_skill_level()<1||fire.samadhi_skill_level()>skill.max_level()
                ||worldgen.search_radius()<1||worldgen.search_radius()>8||worldgen.earth_attempts()<1||worldgen.earth_attempts()>32
                ||worldgen.cold_attempts()<1||worldgen.cold_attempts()>32||worldgen.earth_min_y()>worldgen.earth_max_y()
                ||worldgen.cold_min_y()>worldgen.cold_max_y()||handle_effects.nurture_idle_ticks()<1||handle_effects.nurture_repair()<1)
            throw new IllegalArgumentException("Invalid foundry bounds");
        finite(skill.power_per_level(),0,0.1);finite(skill.overleveled_multiplier(),0,1);finite(heat.minimum_efficiency(),0.1,1);
        finite(handle_effects.nurture_min_power_fraction(),0,1);
        finite(fire.maturity_scale(),0,1);finite(fire.capture_health_fraction(),0,1);finite(fire.beast_drop_chance(),0,1);
        validateBonus(manual);validateBonus(caps);validateBonus(fire_bonus);
        unique(hammers.stream().map(Hammer::id).toList());unique(handles.stream().map(Handle::id).toList());unique(fires.stream().map(Fire::id).toList());
        for(var h:hammers){if(h.power()<1||h.power()>10000||h.quality()<0||h.quality()>10000||h.durability()<1||h.durability()>10000)
            throw new IllegalArgumentException("Invalid hammer "+h.id());ForgingIngredients.validate(h.material());}
        for(var h:handles){finite(h.attack(),0.5,2);finite(h.speed(),0.5,2);finite(h.durability(),0.5,3);finite(h.qi(),0.5,1.5);
            finite(h.movement(),0,0.1);finite(h.knockback(),0,0.5);finite(h.effect_value(),0,10);
            if(!Set.of("none","light","nurture","followup","steady","spell_followup").contains(h.effect())||h.effect_ticks()<0||h.effect_ticks()>30000)
                throw new IllegalArgumentException("Invalid handle effect");
            ForgingIngredients.validate(h.ingredient());
            if(Set.of("nurture","followup","spell_followup").contains(h.effect())&&h.effect_ticks()<1)throw new IllegalArgumentException("Missing effect interval");
            if(h.effect().equals("nurture")&&(h.effect_value()<1||Math.rint(h.effect_value())!=h.effect_value()))throw new IllegalArgumentException("Nonintegral nurture cost");
            if(!h.effect().equals("nurture"))finite(h.effect_value(),0,0.5);
        }
        for(var f:fires){finite(f.strength(),0,1);finite(f.speed(),0.5,2);ForgingIngredients.validate(f.nurture_item());
            if(!affinity_items.containsKey(f.affinity()))throw new IllegalArgumentException("Unknown fire affinity");}
        for(var entry:fire.samadhi_creation_cost().entrySet()){ForgingIngredients.validate(entry.getKey());if(entry.getValue()<1||entry.getValue()>64)throw new IllegalArgumentException("Invalid true fire cost");}
        if(fire.samadhi_creation_cost().isEmpty())throw new IllegalArgumentException("Free true fire");
    }
    private static void unique(List<String> ids){if(ids.isEmpty()||new HashSet<>(ids).size()!=ids.size()
            ||ids.stream().anyMatch(id->!id.matches("[a-z][a-z0-9_]*")))throw new IllegalArgumentException("Invalid foundry IDs");}
    private static void validateBonus(Bonus b){finite(b.attack(),0,2);finite(b.speed(),0,1);finite(b.durability(),0,3);finite(b.armor(),0,0.5);finite(b.qi_discount(),0,0.49);}
    private static void finite(double n,double min,double max){if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException("Invalid foundry coefficient");}
    public static FoundryRules parse(Reader source){
        try(var reader=new com.google.gson.stream.JsonReader(source)){
            reader.setLenient(false);var value=ForgingRules.readJson(reader,"foundry");
            if(reader.peek()!=com.google.gson.stream.JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing foundry JSON");
            validateSchema(value,FoundryRules.class);return new Gson().fromJson(value,FoundryRules.class);
        }catch(IOException e){throw new IllegalArgumentException("Invalid foundry JSON",e);}
    }
    private static void validateSchema(JsonElement value,Type type){
        if(type==int.class){value.getAsBigDecimal().intValueExact();return;}
        if(type==String.class){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected foundry string");return;}
        if(type instanceof Class<?> record&&record.isRecord()){
            var object=value.getAsJsonObject();var fields=new HashSet<String>();
            for(var field:record.getRecordComponents()){fields.add(field.getName());if(!object.has(field.getName()))throw new IllegalArgumentException("Missing foundry field "+field.getName());validateSchema(object.get(field.getName()),field.getGenericType());}
            if(!fields.equals(object.keySet()))throw new IllegalArgumentException("Unknown foundry field");return;
        }
        if(type instanceof ParameterizedType parameter){
            if(parameter.getRawType()==List.class)for(var entry:value.getAsJsonArray())validateSchema(entry,parameter.getActualTypeArguments()[0]);
            else if(parameter.getRawType()==Map.class)for(var entry:value.getAsJsonObject().entrySet())validateSchema(entry.getValue(),parameter.getActualTypeArguments()[1]);
        }
    }
    private static class Holder {static final FoundryRules VALUE=load();}
    public static FoundryRules get(){return Holder.VALUE;}
    private static FoundryRules load(){try(var in=FoundryRules.class.getResourceAsStream("/data/myvillage/forging/foundry.json")){
        if(in==null)throw new IllegalStateException("Missing foundry rules");return parse(new InputStreamReader(in,StandardCharsets.UTF_8));
    }catch(IOException e){throw new IllegalStateException(e);}}
    public Hammer hammer(String id){return hammers.stream().filter(h->h.id().equals(id)).findFirst().orElseThrow();}
    public Handle handle(String id){return handles.stream().filter(h->h.id().equals(id)).findFirst().orElseThrow();}
    public Fire flame(String id){return fires.stream().filter(f->f.id().equals(id)).findFirst().orElseThrow();}
}
