package com.example.myvillage.sim.economy;

import com.example.myvillage.sim.SimDataException;
import com.google.gson.*;
import java.util.*;

/** 宗门兑换和江湖事务共用的作者配置，数值不在运行时另设副本。 */
public record PlayerEconomyRules(Map<String, RewardRange> sectRewards, Map<String, Integer> manualOverrides,
        List<MeritOffer> linlang, List<TownTask> townTasks, Map<Integer, Integer> pillReputation,
        Map<String, Integer> offerReputation, int interactionRange, int actionIntervalTicks) {
    public record Range(int min, int max) {
        public Range { if (min < 0 || max < min || max > 1000000) throw new IllegalArgumentException("奖励区间无效"); }
        int roll(SplittableRandom rng) { return min == max ? min : rng.nextInt(min, max + 1); }
    }
    public record RewardRange(Range contribution, Range reputation, Map<String, Range> stones) {
        public RewardRange { stones = Collections.unmodifiableMap(new TreeMap<>(stones)); }
        public TaskReward roll(long seed) {
            var rng = new SplittableRandom(seed); int merit = contribution.roll(rng), fame = reputation.roll(rng);
            var money = new TreeMap<String, Integer>();
            stones.forEach((kind, range) -> { int amount = range.roll(rng); if (amount > 0) money.put(kind, amount); });
            return new TaskReward(merit, fame, money);
        }
    }
    public record MeritOffer(String id, String name, String item, int quantity, int cost, String rank, int pillQuality) { }
    public record TownTask(String id, String name, String brief, String kind, String item, int count, RewardRange reward) { }
    public PlayerEconomyRules {
        sectRewards = Map.copyOf(sectRewards); manualOverrides = Map.copyOf(manualOverrides);
        linlang = List.copyOf(linlang); townTasks = List.copyOf(townTasks);
        pillReputation = Map.copyOf(pillReputation); offerReputation = Map.copyOf(offerReputation);
    }
    public static PlayerEconomyRules disabled() { return new PlayerEconomyRules(Map.of(), Map.of(), List.of(), List.of(), Map.of(), Map.of(), 5, 4); }
    public int reputationFor(com.example.myvillage.sim.commerce.CommerceRules.Offer offer) {
        if (!offer.kind().equals("buy")) return 0;
        return Math.max(offerReputation.getOrDefault(offer.id(), 0),
                offer.pillQuality() > 0 ? pillReputation.getOrDefault(offer.pillQuality(), 0) : 0);
    }
    public MeritOffer offer(String id) { return linlang.stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("unknown_offer")); }
    public TownTask task(String id) { return townTasks.stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("unknown_task")); }
    public static PlayerEconomyRules parse(String file, JsonObject root) {
        try {
            fields(root, "schema", "sect_task_rewards", "manual_overrides", "linlang", "town_tasks", "pill_quality_reputation", "offer_reputation", "interaction_range", "action_interval_ticks");
            if (number(root, "schema", 1, 1) != 1) throw new IllegalArgumentException("schema");
            var sect = new LinkedHashMap<String, RewardRange>();
            root.getAsJsonObject("sect_task_rewards").entrySet().forEach(e -> sect.put(id(e.getKey()), reward(e.getValue().getAsJsonObject(), true)));
            var manuals = costs(root.getAsJsonObject("manual_overrides"));
            var offerRep = costs(root.getAsJsonObject("offer_reputation"));
            var pills = new TreeMap<Integer, Integer>();
            for (var e : root.getAsJsonObject("pill_quality_reputation").entrySet()) {
                int quality = Integer.parseInt(e.getKey());
                if (quality < 1 || quality > 4) throw new IllegalArgumentException("丹药品质无效");
                pills.put(quality, exact(e.getValue(), 0, 1000000000));
            }
            if (!pills.keySet().equals(Set.of(1, 2, 3, 4))) throw new IllegalArgumentException("必须配置四档丹药声望");
            int last = -1; for (int value : pills.values()) { if (value < last) throw new IllegalArgumentException("声望门槛须递增"); last = value; }
            var goods = new ArrayList<MeritOffer>(); var seen = new HashSet<String>();
            for (var value : root.getAsJsonArray("linlang")) {
                var o = value.getAsJsonObject(); fields(o, "id", "name", "item", "quantity", "contribution", "rank", "pill_quality");
                String key = id(text(o, "id", 64)), rank = text(o, "rank", 16);
                if (!seen.add(key) || !List.of("outer", "inner", "elder").contains(rank)) throw new IllegalArgumentException("琳琅阁商品重复或身份无效");
                String item = resource(text(o, "item", 160)); if (item.equals("minecraft:air")) throw new IllegalArgumentException("琳琅阁商品不能为空气");
                goods.add(new MeritOffer(key, text(o, "name", 64), item, number(o, "quantity", 1, 64),
                        number(o, "contribution", 1, 1000000), rank, number(o, "pill_quality", 0, 4)));
            }
            var tasks = new ArrayList<TownTask>(); seen.clear();
            for (var value : root.getAsJsonArray("town_tasks")) {
                var t = value.getAsJsonObject(); fields(t, "id", "name", "brief", "kind", "item", "count", "reward");
                String key = id(text(t, "id", 64)), kind = text(t, "kind", 16), item = resource(text(t, "item", 160));
                if (!seen.add(key) || !Set.of("hunt", "supply").contains(kind) || kind.equals("hunt") != item.equals("minecraft:air"))
                    throw new IllegalArgumentException("江湖事务类型或物品无效");
                var reward = reward(t.getAsJsonObject("reward"), false);
                if (reward.contribution().max() != 0) throw new IllegalArgumentException("江湖事务不能发宗门贡献");
                tasks.add(new TownTask(key, text(t, "name", 64), text(t, "brief", 160), kind, item, number(t, "count", 1, 10000), reward));
            }
            if (goods.isEmpty() || goods.size() > 32 || tasks.isEmpty() || tasks.size() > 16 || sect.size() > 64 || manuals.size() > 256 || offerRep.size() > 64)
                throw new IllegalArgumentException("目录数量无效");
            return new PlayerEconomyRules(sect, manuals, goods, tasks, pills, offerRep,
                    number(root, "interaction_range", 2, 8), number(root, "action_interval_ticks", 1, 40));
        } catch (RuntimeException error) { throw new SimDataException(file, "player_economy", error.getMessage()); }
    }
    private static RewardRange reward(JsonObject o, boolean sect) {
        String merit = sect ? "contribution_bonus" : "contribution";
        fields(o, merit, "reputation", "stones"); var stones = new TreeMap<String, Range>();
        o.getAsJsonObject("stones").entrySet().forEach(e -> {
            if (!Set.of("low", "middle", "high").contains(e.getKey())) throw new IllegalArgumentException("未知灵石品级");
            stones.put(e.getKey(), range(e.getValue()));
        });
        return new RewardRange(range(o.get(merit)), range(o.get("reputation")), stones);
    }
    private static Range range(JsonElement value) {
        var array = value.getAsJsonArray(); if (array.size() != 2) throw new IllegalArgumentException("奖励需两个端点");
        return new Range(exact(array.get(0), 0, 1000000), exact(array.get(1), 0, 1000000));
    }
    private static Map<String, Integer> costs(JsonObject o) {
        var result = new TreeMap<String, Integer>(); o.entrySet().forEach(e -> result.put(id(e.getKey()), exact(e.getValue(), 0, 1000000000))); return result;
    }
    private static String id(String value) { if (!value.matches("[a-z0-9_]{1,96}")) throw new IllegalArgumentException("无效标识"); return value; }
    private static String resource(String value) { if (value.length() > 160 || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")) throw new IllegalArgumentException("物品标识无效"); return value; }
    private static void fields(JsonObject o, String... names) { if (!o.keySet().equals(Set.of(names))) throw new IllegalArgumentException("字段缺少或未知：" + o.keySet()); }
    private static String text(JsonObject o, String key, int max) { var v = o.get(key); if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key); String s = v.getAsString(); if (s.isBlank() || s.length() > max) throw new IllegalArgumentException(key); return s; }
    private static int number(JsonObject o, String key, int min, int max) { return exact(o.get(key), min, max); }
    private static int exact(JsonElement v, int min, int max) { if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("需要整数"); int n = v.getAsBigDecimal().intValueExact(); if (n < min || n > max) throw new IllegalArgumentException("整数越界"); return n; }
}
