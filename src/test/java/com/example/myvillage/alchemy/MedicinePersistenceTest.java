package com.example.myvillage.alchemy;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicinePersistenceTest {
    @Test void qingzhangQualityDurationSurvivesReadAndBoundsCorruptSaves() {
        var rules=AlchemyRules.get();
        int[] durations={600,750,900,1200};
        for(int q=1;q<=4;q++){
            var state=new MedicineState(450,"qingzhang",durations[q-1]-20,0,q);
            assertEquals(state,MedicineRuntime.read(MedicineRuntime.write(state),rules));
            var corrupt=MedicineRuntime.write(state);corrupt.putInt("effect_ticks",Integer.MAX_VALUE);
            assertEquals(durations[q-1],MedicineRuntime.read(corrupt,rules).effectTicks());
            corrupt.remove("quality");assertEquals(600,MedicineRuntime.read(corrupt,rules).effectTicks());
        }
        // Existing active doses keep their remaining time; upgrading never refills a dose.
        var old=new MedicineState(0,"qingzhang",350,0,4);
        assertEquals(old,MedicineRuntime.read(MedicineRuntime.write(old),rules));
    }
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
