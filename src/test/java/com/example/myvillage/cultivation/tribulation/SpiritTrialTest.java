package com.example.myvillage.cultivation.tribulation;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.cultivation.CultivationMemory;
import com.example.myvillage.cultivation.nascent.NascentRules;
import com.example.myvillage.cultivation.network.SpiritTrialStatusPayload.Action;
import com.mojang.serialization.JsonOps;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpiritTrialTest {
    @Test void sixStrikesUseTheFixedRawAndIntactSoulDamage() {
        var rules = SpiritTrialRules.get();
        assertEquals(List.of(84, 96, 108, 120, 132, 144), rules.damage());
        float[] withSoul = {56, 64, 72, 80, 88, 96};
        for (int strike = 0; strike < withSoul.length; strike++) {
            assertEquals(rules.damage().get(strike).floatValue(), rules.damage(strike, false));
            assertEquals(withSoul[strike], rules.damage(strike, true));
        }
        assertEquals(120, rules.preparationTicks());
        assertEquals(80, rules.strikeIntervalTicks());
        assertEquals(30, rules.warningTicks());
        assertEquals(4, rules.requiredPower());
        assertEquals(251, rules.blockCost(1001));
    }

    @Test void theThirdStrikeOpensTheMiddleScatterWindowAndBlockingRequiresAWarning() {
        var middle = SpiritTrial.Route.MIDDLE;
        assertEquals(Action.NONE, SpiritTrial.action(middle, true, false, 2, 31, false, true, 2, 30));
        assertEquals(Action.BLOCK, SpiritTrial.action(middle, true, false, 2, 30, false, true, 2, 30));
        assertEquals(Action.SCATTER, SpiritTrial.action(middle, true, false, 3, 80, false, true, 3, 30));
        assertEquals(Action.SCATTER, SpiritTrial.action(middle, true, false, 3, 1, false, true, 3, 30));
        assertEquals(Action.BLOCK, SpiritTrial.action(middle, true, false, 3, 1, false, false, 3, 30));
        assertEquals(Action.NONE, SpiritTrial.action(middle, true, false, 3, 1, true, false, 2, 30));
        assertEquals(Action.NONE, SpiritTrial.action(SpiritTrial.Route.HIGH, true, false, 0, 1, false, false, 0, 30));
        assertEquals(Action.UNITE, SpiritTrial.action(middle, false, true, 6, 0, false, false, 4, 30));
    }

    @Test void savedUpperGradeSurvivesBothRecoveryPhasesAndRestoresOneIntactSoul() {
        var lost = CultivationMemory.EMPTY.form(2).disperseSeed().spirit(3).reform(4000, 4320);
        var encoded = CultivationMemory.CODEC.encodeStart(JsonOps.INSTANCE, lost).getOrThrow();
        var restored = CultivationMemory.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(lost, restored);
        var rules = NascentRules.get();
        assertEquals(4320, rules.reformationTarget(restored));
        var seed = restored.reform(4320, rules.reformationTarget(restored));
        assertFalse(seed.seedLost());
        assertFalse(seed.intact());
        assertEquals(0, seed.reformation());
        assertEquals(2160, rules.reformationTarget(seed));
        var intact = seed.reform(2160, rules.reformationTarget(seed));
        assertTrue(intact.intact());
        assertEquals(2, intact.nascentQuality());
        assertEquals(3, intact.spiritQuality());
        assertFalse(intact.shatter().seedLost());
    }
}
