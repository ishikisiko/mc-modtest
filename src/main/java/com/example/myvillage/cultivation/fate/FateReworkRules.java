package com.example.myvillage.cultivation.fate;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 数据决定伴生形态与一次性物资，服务器负责生效资格。 */
public record FateReworkRules(List<ResourceLocation> armor, List<ResourceLocation> weapons, int quality,
        int pills, int stones, int swordWindow, double trialFraction, int trialIdle, List<Stage> stages) {
    public record Stage(ResourceLocation realm, double swordBonus, double reach, int targets, long power,
            int cooldown, double strike, double health, double defense, double earthBonus, double earthResistance) { }

    public static final String GIFT_ID = "yuanxian:starter_wealth";
    public static final ResourceLocation HEART_ID = ResourceLocation.parse("yuanxian:sacred_earth_heart");
    public static final ResourceLocation SWORD_ART = ResourceLocation.parse("yuanxian:companion_sword_pursuit");
    private static final class Holder { static final FateReworkRules VALUE = load(); }
    public static FateReworkRules get() { return Holder.VALUE; }

    public int stage(ResourceLocation realm) {
        for (int i = 0; i < stages.size(); i++) if (stages.get(i).realm().equals(realm)) return i;
        return -1;
    }

    public boolean weaponChoice(int choice) { return choice >= 0 && choice < weapons.size(); }

    public Component weaponName(int choice) {
        var id = weapons.get(choice);
        return Component.translatable("item." + id.getNamespace() + "." + id.getPath());
    }

    private static FateReworkRules load() {
        try (var in = FateReworkRules.class.getResourceAsStream("/data/yuanxian/fates/rework.json")) {
            if (in == null) throw new IllegalStateException("缺少天命进化数表");
            var root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            var opening = root.getAsJsonObject("opening");
            List<ResourceLocation> armor = new ArrayList<>(), weapons = new ArrayList<>();
            opening.getAsJsonArray("armor").forEach(id -> armor.add(ResourceLocation.parse(id.getAsString())));
            opening.getAsJsonArray("weapons").forEach(id -> weapons.add(ResourceLocation.parse(id.getAsString())));
            List<Stage> stages = new ArrayList<>();
            for (var element : root.getAsJsonArray("stages")) {
                var v = element.getAsJsonObject();
                stages.add(new Stage(ResourceLocation.parse(v.get("realm").getAsString()), v.get("sword_bonus").getAsDouble(),
                        v.get("reach").getAsDouble(), v.get("targets").getAsInt(), v.get("power").getAsLong(),
                        v.get("cooldown").getAsInt(), v.get("strike").getAsDouble(), v.get("health").getAsDouble(),
                        v.get("defense").getAsDouble(), v.get("earth_bonus").getAsDouble(), v.get("earth_resistance").getAsDouble()));
            }
            var rules = new FateReworkRules(List.copyOf(armor), List.copyOf(weapons), opening.get("quality").getAsInt(),
                    opening.get("pills").getAsInt(), opening.get("stones").getAsInt(), root.get("sword_window_ticks").getAsInt(),
                    root.get("sword_trial_fraction").getAsDouble(), root.get("sword_trial_idle_ticks").getAsInt(), List.copyOf(stages));
            if (rules.armor.size() != 4 || rules.weapons.isEmpty() || rules.weapons.size() > 6 || rules.stages.size() != 5
                    || rules.quality < 1 || rules.quality > 4 || rules.pills <= 0 || rules.stones <= 0
                    || rules.trialFraction <= 0 || rules.trialFraction > 1 || rules.swordWindow < 1 || rules.trialIdle < 1
                    || stages.stream().map(Stage::realm).distinct().count() != 5
                    || stages.stream().anyMatch(s -> s.reach <= 0 || s.reach > 32 || s.targets < 1 || s.targets > 8
                            || s.power < 1 || s.cooldown < 1 || s.strike <= 0 || s.swordBonus < 0
                            || s.health < 0 || s.defense < 0 || s.earthBonus < 0 || s.earthResistance < 0 || s.earthResistance > 1))
                throw new IllegalArgumentException("天命进化数表无效");
            return rules;
        } catch (java.io.IOException ex) { throw new IllegalStateException("无法读取天命进化数表", ex); }
    }
}
