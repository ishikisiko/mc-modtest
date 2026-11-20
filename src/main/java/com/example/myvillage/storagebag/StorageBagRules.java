package com.example.myvillage.storagebag;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.forging.ForgingIngredients;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StorageBagRules {
    public static final int COLUMNS = 9;
    public static final int PAGE_ROWS = 6;

    public enum Kind {
        MATERIAL("material"), GENERAL("general"), MANUAL("manual");

        private final String id;

        Kind(String id) { this.id = id; }

        public String id() { return id; }
    }

    public enum Station { DING, ARRAY }

    public enum RealmAccess { ALLOWED, LOW, UNAVAILABLE }

    public record Tier(ResourceLocation realm, String name, int rows, Station station, Map<String, Integer> costs) {
        public int capacity() { return rows * COLUMNS; }

        public String itemId(Kind kind) { return "storage_bag_" + kind.id() + "_" + realm.getPath(); }
    }

    private static final class Holder {
        static final List<Tier> TIERS = load();
    }

    private StorageBagRules() {}

    public static List<Tier> tiers() { return Holder.TIERS; }

    private static List<Tier> load() {
        String path = "/data/" + MyVillageMod.MOD_ID + "/storage_bags/tiers.json";
        try (var reader = new InputStreamReader(
                StorageBagRules.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            var data = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("tiers");
            var tiers = new ArrayList<Tier>();
            var realms = new HashSet<ResourceLocation>();
            int previousRows = 0;
            for (var entry : data) {
                var item = entry.getAsJsonObject();
                var realm = ResourceLocation.parse(item.get("realm").getAsString());
                int rows = item.get("rows").getAsInt();
                if (!realm.getNamespace().equals(MyVillageMod.MOD_ID) || !realms.add(realm)
                        || rows <= previousRows || rows > 256 / COLUMNS) {
                    throw new IllegalArgumentException("Invalid storage bag tier: " + item);
                }
                var station = switch (item.get("station").getAsString()) {
                    case "ding" -> Station.DING;
                    case "array" -> Station.ARRAY;
                    default -> throw new IllegalArgumentException("Invalid storage bag station: " + item);
                };
                var costs = new LinkedHashMap<String, Integer>();
                for (var cost : item.getAsJsonObject("costs").entrySet()) {
                    int count = cost.getValue().getAsInt();
                    if (count < 1 || count > 4096) throw new IllegalArgumentException("Invalid cost: " + cost);
                    costs.put(ForgingIngredients.validate(cost.getKey()), count);
                }
                if (costs.isEmpty()) throw new IllegalArgumentException("Missing storage bag costs: " + item);
                tiers.add(new Tier(realm, item.get("name").getAsString(), rows, station, Map.copyOf(costs)));
                previousRows = rows;
            }
            if (tiers.isEmpty() || tiers.getFirst().station() != Station.DING) {
                throw new IllegalArgumentException("Missing initial storage bag tier");
            }
            return List.copyOf(tiers);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load storage bag rules: " + path, exception);
        }
    }

    public static RealmAccess realmAccess(ServerPlayer player, Tier tier) {
        var realms = player.registryAccess().registry(ModCultivationRegistries.REALMS).orElse(null);
        if (realms == null || realms.get(tier.realm()) == null) return RealmAccess.UNAVAILABLE;
        var profile = CultivationService.getProfile(player);
        var current = realms.get(profile.realmId());
        var minimum = realms.get(tier.realm());
        if (current == null || !current.containsStage(profile.stageId())) return RealmAccess.LOW;
        if (profile.realmId().equals(tier.realm()) || current.sortOrder() > minimum.sortOrder()) {
            return RealmAccess.ALLOWED;
        }
        return RealmAccess.LOW;
    }
}
