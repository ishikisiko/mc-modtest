package com.example.myvillage.cultivation.growth;

import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bundled sample tuning shared by client presentation and server settlement. */
public record TechniqueGrowthRules(int practiceTicks, int insightUnlockStage, int insightPerStage,
        int insightCost, int respecStones, List<String> stageNames, Map<ResourceLocation, Technique> techniques) {
    public static final int UNIT = 10_000;
    public enum Effect { CULTIVATION, RECOVERY, DAMAGE, QI_COST }

    public record Insight(String id, String name, String description, Map<Effect, Integer> bonuses) {
        public Insight { bonuses = Map.copyOf(bonuses); }
    }
    public record Transfer(ResourceLocation source, int studyBonusBp, int firstInsightDiscount,
            int inheritedMastery, boolean inheritInsight) { }
    public record Technique(TechniqueCategory category, List<Integer> thresholds, int practiceGain,
            int practiceStones, int hitGain, String potential, Optional<ResourceLocation> next,
            boolean requirePreviousMastery, boolean requirePreviousInsight, Map<Effect, List<Integer>> bonuses,
            List<Insight> insights, List<Transfer> transfers) {
        public Technique {
            thresholds = List.copyOf(thresholds); bonuses = Map.copyOf(bonuses);
            insights = List.copyOf(insights); transfers = List.copyOf(transfers);
        }
        public int cap() { return thresholds.getLast(); }
        public int stage(long points) {
            int stage = 0;
            for (int i = 1; i < thresholds.size(); i++) if (points >= thresholds.get(i)) stage = i;
            return stage;
        }
        public Optional<Insight> insight(String id) { return insights.stream().filter(i -> i.id().equals(id)).findFirst(); }
        public int bonus(Effect effect, long points) {
            var values = bonuses.get(effect);
            return values == null ? 0 : values.get(stage(points));
        }
    }
    public TechniqueGrowthRules { stageNames = List.copyOf(stageNames); techniques = Map.copyOf(techniques); }
    private static class Holder { static final TechniqueGrowthRules INSTANCE = load(); }
    public static TechniqueGrowthRules bundled() { return Holder.INSTANCE; }
    public Optional<Technique> find(ResourceLocation id) { return Optional.ofNullable(techniques.get(id)); }

    private static TechniqueGrowthRules load() {
        String path = "/data/myvillage/cultivation/technique_growth.json";
        try (var input = TechniqueGrowthRules.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing " + path);
            return parse(JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) { throw new IllegalStateException("Invalid technique growth rules", e); }
    }

    public static TechniqueGrowthRules parse(JsonObject root) {
        fields(root, Set.of("schema", "practice_ticks", "insight_unlock_stage", "insight_per_stage",
                "insight_cost", "respec_stones", "stage_names", "techniques"), Set.of());
        number(root, "schema", 1, 1);
        int ticks = number(root, "practice_ticks", 20, 24000);
        if (ticks % 10 != 0) throw new IllegalArgumentException("practice ticks must align with settlement");
        int unlock = number(root, "insight_unlock_stage", 1, 4), per = number(root, "insight_per_stage", 1, 100);
        int cost = number(root, "insight_cost", 1, unlock * per);
        int respec = number(root, "respec_stones", 1, 1000);
        var stages = new ArrayList<String>();
        for (var value : root.getAsJsonArray("stage_names")) stages.add(value.getAsString());
        if (stages.size() != 5 || stages.stream().anyMatch(String::isBlank) || new HashSet<>(stages).size() != 5)
            throw new IllegalArgumentException("exactly five distinct mastery stages required");
        var techniques = new TreeMap<ResourceLocation, Technique>();
        var rows = root.getAsJsonObject("techniques");
        if (rows.isEmpty() || rows.size() > 64) throw new IllegalArgumentException("growth sample size");
        for (String key : rows.keySet()) {
            var row = rows.getAsJsonObject(key);
            fields(row, Set.of("category", "thresholds", "practice_gain", "practice_stones", "hit_gain",
                    "potential", "bonuses", "insights", "transfers"),
                    Set.of("next", "require_previous_mastery", "require_previous_insight"));
            TechniqueCategory category = switch (string(row, "category")) {
                case "core" -> TechniqueCategory.CORE;
                case "active" -> TechniqueCategory.ACTIVE;
                default -> throw new IllegalArgumentException("unsupported sample category");
            };
            var thresholds = new ArrayList<Integer>();
            for (var value : row.getAsJsonArray("thresholds")) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("threshold type");
                thresholds.add(value.getAsBigDecimal().intValueExact());
            }
            if (thresholds.size() != 5 || thresholds.getFirst() != 0 || thresholds.getLast() > 1_000_000)
                throw new IllegalArgumentException("invalid mastery thresholds");
            for (int i = 1; i < thresholds.size(); i++) if (thresholds.get(i) <= thresholds.get(i - 1))
                throw new IllegalArgumentException("mastery thresholds must increase");
            var bonuses = new EnumMap<Effect, List<Integer>>(Effect.class);
            var stageBonuses = row.getAsJsonObject("bonuses");
            for (String effect : stageBonuses.keySet()) {
                Effect parsed = effect(effect, category);
                var values = new ArrayList<Integer>();
                for (var value : stageBonuses.getAsJsonArray(effect)) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("bonus type");
                    int bonus = value.getAsBigDecimal().intValueExact();
                    if (bonus < -8000 || bonus > 20000) throw new IllegalArgumentException("stage bonus out of bounds");
                    values.add(bonus);
                }
                if (values.size() != 5 || values.getFirst() != 0) throw new IllegalArgumentException("five stage bonuses, starting at zero");
                bonuses.put(parsed, List.copyOf(values));
            }
            var insights = new ArrayList<Insight>(); var ids = new HashSet<String>();
            for (var value : row.getAsJsonArray("insights")) {
                var insight = value.getAsJsonObject();
                fields(insight, Set.of("id", "name", "description", "bonuses"), Set.of());
                String id = string(insight, "id");
                if (!id.matches("[a-z_]{1,32}") || !ids.add(id)) throw new IllegalArgumentException("insight id");
                var map = new EnumMap<Effect, Integer>(Effect.class);
                var values = insight.getAsJsonObject("bonuses");
                for (String effect : values.keySet()) map.put(effect(effect, category), number(values, effect, -8000, 20000));
                insights.add(new Insight(id, string(insight, "name"), string(insight, "description"), map));
            }
            if (insights.size() != 2) throw new IllegalArgumentException("sample has two exclusive insights");
            for (Insight insight : insights) for (Effect effect : Effect.values()) {
                int extra = insight.bonuses().getOrDefault(effect, 0);
                for (int bonus : bonuses.getOrDefault(effect, List.of(0)))
                    if (bonus + extra < -8000 || bonus + extra > 30000) throw new IllegalArgumentException("combined bonus out of bounds");
            }
            var transfers = new ArrayList<Transfer>(); var sources = new HashSet<ResourceLocation>();
            for (var value : row.getAsJsonArray("transfers")) {
                var transfer = value.getAsJsonObject();
                fields(transfer, Set.of("source", "study_bonus_bp", "first_insight_discount", "inherited_mastery", "inherit_insight"), Set.of());
                ResourceLocation source = ResourceLocation.parse(string(transfer, "source"));
                if (source.toString().equals(key) || !sources.add(source)) throw new IllegalArgumentException("duplicate/self transfer");
                transfers.add(new Transfer(source, number(transfer, "study_bonus_bp", 0, UNIT),
                        number(transfer, "first_insight_discount", 0, cost - 1),
                        number(transfer, "inherited_mastery", 0, thresholds.get(unlock) - 1), bool(transfer, "inherit_insight")));
            }
            int hitGain = number(row, "hit_gain", 0, thresholds.getLast());
            if (category == TechniqueCategory.CORE && hitGain != 0) throw new IllegalArgumentException("core cannot earn hit mastery");
            techniques.put(ResourceLocation.parse(key), new Technique(category, thresholds,
                    number(row, "practice_gain", 1, thresholds.getLast()), number(row, "practice_stones", 1, 1000),
                    hitGain, string(row, "potential"), row.has("next") ? Optional.of(ResourceLocation.parse(string(row, "next"))) : Optional.empty(),
                    row.has("require_previous_mastery") && bool(row, "require_previous_mastery"),
                    row.has("require_previous_insight") && bool(row, "require_previous_insight"), bonuses, insights, transfers));
        }
        for (var entry : techniques.entrySet()) {
            var technique = entry.getValue();
            if (technique.requirePreviousInsight() && !technique.requirePreviousMastery()) throw new IllegalArgumentException("insight prerequisite needs mastered predecessor");
            if (technique.next().isPresent() && (!techniques.containsKey(technique.next().get()) || technique.next().get().equals(entry.getKey())))
                throw new IllegalArgumentException("invalid sample continuation");
            for (Transfer transfer : technique.transfers()) {
                Technique source = techniques.get(transfer.source());
                if (source == null || source.category() != technique.category()) throw new IllegalArgumentException("unrelated transfer category");
                if ((transfer.inheritInsight() || transfer.inheritedMastery() > 0)
                        && !source.next().equals(Optional.of(entry.getKey()))) throw new IllegalArgumentException("inheritance must follow explicit continuation");
                if (transfer.inheritInsight() && source.insights().stream().anyMatch(i -> technique.insight(i.id()).isEmpty()))
                    throw new IllegalArgumentException("inherited insight missing from next chapter");
            }
        }
        return new TechniqueGrowthRules(ticks, unlock, per, cost, respec, stages, techniques);
    }
    private static Effect effect(String name, TechniqueCategory category) {
        Effect effect = Effect.valueOf(name.toUpperCase(Locale.ROOT));
        if (category == TechniqueCategory.CORE && (effect == Effect.DAMAGE || effect == Effect.QI_COST)
                || category == TechniqueCategory.ACTIVE && (effect == Effect.CULTIVATION || effect == Effect.RECOVERY))
            throw new IllegalArgumentException("effect category mismatch");
        return effect;
    }
    private static String string(JsonObject row, String key) {
        var value = row.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank() || value.getAsString().length() > 512) throw new IllegalArgumentException(key);
        return value.getAsString();
    }
    private static int number(JsonObject row, String key, int min, int max) {
        var value = row.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key);
        int number = value.getAsBigDecimal().intValueExact();
        if (number < min || number > max) throw new IllegalArgumentException(key);
        return number;
    }
    private static boolean bool(JsonObject row, String key) {
        var value = row.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key);
        return value.getAsBoolean();
    }
    private static void fields(JsonObject row, Set<String> required, Set<String> optional) {
        if (!row.keySet().containsAll(required) || row.keySet().stream().anyMatch(k -> !required.contains(k) && !optional.contains(k)))
            throw new IllegalArgumentException("growth fields: " + row.keySet());
    }
}
