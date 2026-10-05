package com.example.myvillage.sim.cli;

import com.example.myvillage.region.runtime.RegionGraph;
import com.example.myvillage.region.runtime.RegionProfile;
import com.example.myvillage.region.runtime.RegionTopologyGenerator;
import com.example.myvillage.region.runtime.Ruleset;
import com.example.myvillage.sim.Overview;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.SimObserver;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.model.StateCodec;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline world-sim runner (no Minecraft, no Gradle):
 * <pre>
 * run --seed N --tier small|medium|large --years N [--days-per-year N] --out FILE.json [--text FILE.txt]
 *     [--root REPO] [--resources DIR]... [--lang zh_cn]
 * </pre>
 * Builds the seed's region graph from the source tree, runs genesis (with prehistory) and N more
 * years, and writes one JSON document: configuration, final state, a census per year from day 0,
 * and every event ever emitted. {@code --text} writes the chronicle as prose. {@code --resources}
 * directories are searched before {@code <root>/src/main/resources} (for trying data variants).
 */
public final class SimCli {
    private SimCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || !args[0].equals("run")) {
            usage("expected the command 'run'");
        }
        Map<String, List<String>> opts = parse(args);
        long seed = Long.parseLong(one(opts, "seed", null));
        String tier = one(opts, "tier", "small");
        int years = Integer.parseInt(one(opts, "years", null));
        Path root = Path.of(one(opts, "root", ".")).toAbsolutePath().normalize();
        Path out = Path.of(one(opts, "out", null));
        String text = one(opts, "text", "");
        String langId = one(opts, "lang", "zh_cn");
        List<Path> searched = new ArrayList<>();
        for (String dir : opts.getOrDefault("resources", List.of())) {
            searched.add(Path.of(dir));
        }
        searched.add(root.resolve("src/main/resources"));

        SimData data = WorldSim.loadData(path -> open(searched, path));
        int dpy = Integer.parseInt(one(opts, "days-per-year",
                String.valueOf(data.rules().time().defaultDaysPerYear())));
        RegionGraph graph = buildGraph(root, seed);

        long started = System.nanoTime();
        Recorder recorder = new Recorder(dpy);
        WorldSim sim = WorldSim.genesis(seed, graph, data, tier, dpy, recorder);
        long genesisDone = System.nanoTime();
        for (long d = 0; d < (long) years * dpy; d++) {
            sim.step(dpy);
        }
        long finished = System.nanoTime();

        Lang lang = new Lang(root.resolve("src/main/resources/assets/myvillage/lang/" + langId + ".json"));
        ChronicleWriter writer = new ChronicleWriter(lang, dpy, sim.prehistoryDays());
        JsonObject doc = new JsonObject();
        JsonObject config = new JsonObject();
        config.addProperty("seed", seed);
        config.addProperty("tier", tier);
        config.addProperty("years", years);
        config.addProperty("days_per_year", dpy);
        config.addProperty("prehistory_years", data.rules().time().prehistoryYears());
        config.addProperty("prehistory_days", sim.prehistoryDays());
        config.addProperty("final_day", sim.day());
        config.add("regions", JsonParser.parseString(graph.toCanonicalJson()));
        doc.add("config", config);
        doc.add("final_state", JsonParser.parseString(new String(sim.toBytes(), StandardCharsets.UTF_8)));
        doc.add("census", recorder.census);
        JsonArray events = new JsonArray();
        for (SimEvent e : recorder.events) {
            JsonObject o = StateCodec.event(e);
            o.addProperty("text", writer.render(e));
            events.add(o);
        }
        doc.add("events", events);
        write(out, new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(doc));
        if (!text.isEmpty()) {
            write(Path.of(text), writer.write(sim, graph, recorder.events, seed, tier, years));
        }
        Overview o = sim.overview(dpy);
        System.out.printf("seed %d %s: %d years after %d prehistory; population %d, sects %d active / %d destroyed, "
                        + "%d events (%d kept); genesis+prehistory %.2fs, run %.2fs%n",
                seed, tier, years, data.rules().time().prehistoryYears(), o.population(), o.activeSects(),
                o.destroyedSects(), recorder.events.size(), sim.recentEvents(1, Integer.MAX_VALUE).size(),
                (genesisDone - started) / 1e9, (finished - genesisDone) / 1e9);
    }

    /** Records every event and a census on day 0 and at the start of every year. */
    static final class Recorder implements SimObserver {
        final List<SimEvent> events = new ArrayList<>();
        final JsonArray census = new JsonArray();
        private final int dpy;

        Recorder(int dpy) {
            this.dpy = dpy;
        }

        @Override
        public void onEvent(SimEvent event) {
            events.add(event);
        }

        @Override
        public void onDayEnd(WorldSim sim) {
            if (sim.day() % dpy != 0) {
                return;
            }
            Overview o = sim.overview(dpy);
            JsonObject c = new JsonObject();
            c.addProperty("day", sim.day());
            c.addProperty("year", o.date().signedYear());
            c.addProperty("population", o.population());
            JsonObject realms = new JsonObject();
            o.livingByRealm().forEach(realms::addProperty);
            c.add("realms", realms);
            c.addProperty("active_sects", o.activeSects());
            JsonArray sects = new JsonArray();
            for (SectView s : sim.sects(false)) {
                JsonObject so = new JsonObject();
                so.addProperty("id", s.id());
                so.addProperty("name", s.name());
                so.addProperty("members", s.memberCount());
                so.addProperty("top_realm", s.topRealmId());
                so.addProperty("resources", Math.round(s.resources() * 10) / 10.0);
                so.addProperty("prestige", Math.round(s.prestige() * 10) / 10.0);
                sects.add(so);
            }
            c.add("sects", sects);
            census.add(c);
        }
    }

    /** The seed's region graph from {@code src/main/resources/data/myvillage/worldgen}. */
    public static RegionGraph buildGraph(Path root, long seed) throws IOException {
        Path worldgen = root.resolve("src/main/resources/data/myvillage/worldgen");
        Ruleset ruleset;
        try (Reader r = Files.newBufferedReader(worldgen.resolve("region_topology.json"), StandardCharsets.UTF_8)) {
            ruleset = Ruleset.fromJson(JsonParser.parseReader(r).getAsJsonObject());
        }
        List<Path> files = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(worldgen.resolve("region_profile"), "*.json")) {
            stream.forEach(files::add);
        }
        files.sort(Path::compareTo);
        List<RegionProfile> catalog = new ArrayList<>();
        for (Path p : files) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                catalog.add(RegionProfile.fromJson(JsonParser.parseReader(r).getAsJsonObject()));
            }
        }
        return RegionTopologyGenerator.generate(seed, ruleset, catalog);
    }

    private static InputStream open(List<Path> roots, String path) throws IOException {
        for (Path root : roots) {
            Path file = root.resolve(path);
            if (Files.isRegularFile(file)) {
                return Files.newInputStream(file);
            }
        }
        return null;
    }

    private static void write(Path file, String content) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    private static Map<String, List<String>> parse(String[] args) {
        Map<String, List<String>> opts = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                usage("bad argument " + args[i]);
            }
            opts.computeIfAbsent(args[i].substring(2), k -> new ArrayList<>()).add(args[++i]);
        }
        return opts;
    }

    private static String one(Map<String, List<String>> opts, String key, String def) {
        List<String> values = opts.get(key);
        if (values == null || values.isEmpty()) {
            if (def == null) {
                usage("missing --" + key);
            }
            return def;
        }
        return values.get(values.size() - 1);
    }

    private static void usage(String problem) {
        System.err.println(problem);
        System.err.println("usage: run --seed N --tier small|medium|large --years N [--days-per-year N] "
                + "--out FILE.json [--text FILE.txt] [--root REPO] [--resources DIR]... [--lang zh_cn]");
        System.exit(2);
    }
}
