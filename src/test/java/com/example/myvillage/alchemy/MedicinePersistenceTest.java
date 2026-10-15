package com.example.myvillage.alchemy;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicinePersistenceTest {
    @Test void saveLoadPreservesPartialIntervalWithoutOfflineAdvancement() {
        var state = new MedicineState(452, "yangyuan", 52, 8);
        CompoundTag saved = MedicineRuntime.write(state);
        assertEquals(state, MedicineRuntime.read(saved.copy(), AlchemyRules.get()));
        assertEquals(state, MedicineRuntime.read(saved.copy(), AlchemyRules.get()));
        var next = MedicineRuntime.read(saved, AlchemyRules.get()).tick(20);
        assertFalse(next.heal());
        assertEquals(new MedicineState(451, "yangyuan", 51, 9), next.next());
    }

    @Test void malformedSaveCannotCreateUnknownOrOverlongBenefits() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("cooldown_ticks", Integer.MAX_VALUE);
        tag.putString("effect", "unknown");
        tag.putInt("effect_ticks", Integer.MAX_VALUE);
        var state = MedicineRuntime.read(tag, AlchemyRules.get());
        assertEquals(AlchemyRules.get().sharedCooldownTicks(), state.cooldownTicks());
        assertEquals("", state.effect());
        tag.putString("effect", "ningshen");
        tag.putInt("healing_elapsed", Integer.MAX_VALUE);
        state = MedicineRuntime.read(tag, AlchemyRules.get());
        assertEquals(AlchemyRules.get().effects().ningshen().durationTicks(), state.effectTicks());
        assertEquals(0, state.healingElapsed());
    }
    @Test void sustainedQualitySurvivesSaveRestartAndOnlineTicks() {
        for (String effect : new String[] {"yangyuan", "ningshen"}) {
            for (int quality = 1; quality <= 4; quality++) {
                MedicineState state = new MedicineState(452, effect, 52, effect.equals("yangyuan") ? 8 : 0, quality);
                CompoundTag saved = MedicineRuntime.write(state);
                assertEquals(quality, saved.getInt("quality"));
                MedicineState loaded = MedicineRuntime.read(saved.copy(), AlchemyRules.get());
                assertEquals(state, loaded);
                assertEquals(quality, loaded.tick(20).next().quality());
                assertEquals(1, loaded.clearBenefit().quality());
            }
        }
    }

    @Test void legacyAndCorruptQualityFallBackToBaseline() {
        CompoundTag saved = MedicineRuntime.write(new MedicineState(452, "yangyuan", 52, 8, 4));
        saved.remove("quality");
        assertEquals(1, MedicineRuntime.read(saved, AlchemyRules.get()).quality());
        for (int invalid : new int[] {Integer.MIN_VALUE, 0, 5, Integer.MAX_VALUE}) {
            saved.putInt("quality", invalid);
            assertEquals(1, MedicineRuntime.read(saved, AlchemyRules.get()).quality());
        }
        saved.putString("quality", "4");
        assertEquals(1, MedicineRuntime.read(saved, AlchemyRules.get()).quality());
        saved.putDouble("quality", 4.0);
        assertEquals(1, MedicineRuntime.read(saved, AlchemyRules.get()).quality());
    }

}
