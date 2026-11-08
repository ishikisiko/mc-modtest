package com.example.myvillage.aptitude;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 资质点数、初始分布、学习门槛及培养参数的唯一配置入口。 */
public final class AptitudeRules {
    private final JsonObject data;
    private final List<String> elements, weapons, crafts, ids;
    private AptitudeRules(JsonObject data) {
        this.data = data;
        if (data.get("version").getAsInt() != 1) throw new IllegalArgumentException("Invalid aptitude rules version");
        elements = strings(data, "elements"); weapons = strings(data, "weapons"); crafts = strings(data, "crafts");
        var all = new ArrayList<>(elements); all.addAll(weapons); all.addAll(crafts); ids = List.copyOf(all);
        if (elements.size() != 5 || ids.stream().distinct().count() != ids.size())
            throw new IllegalArgumentException("Invalid aptitude catalogue");
        for (int i = 0; i < 5; i++) {
            int[] range = rootRange(i + 1);
            if (range[0] < 0 || range[1] < range[0]) throw new IllegalArgumentException("Invalid root range");
        }
        int[] weights = rootCountWeights();
        if (weights.length != elements.size() || java.util.Arrays.stream(weights).anyMatch(value -> value <= 0)
                || (long) weaponMax() - weaponMin() < 0 || weaponMin() < 0)
            throw new IllegalArgumentException("Invalid aptitude initial distribution");
        if (!(halfBonusPoints() > 0) || !(maximumBonus() >= 0 && maximumBonus() <= 2))
            throw new IllegalArgumentException("Invalid aptitude damage curve");
        data.getAsJsonObject("growth").entrySet().forEach(e -> {
            if (e.getValue().getAsInt() <= 0) throw new IllegalArgumentException("Invalid aptitude growth: " + e.getKey());
        });
    }
    private static final class Holder {
        static final AptitudeRules INSTANCE = load();
    }
    public static AptitudeRules get() { return Holder.INSTANCE; }
    private static AptitudeRules load() {
        try (var stream = AptitudeRules.class.getResourceAsStream("/data/myvillage/aptitudes/rules.json")) {
            if (stream == null) throw new IllegalStateException("Missing aptitude rules");
            return new AptitudeRules(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static List<String> strings(JsonObject source, String key) {
        return source.getAsJsonArray(key).asList().stream().map(v -> v.getAsString()).toList();
    }
    public List<String> elements() { return elements; }
    public List<String> weapons() { return weapons; }
    public List<String> crafts() { return crafts; }
    public List<String> ids() { return ids; }
    public boolean valid(String id) { return ids.contains(id); }
    public int[] rootRange(int count) {
        var a = data.getAsJsonArray("initial_root_ranges").get(Math.clamp(count, 1, 5) - 1).getAsJsonArray();
        return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()};
    }
    public int rootGrade(int peak) {
        for (int i = 0; i < 4; i++) if (peak >= rootRange(i + 1)[0]) return i;
        return 4;
    }
    public int[] rootCountWeights() { return data.getAsJsonArray("initial_root_count_weights").asList().stream().mapToInt(value -> value.getAsInt()).toArray(); }
    public int weaponMin() { return data.getAsJsonArray("initial_weapon_range").get(0).getAsInt(); }
    public int weaponMax() { return data.getAsJsonArray("initial_weapon_range").get(1).getAsInt(); }
    public int npcPointsPerRealm() { return data.get("npc_points_per_realm").getAsInt(); }
    public int minimumForGrade(int grade) { return data.getAsJsonObject("learning_requirements").get(Integer.toString(Math.clamp(grade, 1, 4))).getAsInt(); }
    public int growth(String key) { return data.getAsJsonObject("growth").get(key).getAsInt(); }
    public double halfBonusPoints() { return data.getAsJsonObject("damage").get("half_bonus_points").getAsDouble(); }
    public double maximumBonus() { return data.getAsJsonObject("damage").get("maximum_bonus").getAsDouble(); }
    public double multiplier(int points) {
        if (points < 0) throw new IllegalArgumentException("Negative aptitude");
        return 1 + maximumBonus() * points / (points + halfBonusPoints());
    }
    public long practiceRequired(int points) { return growth("practice_base") + (long) points * growth("practice_per_point"); }
    public static int add(int value, int amount) {
        if (value < 0 || amount < 0) throw new IllegalArgumentException("Negative aptitude points");
        return (int) Math.min(Integer.MAX_VALUE, (long) value + amount);
    }
}
