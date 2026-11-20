package com.example.myvillage.cultivation.nascent;

import com.example.myvillage.resources.BuiltinResources;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Bundled duration, combat and reformation values for the three routes. */
public final class NascentRules {
    private final JsonObject data;

    private NascentRules(JsonObject data) {
        this.data = data;
        if (data.getAsJsonArray("pill_ticks").size() != 4 || data.getAsJsonArray("reformation_pill_multipliers").size() != 4) {
            throw new IllegalArgumentException("Four nascent pill qualities required");
        }
        if (value("npc_lotus_chance_basis_points") > 10_000)
            throw new IllegalArgumentException("Invalid NPC lotus chance");
        for (String bounded : new String[]{"refinement_qi_percent", "pill_failure_stability_percent",
                "committed_failure_stability_percent", "duel_failure_progress_percent", "npc_injury_after_shatter",
                "player_lotus_chest_percent"}) {
            if (value(bounded) > 100) throw new IllegalArgumentException("Invalid nascent percentage: " + bounded);
        }
    }

    public static NascentRules get() { return Holder.VALUE; }

    private static final class Holder {
        private static final NascentRules VALUE = load();

        private static NascentRules load() {
            try (var in = BuiltinResources.open("data/yuanxian/cultivation/nascent.json")) {
                if (in == null) throw new IllegalStateException("Missing nascent soul rules");
                return new NascentRules(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    public int value(String key) {
        int value = data.get(key).getAsBigDecimal().intValueExact();
        if (value <= 0) throw new IllegalArgumentException("Non-positive nascent rule: " + key);
        return value;
    }

    public boolean isLotusLootTable(String table) { return data.get("player_lotus_loot_table").getAsString().equals(table); }

    public double npcLotusChance() { return value("npc_lotus_chance_basis_points") / 10_000.0; }

    public int pillTicks(int quality) { return choice("pill_ticks", quality).getAsInt(); }
    public double reformationMultiplier(int quality) { return choice("reformation_pill_multipliers", quality).getAsDouble(); }

    private com.google.gson.JsonElement choice(String key, int quality) {
        JsonArray choices = data.getAsJsonArray(key);
        return choices.get(Math.clamp(quality, 1, 4) - 1);
    }
}
