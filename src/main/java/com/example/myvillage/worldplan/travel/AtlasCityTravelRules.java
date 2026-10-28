package com.example.myvillage.worldplan.travel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/** One authored file supplies server budgets, hazards and client gesture thresholds. */
public record AtlasCityTravelRules(boolean enabled, int searchRadius, int verticalRadius, int hazardPadding,
        int maxChunksPerRequest, int maxConcurrentRequests, int loadTimeoutTicks, int requestCooldownTicks,
        int feedbackIntervalTicks, int candidateChecksPerTick, int clickSlopPixels, int markerHitRadiusPixels,
        Set<String> hazardBlocks, Set<String> hazardTags) {
    public static final String RESOURCE = "myvillage:worldplan/atlas_city_travel.json";
    public static final String CLASSPATH = "/data/myvillage/worldplan/atlas_city_travel.json";

    public AtlasCityTravelRules {
        hazardBlocks = Set.copyOf(hazardBlocks);
        hazardTags = Set.copyOf(hazardTags);
        // Hard ceilings are safety bounds, not alternate runtime tuning defaults.
        if (searchRadius < 1 || searchRadius > 8 || verticalRadius < 0 || verticalRadius > 8
                || hazardPadding < 0 || hazardPadding > 1 || maxChunksPerRequest < 1 || maxChunksPerRequest > 9
                || maxConcurrentRequests < 1 || maxConcurrentRequests > 8
                || loadTimeoutTicks < 20 || loadTimeoutTicks > 2400
                || requestCooldownTicks < 1 || requestCooldownTicks > 1200
                || feedbackIntervalTicks < 1 || feedbackIntervalTicks > 1200
                || candidateChecksPerTick < 1 || candidateChecksPerTick > 1024
                || clickSlopPixels < 1 || clickSlopPixels > 12
                || markerHitRadiusPixels < 1 || markerHitRadiusPixels > 16
                || hazardBlocks.isEmpty() || hazardBlocks.size() > 128 || hazardTags.size() > 16)
            throw new IllegalArgumentException("舆图传送规则超出安全预算");
        for (String id : hazardBlocks) requireResourceId(id);
        for (String id : hazardTags) requireResourceId(id);
    }

    public static AtlasCityTravelRules parse(JsonObject o) {
        if (integer(o, "schema") != 1) throw new IllegalArgumentException("未知舆图传送规则版本");
        if (!o.get("enabled").isJsonPrimitive() || !o.getAsJsonPrimitive("enabled").isBoolean())
            throw new IllegalArgumentException("enabled 必须为布尔值");
        return new AtlasCityTravelRules(o.get("enabled").getAsBoolean(), integer(o,"search_radius"),
                integer(o,"vertical_radius"), integer(o,"hazard_padding"), integer(o,"max_chunks_per_request"),
                integer(o,"max_concurrent_requests"), integer(o,"load_timeout_ticks"),
                integer(o,"request_cooldown_ticks"), integer(o,"feedback_interval_ticks"),
                integer(o,"candidate_checks_per_tick"), integer(o,"click_slop_pixels"),
                integer(o,"marker_hit_radius_pixels"), ids(o,"hazard_blocks"), ids(o,"hazard_tags"));
    }

    private static int integer(JsonObject o, String key) {
        var n = o.getAsJsonPrimitive(key);
        if (!n.isNumber()) throw new IllegalArgumentException(key + " 必须为整数");
        return n.getAsBigDecimal().intValueExact();
    }

    private static Set<String> ids(JsonObject o, String key) {
        Set<String> ids = new LinkedHashSet<>();
        for (var element : o.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
                    || !ids.add(element.getAsString())) throw new IllegalArgumentException("非法或重复的危险方块标识");
        }
        return ids;
    }

    private static void requireResourceId(String id) {
        if (id == null || id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("非法危险方块资源路径");
    }

    /** Client UI uses the packaged copy; server resource overrides are loaded by the server lifecycle. */
    public static AtlasCityTravelRules bundled() { return Bundled.VALUE; }
    private static final class Bundled {
        private static final AtlasCityTravelRules VALUE = load();
        private static AtlasCityTravelRules load() {
            try (var in = AtlasCityTravelRules.class.getResourceAsStream(CLASSPATH)) {
                if (in == null) throw new IllegalStateException("缺少舆图传送规则");
                return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (IOException error) { throw new IllegalStateException("无法读取舆图传送规则", error); }
        }
    }
}
