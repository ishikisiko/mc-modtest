package com.example.myvillage.combat.art;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.stats.DamageType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Shared timing and geometry for the additional Qi Refining and Foundation arts. */
public record EarlyRealmArt(Mechanic mechanic, String tier, Optional<DamageType> element,
        int windup, int recovery, double range, double length, double width, double arc, double radius,
        double height, double secondaryRange, int targets, double damage, double secondary, double step,
        double push, double potency, double threshold, int duration, int delay, int warning, int statusTicks) {
    public enum Mechanic {
        SIDESTEP_SLASH("sword"), BRACE_SPEAR("spear"), SHOULDER_RAM("fist"),
        ARROW_RING("metal"), VINE_LINE("wood"), GATHER_TIDE("water"), WAITING_FLAME("fire"), EARTH_SPIKE("earth"),
        LINGERING_SLASH("sword"), STEPPING_SPEAR("spear"), SWEEPING_SPEAR("spear"), SHOULDER_THROW("fist"),
        BACK_GUARD("fist"), ARMOR_LINE("metal"), HEALING_BRANCH("wood"), REFLECTED_WAVE("water"),
        MATURE_FLAME("fire"), STONE_SCREEN("earth");

        private final String route;
        Mechanic(String route) { this.route = route; }
    }

    private static final class Holder {
        static final JsonObject DOCUMENT = load();
        static final Set<String> SHELVES = StreamSupport.stream(
                DOCUMENT.getAsJsonArray("shelf_regions").spliterator(), false)
                .map(com.google.gson.JsonElement::getAsString).collect(Collectors.toUnmodifiableSet());
    }

    static JsonObject document() { return Holder.DOCUMENT; }
    public static Set<String> shelfRegions() { return Holder.SHELVES; }

    public boolean foundation() { return tier.equals("foundation_establishment"); }

    static EarlyRealmArt parse(JsonObject row, String family) {
        var mechanic = Mechanic.valueOf(row.get("mechanic").getAsString().toUpperCase(Locale.ROOT));
        String tier = row.get("tier").getAsString();
        var element = row.has("element") ? Optional.of(DamageType.fromId(row.get("element").getAsString()))
                : Optional.<DamageType>empty();
        if (!Set.of("qi_refining", "foundation_establishment").contains(tier)
                || !element.map(DamageType::id).orElse(family).equals(mechanic.route)
                || element.isPresent() != family.equals("elemental"))
            throw new IllegalArgumentException("绝技境界或路线无效 " + mechanic);
        if (element.orElse(null) == DamageType.PHYSICAL)
            throw new IllegalArgumentException("五行绝技需要元素类型 " + mechanic);
        double range = GoldenCoreArt.number(row, "range", 0, 1, 64);
        return new EarlyRealmArt(mechanic, tier, element, ticks(row, "windup", 1, 40),
                ticks(row, "recovery", 2, 40), range, GoldenCoreArt.number(row, "length", range, 1, 64),
                GoldenCoreArt.number(row, "width", 1, .1, 16), GoldenCoreArt.number(row, "arc", 120, 1, 180),
                value(row, "radius", 16), value(row, "height", 16), value(row, "secondary_range", 48),
                ticks(row, "targets", 1, 12), GoldenCoreArt.number(row, "damage", 1, .01, 8),
                value(row, "secondary", 8), value(row, "step", 6), value(row, "push", 6),
                value(row, "potency", 1), value(row, "threshold", 8), ticks(row, "duration", 0, 200),
                ticks(row, "delay", 0, 200), ticks(row, "warning", 0, 200), ticks(row, "status_ticks", 0, 200));
    }

    int lastStrike() {
        return windup + switch (mechanic) {
            case ARROW_RING, BACK_GUARD -> duration - 1;
            default -> 0;
        };
    }

    AttackMoveDefinition move(AttackMoveDefinition template) {
        return GoldenCoreArt.timedMove(template, windup, lastStrike(), recovery, range, damage, targets);
    }

    private static double value(JsonObject row, String key, double max) {
        return GoldenCoreArt.number(row, key, 0, 0, max);
    }

    private static int ticks(JsonObject row, String key, int min, int max) {
        return GoldenCoreArt.integer(row, key, 0, min, max);
    }

    private static JsonObject load() {
        String path = "/data/yuanxian/combat/early_realm_arts.json";
        try (var stream = EarlyRealmArt.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalArgumentException("缺少 " + path);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("炼气与筑基绝技格式无效");
            return root;
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取炼气与筑基绝技", ex);
        }
    }
}
