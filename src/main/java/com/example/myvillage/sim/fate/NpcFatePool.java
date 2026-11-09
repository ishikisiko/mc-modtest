package com.example.myvillage.sim.fate;

import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.model.Person;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** 与玩家注册表同源的纯抽取元数据；运行时由当前服务器注册表构造。 */
public final class NpcFatePool {
    private static final String DIRECTORY = "data/myvillage/myvillage/innate_fate";
    public record Entry(String id, int cost, String group, boolean enabled, String element,
                        int minimumAptitude, int maximumRoots) {
        public Entry {
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || cost < 1 || cost > 6 || group.isBlank()
                    || minimumAptitude < 0 || maximumRoots < 1 || maximumRoots > 5)
                throw new IllegalArgumentException("NPC 天赋抽取元数据无效：" + id);
        }
        public boolean eligible(int[] root) {
            if (!enabled) return false;
            if (element.isEmpty()) return true;
            int index = Person.ELEMENTS.indexOf(element.startsWith("myvillage:") ? element.substring(10) : element);
            return index >= 0 && root[index] >= minimumAptitude
                    && Arrays.stream(root).filter(v -> v > 0).count() <= maximumRoots;
        }
    }
    private record Triple(int a, int b, int c, int cost) {
        boolean eligible(boolean[] allowed) { return allowed[a] && allowed[b] && allowed[c]; }
    }
    private final int budget;
    private final List<Entry> entries;
    private final List<Triple> triples;

    public NpcFatePool(int budget, Collection<Entry> definitions) {
        if (budget < NpcFateRoll.COUNT + 1 || budget > NpcFateRoll.COUNT * 6)
            throw new IllegalArgumentException("NPC 天赋预算须为 4 至 18 点");
        this.budget = budget;
        var sorted = new TreeMap<String, Entry>();
        for (var entry : definitions)
            if (sorted.put(entry.id(), entry) != null) throw new IllegalArgumentException("重复天赋：" + entry.id());
        entries = List.copyOf(sorted.values());
        var choices = new ArrayList<Triple>();
        for (int a = 0; a < entries.size(); a++) for (int b = a + 1; b < entries.size(); b++) {
            var first = entries.get(a); var second = entries.get(b);
            if (!first.enabled() || !second.enabled() || first.group().equals(second.group())) continue;
            for (int c = b + 1; c < entries.size(); c++) {
                var third = entries.get(c);
                int cost = first.cost() + second.cost() + third.cost();
                if (third.enabled() && cost <= budget && !third.group().equals(first.group())
                        && !third.group().equals(second.group())) choices.add(new Triple(a, b, c, cost));
            }
        }
        triples = List.copyOf(choices);
    }

    public int budget() { return budget; }
    public List<Entry> entries() { return entries; }

    public NpcFateRoll roll(int[] root, boolean prodigy, SimRng random) {
        if (root.length != Person.ELEMENTS.size() || Arrays.stream(root).anyMatch(v -> v < 0))
            throw new IllegalArgumentException("NPC 灵根无效");
        boolean[] allowed = new boolean[entries.size()];
        for (int i = 0; i < entries.size(); i++) allowed[i] = entries.get(i).eligible(root);
        int[] counts = new int[budget + 1];
        for (var triple : triples) if (triple.eligible(allowed)) counts[triple.cost()]++;
        var totals = new ArrayList<Integer>();
        for (int cost = NpcFateRoll.COUNT; cost <= budget; cost++)
            if (counts[cost] > 0 && (prodigy ? cost == budget : cost < budget)) totals.add(cost);
        if (totals.isEmpty()) throw new IllegalArgumentException("当前天赋池无法满足 NPC 的预算、灵根与互斥条件");
        int spent = totals.get(random.nextInt(totals.size()));
        int index = random.nextInt(counts[spent]);
        for (var triple : triples) if (triple.cost() == spent && triple.eligible(allowed) && index-- == 0)
            return new NpcFateRoll(List.of(entries.get(triple.a()).id(), entries.get(triple.b()).id(),
                    entries.get(triple.c()).id()), budget, spent).validateIdentity(prodigy);
        throw new IllegalStateException("NPC 天赋组合计数不一致");
    }

    private static final class Holder { static final NpcFatePool VALUE = loadBundled(); }
    public static NpcFatePool bundled() { return Holder.VALUE; }

    public static int configuredBudget() {
        try (var input = NpcFatePool.class.getResourceAsStream("/data/myvillage/fates/npc_rules.json")) {
            if (input == null) throw new IllegalStateException("缺少 NPC 天赋规则");
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            return json.get("point_budget").getAsBigDecimal().intValueExact();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    private static Entry read(String id, JsonObject json) {
        return new Entry(id, json.get("cost").getAsInt(), json.get("group").getAsString(),
                !json.has("enabled") || json.get("enabled").getAsBoolean(),
                json.has("element") ? json.get("element").getAsString() : "",
                json.has("minimum_aptitude") ? json.get("minimum_aptitude").getAsInt() : 0,
                json.has("maximum_roots") ? json.get("maximum_roots").getAsInt() : 5);
    }

    /** 离线命簿直接发现实际 JSON 文件；新增词条无需另维护 NPC 白名单。 */
    private static NpcFatePool loadBundled() {
        var entries = new TreeMap<String, Entry>();
        try {
            var roots = NpcFatePool.class.getClassLoader().getResources(DIRECTORY);
            while (roots.hasMoreElements()) {
                var url = roots.nextElement();
                if (url.getProtocol().equals("file")) {
                    Path directory = Path.of(url.toURI());
                    try (var files = Files.walk(directory)) {
                        for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                            String id = "myvillage:" + directory.relativize(file).toString().replace('\\', '/').replaceFirst("\\.json$", "");
                            entries.put(id, read(id, JsonParser.parseString(Files.readString(file)).getAsJsonObject()));
                        }
                    }
                } else if (url.openConnection() instanceof java.net.JarURLConnection connection) {
                    connection.setUseCaches(false);
                    try (var jar = connection.getJarFile()) {
                        for (var file : jar.stream().filter(e -> e.getName().startsWith(DIRECTORY + "/") && e.getName().endsWith(".json")).toList()) {
                            String id = "myvillage:" + file.getName().substring(DIRECTORY.length() + 1, file.getName().length() - 5);
                            try (var reader = new InputStreamReader(jar.getInputStream(file), StandardCharsets.UTF_8)) {
                                entries.put(id, read(id, JsonParser.parseReader(reader).getAsJsonObject()));
                            }
                        }
                    }
                }
            }
            if (entries.isEmpty()) throw new IllegalStateException("未发现 NPC 天赋词条文件");
            return new NpcFatePool(configuredBudget(), entries.values());
        } catch (Exception e) { throw new IllegalStateException("NPC 天赋池加载失败", e); }
    }
}
