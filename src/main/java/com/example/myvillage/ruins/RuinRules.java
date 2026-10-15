package com.example.myvillage.ruins;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared authored geometry and rewards; no client-supplied positions or rewards. */
public record RuinRules(Pos size, Pos core, Pos vault, int confirmationTicks,
                        double interactionDistance, String manual, List<Plant> garden,
                        List<Reward> vaultItems, List<Guardian> guardians) {
    public record Pos(int x, int y, int z) {
        public Pos subtract(Pos other) { return new Pos(x-other.x, y-other.y, z-other.z); }
        /** The template faces north; native structure rotation rotates the core's facing. */
        public Pos rotate(int quarterTurns) {
            return switch (Math.floorMod(quarterTurns, 4)) {
                case 1 -> new Pos(-z, y, x);
                case 2 -> new Pos(-x, y, -z);
                case 3 -> new Pos(z, y, -x);
                default -> this;
            };
        }
    }
    public record Plant(Pos pos, String crop) {}
    public record Reward(String id, int count) {}
    public record Guardian(String realm, int stage) {}
    public RuinRules {
        garden = List.copyOf(garden); vaultItems = List.copyOf(vaultItems); guardians = List.copyOf(guardians);
        if (size.x < 1 || size.y < 1 || size.z < 1 || size.x > 64 || size.y > 64 || size.z > 64
                || confirmationTicks < 1 || confirmationTicks > 1200
                || !Double.isFinite(interactionDistance) || interactionDistance <= 0 || interactionDistance > 6
                || garden.isEmpty() || garden.size() > 32 || vaultItems.size() > 26)
            throw new IllegalArgumentException("Invalid ruin bounds");
        Set<Pos> occupied = new HashSet<>();
        for (Pos p : List.of(core, vault)) requireInside(p, size, occupied);
        for (Plant plant : garden) {
            requireInside(plant.pos, size, occupied);
            requireInside(new Pos(plant.pos.x, plant.pos.y-1, plant.pos.z), size, occupied);
            requireId(plant.crop);
        }
        requireId(manual);
        for (Reward reward : vaultItems) {
            requireId(reward.id);
            if (reward.count < 1 || reward.count > 64) throw new IllegalArgumentException("Invalid reward count");
        }
        if (guardians.size() > 8) throw new IllegalArgumentException("Too many guardians");
        for (Guardian guardian : guardians) {
            requireId(guardian.realm);
            if (guardian.stage < 0 || guardian.stage > 100) throw new IllegalArgumentException("Invalid guardian stage");
        }
    }
    private static void requireInside(Pos p, Pos size, Set<Pos> occupied) {
        if (p.x < 0 || p.y < 0 || p.z < 0 || p.x >= size.x || p.y >= size.y || p.z >= size.z
                || !occupied.add(p)) throw new IllegalArgumentException("Invalid/duplicate ruin target " + p);
    }
    private static void requireId(String id) {
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid id " + id);
    }
    private static Pos pos(JsonElement element) {
        JsonArray a = element.getAsJsonArray();
        if (a.size() != 3) throw new IllegalArgumentException("Expected xyz");
        return new Pos(exactInt(a.get(0)), exactInt(a.get(1)), exactInt(a.get(2)));
    }
    private static int exactInt(JsonElement e) { return e.getAsBigDecimal().intValueExact(); }
    public static RuinRules parse(Reader reader) {
        JsonObject r = JsonParser.parseReader(reader).getAsJsonObject();
        if (exactInt(r.get("version")) != 1) throw new IllegalArgumentException("Unsupported ruin version");
        List<Plant> plants = new ArrayList<>();
        for (JsonElement e : r.getAsJsonArray("garden")) {
            JsonObject p = e.getAsJsonObject(); plants.add(new Plant(pos(p.get("pos")), p.get("crop").getAsString()));
        }
        List<Reward> rewards = new ArrayList<>();
        for (JsonElement e : r.getAsJsonArray("vault_items")) {
            JsonObject p = e.getAsJsonObject(); rewards.add(new Reward(p.get("id").getAsString(), exactInt(p.get("count"))));
        }
        List<Guardian> guardians = new ArrayList<>();
        for (JsonElement e : r.getAsJsonArray("guardians")) {
            JsonObject p = e.getAsJsonObject(); guardians.add(new Guardian(p.get("realm").getAsString(), exactInt(p.get("stage"))));
        }
        return new RuinRules(pos(r.get("size")), pos(r.get("core")), pos(r.get("vault")),
                exactInt(r.get("confirmation_ticks")), r.get("interaction_distance").getAsDouble(),
                r.get("manual").getAsString(), plants, rewards, guardians);
    }
    private static final class Holder {
        static final RuinRules RULES = load();
        private static RuinRules load() {
            try (InputStream in = RuinRules.class.getResourceAsStream("/data/myvillage/ruins/danxia.json")) {
                if (in == null) throw new IllegalStateException("Missing danxia rules");
                return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) { throw new IllegalStateException(e); }
        }
    }
    public static RuinRules get() { return Holder.RULES; }
}
