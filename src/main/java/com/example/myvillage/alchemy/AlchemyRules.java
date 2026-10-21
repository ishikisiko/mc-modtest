package com.example.myvillage.alchemy;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Validated, immutable bundled alchemy data. No client input determines brewing or medicine. */
public final class AlchemyRules {
    public static final String RESOURCE = "/data/myvillage/alchemy/rules.json";
    public static final String FUEL = com.example.myvillage.item.SpiritStoneTags.LOW_GRADE_REFERENCE;
    public record Recipe(String id, String formula, List<String> ingredients, int fuelCount,
                         int durationTicks, String output, int outputCount, List<Integer> ingredientCounts,
                         int minimumAptitude, int fixedQuality) {
        public Recipe { ingredients = List.copyOf(ingredients); ingredientCounts = List.copyOf(ingredientCounts); }
        public Recipe(String id,String formula,List<String> ingredients,int fuelCount,int durationTicks,String output,int outputCount) {
            this(id,formula,ingredients,fuelCount,durationTicks,output,outputCount,Collections.nCopies(ingredients.size(),1),0,0);
        }
        public boolean matches(String formulaId, String first, String second) {
            return ingredients.size()==2 && formula.equals(formulaId) && ((ingredients.get(0).equals(first) && ingredients.get(1).equals(second))
                    || (ingredients.get(1).equals(first) && ingredients.get(0).equals(second)));
        }
        public boolean eligible(int score, int cap) { return score >= minimumAptitude && (fixedQuality == 0 || cap >= fixedQuality); }
        public boolean acceptsBatch(AlchemyBatch batch) {
            return batch!=null && id.equals(batch.recipe()) && (fixedQuality==0
                || (batch.quality()==fixedQuality && eligible(batch.score(),batch.cap())));
        }
        public String thirdIngredient() { return ingredients.size()==3 ? ingredients.get(2) : ""; }
        public int count(int i) { return i < ingredientCounts.size() ? ingredientCounts.get(i) : 0; }
        public List<String> refillItems() { return List.of(ingredients.get(0),ingredients.get(1),AlchemyRules.FUEL,thirdIngredient()); }
        public List<Integer> refillCounts() { return List.of(count(0),count(1),fuelCount,count(2)); }
        public boolean matchesStacks(List<net.minecraft.world.item.ItemStack> slots) {
            if (slots.size()!=3) return false;
            // Old two-herb recipes retain order independence.
            if (ingredients.size()==2) return matches(formula,
                com.example.myvillage.block.entity.AlchemyFurnaceBlockEntity.id(slots.get(0)),
                com.example.myvillage.block.entity.AlchemyFurnaceBlockEntity.id(slots.get(1))) && slots.get(2).isEmpty();
            for(int i=0;i<3;i++) if (!ingredients.get(i).equals(com.example.myvillage.block.entity.AlchemyFurnaceBlockEntity.id(slots.get(i))) || slots.get(i).getCount()<count(i)) return false;
            return true;
        }
    }
    public record Huiling(double powerFraction) {}
    public record Yangyuan(int durationTicks, int intervalTicks, double healFraction) {}
    public record Ningshen(int durationTicks, double stabilityMultiplier) {}
    public record Effects(Huiling huiling, Yangyuan yangyuan, Ningshen ningshen) {}
    private final int useTicks;
    private final int sharedCooldownTicks;
    private final List<Recipe> recipes;
    private final Effects effects;
    private final int[][] qualityWeights = new int[4][4];
    private final double[] qualityMultipliers = new double[4];

    private AlchemyRules(JsonObject root) {
        fields(root, "version", "use_ticks", "shared_cooldown_ticks", "recipes", "effects", "quality");
        if (integer(root, "version", 2, 2) != 2) throw invalid("version", "unsupported");
        JsonObject quality = object(root.get("quality"), "quality");
        fields(quality, "weights_by_cap", "strength_multipliers");
        JsonElement weights = quality.get("weights_by_cap"), factors = quality.get("strength_multipliers");
        if (!weights.isJsonArray() || weights.getAsJsonArray().size() != 4
                || !factors.isJsonArray() || factors.getAsJsonArray().size() != 4)
            throw invalid("quality", "four ceilings and four multipliers required");
        for (int cap = 1; cap <= 4; cap++) {
            JsonElement row = weights.getAsJsonArray().get(cap - 1);
            if (!row.isJsonArray() || row.getAsJsonArray().size() != 4)
                throw invalid("quality.weights_by_cap", "four weights per ceiling required");
            int total = 0;
            for (int q = 1; q <= 4; q++) {
                int weight = integerValue(row.getAsJsonArray().get(q - 1), "quality weight", 0, 1000000);
                if (q <= cap && weight == 0) throw invalid("quality weight", "eligible outcome must have positive weight");
                if (q > cap && weight != 0) throw invalid("quality weight", "above ceiling");
                qualityWeights[cap - 1][q - 1] = weight;
                total += weight;
            }
            if (total == 0) throw invalid("quality weights", "positive total required");
            JsonObject factor = new JsonObject();
            factor.add("value", factors.getAsJsonArray().get(cap - 1));
            double multiplier = number(factor, "value", 0, 16);
            if ((cap == 1 && multiplier != 1) || (cap > 1 && multiplier <= qualityMultipliers[cap - 2]))
                throw invalid("quality.strength_multipliers", "baseline one and strictly increasing required");
            qualityMultipliers[cap - 1] = multiplier;
        }
        useTicks = integer(root, "use_ticks", 1, 72000);
        sharedCooldownTicks = integer(root, "shared_cooldown_ticks", 1, 1200000);
        if (!root.get("recipes").isJsonArray()) throw invalid("recipes", "array required");
        List<Recipe> result = new ArrayList<>();
        Set<String> ids = new HashSet<>(), formulas = new HashSet<>();
        for (JsonElement value : root.getAsJsonArray("recipes")) {
            JsonObject r = object(value, "recipe");
            JsonObject legacy = r.deepCopy();
            legacy.remove("ingredient_counts"); legacy.remove("minimum_aptitude"); legacy.remove("fixed_quality");
            fields(legacy, "id", "formula", "ingredients", "fuel_count", "duration_ticks", "output", "output_count");
            String id = string(r, "id"), formula = item(r, "formula"), output = item(r, "output");
            if (!id.matches("[a-z0-9_]+") || !ids.add(id) || !formulas.add(formula))
                throw invalid("recipe", "invalid or duplicate id/formula/output");
            if (!r.get("ingredients").isJsonArray() || (r.getAsJsonArray("ingredients").size() < 2 || r.getAsJsonArray("ingredients").size() > 3))
                throw invalid("ingredients", "two or three item ids required");
            List<String> ingredients = new ArrayList<>();
            for (JsonElement ingredient : r.getAsJsonArray("ingredients")) ingredients.add(itemId(ingredient, "ingredients"));
            if (new HashSet<>(ingredients).size() != ingredients.size()) throw invalid("ingredients", "must be distinct");
            List<Integer> counts = new ArrayList<>();
            if(r.has("ingredient_counts")) {
                if(!r.get("ingredient_counts").isJsonArray() || r.getAsJsonArray("ingredient_counts").size()!=ingredients.size()) throw invalid("ingredient_counts","one per ingredient");
                for(var c:r.getAsJsonArray("ingredient_counts")) counts.add(integerValue(c,"ingredient count",1,64));
            } else counts.addAll(Collections.nCopies(ingredients.size(),1));
            int minimum=r.has("minimum_aptitude")?integer(r,"minimum_aptitude",0,100):0;
            int fixed=r.has("fixed_quality")?integer(r,"fixed_quality",1,3):0;
            result.add(new Recipe(id, formula, ingredients, integer(r, "fuel_count", 1, 64),
                    integer(r, "duration_ticks", 1, 32767), output, integer(r, "output_count", 1, 16), counts, minimum, fixed));
        }
        if (!ids.containsAll(Set.of("huiling","yangyuan","ningshen"))) throw invalid("recipes","missing legacy recipes");
        Set<String> herbs=Set.of("spirit_leaf","dew_blossom","sunroot","blood_berry","cloud_fungus","calm_lotus","ningyuan_fruit_low","ningyuan_fruit_high");
        for (Recipe r:result) {
            if (!r.formula().equals("myvillage:formula_"+r.id())) throw invalid("formula","must match recipe id");
            for(String herb:r.ingredients())if(!herb.startsWith("myvillage:")||!herbs.contains(herb.substring(10)))throw invalid("ingredients","unknown herb");
            boolean foundation=r.output().equals("myvillage:pill_zhuji")||r.output().equals("myvillage:pill_buji");
            if(foundation && (r.fixedQuality()==0 || r.ingredients().size()!=3 || (r.output().endsWith("buji")&&r.fixedQuality()<2)))throw invalid("recipe","foundation recipe needs three herbs and bounded fixed quality");
            if(!foundation && (!r.output().equals("myvillage:pill_"+r.id()) || r.fixedQuality()!=0 || r.ingredients().size()!=2 || r.ingredientCounts().stream().anyMatch(n->n!=1)))throw invalid("recipe","legacy medicine shape changed");
        }
        recipes = List.copyOf(result);
        JsonObject e = object(root.get("effects"), "effects");
        fields(e, "huiling", "yangyuan", "ningshen");
        JsonObject h = object(e.get("huiling"), "huiling"), y = object(e.get("yangyuan"), "yangyuan"),
                n = object(e.get("ningshen"), "ningshen");
        fields(h, "power_fraction"); fields(y, "duration_ticks", "interval_ticks", "heal_fraction");
        fields(n, "duration_ticks", "stability_multiplier");
        int duration = integer(y, "duration_ticks", 1, 1200000);
        int interval = integer(y, "interval_ticks", 1, duration);
        effects = new Effects(new Huiling(number(h, "power_fraction", 0, 1)),
                new Yangyuan(duration, interval, number(y, "heal_fraction", 0, 1)),
                new Ningshen(integer(n, "duration_ticks", 1, 1200000),
                        number(n, "stability_multiplier", 1, 16)));
        for (String id : List.of("huiling", "yangyuan", "ningshen"))
            if (!ids.contains(id)) throw invalid("recipes", "missing medicine recipe " + id);
    }
    private static final class Holder { static final AlchemyRules RULES = load(); }
    public static AlchemyRules get() { return Holder.RULES; }
    private static AlchemyRules load() {
        try (InputStream stream = AlchemyRules.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            return parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) { throw new IllegalStateException("Cannot load " + RESOURCE, e); }
    }
    public static AlchemyRules parse(Reader source) {
        try (JsonReader reader = new JsonReader(source)) {
            reader.setLenient(false);
            JsonElement root = readJson(reader, "alchemy");
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("alchemy", "trailing content");
            return new AlchemyRules(object(root, "alchemy"));
        } catch (IOException | NumberFormatException | IllegalStateException e) {
            throw new IllegalArgumentException("Invalid alchemy JSON", e);
        }
    }
    public int useTicks() { return useTicks; }
    public int sharedCooldownTicks() { return sharedCooldownTicks; }
    public List<Recipe> recipes() { return recipes; }
    public Effects effects() { return effects; }
    public double qualityMultiplier(int quality) { return qualityMultipliers[requireQuality(quality) - 1]; }
    public int qualityWeightTotal(int cap) {
        return Arrays.stream(qualityWeights[requireQuality(cap) - 1]).sum();
    }
    /** Deterministic inverse weighted draw; RNG and saved outcome belong to the server batch. */
    public int rollQuality(int cap, int draw) {
        int total = qualityWeightTotal(cap);
        if (draw < 0 || draw >= total) throw invalid("quality draw", "outside weighted range");
        for (int q = 1; q <= cap; q++) {
            draw -= qualityWeights[cap - 1][q - 1];
            if (draw < 0) return q;
        }
        throw new IllegalStateException("Validated quality weights did not resolve");
    }
    private static int requireQuality(int quality) {
        if (quality < 1 || quality > 4) throw invalid("quality", "must be one through four");
        return quality;
    }
    public Optional<Recipe> recipe(String id) { return recipes.stream().filter(r -> r.id().equals(id)).findFirst(); }
    public Optional<Recipe> recipeForFormula(String id) { return recipes.stream().filter(r -> r.formula().equals(id)).findFirst(); }
    public boolean isHerb(String id) { return recipes.stream().anyMatch(r -> r.ingredients().contains(id)); }
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
        return new IllegalArgumentException("Invalid alchemy " + field + ": " + reason);
    }
    /** JsonParser normally discards duplicate keys; keep them a validation error instead. */
    private static JsonElement readJson(JsonReader reader, String path) throws IOException {
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
