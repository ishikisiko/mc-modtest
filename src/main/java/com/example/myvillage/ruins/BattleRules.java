package com.example.myvillage.ruins;

import com.google.gson.*;
import com.example.myvillage.stats.StatsRules;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored encounter data is the only source for timing, placement, enemies and rewards. */
public record BattleRules(String id, String mode, RuinRules.Pos cache, RuinRules.Pos entry,
        int tickInterval, int waveDelayTicks, double interactionDistance, double leashRadius,
        double triggerRadius, String manual, List<RuinRules.Reward> rewards, List<Wave> waves) {
    public record Guard(String entity, RuinRules.Pos pos, String realm, int stage, String weapon, boolean elite) {}
    public record Wave(RuinRules.Pos trigger, List<Guard> guards) {}
    private static final Set<String> IDS = Set.of("duange_ruin", "fuyao_ruin");
    private static final class Bundled {
        static final Map<String, BattleRules> ALL = load();
        static Map<String, BattleRules> load() {
            Map<String, BattleRules> result = new HashMap<>();
            for (String id : IDS) {
                try (var in = BattleRules.class.getResourceAsStream("/data/myvillage/ruins/"+id+".json")) {
                    if (in == null) throw new IllegalStateException("Missing battle rules " + id);
                    result.put(id, parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()));
                } catch (IOException e) { throw new IllegalStateException(e); }
            }
            return Map.copyOf(result);
        }
    }
    public static BattleRules get(String id) { return Bundled.ALL.get(id); }
    private static int integer(JsonElement v) { return v.getAsBigDecimal().intValueExact(); }
    private static RuinRules.Pos pos(JsonElement v) {
        var a=v.getAsJsonArray();
        if (a.size()!=3) throw new IllegalArgumentException("Position length");
        var p=new RuinRules.Pos(integer(a.get(0)),integer(a.get(1)),integer(a.get(2)));
        if (p.x()<0 || p.y()<0 || p.z()<0 || p.x()>80 || p.y()>80 || p.z()>80) throw new IllegalArgumentException("Position bounds");
        return p;
    }
    public static BattleRules parse(JsonObject r) {
        String id=r.get("id").getAsString(), mode=r.get("mode").getAsString();
        if (integer(r.get("version"))!=1 || !IDS.contains(id) || !Set.of("route","arena").contains(mode)) throw new IllegalArgumentException("Battle identity");
        List<Wave> waves=new ArrayList<>();
        for (var value:r.getAsJsonArray("waves")) {
            var w=value.getAsJsonObject(); List<Guard> guards=new ArrayList<>();
            for (var gv:w.getAsJsonArray("guards")) {
                var g=gv.getAsJsonObject(); String entity=g.get("entity").getAsString(), realm=g.get("realm").getAsString();
                int stage=integer(g.get("stage"));
                if (!Set.of("minecraft:zombie","minecraft:skeleton","minecraft:wither_skeleton","myvillage:demon_wolf").contains(entity)) throw new IllegalArgumentException("Unsupported guard");
                StatsRules.bundled().realmKey(realm,stage);
                guards.add(new Guard(entity,pos(g.get("pos")),realm,stage,g.get("weapon").getAsString(),g.get("elite").getAsBoolean()));
            }
            if (guards.isEmpty() || guards.size()>8) throw new IllegalArgumentException("Guard bounds");
            waves.add(new Wave(pos(w.get("trigger")),List.copyOf(guards)));
        }
        List<RuinRules.Reward> rewards=new ArrayList<>();
        for (var v:r.getAsJsonArray("rewards")) {
            var x=v.getAsJsonObject(); int count=integer(x.get("count"));
            if (count<1 || count>16) throw new IllegalArgumentException("Reward bounds");
            rewards.add(new RuinRules.Reward(x.get("id").getAsString(),count));
        }
        int interval=integer(r.get("tick_interval")), delay=integer(r.get("wave_delay_ticks"));
        double distance=r.get("interaction_distance").getAsDouble(), leash=r.get("leash_radius").getAsDouble(), trigger=r.get("trigger_radius").getAsDouble();
        if(waves.isEmpty() || waves.size()>8 || rewards.size()>26 || interval<1 || interval>100 || delay<0 || delay>1200
                || !(distance>0 && distance<=6) || !(leash>0 && leash<=96) || !(trigger>0 && trigger<=32)) throw new IllegalArgumentException("Battle limits");
        return new BattleRules(id,mode,pos(r.get("cache")),pos(r.get("entry")),interval,delay,distance,leash,trigger,
                r.get("manual").getAsString(),List.copyOf(rewards),List.copyOf(waves));
    }
}
