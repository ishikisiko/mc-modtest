package com.example.myvillage.cultivation.tribulation;

import com.example.myvillage.resources.BuiltinResources;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public record SpiritTrialRules(int preparationTicks, int strikeIntervalTicks, int warningTicks, int requiredPower,
        List<Integer> damage, int nascentShareDenominator, int blockPowerPercent, int arenaRadius,
        int verticalRange, int boundaryGraceTicks) {
    public SpiritTrialRules {
        damage = List.copyOf(damage);
        if (damage.size() != 6 || damage.stream().anyMatch(value -> value < 1 || value > 2048)
                || preparationTicks <= warningTicks || strikeIntervalTicks <= warningTicks || warningTicks < 1
                || requiredPower < 1 || requiredPower > damage.size()
                || nascentShareDenominator < 2 || blockPowerPercent < 1 || blockPowerPercent > 100
                || arenaRadius < 2 || arenaRadius > 32 || verticalRange < 2 || verticalRange > 32
                || boundaryGraceTicks < 1) {
            throw new IllegalArgumentException("Invalid spirit transformation trial rules");
        }
    }

    public float damage(int strike, boolean intact) {
        int original = damage.get(strike);
        return intact ? original * (nascentShareDenominator - 1f) / nascentShareDenominator : original;
    }

    public long blockCost(long capacity) {
        return Math.ceilDiv(Math.multiplyExact(capacity, blockPowerPercent), 100);
    }

    public static SpiritTrialRules get() { return Holder.RULES; }

    private static final class Holder {
        private static final SpiritTrialRules RULES = load();

        private static SpiritTrialRules load() {
            try (var stream = BuiltinResources.open("data/yuanxian/cultivation/spirit_trial.json")) {
                if (stream == null) throw new IllegalStateException("Missing spirit transformation trial rules");
                JsonObject data = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                List<Integer> damage = new ArrayList<>();
                data.getAsJsonArray("damage").forEach(value -> damage.add(value.getAsBigDecimal().intValueExact()));
                return new SpiritTrialRules(number(data, "preparation_ticks"), number(data, "strike_interval_ticks"),
                        number(data, "warning_ticks"), number(data, "required_power"), damage,
                        number(data, "nascent_share_denominator"), number(data, "block_power_percent"),
                        number(data, "arena_radius"), number(data, "vertical_range"), number(data, "boundary_grace_ticks"));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private static int number(JsonObject data, String key) { return data.get(key).getAsBigDecimal().intValueExact(); }
    }
}
