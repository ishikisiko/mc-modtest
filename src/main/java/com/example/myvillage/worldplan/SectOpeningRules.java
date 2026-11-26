package com.example.myvillage.worldplan;

import com.example.myvillage.sim.data.SectProgressionRules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.example.myvillage.worldplan.WorldPlanRules.*;

/** 开档名单参数和三个创宗位的配置随新世界一起冻结。 */
public record SectOpeningRules(int count, int lineages, double qiExponent, double crowding,
                               Map<String, SiteChoice> foundingSites) {
    public static final String AUTO = "auto";

    public record SiteChoice(String region, String style) {
        public SiteChoice {
            if (!region.matches("[a-z][a-z0-9_]*") || !style.matches("[a-z][a-z0-9_]*"))
                throw new IllegalArgumentException("创宗位的州和建筑系列须填写配置 ID 或 auto");
        }
    }

    public SectOpeningRules {
        foundingSites = Map.copyOf(foundingSites);
    }

    static SectOpeningRules read(JsonObject json, SectPlanningRules layouts) {
        int count = integer(json, "count", 6, 48);
        int groups = integer(json, "lineages", 2, Math.min(6, count / 3));
        JsonObject sites = json.getAsJsonObject("founding_sites");
        if (sites == null || !sites.keySet().equals(Set.copyOf(SectProgressionRules.IDS)))
            throw new IllegalArgumentException("必须为分宗、总舵和主宗各配置一个创宗位");
        Map<String, SiteChoice> choices = new LinkedHashMap<>();
        for (String tier : SectProgressionRules.IDS) {
            JsonObject entry = sites.getAsJsonObject(tier);
            var choice = new SiteChoice(text(entry, "region"), text(entry, "style"));
            if (!choice.style().equals(AUTO)) layouts.family(choice.style(), tier);
            choices.put(tier, choice);
        }
        return new SectOpeningRules(count, groups, number(json, "region_qi_exponent", 0, 8),
                number(json, "region_crowding", 0, 16), choices);
    }

    public static WorldPlanRules withChoices(WorldPlanRules rules, Map<String, SiteChoice> choices) {
        if (rules.sectOpening == null || rules.sectOpening.foundingSites().equals(choices)) return rules;
        JsonObject frozen = JsonParser.parseString(rules.json()).getAsJsonObject();
        var selection = new JsonObject();
        for (String tier : SectProgressionRules.IDS) {
            var choice = choices.get(tier);
            var entry = new JsonObject();
            entry.addProperty("region", choice.region());
            entry.addProperty("style", choice.style());
            selection.add(tier, entry);
        }
        frozen.getAsJsonObject("sect_opening").add("founding_sites", selection);
        return new WorldPlanRules(frozen.toString());
    }
}
