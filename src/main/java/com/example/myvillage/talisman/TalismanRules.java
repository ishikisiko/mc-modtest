package com.example.myvillage.talisman;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 唯一数值来源是专属生成器发布的 rules.json。 */
public final class TalismanRules {
    private final JsonObject root;
    private TalismanRules(JsonObject input) {
        root = input.deepCopy();
        integer(root, "schema", 1, 1);
        int pulse = draw("pulse_ticks"), lease = draw("lease_ticks");
        if (draw("heartbeat_ticks") >= lease || lease >= pulse * 4 || draw("pulses") > 1000)
            throw new IllegalArgumentException("制符脉冲/租期无效");
        draw("power_per_pulse"); draw("paper_count"); draw("ink_count");
        use("activation_power"); use("cooldown_ticks"); use("max_per_player"); use("max_server"); use("particle_ticks"); use("reclaim_range"); use("range");
        for (int q = 1; q <= 4; q++) { damage(q); rootTicks(q); shield(q); }
        ticks("lightning", "warning_ticks"); ticks("snare", "lifetime_ticks"); ticks("ward", "duration_ticks");
        radius("lightning"); radius("snare");
    }
    private static final class Bundled { static final TalismanRules VALUE = load(); }
    public static TalismanRules get() { return Bundled.VALUE; }
    public static TalismanRules parse(JsonObject object) { return new TalismanRules(object); }
    private static TalismanRules load() {
        try (var in = TalismanRules.class.getResourceAsStream("/data/myvillage/talisman/rules.json")) {
            if (in == null) throw new IllegalStateException("缺少制符规则");
            return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) { throw new IllegalStateException("制符规则读取失败", e); }
    }
    public int draw(String key) { return integer(root.getAsJsonObject("drawing"), key, 1, 1000); }
    public int use(String key) { return integer(root.getAsJsonObject("use"), key, 1, 256); }
    public int ticks(String kind, String key) { return integer(root.getAsJsonObject(kind), key, 1, 72000); }
    public double radius(String kind) { return number(root.getAsJsonObject(kind), "radius", .1, 16); }
    public float damage(int quality) { return (float) quality("lightning", "damage", quality); }
    public int rootTicks(int quality) { return (int) quality("snare", "root_ticks", quality); }
    public float shield(int quality) { return (float) quality("ward", "shield", quality); }
    private double quality(String kind, String key, int quality) {
        TalismanQuality.require(quality);
        var a = root.getAsJsonObject(kind).getAsJsonArray(key);
        if (a == null || a.size() != 4) throw new IllegalArgumentException("制符品质数值必须有四档");
        double last = 0;
        for (var v : a) {
            double n = v.getAsDouble();
            if (!Double.isFinite(n) || n <= last || n > 10000 || key.equals("root_ticks") && n != Math.rint(n))
                throw new IllegalArgumentException("制符品质数值无效");
            last = n;
        }
        return a.get(quality - 1).getAsDouble();
    }
    private static int integer(JsonObject o, String key, int min, int max) {
        double n = number(o, key, min, max);
        if (n != Math.rint(n)) throw new IllegalArgumentException("整数配置无效：" + key);
        return (int) n;
    }
    private static double number(JsonObject o, String key, double min, double max) {
        if (o == null || !o.has(key) || !o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("缺少数值：" + key);
        double n = o.get(key).getAsDouble();
        if (!Double.isFinite(n) || n < min || n > max) throw new IllegalArgumentException("数值越界：" + key);
        return n;
    }
}
