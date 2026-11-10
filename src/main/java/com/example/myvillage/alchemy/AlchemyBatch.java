package com.example.myvillage.alchemy;

import com.example.myvillage.cultivation.crafting.CraftingQualityRules;
import net.minecraft.nbt.*;
import java.util.*;
import java.util.function.DoubleSupplier;

/** Every pill, including failures, is frozen once by the server and persisted with its furnace batch. */
public record AlchemyBatch(UUID brewer,String recipe,int score,String tier,int difficulty,
                           List<CraftingQualityRules.Roll> outcomes) {
    public AlchemyBatch {
        Objects.requireNonNull(brewer,"brewer");outcomes=List.copyOf(outcomes);
        if(recipe==null||!recipe.matches("[a-z0-9_]+")||score<0||outcomes.isEmpty()||outcomes.size()>16)
            throw new IllegalArgumentException("Invalid alchemy batch");
        var rules=CraftingQualityRules.get();rules.tier(tier);rules.requireDifficulty(difficulty);
        for(var outcome:outcomes)if(rules.classify(tier,difficulty,outcome.score())!=outcome.quality())
            throw new IllegalArgumentException("Alchemy outcome does not match frozen score");
    }
    public static AlchemyBatch create(UUID brewer,AlchemyRules.Recipe recipe,int aptitude,DoubleSupplier gaussian) {
        var rules=CraftingQualityRules.get();var results=new ArrayList<CraftingQualityRules.Roll>();
        for(int i=0;i<recipe.outputCount();i++)results.add(rules.roll(aptitude,recipe.qualityTier(),recipe.difficulty(),gaussian));
        return new AlchemyBatch(brewer,recipe.id(),aptitude,recipe.qualityTier(),recipe.difficulty(),results);
    }
    public int count(int quality){return (int)outcomes.stream().filter(r->r.quality()==quality).count();}
    public int failures(){return count(0);}
    public int successfulCount(){return outcomes.size()-failures();}
    public int bestQuality(){return outcomes.stream().mapToInt(CraftingQualityRules.Roll::quality).max().orElse(0);}
    public enum Rejection {
        INVALID("invalid"),BUSY("busy"),UNAWAKENED("unawakened"),INGREDIENTS("ingredients"),FUEL("fuel"),OUTPUT("output");
        private final String suffix;
        Rejection(String suffix){this.suffix=suffix;}
        public String messageKey(){return "message.myvillage.alchemy.start_"+suffix;}
    }
    public static Rejection rejection(boolean actor,boolean active,boolean awakened,boolean ingredients,boolean fuel,boolean outputEmpty){
        if(!actor)return Rejection.INVALID;if(active)return Rejection.BUSY;if(!awakened)return Rejection.UNAWAKENED;
        if(!ingredients)return Rejection.INGREDIENTS;if(!fuel)return Rejection.FUEL;return outputEmpty?null:Rejection.OUTPUT;
    }
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putInt("Version",2);tag.putUUID("Brewer",brewer);tag.putString("Recipe",recipe);
        tag.putInt("Score",score);tag.putString("Tier",tier);tag.putInt("Difficulty",difficulty);
        var results=new ListTag();for(var r:outcomes){var result=new CompoundTag();result.putDouble("Score",r.score());result.putInt("Quality",r.quality());results.add(result);}
        tag.put("Outcomes",results);return tag;
    }
    public static Optional<AlchemyBatch> read(CompoundTag tag){
        if(!tag.contains("Version",Tag.TAG_INT)||tag.getInt("Version")!=2||!tag.hasUUID("Brewer")
                ||!tag.contains("Recipe",Tag.TAG_STRING)||!tag.contains("Score",Tag.TAG_INT)
                ||!tag.contains("Tier",Tag.TAG_STRING)||!tag.contains("Difficulty",Tag.TAG_INT)
                ||!tag.contains("Outcomes",Tag.TAG_LIST))return Optional.empty();
        try{
            var results=new ArrayList<CraftingQualityRules.Roll>();
            for(var entry:tag.getList("Outcomes",Tag.TAG_COMPOUND)){
                var result=(CompoundTag)entry;
                if(!result.contains("Score",Tag.TAG_DOUBLE)||!result.contains("Quality",Tag.TAG_INT))return Optional.empty();
                results.add(new CraftingQualityRules.Roll(result.getDouble("Score"),result.getInt("Quality")));
            }
            return Optional.of(new AlchemyBatch(tag.getUUID("Brewer"),tag.getString("Recipe"),tag.getInt("Score"),
                    tag.getString("Tier"),tag.getInt("Difficulty"),results));
        }catch(IllegalArgumentException|IllegalStateException e){return Optional.empty();}
    }
}
