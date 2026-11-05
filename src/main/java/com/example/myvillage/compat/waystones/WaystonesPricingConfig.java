package com.example.myvillage.compat.waystones;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** No optional API references: registering this server config is safe without Waystones. */
public final class WaystonesPricingConfig {
    public static final String FILE_NAME = "myvillage-waystones-server.toml";
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue BASE_COST, BLOCKS_PER_STEP, MINIMUM_COEFFICIENT,
            MAXIMUM_COEFFICIENT, INTERDIMENSIONAL_COEFFICIENT, UNKNOWN_REALM_MULTIPLIER;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> REALM_MULTIPLIERS;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("pricing");
        BASE_COST = builder.comment("基础下品灵石费用。最终费用为基础费用 × 大境界倍率 × 路程系数。")
                .worldRestart().defineInRange("base_cost", 1, 1, Integer.MAX_VALUE);
        BLOCKS_PER_STEP = builder.comment("同维度按水平直线距离计算，每档格数；不足一档向上取整。")
                .worldRestart().defineInRange("blocks_per_step", 1000, 1, Integer.MAX_VALUE);
        MINIMUM_COEFFICIENT = builder.comment("最小路程系数。")
                .worldRestart().defineInRange("minimum_distance_coefficient", 1, 1, Integer.MAX_VALUE);
        MAXIMUM_COEFFICIENT = builder.comment("最大路程系数，远距离收费在此封顶。")
                .worldRestart().defineInRange("maximum_distance_coefficient", 10, 1, Integer.MAX_VALUE);
        INTERDIMENSIONAL_COEFFICIENT = builder.comment("跨维度使用固定路程系数，不直接比较不同维度的坐标。")
                .worldRestart().defineInRange("interdimensional_coefficient", 10, 1, Integer.MAX_VALUE);
        UNKNOWN_REALM_MULTIPLIER = builder.comment("未配置的境界或尚未同步的客户端档案使用此倍率；不会按零费用处理。")
                .worldRestart().defineInRange("unknown_realm_multiplier", 10, 1, Integer.MAX_VALUE);
        REALM_MULTIPLIERS = builder.comment("按大境界 ID 指定正整数倍率，小阶段不影响价格。",
                        "各倍率与基础费用、最大路程系数的乘积不得超过 16777216，以保持 Waystones 变量精确。")
                .worldRestart().defineListAllowEmpty("realm_multipliers", List.of(
                        "myvillage:mortal=1", "myvillage:qi_refining=1",
                        "myvillage:foundation_establishment=3", "myvillage:golden_core=10"),
                        WaystonesPricingConfig::validRealmEntry);
        builder.pop();
        SPEC = builder.build();
    }

    private WaystonesPricingConfig() { }

    private static <T> T value(ModConfigSpec.ConfigValue<T> config) {
        return SPEC.isLoaded() ? config.get() : config.getDefault();
    }

    public static WaystonesPricingRules rules() {
        return new WaystonesPricingRules(value(BASE_COST), value(BLOCKS_PER_STEP), value(MINIMUM_COEFFICIENT),
                value(MAXIMUM_COEFFICIENT), value(INTERDIMENSIONAL_COEFFICIENT), value(UNKNOWN_REALM_MULTIPLIER),
                parseRealmMultipliers(value(REALM_MULTIPLIERS)));
    }

    static Map<ResourceLocation, Integer> parseRealmMultipliers(List<? extends String> entries) {
        var result = new LinkedHashMap<ResourceLocation, Integer>();
        for (String entry : entries) {
            if (!validRealmEntry(entry)) throw new IllegalArgumentException("Invalid realm price entry: " + entry);
            String[] parts = entry.split("=", -1);
            var id = ResourceLocation.parse(parts[0].strip());
            if (result.putIfAbsent(id, Integer.parseInt(parts[1].strip())) != null)
                throw new IllegalArgumentException("Duplicate realm price entry: " + id);
        }
        return Map.copyOf(result);
    }

    private static boolean validRealmEntry(Object value) {
        if (!(value instanceof String entry)) return false;
        String[] parts = entry.split("=", -1);
        if (parts.length != 2 || !parts[0].strip().contains(":")
                || ResourceLocation.tryParse(parts[0].strip()) == null || !parts[1].strip().matches("[1-9][0-9]*")) return false;
        try { return Integer.parseInt(parts[1].strip()) > 0; }
        catch (NumberFormatException exception) { return false; }
    }

    public static void validate(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) rules();
    }
}
