package com.example.myvillage.boss.lair;

import com.example.myvillage.ruins.RuinRules.Pos;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The bundled authored input also owns native-template geometry and natural placement. */
public record ThunderTigerLairRules(Pos size, Pos anchor, Pos spawn, Pos entry, Pos trigger,
        List<Pos> pillars, int tickInterval, double triggerRadius) {
    public ThunderTigerLairRules {
        pillars = List.copyOf(pillars);
        if (size.x() < 1 || size.y() < 1 || size.z() < 1
                || size.x() > 80 || size.y() > 80 || size.z() > 80
                || tickInterval < 1 || tickInterval > 100
                || !Double.isFinite(triggerRadius) || triggerRadius <= 0 || triggerRadius > 48
                || pillars.isEmpty() || pillars.size() > 8 || new HashSet<>(pillars).size() != pillars.size())
            throw new IllegalArgumentException("Invalid lair bounds");
        var positions = new ArrayList<>(List.of(anchor, spawn, entry, trigger));
        positions.addAll(pillars);
        for (var p : positions) if (p.x() < 0 || p.y() < 0 || p.z() < 0
                || p.x() >= size.x() || p.y() >= size.y() || p.z() >= size.z())
            throw new IllegalArgumentException("Lair position outside template");
        for (var pillar : pillars) if (pillar.x() < 1 || pillar.z() < 1 || pillar.y() < 1
                || pillar.x() + 1 >= size.x() || pillar.z() + 1 >= size.z() || pillar.y() + 5 > size.y())
            throw new IllegalArgumentException("Pillar volume outside template");
        if (spawn.y() < 1) throw new IllegalArgumentException("Boss home needs solid support");
    }
    private static int integer(JsonElement e) { return e.getAsBigDecimal().intValueExact(); }
    private static Pos position(JsonElement e) {
        var a = e.getAsJsonArray();
        if (a.size() != 3) throw new IllegalArgumentException("Expected xyz");
        return new Pos(integer(a.get(0)), integer(a.get(1)), integer(a.get(2)));
    }
    public static ThunderTigerLairRules parse(Reader reader) {
        var r = JsonParser.parseReader(reader).getAsJsonObject();
        if (integer(r.get("version")) != 1 || !r.get("id").getAsString().equals("thunder_tiger_lair")
                || !r.get("entity").getAsString().equals("myvillage:thunder_tiger")
                || integer(r.get("pillar_radius")) != 1 || integer(r.get("pillar_height")) != 5)
            throw new IllegalArgumentException("Unsupported lair contract");
        var pillars = new ArrayList<Pos>();
        for (var p : r.getAsJsonArray("pillars")) pillars.add(position(p));
        return new ThunderTigerLairRules(position(r.get("size")), position(r.get("anchor")),
                position(r.get("spawn")), position(r.get("entry")), position(r.get("trigger")),
                pillars, integer(r.get("tick_interval")), r.get("trigger_radius").getAsDouble());
    }
    private static final class Bundled {
        static final ThunderTigerLairRules RULES = load();
        static ThunderTigerLairRules load() {
            try (var in = ThunderTigerLairRules.class.getResourceAsStream("/data/myvillage/boss/thunder_tiger_lair.json")) {
                if (in == null) throw new IllegalStateException("Missing thunder tiger lair rules");
                return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) { throw new IllegalStateException(e); }
        }
    }
    public static ThunderTigerLairRules get() { return Bundled.RULES; }
}
