package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Long-run health (design §7): several seeds on all three tiers, 300 years after prehistory. The
 * bands were chosen from observed runs (see the checkpoint-2 report) with a margin, then pinned.
 * They are checked at 24 days per year (the cultivation calendar's default since 2026-10-06) and,
 * as a regression for servers that configure the old value, at 6. The runs are independent worlds,
 * so they run in parallel; each one is deterministic regardless of the thread it runs on.
 */
class WorldSimHealthTest {
    /** The cultivation calendar's default days per year; the ledger settles one sim day per calendar day. */
    static final int DPY = 24;
    /** The default before 2026-10-06, kept as a regression. */
    static final int LEGACY_DPY = 6;
    private static final int YEARS = 300;

    /** Band per tier: population fraction of target, imp-3 and imp-2 events per year. */
    private record Band(double popLo, double popHi, double imp3Lo, double imp3Hi, double imp2Hi, int minSects) {
    }

    private static final Map<String, Band> BANDS = Map.of(
            "small", new Band(0.80, 1.15, 0.15, 3.0, 8.0, 2),
            "medium", new Band(0.85, 1.12, 0.4, 10.0, 25.0, 5),
            "large", new Band(0.88, 1.10, 1.0, 20.0, 60.0, 10));

    /** Observations of one run. */
    record Run(String tier, long seed, int dpy, int popMin, int popMax, Map<String, Double> realmAvg, int successions,
               int founded, int destroyed, int minActiveSects, int longestMonopolyYears, double imp3PerYear,
               double imp2PerYear, int coresReached) {
    }

    static Run run(String tier, long seed, int dpy) {
        SimFixtures.Collector c = new SimFixtures.Collector();
        WorldSim sim = WorldSim.genesis(seed, SimFixtures.graph(seed), SimFixtures.data(), tier, dpy, c);
        long start = sim.day();
        int popMin = Integer.MAX_VALUE;
        int popMax = 0;
        int minSects = Integer.MAX_VALUE;
        int monopoly = 0;
        int longest = 0;
        Map<String, Double> realmSum = new TreeMap<>();
        for (int year = 0; year < YEARS; year++) {
            SimFixtures.run(sim, dpy, dpy);
            Overview o = sim.overview(dpy);
            popMin = Math.min(popMin, o.population());
            popMax = Math.max(popMax, o.population());
            minSects = Math.min(minSects, o.activeSects());
            o.livingByRealm().forEach((k, v) -> realmSum.merge(k, (double) v, Double::sum));
            int total = 0;
            int biggest = 0;
            for (SectView s : sim.sects(false)) {
                total += s.memberCount();
                biggest = Math.max(biggest, s.memberCount());
            }
            monopoly = total > 0 && biggest > 0.6 * total ? monopoly + 1 : 0;
            longest = Math.max(longest, monopoly);
        }
        Map<String, Double> avg = new TreeMap<>();
        realmSum.forEach((k, v) -> avg.put(k, v / YEARS));
        int succ = 0;
        int founded = 0;
        int destroyed = 0;
        int imp3 = 0;
        int imp2 = 0;
        int cores = 0;
        for (SimEvent e : c.events) {
            if (e.day() < start) {
                continue;
            }
            switch (e.type()) {
                case "succession" -> succ++;
                case "sect_founded", "sect_split" -> founded++;
                case "sect_destroyed", "sect_extinct" -> destroyed++;
                default -> {
                }
            }
            if (e.importance() == 3) {
                imp3++;
            } else if (e.importance() == 2) {
                imp2++;
            }
            if (e.textKey().contains("breakthrough.golden_core")) {
                cores++;
            }
        }
        return new Run(tier, seed, dpy, popMin, popMax, avg, succ, founded, destroyed, minSects, longest,
                imp3 / (double) YEARS, imp2 / (double) YEARS, cores);
    }

    /** Small seeds 1 to 3, medium seeds 1 and 2, large seed 1, at {@code dpy} days per year, in parallel. */
    private static List<Run> runSet(int dpy) {
        record Spec(String tier, long seed) {
        }
        List<Spec> specs = List.of(new Spec("small", 1), new Spec("small", 2), new Spec("small", 3),
                new Spec("medium", 1), new Spec("medium", 2), new Spec("large", 1));
        SimFixtures.data();
        specs.forEach(s -> SimFixtures.graph(s.seed()));
        return specs.parallelStream().map(s -> run(s.tier(), s.seed(), dpy)).toList();
    }

    @Test
    void worldsStayHealthyForThreeHundredYears() {
        check(runSet(DPY));
    }

    @Test
    void worldsStayHealthyAtTheLegacySixDaysPerYear() {
        check(runSet(LEGACY_DPY));
    }

    private static void check(List<Run> runs) {
        List<String> problems = new ArrayList<>();
        int founded = 0;
        int destroyed = 0;
        for (Run r : runs) {
            System.out.println("health " + r);
            Band b = BANDS.get(r.tier());
            int target = SimFixtures.data().rules().tier(r.tier()).population();
            String id = r.tier() + "/" + r.seed() + " at " + r.dpy() + " days per year: ";
            if (r.popMin() < b.popLo() * target || r.popMax() > b.popHi() * target) {
                problems.add(id + "population " + r.popMin() + ".." + r.popMax() + " outside band of " + target);
            }
            double qi = r.realmAvg().get("qi_refining");
            double fo = r.realmAvg().get("foundation_establishment");
            double gc = r.realmAvg().get("golden_core");
            double ns = r.realmAvg().get("nascent_soul");
            if (!(qi > fo && fo > gc && gc >= ns)) {
                problems.add(id + "no realm pyramid " + r.realmAvg());
            }
            if (ns > 0.02 * target) {
                problems.add(id + "nascent soul is not rare: " + ns);
            }
            if (r.coresReached() < 1) {
                problems.add(id + "nobody reached golden core");
            }
            if (r.successions() < 5) {
                problems.add(id + "masters barely change: " + r.successions());
            }
            if (r.minActiveSects() < b.minSects()) {
                problems.add(id + "active sects fell to " + r.minActiveSects());
            }
            if (r.tier().equals("small") && r.longestMonopolyYears() > 100) {
                problems.add(id + "one sect held over 60% of all members for " + r.longestMonopolyYears() + " years");
            }
            if (r.imp3PerYear() < b.imp3Lo() || r.imp3PerYear() > b.imp3Hi()) {
                problems.add(id + "importance-3 per year " + r.imp3PerYear());
            }
            if (r.imp2PerYear() > b.imp2Hi()) {
                problems.add(id + "importance-2 per year " + r.imp2PerYear());
            }
            founded += r.founded();
            destroyed += r.destroyed();
        }
        if (founded < 1 || destroyed < 1) {
            problems.add("across the run set sects must be both founded (" + founded + ") and destroyed (" + destroyed + ")");
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
