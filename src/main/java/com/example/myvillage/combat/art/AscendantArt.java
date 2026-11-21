package com.example.myvillage.combat.art;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.stats.DamageType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static com.example.myvillage.combat.art.GoldenCoreArt.integer;
import static com.example.myvillage.combat.art.GoldenCoreArt.number;

public record AscendantArt(Mechanic mechanic, String tier, Optional<DamageType> element,
                           Geometry geometry, Timing timing, Effect effect, Limits limits) {
    public enum Mechanic {
        TWIN_BLADE("sword"), EVADE_ECHO("sword"), SWORD_GATE("sword"), SWORD_POINTS("sword"),
        OPPOSING_SPEARS("spear"), SPEAR_POSTS("spear"), CLEAVING_CHARGE("spear"), SPEAR_ARRAY("spear"),
        GUARDING_ECHO("fist"), PIERCING_FIST("fist"), DRAGON_STANCE("fist"), FALLING_PALM("fist"),
        GOLD_MIRROR("metal"), ARMOR_STAR("metal"), GOLD_PLATES("metal"), ARMOR_STAMP("metal"),
        LINKED_ROOTS("wood"), RESCUE_VINE("wood"), WOOD_HARVEST("wood"), ANCIENT_SHADE("wood"),
        MEETING_TIDES("water"), FLOW_LANE("water"), RETURNING_SEA("water"), FREEZING_CREST("water"),
        PAIRED_FURNACES("fire"), FIRE_ESCAPE("fire"), FIRE_ENCLOSURE("fire"), FIRE_HARVEST("fire"),
        MOVING_WALL("earth"), STEADY_PLATFORM("earth"), FOLDING_EARTH("earth"), MOUNTAIN_SHELL("earth");

        private final String route;
        Mechanic(String route) { this.route = route; }
    }

    public record Geometry(double range, double length, double width, double radius, double inner,
                           double reach, double height, double gap, double step, double arc) {}
    public record Timing(int windup, int recovery, int duration, int delay, int window, int statusTicks, int interval) {}
    public record Effect(double damage, double secondary, double bonus, double control, double budget, double push) {}
    public record Limits(int targets, int count, int perTarget) {}

    private static final class Holder {
        static final JsonObject DATA = load();
        static final Set<String> SHELVES = shelves(DATA);
        static final String REGION = DATA.get("expedition_region").getAsString();
    }

    static JsonObject document() { return Holder.DATA; }
    public static Set<String> shelfRegions() { return Holder.SHELVES; }
    public static String expeditionRegion() { return Holder.REGION; }
    public boolean spiritTransformation() { return tier.equals("spirit_transformation"); }

    static AscendantArt parse(JsonObject row, String family) {
        var mechanism = Mechanic.valueOf(row.get("mechanic").getAsString().toUpperCase(Locale.ROOT));
        String tier = row.get("tier").getAsString();
        var element = row.has("element") ? Optional.of(DamageType.fromId(row.get("element").getAsString()))
                : Optional.<DamageType>empty();
        if (!Set.of("nascent_soul", "spirit_transformation").contains(tier)
                || !element.map(DamageType::id).orElse(family).equals(mechanism.route)
                || element.isPresent() != family.equals("elemental") || element.orElse(null) == DamageType.PHYSICAL)
            throw new IllegalArgumentException("高境绝技的境界或路线无效 " + mechanism);
        double range = number(row, "range", 0, 1, 64);
        var shape = new Geometry(range, number(row, "length", range, .1, 48), number(row, "width", 2.6, .1, 24),
                metric(row, "radius", 16), metric(row, "inner", 16), metric(row, "reach", 48),
                metric(row, "height", 16), metric(row, "gap", 16), metric(row, "step", 12),
                number(row, "arc", 120, 1, 180));
        var timing = new Timing(integer(row, "windup", 0, 1, 40), integer(row, "recovery", 0, 2, 40),
                ticks(row, "duration", 240), ticks(row, "delay", 120), ticks(row, "window", 120), ticks(row, "status_ticks", 200),
                integer(row, "interval", 1, 1, 40));
        var effect = new Effect(number(row, "damage", 1, .01, 8), metric(row, "secondary", 8), metric(row, "bonus", 8),
                metric(row, "control", 1), metric(row, "budget", 1), metric(row, "push", 6));
        var limits = new Limits(integer(row, "targets", 1, 1, 12), integer(row, "count", 1, 1, 12),
                integer(row, "per_target", 1, 1, 4));
        if (shape.inner() > Math.max(shape.width(), shape.radius()))
            throw new IllegalArgumentException("绝技内圈大于外圈 " + mechanism);
        return new AscendantArt(mechanism, tier, element, shape, timing, effect, limits);
    }

    AttackMoveDefinition move(AttackMoveDefinition template) {
        int end = timing.windup() + (mechanic == Mechanic.DRAGON_STANCE ? timing.duration() : 0);
        return GoldenCoreArt.timedMove(template, timing.windup(), end, timing.recovery(), geometry.range(),
                effect.damage(), limits.targets());
    }

    private static double metric(JsonObject row, String key, double max) { return number(row, key, 0, 0, max); }
    private static int ticks(JsonObject row, String key, int max) { return integer(row, key, 0, 0, max); }
    private static Set<String> shelves(JsonObject json) {
        var regions = new HashSet<String>();
        json.getAsJsonArray("shelf_regions").forEach(x -> regions.add(x.getAsString()));
        return Set.copyOf(regions);
    }
    private static JsonObject load() {
        String path = "/data/yuanxian/combat/ascendant_arts.json";
        try (var stream = AscendantArt.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalArgumentException("缺少 " + path);
            var data = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (data.get("schema").getAsInt() != 1) throw new IllegalArgumentException("高境绝技格式无效");
            return data;
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取元婴与化神绝技", ex);
        }
    }
}
