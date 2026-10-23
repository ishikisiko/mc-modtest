package com.example.myvillage.story.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, strictly validated production geometry. No world access or client types. */
public record SceneLayout(String id, String version, List<Site> sites, Bounds bounds,
                          Map<String, Station> positions, List<Target> targets, List<Cell> roads) {
    private static final Set<String> ROLES = Set.of("spawn", "guide", "pharmacist", "zhou_he", "zhou_he_home", "warden");
    private static final Set<String> TARGETS = Set.of("water_sample", "withered_crop", "sample_examination",
            "medicine_chest", "transport_record", "recut_array", "fresh_cut", "old_array_text", "spring_core");
    private static final Set<String> SITES = Set.of("relay_inn", "apothecary", "canal_gardens", "old_relay", "spring_array", "initiation_court");

    public record Pos(int x, int y, int z) {
        public Pos add(Pos other) { return new Pos(x + other.x, y + other.y, z + other.z); }
    }
    public record Bounds(Pos min, Pos max) {
        public boolean contains(Pos p) {
            return p.x >= min.x && p.x <= max.x && p.y >= min.y && p.y <= max.y && p.z >= min.z && p.z <= max.z;
        }
    }
    public record Site(String id, String template, Pos origin, Pos size, String sha256) {
        public Bounds bounds() { return new Bounds(origin, origin.add(new Pos(size.x - 1, size.y - 1, size.z - 1))); }
    }
    public record Station(Pos pos, Pos approach) {}
    public record Target(String id, String block, String state, Pos pos, Pos approach) {}
    public record Cell(Pos pos, String state) {}

    public SceneLayout {
        sites = List.copyOf(sites);
        positions = Map.copyOf(positions);
        targets = List.copyOf(targets);
        roads = List.copyOf(roads);
    }

    public static SceneLayout parse(JsonObject root) {
        require(integer(root.get("schema")) == 1, "unsupported scene schema");
        String id = text(root, "id"), version = text(root, "version");
        require(id.equals("qianzhen_chapter_1"), "unknown scene id");
        require(version.matches("[A-Za-z0-9_.-]{1,80}"), "invalid scene version");
        JsonObject rawBounds = root.getAsJsonObject("bounds");
        Bounds bounds = new Bounds(pos(rawBounds.get("min")), pos(rawBounds.get("max")));
        require(bounds.min.x <= bounds.max.x && bounds.min.z <= bounds.max.z && bounds.min.y == 0 && bounds.max.y <= 128,
                "invalid scene bounds");
        require((long)(bounds.max.x-bounds.min.x+1)*(bounds.max.y+1)*(bounds.max.z-bounds.min.z+1) <= 2_000_000,
                "scene bounds exceed deployment budget");
        List<Site> sites = new ArrayList<>();
        Set<String> siteIds = new HashSet<>();
        for (JsonElement element : root.getAsJsonArray("sites")) {
            JsonObject raw = element.getAsJsonObject();
            String siteId = text(raw, "id");
            require(siteIds.add(siteId), "duplicate site");
            String template = text(raw, "template"), sha = text(raw, "sha256");
            require(template.equals("myvillage:qianzhen/"+siteId) && SITES.contains(siteId), "invalid site template");
            require(sha.matches("[0-9a-f]{64}"), "invalid template digest");
            Site site = new Site(siteId, template, pos(raw.get("origin")), pos(raw.get("size")), sha);
            require(site.size.x > 0 && site.size.y > 0 && site.size.z > 0 && site.origin.y == 0, "invalid site size/origin");
            require(bounds.contains(site.bounds().min) && bounds.contains(site.bounds().max), "site outside bounds");
            for (Site other : sites) require(!overlaps(site.bounds(), other.bounds()), "overlapping site volumes");
            sites.add(site);
        }
        require(siteIds.equals(SITES), "missing scene sites");
        Map<String, Station> positions = new LinkedHashMap<>();
        for (var entry : root.getAsJsonObject("positions").entrySet()) {
            JsonObject value = entry.getValue().getAsJsonObject();
            Station station = new Station(pos(value.get("pos")), pos(value.get("approach")));
            require(bounds.contains(station.pos) && bounds.contains(station.approach), "station outside bounds");
            positions.put(entry.getKey(), station);
        }
        Set<String> stationIds = new HashSet<>(ROLES); stationIds.addAll(TARGETS);
        require(positions.keySet().equals(stationIds), "scene position API differs");
        List<Target> targets = new ArrayList<>();
        Set<String> targetIds = new HashSet<>(); Set<Pos> targetCells = new HashSet<>();
        for (JsonElement element : root.getAsJsonArray("targets")) {
            JsonObject raw = element.getAsJsonObject();
            Target target = new Target(text(raw, "id"), text(raw, "block"), state(raw), pos(raw.get("pos")), pos(raw.get("approach")));
            require(targetIds.add(target.id) && targetCells.add(target.pos), "duplicate target id/position");
            require(blockId(target.state).equals(target.block), "target state/block differ");
            require(new Station(target.pos, target.approach).equals(positions.get(target.id)), "target station differs");
            require(sites.stream().anyMatch(site -> site.bounds().contains(target.pos)), "target outside buildings");
            targets.add(target);
        }
        require(targetIds.equals(TARGETS), "scene target API differs");
        List<Cell> roads = new ArrayList<>(); Set<Pos> cells = new HashSet<>();
        for (JsonElement element : root.getAsJsonArray("roads")) {
            JsonObject raw = element.getAsJsonObject(); Cell cell = new Cell(pos(raw.get("pos")), state(raw));
            require(cells.add(cell.pos) && bounds.contains(cell.pos), "duplicate/outside road cell");
            require(cell.pos.y >= 0 && cell.pos.y <= 4, "road outside ground clearance");
            require(sites.stream().noneMatch(site -> site.bounds().contains(cell.pos)), "road overwrites building");
            roads.add(cell);
        }
        require(!roads.isEmpty() && roads.size() <= 50_000, "invalid road cell count");
        return new SceneLayout(id, version, sites, bounds, positions, targets, roads);
    }

    private static boolean overlaps(Bounds a, Bounds b) {
        return a.min.x <= b.max.x && a.max.x >= b.min.x && a.min.y <= b.max.y && a.max.y >= b.min.y
                && a.min.z <= b.max.z && a.max.z >= b.min.z;
    }
    private static String state(JsonObject raw) {
        String state = text(raw, "state");
        require(state.matches("[a-z0-9_.-]+:[a-z0-9_./-]+(?:\\[[a-z0-9_=,.-]+])?"), "invalid blockstate syntax");
        return state;
    }
    static String blockId(String state) { int i = state.indexOf('['); return i < 0 ? state : state.substring(0, i); }
    private static Pos pos(JsonElement element) {
        JsonArray raw = element.getAsJsonArray(); require(raw.size() == 3, "position must have three integers");
        Pos p = new Pos(integer(raw.get(0)), integer(raw.get(1)), integer(raw.get(2)));
        require(Math.abs((long)p.x) <= 1024 && p.y >= 0 && p.y <= 128 && Math.abs((long)p.z) <= 1024, "unbounded local position");
        return p;
    }
    private static int integer(JsonElement raw) {
        require(raw != null && raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber(), "expected integer");
        try { return raw.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException invalid) { throw new IllegalArgumentException("expected bounded integer", invalid); }
    }
    private static String text(JsonObject raw, String key) {
        JsonElement value = raw.get(key);
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), "missing text: "+key);
        String text = value.getAsString(); require(!text.isBlank() && text.length() <= 256, "invalid text: "+key); return text;
    }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
}
