package com.example.myvillage.sim.cli;

import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.sim.Overview;
import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.SimDate;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.WorldSim;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Renders a run as chronicle prose for a terminal: the notable and major events grouped by era
 * year, then short biographies of the most eventful people with all their events. Every line comes
 * from the language file.
 */
public final class ChronicleWriter {
    public static final String R = "world_sim.report.";
    /** Report keys with their param counts (checked against both language files by a test). */
    public static final Map<String, Integer> KEYS = Map.ofEntries(
            Map.entry(R + "title", 0),
            Map.entry(R + "config", 5),
            Map.entry(R + "tier.small", 0),
            Map.entry(R + "tier.medium", 0),
            Map.entry(R + "tier.large", 0),
            Map.entry(R + "regions", 0),
            Map.entry(R + "region", 3),
            Map.entry(R + "region.closed", 3),
            Map.entry(R + "chronicle", 0),
            Map.entry(R + "year", 1),
            Map.entry(R + "summary", 4),
            Map.entry(R + "realm_count", 2),
            Map.entry(R + "sects_final", 0),
            Map.entry(R + "sect_line", 5),
            Map.entry(R + "sect_line.destroyed", 2),
            Map.entry(R + "biographies", 0),
            Map.entry(R + "bio.born", 2),
            Map.entry(R + "bio.alive", 3),
            Map.entry(R + "bio.dead", 3),
            Map.entry(R + "rogue", 0),
            Map.entry(R + "bio.repeat", 4),
            Map.entry(R + "end", 0));

    private static final String RULE = "────────────────────────────────────────";
    private static final int BIOGRAPHIES = 8;

    private final Lang lang;
    private final int dpy;
    private final long prehistoryDays;

    public ChronicleWriter(Lang lang, int dpy, long prehistoryDays) {
        this.lang = lang;
        this.dpy = dpy;
        this.prehistoryDays = prehistoryDays;
    }

    public String render(SimEvent e) {
        return lang.format(e.textKey(), e.params());
    }

    public String date(long day) {
        SimDate d = SimDate.of(day, prehistoryDays, dpy);
        return lang.format(d.textKey(), d.year());
    }

    public String write(WorldSim sim, RegionGraph graph, List<SimEvent> events, long seed, String tier, int years) {
        StringBuilder out = new StringBuilder();
        out.append(lang.raw(R + "title")).append('\n');
        out.append(lang.format(R + "config", seed, lang.raw(R + "tier." + tier), dpy, prehistoryDays / dpy, years))
                .append('\n').append(RULE).append('\n');
        out.append(lang.raw(R + "regions")).append('\n');
        for (GenRegion r : graph.regions()) {
            String key = r.admittedSubjects().contains("sect") ? R + "region" : R + "region.closed";
            out.append("  ").append(lang.format(key, r.displayName(), r.qi().lo() + "—" + r.qi().hi(),
                    r.danger().lo() + "—" + r.danger().hi())).append('\n');
        }
        out.append('\n').append(lang.raw(R + "chronicle")).append('\n').append(RULE).append('\n');
        long year = Long.MIN_VALUE;
        for (SimEvent e : events) {
            if (e.importance() < 2) {
                continue;
            }
            long y = SimDate.of(e.day(), prehistoryDays, dpy).signedYear();
            if (y != year) {
                year = y;
                out.append('\n').append(lang.format(R + "year", date(e.day()))).append('\n');
            }
            out.append(e.importance() >= 3 ? "  ◆ " : "  · ").append(render(e)).append('\n');
        }
        out.append('\n').append(RULE).append('\n');
        summary(out, sim);
        out.append('\n').append(lang.raw(R + "biographies")).append('\n').append(RULE).append('\n');
        for (int id : mostEventful(events, BIOGRAPHIES)) {
            biography(out, sim, id, events);
        }
        out.append('\n').append(lang.raw(R + "end")).append('\n');
        return out.toString();
    }

    private void summary(StringBuilder out, WorldSim sim) {
        Overview o = sim.overview(dpy);
        List<String> realms = new ArrayList<>();
        for (Map.Entry<String, Integer> e : o.livingByRealm().entrySet()) {
            realms.add(lang.format(R + "realm_count", "@world_sim.realm." + e.getKey(), e.getValue()));
        }
        out.append(lang.format(R + "summary", o.population(), String.join("，", realms), o.activeSects(),
                o.destroyedSects())).append('\n');
        out.append(lang.raw(R + "sects_final")).append('\n');
        for (SectView s : sim.sects(true)) {
            if (s.state().equals("active")) {
                out.append("  ").append(lang.format(R + "sect_line", s.name(), regionName(sim, s.regionId()),
                        s.masterName(), s.memberCount(), Math.round(s.prestige()))).append('\n');
            } else {
                out.append("  ").append(lang.format(R + "sect_line.destroyed", s.name(), date(s.destroyedDay())))
                        .append('\n');
            }
        }
    }

    private String regionName(WorldSim sim, String regionId) {
        return sim.region(regionId, dpy).displayName();
    }

    /** People ranked by how much happened to them: major events weigh most. */
    static List<Integer> mostEventful(List<SimEvent> events, int count) {
        Map<Integer, Integer> score = new HashMap<>();
        for (SimEvent e : events) {
            int w = e.importance() == 3 ? 12 : e.importance() == 2 ? 4 : 1;
            for (int actor : e.actors()) {
                score.merge(actor, w, Integer::sum);
            }
        }
        TreeMap<Integer, Integer> sorted = new TreeMap<>(score);
        List<Integer> ids = new ArrayList<>(sorted.keySet());
        ids.sort(Comparator.comparingInt((Integer id) -> -sorted.get(id)).thenComparingInt(id -> id));
        return ids.subList(0, Math.min(count, ids.size()));
    }

    private void biography(StringBuilder out, WorldSim sim, int id, List<SimEvent> events) {
        Optional<PersonView> found = sim.person(id);
        if (found.isEmpty()) {
            return;
        }
        PersonView p = found.get();
        String title = p.title().isEmpty() ? "" : "（" + p.title() + "）";
        out.append('\n').append("【").append(p.name()).append(title).append("】").append('\n');
        String root = p.rootGrade().isEmpty() ? "" : lang.raw("world_sim.root." + p.rootGrade());
        out.append("  ").append(lang.format(R + "bio.born", date(p.birthDay()), root));
        String stage = "@world_sim.stage." + p.realmId() + "." + (p.stage() + 1);
        if (p.alive()) {
            String sect = p.sectId() >= 0 ? p.sectName() : lang.raw(R + "rogue");
            String rank = p.sectId() >= 0 ? lang.raw("world_sim.rank." + p.rank()) : "";
            out.append(lang.format(R + "bio.alive", sect, rank, lang.raw(stage.substring(1))));
        } else {
            long age = (p.deathDay() - p.birthDay()) / dpy;
            out.append(lang.format(R + "bio.dead", date(p.deathDay()), age, lang.raw(stage.substring(1))));
        }
        out.append('\n');
        List<SimEvent> own = new ArrayList<>();
        for (SimEvent e : events) {
            if (e.actors().contains(id)) {
                own.add(e);
            }
        }
        // Runs of the same kind of line (stage after stage, failure after failure) fold into one.
        for (int i = 0; i < own.size(); ) {
            int j = i + 1;
            while (j < own.size() && own.get(j).textKey().equals(own.get(i).textKey())
                    && own.get(j).subject() == own.get(i).subject()) {
                j++;
            }
            SimEvent last = own.get(j - 1);
            if (j - i >= 3) {
                out.append("    ").append(lang.format(R + "bio.repeat", date(own.get(i).day()), date(last.day()),
                        render(last), j - i)).append('\n');
            } else {
                for (int k = i; k < j; k++) {
                    out.append("    ").append(date(own.get(k).day())).append("　").append(render(own.get(k))).append('\n');
                }
            }
            i = j;
        }
    }
}
