package com.example.myvillage.worldplan;

import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.engine.Genesis;
import com.example.myvillage.sim.engine.Purpose;
import com.example.myvillage.sim.engine.SimContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.myvillage.sim.data.SectProgressionRules.*;

/** 冻结各地块的用途、开档名次和隶属，再由通用选址器落地。 */
final class OpeningSectPlanner {
    record Slot(String region, int index, SectPlanningRules.Layout layout, int openingRank,
                boolean foundingOnly, String parentSiteId) {
        String id() { return "sect_site_" + region + "_" + index; }

        Slot under(Slot parent, SectPlanningRules.Layout matched) {
            return new Slot(region, index, matched, openingRank, foundingOnly, parent.id());
        }
    }

    private OpeningSectPlanner() {}

    static Map<String, List<Slot>> plan(RegionGraph graph, SectPlanningRules layouts, SectOpeningRules policy,
                                        int seedSalt, String spawnRegion) {
        var eligible = graph.progressionOrder().stream()
                .filter(region -> SimContext.admitsSects(graph.regionById(region))).toList();
        var regions = new ArrayList<>(Genesis.chooseSectRegions(graph, policy.count(), layouts.entryStates(),
                region -> layouts.tier(graph, region), policy.qiExponent(), policy.crowding(),
                SimRng.at(graph.seed(), 0, 0, Purpose.GENESIS_SECT_REGION)));
        ensureLineageCapacity(regions, eligible, graph, layouts, policy.lineages());

        Map<String, List<Slot>> byRegion = new LinkedHashMap<>();
        List<Slot> roster = new ArrayList<>(regions.size());
        for (String region : regions) {
            List<Slot> slots = byRegion.computeIfAbsent(region, ignored -> new ArrayList<>());
            int index = slots.size();
            var slot = new Slot(region, index, layouts.choose(region.hashCode(), seedSalt, index,
                    layouts.tier(graph, region)), roster.size(), false, "");
            slots.add(slot);
            roster.add(slot);
        }
        var roots = roster.stream().filter(slot -> slot.layout.tier.equals(MAIN)).limit(policy.lineages()).toList();
        for (Slot root : roots) {
            Slot hub = attach(roster, byRegion, layouts, root, root.layout.family, HEADQUARTERS);
            attach(roster, byRegion, layouts, hub, root.layout.family, BRANCH);
        }

        for (String tier : IDS) {
            var choice = policy.foundingSites().get(tier);
            List<String> hosts = eligible.stream().filter(region -> layouts.tier(graph, region).equals(tier)).toList();
            String region = choice.region();
            if (region.equals(SectOpeningRules.AUTO)) {
                region = tier.equals(BRANCH) && hosts.contains(spawnRegion) ? spawnRegion
                        : hosts.stream().min(Comparator.comparingInt(id -> byRegion.getOrDefault(id, List.of()).size()))
                                .orElseThrow(() -> new IllegalStateException("缺少创宗位所在州："+tier));
            }
            if (!hosts.contains(region))
                throw new IllegalArgumentException("创宗位所在州和宗门等级不匹配："+tier+" / "+region);
            List<Slot> slots = byRegion.computeIfAbsent(region, ignored -> new ArrayList<>());
            int index = slots.size();
            var layout = choice.style().equals(SectOpeningRules.AUTO)
                    ? layouts.choose(region.hashCode(),seedSalt,index,tier) : layouts.family(choice.style(), tier);
            slots.add(new Slot(region, index, layout, -1, true, ""));
        }
        return byRegion;
    }

    private static void ensureLineageCapacity(List<String> regions, List<String> eligible, RegionGraph graph,
                                              SectPlanningRules layouts, int groups) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        regions.forEach(region -> counts.merge(layouts.tier(graph, region), 1, Integer::sum));
        for (String tier : IDS) {
            while (counts.getOrDefault(tier, 0) < groups) {
                String host = eligible.stream().filter(region -> layouts.tier(graph, region).equals(tier))
                        .min(Comparator.comparingLong(region -> regions.stream().filter(region::equals).count()))
                        .orElseThrow(() -> new IllegalArgumentException("宗门隶属关系缺少可用州："+tier));
                int replacement = regions.size() - 1;
                while (counts.get(layouts.tier(graph, regions.get(replacement))) <= groups) replacement--;
                String previous = regions.set(replacement, host);
                counts.merge(layouts.tier(graph, previous), -1, Integer::sum);
                counts.merge(tier, 1, Integer::sum);
            }
        }
    }

    private static Slot attach(List<Slot> roster, Map<String, List<Slot>> byRegion, SectPlanningRules layouts,
                               Slot parent, String family, String tier) {
        Slot slot = roster.stream()
                .filter(candidate -> candidate.parentSiteId.isEmpty() && candidate.layout.tier.equals(tier))
                .min(Comparator.comparing(candidate -> !candidate.layout.family.equals(family))).orElseThrow();
        Slot child = slot.under(parent, layouts.family(family, tier));
        roster.set(child.openingRank, child);
        byRegion.get(child.region).set(child.index, child);
        return child;
    }
}
