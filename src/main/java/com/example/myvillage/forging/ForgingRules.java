package com.example.myvillage.forging;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bundled production costs and effects, validated once on both sides. */
public final class ForgingRules {
    public static final int MAX_RECIPES = 32;
    public static final String RESOURCE = "/data/yuanxian/forging/rules.json";
    public record Recipe(String id, String pattern, String output, Map<String,Integer> costs, List<String> traits, String qualityTier, String infusionMaterial) {
        public Recipe { costs = Map.copyOf(costs); traits = List.copyOf(traits); }
    }
    public record Trait(Map<String,Integer> costs, List<Double> values) {
        public Trait { costs = Map.copyOf(costs); values = List.copyOf(values); }
        public double value(int quality) { return values.get(ForgeMark.requireQuality(quality)-1); }
    }
    private final List<Recipe> recipes;
    private final Map<String,Trait> traits;
    private final Map<String,Integer> repairCosts;
    private final double repairFraction;
    private final float toolMiningSpeedBonus;
    private ForgingRules(JsonObject root) {
        fields(root, "version", "recipes", "traits", "repair", "tool_mining_speed_bonus");
        integer(root,"version",3,3);
        toolMiningSpeedBonus = (float) number(root, "tool_mining_speed_bonus", 0, Float.MAX_VALUE);
        JsonObject definitions = object(root.get("traits"),"traits");
        for (String key : definitions.keySet()) ForgeMark.requireTrait(key);
        for (String key : java.util.List.of("sharp","channeling","tough")) if (!definitions.has(key)) throw invalid("traits", "missing legacy trait");
        Map<String,Trait> parsed = new LinkedHashMap<>();
        for (String id : ForgeMark.TRAITS) {
            if (!definitions.has(id)) continue;
            JsonObject t = object(definitions.get(id),id); fields(t,"costs","values");
            var values = t.getAsJsonArray("values");
            if (values.size()!=4) throw invalid(id,"four quality values required");
            List<Double> levels = new ArrayList<>();
            for (var v : values) {
                JsonObject wrapper = new JsonObject(); wrapper.add("v",v);
                double n = switch (id) {
                    case ForgeMark.SEEK_VEIN -> integer(wrapper,"v",1,12);
                    case ForgeMark.GRADE_GROUND -> integer(wrapper,"v",1,2);
                    case ForgeMark.REPLANT_TREE -> integer(wrapper,"v",1,1);
                    case ForgeMark.HARVEST_HERBS -> number(wrapper,"v",0,1);
                    default -> number(wrapper,"v",id.equals("tough") ? 1 : 0,id.equals("channeling") ? 0.75 : 8);
                };
                if (!levels.isEmpty() && (n<levels.getLast() ||
                        n==levels.getLast() && !id.equals(ForgeMark.GRADE_GROUND) && !id.equals(ForgeMark.REPLANT_TREE)))
                    throw invalid(id,"values out of order");
                levels.add(n);
            }
            parsed.put(id,new Trait(costs(t.get("costs"),0),levels));
        }
        traits = Collections.unmodifiableMap(parsed);
        List<Recipe> rs = new ArrayList<>(); Set<String> ids=new HashSet<>(), outputs=new HashSet<>(), patterns=new HashSet<>();
        for (var element : root.getAsJsonArray("recipes")) {
            var r=object(element,"recipe"); fields(r,"id","pattern","output","costs","traits","quality_tier","infusion_material");
            String id=string(r,"id"), pattern=item(r,"pattern"), output=item(r,"output");
            if (!id.matches("[a-z0-9_]{1,64}") || !ids.add(id) || !outputs.add(output) || !patterns.add(pattern))
                throw invalid("recipe","invalid or duplicate identity");
            List<String> allowed=new ArrayList<>();
            for (var entry : r.getAsJsonArray("traits")) {
                String trait=entry.getAsString();
                if (!traits.containsKey(trait) || allowed.contains(trait)) throw invalid("traits","invalid/duplicate");
                allowed.add(trait);
            }
            String tier=string(r,"quality_tier");com.example.myvillage.cultivation.crafting.CraftingQualityRules.get().tier(tier);
            var ingredients=costs(r.get("costs"),1);
            String infusionMaterial=ForgingIngredients.validate(string(r,"infusion_material"));
            if (!ingredients.containsKey(infusionMaterial))
                throw invalid("infusion_material","must be an ingredient of the recipe");
            rs.add(new Recipe(id,pattern,output,ingredients,allowed,tier,infusionMaterial));
        }
        if (rs.isEmpty() || rs.size()>MAX_RECIPES) throw invalid("recipes","one through " + MAX_RECIPES + " recipes required");
        recipes=List.copyOf(rs);
        for (var r:recipes) for (String t:r.traits())
            if (costs(r,t).size()>4) throw invalid("costs","at most four combined materials fit the menu");
        var repair=object(root.get("repair"),"repair");fields(repair,"costs","fraction");
        repairCosts=costs(repair.get("costs"),1);repairFraction=number(repair,"fraction",0,1);
    }
    private static Map<String,Integer> costs(JsonElement input, int minimum) {
        var o=object(input,"costs");Map<String,Integer> result=new TreeMap<>();
        if (o.size()<minimum || o.size()>8) throw invalid("costs","invalid ingredient count");
        for (var e:o.entrySet()) {
            String id=ForgingIngredients.validate(e.getKey());
            result.put(id,integerValue(e.getValue(),"count",1,64));
        }
        return Collections.unmodifiableMap(result);
    }
    private static final class Holder { static final ForgingRules VALUE=load(); }
    public static ForgingRules get(){return Holder.VALUE;}
    private static ForgingRules load(){
        try(var stream=ForgingRules.class.getResourceAsStream(RESOURCE)){
            if(stream==null)throw new IllegalStateException("Missing "+RESOURCE);
            return parse(new InputStreamReader(stream,StandardCharsets.UTF_8));
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public static ForgingRules parse(Reader source){
        try(JsonReader reader=new JsonReader(source)){
            reader.setLenient(false);var root=readJson(reader,"forging");
            if(reader.peek()!=JsonToken.END_DOCUMENT)throw invalid("json","trailing content");
            return new ForgingRules(object(root,"forging"));
        }catch(IOException|IllegalStateException|NullPointerException e){throw new IllegalArgumentException("Invalid forging JSON",e);}
    }
    public List<Recipe> recipes(){return recipes;}
    public Optional<Recipe> recipe(String id){return recipes.stream().filter(r->r.id().equals(id)).findFirst();}
    public Optional<Recipe> forOutput(String id){return recipes.stream().filter(r->r.output().equals(id)).findFirst();}
    public Trait trait(String id){ForgeMark.requireTrait(id);return traits.get(id);}
    public Map<String,Integer> costs(Recipe recipe,String trait){
        if(!recipe.traits().contains(trait))throw invalid("trait","not supported by recipe");
        Map<String,Integer> result=new TreeMap<>(recipe.costs());
        infusionMaterials(recipe,trait).forEach((id,n)->result.merge(id,n,Integer::sum));
        return Collections.unmodifiableMap(result);
    }
    public Map<String,Integer> infusionMaterials(Recipe recipe,String trait){
        if(!recipe.traits().contains(trait))throw invalid("trait","not supported by recipe");
        Map<String,Integer> result=new TreeMap<>(trait(trait).costs());
        result.merge(recipe.infusionMaterial(),1,Integer::sum);
        return Collections.unmodifiableMap(result);
    }
    public Map<String,Integer> repairCosts(){return repairCosts;}
    public double repairFraction(){return repairFraction;}
    public float toolMiningSpeedBonus() { return toolMiningSpeedBonus; }
    private static void fields(JsonObject o, String... expected) {
        Set<String> names = Set.of(expected);
        for (String name : o.keySet()) if (!names.contains(name)) throw invalid(name, "unknown field");
        for (String name : names) if (!o.has(name)) throw invalid(name, "missing field");
    }
    private static JsonObject object(JsonElement e, String field) {
        if (e == null || !e.isJsonObject()) throw invalid(field, "object required"); return e.getAsJsonObject();
    }
    private static String string(JsonObject o, String field) {
        JsonElement e = o.get(field);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw invalid(field, "string required");
        return e.getAsString();
    }
    private static String item(JsonObject o, String field) { return itemId(o.get(field), field); }
    private static String itemId(JsonElement e, String field) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()
                || !e.getAsString().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw invalid(field, "item id required");
        return e.getAsString();
    }
    private static int integer(JsonObject o, String field, int min, int max) {
        return integerValue(o.get(field), field, min, max);
    }
    private static int integerValue(JsonElement e, String field, int min, int max) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw invalid(field, "integer required");
        try { int n = e.getAsBigDecimal().intValueExact(); if (n < min || n > max) throw invalid(field, "out of range"); return n; }
        catch (ArithmeticException ex) { throw invalid(field, "integer required"); }
    }
    private static double number(JsonObject o, String field, double min, double max) {
        JsonElement e = o.get(field);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw invalid(field, "number required");
        double n = e.getAsDouble(); if (!Double.isFinite(n) || n <= min || n > max) throw invalid(field, "out of range"); return n;
    }
    private static IllegalArgumentException invalid(String field, String reason) {
        return new IllegalArgumentException("Invalid forging " + field + ": " + reason);
    }
    /** JsonParser normally discards duplicate keys; keep them a validation error instead. */
    static JsonElement readJson(JsonReader reader, String path) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw invalid(path + "." + name, "duplicate field");
                    object.add(name, readJson(reader, path + "." + name));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(readJson(reader, path + "[" + array.size() + "]"));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw invalid(path, "unexpected token " + reader.peek());
        };
    }
}
