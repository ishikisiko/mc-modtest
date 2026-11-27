package com.example.myvillage.sim.data;

import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.sim.SimRng;
import java.util.*;

/** 宗门等级、任职境界和月供奉的唯一配置。宗门等级在成立时确定。 */
public record SectProgressionRules(int entryStates, int regionalHubs, Map<String, Tier> tiers, Trial trial,
                                   Tournament tournament) {
    public static final String BRANCH = "branch", HEADQUARTERS = "headquarters", MAIN = "main";
    public static final List<String> IDS = List.of(BRANCH, HEADQUARTERS, MAIN);
    public static final List<String> STONES = List.of("low", "middle", "high");
    public record Master(String realm, int minimumStage, int maximumStage, int weight) { }
    public record Trial(String entity, String realm, int stage, int durationTicks, int radius, int spawnRadius) { }
    public record Tournament(int groupSize, int requiredWins, int stageGap, int readyTicks,
                             int boutTicks, int intervalTicks, int watchRadius, int maxLiveCourts) { }
    public record Tier(String id, String name, Rules.RealmStage outer, Rules.PlayerPromotion promotion,
                       List<Master> masters, String stone, Map<String, Integer> monthly) {
        public Master master(SimRng rng) {
            double[] weights = masters.stream().mapToDouble(Master::weight).toArray();
            return masters.get(rng.weighted(weights));
        }
        public Rules.RealmStage minimumMaster(RealmTable realms) {
            var first = masters.stream().min(Comparator.comparingInt((Master m) -> realms.indexOf(m.realm()))
                    .thenComparingInt(Master::minimumStage)).orElseThrow();
            return new Rules.RealmStage(first.realm(), first.minimumStage());
        }
        public int stipend(String rank) { return monthly.getOrDefault(rank, 0); }
    }
    public Tier tier(String id) {
        Tier result = tiers.get(id);
        if (result == null) throw new IllegalArgumentException("未知宗门等级：" + id);
        return result;
    }
    public String forRegion(RegionGraph graph, String regionId) {
        return forRegion(graph, regionId, entryStates, regionalHubs);
    }
    public static String forRegion(RegionGraph graph, String regionId, int entryStates, int regionalHubs) {
        GenRegion region = graph.regionById(regionId);
        if (region.role().equals("anchor")) return MAIN;
        if (!graph.progressionOrder().isEmpty()) {
            var levels=graph.regions().stream().filter(r->!r.role().equals("anchor")&&r.admittedSubjects().contains("sect"))
                    .map(GenRegion::tier).distinct().sorted().skip(entryStates).sorted(Comparator.reverseOrder()).limit(regionalHubs).toList();
            return levels.contains(region.tier()) ? HEADQUARTERS : BRANCH;
        }
        List<GenRegion> hubs = graph.regions().stream()
                .filter(r -> !r.role().equals("anchor") && r.admittedSubjects().contains("sect"))
                .sorted(Comparator.comparingInt(GenRegion::tier).thenComparing(GenRegion::id))
                .skip(entryStates)
                .sorted(Comparator.comparingInt(GenRegion::tier).reversed().thenComparing(GenRegion::id))
                .limit(regionalHubs).toList();
        return hubs.stream().anyMatch(r -> r.id().equals(regionId)) ? HEADQUARTERS : BRANCH;
    }
    public Rules.PlayerPromotion promotion(String id, Rules.PlayerPromotion branch) {
        return tier(id).promotion() == null ? branch : tier(id).promotion();
    }
    static SectProgressionRules parse(SimJson.Fields f, RealmTable realms, Rules.PlayerPromotion branch) {
        int entries = f.integer("entry_states", 1, 3);
        int hubs = f.integer("regional_hubs", 1, 2);
        Map<String, Tier> tiers = new LinkedHashMap<>();
        SimJson.Fields ts = f.object("levels", Set.copyOf(IDS));
        for (String id : IDS) {
            SimJson.Fields t = ts.object(id, Set.of("name", "outer", "promotion", "masters", "stone", "monthly"));
            Rules.RealmStage outer = stage(t.object("outer", Set.of("realm", "stage")), realms);
            Rules.PlayerPromotion promotion = null;
            if (!id.equals(BRANCH)) {
                SimJson.Fields p = t.object("promotion", Set.of("inner", "elder"));
                promotion = new Rules.PlayerPromotion(threshold(p.object("inner", Set.of("realm", "stage", "contribution")), realms),
                        threshold(p.object("elder", Set.of("realm", "stage", "contribution")), realms));
            } else if (t.has("promotion")) throw t.error("promotion", "分宗晋升由 player.promotion 定义");
            var array = t.nonEmptyArray("masters");
            List<Master> masters = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                var row = t.element("masters", array, i, Set.of("realm", "minimum_stage", "maximum_stage", "weight"));
                String realm = row.nonEmptyString("realm");
                if (realms.indexOf(realm) < 0) throw row.error("realm", "未知境界");
                int lo = row.integer("minimum_stage", 0, realms.byId(realm).lastStage());
                masters.add(new Master(realm, lo, row.integer("maximum_stage", lo, realms.byId(realm).lastStage()), row.positiveInteger("weight")));
            }
            var bars = promotion == null ? branch : promotion;
            int outerPosition = realms.indexOf(outer.realm()) * 100 + outer.stage();
            int innerPosition = realms.indexOf(bars.inner().stage().realm()) * 100 + bars.inner().stage().stage();
            int elderPosition = realms.indexOf(bars.elder().stage().realm()) * 100 + bars.elder().stage().stage();
            if (outerPosition >= innerPosition || innerPosition >= elderPosition
                    || masters.stream().anyMatch(m -> realms.indexOf(m.realm()) * 100 + m.minimumStage() < elderPosition))
                throw t.error("promotion", "外门、内门、长老与宗主的境界必须依次有序");
            String stone = t.nonEmptyString("stone");
            if (!STONES.contains(stone)) throw t.error("stone", "未知灵石品级");
            Map<String, Integer> monthly = new LinkedHashMap<>();
            var money = t.object("monthly", Set.of("outer", "inner", "elder", "sect_master"));
            for (String rank : List.of("outer", "inner", "elder", "sect_master")) monthly.put(rank, money.positiveInteger(rank));
            tiers.put(id, new Tier(id, t.nonEmptyString("name"), outer, promotion, List.copyOf(masters), stone, Map.copyOf(monthly)));
        }
        var tr = f.object("elder_trial", Set.of("entity", "realm", "stage", "duration_ticks", "radius", "spawn_radius"));
        var target = stage(tr, realms);
        if (!target.realm().equals("qi_refining") || target.stage() != realms.byId("qi_refining").lastStage())
            throw tr.error("stage", "分宗长老考核对手必须为炼气巅峰");
        var cup = f.object("tournament", Set.of("group_size", "required_wins", "stage_gap", "ready_ticks",
                "bout_ticks", "interval_ticks", "watch_radius", "max_live_courts"));
        int groupSize = cup.integer("group_size", 3, 4);
        var tournament = new Tournament(groupSize, cup.integer("required_wins", 2, groupSize - 1),
                cup.integer("stage_gap", 0, 8), cup.integer("ready_ticks", 20, 200),
                cup.integer("bout_ticks", 200, 6000), cup.integer("interval_ticks", 20, 1200),
                cup.integer("watch_radius", 16, 96), cup.integer("max_live_courts", 1, 16));
        return new SectProgressionRules(entries, hubs, Map.copyOf(tiers), new Trial(tr.nonEmptyString("entity"), target.realm(), target.stage(),
                tr.positiveInteger("duration_ticks"), tr.integer("radius", 8, 128), tr.integer("spawn_radius", 2, 24)), tournament);
    }
    private static Rules.RealmStage stage(SimJson.Fields f, RealmTable realms) {
        String realm = f.nonEmptyString("realm");
        if (realms.indexOf(realm) < 0) throw f.error("realm", "未知境界");
        return new Rules.RealmStage(realm, f.integer("stage", 0, realms.byId(realm).lastStage()));
    }
    private static Rules.PlayerThreshold threshold(SimJson.Fields f, RealmTable realms) {
        var s = stage(f, realms);
        return new Rules.PlayerThreshold(new Rules.PlayerRealmStage(s.realm(), s.stage()), f.nonNegativeInteger("contribution"));
    }
}
