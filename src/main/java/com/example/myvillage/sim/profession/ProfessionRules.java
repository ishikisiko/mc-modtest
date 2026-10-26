package com.example.myvillage.sim.profession;

import com.example.myvillage.sim.SimDataException;
import com.example.myvillage.sim.commerce.CommerceRules;
import com.google.gson.JsonObject;
import java.util.*;

/** NPC crop tuning is authored here; recipe quantities and times are projected from alchemy rules. */
public record ProfessionRules(int maxExperience, int experiencePerLevel, int gatheringExperience,
        int plantingExperience, int harvestExperience, int alchemyExperience, int gatherQuotaLimit,
        int maxGardensPerSite, int gardenCapacity, int seedUnits, int harvestUnits, int tendedBonus,
        int growthTicks, int plantTicks, int tendTicks, int harvestTicks, double physicalReach,
        Map<String,Herb> herbRules, List<Recipe> recipes) {
    public static final int MAX_GROWTH_TICKS = 2_400_000;
    public static final Set<String> HERBS = Set.of("myvillage:spirit_leaf", "myvillage:dew_blossom", "myvillage:sunroot",
            "myvillage:blood_berry", "myvillage:cloud_fungus", "myvillage:calm_lotus");
    public record Herb(int gatheringLevel, int plantingLevel) { }
    public record Recipe(String id, String output, int outputCount, int durationTicks, int minimumLevel,
            Map<String,Integer> ingredients) {
        public Recipe { ingredients = Map.copyOf(ingredients); }
    }
    public ProfessionRules { herbRules = Map.copyOf(herbRules); recipes = List.copyOf(recipes); }
    public Set<String> herbs() { return herbRules.keySet(); }
    public int level(int experience) { return Math.min(maxExperience, Math.max(0, experience)) / experiencePerLevel; }
    public Recipe recipe(String id) { return recipes.stream().filter(r -> r.id().equals(id)).findFirst().orElse(null); }
    public static ProfessionRules disabled() {
        return new ProfessionRules(10000,20,1,1,2,2,48,8,1,1,2,1,2400,200,200,200,2.5,Map.of(),List.of());
    }
    public static ProfessionRules parse(String file, JsonObject o, JsonObject alchemy, CommerceRules commerce) {
        try {
            fields(o,"schema","max_experience","experience_per_level","gathering_experience","planting_experience",
                    "harvest_experience","alchemy_experience","gather_quota_limit","max_gardens_per_site","garden_capacity",
                    "seed_units","harvest_units","tended_bonus","growth_ticks","plant_ticks","tend_ticks","harvest_ticks","physical_reach","herbs","recipes");
            integer(o,"schema",1,1);
            int max=integer(o,"max_experience",1,1000000), step=integer(o,"experience_per_level",1,max);
            Map<String,Herb> herbs=new TreeMap<>();
            JsonObject h=o.getAsJsonObject("herbs");
            if(!h.keySet().equals(HERBS)) throw new IllegalArgumentException("expected the existing six herbs");
            for(String id:h.keySet()) {
                var v=h.getAsJsonObject(id);fields(v,"gathering_level","planting_level");
                herbs.put(id,new Herb(integer(v,"gathering_level",0,max/step),integer(v,"planting_level",0,max/step)));
            }
            if(commerce.offers().stream().anyMatch(offer->offer.pool().equals("herbs")&&!herbs.containsKey(offer.item())))
                throw new IllegalArgumentException("commerce contains unsupported herb");
            JsonObject selected=o.getAsJsonObject("recipes");
            if(!selected.keySet().equals(Set.of("huiling","yangyuan","ningshen"))) throw new IllegalArgumentException("expected three basic recipes");
            List<Recipe> recipes=new ArrayList<>();
            for(var value:alchemy.getAsJsonArray("recipes")) {
                var r=value.getAsJsonObject();String id=r.get("id").getAsString();
                if(!selected.has(id))continue;
                String output=r.get("output").getAsString();
                if(commerce.offers().stream().noneMatch(offer->offer.kind().equals("buy")&&offer.item().equals(output)))
                    throw new IllegalArgumentException("recipe output is not sold: "+id);
                Map<String,Integer> ingredients=new TreeMap<>();var list=r.getAsJsonArray("ingredients");
                var counts=r.has("ingredient_counts")?r.getAsJsonArray("ingredient_counts"):null;
                if(list.isEmpty()||list.size()>HERBS.size()||counts!=null&&counts.size()!=list.size())throw new IllegalArgumentException("ingredients");
                for(int i=0;i<list.size();i++) {
                    String item=list.get(i).getAsString();if(!herbs.containsKey(item))throw new IllegalArgumentException("unsupported NPC ingredient");
                    int count=counts==null?1:counts.get(i).getAsBigDecimal().intValueExact();
                    if(count<1||count>64)throw new IllegalArgumentException("ingredient count");
                    ingredients.merge(item,count,Math::addExact);
                }
                recipes.add(new Recipe(id,output,integer(r,"output_count",1,64),integer(r,"duration_ticks",20,240000),
                        integer(selected,id,0,max/step),ingredients));
            }
            if(recipes.size()!=selected.size()||recipes.stream().map(Recipe::id).distinct().count()!=recipes.size()
                    ||recipes.stream().map(Recipe::output).distinct().count()!=recipes.size())
                throw new IllegalArgumentException("missing or duplicate recipe");
            recipes.sort(Comparator.comparing(Recipe::id));
            int seed=integer(o,"seed_units",1,64),yield=integer(o,"harvest_units",1,64);
            if(yield<=seed)throw new IllegalArgumentException("harvest must renew the seed");
            return new ProfessionRules(max,step,integer(o,"gathering_experience",1,max),integer(o,"planting_experience",1,max),
                    integer(o,"harvest_experience",1,max),integer(o,"alchemy_experience",1,max),integer(o,"gather_quota_limit",1,10000),
                    integer(o,"max_gardens_per_site",1,64),integer(o,"garden_capacity",1,1),seed,yield,integer(o,"tended_bonus",1,64),
                    integer(o,"growth_ticks",20,MAX_GROWTH_TICKS),integer(o,"plant_ticks",20,240000),integer(o,"tend_ticks",20,240000),
                    integer(o,"harvest_ticks",20,240000),decimal(o,"physical_reach",0.5,8),herbs,recipes);
        } catch(RuntimeException e) { throw new SimDataException(file,"profession",e.getMessage()); }
    }
    private static void fields(JsonObject o,String... fields) {
        if(!o.keySet().equals(Set.of(fields)))throw new IllegalArgumentException("missing/unknown fields "+o.keySet());
    }
    private static int integer(JsonObject o,String field,int lo,int hi) {
        var v=o.get(field);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(field);
        int n=v.getAsBigDecimal().intValueExact();if(n<lo||n>hi)throw new IllegalArgumentException(field);return n;
    }
    private static double decimal(JsonObject o,String field,double lo,double hi) {
        var v=o.get(field);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(field);
        double n=v.getAsDouble();if(!Double.isFinite(n)||n<lo||n>hi)throw new IllegalArgumentException(field);return n;
    }
}
