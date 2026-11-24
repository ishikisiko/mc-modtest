package com.example.myvillage.entity.beast;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

record CliffRayRules(int horizontal, int vertical, int scanInterval, int approachTicks,
                     int releaseFrame, double attackArc, double diveDrop, double diveRise) {
    private static final String FILE = "data/yuanxian/beast/cliff_ray_perch.json";

    static CliffRayRules load() {
        var stream = CliffRayRules.class.getClassLoader().getResourceAsStream(FILE);
        if (stream == null) throw new IllegalStateException("缺少岩鳐抱崖参数");
        BeastJson.Fields f;
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            f = new BeastJson.Fields(FILE, "", BeastJson.parseStrict(FILE, reader),
                    Set.of("search_horizontal", "search_vertical", "scan_interval_ticks", "approach_ticks", "release_frame",
                            "attack_arc_degrees", "dive_drop", "dive_rise"));
        } catch (IOException error) {
            throw new IllegalStateException("无法读取岩鳐抱崖参数", error);
        }
        var rule = new CliffRayRules(f.positiveInteger("search_horizontal"), f.positiveInteger("search_vertical"),
                f.positiveInteger("scan_interval_ticks"), f.positiveInteger("approach_ticks"),
                f.positiveInteger("release_frame"), f.positiveNumber("attack_arc_degrees"),
                f.positiveNumber("dive_drop"), f.positiveNumber("dive_rise"));
        if (rule.horizontal > 8 || rule.vertical > 4 || rule.attackArc > 90 || rule.approachTicks > 80) {
            throw new IllegalArgumentException("岩鳐抱崖搜索、转向或靠近范围过大");
        }
        return rule;
    }
}
