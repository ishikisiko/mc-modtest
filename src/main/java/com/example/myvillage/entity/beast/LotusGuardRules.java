package com.example.myvillage.entity.beast;

import com.example.myvillage.resources.BuiltinResources;
import com.example.myvillage.stats.StatsRules;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Static tuning for the unique stone-lotus encounter. Move timing lives in the shared beast definition. */
public record LotusGuardRules(Body body, Encounter encounter, Arena arena, Armor armor) {
    private static final String RESOURCE = "data/yuanxian/beast/lotus_guard_boss.json";

    public record Body(float width, float height, float eyeHeight, float shadowRadius) {}
    public record Encounter(ResourceLocation realm, int stage, int experience) {}
    public record Arena(double wakeRadius, double homeX, double homeZ, double targetX, double targetZ,
                        double elevation, int checkTicks, int repathTicks, double observerRadius) {}
    public record Armor(int returnTicks, float weakMultiplier, double flankNear, double flankHalfLength) {}

    public static LotusGuardRules get() { return Loaded.RULES; }

    private static final class Loaded {
        private static final LotusGuardRules RULES = load();
    }

    private static LotusGuardRules load() {
        try (var in = BuiltinResources.open(RESOURCE)) {
            if (in == null) throw new IllegalStateException("缺少伏莲兽遭遇配置");
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("无法读取伏莲兽遭遇配置", e);
        }
    }

    static LotusGuardRules parse(Reader source) {
        var data = new BeastJson.Fields(RESOURCE, "", BeastJson.parseStrict(RESOURCE, source),
                Set.of("schema", "body", "encounter", "arena", "armor"));
        data.schema();
        var b = data.object("body", Set.of("width", "height", "eye_height", "shadow_radius"));
        Body body = new Body((float) bounded(b, "width", 4, 16), (float) bounded(b, "height", 3.5, 8),
            (float) bounded(b, "eye_height", .5, 8), (float) bounded(b, "shadow_radius", 0, 8));
        if (body.eyeHeight() >= body.height()) throw b.error("eye_height", "must be below height");
        var e = data.object("encounter", Set.of("realm", "stage", "experience"));
        Encounter encounter = new Encounter(e.id("realm"), e.nonNegativeInteger("stage"), e.nonNegativeInteger("experience"));
        StatsRules.bundled().realmKey(encounter.realm().toString(), encounter.stage());
        var a = data.object("arena", Set.of("wake_radius", "home_x", "home_z", "target_x", "target_z",
                "elevation", "check_ticks", "repath_ticks", "observer_radius"));
        Arena arena = new Arena(bounded(a, "wake_radius", 8, 64), bounded(a, "home_x", 8, 26),
            bounded(a, "home_z", 8, 20), bounded(a, "target_x", 8, 34), bounded(a, "target_z", 8, 28),
            bounded(a, "elevation", 2, 12), interval(a, "check_ticks"), interval(a, "repath_ticks"),
            bounded(a, "observer_radius", 8, 64));
        if (arena.homeX() > arena.targetX() || arena.homeZ() > arena.targetZ()) {
            throw a.error("target_x", "target bounds must include the home bounds");
        }
        var w = data.object("armor", Set.of("return_ticks", "weak_multiplier", "flank_near", "flank_half_length"));
        Armor armor = new Armor(w.positiveInteger("return_ticks"), (float) bounded(w, "weak_multiplier", 1, 3),
            bounded(w, "flank_near", 0, 10), bounded(w, "flank_half_length", 0, 8));
        return new LotusGuardRules(body, encounter, arena, armor);
    }

    public static void validateMoves(BeastDefinition definition) {
        for (var move : definition.moves()) {
            if (LotusGuardCombat.side(move.id()) != 0
                    && move.totalTicks() - get().armor().returnTicks() <= move.activeTicks().last() + 1) {
                throw new IllegalStateException("伏莲兽侧甲需要留出可反击的窗口：" + move.id());
            }
        }
    }

    private static int interval(BeastJson.Fields field, String key) {
        int value = field.positiveInteger(key);
        if (value < 5 || value > 120) throw field.error(key, "must be from 5 to 120 ticks");
        return value;
    }

    private static double bounded(BeastJson.Fields fields, String key, double min, double max) {
        double n = fields.number(key);
        if (n < min || n > max) throw fields.error(key, "outside [" + min + ", " + max + "]");
        return n;
    }
}
