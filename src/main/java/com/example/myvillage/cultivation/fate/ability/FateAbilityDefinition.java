package com.example.myvillage.cultivation.fate.ability;

import com.example.myvillage.cultivation.fate.FateState;
import com.example.myvillage.cultivation.fate.FateDefinition;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;

/** 天命主动能力的独立作者配置；两端读取同一打包资源，不写入已学功法。 */
public record FateAbilityDefinition(ResourceLocation id, ResourceLocation requiredFate, String name,
        String description, int cooldownTicks, double costFraction, long minimumCost, double radius) {
    public FateAbilityDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(requiredFate);
        if (id.toString().length() > 128 || requiredFate.toString().length() > 128
                || id.equals(ResourceLocation.parse("myvillage:auto_weapon_art"))
                || id.equals(ResourceLocation.parse("myvillage:unassigned"))
                || name == null || name.isBlank() || description == null || description.isBlank()
                || cooldownTicks < 1 || cooldownTicks > 12000
                || !Double.isFinite(costFraction) || costFraction <= 0 || costFraction > 1
                || minimumCost < 1 || !Double.isFinite(radius) || radius <= 0 || radius > 32) {
            throw new IllegalArgumentException("天命主动能力配置越界");
        }
    }

    /** 消耗按当前最大灵力向上取整；使用十进制乘法，避免大容量溢出或浮点舍入。 */
    public long cost(long capacity) {
        if (capacity < 0) throw new IllegalArgumentException("灵力上限不能为负");
        long scaled = BigDecimal.valueOf(capacity).multiply(BigDecimal.valueOf(costFraction))
                .setScale(0, RoundingMode.CEILING).longValueExact();
        return Math.max(minimumCost, scaled);
    }

    public boolean granted(FateState state, FateDefinition requiredDefinition) {
        return requiredDefinition != null && requiredDefinition.enabled()
                && state.confirmed() && state.selected().contains(requiredFate.toString());
    }

    /** 移除／禁用天命后立即失去授权；旧 selected ID 继续留档，但不能施放。 */
    public boolean granted(FateState state, RegistryAccess registries) {
        var requiredDefinition = registries.registry(ModCultivationRegistries.INNATE_FATES)
                .flatMap(registry -> registry.getOptional(requiredFate)).orElse(null);
        return granted(state, requiredDefinition);
    }

    private static final class Holder {
        private static final Map<ResourceLocation, FateAbilityDefinition> ALL = load();
    }

    public static Optional<FateAbilityDefinition> find(ResourceLocation id) {
        return Optional.ofNullable(Holder.ALL.get(id));
    }

    public static List<FateAbilityDefinition> all() { return List.copyOf(Holder.ALL.values()); }

    private static Map<ResourceLocation, FateAbilityDefinition> load() {
        String path = "/data/myvillage/combat/fate_abilities.json";
        try (var stream = FateAbilityDefinition.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalArgumentException("缺少主动能力配置：" + path);
            return parse(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception exception) {
            throw new IllegalStateException("无法加载天命主动能力配置：" + path, exception);
        }
    }

    static Map<ResourceLocation, FateAbilityDefinition> parse(JsonObject root) {
        if (integer(root, "schema", 1, 1) != 1) throw new IllegalArgumentException("未知主动能力配置版本");
        var rows = root.getAsJsonArray("abilities");
        if (rows == null || rows.size() > 64) throw new IllegalArgumentException("主动能力数量越界");
        Map<ResourceLocation, FateAbilityDefinition> result = new LinkedHashMap<>();
        for (var element : rows) {
            var row = element.getAsJsonObject();
            var definition = new FateAbilityDefinition(ResourceLocation.parse(string(row, "id")),
                    ResourceLocation.parse(string(row, "required_fate")), string(row, "name"),
                    string(row, "description"), (int) integer(row, "cooldown_ticks", 1, 12000),
                    number(row, "cost_fraction"), integer(row, "minimum_cost", 1, Long.MAX_VALUE), number(row, "radius"));
            if (result.putIfAbsent(definition.id(), definition) != null)
                throw new IllegalArgumentException("主动能力 ID 重复：" + definition.id());
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static String string(JsonObject row, String key) {
        if (!row.has(key) || !row.get(key).isJsonPrimitive() || !row.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException("配置需要文字：" + key);
        return row.get(key).getAsString();
    }

    private static double number(JsonObject row, String key) {
        if (!row.has(key) || !row.get(key).isJsonPrimitive() || !row.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("配置需要数字：" + key);
        return row.get(key).getAsDouble();
    }

    private static long integer(JsonObject row, String key, long min, long max) {
        number(row, key);
        long value;
        try { value = row.getAsJsonPrimitive(key).getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException exception) { throw new IllegalArgumentException("配置需要整数：" + key, exception); }
        if (value < min || value > max) throw new IllegalArgumentException("配置整数越界：" + key);
        return value;
    }
}
