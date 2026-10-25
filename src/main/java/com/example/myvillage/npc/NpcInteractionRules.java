package com.example.myvillage.npc;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Sole authored source for conversation and unaffiliated cultivator movement bounds. */
public record NpcInteractionRules(int schema, double range, int sessionTicks, int pauseTicks,
        int intentCooldownTicks, int maxSessions, int wanderRadius) {
    private static final NpcInteractionRules INSTANCE = load();
    public NpcInteractionRules {
        if (schema != 1 || !Double.isFinite(range) || range <= 0 || range > 10
                || sessionTicks < 20 || sessionTicks > 12000 || pauseTicks < 1 || pauseTicks > sessionTicks
                || intentCooldownTicks < 1 || intentCooldownTicks > pauseTicks
                || maxSessions < 1 || maxSessions > 4096 || wanderRadius < 2 || wanderRadius > 64)
            throw new IllegalArgumentException("invalid_npc_interaction_rules");
    }
    public static NpcInteractionRules get() { return INSTANCE; }
    private static NpcInteractionRules load() {
        String path = "/data/myvillage/npc_interaction/rules.json";
        try (var input = NpcInteractionRules.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing " + path);
            return new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), NpcInteractionRules.class);
        } catch (java.io.IOException ex) { throw new IllegalStateException(path, ex); }
    }
}
