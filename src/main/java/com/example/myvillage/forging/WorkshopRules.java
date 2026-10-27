package com.example.myvillage.forging;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored workshop tuning; shared by the server, UI and focused tests. */
public record WorkshopRules(int version, int shape_at, int forming_strikes, int strike_ticks,
        int contact_tick, int heat_ticks, int hammer_durability, int hammer_damage_per_strike, Map<String,Integer> operation_ticks,
        Map<String,Integer> fuel_cost,
        Map<String,Integer> grind_cost, Map<String,Integer> training_cost, String spirit_ingredient,
        List<Band> training_bands, double attack_per_strike, double speed_per_strike,
        double durability_per_strike, double qi_discount_per_strike, double damage_per_experience,
        double treatment_attack, double treatment_speed, double treatment_durability, int experience_limit, float sound_volume, float sound_pitch_jitter, List<Craft> crafts) {
    public static final String RESOURCE="/data/myvillage/forging/workshop.json";
    public static final List<String> KINDS=List.of("sword","spear","pickaxe","axe","shovel","hoe","helmet","chestplate","leggings","boots");
    public record Band(int through,int experience) {}
    public record Craft(String recipe,String kind,boolean requires_pattern,int max_strikes,int work_experience) {
        public boolean armor(){return Set.of("helmet","chestplate","leggings","boots").contains(kind);}
        public boolean treatment(int choice){return choice>=0&&choice<=2&&(!armor()||choice==2);}
        public boolean training(int choice){return choice>=1&&choice<=3&&(!armor()||choice==2);}
        public boolean weapon(){return kind.equals("sword")||kind.equals("spear");}
    }
    public WorkshopRules {
        operation_ticks=Map.copyOf(operation_ticks);fuel_cost=costs(fuel_cost);
        grind_cost=costs(grind_cost);training_cost=costs(training_cost);
        training_bands=List.copyOf(training_bands);crafts=List.copyOf(crafts);
        if(version!=3||shape_at<1||forming_strikes<=shape_at||forming_strikes>64
                ||strike_ticks<8||strike_ticks>60||contact_tick<1||contact_tick>=strike_ticks
                ||heat_ticks<strike_ticks||heat_ticks>1200||experience_limit<1||experience_limit>1000000)
            throw new IllegalArgumentException("Invalid workshop timing/bounds");
        if(hammer_durability<1||hammer_durability>10000||hammer_damage_per_strike<1||hammer_damage_per_strike>hammer_durability)
            throw new IllegalArgumentException("Invalid forging hammer durability");
        if(!operation_ticks.keySet().equals(Set.of("heat","quench","temper","grind","assemble","infuse","repair"))
                ||operation_ticks.values().stream().anyMatch(n->n<1||n>200))throw new IllegalArgumentException("Invalid operation timing");
        ForgingIngredients.validate(spirit_ingredient);
        positive(attack_per_strike,0.1);positive(speed_per_strike,0.02);positive(durability_per_strike,0.05);
        positive(qi_discount_per_strike,0.005);positive(damage_per_experience,100);
        positive(treatment_attack,1);positive(treatment_speed,0.2);positive(treatment_durability,0.5);
        positive(sound_volume,1);positive(sound_pitch_jitter,0.2);
        int last=0;
        for(var band:training_bands){if(band.through()<=last||band.through()>1000||band.experience()<1||band.experience()>10000)
            throw new IllegalArgumentException("Invalid training bands");last=band.through();}
        Set<String> ids=new HashSet<>(), kinds=new HashSet<>();
        for(var craft:crafts){if(!ids.add(craft.recipe())||!kinds.add(craft.kind())||!KINDS.contains(craft.kind())
                ||craft.max_strikes()<1||craft.max_strikes()>last||craft.max_strikes()*qi_discount_per_strike>=0.5
                ||craft.work_experience()<0||craft.work_experience()>100)
            throw new IllegalArgumentException("Invalid workshop craft");}
        if(crafts.isEmpty()||crafts.size()>20)throw new IllegalArgumentException("Invalid craft count");
    }
    private static void positive(double value,double max){if(!Double.isFinite(value)||value<=0||value>max)throw new IllegalArgumentException("Invalid workshop tuning");}
    private static Map<String,Integer> costs(Map<String,Integer> input){
        if(input.isEmpty()||input.size()>8)throw new IllegalArgumentException("Invalid workshop costs");
        input.forEach((id,n)->{ForgingIngredients.validate(id);if(n<1||n>64)throw new IllegalArgumentException("Invalid cost");});
        return Collections.unmodifiableMap(new TreeMap<>(input));
    }
    public static WorkshopRules parse(Reader source){
        try(var reader=new com.google.gson.stream.JsonReader(source)){
            reader.setLenient(false);var root=ForgingRules.readJson(reader,"workshop").getAsJsonObject();
            if(reader.peek()!=com.google.gson.stream.JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing workshop JSON");
            var fields=new HashSet<String>();for(var field:WorkshopRules.class.getRecordComponents()){
                fields.add(field.getName());if(!root.has(field.getName()))throw new IllegalArgumentException("Missing workshop field "+field.getName());
                if(field.getType()==int.class)root.get(field.getName()).getAsBigDecimal().intValueExact();
            }
            if(!fields.equals(root.keySet()))throw new IllegalArgumentException("Unknown workshop fields");
            for(String name:List.of("operation_ticks","fuel_cost","grind_cost","training_cost"))
                for(var entry:root.getAsJsonObject(name).entrySet())entry.getValue().getAsBigDecimal().intValueExact();
            for(var band:root.getAsJsonArray("training_bands"))for(var entry:band.getAsJsonObject().entrySet())entry.getValue().getAsBigDecimal().intValueExact();
            return new Gson().fromJson(root,WorkshopRules.class);
        }catch(IOException e){throw new IllegalArgumentException("Invalid workshop JSON",e);}
    }
    private static final class Holder{static final WorkshopRules VALUE=load();}
    public static WorkshopRules get(){return Holder.VALUE;}
    private static WorkshopRules load(){try(var in=WorkshopRules.class.getResourceAsStream(RESOURCE)){
        if(in==null)throw new IllegalStateException("Missing workshop rules");
        return parse(new InputStreamReader(in,StandardCharsets.UTF_8));
    }catch(IOException e){throw new IllegalStateException(e);}}
    public Craft craft(String recipe){return crafts.stream().filter(c->c.recipe().equals(recipe)).findFirst().orElseThrow();}
    public Optional<Craft> forOutput(String item){return ForgingRules.get().forOutput(item).flatMap(r->crafts.stream().filter(c->c.recipe().equals(r.id())).findFirst());}
    public int experienceCost(int strikes){for(var band:training_bands)if(strikes<band.through())return band.experience();return Integer.MAX_VALUE;}
    public int available(TemperRecord record,Craft craft){
        int experience=record.experience(),hits=record.strikes(),count=0;
        while(hits<craft.max_strikes()&&experience>=experienceCost(hits)){experience-=experienceCost(hits++);count++;}
        return count;
    }
    public Map<String,Integer> blankCosts(ForgingRules.Recipe recipe){var costs=new TreeMap<>(recipe.costs());costs.remove(spirit_ingredient);return costs;}
    public Map<String,Integer> infusionCosts(ForgingRules.Recipe recipe,String trait){
        var result=new TreeMap<>(ForgingRules.get().trait(trait).costs());
        int n=recipe.costs().getOrDefault(spirit_ingredient,0);if(n>0)result.merge(spirit_ingredient,n,Integer::sum);return result;
    }
}
