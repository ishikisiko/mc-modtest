package com.example.myvillage.cultivation.crafting;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.DoubleSupplier;

/** Shared server-owned normal distribution. Outcome zero is a failed attempt, never an item quality. */
public final class CraftingQualityRules {
    public static final String RESOURCE = "/data/myvillage/crafting/quality.json";
    public record Tier(String id, int baseline, double span, List<Double> thresholds) {
        public Tier { thresholds = List.copyOf(thresholds); }
        public double mean(int aptitude) {
            if (aptitude < 0) throw new IllegalArgumentException("Negative crafting aptitude");
            return 100.0 * (aptitude - (double) baseline) / span;
        }
    }
    public record Roll(double score, int quality) {
        public Roll {
            if (!Double.isFinite(score) || quality < 0 || quality > 4)
                throw new IllegalArgumentException("Invalid crafting outcome");
        }
        public boolean succeeded() { return quality != 0; }
    }
    private final int initialMin, initialMax, maximumDifficulty;
    private final double deviation, difficultyStep;
    private final Map<String, Tier> tiers;

    private CraftingQualityRules(JsonObject root) {
        fields(root,"version","initial_roll","standard_deviation","maximum_difficulty","difficulty_threshold_step","tiers");
        if (integer(root,"version",1,1) != 1) throw invalid("version");
        var initial = object(root.get("initial_roll")); fields(initial,"min","max");
        initialMin = integer(initial,"min",0,Integer.MAX_VALUE);
        initialMax = integer(initial,"max",initialMin,Integer.MAX_VALUE - 1);
        deviation = positive(root,"standard_deviation");
        maximumDifficulty = integer(root,"maximum_difficulty",0,64);
        difficultyStep = positive(root,"difficulty_threshold_step");
        if (!root.get("tiers").isJsonArray() || root.getAsJsonArray("tiers").size()==0) throw invalid("tiers");
        var parsed = new LinkedHashMap<String,Tier>();
        for (var entry : root.getAsJsonArray("tiers")) {
            var t = object(entry); fields(t,"id","baseline","span","thresholds");
            String id = t.get("id").getAsString();
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+") || parsed.containsKey(id)) throw invalid("tier id");
            var cuts = t.getAsJsonArray("thresholds");
            if (cuts == null || cuts.size() != 4) throw invalid("four thresholds required");
            var values = new ArrayList<Double>(); double previous = Double.NEGATIVE_INFINITY;
            for (var cut : cuts) {
                if (!cut.isJsonPrimitive() || !cut.getAsJsonPrimitive().isNumber()) throw invalid("numeric thresholds required");
                double value = cut.getAsDouble();
                if (!Double.isFinite(value) || value <= previous) throw invalid("ordered thresholds required");
                values.add(value); previous = value;
            }
            parsed.put(id,new Tier(id,integer(t,"baseline",0,Integer.MAX_VALUE),positive(t,"span"),values));
        }
        tiers = Collections.unmodifiableMap(parsed);
    }
    private static final class Holder { static final CraftingQualityRules VALUE = load(); }
    public static CraftingQualityRules get() { return Holder.VALUE; }
    private static CraftingQualityRules load() {
        try (var input = CraftingQualityRules.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing " + RESOURCE);
            return parse(new InputStreamReader(input,StandardCharsets.UTF_8));
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    public static CraftingQualityRules parse(Reader source) {
        try (var reader = new JsonReader(source)) {
            reader.setLenient(false);
            var root = read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("trailing content");
            return new CraftingQualityRules(object(root));
        } catch (IOException | IllegalStateException | NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid crafting quality rules",e);
        }
    }
    public int initialMin() { return initialMin; }
    public int initialMax() { return initialMax; }
    public int maximumDifficulty() { return maximumDifficulty; }
    public double deviation() { return deviation; }
    public double difficultyStep() { return difficultyStep; }
    public Collection<Tier> tiers() { return tiers.values(); }
    public Tier tier(String id) {
        var tier = tiers.get(id);
        if (tier == null) throw invalid("unknown tier " + id);
        return tier;
    }
    public void requireDifficulty(int difficulty) {
        if (difficulty < 0 || difficulty > maximumDifficulty) throw invalid("difficulty");
    }
    public List<Double> thresholds(String tierId,int difficulty) {
        requireDifficulty(difficulty);
        return tier(tierId).thresholds().stream().map(v -> v + difficulty * difficultyStep).toList();
    }
    public int classify(String tierId,int difficulty,double score) {
        if (!Double.isFinite(score)) throw invalid("non-finite score");
        var cuts = thresholds(tierId,difficulty);
        for (int i=0;i<cuts.size();i++) if (score < cuts.get(i)) return i;
        return 4;
    }
    public Roll roll(int aptitude,String tierId,int difficulty,DoubleSupplier gaussian) {
        requireDifficulty(difficulty);
        double score = tier(tierId).mean(aptitude) + deviation * gaussian.getAsDouble();
        return new Roll(score,classify(tierId,difficulty,score));
    }
    /** Five probabilities in order: failure, lower, middle, upper, perfect. */
    public List<Double> probabilities(int aptitude,String tierId,int difficulty) {
        double mean = tier(tierId).mean(aptitude); var cuts = thresholds(tierId,difficulty);
        var result = new ArrayList<Double>(); double previous = 0;
        for (double cut : cuts) {
            double p = cdf((cut - mean) / deviation);
            result.add(Math.max(0,p - previous)); previous = p;
        }
        result.add(cdf((mean - cuts.getLast()) / deviation));
        return List.copyOf(result);
    }
    private static double cdf(double x) { return 0.5 * erfc(-x / Math.sqrt(2)); }
    private static double erfc(double x) {
        if (x == 0) return 1;
        double z=Math.abs(x),t=1/(1+0.5*z);
        double value=t*Math.exp(-z*z-1.26551223+t*(1.00002368+t*(0.37409196+t*(0.09678418+t*(-0.18628806+t*(0.27886807+t*(-1.13520398+t*(1.48851587+t*(-0.82215223+t*0.17087277)))))))));
        return x>=0?value:2-value;
    }
    private static IllegalArgumentException invalid(String field) { return new IllegalArgumentException("Invalid crafting quality " + field); }
    private static JsonObject object(JsonElement value) { if (value == null || !value.isJsonObject()) throw invalid("object"); return value.getAsJsonObject(); }
    private static void fields(JsonObject object,String... names) {
        if (!object.keySet().equals(Set.of(names))) throw invalid("fields " + object.keySet());
    }
    private static int integer(JsonObject object,String key,int min,int max) {
        var raw=object.get(key);if(raw==null||!raw.isJsonPrimitive()||!raw.getAsJsonPrimitive().isNumber())throw invalid(key);
        int value=raw.getAsBigDecimal().intValueExact();
        if (value<min || value>max) throw invalid(key); return value;
    }
    private static double positive(JsonObject object,String key) {
        var raw=object.get(key);if(raw==null||!raw.isJsonPrimitive()||!raw.getAsJsonPrimitive().isNumber())throw invalid(key);
        double value=raw.getAsDouble();
        if (!Double.isFinite(value) || value<=0) throw invalid(key); return value;
    }
    private static JsonElement read(JsonReader reader) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var value=new JsonObject();reader.beginObject();
                while(reader.hasNext()){String key=reader.nextName();if(value.has(key))throw invalid("duplicate " + key);value.add(key,read(reader));}
                reader.endObject();yield value;
            }
            case BEGIN_ARRAY -> {var value=new JsonArray();reader.beginArray();while(reader.hasNext())value.add(read(reader));reader.endArray();yield value;}
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {reader.nextNull();yield JsonNull.INSTANCE;}
            default -> throw invalid("JSON token");
        };
    }
}
