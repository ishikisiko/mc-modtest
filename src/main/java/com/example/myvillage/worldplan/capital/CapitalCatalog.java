package com.example.myvillage.worldplan.capital;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static com.example.myvillage.worldplan.capital.CapitalJson.*;

/** Authored definitions, not placed cities. No implicit classpath loading or worldgen registration. */
public final class CapitalCatalog {
    public record Definition(CapitalRegion regionId, String cityId, String displayName, String appearanceId,
                             String layoutId, String targetScale, String terrainProfile, String definitionStage,
                             List<String> requiredServiceRoles) {
        public Definition {
            Objects.requireNonNull(regionId); id(cityId);
            for (String value : List.of(displayName, appearanceId, layoutId, targetScale, terrainProfile, definitionStage))
                if (value.isBlank()) throw new IllegalArgumentException("城市定义文本不能为空");
            requiredServiceRoles = List.copyOf(requiredServiceRoles);
            if (requiredServiceRoles.isEmpty() || requiredServiceRoles.stream().anyMatch(String::isBlank)
                    || new HashSet<>(requiredServiceRoles).size() != requiredServiceRoles.size())
                throw new IllegalArgumentException("城市服务职责必须非空且不重复");
        }
    }

    private final JsonObject snapshot;
    private final List<Definition> definitions;
    private final Map<CapitalRegion, Definition> byRegion;
    private final Map<String, Definition> byCity;

    public static CapitalCatalog read(Reader reader) {
        return new CapitalCatalog(JsonParser.parseReader(reader).getAsJsonObject());
    }

    public CapitalCatalog(JsonObject json) {
        snapshot = json.deepCopy();
        if (integer(snapshot, "schema") != 1 || !text(snapshot, "activation").equals("foundation_only"))
            throw new IllegalArgumentException("仅支持未激活的八州基础目录");
        JsonObject policy = object(snapshot, "region_policy");
        var outer = required(policy, "outer_replicas_have_capital");
        if (!text(policy, "scope").equals("core_only")
                || integer(policy, "region_count") != CapitalRegion.values().length
                || integer(policy, "capitals_per_region") != 1
                || !outer.isJsonPrimitive() || !outer.getAsJsonPrimitive().isBoolean() || outer.getAsBoolean()
                || !text(policy, "on_candidate_exhaustion").equals("fail_whole_plan"))
            throw new IllegalArgumentException("目录必须要求八个核心州域各一城，失败不得跳过");
        var decoded = list(snapshot, "capitals", c -> new Definition(CapitalRegion.fromId(text(c, "region_id")),
                text(c, "city_id"), text(c, "display_name"), text(c, "appearance_id"), text(c, "layout_id"),
                text(c, "target_scale"), text(c, "terrain_profile"), text(c, "definition_stage"),
                array(c, "required_service_roles").asList().stream().map(element -> {
                    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
                        throw new IllegalArgumentException("城市服务职责必须为文本");
                    return element.getAsString();
                }).toList()));
        var regionIndex = new EnumMap<CapitalRegion, Definition>(CapitalRegion.class);
        var cityIndex = new HashMap<String, Definition>();
        for (Definition definition : decoded) {
            if (regionIndex.putIfAbsent(definition.regionId, definition) != null
                    || cityIndex.putIfAbsent(definition.cityId, definition) != null)
                throw new IllegalArgumentException("州域与主城身份必须一一对应");
        }
        if (regionIndex.size() != CapitalRegion.values().length)
            throw new IllegalArgumentException("目录必须包含八座主城，包括魔域");
        var ordered = new ArrayList<>(decoded);
        ordered.sort(Comparator.comparing(Definition::regionId));
        definitions = List.copyOf(ordered); byRegion = Map.copyOf(regionIndex); byCity = Map.copyOf(cityIndex);
    }

    public List<Definition> definitions() { return definitions; }

    public Definition capitalOf(CapitalRegion regionId) {
        Definition definition = byRegion.get(regionId);
        if (definition == null) throw new IllegalArgumentException("核心州域没有主城定义: " + regionId);
        return definition;
    }

    public Definition city(String cityId) {
        Definition definition = byCity.get(cityId);
        if (definition == null) throw new IllegalArgumentException("未知主城身份: " + cityId);
        return definition;
    }

    public JsonObject toJson() { return snapshot.deepCopy(); }
}
