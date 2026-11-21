package com.example.myvillage.combat.art;

import com.example.myvillage.combat.definition.AnimationDefinition;
import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.CameraCues;
import com.example.myvillage.combat.definition.ReactionDefinition;
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

/** Server and presentation share this frozen, generated combat definition. */
public record GoldenCoreArt(Mechanic mechanic, Optional<DamageType> element, int windup, int recovery,
        double range, double length, double width, double radius, double inner, double secondaryRange,
        int targets, double damage, double secondary, double bonus, double step, double push,
        double status, int statusTicks, int duration, int interval) {
    private static final String RESOURCE = "/data/yuanxian/combat/golden_core_arts.json";

    public enum Mechanic {
        PIERCING_SWORD("sword"), CROSSING_TRACES("sword"), TIP_CHARGE("spear"), RETREATING_THRUST("spear"),
        STORED_PUNCH("fist"), GROUND_RINGS("fist"), ARMOR_LOCK("metal"), METAL_CHAIN("metal"),
        WOOD_TRANSFER("wood"), WOOD_TRAIL("wood"), SIDE_TIDE("water"), FROST_SHATTER("water"),
        FIRE_FUSE("fire"), FIRE_ROWS("fire"), EARTH_ROWS("earth"), STONE_STAMP("earth");

        private final String route;
        Mechanic(String route) { this.route = route; }
    }

    public record Rules(double aimArc, double lineAssistArc, int windup, int recovery,
                        double spearTipMin, double spearTipMax, double pvpDamage, double pvpDisplacement,
                        Set<String> shelfRegions) {
        public Rules { shelfRegions = Set.copyOf(shelfRegions); }
    }

    private static final class Holder {
        static final JsonObject DOCUMENT = load();
        static final Rules RULES = readRules(DOCUMENT.getAsJsonObject("rules"));
    }

    static JsonObject document() { return Holder.DOCUMENT; }
    public static Rules rules() { return Holder.RULES; }

    static GoldenCoreArt parse(JsonObject row, String family) {
        var mechanic = Mechanic.valueOf(row.get("mechanic").getAsString().toUpperCase(Locale.ROOT));
        var element = row.has("element") ? Optional.of(DamageType.fromId(row.get("element").getAsString()))
                : Optional.<DamageType>empty();
        if (!element.map(DamageType::id).orElse(family).equals(mechanic.route)
                || element.isPresent() != family.equals("elemental") || element.orElse(null) == DamageType.PHYSICAL)
            throw new IllegalArgumentException("金丹绝技路线与执行器不一致 " + mechanic);
        double range = number(row, "range", 0, 1, 64);
        double radius = number(row, "radius", 0, 0, 16);
        return new GoldenCoreArt(mechanic, element, integer(row, "windup", rules().windup(), 1, 40), rules().recovery(),
                range, number(row, "length", range, 1, 48), number(row, "width", 1, 0.1, 16), radius,
                number(row, "inner", 0, 0, radius), number(row, "secondary_range", 0, 0, 48),
                integer(row, "targets", 0, 1, 12), number(row, "damage", 0, 0.01, 6),
                number(row, "secondary", 0, 0, 6), number(row, "bonus", 0, 0, 6),
                number(row, "step", 0, 0, 6), number(row, "push", 0, 0, 4),
                number(row, "status", 0, 0, 1), integer(row, "status_ticks", 0, 0, 200),
                integer(row, "duration", 0, 0, 200), integer(row, "interval", 0, 0, 200));
    }

    public int lastStrike() {
        return windup + switch (mechanic) {
            case GROUND_RINGS, FIRE_ROWS, EARTH_ROWS -> interval * 2;
            case RETREATING_THRUST -> interval;
            default -> 0;
        };
    }

    AttackMoveDefinition move(AttackMoveDefinition template) {
        int end = lastStrike();
        int total = end + 1 + recovery;
        return new AttackMoveDefinition(template.id(), template.displayKey(), template.kind(), total, windup, end,
                damage, targets, range, end + 1, total, ReactionDefinition.NONE,
                new AnimationDefinition(template.id(), total), template.hitbox(), Optional.empty(), template.feedback(),
                new CameraCues(0, 0, 0, 0, 0), Optional.empty());
    }

    private static JsonObject load() {
        try (var stream = GoldenCoreArt.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("缺少金丹绝技配置");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("金丹绝技格式无效");
            return root;
        } catch (IOException e) {
            throw new IllegalStateException("无法读取金丹绝技", e);
        }
    }

    private static Rules readRules(JsonObject row) {
        var regions = new HashSet<String>();
        row.getAsJsonArray("shelf_regions").forEach(value -> regions.add(value.getAsString()));
        double tipMin = number(row, "spear_tip_min", 2, 0, 8);
        return new Rules(number(row, "aim_arc", 48, 1, 180), number(row, "line_assist_arc", 36, 1, 180),
                integer(row, "windup", 12, 1, 40), integer(row, "recovery", 7, 2, 40), tipMin,
                number(row, "spear_tip_max", 6.3, tipMin, 8), number(row, "pvp_damage", 0.75, 0.01, 1),
                number(row, "pvp_displacement", 0.5, 0, 1), regions);
    }

    private static double number(JsonObject row, String key, double fallback, double min, double max) {
        double value = row.has(key) ? row.get(key).getAsDouble() : fallback;
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("金丹绝技数值越界 " + key);
        return value;
    }

    private static int integer(JsonObject row, String key, int fallback, int min, int max) {
        double value = number(row, key, fallback, min, max);
        if (value != Math.rint(value)) throw new IllegalArgumentException("金丹绝技需要整数 " + key);
        return (int) value;
    }
}
