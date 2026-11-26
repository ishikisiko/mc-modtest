package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.worldplan.SectOpeningRules;
import com.example.myvillage.worldplan.WorldPlanRules;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

import static com.example.myvillage.sim.data.SectProgressionRules.*;
import static com.example.myvillage.worldplan.SectOpeningRules.AUTO;

/** 在主世界绑定种子前读取一次，实际选择随世界规划保存。 */
public final class FoundingSiteConfig {
    public static final String FILE_NAME = "yuanxian-founding-sites.toml";
    public static final ModConfigSpec SPEC;
    private static final Map<String, Options> OPTIONS;

    private record Options(ModConfigSpec.ConfigValue<String> region, ModConfigSpec.ConfigValue<String> style) {}

    static {
        var builder = new ModConfigSpec.Builder();
        Map<String, Options> options = new LinkedHashMap<>();
        builder.comment("三个修仙地图预设共用，仅在创建新世界时读取。").push("founding_sites");
        for (String tier : IDS) {
            String regions = switch (tier) {
                case BRANCH -> "donghuang（东荒）、lingyue（灵岳）、beiyuan（北原）";
                case HEADQUARTERS -> "donghai（东海）、ximo（西漠）、nanjiang（南疆）";
                case MAIN -> "zhongzhou（中州）";
                default -> throw new IllegalArgumentException(tier);
            };
            builder.push(tier);
            var region = builder.comment("所在州。auto 由选址规则决定。可选 " + regions + "。")
                    .define("region", AUTO);
            var style = builder.comment("建筑系列。auto 由种子决定。可选 cloud_gate（云阙）、cliff_retreat（松崖）、",
                            "valley_courts（听泉双岸）、valley_retreat（听泉谷中）、lake_isles（照月连岛）、lake_retreat（照月湖心）。")
                    .define("style", AUTO);
            options.put(tier, new Options(region, style));
            builder.pop();
        }
        builder.pop();
        OPTIONS = Map.copyOf(options);
        SPEC = builder.build();
    }

    private FoundingSiteConfig() {}

    static WorldPlanRules freeze(WorldPlanRules rules) {
        if (!SPEC.isLoaded() || rules.sectOpening == null) return rules;
        Map<String, SectOpeningRules.SiteChoice> choices = new LinkedHashMap<>();
        for (String tier : IDS) {
            Options options = OPTIONS.get(tier);
            choices.put(tier, new SectOpeningRules.SiteChoice(options.region.get(), options.style.get()));
        }
        return SectOpeningRules.withChoices(rules, choices);
    }
}
