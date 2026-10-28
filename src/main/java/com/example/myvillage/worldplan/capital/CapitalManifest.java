package com.example.myvillage.worldplan.capital;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.List;
import java.util.Set;
import static com.example.myvillage.worldplan.capital.CapitalJson.*;

/**
 * Immutable reader for coordination-contract exchange schema 1. Preview metadata is neither a
 * production template nor proof that terrain, routes, buildings or services exist in a world.
 * Unknown extension fields survive a toJson round trip.
 */
public final class CapitalManifest {
    public static final int SCHEMA = 1;
    public static final String STAGE = "preview", UNITS = "block";
    public static final List<String> REQUIRED_KEYS = List.of("schema", "city_id", "region_id", "stage", "units",
            "bounds", "source", "terrain", "districts", "roads", "buildings", "entries", "services");

    public record Source(String kind, String ref) {}
    public record District(String id, String role, CapitalGeometry.Bounds bounds) {}
    public record Road(String id, String kind, double width, List<CapitalGeometry.Point> points) {
        public Road { points = List.copyOf(points); }
    }
    public record Building(String id, String role, String template, CapitalGeometry.Point position,
                           int rotationQuarters) {}
    public record Entry(String id, CapitalGeometry.Point position, String facing) {}
    public record Service(String id, String role, String buildingId, CapitalGeometry.Point position) {}

    /** Data may be an owned relative path or inline JSON. The reader never opens it implicitly. */
    public static final class Terrain {
        private final String kind;
        private final JsonElement data;
        private Terrain(String kind, JsonElement data) { this.kind = kind; this.data = data.deepCopy(); }
        public String kind() { return kind; }
        public JsonElement data() { return data.deepCopy(); }
    }

    private final JsonObject snapshot;
    private final String cityId;
    private final CapitalRegion regionId;
    private final CapitalGeometry.Bounds bounds;
    private final Source source;
    private final Terrain terrain;
    private final List<District> districts;
    private final List<Road> roads;
    private final List<Building> buildings;
    private final List<Entry> entries;
    private final List<Service> services;

    public static CapitalManifest read(Reader reader) {
        return new CapitalManifest(JsonParser.parseReader(reader).getAsJsonObject());
    }

    public CapitalManifest(JsonObject json) {
        snapshot = json.deepCopy();
        for (String key : REQUIRED_KEYS) required(snapshot, key);
        if (integer(snapshot, "schema") != SCHEMA || !STAGE.equals(text(snapshot, "stage"))
                || !UNITS.equals(text(snapshot, "units")))
            throw new IllegalArgumentException("仅支持 exchange schema=1, stage=preview, units=block");
        cityId = id(text(snapshot, "city_id"));
        regionId = CapitalRegion.fromId(text(snapshot, "region_id"));
        bounds = CapitalJson.bounds(object(snapshot, "bounds"));
        JsonObject s = object(snapshot, "source"), t = object(snapshot, "terrain");
        source = new Source(text(s, "kind"), text(s, "ref"));
        JsonElement data = required(t, "data");
        if (!data.isJsonArray() && !data.isJsonObject()
                && !(data.isJsonPrimitive() && data.getAsJsonPrimitive().isString() && !data.getAsString().isBlank()))
            throw new IllegalArgumentException("terrain.data 必须为相对路径或内联数组/对象");
        terrain = new Terrain(text(t, "kind"), data);
        districts = list(snapshot, "districts", d -> new District(id(text(d, "id")), text(d, "role"), CapitalJson.bounds(object(d, "bounds"))));
        roads = list(snapshot, "roads", r -> {
            double width = number(required(r, "width"));
            List<CapitalGeometry.Point> points = array(r, "points").asList().stream().map(CapitalJson::point).toList();
            if (width <= 0 || points.size() < 2) throw new IllegalArgumentException("道路需要正宽度和至少两个路径点");
            return new Road(id(text(r, "id")), text(r, "kind"), width, points);
        });
        buildings = list(snapshot, "buildings", b -> {
            int rotation = integer(b, "rotation_quarters");
            if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("建筑旋转必须为 0..3");
            return new Building(id(text(b, "id")), text(b, "role"), text(b, "template"),
                    point(required(b, "position")), rotation);
        });
        entries = list(snapshot, "entries", e -> {
            String facing = text(e, "facing");
            if (!Set.of("north", "south", "east", "west").contains(facing))
                throw new IllegalArgumentException("未知城市入口朝向: " + facing);
            return new Entry(id(text(e, "id")), point(required(e, "position")), facing);
        });
        services = list(snapshot, "services", v -> new Service(id(text(v, "id")), text(v, "role"),
                id(text(v, "building_id")), point(required(v, "position"))));
        unique(districts, District::id); unique(roads, Road::id); unique(buildings, Building::id);
        unique(entries, Entry::id); unique(services, Service::id);
        Set<String> buildingIds = buildings.stream().map(Building::id).collect(java.util.stream.Collectors.toSet());
        for (Service service : services) if (!buildingIds.contains(service.buildingId))
            throw new IllegalArgumentException("服务锚点引用不存在的建筑: " + service.id);
    }

    public String cityId() { return cityId; }
    public CapitalRegion regionId() { return regionId; }
    public CapitalGeometry.Bounds bounds() { return bounds; }
    public Source source() { return source; }
    public Terrain terrain() { return terrain; }
    public List<District> districts() { return districts; }
    public List<Road> roads() { return roads; }
    public List<Building> buildings() { return buildings; }
    public List<Entry> entries() { return entries; }
    public List<Service> services() { return services; }
    public JsonObject toJson() { return snapshot.deepCopy(); }
}
