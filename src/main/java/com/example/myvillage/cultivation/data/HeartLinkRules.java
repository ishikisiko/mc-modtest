package com.example.myvillage.cultivation.data;

import com.example.myvillage.cultivation.data.TechniqueTraits.ContactState;
import com.example.myvillage.cultivation.data.TechniqueTraits.Trait;
import com.example.myvillage.stats.DamageType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 装备心法的状态规则和双端显示说明共用一份定义。 */
public record HeartLinkRules(Map<ResourceLocation, Attack> attack,
                             Map<ResourceLocation, Defense> defense,
                             Map<ResourceLocation, Spirit> spirit,
                             int defenseCooldown, int spiritCooldown, int armorTicks,
                             Map<ResourceLocation, List<Trait>> traits) {
    public enum Route {
        SWORD(null), SPEAR(null), FIST(null), METAL(DamageType.METAL), WOOD(DamageType.WOOD),
        WATER(DamageType.WATER), FIRE(DamageType.FIRE), EARTH(DamageType.EARTH);

        private final DamageType element;

        Route(DamageType element) { this.element = element; }
        public DamageType element() { return element; }
        public boolean matches(String family, DamageType type) {
            return element != null ? type == element : type == null && name().equalsIgnoreCase(family);
        }
    }

    public enum AttackUse { GRANT, STRIKE }
    public enum Trigger { PAID, BASIC, BASIC_TIP, SNEAK_PAID, SNEAK_CLOSE }
    public enum DefenseUse { SHIELD, MEND, RETALIATE }

    public record Attack(Route route, AttackUse use, Trigger trigger, ContactState state,
                         ContactState requires, int cooldown, int percent) { }
    public record Defense(Route route, DefenseUse use, ContactState state, int healthPercent) { }
    public record Spirit(Route route, ContactState state, int qi) { }
    private record Band(int cooldown, int percent) { }

    static HeartLinkRules parse(JsonObject root, JsonObject labels) {
        var bands = new ArrayList<Band>();
        for (var value : root.getAsJsonArray("attack_bands")) {
            var band = value.getAsJsonObject();
            bands.add(new Band(integer(band, "cooldown", 1, 1200), integer(band, "percent", 1, 30)));
        }
        int defenseCooldown = integer(root, "defense_cooldown", 1, 1200);
        int spiritCooldown = integer(root, "spirit_cooldown", 1, 1200);
        int armorTicks = integer(root, "armor_ticks", 1, 1200);
        Map<ResourceLocation, Attack> attack = new HashMap<>();
        Map<ResourceLocation, Defense> defense = new HashMap<>();
        Map<ResourceLocation, Spirit> spirit = new HashMap<>();
        Map<ResourceLocation, List<Trait>> traits = new HashMap<>();

        for (var value : root.getAsJsonArray("attack")) {
            var row = value.getAsJsonObject();
            var route = field(row, "route", Route.class);
            var use = field(row, "use", AttackUse.class);
            var trigger = field(row, "trigger", Trigger.class);
            var state = field(row, "state", ContactState.class);
            var requires = row.has("requires") ? field(row, "requires", ContactState.class) : null;
            var hearts = row.getAsJsonArray("hearts");
            if (hearts.size() != bands.size()) throw new IllegalArgumentException("攻心法与境界参数数量不符");
            for (int i = 0; i < hearts.size(); i++) {
                var id = key(hearts.get(i).getAsString());
                var band = bands.get(i);
                attack.put(id, new Attack(route, use, trigger, state, requires, band.cooldown(), band.percent()));
                String role = use == AttackUse.GRANT ? state.casterState() ? "获得" : "施加" : "消耗";
                String valueText = "";
                if (use == AttackUse.STRIKE) valueText = "本击伤害提高 " + band.percent() + "%。";
                if (use == AttackUse.GRANT && state == ContactState.ARMOR_BREAK)
                    valueText = "护甲降低 " + band.percent() + "%，持续 " + armorTicks + " 刻。";
                var primary = trait(labels, state.tag(), role, description(row) + valueText + interval(band.cooldown()));
                var rows = requires == null ? List.of(primary) : List.of(primary,
                        trait(labels, requires.tag(), "读取", "攻心法在目标带有对应状态时可以结算。"));
                putTraits(traits, id, rows);
            }
        }
        for (var entry : root.getAsJsonObject("defense").entrySet()) {
            var row = entry.getValue().getAsJsonObject();
            var id = key(entry.getKey());
            var route = field(row, "route", Route.class);
            var use = field(row, "use", DefenseUse.class);
            var state = field(row, "state", ContactState.class);
            int amount = use == DefenseUse.RETALIATE ? 0 : integer(row, "health_percent", 1, 15);
            defense.put(id, new Defense(route, use, state, amount));
            var primary = trait(labels, state.tag(), use == DefenseUse.RETALIATE ? "施加" : "转化",
                    description(row) + interval(defenseCooldown));
            var rows = use == DefenseUse.SHIELD ? List.of(primary,
                    trait(labels, "shield", "获得", "转换后获得短时护盾，最高吸收自身最大生命的 " + amount + "%。"))
                    : List.of(primary);
            putTraits(traits, id, rows);
        }
        for (var entry : root.getAsJsonObject("spirit").entrySet()) {
            var row = entry.getValue().getAsJsonObject();
            var id = key(entry.getKey());
            var route = field(row, "route", Route.class);
            var state = field(row, "state", ContactState.class);
            int qi = integer(row, "qi", 1, 20);
            spirit.put(id, new Spirit(route, state, qi));
            putTraits(traits, id, List.of(trait(labels, state.tag(), "转化",
                    description(row) + "回复至多 " + qi + " 点灵力。" + interval(spiritCooldown))));
        }
        return new HeartLinkRules(Map.copyOf(attack), Map.copyOf(defense), Map.copyOf(spirit),
                defenseCooldown, spiritCooldown, armorTicks, Map.copyOf(traits));
    }

    private static String interval(int ticks) { return "同栏触发间隔 " + ticks + " 刻。"; }

    private static Trait trait(JsonObject labels, String tag, String role, String description) {
        return new Trait(tag, labels.get(tag).getAsString(), role, description);
    }

    private static void putTraits(Map<ResourceLocation, List<Trait>> traits, ResourceLocation id, List<Trait> rows) {
        if (traits.putIfAbsent(id, rows) != null) throw new IllegalArgumentException("心法联动重复 " + id);
    }

    private static ResourceLocation key(String path) { return ResourceLocation.fromNamespaceAndPath("yuanxian", path); }

    private static String description(JsonObject row) {
        String description = row.get("description").getAsString();
        if (description.isBlank()) throw new IllegalArgumentException("缺少心法联动描述");
        return description;
    }

    private static <T extends Enum<T>> T field(JsonObject row, String name, Class<T> type) {
        return Enum.valueOf(type, row.get(name).getAsString().toUpperCase(Locale.ROOT));
    }

    private static int integer(JsonObject row, String key, int low, int high) {
        var value = row.get(key).getAsBigDecimal();
        int result = value.intValueExact();
        if (result < low || result > high) throw new IllegalArgumentException("心法联动数值越界 " + key);
        return result;
    }
}
