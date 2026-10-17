package com.example.myvillage.entity.npc;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Gameplay values have one authored source, independent of NPC appearance. */
public record NpcCombatRules(int schema, String weapon, double health, double attack, double speed,
        double followRange, long spirit, int decisionTicks, int reactionTicks, double pursuitRadius,
        int lostTargetTicks, double retreatFraction, double escapeDistance, double approachSpeed,
        double retreatSpeed, int recoveryDelayTicks, int healIntervalTicks, double healFraction,
        int playerKillStandingPenalty) {
    private static final String PATH = "/data/myvillage/npc_combat/rules.json";
    private static final NpcCombatRules INSTANCE = load();
    public NpcCombatRules {
        if (schema != 1 || weapon == null || !weapon.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || !positive(health) || !positive(attack) || !positive(speed) || !positive(followRange)
                || spirit < 0 || decisionTicks < 1 || reactionTicks < 1 || !positive(pursuitRadius)
                || lostTargetTicks < 1 || !(retreatFraction > 0 && retreatFraction < 1)
                || !positive(escapeDistance) || !positive(approachSpeed) || !positive(retreatSpeed)
                || recoveryDelayTicks < 1 || healIntervalTicks < 1 || !(healFraction > 0 && healFraction <= 1)
                || playerKillStandingPenalty < 0 || playerKillStandingPenalty > 100)
            throw new IllegalArgumentException("invalid_npc_combat_rules");
    }
    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
    public static NpcCombatRules get() { return INSTANCE; }
    private static NpcCombatRules load() {
        try (var stream = NpcCombatRules.class.getResourceAsStream(PATH)) {
            if (stream == null) throw new IllegalStateException("Missing " + PATH);
            return new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), NpcCombatRules.class);
        } catch (java.io.IOException ex) { throw new IllegalStateException(PATH, ex); }
    }
}
