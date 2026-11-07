package com.example.myvillage.cultivation.fate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** 长生道胎的年度数表；属性单位依次为生命、肉身防御、自身攻击。 */
public record EverlastingRules(double lifespanBonus, double creditStep, Map<String, Annual> yearly) {
    public record Annual(long health, long defense, long attack) {
        public Annual { if (health < 0 || defense < 0 || attack < 0) throw new IllegalArgumentException("非法长生年增量"); }
        public static final Annual ZERO = new Annual(0, 0, 0);
        public boolean empty() { return health == 0 && defense == 0 && attack == 0; }
    }
    public EverlastingRules {
        if (!Double.isFinite(lifespanBonus) || lifespanBonus < 0 || lifespanBonus > 1
                || creditStep != 0.5) throw new IllegalArgumentException("非法长生配置");
        yearly = Map.copyOf(yearly);
        if (!yearly.keySet().equals(Set.of("myvillage:mortal", "myvillage:qi_refining",
                "myvillage:foundation_establishment", "myvillage:golden_core"))
                || !yearly.get("myvillage:mortal").empty())
            throw new IllegalArgumentException("长生数表须覆盖当前四境，凡人不得成长");
    }
    private static final class Holder { private static final EverlastingRules VALUE = load(); }
    public static EverlastingRules get() { return Holder.VALUE; }
    public Annual at(String realm) { return yearly.getOrDefault(realm, Annual.ZERO); }
    public int maximumYears(int realmYears, boolean active) {
        if (realmYears <= 0) throw new IllegalArgumentException("寿元上限必须为正");
        // 时间协议以整年传输；当前四境的增寿结果均为整年。
        return active ? Math.toIntExact((long) Math.floor(realmYears * (1 + lifespanBonus))) : realmYears;
    }
    private static EverlastingRules load() {
        try (var input = EverlastingRules.class.getResourceAsStream("/data/myvillage/fates/everlasting.json")) {
            if (input == null) throw new IllegalStateException("缺少长生道胎配置");
            return parse(JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException e) { throw new IllegalStateException("无法读取长生道胎配置", e); }
    }
    public static EverlastingRules parse(JsonObject root) {
        if (!root.keySet().equals(Set.of("schema", "lifespan_bonus", "credit_step", "yearly"))
                || root.get("schema").getAsDouble() != 1) throw new IllegalArgumentException("非法长生配置格式");
        double step = root.get("credit_step").getAsDouble();
        if (step != 0.5) throw new IllegalArgumentException("长生属性必须以半点结算");
        Map<String, Annual> rates = new TreeMap<>();
        root.getAsJsonObject("yearly").entrySet().forEach(entry -> {
            var row = entry.getValue().getAsJsonArray();
            if (row.size() != 3) throw new IllegalArgumentException("长生年增量须有三项");
            long[] units = new long[3];
            for (int index = 0; index < units.length; index++) {
                double value = row.get(index).getAsDouble() / step;
                if (!Double.isFinite(value) || value < 0 || value != Math.rint(value) || value > Integer.MAX_VALUE)
                    throw new IllegalArgumentException("长生年增量须为有界的半点倍数");
                units[index] = (long) value;
            }
            rates.put(entry.getKey(), new Annual(units[0], units[1], units[2]));
        });
        return new EverlastingRules(root.get("lifespan_bonus").getAsDouble(), step, rates);
    }
}
