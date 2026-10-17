package com.example.myvillage.cultivation.fate;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedFateStateTest {
    private final UUID target = UUID.fromString("5f8d2dbe-a43d-43d3-99c0-fb29f7021f4f");
    private FateState state() {
        return new FateState(1, 1, List.of("myvillage:robust", "myvillage:bright", "myvillage:innate_sword_heart"),
                "", List.of("myvillage:robust", "myvillage:bright", "myvillage:innate_sword_heart"), true,
                500, 10, .2, 600)
                .withPillAegis(new PillAegisState(4, 700, 800))
                .withTemperedBones(new TemperedBonesState(2, false))
                .withSwordHeart(new SwordHeartState(150, 250))
                .withWindStep(new WindStepState(false, 350))
                .withFireSpirit(new FireSpiritCooldownState(Map.of(target.toString(), 450L)));
    }
    @Test void allOptionalStatesRoundTripTogether() {
        FateState state = state();
        assertEquals(state, FateState.CODEC.parse(JsonOps.INSTANCE,
                FateState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()).getOrThrow());
    }
    @Test void oldSaveDefaultsNeverGrantAnAirChargeOrOpening() {
        var state = FateState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        assertEquals(SwordHeartState.EMPTY, state.swordHeart());
        assertEquals(WindStepState.EMPTY, state.windStep());
        assertTrue(state.windStep().airSpent());
        assertEquals(FireSpiritCooldownState.EMPTY, state.fireSpirit());
    }
    @Test void unrelatedTransitionsKeepAdvancedState() {
        var original = state();
        for (var changed : List.of(original.timers(501, 8, .1, 601),
                original.choice(original.candidates(), "", original.selected(), true, 2),
                original.withPillAegis(PillAegisState.EMPTY),
                original.withTemperedBones(TemperedBonesState.EMPTY))) {
            assertEquals(original.swordHeart(), changed.swordHeart());
            assertEquals(original.windStep(), changed.windStep());
            assertEquals(original.fireSpirit(), changed.fireSpirit());
        }
    }
    @Test void deathAndOperatorResetDiscardBenefitsWithoutResettingCooldowns() {
        var original = state();
        for (var changed : List.of(original.afterDeath(), original.resetChoices())) {
            assertEquals(0, changed.swordHeart().openingExpiresAt());
            assertEquals(250, changed.swordHeart().cooldownReadyAt());
            assertTrue(changed.windStep().airSpent());
            assertEquals(350, changed.windStep().readyAt());
            assertEquals(original.fireSpirit(), changed.fireSpirit());
            assertEquals(0, changed.pillAegis().shield());
            assertEquals(800, changed.pillAegis().cooldownReadyAt());
        }
        assertEquals(original.selected(), original.afterDeath().selected());
        assertFalse(original.resetChoices().confirmed());
    }
    @Test void advancedWritersKeepEachOthersState() {
        var original = state();
        assertEquals(original.windStep(), original.withSwordHeart(SwordHeartState.EMPTY).windStep());
        assertEquals(original.fireSpirit(), original.withSwordHeart(SwordHeartState.EMPTY).fireSpirit());
        assertEquals(original.swordHeart(), original.withWindStep(WindStepState.EMPTY).swordHeart());
        assertEquals(original.fireSpirit(), original.withWindStep(WindStepState.EMPTY).fireSpirit());
        assertEquals(original.swordHeart(), original.withFireSpirit(FireSpiritCooldownState.EMPTY).swordHeart());
        assertEquals(original.windStep(), original.withFireSpirit(FireSpiritCooldownState.EMPTY).windStep());
    }
}
